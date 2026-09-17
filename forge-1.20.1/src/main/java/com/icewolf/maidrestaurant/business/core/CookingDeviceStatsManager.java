package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 厨具统计管理器
 * 按打单机隔离统计厨具数量，避免多个打单机之间的厨具占用互相影响
 */
public class CookingDeviceStatsManager {
    private static CookingDeviceStatsManager instance;
    
    public static final String TYPE_STOCKPOT = "Stockpot";
    public static final String TYPE_COOKING_POT = "CookingPot";
    public static final String TYPE_POT = "Pot";
    public static final String TYPE_STEAMER = "Steamer";
    
    private final Map<Long, StationStats> stationStatsMap = new ConcurrentHashMap<>();
    private long lastCleanupTick = 0L;

    private CookingDeviceStatsManager() {
    }

    public static synchronized CookingDeviceStatsManager getInstance() {
        if (instance == null) {
            instance = new CookingDeviceStatsManager();
        }
        return instance;
    }

    /**
     * 更新指定打单机的厨具统计
     * @param level 服务端世界
     * @param machinePos 打单机位置
     * @param currentTick 当前游戏tick
     */
    public void updateStation(ServerLevel level, BlockPos machinePos, long currentTick) {
        int range = BusinessConfig.dishScanRange;
        long key = machinePos.asLong();
        StationStats stats = this.stationStatsMap.computeIfAbsent(key, k -> new StationStats(machinePos.immutable()));
        if (currentTick - stats.lastUpdateTick < 10L) {
            return;
        }
        stats.lastUpdateTick = currentTick;
        stats.deviceCounts.clear();
        
        // 扫描打单机周围的厨具
        for (BlockPos check : BlockPos.betweenClosed(machinePos.offset(-range, -4, -range), machinePos.offset(range, 4, range))) {
            BlockEntity be = level.getBlockEntity(check);
            if (be == null) continue;
            String className = be.getClass().getName();
            String type = CookingDeviceStatsManager.getDeviceTypeFromClassName(className);
            if (type == null) continue;
            stats.deviceCounts.merge(type, 1, Integer::sum);
        }
    }

    /**
     * 从BlockEntity类名获取厨具类型
     * 注意：必须先判断CookingPot，再判断Pot，因为"CookingPot"也包含"Pot"
     * @param className BlockEntity类名
     * @return 厨具类型，如果不是厨具则返回null
     */
    public static String getDeviceTypeFromClassName(String className) {
        if (className.matches(".*\\.StockpotBlockEntity")) {
            return TYPE_STOCKPOT;
        }
        if (className.matches(".*\\.CookingPotBlockEntity")) {
            return TYPE_COOKING_POT;
        }
        if (className.matches(".*\\.PotBlockEntity")) {
            return TYPE_POT;
        }
        if (className.matches(".*\\.SteamerBlockEntity")) {
            return TYPE_STEAMER;
        }
        return null;
    }

    /**
     * 从任务类名获取厨具类型
     * @param taskClassName 任务类名
     * @return 厨具类型，如果无法识别则返回null
     */
    public static String getDeviceTypeFromTaskClass(String taskClassName) {
        if (taskClassName.contains(TYPE_STOCKPOT)) {
            return TYPE_STOCKPOT;
        }
        if (taskClassName.contains(TYPE_COOKING_POT)) {
            return TYPE_COOKING_POT;
        }
        if (taskClassName.contains(TYPE_POT)) {
            return TYPE_POT;
        }
        if (taskClassName.contains(TYPE_STEAMER)) {
            return TYPE_STEAMER;
        }
        return null;
    }

    /**
     * 检查是否可以发布指定厨具类型的烹饪任务
     * @param machinePos 打单机位置
     * @param deviceType 厨具类型
     * @param level 服务端世界
     * @return true表示可以发布，false表示厨具不足或都被占用
     */
    public boolean canPublishTask(BlockPos machinePos, String deviceType, ServerLevel level) {
        if (deviceType == null) {
            return true;
        }
        long key = machinePos.asLong();
        StationStats stats = this.stationStatsMap.get(key);
        if (stats == null) {
            return true;
        }
        int totalDevices = stats.getDeviceCount(deviceType);
        if (totalDevices == 0) {
            return false;
        }
        int activeTasks = this.countActiveCookingTasksByType(deviceType, machinePos, level);
        boolean canPublish = activeTasks < totalDevices;
        return canPublish;
    }

    /**
     * 统计指定厨具类型的活跃烹饪任务数量
     * @param deviceType 厨具类型
     * @param level 服务端世界
     * @return 活跃烹饪任务数量
     */
    private int countActiveCookingTasksByType(String deviceType, BlockPos machinePos, ServerLevel level) {
        int count = 0;
        try {
            TaskManager taskManager = TaskManager.getInstance();
            count = taskManager.getActiveCookingTaskCountByDeviceType(deviceType, machinePos, level);
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("[厨具统计] 统计活跃烹饪任务失败", e);
        }
        return count;
    }

    /**
     * 清理非激活打单机的统计数据
     * @param activeMachines 当前激活的打单机位置集合
     * @param currentTick 当前游戏tick
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

    /**
     * 获取指定打单机的厨具统计
     * @param machinePos 打单机位置
     * @return 厨具统计数据
     */
    public StationStats getStationStats(BlockPos machinePos) {
        return this.stationStatsMap.get(machinePos.asLong());
    }

    /**
     * 清空所有统计数据
     */
    public void clear() {
        this.stationStatsMap.clear();
        this.lastCleanupTick = 0L;
    }

    /**
     * 打单机厨具统计数据
     */
    public static class StationStats {
        public final BlockPos machinePos;
        public final Map<String, Integer> deviceCounts = new HashMap<>();
        public long lastUpdateTick = 0L;

        public StationStats(BlockPos machinePos) {
            this.machinePos = machinePos;
        }

        /**
         * 获取指定类型的厨具数量
         * @param type 厨具类型
         * @return 厨具数量
         */
        public int getDeviceCount(String type) {
            return this.deviceCounts.getOrDefault(type, 0);
        }
    }
}
