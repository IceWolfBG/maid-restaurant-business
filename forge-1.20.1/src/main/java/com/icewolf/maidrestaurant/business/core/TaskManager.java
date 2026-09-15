package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.FoodPlateBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.core.CookingDeviceStatsManager;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;

import java.util.*;

/**
 * 中心化任务管理器
 * 负责管理所有自动化任务的状态、分配、超时检测和异常处理
 *
 * 任务状态流转：
 * PENDING（待处理）→ ASSIGNED（已分配）→ IN_PROGRESS（进行中）→ COMPLETED（已完成）
 *                          ↓
 *                      TIMEOUT（超时）→ 重试1次 → 仍失败则回到PENDING重新分配
 *                          ↓
 *                      FAILED（失败）→ 移除任务
 */
public class TaskManager {

    // 任务类型常量
    public static final String TYPE_PACKAGING = "packaging";
    public static final String TYPE_DELIVERY = "delivery";
    public static final String TYPE_COOKING = "cooking";
    public static final String TYPE_PREP = "prep";
    public static final String TYPE_DISHWASHING = "dishwashing";
    public static final String TYPE_COLLECT_PLATE = "collect_plate";

    // 任务状态
    public enum TaskStatus {
        PENDING,      // 待处理
        ASSIGNED,     // 已分配给女仆，女仆正在前往目标
        IN_PROGRESS,  // 女仆已到达目标，正在执行交互
        COMPLETED,    // 已完成
        TIMEOUT,      // 超时
        FAILED        // 失败
    }

    // PENDING任务超时时间（tick，20tick=1秒）
    // 如果一个任务处于PENDING状态超过这个时间，说明没有女仆能领取，自动移除
    private static final long PENDING_TIMEOUT = 600L; // 30秒

    // 任务超时时间（tick，20tick=1秒）
    private static final Map<String, Long> TIMEOUT_TICKS = new HashMap<>();
    static {
        TIMEOUT_TICKS.put(TYPE_PACKAGING, 600L);    // 30秒
        TIMEOUT_TICKS.put(TYPE_DELIVERY, 1200L);     // 60秒
        TIMEOUT_TICKS.put(TYPE_COOKING, 1800L);      // 90秒（每道菜独立计时）
        TIMEOUT_TICKS.put(TYPE_PREP, 1200L);          // 60秒
        TIMEOUT_TICKS.put(TYPE_DISHWASHING, 1200L);   // 60秒
        TIMEOUT_TICKS.put(TYPE_COLLECT_PLATE, 300L);    // 15秒（收盘子很快）
    }

    // ASSIGNED状态超时时间（tick，20tick=1秒）
    // 如果任务处于ASSIGNED状态超过这个时间，说明女仆没有开始交互，自动失败重新分配
    // 从配置文件读取，默认600tick = 30秒
    private static long getAssignedTimeout() {
        try {
            return com.icewolf.maidrestaurant.business.config.TaskSafetyConfig.assignedTaskTimeout;
        } catch (Throwable t) {
            return 600L;
        }
    }

    // 最大重试次数
    private static final int MAX_RETRIES = 1;

    // 检测频率（每10tick=0.5秒检测一次）
    private static final long CHECK_INTERVAL = 10L;

    // 任务信息
    public static class TaskInfo {
        public final String taskId;
        public final String taskType;
        public final BlockPos targetPos;
        public final BlockPos machinePos;
        public final long createTime; // 任务创建时间
        public TaskStatus status;
        public UUID assignedMaid;
        public long assignTime;
        public long lastHeartbeat;
        public int retryCount;
        public Map<String, Object> data; // 额外数据（如订单信息、餐盘信息等）
        public String deviceType; // 厨具类型（仅烹饪任务使用，用于统计活跃任务数）

        public TaskInfo(String taskId, String taskType, BlockPos targetPos, BlockPos machinePos, long createTime) {
            this.taskId = taskId;
            this.taskType = taskType;
            this.targetPos = targetPos;
            this.machinePos = machinePos;
            this.createTime = createTime;
            this.status = TaskStatus.PENDING;
            this.assignedMaid = null;
            this.assignTime = 0;
            this.lastHeartbeat = 0;
            this.retryCount = 0;
            this.data = new HashMap<>();
            this.deviceType = null;
        }
    }

    // 单例
    private static TaskManager instance;

    // 所有任务（taskId -> TaskInfo）
    private final Map<String, TaskInfo> tasks = new HashMap<>();

    // 按目标位置索引的任务（用于快速查找某个位置是否有任务）
    private final Map<BlockPos, Set<String>> tasksByTarget = new HashMap<>();

    // 按女仆索引的任务（用于快速查找某个女仆正在执行的任务）
    private final Map<UUID, String> tasksByMaid = new HashMap<>();

    private long lastCheckTick = 0;
    private long currentTick = 0;
    private ServerLevel serverLevel = null; 
// 缓存当前服务端世界，用于检查女仆烹饪状态 
// 当前游戏tick，在tick方法中更新
    
    // BusinessManager引用，用于自动接单等功能
    private BusinessManager businessManager;

    // 中心化检索缓存
    private List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> cachedMaids = new ArrayList<>();
    private long lastMaidCacheTick = 0;
    private static final long MAID_CACHE_INTERVAL = 10L; // 每10tick更新一次女仆缓存

    // 带餐盘的操作台缓存（用于送餐任务）
    private List<BlockPos> cachedCountersWithPlates = new ArrayList<>();
    private long lastPlateCacheTick = 0;
    private static final long PLATE_CACHE_INTERVAL = 10L; // 每10tick更新一次餐盘缓存

    // ========== 厨具占用管理（混合检测方案） ==========
    // 正在被使用的厨具（位置 -> 占用信息）
    private final Map<BlockPos, DeviceOccupancyInfo> occupiedDevices = new HashMap<>();

    // 厨具占用超时时间（tick，20tick=1秒）
    // 如果一个厨具被占用超过这个时间且没有对应任务在执行，强制释放
    private static final long DEVICE_OCCUPY_TIMEOUT = 200L; // 10秒

    // 厨具占用信息
    public static class DeviceOccupancyInfo {
        public final BlockPos devicePos;
        public final String taskId;
        public final UUID maidUUID;
        public final long occupyTime;

        public DeviceOccupancyInfo(BlockPos devicePos, String taskId, UUID maidUUID, long occupyTime) {
            this.devicePos = devicePos;
            this.taskId = taskId;
            this.maidUUID = maidUUID;
            this.occupyTime = occupyTime;
        }
    }

    private TaskManager() {}

    public static TaskManager getInstance() {
        if (instance == null) {
            instance = new TaskManager();
        }
        return instance;
    }
    
    /**
     * 设置BusinessManager引用，用于自动接单等功能
     */
    public void setBusinessManager(BusinessManager businessManager) {
        this.businessManager = businessManager;
    }

    /**
     * 创建新任务
     * @return 任务ID，如果该位置已有同类型任务则返回null
     */
    public String createTask(String taskType, BlockPos targetPos, BlockPos machinePos) {
        return createTask(taskType, targetPos, machinePos, null);
    }

    /**
     * 创建任务（带厨具类型）
     * @param taskType 任务类型
     * @param targetPos 目标位置
     * @param machinePos 打单机位置
     * @param deviceType 厨具类型（Stockpot/CookingPot/Pot/Steamer）
     * @return 任务ID，如果创建失败返回null
     */
    public String createTask(String taskType, BlockPos targetPos, BlockPos machinePos, String deviceType) {
        // 检查该位置是否已有同类型的PENDING或ASSIGNED任务
        Set<String> existingTasks = tasksByTarget.get(targetPos);
        if (existingTasks != null) {
            for (String taskId : existingTasks) {
                TaskInfo task = tasks.get(taskId);
                if (task != null && task.taskType.equals(taskType) &&
                    (task.status == TaskStatus.PENDING || task.status == TaskStatus.ASSIGNED || task.status == TaskStatus.IN_PROGRESS)) {
                    // 已有同类型任务，不重复创建
                    return null;
                }
            }
        }

        String taskId = UUID.randomUUID().toString();
        TaskInfo task = new TaskInfo(taskId, taskType, targetPos, machinePos, currentTick);
        task.deviceType = deviceType;
        tasks.put(taskId, task);
        tasksByTarget.computeIfAbsent(targetPos, k -> new HashSet<>()).add(taskId);

        return taskId;
    }

    /**
     * 为女仆分配任务
     * @return 分配的任务，如果没有可用任务则返回null
     */
    public TaskInfo assignTask(UUID maidUUID, String taskType, ServerLevel level) {
        // 检查女仆是否已经有任务
        if (tasksByMaid.containsKey(maidUUID)) {
            return null;
        }

        // 查找该类型的PENDING任务
        TaskInfo bestTask = null;
        double bestDist = Double.MAX_VALUE;

        Entity maidEntity = level.getEntity(maidUUID);
        if (maidEntity == null) return null;

        for (TaskInfo task : tasks.values()) {
            if (!task.taskType.equals(taskType)) continue;
            if (task.status != TaskStatus.PENDING) continue;

            // 优先分配距离女仆最近的任务
            double dist = task.targetPos.distSqr(maidEntity.blockPosition());
            if (dist < bestDist) {
                bestDist = dist;
                bestTask = task;
            }
        }

        if (bestTask != null) {
            bestTask.status = TaskStatus.ASSIGNED;
            bestTask.assignedMaid = maidUUID;
            bestTask.assignTime = currentTick;
            bestTask.lastHeartbeat = currentTick;
            tasksByMaid.put(maidUUID, bestTask.taskId);
            String maidName = maidEntity != null ? maidEntity.getName().getString() : "unknown";
        }

        return bestTask;
    }

    /**
     * 女仆开始执行交互（到达目标位置）
     */
    public void startInteraction(UUID maidUUID) {
        String taskId = tasksByMaid.get(maidUUID);
        if (taskId == null) return;
        TaskInfo task = tasks.get(taskId);
        if (task != null && task.status == TaskStatus.ASSIGNED) {
            task.status = TaskStatus.IN_PROGRESS;
            task.lastHeartbeat = currentTick;
        }
    }

    /**
     * 更新心跳（女仆在执行任务时定期调用）
     */
    public void heartbeat(UUID maidUUID, long currentTick) {
        String taskId = tasksByMaid.get(maidUUID);
        if (taskId == null) return;
        TaskInfo task = tasks.get(taskId);
        if (task != null) {
            task.lastHeartbeat = currentTick;
            // 每200tick（10秒）输出一次心跳状态，方便排查卡住问题
            if (currentTick % 200L == 0L && task.taskType.equals(TYPE_COOKING)) {
            }
        }
    }

    /**
     * 完成任务
     */
    public void completeTask(UUID maidUUID) {
        String taskId = tasksByMaid.remove(maidUUID);
        if (taskId == null) return;
        TaskInfo task = tasks.remove(taskId);
        if (task != null) {
            Set<String> targetTasks = tasksByTarget.get(task.targetPos);
            if (targetTasks != null) {
                targetTasks.remove(taskId);
                if (targetTasks.isEmpty()) {
                    tasksByTarget.remove(task.targetPos);
                }
            }
            task.status = TaskStatus.COMPLETED;
            long duration = currentTick - task.createTime;
            // 自动释放关联的厨具占用（烹饪任务的targetPos就是厨具位置）
            if (task.taskType.equals(TYPE_COOKING) && task.targetPos != null) {
                releaseDevice(task.targetPos);
            } else {
            }
        }
    }

    /**
     * 任务失败（如目标消失、验证失败等）
     */
    public void failTask(UUID maidUUID, String reason) {
        String taskId = tasksByMaid.remove(maidUUID);
        if (taskId == null) return;
        TaskInfo task = tasks.remove(taskId);
        if (task != null) {
            Set<String> targetTasks = tasksByTarget.get(task.targetPos);
            if (targetTasks != null) {
                targetTasks.remove(taskId);
                if (targetTasks.isEmpty()) {
                    tasksByTarget.remove(task.targetPos);
                }
            }
            task.status = TaskStatus.FAILED;
            long duration = currentTick - task.createTime;
            long sinceHeartbeat = currentTick - task.lastHeartbeat;
            MaidRestaurantBusiness.LOGGER.warn("任务失败: id={} 类型={} 状态={} 女仆={} 目标={} 原因={} 已运行={}tick 最后心跳={}tick前", 
                taskId, task.taskType, task.status, maidUUID, task.targetPos, reason, duration, sinceHeartbeat);
            // 自动释放关联的厨具占用（烹饪任务的targetPos就是厨具位置）
            if (task.taskType.equals(TYPE_COOKING) && task.targetPos != null) {
                releaseDevice(task.targetPos);
            }
        }
    }

    /**
     * 检查某个位置是否有指定类型的任务在执行
     */
    public boolean hasTaskAt(BlockPos targetPos, String taskType) {
        Set<String> targetTasks = tasksByTarget.get(targetPos);
        if (targetTasks == null) return false;
        for (String taskId : targetTasks) {
            TaskInfo task = tasks.get(taskId);
            if (task != null && task.taskType.equals(taskType) &&
                (task.status == TaskStatus.PENDING || task.status == TaskStatus.ASSIGNED || task.status == TaskStatus.IN_PROGRESS)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取女仆当前正在执行的任务
     */
    public TaskInfo getMaidTask(UUID maidUUID) {
        String taskId = tasksByMaid.get(maidUUID);
        if (taskId == null) return null;
        return tasks.get(taskId);
    }

    /**
     * 检查女仆是否有任务在执行
     * 注意：会自动清理卡住超过超时时间1.5倍的任务，避免女仆永远无法分配新任务
     */
    /**
     * 获取女仆当前任务ID（调试用）
     */
    public String getMaidTaskId(UUID maidUUID) {
        return tasksByMaid.get(maidUUID);
    }

    public boolean hasMaidTask(UUID maidUUID) {
        // 先检查是否有卡住的任务，如果有就强制释放
        String taskId = tasksByMaid.get(maidUUID);
        if (taskId != null) {
            TaskInfo task = tasks.get(taskId);
            if (task != null && task.lastHeartbeat > 0 && currentTick > 0) {
                long timeout = TIMEOUT_TICKS.getOrDefault(task.taskType, 1200L);
                long stuckThreshold = (long)(timeout * 1.5); // 超过超时时间1.5倍视为卡住
                if (currentTick - task.lastHeartbeat > stuckThreshold) {
                    // 方案2：检查女仆是否真的在烹饪（有CookRequest或坐在椅子上）
                    // 如果女仆正在烹饪，就不取消任务，而是更新心跳，避免误杀正常烹饪
                    if (task.taskType.equals(TYPE_COOKING) && isMaidActuallyCooking(maidUUID)) {
                        task.lastHeartbeat = currentTick;
                        return true;
                    }
                    MaidRestaurantBusiness.LOGGER.warn("TaskManager: 女仆 {} 的任务 {} 卡住超过 {} tick（阈值{}），强制释放",
                        maidUUID, taskId, currentTick - task.lastHeartbeat, stuckThreshold);
                    failTask(maidUUID, "任务卡住，强制释放");
                    return false;
                }
            }
        }
        return tasksByMaid.containsKey(maidUUID);
    }

    /**
     * 检查女仆是否真的在烹饪（有CookRequest或坐在椅子上）
     * 用于避免卡住检测误杀正常烹饪的女仆
     */
    private boolean isMaidActuallyCooking(UUID maidUUID) {
        if (serverLevel == null) return false;
        try {
            Entity entity = serverLevel.getEntity(maidUUID);
            if (!(entity instanceof EntityMaid)) return false;
            EntityMaid maid = (EntityMaid) entity;
            // 检查是否有CookRequest
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            if (request != null) {
                return true;
            }
            // 检查是否坐在椅子上（isPassenger表示正在乘坐某个实体，比如椅子）
            if (maid.isPassenger()) {
                return true;
            }
            return false;
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("[TaskManager] 检查女仆烹饪状态异常", t);
            return false;
        }
    }

    /**
     * 获取女仆当前的任务数（用于负载均衡）
     */
    public int getMaidTaskCount(UUID maidUUID) {
        return hasMaidTask(maidUUID) ? 1 : 0;
    }

    // ========== 厨具占用管理方法 ==========

    /**
     * 标记厨具为被占用
     * @param devicePos 厨具位置
     * @param taskId 关联的任务ID
     * @param maidUUID 占用的女仆UUID
     * @return 是否成功占用（如果已被占用返回false）
     */
    public boolean occupyDevice(BlockPos devicePos, String taskId, UUID maidUUID) {
        if (devicePos == null) return false;
        DeviceOccupancyInfo existing = occupiedDevices.get(devicePos);
        if (existing != null) {
            // 已被占用，检查是否是同一个任务/女仆
            if (taskId != null && taskId.equals(existing.taskId)) {
                // 同一个任务，允许重新占用
                return true;
            }
            MaidRestaurantBusiness.LOGGER.warn("[TaskManager厨具] 厨具 {} 已被任务 {} 女仆 {} 占用，拒绝任务 {} 女仆 {} 的占用请求",
                devicePos, existing.taskId, existing.maidUUID, taskId, maidUUID);
            return false;
        }
        occupiedDevices.put(devicePos, new DeviceOccupancyInfo(devicePos, taskId, maidUUID, currentTick));
        return true;
    }

    /**
     * 释放厨具占用
     * @param devicePos 厨具位置
     */
    public void releaseDevice(BlockPos devicePos) {
        if (devicePos == null) return;
        DeviceOccupancyInfo removed = occupiedDevices.remove(devicePos);
        if (removed != null) {
            long occupyDuration = currentTick - removed.occupyTime;
        }
    }

    /**
     * 检查厨具是否被占用
     * @param devicePos 厨具位置
     * @return 是否被占用
     */
    public boolean isDeviceOccupied(BlockPos devicePos) {
        if (devicePos == null) return false;
        return occupiedDevices.containsKey(devicePos);
    }

    /**
     * 获取当前被占用的厨具数量
     */
    public int getOccupiedDeviceCount() {
        return occupiedDevices.size();
    }
    
    /**
     * 统计指定厨具类型的活跃烹饪任务数量
     * 通过检查烹饪任务的目标位置的BlockEntity类型来判断
     * @param deviceType 厨具类型（如"Stockpot"、"Pot"等）
     * @param level 服务端世界
     * @return 活跃烹饪任务数量
     */
    /**
     * 统计指定打单机的指定厨具类型的活跃烹饪任务数量（按打单机隔离）
     * @param deviceType 厨具类型
     * @param machinePos 打单机位置
     * @param level 服务端世界
     * @return 活跃烹饪任务数量
     */
    public int getActiveCookingTaskCountByDeviceType(String deviceType, BlockPos machinePos, ServerLevel level) {
        int count = 0;
        for (TaskInfo task : tasks.values()) {
            // 只统计烹饪任务
            if (!task.taskType.equals(TYPE_COOKING)) continue;
            // 按打单机隔离：只统计同一打单机的任务
            if (machinePos != null && task.machinePos != null && !task.machinePos.equals(machinePos)) continue;
            // 统计待分配、已分配和进行中的任务（包括刚创建还没分配的PENDING任务）
            if (task.status != TaskStatus.PENDING && task.status != TaskStatus.ASSIGNED && task.status != TaskStatus.IN_PROGRESS) continue;
            // 使用任务记录的deviceType字段（创建任务时设置），避免通过targetPos重新判断导致不准确
            if (task.deviceType != null && deviceType.equals(task.deviceType)) {
                count++;
            }
        }
        return count;
    }

    /**
     * 统计全局指定厨具类型的活跃烹饪任务数量（不按打单机隔离，向后兼容）
     */
    public int getActiveCookingTaskCountByDeviceType(String deviceType, ServerLevel level) {
        return getActiveCookingTaskCountByDeviceType(deviceType, null, level);
    }

    /**
     * 验证并清理超时的厨具占用（自愈机制）
     * 在tick方法中调用
     */
    /**
     * 清理ASSIGNED状态超时的任务
     * 如果任务处于ASSIGNED状态超过ASSIGNED_TIMEOUT，说明女仆没有开始交互，自动失败重新分配
     */
    private void cleanupAssignedTimeoutTasks() {
        List<String> toFail = new ArrayList<>();
        for (TaskInfo task : tasks.values()) {
            if (task.status == TaskStatus.ASSIGNED && task.assignedMaid != null) {
                long assignedDuration = currentTick - task.assignTime;
                if (assignedDuration > getAssignedTimeout()) {
                    // 重要：如果是烹饪任务，检查女仆是否有CookRequest
                    // 如果女仆有CookRequest，说明她正在烹饪流程中（可能在拿食材、接水等），
                    // 只是还没有进入MaidCookingTask.start阶段，不应该超时失败
                    // 延长超时时间（更新assignTime），给女仆更多时间完成准备工作
                    if (task.taskType.equals(TYPE_COOKING) && serverLevel != null) {
                        try {
                            Entity entity = serverLevel.getEntity(task.assignedMaid);
                            if (entity instanceof EntityMaid) {
                                EntityMaid maid = (EntityMaid) entity;
                                CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
                                if (request != null) {
                                    // 女仆有CookRequest，正在烹饪流程中，延长超时时间
                                    task.assignTime = currentTick;
                                    task.lastHeartbeat = currentTick;
                                    continue;
                                }
                            }
                        } catch (Throwable t) {
                            MaidRestaurantBusiness.LOGGER.error("[TaskManager] 检查烹饪任务女仆CookRequest异常", t);
                        }
                    }
                    toFail.add(task.taskId);
                    MaidRestaurantBusiness.LOGGER.warn("[TaskManager] ASSIGNED任务超时: id={} 类型={} 女仆={} 分配时长={}tick（阈值{}），自动失败重新分配",
                        task.taskId, task.taskType, task.assignedMaid, assignedDuration, getAssignedTimeout());
                }
            }
        }
        for (String taskId : toFail) {
            TaskInfo task = tasks.get(taskId);
            if (task != null && task.assignedMaid != null) {
                failTask(task.assignedMaid, "ASSIGNED状态超时，女仆未开始交互");
            }
        }
    }

    /**
     * 检测烹饪完成但任务未正常结束的情况
     * 女仆餐厅行为树可能在烹饪完成后直接移除MaidCookingTask而不调用stop方法，
     * 导致completeTask没有被执行，任务状态一直卡在IN_PROGRESS，厨具也一直被占用。
     * 检测条件：IN_PROGRESS的烹饪任务 + 女仆已经没有CookRequest + 心跳停止超过100tick
     */
    private void cleanupCompletedCookingTasks(ServerLevel level) {
        List<UUID> toComplete = new ArrayList<>();
        for (TaskInfo task : tasks.values()) {
            if (!task.taskType.equals(TYPE_COOKING)) continue;
            if (task.status != TaskStatus.IN_PROGRESS) continue;
            if (task.assignedMaid == null) continue;
            // 心跳停止超过100tick（5秒），说明MaidCookingTask已经被行为树移除
            if (currentTick - task.lastHeartbeat <= 100L) continue;
            // 检查女仆是否已经没有CookRequest了
            try {
                Entity entity = level.getEntity(task.assignedMaid);
                if (!(entity instanceof EntityMaid)) continue;
                EntityMaid maid = (EntityMaid) entity;
                CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
                if (request != null) {
                    // 女仆还有CookRequest，说明还在烹饪中，不处理
                    // 但是更新心跳，避免被卡住检测误杀
                    task.lastHeartbeat = currentTick;
                    continue;
                }
                // 女仆已经没有CookRequest了，说明烹饪已经完成，主动完成任务
                toComplete.add(task.assignedMaid);
                MaidRestaurantBusiness.LOGGER.warn("[TaskManager] 检测到烹饪完成但任务未正常结束，主动完成任务: id={} 女仆={} 目标={} 心跳停止={}tick",
                    task.taskId, task.assignedMaid, task.targetPos, currentTick - task.lastHeartbeat);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("[TaskManager] 检测烹饪完成任务异常", t);
            }
        }
        for (UUID maidUUID : toComplete) {
            completeTask(maidUUID);
        }
    }

    private void cleanupTimeoutDevices() {
        List<BlockPos> toRelease = new ArrayList<>();
        for (Map.Entry<BlockPos, DeviceOccupancyInfo> entry : occupiedDevices.entrySet()) {
            DeviceOccupancyInfo info = entry.getValue();
            // 检查关联的任务是否还存在
            TaskInfo task = info.taskId != null ? tasks.get(info.taskId) : null;
            boolean taskExists = task != null;
            // 如果任务存在且正在执行中（IN_PROGRESS），不释放厨具
            // 因为烹饪等任务可能需要很长时间，心跳会持续更新
            boolean taskInProgress = taskExists && task.status == TaskStatus.IN_PROGRESS;
            // 检查是否超时（只有任务不存在或不在执行中时才检查超时）
            boolean timeout = !taskInProgress && (currentTick - info.occupyTime > DEVICE_OCCUPY_TIMEOUT);

            if (!taskExists || timeout) {
                toRelease.add(entry.getKey());
                MaidRestaurantBusiness.LOGGER.warn("[TaskManager厨具] 厨具 {} 占用超时或任务不存在，强制释放（任务存在={} 执行中={} 超时={} 占用时长={}tick）",
                    entry.getKey(), taskExists, taskInProgress, timeout, currentTick - info.occupyTime);
            }
        }
        for (BlockPos pos : toRelease) {
            occupiedDevices.remove(pos);
        }
        if (!toRelease.isEmpty()) {
        }
    }

    /**
     * 获取当前tick计数（供外部Mixin调用更新心跳）
     */
    public long getCurrentTick() {
        return currentTick;
    }

    /**
     * 智能任务分配：找到最适合执行任务的女仆
     * 综合考虑：距离（权重70%）+ 负载均衡（权重30%）
     * @param maids 候选女仆列表
     * @param targetPos 任务目标位置
     * @return 最适合的女仆，如果没有可用女仆返回null
     */
    public com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid findBestMaidForTask(
            List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> maids,
            BlockPos targetPos) {
        if (maids == null || maids.isEmpty()) return null;

        com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid bestMaid = null;
        double bestScore = Double.MAX_VALUE;

        // 计算最大距离，用于归一化
        double maxDist = 1.0;
        for (com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid : maids) {
            double dist = maid.blockPosition().distSqr(targetPos);
            if (dist > maxDist) maxDist = dist;
        }

        for (com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid : maids) {
            // 跳过有任务的女仆
            if (hasMaidTask(maid.getUUID())) continue;

            // 计算距离分数（0-1，越小越好）
            double dist = maid.blockPosition().distSqr(targetPos);
            double distScore = dist / maxDist;

            // 计算负载分数（0-1，越小越好）
            // 当前实现中每个女仆最多一个任务，所以负载分数就是0或1
            // 预留接口，后续可以支持多任务队列
            double loadScore = getMaidTaskCount(maid.getUUID());

            // 综合评分：距离权重70%，负载权重30%
            double score = distScore * 0.7 + loadScore * 0.3;

            if (score < bestScore) {
                bestScore = score;
                bestMaid = maid;
            }
        }

        return bestMaid;
    }

    /**
     * 获取可用女仆列表（没有任务在执行的女仆），按距离排序
     */
    public List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> getAvailableMaids(
            List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> maids,
            BlockPos targetPos) {
        List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> available = new ArrayList<>();
        for (com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid : maids) {
            if (!hasMaidTask(maid.getUUID())) {
                available.add(maid);
            }
        }
        // 按距离排序
        available.sort((a, b) -> Double.compare(
            a.blockPosition().distSqr(targetPos),
            b.blockPosition().distSqr(targetPos)
        ));
        return available;
    }

    /**
     * 超时检测和异常处理（每10tick调用一次）
     */
    public void tick(long currentTick, ServerLevel level) {
        this.currentTick = currentTick;
        this.serverLevel = level;
        if (currentTick - lastCheckTick < CHECK_INTERVAL) return;
        lastCheckTick = currentTick;

        // 每200tick（10秒）输出一次所有烹饪任务的状态，方便排查卡住问题
        if (currentTick % 200L == 0L) {
            int cookingCount = 0;
            for (TaskInfo task : tasks.values()) {
                if (task.taskType.equals(TYPE_COOKING) && 
                    (task.status == TaskStatus.PENDING || task.status == TaskStatus.ASSIGNED || task.status == TaskStatus.IN_PROGRESS)) {
                    cookingCount++;
                    long age = currentTick - task.createTime;
                    long sinceHeartbeat = currentTick - task.lastHeartbeat;
                }
            }
            if (cookingCount > 0) {
            }
        }

        // 自动接单：集成到TaskManager中，每10tick检查一次，少一次监测
        if (businessManager != null) {
            try {
                OrderBridge.tickOrders(level, businessManager);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("TaskManager: 自动接单异常", t);
            }
        }

        // 更新厨具统计（按打单机隔离）
        try {
            Set<BlockPos> activatedMachines = ActivationCache.getActivatedMachines(level);
            for (BlockPos machinePos : activatedMachines) {
                if (machinePos != null) {
                    CookingDeviceStatsManager.getInstance().updateStation(level, machinePos, currentTick);
                }
            }
            // 清理非激活打单机的统计数据
            CookingDeviceStatsManager.getInstance().cleanupInactiveStations(activatedMachines, currentTick);
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("TaskManager: 厨具统计更新异常", e);
        }

        // 每200tick（10秒）输出一次任务统计信息
        if (currentTick % 200L == 0L) {
            int pending = getPendingTaskCount();
            int active = getActiveTaskCount();
            int total = tasks.size();
        }

        // 清理ASSIGNED状态超时的任务（女仆未开始交互，自动失败重新分配）
        cleanupAssignedTimeoutTasks();
        // 检测烹饪完成但任务未正常结束的情况（女仆餐厅行为树可能直接移除MaidCookingTask而不调用stop）
        cleanupCompletedCookingTasks(level);
        // 清理超时的厨具占用（自愈机制）
        cleanupTimeoutDevices();

        List<String> toRemove = new ArrayList<>();
        List<TaskInfo> toReassign = new ArrayList<>();

        for (TaskInfo task : tasks.values()) {
            if (task.status == TaskStatus.COMPLETED || task.status == TaskStatus.FAILED) {
                toRemove.add(task.taskId);
                continue;
            }

            // PENDING任务超时检测：如果超过30秒没有女仆领取，自动移除
            if (task.status == TaskStatus.PENDING) {
                if (currentTick - task.createTime > PENDING_TIMEOUT) {
                    MaidRestaurantBusiness.LOGGER.warn("TaskManager: PENDING任务 {} 类型={} 超时（{}tick无女仆领取），自动移除",
                        task.taskId, task.taskType, currentTick - task.createTime);
                    toRemove.add(task.taskId);
                }
                continue;
            }

            // 激进卡住检测：基于任务创建时间，即使心跳一直在更新，只要超过超时时间的3倍就强制释放
            // 这是为了处理心跳正常但任务实际卡住的情况（比如女仆一直在移动但永远到不了目标）
            long timeout = TIMEOUT_TICKS.getOrDefault(task.taskType, 1200L);
            long hardStuckThreshold = timeout * 3;
            if (task.createTime > 0 && currentTick - task.createTime > hardStuckThreshold) {
                MaidRestaurantBusiness.LOGGER.warn("TaskManager: 任务 {} 类型={} 激进卡住检测触发（创建后{}tick超过阈值{}），强制释放，分配女仆={}",
                    task.taskId, task.taskType, currentTick - task.createTime, hardStuckThreshold, task.assignedMaid);
                // 强制释放
                if (task.assignedMaid != null) {
                    tasksByMaid.remove(task.assignedMaid);
                    // 重置卡住的女仆状态
                    Entity maidEntity = level.getEntity(task.assignedMaid);
                    if (maidEntity != null && maidEntity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid) {
                        MaidUtils.resetMaidState(level, (com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid) maidEntity);
                    }
                }
                // 释放厨具占用
                if (task.taskType.equals(TYPE_COOKING) && task.targetPos != null) {
                    releaseDevice(task.targetPos);
                }
                toRemove.add(task.taskId);
                continue;
            }

            // 检查超时（基于心跳）
            if (currentTick - task.lastHeartbeat > timeout) {
                MaidRestaurantBusiness.LOGGER.warn("TaskManager: 任务 {} 类型={} 超时（{}tick无心跳），重试次数={}",
                    task.taskId, task.taskType, currentTick - task.lastHeartbeat, task.retryCount);

                if (task.retryCount < MAX_RETRIES) {
                    // 重试：让原女仆重新执行
                    task.retryCount++;
                    task.status = TaskStatus.PENDING;
                    if (task.assignedMaid != null) {
                        tasksByMaid.remove(task.assignedMaid);
                    }
                    task.assignedMaid = null;
                    task.assignTime = 0;
                    task.lastHeartbeat = 0;
                    toReassign.add(task);
                } else {
                    // 重试次数用完，标记失败并重新分配（回到PENDING，让其他女仆尝试）
                    task.status = TaskStatus.PENDING;
                    task.retryCount = 0; // 重置重试次数，让新女仆有机会尝试
                    if (task.assignedMaid != null) {
                        tasksByMaid.remove(task.assignedMaid);
                        // 重置卡住的女仆状态
                        Entity maidEntity = level.getEntity(task.assignedMaid);
                        if (maidEntity != null && maidEntity instanceof com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid) {
                            MaidUtils.resetMaidState(level, (com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid) maidEntity);
                        }
                    }
                    task.assignedMaid = null;
                    task.assignTime = 0;
                    task.lastHeartbeat = 0;
                }
            }
        }

        // 清理已完成/失败的任务
        for (String taskId : toRemove) {
            TaskInfo task = tasks.remove(taskId);
            if (task != null) {
                Set<String> targetTasks = tasksByTarget.get(task.targetPos);
                if (targetTasks != null) {
                    targetTasks.remove(taskId);
                    if (targetTasks.isEmpty()) {
                        tasksByTarget.remove(task.targetPos);
                    }
                }
            }
        }

        // 重新分配的任务会在下一次assignTask调用时被分配
    }

    /**
     * 获取所有待处理任务数量
     */
    public int getPendingTaskCount() {
        int count = 0;
        for (TaskInfo task : tasks.values()) {
            if (task.status == TaskStatus.PENDING) count++;
        }
        return count;
    }

    /**
     * 获取所有进行中任务数量
     */
    public int getActiveTaskCount() {
        int count = 0;
        for (TaskInfo task : tasks.values()) {
            if (task.status == TaskStatus.ASSIGNED || task.status == TaskStatus.IN_PROGRESS) count++;
        }
        return count;
    }

    /**
     * 获取缓存的女仆列表（中心化检索，避免各个Bridge重复获取所有女仆）
     * 每10tick更新一次缓存
     */
    public List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> getCachedMaids(ServerLevel level) {
        if (currentTick - lastMaidCacheTick >= MAID_CACHE_INTERVAL || cachedMaids.isEmpty()) {
            // 以激活的打单机为中心搜索女仆，避免以(0,0,0)为中心导致远距离打单机的女仆被遗漏
            Set<BlockPos> activatedMachines = ActivationCache.getActivatedMachines(level);
            Set<UUID> maidUUIDs = new HashSet<>();
            List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> result = new ArrayList<>();
            int range = BusinessConfig.dishScanRange;
            int yRange = 32;
            
            if (activatedMachines.isEmpty()) {
                BlockPos spawn = level.getSharedSpawnPos();
                result = level.getEntitiesOfClass(
                    com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class,
                    new AABB(spawn.getX() - 2000, spawn.getY() - 256, spawn.getZ() - 2000,
                        spawn.getX() + 2000, spawn.getY() + 256, spawn.getZ() + 2000)
                );
            } else {
                for (BlockPos machinePos : activatedMachines) {
                    if (machinePos == null) continue;
                    AABB aabb = new AABB(
                        machinePos.getX() - range, machinePos.getY() - yRange, machinePos.getZ() - range,
                        machinePos.getX() + range, machinePos.getY() + yRange, machinePos.getZ() + range
                    );
                    List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> maids = 
                        level.getEntitiesOfClass(com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid.class, aabb);
                    for (com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid : maids) {
                        if (maid != null && maid.isAlive() && maidUUIDs.add(maid.getUUID())) {
                            result.add(maid);
                        }
                    }
                }
            }
            cachedMaids = result;
            lastMaidCacheTick = currentTick;
        }
        return cachedMaids;
    }
    
    /**
     * 获取指定打单机周围的缓存女仆列表（避免女仆跑到别的打单机去工作）
     */
    public List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> getCachedMaidsForMachine(ServerLevel level, BlockPos machinePos) {
        List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> allMaids = getCachedMaids(level);
        List<com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid> result = new ArrayList<>();
        int range = BusinessConfig.dishScanRange;
        int rangeSqr = range * range;
        for (com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid maid : allMaids) {
            if (maid == null || !maid.isAlive()) continue;
            if (maid.blockPosition().distSqr(machinePos) <= rangeSqr) {
                result.add(maid);
            }
        }
        return result;
    }

    /**
     * 获取缓存的带餐盘的操作台列表（中心化检索，避免每个女仆重复遍历区块）
     * 每10tick更新一次缓存
     * 注意：当前版本暂时返回空列表，由DeliveryBridge使用自己的检索逻辑
     * 后续版本会实现真正的中心化检索
     */
    public List<BlockPos> getCachedCountersWithPlates(ServerLevel level) {
        // 暂时返回空列表，由DeliveryBridge使用自己的检索逻辑
        // 后续版本会实现真正的中心化检索
        return new ArrayList<>();
    }

    /**
     * 清理所有任务（用于世界卸载时）
     */
    public void clearAll() {
        tasks.clear();
        tasksByTarget.clear();
        tasksByMaid.clear();
        cachedMaids.clear();
        cachedCountersWithPlates.clear();
    }
}
