package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.core.TaskManager;
import com.mastermarisa.maid_restaurant.init.ModEntities;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidGetFromStorageTask;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.utils.MaidStateManager;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(value={MaidGetFromStorageTask.class})
public class MaidGetFromStorageTaskMixin {
    // 记录每个女仆上次输出日志的时间，避免刷屏
    private static final Map<UUID, Long> lastLogTime = new HashMap<>();
    private static final long LOG_COOLDOWN = 100L; // 5秒冷却

    private boolean shouldLog(UUID maidUUID) {
        long currentTick = TaskManager.getInstance().getCurrentTick();
        Long last = lastLogTime.get(maidUUID);
        if (last == null || currentTick - last > LOG_COOLDOWN) {
            lastLogTime.put(maidUUID, currentTick);
            return true;
        }
        return false;
    }

    /**
     * 拦截 checkExtraStartConditions，输出详细的条件检查日志
     */
    @Inject(method={"checkExtraStartConditions"}, at=@At("RETURN"), remap=false)
    private void onCheckExtraStartConditions(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            boolean hasBusiness = request != null && request.extraData != null && request.extraData.contains("BusinessCounter");
            if (hasBusiness && shouldLog(maid.getUUID())) {
                boolean result = cir.getReturnValue();
                MaidStateManager.CookState cookState = MaidStateManager.cookState(maid, level);
                
                String reason = "";
                if (cookState != MaidStateManager.CookState.STORAGE) reason += " cookState=" + cookState;
                
                // 已删除 GetFromStorage.checkExtraStartConditions 日志，避免刷屏
            }
        } catch (Throwable t) {
            // 静默处理
        }
    }

    /**
     * 拦截 tick，输出女仆的位置和目标位置
     */
    @Inject(method={"tick"}, at=@At("HEAD"), remap=false)
    private void onTick(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            boolean hasBusiness = request != null && request.extraData != null && request.extraData.contains("BusinessCounter");
            if (hasBusiness && shouldLog(maid.getUUID())) {
                PositionTracker targetPos = maid.getBrain().getMemory(ModEntities.TARGET_POS.get()).orElse(null);
                
                String targetStr = targetPos != null ? targetPos.currentBlockPosition().toString() : "null";
                
                // 已删除 GetFromStorage.tick 日志，避免刷屏
            }
        } catch (Throwable t) {
            // 静默处理
        }
    }

    /**
     * 拦截 stop，输出停止原因
     */
    @Inject(method={"stop"}, at=@At("HEAD"), remap=false)
    private void onStop(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            boolean hasBusiness = request != null && request.extraData != null && request.extraData.contains("BusinessCounter");
            if (hasBusiness) {
                PositionTracker targetPos = maid.getBrain().getMemory(ModEntities.TARGET_POS.get()).orElse(null);
                
                String targetStr = targetPos != null ? targetPos.currentBlockPosition().toString() : "null";
                
                // 计算女仆到目标的距离
                double distToTarget = -1;
                if (targetPos != null) {
                    BlockPos target = targetPos.currentBlockPosition();
                    distToTarget = Math.sqrt(target.distSqr(maid.blockPosition()));
                }
                
                MaidRestaurantBusiness.LOGGER.info("[烹饪调试] GetFromStorage.stop: 女仆={} 位置={} 目标容器={} 到目标距离={} 食物={}",
                    maid.getName().getString(), maid.blockPosition(), targetStr,
                    distToTarget >= 0 ? String.format("%.2f", distToTarget) : "null",
                    request != null ? request.id : "null");
            }
        } catch (Throwable t) {
            // 静默处理
        }
    }
}
