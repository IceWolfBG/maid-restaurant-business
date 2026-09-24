package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.AutomationConfig;
import com.icewolf.maidrestaurant.business.config.GameplayConfig;
import com.icewolf.maidrestaurant.business.config.PerformanceConfig;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.utils.CookTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 厨具统计管理器（数据驱动 / 全厨具兼容，按激活打单机隔离）
 *
 * 厨具类型不再硬编码，统一以女仆餐厅注册的 {@link ICookTask} 的 UID 为粒度：
 * <ul>
 *   <li>设备单位 = 一个非空 BlockEntity（设备本体）。部分厨具的 {@code isValidWorkBlock}
 *       会对设备本体上/下相邻坐标也返回 true（例如 farm_and_charm 火炉的判定在 y-1~y+1
 *       容错查找炉体），本管理器只在“非空 BlockEntity 位置”归类，并对竖直相邻的同类锚点
 *       去重（保留更靠上的设备本体），保证一台物理设备只计数 1、只暴露一个本体坐标。</li>
 *   <li><b>数量容量与动态可用解耦</b>：数量统计只数物理设备台数（结构容量，按激活打单机
 *       ±dishScanRange、y±4 锚点枚举）；设备此刻有无燃料、点未点燃、厨凳是否被占等动态条件，
 *       一律交给选台阶段（官方 {@code searchWorkBlock} 与 {@code isValidWorkBlock} 实时复核）。
 *       带动态热源的设备（如 farm_and_charm 火炉：有燃料或正在烧才算可开工）不会因采样瞬间缺燃料
 *       被剔出数量闸；选台返回设备本体/厨凳坐标，不会把炉体上、下的容错空气位当成交互目标。</li>
 *   <li>任务归类用 {@link CookTasks#getUID(RecipeType)}，与统计同一 key，
 *       天然消除类名 contains（CookingPot 含 Pot）一类误判。</li>
 * </ul>
 *
 * 边界：少数设备（如烘焙坊玻璃杯饮品台）的工作位是没有 BlockEntity 的空气位，
 * 高频统计扫不到它们，UID 不会进入 {@link #BE_AWARE_UIDS}；{@link #canPublishTask}
 * 对其放行，数量管理退化为选台阶段的全坐标扫描 + 坐标占用兜底。
 */
public class CookingDeviceStatsManager {
    private static CookingDeviceStatsManager instance;

    /** 打单机位置 -> 该机器的厨具统计（按激活打单机隔离） */
    private final Map<Long, StationStats> stationStatsMap = new ConcurrentHashMap<>();
    private long lastCleanupTick = 0L;

    /** 设备方块 -> 它结构上所属的厨具任务（按方块粒度固化；同一 BlockEntity 类被多个方块共用时也不会串配） */
    private static final Map<Block, ICookTask> BLOCK_TASK_CACHE = new ConcurrentHashMap<>();
    /** 已确认能在“非空 BlockEntity 位置”被识别到的厨具 UID（受数量上限管理的设备集合；无 BE 设备永不进入） */
    private static final Set<String> BE_AWARE_UIDS = ConcurrentHashMap.newKeySet();

    private CookingDeviceStatsManager() {
    }

    public static synchronized CookingDeviceStatsManager getInstance() {
        if (instance == null) {
            instance = new CookingDeviceStatsManager();
        }
        return instance;
    }

    /** 配方所需厨具 -> UID（数量管理与任务计数共同的权威 key）；无法识别或返回空串时归一为 null。 */
    public static String getDeviceUid(RecipeType<?> recipeType) {
        if (recipeType == null) return null;
        try {
            String uid = CookTasks.getUID(recipeType);
            return (uid == null || uid.isEmpty()) ? null : uid;
        } catch (Throwable t) {
            return null;
        }
    }

    /** 该 UID 是否属于“有 BlockEntity 本体”的设备（受数量上限管理）。 */
    public static boolean isBeAwareUid(String uid) {
        return uid != null && BE_AWARE_UIDS.contains(uid);
    }

    /**
     * 更新指定打单机的厨具统计（每 10tick 一次），只扫描非空 BlockEntity 位置。
     */
    public void updateStation(ServerLevel level, BlockPos machinePos, long currentTick) {
        int range = PerformanceConfig.dishScanRange;
        long key = machinePos.asLong();
        StationStats stats = this.stationStatsMap.computeIfAbsent(key, k -> new StationStats(machinePos.immutable()));
        if (currentTick - stats.lastUpdateTick < 10L) {
            return;
        }
        stats.lastUpdateTick = currentTick;
        stats.deviceCounts.clear();
        stats.anchorsByUid.clear();

        List<ICookTask> registered;
        try {
            registered = CookTasks.getRegistered();
        } catch (Throwable t) {
            return;
        }
        if (registered == null || registered.isEmpty()) return;

        // 先按 UID 收集原始“设备本体”锚点（非空 BE 位置），再做竖直相邻去重
        Map<String, List<BlockPos>> raw = new HashMap<>();
        for (BlockPos check : BlockPos.betweenClosed(machinePos.offset(-range, -4, -range), machinePos.offset(range, 4, range))) {
            BlockEntity be = level.getBlockEntity(check);
            if (be == null) continue;
            ICookTask matched = resolveTaskForBlockEntity(level, be, check, registered);
            if (matched == null) continue;
            String uid = matched.getUID();
            if (uid == null || uid.isEmpty()) continue;
            BE_AWARE_UIDS.add(uid);
            raw.computeIfAbsent(uid, k -> new ArrayList<>()).add(check.immutable());
        }

        for (Map.Entry<String, List<BlockPos>> e : raw.entrySet()) {
            List<BlockPos> anchors = verticalDedup(e.getValue());
            stats.anchorsByUid.put(e.getKey(), anchors);
            stats.deviceCounts.put(e.getKey(), anchors.size());
        }
    }

    /**
     * 竖直相邻去重：若锚点 a 的正上方也是同类锚点，说明 a 是被“y±1 容错判定”误中的下方支撑方块，
     * 剔除 a、保留更靠上的真正设备本体。独立设备（上方为空气）不受影响。
     */
    private static List<BlockPos> verticalDedup(List<BlockPos> anchors) {
        if (anchors.size() <= 1) return anchors;
        Set<Long> occupied = new HashSet<>();
        for (BlockPos p : anchors) {
            occupied.add(p.asLong());
        }
        List<BlockPos> out = new ArrayList<>(anchors.size());
        for (BlockPos p : anchors) {
            if (occupied.contains(p.above().asLong())) continue; // 上方还有同类锚点，本格是被误中的下方块
            out.add(p);
        }
        return out;
    }

    /**
     * 实时枚举某个厨具任务在该打单机范围内的“设备本体”锚点（非空 BE、isValidWorkBlock 成立、竖直去重）。
     * 供选台在统计缓存尚未建立时兜底使用；判定口径与 {@link #updateStation} 完全一致。
     */
    public static List<BlockPos> enumerateAnchorsRealTime(ServerLevel level, BlockPos machinePos, ICookTask cookTask) {
        int range = PerformanceConfig.dishScanRange;
        List<BlockPos> raw = new ArrayList<>();
        for (BlockPos check : BlockPos.betweenClosed(machinePos.offset(-range, -4, -range), machinePos.offset(range, 4, range))) {
            if (level.getBlockEntity(check) == null) continue;
            boolean valid;
            try {
                valid = cookTask.isValidWorkBlock(level, null, check);
            } catch (Throwable t) {
                valid = false;
            }
            if (valid) {
                raw.add(check.immutable());
            }
        }
        return verticalDedup(raw);
    }

    /**
     * 获取该打单机范围内某厨具 UID 的缓存设备本体锚点（副本）。
     * 调用方在选台时应再用 isValidWorkBlock 实时复核（热源/存在性可能在两次扫描间变化）。
     */
    public List<BlockPos> getAnchors(BlockPos machinePos, String uid) {
        StationStats stats = this.stationStatsMap.get(machinePos.asLong());
        if (stats == null) return new ArrayList<>();
        List<BlockPos> list = stats.anchorsByUid.get(uid);
        return list == null ? new ArrayList<>() : new ArrayList<>(list);
    }

    /**
     * 判定某个非空 BlockEntity 位置属于哪个厨具任务。
     * 已确认结构归属的方块直接返回缓存的那一个 task（按物理设备实例计数，动态可用性归选台阶段判定）。
     * 未确认的方块先用“任务图标物品 == 该方块实体所在方块的物品”做精确本体归属（纯结构，不看相邻/
     * 燃料/点燃，避免 ±1 容错让相邻别的设备冒领，例如火炉上、下相邻的砧板把火炉认成砧板）；图标无法
     * 精确匹配时，再退回 isValidWorkBlock 首个命中的任务认领，兼容图标非常规或无物品形式的设备。
     */
    private static ICookTask resolveTaskForBlockEntity(ServerLevel level, BlockEntity be, BlockPos pos, List<ICookTask> registered) {
        Block block = be.getBlockState().getBlock();
        ICookTask cached = BLOCK_TASK_CACHE.get(block);
        if (cached != null) {
            // 结构归属已学会：该设备方块在物理上就属于此厨具，直接按设备实例计数（容量）。
            // isValidWorkBlock 内含燃料/点燃、±1 相邻容错等动态条件，不能用它逐轮否决设备的存在性，
            // 否则火炉在燃料耗尽/未点燃的采样瞬间会被剔出数量统计，使数量闸对其永久放行、一台炉被派多个任务。
            // “此刻能否开工”仍由选台阶段的官方 searchWorkBlock / safeValidWorkBlock 实时判定（冷炉选不到即不发布）。
            return cached;
        }

        // 第一判据（精确本体）：任务图标物品 == 该方块实体所在方块的物品。纯结构判定，不使用
        // isValidWorkBlock 的 ±1 相邻容错/燃料/点燃，防止相邻的别的设备冒领本格设备。
        Item blockItem = null;
        try {
            blockItem = block.asItem();
        } catch (Throwable t) {
            blockItem = null;
        }
        if (blockItem != null && blockItem != Items.AIR) {
            for (ICookTask task : registered) {
                ItemStack icon;
                try {
                    icon = task.getIcon();
                } catch (Throwable t) {
                    continue;
                }
                if (icon != null && !icon.isEmpty() && icon.getItem() == blockItem) {
                    BLOCK_TASK_CACHE.put(block, task);
                    return task;
                }
            }
        }

        // 兜底：图标无法精确匹配（图标非常规、或方块无物品形式）时，退回 isValidWorkBlock 首个认领，保证兼容
        for (ICookTask task : registered) {
            boolean valid;
            try {
                valid = task.isValidWorkBlock(level, null, pos);
            } catch (Throwable t) {
                valid = false;
            }
            if (valid) {
                BLOCK_TASK_CACHE.put(block, task);
                return task;
            }
        }
        return null;
    }

    /**
     * 是否还能发布某厨具类型的烹饪任务（按打单机隔离）。
     * 活跃任务数（PENDING/ASSIGNED/IN_PROGRESS）必须小于该机器当前可用厨具数。
     * 无 BlockEntity 的设备（饮品杯等）不在数量管理范围，直接放行，交给选台坐标占用兜底。
     */
    public boolean canPublishTask(BlockPos machinePos, String uid, ServerLevel level) {
        if (uid == null) {
            return true;
        }
        // 尚未在世界的非空 BlockEntity 位置出现过的厨具类型：可能是无 BE 设备，也可能玩家还没放置；
        // 两种情况都交给 findCookingDevice 选台判定（找不到会返回 null 并提示缺厨具），不做数量上限拦截。
        if (!BE_AWARE_UIDS.contains(uid)) {
            return true;
        }
        StationStats stats = this.stationStatsMap.get(machinePos.asLong());
        if (stats == null) {
            return true; // 还没完成首次扫描，避免开局误拦
        }
        int totalDevices = stats.getDeviceCount(uid);
        if (totalDevices == 0) {
            return false;
        }
        int activeTasks = this.countActiveCookingTasksByType(uid, machinePos, level);
        return activeTasks < totalDevices;
    }

    private int countActiveCookingTasksByType(String uid, BlockPos machinePos, ServerLevel level) {
        try {
            return TaskManager.getInstance().getActiveCookingTaskCountByDeviceType(uid, machinePos, level);
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("[厨具统计] 统计活跃烹饪任务失败 uid={}", uid, e);
            return 0;
        }
    }

    /**
     * 清理非激活打单机的统计数据
     */
    public void cleanupInactiveStations(Set<BlockPos> activeMachines, long currentTick) {
        if (currentTick - this.lastCleanupTick < 100L) {
            return;
        }
        this.lastCleanupTick = currentTick;
        HashSet<Long> activeKeys = new HashSet<>();
        for (BlockPos pos : activeMachines) {
            activeKeys.add(pos.asLong());
        }
        for (Long key : new HashSet<>(this.stationStatsMap.keySet())) {
            if (activeKeys.contains(key)) continue;
            this.stationStatsMap.remove(key);
        }
    }

    public StationStats getStationStats(BlockPos machinePos) {
        return this.stationStatsMap.get(machinePos.asLong());
    }

    public void clear() {
        this.stationStatsMap.clear();
        this.lastCleanupTick = 0L;
        // BLOCK_TASK_CACHE / BE_AWARE_UIDS 属于模组加载期的结构事实，不在此清理
    }

    /**
     * 打单机厨具统计数据（key = 厨具 ICookTask 的 UID）
     */
    public static class StationStats {
        public final BlockPos machinePos;
        public final Map<String, Integer> deviceCounts = new HashMap<>();
        /** UID -> 当前可用的设备本体坐标（已竖直去重），供选台复用 */
        public final Map<String, List<BlockPos>> anchorsByUid = new HashMap<>();
        public long lastUpdateTick = 0L;

        public StationStats(BlockPos machinePos) {
            this.machinePos = machinePos;
        }

        public int getDeviceCount(String uid) {
            return this.deviceCounts.getOrDefault(uid, 0);
        }
    }
}
