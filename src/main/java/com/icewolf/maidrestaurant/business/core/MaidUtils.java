/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid
 *  net.minecraft.core.BlockPos
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.Mob
 *  net.minecraft.world.entity.ai.Brain
 *  net.minecraft.world.entity.ai.memory.MemoryModuleType
 *  net.minecraft.world.entity.ai.navigation.PathNavigation
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.phys.AABB
 *  net.minecraftforge.items.IItemHandler
 *  net.minecraftforge.items.ItemHandlerHelper
 *  net.minecraftforge.registries.ForgeRegistries
 */
package com.icewolf.maidrestaurant.business.core;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

public class MaidUtils {
    private static Method GET_X;
    private static Method GET_Y;
    private static Method GET_Z;
    private static Method NAV_GET;
    private static Method NAV_MOVE_TO;
    private static Method GET_AVAILABLE_INV;
    private static Method GET_TASK;
    private static boolean init;
    public static final String TASK_COOK = "maid_restaurant:cook";
    public static final String TASK_WAITER = "maid_restaurant:waiter";
    private static final Set<EntityMaid> occupiedMaids;
    
    // 女仆任务跟踪（用于卡住自愈和员工人数统计）
    private static final Map<UUID, MaidTaskInfo> taskTracker = new HashMap<>();
    
    // 女仆与打单机的绑定关系（通过健康证或公示栏绑定）
    private static final Map<UUID, BlockPos> maidBindings = new HashMap<>();
    // 绑定来源记录（用于调试和去重）
    private static final Map<UUID, String> maidBindingSources = new HashMap<>();
    
    public static class MaidTaskInfo {
        public long startTime;
        public double startX, startY, startZ;
        public BlockPos machinePos; // 所属打单机
        public String taskType; // "delivery", "cooking", "packaging", "dishwashing"
        
        public MaidTaskInfo(long startTime, double x, double y, double z, BlockPos machinePos, String taskType) {
            this.startTime = startTime;
            this.startX = x;
            this.startY = y;
            this.startZ = z;
            this.machinePos = machinePos;
            this.taskType = taskType;
        }
    }

    private static void init() {
        if (init) {
            return;
        }
        try {
            GET_X = Entity.class.getDeclaredMethod("getX", new Class[0]);
            GET_X.setAccessible(true);
            GET_Y = Entity.class.getDeclaredMethod("getY", new Class[0]);
            GET_Y.setAccessible(true);
            GET_Z = Entity.class.getDeclaredMethod("getZ", new Class[0]);
            GET_Z.setAccessible(true);
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("MaidUtils: position methods failed: {}", t.toString());
        }
        try {
            NAV_GET = Mob.class.getDeclaredMethod("getNavigation", new Class[0]);
            NAV_GET.setAccessible(true);
            NAV_MOVE_TO = PathNavigation.class.getDeclaredMethod("moveTo", Double.TYPE, Double.TYPE, Double.TYPE, Double.TYPE);
            NAV_MOVE_TO.setAccessible(true);
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("MaidUtils: navigation failed: {}", t.toString());
        }
        try {
            for (Method m : EntityMaid.class.getMethods()) {
                if (!m.getName().equals("getAvailableInv")) continue;
                GET_AVAILABLE_INV = m;
                break;
            }
            for (Method m : EntityMaid.class.getMethods()) {
                if (!m.getName().equals("getTask") || m.getParameterCount() != 0) continue;
                GET_TASK = m;
                break;
            }
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("MaidUtils: maid methods failed: {}", t.toString());
        }
        init = true;
    }

    public static double getX(Entity e) {
        MaidUtils.init();
        try {
            return (Double)GET_X.invoke(e, new Object[0]);
        }
        catch (Throwable t) {
            return e.getX();
        }
    }

    public static double getY(Entity e) {
        MaidUtils.init();
        try {
            return (Double)GET_Y.invoke(e, new Object[0]);
        }
        catch (Throwable t) {
            return e.getY();
        }
    }

    public static double getZ(Entity e) {
        MaidUtils.init();
        try {
            return (Double)GET_Z.invoke(e, new Object[0]);
        }
        catch (Throwable t) {
            return e.getZ();
        }
    }

    public static String getTaskUid(EntityMaid maid) {
        MaidUtils.init();
        try {
            Object task = GET_TASK.invoke(maid, new Object[0]);
            if (task == null) {
                return null;
            }
            Method getUid = task.getClass().getMethod("getUid", new Class[0]);
            Object uid = getUid.invoke(task, new Object[0]);
            return uid != null ? uid.toString() : null;
        }
        catch (Throwable t) {
            return null;
        }
    }

    public static boolean isCookMaid(EntityMaid maid) {
        return TASK_COOK.equals(MaidUtils.getTaskUid(maid));
    }

    public static boolean isWaiterMaid(EntityMaid maid) {
        return TASK_WAITER.equals(MaidUtils.getTaskUid(maid));
    }

    public static IItemHandler getInventory(EntityMaid maid) {
        MaidUtils.init();
        try {
            Object inv = GET_AVAILABLE_INV.invoke(maid, true);
            if (inv instanceof IItemHandler) {
                IItemHandler handler = (IItemHandler)inv;
                return handler;
            }
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("MaidUtils: getInventory failed", t);
        }
        return null;
    }

    public static boolean isNear(EntityMaid maid, BlockPos target, double range) {
        double dz;
        double dy;
        double dx = MaidUtils.getX((Entity)maid) - ((double)target.getX() + 0.5);
        return dx * dx + (dy = MaidUtils.getY((Entity)maid) - (double)target.getY()) * dy + (dz = MaidUtils.getZ((Entity)maid) - ((double)target.getZ() + 0.5)) * dz < range * range;
    }

    public static boolean moveTo(EntityMaid maid, BlockPos target, double speed) {
        try {
            return maid.getNavigation().moveTo((double)target.getX() + 0.5, target.getY(), (double)target.getZ() + 0.5, speed);
        }
        catch (Throwable t) {
            return false;
        }
    }

    public static boolean moveToSide(EntityMaid maid, BlockPos target, double speed) {
        try {
            double sz;
            double sx;
            double mx = maid.getX();
            double mz = maid.getZ();
            double tx = (double)target.getX() + 0.5;
            double tz = (double)target.getZ() + 0.5;
            double dx = mx - tx;
            double dz = mz - tz;
            if (Math.abs(dx) > Math.abs(dz)) {
                sx = dx > 0.0 ? (double)target.getX() + 1.5 : (double)target.getX() - 0.5;
                sz = tz;
            } else {
                sx = tx;
                sz = dz > 0.0 ? (double)target.getZ() + 1.5 : (double)target.getZ() - 0.5;
            }
            return maid.getNavigation().moveTo(sx, target.getY(), sz, speed);
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("MaidUtils: moveToSide failed to {}", target, t);
            return false;
        }
    }

    public static void resetMaidState(ServerLevel level, EntityMaid maid) {
        try {
            // 1. 从椅子上下来
            try {
                Class<?> behaviorUtils = Class.forName("com.mastermarisa.maid_restaurant.utils.BehaviorUtils");
                java.lang.reflect.Method stopRide = behaviorUtils.getMethod("stopRide", net.minecraft.world.level.Level.class, EntityMaid.class);
                stopRide.invoke(null, level, maid);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("resetMaidState: stopRide failed", t);
            }
            // 2. 清除目标位置
            try {
                Class<?> behaviorUtils = Class.forName("com.mastermarisa.maid_restaurant.utils.BehaviorUtils");
                java.lang.reflect.Method eraseTargetPos = behaviorUtils.getMethod("eraseTargetPos", net.minecraft.world.entity.LivingEntity.class);
                eraseTargetPos.invoke(null, maid);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("resetMaidState: eraseTargetPos failed", t);
            }
            // 3. 清除大脑记忆
            try {
                Brain<?> brain = maid.getBrain();
                brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                brain.eraseMemory(MemoryModuleType.PATH);
                brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("resetMaidState: erase brain memories failed", t);
            }
            // 4. 用反射清除女仆餐厅的记忆模块（CHAIR_POS, CACHED_WORK_BLOCK）
            try {
                Class<?> modEntities = Class.forName("com.mastermarisa.maid_restaurant.init.ModEntities");
                String[] memoryNames = {"CHAIR_POS", "CACHED_WORK_BLOCK"};
                for (String name : memoryNames) {
                    try {
                        java.lang.reflect.Field field = modEntities.getDeclaredField(name);
                        field.setAccessible(true);
                        Object memoryModule = field.get(null);
                        if (memoryModule instanceof MemoryModuleType) {
                            maid.getBrain().eraseMemory((MemoryModuleType<?>) memoryModule);
                            MaidRestaurantBusiness.LOGGER.info("resetMaidState: erased memory {}", name);
                        }
                    } catch (Throwable t) {
                        MaidRestaurantBusiness.LOGGER.warn("resetMaidState: erase memory {} failed", name, t);
                    }
                }
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("resetMaidState: ModEntities reflection failed", t);
            }
            // 5. 停止导航
            try {
                maid.getNavigation().stop();
            } catch (Throwable t) {}
            // 6. 释放BlockUsageManager中女仆对所有块的使用
            try {
                Class<?> blockUsageManager = Class.forName("com.mastermarisa.maid_restaurant.utils.BlockUsageManager");
                java.lang.reflect.Field blockUsageField = blockUsageManager.getDeclaredField("blockUsage");
                blockUsageField.setAccessible(true);
                Object blockUsageMap = blockUsageField.get(null);
                if (blockUsageMap instanceof java.util.Map) {
                    java.util.Map<?, ?> map = (java.util.Map<?, ?>) blockUsageMap;
                    UUID maidUUID = maid.getUUID();
                    int released = 0;
                    for (Object key : new ArrayList<>(map.keySet())) {
                        try {
                            Long posLong = (Long) key;
                            BlockPos pos = BlockPos.of(posLong);
                            java.lang.reflect.Method removeUser = blockUsageManager.getMethod("removeUser", BlockPos.class, UUID.class);
                            removeUser.invoke(null, pos, maidUUID);
                            released++;
                        } catch (Throwable t) {}
                    }
                    if (released > 0) {
                        MaidRestaurantBusiness.LOGGER.info("resetMaidState: 释放女仆对{}个块的使用", released);
                    }
                }
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("resetMaidState: release BlockUsageManager failed", t);
            }
            // 7. 清除女仆的所有烹饪请求（避免残留请求导致卡死）
            try {
                Class<?> requestManager = Class.forName("com.mastermarisa.maid_restaurant.utils.RequestManager");
                Class<?> cookRequestHandlerClass = Class.forName("com.mastermarisa.maid_restaurant.request.CookRequestHandler");
                java.lang.reflect.Method getOrCreate = cookRequestHandlerClass.getMethod("getOrCreate", EntityMaid.class);
                Object handler = getOrCreate.invoke(null, maid);
                if (handler != null) {
                    java.lang.reflect.Method sizeMethod = handler.getClass().getMethod("size");
                    int size = (Integer) sizeMethod.invoke(handler);
                    if (size > 0) {
                        for (int i = size - 1; i >= 0; i--) {
                            try {
                                java.lang.reflect.Method removeAt = handler.getClass().getMethod("removeAt", int.class);
                                removeAt.invoke(handler, i);
                            } catch (Throwable t) {}
                        }
                        MaidRestaurantBusiness.LOGGER.info("resetMaidState: 清除女仆{}个残留烹饪请求", size);
                    }
                }
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("resetMaidState: clear cook requests failed", t);
            }
            // 8. 最彻底的重置：强制停止骑行、清除所有大脑记忆、重置AI状态
            try {
                maid.stopRiding();
            } catch (Throwable t) {}
            try {
                // 清除所有大脑记忆（用反射调用removeAllMemories）
                java.lang.reflect.Method removeAllMemories = maid.getBrain().getClass().getMethod("removeAllMemories");
                removeAllMemories.invoke(maid.getBrain());
                MaidRestaurantBusiness.LOGGER.info("resetMaidState: 已清除所有大脑记忆");
            } catch (Throwable t) {
                // 如果removeAllMemories不存在，逐个清除关键记忆
                try {
                    Brain<?> brain = maid.getBrain();
                    brain.eraseMemory(MemoryModuleType.WALK_TARGET);
                    brain.eraseMemory(MemoryModuleType.PATH);
                    brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
                    brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
                    brain.eraseMemory(MemoryModuleType.INTERACTION_TARGET);
                } catch (Throwable t2) {}
            }
            try {
                // 重置AI状态（setNoAi(true)然后false，强制AI重新评估）
                maid.setNoAi(true);
                maid.setNoAi(false);
            } catch (Throwable t) {}
            // 9. 重置女仆餐厅的任务检查速率（参考MaidCookingTask.stop），确保下一个任务能被正确检测
            try {
                Class<?> checkRateManager = Class.forName("com.mastermarisa.maid_restaurant.utils.CheckRateManager");
                java.lang.reflect.Method setNextCheckTick = checkRateManager.getMethod("setNextCheckTick", String.class, long.class);
                UUID maidUUID = maid.getUUID();
                setNextCheckTick.invoke(null, maidUUID + "ApproachCookBlock", 0);
                setNextCheckTick.invoke(null, maidUUID + "GetFromStorage", 0);
                MaidRestaurantBusiness.LOGGER.info("resetMaidState: 已重置任务检查速率");
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("resetMaidState: reset CheckRateManager failed", t);
            }
            // 10. 解除经营模组的忙碌标记（防止任务异常结束后女仆一直卡住）
            try {
                if (MaidUtils.isOccupied(maid)) {
                    MaidUtils.setOccupied(maid, false);
                    MaidRestaurantBusiness.LOGGER.info("resetMaidState: 已解除女仆 {} 的isOccupied标记", maid.getName().getString());
                }
            } catch (Throwable t) {}
            MaidRestaurantBusiness.LOGGER.info("resetMaidState: 女仆 {} 状态已彻底重置", maid.getName().getString());
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("resetMaidState: failed", t);
        }
    }

    public static boolean isOccupied(EntityMaid maid) {
        return occupiedMaids.contains(maid);
    }

    public static void setOccupied(EntityMaid maid, boolean occupied) {
        if (occupied) {
            occupiedMaids.add(maid);
        } else {
            occupiedMaids.remove(maid);
            // 清除任务跟踪
            taskTracker.remove(maid.getUUID());
        }
    }
    
    /**
     * 检查女仆是否有任务跟踪记录
     */
    public static boolean hasTaskTracker(UUID maidUUID) {
        return taskTracker.containsKey(maidUUID);
    }
    
    /**
     * 标记女仆开始执行任务（记录任务信息用于卡住检测和人数统计）
     */
    public static void startTask(EntityMaid maid, BlockPos machinePos, String taskType, long currentTick) {
        taskTracker.put(maid.getUUID(), new MaidTaskInfo(
            currentTick,
            MaidUtils.getX((Entity)maid),
            MaidUtils.getY((Entity)maid),
            MaidUtils.getZ((Entity)maid),
            machinePos,
            taskType
        ));
    }
    
    /**
     * 检测卡住的女仆并自动重置
     * @param stuckTimeoutTicks 卡住超时时间（tick，默认600=30秒）
     * @param minMoveDistance 最小移动距离（方块，小于此值视为卡住）
     * @return 被重置的女仆数量
     */
    public static int checkAndResetStuckMaids(ServerLevel level, long currentTick, int stuckTimeoutTicks, double minMoveDistance) {
        if (taskTracker.isEmpty()) return 0;
        
        int resetCount = 0;
        List<UUID> toRemove = new ArrayList<>();
        
        for (Map.Entry<UUID, MaidTaskInfo> entry : taskTracker.entrySet()) {
            UUID maidUUID = entry.getKey();
            MaidTaskInfo info = entry.getValue();
            
            // 检查任务是否超时
            if (currentTick - info.startTime < stuckTimeoutTicks) continue;
            
            // 找到女仆实体
            Entity maidEntity = null;
            for (ServerLevel lvl : level.getServer().getAllLevels()) {
                maidEntity = lvl.getEntity(maidUUID);
                if (maidEntity != null) break;
            }
            
            if (maidEntity == null || !(maidEntity instanceof EntityMaid)) {
                toRemove.add(maidUUID);
                continue;
            }
            
            EntityMaid maid = (EntityMaid) maidEntity;
            
            // 检查女仆是否移动了
            double currentX = MaidUtils.getX((Entity)maid);
            double currentY = MaidUtils.getY((Entity)maid);
            double currentZ = MaidUtils.getZ((Entity)maid);
            double moveDist = Math.sqrt(
                (currentX - info.startX) * (currentX - info.startX) +
                (currentY - info.startY) * (currentY - info.startY) +
                (currentZ - info.startZ) * (currentZ - info.startZ)
            );
            
            if (moveDist < minMoveDistance) {
                // 女仆卡住了，重置
                MaidRestaurantBusiness.LOGGER.warn("女仆卡住自愈: 女仆 {} 任务类型={} 超时{}tick 仅移动{}方块，正在重置",
                    maid.getName().getString(), info.taskType, currentTick - info.startTime, moveDist);
                MaidUtils.resetMaidState(level, maid);
                resetCount++;
            } else {
                // 女仆在移动，更新起始位置（延长检测）
                info.startX = currentX;
                info.startY = currentY;
                info.startZ = currentZ;
                info.startTime = currentTick;
            }
        }
        
        for (UUID uuid : toRemove) {
            taskTracker.remove(uuid);
        }
        
        return resetCount;
    }
    
    /**
     * 检查某台打单机是否还能接受新的女仆工作
     * 只统计附近32格内【正在工作】的绑定女仆（isOccupied=true），空闲的绑定女仆不算占用名额
     * 如果没有女仆绑定到该打单机，则回退到自动分配模式（不限制人数）
     */
    public static boolean canAcceptWorker(ServerLevel level, BlockPos machinePos) {
        int boundCount = getWorkerCountForMachine(machinePos);
        // 如果没有女仆绑定，回退到自动分配模式，不限制人数
        if (boundCount == 0) {
            return true;
        }
        int maxWorkers = ProgressionManager.getMaxWorkers(level, machinePos);
        // 只统计附近32格内【正在工作】的绑定女仆（isOccupied=true）
        int workingWorkers = 0;
        for (Map.Entry<UUID, BlockPos> entry : maidBindings.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().equals(machinePos)) continue;
            // 检查女仆实体是否在附近且正在工作
            Entity maidEntity = null;
            for (ServerLevel lvl : level.getServer().getAllLevels()) {
                maidEntity = lvl.getEntity(entry.getKey());
                if (maidEntity != null) break;
            }
            if (maidEntity == null || !(maidEntity instanceof EntityMaid)) continue;
            double dist = maidEntity.distanceToSqr(machinePos.getX() + 0.5, machinePos.getY(), machinePos.getZ() + 0.5);
            if (dist <= 1024.0 && isOccupied((EntityMaid) maidEntity)) { // 32格范围内且正在工作
                workingWorkers++;
            }
        }
        MaidRestaurantBusiness.LOGGER.info("员工检查: 打单机 {} 绑定总数={}, 附近工作中={}, 上限={}", machinePos, boundCount, workingWorkers, maxWorkers);
        return workingWorkers < maxWorkers;
    }
    
    // ==================== 女仆绑定相关方法 ====================
    
    /**
     * 将女仆绑定到打单机
     * @param source 绑定来源（"health_certificate" 或 "public_notice_board"）
     */
    public static void bindMaidToMachine(UUID maidUUID, BlockPos machinePos, String source) {
        if (maidUUID == null || machinePos == null) return;
        BlockPos existing = maidBindings.get(maidUUID);
        if (existing != null && existing.equals(machinePos)) {
            // 已经绑定到同一台打单机，更新来源
            maidBindingSources.put(maidUUID, source);
            return;
        }
        maidBindings.put(maidUUID, machinePos);
        maidBindingSources.put(maidUUID, source);
        MaidRestaurantBusiness.LOGGER.info("女仆绑定: UUID={} 绑定到打单机 {} 来源={}", maidUUID, machinePos, source);
    }
    
    /**
     * 解除女仆绑定（只有指定来源才能解除，防止健康证卸下时误删公示栏的绑定）
     */
    public static void unbindMaid(UUID maidUUID, String source) {
        if (maidUUID == null) return;
        String existingSource = maidBindingSources.get(maidUUID);
        if (existingSource != null && existingSource.equals(source)) {
            maidBindings.remove(maidUUID);
            maidBindingSources.remove(maidUUID);
            MaidRestaurantBusiness.LOGGER.info("女仆解绑: UUID={} 来源={}", maidUUID, source);
        }
    }
    
    /**
     * 获取女仆绑定的打单机位置
     */
    public static BlockPos getBoundMachine(UUID maidUUID) {
        if (maidUUID == null) return null;
        return maidBindings.get(maidUUID);
    }
    
    /**
     * 检查女仆是否绑定到指定打单机
     */
    public static boolean isMaidBoundToMachine(UUID maidUUID, BlockPos machinePos) {
        if (maidUUID == null || machinePos == null) return false;
        BlockPos bound = maidBindings.get(maidUUID);
        return bound != null && bound.equals(machinePos);
    }
    
    /**
     * 统计绑定到某打单机的女仆数量
     */
    public static int getWorkerCountForMachine(BlockPos machinePos) {
        if (machinePos == null) return 0;
        int count = 0;
        for (Map.Entry<UUID, BlockPos> entry : maidBindings.entrySet()) {
            if (entry.getValue() != null && entry.getValue().equals(machinePos)) {
                count++;
            }
        }
        return count;
    }
    
    /**
     * 清理已失效的绑定（女仆实体不存在时）
     */
    public static void cleanupInvalidBindings(ServerLevel level) {
        List<UUID> toRemove = new ArrayList<>();
        for (UUID maidUUID : maidBindings.keySet()) {
            boolean found = false;
            for (ServerLevel lvl : level.getServer().getAllLevels()) {
                if (lvl.getEntity(maidUUID) != null) {
                    found = true;
                    break;
                }
            }
            if (!found) {
                toRemove.add(maidUUID);
            }
        }
        for (UUID uuid : toRemove) {
            maidBindings.remove(uuid);
            maidBindingSources.remove(uuid);
        }
        if (!toRemove.isEmpty()) {
            MaidRestaurantBusiness.LOGGER.info("清理了 {} 个失效的女仆绑定", toRemove.size());
        }
    }
    
    /**
     * 主动从女仆饰品栏维护绑定关系（不依赖饰品的onTick调用）
     * 遍历所有女仆，检查饰品栏中是否有记录了打单机的健康证
     */
    public static void updateBindingsFromBaubles(ServerLevel level) {
        try {
            List<EntityMaid> allMaids = new ArrayList<>();
            for (ServerLevel lvl : level.getServer().getAllLevels()) {
                allMaids.addAll(lvl.getEntitiesOfClass(EntityMaid.class, new net.minecraft.world.phys.AABB(
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                )));
            }
            
            MaidRestaurantBusiness.LOGGER.info("绑定维护: 找到 {} 个女仆", allMaids.size());
            
            for (EntityMaid maid : allMaids) {
                if (maid == null || !maid.isAlive()) continue;
                UUID maidUUID = maid.getUUID();
                String maidName = maid.getName().getString();
                
                // 检查女仆饰品栏中是否有记录了打单机的健康证
                BlockPos foundMachine = null;
                int totalSlots = 0;
                int healthCertCount = 0;
                try {
                    // 方式1：通过getAvailableInv访问
                    net.minecraftforge.items.IItemHandler inv = maid.getAvailableInv(false);
                    if (inv != null) {
                        totalSlots = inv.getSlots();
                        for (int i = 0; i < inv.getSlots(); i++) {
                            ItemStack stack = inv.getStackInSlot(i);
                            if (stack.isEmpty()) continue;
                            String itemName = stack.getItem().getClass().getSimpleName();
                            if (itemName.contains("HealthCertificate") || stack.getItem() instanceof com.icewolf.maidrestaurant.business.item.HealthCertificateItem) {
                                healthCertCount++;
                                if (com.icewolf.maidrestaurant.business.item.HealthCertificateItem.hasMachine(stack)) {
                                    BlockPos machinePos = com.icewolf.maidrestaurant.business.item.HealthCertificateItem.getMachinePos(stack);
                                    if (machinePos != null) {
                                        foundMachine = machinePos;
                                        break;
                                    }
                                }
                            }
                        }
                    }
                    
                    // 方式2：如果方式1没找到，通过反射直接访问maidBauble字段
                    if (foundMachine == null) {
                        try {
                            java.lang.reflect.Field baubleField = EntityMaid.class.getDeclaredField("maidBauble");
                            baubleField.setAccessible(true);
                            Object baubleHandler = baubleField.get(maid);
                            if (baubleHandler instanceof net.minecraftforge.items.IItemHandler) {
                                net.minecraftforge.items.IItemHandler baubleInv = (net.minecraftforge.items.IItemHandler) baubleHandler;
                                for (int i = 0; i < baubleInv.getSlots(); i++) {
                                    ItemStack stack = baubleInv.getStackInSlot(i);
                                    if (stack.isEmpty()) continue;
                                    String itemName = stack.getItem().getClass().getSimpleName();
                                    if (itemName.contains("HealthCertificate") || stack.getItem() instanceof com.icewolf.maidrestaurant.business.item.HealthCertificateItem) {
                                        healthCertCount++;
                                        if (com.icewolf.maidrestaurant.business.item.HealthCertificateItem.hasMachine(stack)) {
                                            BlockPos machinePos = com.icewolf.maidrestaurant.business.item.HealthCertificateItem.getMachinePos(stack);
                                            if (machinePos != null) {
                                                foundMachine = machinePos;
                                                break;
                                            }
                                        }
                                    }
                                }
                            }
                        } catch (Throwable reflectErr) {
                            // 反射失败，忽略
                        }
                    }
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.error("检查女仆 {} 饰品栏时异常: {}", maidName, t.toString());
                }
                
                MaidRestaurantBusiness.LOGGER.info("绑定维护: 女仆 {} ({}), 饰品栏槽位={}, 健康证数量={}, 找到打单机={}", 
                    maidName, maidUUID, totalSlots, healthCertCount, foundMachine);
                
                if (foundMachine != null) {
                    // 女仆饰品栏中有健康证，建立绑定
                    if (!isMaidBoundToMachine(maidUUID, foundMachine)) {
                        bindMaidToMachine(maidUUID, foundMachine, "health_certificate");
                    }
                } else {
                    // 女仆饰品栏中没有健康证，解除健康证来源的绑定
                    String source = maidBindingSources.get(maidUUID);
                    if ("health_certificate".equals(source)) {
                        unbindMaid(maidUUID, "health_certificate");
                    }
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("维护饰品栏绑定关系时异常", t);
        }
    }
    
    /**
     * 检测被标记为不忙碌但AI状态卡住的女仆（任务清理不彻底导致的）
     * 这种女仆isOccupied=false，但isMaidBusy=true（大脑中还有WALK_TARGET或PATH记忆）
     * @return 被重置的女仆数量
     */
    public static int checkAndResetIdleStuckMaids(ServerLevel level) {
        int resetCount = 0;
        try {
            // 遍历所有女仆
            List<EntityMaid> allMaids = new ArrayList<>();
            for (ServerLevel lvl : level.getServer().getAllLevels()) {
                allMaids.addAll(lvl.getEntitiesOfClass(EntityMaid.class, new net.minecraft.world.phys.AABB(
                    Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY,
                    Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY
                )));
            }
            
            for (EntityMaid maid : allMaids) {
                if (maid == null || !maid.isAlive()) continue;
                // 检测1：如果女仆被标记为不忙碌但AI状态卡住，重置她
                if (!isOccupied(maid)) {
                    if (isMaidBusy(maid)) {
                        MaidRestaurantBusiness.LOGGER.warn("女仆空闲卡住自愈: 女仆 {} 被标记为不忙碌但AI状态卡住，正在重置", maid.getName().getString());
                        resetMaidState(level, maid);
                        resetCount++;
                    }
                    continue;
                }
                // 检测2：女仆被标记为忙碌，但没有正在执行的任务（任务异常结束）
                // 检查女仆的持久化数据中是否有任务标记（送餐/打包/洗碗）
                boolean hasActiveTask = false;
                String activeTaskType = "none";
                try {
                    net.minecraft.nbt.CompoundTag data = maid.getPersistentData();
                    if (data.contains("BusinessDeliverCounter")) {
                        hasActiveTask = true;
                        activeTaskType = "delivery";
                    } else if (data.contains("BusinessPackCounter")) {
                        hasActiveTask = true;
                        activeTaskType = "pack";
                    } else if (data.contains("BusinessWashCounter")) {
                        hasActiveTask = true;
                        activeTaskType = "wash";
                    } else if (data.contains("BusinessCookCounter")) {
                        hasActiveTask = true;
                        activeTaskType = "cook";
                    } else if (data.contains("BusinessCollectPlate")) {
                        hasActiveTask = true;
                        activeTaskType = "collect";
                    }
                } catch (Throwable t) {}
                // 检查taskTracker中是否有记录
                boolean hasTaskTracker = taskTracker.containsKey(maid.getUUID());
                
                // 检查女仆是否在移动（如果导航在进行中，说明可能真的在工作）
                boolean isNavigating = false;
                try {
                    isNavigating = maid.getNavigation().isInProgress();
                } catch (Throwable t) {}
                
                if (!hasActiveTask && !hasTaskTracker && !isNavigating) {
                    // 女仆被标记为忙碌，但没有任何任务记录，也没有在导航，说明任务异常结束，清理忙碌标记
                    MaidRestaurantBusiness.LOGGER.warn("女仆幽灵忙碌自愈: 女仆 {} 被标记为忙碌但没有任何任务记录也没有在导航，清理忙碌标记", 
                        maid.getName().getString());
                    setOccupied(maid, false);
                    // 同时调用TaskSafetyUtils彻底重置女仆状态
                    try {
                        Class<?> safetyUtils = Class.forName("com.icewolf.maidrestaurant.business.core.TaskSafetyUtils");
                        java.lang.reflect.Method resetMethod = safetyUtils.getMethod("resetMaidState", EntityMaid.class);
                        resetMethod.invoke(null, maid);
                    } catch (Throwable t) {
                        MaidRestaurantBusiness.LOGGER.warn("女仆幽灵忙碌自愈: 调用TaskSafetyUtils.resetMaidState失败", t);
                    }
                    resetCount++;
                } else {
                    // 女仆确实有任务在执行，输出调试信息
                    if (maid.tickCount % 200 == 0) {
                        MaidRestaurantBusiness.LOGGER.info("女仆忙碌状态调试: 女仆 {} 任务类型={} 有TaskTracker={} 导航中={}", 
                            maid.getName().getString(), activeTaskType, hasTaskTracker, isNavigating);
                    }
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("检测空闲卡住女仆时异常", t);
        }
        return resetCount;
    }

    public static boolean isMaidBusy(EntityMaid maid) {
        if (MaidUtils.isOccupied(maid)) {
            return true;
        }
        try {
            Brain brain = maid.getBrain();
            MemoryModuleType walkTarget = MemoryModuleType.WALK_TARGET;
            if (brain.hasMemoryValue(walkTarget)) {
                return true;
            }
            MemoryModuleType path = MemoryModuleType.PATH;
            if (brain.hasMemoryValue(path)) {
                return true;
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
        return false;
    }

    public static EntityMaid findMaid(ServerLevel level, BlockPos center, int range, String taskUid) {
        List<EntityMaid> maids = (List)level.getEntitiesOfClass(EntityMaid.class, new AABB(center).inflate((double)range));
        EntityMaid nearest = null;
        double minDist = Double.MAX_VALUE;
        for (EntityMaid m : maids) {
            double dz;
            double dy;
            double dx;
            double dist;
            // 检查TaskManager任务：避免返回有卡住任务的女仆
            if (TaskManager.getInstance().hasMaidTask(m.getUUID())) continue;
            if (MaidUtils.isMaidBusy(m) || taskUid != null && !taskUid.equals(MaidUtils.getTaskUid(m)) || !((dist = (dx = MaidUtils.getX((Entity)m) - ((double)center.getX() + 0.5)) * dx + (dy = MaidUtils.getY((Entity)m) - (double)center.getY()) * dy + (dz = MaidUtils.getZ((Entity)m) - ((double)center.getZ() + 0.5)) * dz) < minDist)) continue;
            minDist = dist;
            nearest = m;
        }
        return nearest;
    }

    public static EntityMaid findWaiterMaid(ServerLevel level, BlockPos center, int range) {
        return MaidUtils.findMaid(level, center, range, TASK_WAITER);
    }

    public static EntityMaid findCookMaid(ServerLevel level, BlockPos center, int range) {
        return MaidUtils.findMaid(level, center, range, TASK_COOK);
    }
    
    /**
     * 查找绑定到指定打单机的侍者女仆
     * 如果没有绑定女仆，返回null（由调用方决定是否回退到自动分配）
     * 使用UUID查找女仆，避免getEntitiesOfClass在多模组环境中找不到女仆的问题
     */
    public static EntityMaid findBoundWaiterMaid(ServerLevel level, BlockPos center, int range, BlockPos machinePos) {
        if (machinePos == null) return null;
        
        // 方式1：从maidBindings中获取绑定到该打单机的所有女仆UUID，用UUID查找
        EntityMaid nearest = null;
        double minDist = Double.MAX_VALUE;
        int boundCount = 0;
        int foundByUuid = 0;
        int inRange = 0;
        int isWaiter = 0;
        int isFree = 0;
        
        for (Map.Entry<UUID, BlockPos> entry : maidBindings.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().equals(machinePos)) continue;
            boundCount++;
            UUID maidUUID = entry.getKey();
            
            // 用UUID在所有维度查找女仆
            Entity maidEntity = null;
            for (ServerLevel lvl : level.getServer().getAllLevels()) {
                maidEntity = lvl.getEntity(maidUUID);
                if (maidEntity != null) break;
            }
            if (maidEntity == null || !(maidEntity instanceof EntityMaid)) continue;
            foundByUuid++;
            
            EntityMaid m = (EntityMaid) maidEntity;
            // 检查是否在范围内
            double dx = MaidUtils.getX((Entity)m) - ((double)center.getX() + 0.5);
            double dy = MaidUtils.getY((Entity)m) - ((double)center.getY());
            double dz = MaidUtils.getZ((Entity)m) - ((double)center.getZ() + 0.5);
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist > (double)range * range) continue;
            inRange++;
            
            // 检查是否是侍者职业
            if (!TASK_WAITER.equals(MaidUtils.getTaskUid(m))) continue;
            isWaiter++;
            
            // 检查是否忙碌
            if (MaidUtils.isMaidBusy(m)) continue;
            if (TaskManager.getInstance().hasMaidTask(m.getUUID())) continue;
            isFree++;
            
            if (dist < minDist) {
                minDist = dist;
                nearest = m;
            }
        }
        
        // 方式2：如果UUID查找失败，回退到getEntitiesOfClass（兼容旧绑定）
        if (nearest == null) {
            List<EntityMaid> maids = (List)level.getEntitiesOfClass(EntityMaid.class, new AABB(center).inflate((double)range));
            for (EntityMaid m : maids) {
                if (MaidUtils.isMaidBusy(m)) continue;
                if (TaskManager.getInstance().hasMaidTask(m.getUUID())) continue;
                if (!TASK_WAITER.equals(MaidUtils.getTaskUid(m))) continue;
                if (!isMaidBoundToMachine(m.getUUID(), machinePos)) continue;
                double dx = MaidUtils.getX((Entity)m) - ((double)center.getX() + 0.5);
                double dy = MaidUtils.getY((Entity)m) - ((double)center.getY());
                double dz = MaidUtils.getZ((Entity)m) - ((double)center.getZ() + 0.5);
                double dist = dx * dx + dy * dy + dz * dz;
                if (dist < minDist) {
                    minDist = dist;
                    nearest = m;
                }
            }
        }
        
        if (nearest == null) {
            MaidRestaurantBusiness.LOGGER.info("[绑定调试] findBoundWaiterMaid: 打单机={}, 绑定总数={}, UUID找到={}, 范围内={}, 侍者职业={}, 空闲={}, 结果=null", 
                machinePos, boundCount, foundByUuid, inRange, isWaiter, isFree);
        } else {
            MaidRestaurantBusiness.LOGGER.info("[绑定调试] findBoundWaiterMaid: 找到女仆={}, 距离={}", nearest.getName().getString(), Math.sqrt(minDist));
        }
        
        return nearest;
    }
    
    /**
     * 查找绑定到指定打单机的厨师女仆
     * 使用UUID查找女仆，避免getEntitiesOfClass在多模组环境中找不到女仆的问题
     */
    public static EntityMaid findBoundCookMaid(ServerLevel level, BlockPos center, int range, BlockPos machinePos) {
        if (machinePos == null) return null;
        
        // 方式1：从maidBindings中获取绑定到该打单机的所有女仆UUID，用UUID查找
        EntityMaid nearest = null;
        double minDist = Double.MAX_VALUE;
        int boundCount = 0;
        int foundByUuid = 0;
        int inRange = 0;
        int isCook = 0;
        int isFree = 0;
        
        for (Map.Entry<UUID, BlockPos> entry : maidBindings.entrySet()) {
            if (entry.getValue() == null || !entry.getValue().equals(machinePos)) continue;
            boundCount++;
            UUID maidUUID = entry.getKey();
            
            // 用UUID在所有维度查找女仆
            Entity maidEntity = null;
            for (ServerLevel lvl : level.getServer().getAllLevels()) {
                maidEntity = lvl.getEntity(maidUUID);
                if (maidEntity != null) break;
            }
            if (maidEntity == null || !(maidEntity instanceof EntityMaid)) continue;
            foundByUuid++;
            
            EntityMaid m = (EntityMaid) maidEntity;
            // 检查是否在范围内
            double dx = MaidUtils.getX((Entity)m) - ((double)center.getX() + 0.5);
            double dy = MaidUtils.getY((Entity)m) - ((double)center.getY());
            double dz = MaidUtils.getZ((Entity)m) - ((double)center.getZ() + 0.5);
            double dist = dx * dx + dy * dy + dz * dz;
            if (dist > (double)range * range) continue;
            inRange++;
            
            // 检查是否是厨师职业
            if (!TASK_COOK.equals(MaidUtils.getTaskUid(m))) continue;
            isCook++;
            
            // 检查是否忙碌
            if (MaidUtils.isMaidBusy(m)) continue;
            if (TaskManager.getInstance().hasMaidTask(m.getUUID())) continue;
            isFree++;
            
            if (dist < minDist) {
                minDist = dist;
                nearest = m;
            }
        }
        
        // 方式2：如果UUID查找失败，回退到getEntitiesOfClass（兼容旧绑定）
        if (nearest == null) {
            List<EntityMaid> maids = (List)level.getEntitiesOfClass(EntityMaid.class, new AABB(center).inflate((double)range));
            for (EntityMaid m : maids) {
                if (MaidUtils.isMaidBusy(m)) continue;
                if (TaskManager.getInstance().hasMaidTask(m.getUUID())) continue;
                if (!TASK_COOK.equals(MaidUtils.getTaskUid(m))) continue;
                if (!isMaidBoundToMachine(m.getUUID(), machinePos)) continue;
                double dx = MaidUtils.getX((Entity)m) - ((double)center.getX() + 0.5);
                double dy = MaidUtils.getY((Entity)m) - ((double)center.getY());
                double dz = MaidUtils.getZ((Entity)m) - ((double)center.getZ() + 0.5);
                double dist = dx * dx + dy * dy + dz * dz;
                if (dist < minDist) {
                    minDist = dist;
                    nearest = m;
                }
            }
        }
        
        if (nearest == null) {
            MaidRestaurantBusiness.LOGGER.info("[绑定调试] findBoundCookMaid: 打单机={}, 绑定总数={}, UUID找到={}, 范围内={}, 厨师职业={}, 空闲={}, 结果=null", 
                machinePos, boundCount, foundByUuid, inRange, isCook, isFree);
        } else {
            MaidRestaurantBusiness.LOGGER.info("[绑定调试] findBoundCookMaid: 找到女仆={}, 距离={}", nearest.getName().getString(), Math.sqrt(minDist));
        }
        
        return nearest;
    }
    
    /**
     * 智能查找女仆：如果有绑定到该打单机的女仆，只找绑定的；否则回退到自动分配（找最近的空闲女仆）
     */
    public static EntityMaid findWaiterMaidSmart(ServerLevel level, BlockPos center, int range, BlockPos machinePos) {
        if (machinePos != null && getWorkerCountForMachine(machinePos) > 0) {
            EntityMaid bound = findBoundWaiterMaid(level, center, range, machinePos);
            if (bound != null) return bound;
            // 有绑定女仆但都在忙碌，返回null（不回退到非绑定女仆）
            return null;
        }
        // 没有绑定女仆，回退到自动分配
        return findWaiterMaid(level, center, range);
    }
    
    public static EntityMaid findCookMaidSmart(ServerLevel level, BlockPos center, int range, BlockPos machinePos) {
        if (machinePos != null && getWorkerCountForMachine(machinePos) > 0) {
            EntityMaid bound = findBoundCookMaid(level, center, range, machinePos);
            if (bound != null) return bound;
            return null;
        }
        return findCookMaid(level, center, range);
    }

    public static int countItem(IItemHandler inv, ResourceLocation itemId) {
        if (inv == null || itemId == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < inv.getSlots(); ++i) {
            ResourceLocation rl;
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty() || !itemId.equals((Object)(rl = ForgeRegistries.ITEMS.getKey(stack.getItem())))) continue;
            count += stack.getCount();
        }
        return count;
    }

    public static int takeItem(IItemHandler from, IItemHandler to, ResourceLocation itemId, int maxCount) {
        if (from == null || to == null || itemId == null) {
            return 0;
        }
        int taken = 0;
        for (int i = 0; i < from.getSlots() && taken < maxCount; ++i) {
            int toTake;
            ItemStack extracted;
            ResourceLocation rl;
            ItemStack stack = from.getStackInSlot(i);
            if (stack.isEmpty() || !itemId.equals((Object)(rl = ForgeRegistries.ITEMS.getKey(stack.getItem()))) || (extracted = from.extractItem(i, toTake = Math.min(stack.getCount(), maxCount - taken), false)).isEmpty()) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked((IItemHandler)to, (ItemStack)extracted, (boolean)false);
            taken += extracted.getCount() - remainder.getCount();
            if (remainder.isEmpty()) continue;
            from.insertItem(i, remainder, false);
        }
        return taken;
    }

    public static int transferFromMaid(EntityMaid maid, String itemId, int maxCount, IItemHandler target) {
        IItemHandler maidInv = MaidUtils.getInventory(maid);
        if (maidInv == null || target == null) {
            return 0;
        }
        ResourceLocation rl = new ResourceLocation(itemId);
        return MaidUtils.takeItem(maidInv, target, rl, maxCount);
    }

    // ========== 排班表配置检查 ==========
    public static final int SCHED_AUTO_ENABLED = 0;
    public static final int SCHED_AUTO_DELIVERY = 1;
    public static final int SCHED_AUTO_PACKAGING = 2;
    public static final int SCHED_AUTO_COOKING = 3;
    public static final int SCHED_AUTO_PREP = 4;
    public static final int SCHED_AUTO_COLLECT = 5;
    public static final int SCHED_AUTO_WASH = 6;
    public static final int SCHED_AUTO_ACCEPT = 7;

    /**
     * 查找打单机附近16格内绑定了该打单机的排班表方块实体
     * 只返回绑定了当前打单机的排班表，避免一个排班表影响多个打单机
     */
    public static com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity getNearbyScheduleBoard(ServerLevel level, BlockPos machinePos) {
        if (level == null || machinePos == null) return null;
        for (BlockPos pos : BlockPos.betweenClosed(machinePos.offset(-16, -8, -16), machinePos.offset(16, 8, 16))) {
            net.minecraft.world.level.block.entity.BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity) {
                com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity board = (com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity) be;
                // 只返回绑定了当前打单机的排班表
                if (board.hasBoundMachine() && machinePos.equals(board.getBoundMachinePos())) {
                    return board;
                }
            }
        }
        return null;
    }

    /**
     * 检查打单机附近的排班表中指定配置是否启用
     * 如果没有排班表，默认返回true（全部启用）
     */
    public static boolean isScheduleBoardEnabled(ServerLevel level, BlockPos machinePos, int configType) {
        com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity board = getNearbyScheduleBoard(level, machinePos);
        if (board == null) return true; // 没有排班表，默认全部启用
        if (!board.isAutoEnabled()) return false; // 总开关关闭，全部禁用
        switch (configType) {
            case SCHED_AUTO_DELIVERY: return board.isAutoDelivery();
            case SCHED_AUTO_PACKAGING: return board.isAutoPackaging();
            case SCHED_AUTO_COOKING: return board.isAutoCooking();
            case SCHED_AUTO_PREP: return board.isAutoPrep();
            case SCHED_AUTO_COLLECT: return board.isAutoCollect();
            case SCHED_AUTO_WASH: return board.isAutoWash();
            case SCHED_AUTO_ACCEPT: return board.isAutoAccept();
            default: return true;
        }
    }

    /**
     * 获取排班表中的洗碗阈值
     * 获取排班表的洗碗阈值
     * 如果没有排班表，返回0（表示使用配置文件默认值）
     */
    public static int getScheduleBoardMinPlates(ServerLevel level, BlockPos machinePos) {
        com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity board = getNearbyScheduleBoard(level, machinePos);
        if (board == null) return 0;
        return board.getMinPlatesToWash();
    }

    static {
        init = false;
        occupiedMaids = Collections.newSetFromMap(new WeakHashMap());
    }
}
