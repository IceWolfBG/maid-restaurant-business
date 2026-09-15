package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.config.BusinessConfig;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 厨具统计管理器（按打单机隔离）
 * 负责统计每个打单机周围的厨具总数，以及正在进行的烹饪任务数
 * 用于发布任务前的检查，避免发布超过厨具数量的任务
 */
public class CookingDeviceStatsManager {
    private static CookingDeviceStatsManager instance;

    // 厨具类型常量
    public static final String TYPE_STOCKPOT = "Stockpot";        // 汤锅
    public static final String TYPE_COOKING_POT = "CookingPot";    // 厨锅（农夫乐事）
    public static final String TYPE_POT = "Pot";                    // 炒锅（森罗物语）
    public static final String TYPE_STEAMER = "Steamer";            // 蒸笼

    // 每个打单机的厨具统计
    public static class StationStats {
        public final BlockPos machinePos;
        // 厨具类型 -> 总数
        public final Map<String, Integer> deviceCounts = new HashMap<>();
        // 上一次扫描的厨具数量（用于判断是否有变化）
        public final Map<String, Integer> lastDeviceCounts = new HashMap<>();
        // 最后更新时间
        public long lastUpdateTick = 0;
        // 上一次canPublishTask的结果（用于判断是否有变化）
        public boolean lastCanPublishResult = true;
        public String lastCanPublishDeviceType = "";

        public StationStats(BlockPos machinePos) {
            this.machinePos = machinePos;
        }

        public int getDeviceCount(String type) {
            return deviceCounts.getOrDefault(type, 0);
        }

        // 检查厨具数量是否有变化
        public boolean hasDeviceCountsChanged() {
            if (lastDeviceCounts.isEmpty() && deviceCounts.isEmpty()) {
                return false;
            }
            if (lastDeviceCounts.size() != deviceCounts.size()) {
                return true;
            }
            for (Map.Entry<String, Integer> entry : deviceCounts.entrySet()) {
                if (!lastDeviceCounts.getOrDefault(entry.getKey(), 0).equals(entry.getValue())) {
                    return true;
                }
            }
            return false;
        }

        // 更新上一次的厨具数量
        public void updateLastDeviceCounts() {
            lastDeviceCounts.clear();
            lastDeviceCounts.putAll(deviceCounts);
        }
    }

    // 打单机位置 -> 厨具统计
    private final Map<Long, StationStats> stationStatsMap = new ConcurrentHashMap<>();

    // 最后清理时间
    private long lastCleanupTick = 0;

    private CookingDeviceStatsManager() {}

    public static synchronized CookingDeviceStatsManager getInstance() {
        if (instance == null) {
            instance = new CookingDeviceStatsManager();
        }
        return instance;
    }

    /**
     * 更新指定打单机的厨具统计（每10tick调用一次）
     */
    public void updateStation(ServerLevel level, BlockPos machinePos, long currentTick) {
        // 使用配置的扫描范围（默认24格，最大48格），与TaskManager和其他扫描保持一致
        int range = BusinessConfig.dishScanRange;
        long key = machinePos.asLong();
        StationStats stats = stationStatsMap.computeIfAbsent(key, k -> new StationStats(machinePos.immutable()));

        // 每10tick更新一次
        if (currentTick - stats.lastUpdateTick < 10) {
            return;
        }
        stats.lastUpdateTick = currentTick;

        // 重置统计
        stats.deviceCounts.clear();

        int scannedBlocks = 0;
        int foundDevices = 0;
        // 扫描范围内所有厨具
        for (BlockPos check : BlockPos.betweenClosed(
                machinePos.offset(-range, -8, -range),
                machinePos.offset(range, 8, range))) {
            scannedBlocks++;
            BlockEntity be = level.getBlockEntity(check);
            if (be == null) continue;
            String cn = be.getClass().getName();

            String type = getDeviceTypeFromClassName(cn);
            if (type != null) {
                foundDevices++;
                stats.deviceCounts.merge(type, 1, Integer::sum);
            }
        }

    }

    /**
     * 从类名获取厨具类型（严格匹配，避免CookingPot被误判为Pot）
     */
    public static String getDeviceTypeFromClassName(String className) {
        // 用正则表达式精确匹配类名结尾（带包名分隔符）
        if (className.matches(".*\\.StockpotBlockEntity")) {
            return TYPE_STOCKPOT;
        } else if (className.matches(".*\\.CookingPotBlockEntity")) {
            return TYPE_COOKING_POT;
        } else if (className.matches(".*\\.PotBlockEntity")) {
            return TYPE_POT;
        } else if (className.matches(".*\\.SteamerBlockEntity")) {
            return TYPE_STEAMER;
        }
        return null;
    }

    /**
     * 从烹饪任务类名获取厨具类型（严格匹配，注意顺序）
     */
    public static String getDeviceTypeFromTaskClass(String taskClassName) {
        // 注意：必须先判断CookingPot，再判断Pot，因为"CookingPot"也包含"Pot"
        if (taskClassName.contains("Stockpot")) {
            return TYPE_STOCKPOT;
        } else if (taskClassName.contains("CookingPot")) {
            return TYPE_COOKING_POT;
        } else if (taskClassName.contains("Pot")) {
            return TYPE_POT;
        } else if (taskClassName.contains("Steamer")) {
            return TYPE_STEAMER;
        }
        return null; // 未知类型返回null，不进行厨具数量管理
    }

    /**
     * 检查指定打单机是否可以发布指定类型的烹饪任务
     * 原理：正在进行的该类型任务数 < 该类型厨具总数
     */
    public boolean canPublishTask(BlockPos machinePos, String deviceType, ServerLevel level) {
        // 未知厨具类型（deviceType为null），不进行厨具数量管理，直接允许发布
        if (deviceType == null) {
            return true;
        }
        
        long key = machinePos.asLong();
        StationStats stats = stationStatsMap.get(key);
        if (stats == null) {
            // 没有统计数据，允许发布（避免因为没扫描到而不发布任务）
            return true;
        }

        int totalDevices = stats.getDeviceCount(deviceType);
        if (totalDevices == 0) {
            // 没有该类型厨具，不允许发布
            return false;
        }

        // 获取正在进行的该类型烹饪任务数
        int activeTasks = countActiveCookingTasksByType(deviceType, level);

        boolean canPublish = activeTasks < totalDevices;
        return canPublish;
    }

    /**
     * 统计正在进行的指定类型烹饪任务数
     * 通过TaskManager获取所有IN_PROGRESS状态的烹饪任务，然后根据任务的targetPos判断厨具类型
     */
    private int countActiveCookingTasksByType(String deviceType, ServerLevel level) {
        int count = 0;
        try {
            TaskManager taskManager = TaskManager.getInstance();
            // 遍历所有任务，统计正在进行的烹饪任务
            // TaskManager的tasks是private的，我们通过其他方式获取
            // 这里用反射或者提供一个公共方法
            count = taskManager.getActiveCookingTaskCountByDeviceType(deviceType, level);
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("[厨具统计] 统计活跃烹饪任务失败", e);
        }
        return count;
    }

    /**
     * 清理不在激活列表中的打单机统计
     */
    public void cleanupInactiveStations(Set<BlockPos> activeMachines, long currentTick) {
        if (currentTick - lastCleanupTick < 100) {
            return;
        }
        lastCleanupTick = currentTick;

        Set<Long> activeKeys = new HashSet<>();
        for (BlockPos pos : activeMachines) {
            activeKeys.add(pos.asLong());
        }

        int removedCount = 0;
        for (Long key : new HashSet<>(stationStatsMap.keySet())) {
            if (!activeKeys.contains(key)) {
                stationStatsMap.remove(key);
                removedCount++;
            }
        }

        if (removedCount > 0) {
            MaidRestaurantBusiness.LOGGER.info("[厨具统计] 清理了{}个非激活打单机的统计数据", removedCount);
        }
    }

    /**
     * 获取指定打单机的统计信息（用于调试）
     */
    public StationStats getStationStats(BlockPos machinePos) {
        return stationStatsMap.get(machinePos.asLong());
    }

    /**
     * 清空所有状态（世界卸载时调用）
     */
    public void clear() {
        stationStatsMap.clear();
        lastCleanupTick = 0;
    }
}
