package com.icewolf.maidrestaurant.business.core;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 任务安全工具类：提供防卡死机制
 * 1. 目标不可达检测（三次交互确认）
 * 2. 物品/目标消失检测（10tick一次）
 * 3. 女仆状态强制重置
 */
public class TaskSafetyUtils {
    
    // 交互失败记录：maidUUID -> (targetPos -> failCount)
    private static final Map<UUID, Map<String, Integer>> interactFailCounts = new HashMap<>();
    
    // 消失检测缓存：maidUUID -> lastCheckTick
    private static final Map<UUID, Long> lastDisappearCheck = new HashMap<>();
    
    // 最大交互失败次数
    private static final int MAX_INTERACT_FAILS = 3;
    
    // 消失检测间隔（tick）
    private static final int DISAPPEAR_CHECK_INTERVAL = 10;
    
    /**
     * 记录交互失败
     * @return true表示达到最大失败次数，应该放弃任务
     */
    public static boolean recordInteractFail(UUID maidUUID, BlockPos targetPos) {
        String key = targetPos.toString();
        Map<String, Integer> maidFails = interactFailCounts.computeIfAbsent(maidUUID, k -> new HashMap<>());
        int count = maidFails.getOrDefault(key, 0) + 1;
        maidFails.put(key, count);
        
        if (count >= MAX_INTERACT_FAILS) {
            MaidRestaurantBusiness.LOGGER.warn("任务安全: 女仆 {} 与目标 {} 交互失败{}次，放弃任务", 
                maidUUID, targetPos, count);
            return true;
        }
        return false;
    }
    
    /**
     * 记录交互成功，清除失败计数
     */
    public static void recordInteractSuccess(UUID maidUUID, BlockPos targetPos) {
        Map<String, Integer> maidFails = interactFailCounts.get(maidUUID);
        if (maidFails != null) {
            maidFails.remove(targetPos.toString());
        }
    }
    
    /**
     * 清除女仆的所有交互失败记录
     */
    public static void clearMaidFails(UUID maidUUID) {
        interactFailCounts.remove(maidUUID);
        lastDisappearCheck.remove(maidUUID);
    }
    
    /**
     * 检查目标方块是否还存在（10tick检测一次）
     * @return true表示目标已消失，应该放弃任务
     */
    public static boolean isTargetDisappeared(UUID maidUUID, ServerLevel level, BlockPos targetPos, long currentTick) {
        Long lastCheck = lastDisappearCheck.get(maidUUID);
        if (lastCheck != null && currentTick - lastCheck < DISAPPEAR_CHECK_INTERVAL) {
            return false; // 还没到检测时间
        }
        lastDisappearCheck.put(maidUUID, currentTick);
        
        if (targetPos == null) return true;
        BlockState state = level.getBlockState(targetPos);
        return state.isAir();
    }
    
    /**
     * 检查目标方块实体是否还存在（10tick检测一次）
     * @return true表示目标已消失，应该放弃任务
     */
    public static boolean isBlockEntityDisappeared(UUID maidUUID, ServerLevel level, BlockPos targetPos, 
                                                     Class<? extends BlockEntity> expectedType, long currentTick) {
        if (isTargetDisappeared(maidUUID, level, targetPos, currentTick)) {
            return true;
        }
        BlockEntity be = level.getBlockEntity(targetPos);
        return be == null || !expectedType.isInstance(be);
    }
    
    /**
     * 检查女仆背包中是否还有指定物品（10tick检测一次）
     * @return true表示物品已消失，应该放弃任务
     */
    public static boolean isItemDisappeared(UUID maidUUID, EntityMaid maid, String itemId, int minCount, long currentTick) {
        Long lastCheck = lastDisappearCheck.get(maidUUID);
        if (lastCheck != null && currentTick - lastCheck < DISAPPEAR_CHECK_INTERVAL) {
            return false;
        }
        lastDisappearCheck.put(maidUUID, currentTick);
        
        if (maid == null || itemId == null) return true;
        IItemHandler inv = MaidUtils.getInventory(maid);
        if (inv == null) return true;
        
        int count = MaidUtils.countItem(inv, net.minecraft.resources.ResourceLocation.tryParse(itemId));
        return count < minCount;
    }
    
    /**
     * 强制重置女仆状态（任务结束时调用）
     */
    public static void resetMaidState(EntityMaid maid) {
        if (maid == null) return;
        
        try {
            // 清除忙碌标记
            MaidUtils.setOccupied(maid, false);
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("任务安全: 重置女仆忙碌标记失败 {}", t.toString());
        }
        
        try {
            // 停止导航
            maid.getNavigation().stop();
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("任务安全: 停止女仆导航失败 {}", t.toString());
        }
        
        try {
            // 清除大脑记忆
            Brain<?> brain = maid.getBrain();
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            brain.eraseMemory(MemoryModuleType.PATH);
            brain.eraseMemory(MemoryModuleType.LOOK_TARGET);
        } catch (Throwable t) {
            // 忽略
        }
        
        try {
            // 清除持久化数据中的任务标记
            CompoundTag data = maid.getPersistentData();
            data.remove("BusinessDeliverCounter");
            data.remove("BusinessDeliverCustomerId");
            data.remove("BusinessDeliverStage");
            data.remove("BusinessPackCounter");
            data.remove("BusinessPackStage");
            data.remove("BusinessWashCounter");
            data.remove("BusinessWashStage");
            data.remove("BusinessCookCounter");
            data.remove("BusinessCookStage");
            data.remove("BusinessCollectPlate");
            data.remove("BusinessCollectStage");
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("任务安全: 清除女仆任务标记失败 {}", t.toString());
        }
        
        // 清除交互失败记录
        clearMaidFails(maid.getUUID());
    }
    
    /**
     * 放弃任务并重置女仆状态
     */
    public static void abandonTask(EntityMaid maid, String reason) {
        if (maid != null) {
            resetMaidState(maid);
        }
    }
}
