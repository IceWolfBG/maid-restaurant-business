package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.mastermarisa.maid_restaurant.utils.CookTasks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局厨具状态管理器
 * 负责跟踪所有厨具的占用状态，避免多个厨师同时使用同一个厨具
 */
public class CookingDeviceManager {
    private static CookingDeviceManager instance;

    // 厨具信息
    public static class DeviceInfo {
        public final BlockPos pos;
        public final String type;  // "Stockpot" / "Pot" / "Steamer"
        public boolean occupied;
        public UUID occupant;
        public long occupyStartTime;

        public DeviceInfo(BlockPos pos, String type) {
            this.pos = pos;
            this.type = type;
            this.occupied = false;
            this.occupant = null;
            this.occupyStartTime = 0;
        }
    }

    // 全局厨具表（位置 -> 厨具信息）
    private final Map<Long, DeviceInfo> devices = new ConcurrentHashMap<>();

    // 持久化保存的厨具占用状态
    private CookingDeviceSavedData savedData;

    // 最后更新时间
    private long lastUpdateTick = 0;

    // 厨具占用超时时间（tick，默认1800=90秒）
    private static final long OCCUPY_TIMEOUT = 1800L;

    private CookingDeviceManager() {}

    public static synchronized CookingDeviceManager getInstance() {
        if (instance == null) {
            instance = new CookingDeviceManager();
        }
        return instance;
    }

    /**
     * 初始化持久化保存（在世界加载时调用）
     */
    public void initSavedData(ServerLevel level) {
        if (savedData == null) {
            savedData = CookingDeviceSavedData.get(level);
            // 从SavedData同步占用状态到内存
            syncFromSavedData();
        }
    }

    /**
     * 从SavedData同步占用状态到内存
     */
    private void syncFromSavedData() {
        if (savedData == null) return;
        for (Map.Entry<Long, CookingDeviceSavedData.DeviceOccupancy> entry : savedData.getDevices().entrySet()) {
            long key = entry.getKey();
            CookingDeviceSavedData.DeviceOccupancy occupancy = entry.getValue();
            DeviceInfo info = devices.get(key);
            if (info == null) {
                // 内存中没有这个厨具，创建一个（位置暂时用0，0，0，因为我们只需要占用状态）
                info = new DeviceInfo(BlockPos.ZERO, occupancy.type);
                devices.put(key, info);
            }
            info.occupied = occupancy.occupied;
            info.occupant = occupancy.occupant;
            info.occupyStartTime = occupancy.occupyStartTime;
        }
    }

    /**
     * 更新全局厨具状态（每10tick调用一次）
     * 扫描范围内所有厨具，更新状态，清理超时占用
     */
    public void update(ServerLevel level, BlockPos center, int range, long currentTick) {
        // 每10tick更新一次
        if (currentTick - lastUpdateTick < 10) {
            return;
        }
        lastUpdateTick = currentTick;

        // 扫描范围内所有厨具
        Set<Long> currentDevices = new HashSet<>();
        int scannedStockpot = 0, scannedPot = 0, scannedSteamer = 0;
        for (BlockPos check : BlockPos.betweenClosed(
                center.offset(-range, -4, -range),
                center.offset(range, 4, range))) {
            BlockEntity be = level.getBlockEntity(check);
            if (be == null) continue;
            String cn = be.getClass().getName();
            String type = null;
            if (cn.contains("StockpotBlockEntity")) {
                type = "Stockpot";
                scannedStockpot++;
            } else if (cn.contains("CookingPotBlockEntity") || cn.contains("PotBlockEntity")) {
                type = "Pot";
                scannedPot++;
            } else if (cn.contains("SteamerBlockEntity")) {
                type = "Steamer";
                scannedSteamer++;
            }
            if (type != null) {
                long key = check.asLong();
                currentDevices.add(key);
                if (!devices.containsKey(key)) {
                    devices.put(key, new DeviceInfo(check.immutable(), type));
                }
            }
        }

        // 清理不在范围内的厨具
        int removedCount = 0;
        for (Long key : new HashSet<>(devices.keySet())) {
            if (!currentDevices.contains(key)) {
                DeviceInfo info = devices.get(key);
                devices.remove(key);
                removedCount++;
            }
        }

        // 清理超时占用
        int timeoutReleased = 0;
        for (DeviceInfo info : devices.values()) {
            if (info.occupied && currentTick - info.occupyStartTime > OCCUPY_TIMEOUT) {
                MaidRestaurantBusiness.LOGGER.warn("[厨具管理] 厨具超时自动释放: 类型={}, 位置={}, 占用者={}, 占用时长={}tick",
                        info.type, info.pos, info.occupant, currentTick - info.occupyStartTime);
                info.occupied = false;
                info.occupant = null;
                info.occupyStartTime = 0;
                timeoutReleased++;
            }
        }

        // 每100tick输出一次详细状态
        if (currentTick % 100L == 0L) {
            int total = devices.size();
            int occupied = 0, available = 0;
            int occStockpot = 0, occPot = 0, occSteamer = 0;
            int availStockpot = 0, availPot = 0, availSteamer = 0;
            for (DeviceInfo info : devices.values()) {
                if (info.occupied) {
                    occupied++;
                    if (info.type.equals("Stockpot")) occStockpot++;
                    else if (info.type.equals("Pot")) occPot++;
                    else if (info.type.equals("Steamer")) occSteamer++;
                } else {
                    available++;
                    if (info.type.equals("Stockpot")) availStockpot++;
                    else if (info.type.equals("Pot")) availPot++;
                    else if (info.type.equals("Steamer")) availSteamer++;
                }
            }
        }
    }

    /**
     * 获取指定类型的可用厨具数量
     */
    public int getAvailableDeviceCount(String type) {
        int count = 0;
        for (DeviceInfo info : devices.values()) {
            if (info.type.equals(type) && !info.occupied) {
                count++;
            }
        }
        return count;
    }

    /**
     * 获取指定类型的可用厨具（按距离排序）
     */
    public List<DeviceInfo> getAvailableDevices(String type, BlockPos fromPos) {
        List<DeviceInfo> result = new ArrayList<>();
        for (DeviceInfo info : devices.values()) {
            if (info.type.equals(type) && !info.occupied) {
                result.add(info);
            }
        }
        // 按距离排序
        if (fromPos != null) {
            result.sort((a, b) -> Double.compare(
                    a.pos.distSqr(fromPos),
                    b.pos.distSqr(fromPos)));
        }
        return result;
    }

    /**
     * 获取最近的可用厨具
     */
    public DeviceInfo getNearestAvailableDevice(String type, BlockPos fromPos) {
        List<DeviceInfo> available = getAvailableDevices(type, fromPos);
        return available.isEmpty() ? null : available.get(0);
    }

    /**
     * 检查厨具是否被占用
     * 多厨师并行优化：用于避免多个厨师同时使用同一个厨具
     */
    public boolean isOccupied(BlockPos pos) {
        DeviceInfo info = devices.get(pos.asLong());
        if (info == null) {
            // 厨具不在管理范围内，认为是空闲的（允许使用）
            return false;
        }
        return info.occupied;
    }

    /**
     * 标记厨具为被占用
     */
    public boolean markOccupied(BlockPos pos, UUID maidUUID, long currentTick) {
        DeviceInfo info = devices.get(pos.asLong());
        if (info == null) {
            // 厨具不在管理范围内，直接返回成功（允许使用）
            MaidRestaurantBusiness.LOGGER.warn("[厨具管理] markOccupied: 厨具 {} 不在管理范围内，允许使用", pos);
            return true;
        }
        if (info.occupied && !info.occupant.equals(maidUUID)) {
            // 已被其他厨师占用
            MaidRestaurantBusiness.LOGGER.warn("[厨具管理] markOccupied: 厨具 {} 类型={} 已被 {} 占用，拒绝 {} 的占用请求",
                    pos, info.type, info.occupant, maidUUID);
            return false;
        }
        boolean wasOccupied = info.occupied;
        info.occupied = true;
        info.occupant = maidUUID;
        info.occupyStartTime = currentTick;
        // 同步到持久化保存
        if (savedData != null) {
            savedData.setOccupied(pos, info.type, maidUUID, currentTick);
        }
        return true;
    }

    /**
     * 标记厨具为空闲
     */
    public void markFree(BlockPos pos) {
        DeviceInfo info = devices.get(pos.asLong());
        if (info != null) {
            UUID oldOccupant = info.occupant;
            boolean wasOccupied = info.occupied;
            info.occupied = false;
            info.occupant = null;
            info.occupyStartTime = 0;
            // 同步到持久化保存
            if (savedData != null) {
                savedData.setFree(pos);
            }
        } else {
            MaidRestaurantBusiness.LOGGER.warn("[厨具管理] markFree: 厨具 {} 不在管理范围内(devices中找不到)，无法释放！当前管理的厨具数={}", pos, devices.size());
            // 打印当前管理的所有厨具
            for (DeviceInfo d : devices.values()) {
            }
        }
    }

    /**
     * 根据配方类型获取厨具类型字符串
     */
    public static String getDeviceTypeFromRecipe(RecipeType<?> recipeType) {
        try {
            String taskClass = CookTasks.getTask(recipeType).getClass().getSimpleName();
            if (taskClass.contains("Stockpot")) return "Stockpot";
            if (taskClass.contains("Pot")) return "Pot";
            if (taskClass.contains("Steamer")) return "Steamer";
        } catch (Exception e) {
            // ignore
        }
        return "Pot"; // 默认炒锅
    }

    /**
     * 清空所有状态（世界卸载时调用）
     */
    public void clear() {
        devices.clear();
        lastUpdateTick = 0;
    }

    /**
     * 获取统计信息（用于调试）
     */
    public Map<String, Integer> getStats() {
        Map<String, Integer> stats = new HashMap<>();
        int total = 0, occupied = 0, available = 0;
        for (DeviceInfo info : devices.values()) {
            total++;
            if (info.occupied) occupied++;
            else available++;
        }
        stats.put("total", total);
        stats.put("occupied", occupied);
        stats.put("available", available);
        return stats;
    }
}
