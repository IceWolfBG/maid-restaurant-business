package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.core.TaskManager;
import com.mastermarisa.maid_restaurant.init.ModEntities;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidApproachCookBlockTask;
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

@Mixin(value={MaidApproachCookBlockTask.class})
public class MaidApproachCookBlockTaskMixin {
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
                boolean isPassenger = maid.isPassenger();
                
                String reason = "";
                if (isPassenger) reason += " isPassenger";
                if (cookState != MaidStateManager.CookState.COOK) reason += " cookState=" + cookState;
                
                // 已删除 ApproachCookBlock.checkExtraStartConditions 日志，避免刷屏
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
                PositionTracker chairPos = maid.getBrain().getMemory(ModEntities.CHAIR_POS.get()).orElse(null);
                
                String targetStr = targetPos != null ? targetPos.currentBlockPosition().toString() : "null";
                String chairStr = chairPos != null ? chairPos.currentBlockPosition().toString() : "null";
                
                // 已删除 ApproachCookBlock.tick 日志，避免刷屏
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
                PositionTracker chairPos = maid.getBrain().getMemory(ModEntities.CHAIR_POS.get()).orElse(null);
                
                String targetStr = targetPos != null ? targetPos.currentBlockPosition().toString() : "null";
                String chairStr = chairPos != null ? chairPos.currentBlockPosition().toString() : "null";
                
                // 计算女仆到椅子的距离
                double distToChair = -1;
                if (chairPos != null) {
                    BlockPos chair = chairPos.currentBlockPosition();
                    distToChair = Math.sqrt(chair.distSqr(maid.blockPosition()));
                }
                
                MaidRestaurantBusiness.LOGGER.info("[烹饪调试] ApproachCookBlock.stop: 女仆={} 位置={} 目标={} 椅子={} 到椅子距离={} 食物={}",
                    maid.getName().getString(), maid.blockPosition(), targetStr, chairStr,
                    distToChair >= 0 ? String.format("%.2f", distToChair) : "null",
                    request != null ? request.id : "null");
            }
        } catch (Throwable t) {
            // 静默处理
        }
    }
}
