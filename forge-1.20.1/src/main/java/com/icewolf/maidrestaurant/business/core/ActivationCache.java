package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 打单机激活状态缓存
 * 排班表与打单机是一对一关系，缓存已激活的打单机位置，避免每次遍历
 */
public class ActivationCache {
    // 维度 -> 已激活的打单机位置集合
    private static final Map<ServerLevel, Set<BlockPos>> activatedMachines = new ConcurrentHashMap<>();

    /**
     * 标记打单机为已激活
     */
    public static void activate(ServerLevel level, BlockPos machinePos) {
        activatedMachines.computeIfAbsent(level, k -> ConcurrentHashMap.newKeySet()).add(machinePos.immutable());
    }

    /**
     * 标记打单机为未激活
     */
    public static void deactivate(ServerLevel level, BlockPos machinePos) {
        Set<BlockPos> set = activatedMachines.get(level);
        if (set != null) {
            set.remove(machinePos.immutable());
        }
    }

    /**
     * 检查打单机是否已激活
     */
    public static boolean isActivated(ServerLevel level, BlockPos machinePos) {
        Set<BlockPos> set = activatedMachines.get(level);
        return set != null && set.contains(machinePos.immutable());
    }

    /**
     * 重新计算某个打单机的激活状态（用于缓存修复）
     */
    public static void recalculate(ServerLevel level, BlockPos machinePos) {
        boolean found = false;
        // 扫描16格范围内的排班表
        for (BlockPos checkPos : BlockPos.betweenClosed(
                machinePos.offset(-16, -8, -16),
                machinePos.offset(16, 8, 16))) {
            BlockEntity be = level.getBlockEntity(checkPos);
            if (be instanceof ScheduleBoardBlockEntity board) {
                if (board.hasBoundMachine() && board.getBoundMachinePos() != null
                        && board.getBoundMachinePos().equals(machinePos) && board.isAutoEnabled()) {
                    found = true;
                    break;
                }
            }
        }
        if (found) {
            activate(level, machinePos);
        } else {
            deactivate(level, machinePos);
        }
    }

    /**
     * 初始化某个维度的缓存（清空旧缓存，排班表加载时会自动注册）
     */
    public static void initLevel(ServerLevel level) {
        // 清空旧缓存，排班表的 onLoad 会自动重新注册
        activatedMachines.remove(level);
    }

    /**
     * 清除某个维度的缓存（世界卸载时调用）
     */
    public static void clearLevel(ServerLevel level) {
        activatedMachines.remove(level);
    }

    /**
     * 获取某个维度已激活的打单机数量（用于调试）
     */
    /**
     * 获取某个维度已激活的打单机位置集合
     */
    public static Set<BlockPos> getActivatedMachines(ServerLevel level) {
        Set<BlockPos> set = activatedMachines.get(level);
        return set != null ? set : java.util.Collections.emptySet();
    }
    
    public static int getActivatedCount(ServerLevel level) {
        Set<BlockPos> set = activatedMachines.get(level);
        return set != null ? set.size() : 0;
    }
}
