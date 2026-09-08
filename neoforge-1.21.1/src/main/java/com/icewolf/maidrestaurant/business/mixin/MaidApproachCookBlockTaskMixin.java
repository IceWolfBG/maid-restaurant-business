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
    // 璁板綍姣忎釜濂充粏涓婃杈撳嚭鏃ュ織鐨勬椂闂达紝閬垮厤鍒峰睆
    private static final Map<UUID, Long> lastLogTime = new HashMap<>();
    private static final long LOG_COOLDOWN = 100L; // 5绉掑喎鍗?

    private boolean shouldLog(EntityMaid maid) {
        long currentTick = maid.tickCount;
        Long last = lastLogTime.get(maid.getUUID());
        if (last == null || currentTick - last > LOG_COOLDOWN) {
            lastLogTime.put(maid.getUUID(), currentTick);
            return true;
        }
        return false;
    }

    /**
     * 鎷︽埅 checkExtraStartConditions锛岃緭鍑鸿缁嗙殑鏉′欢妫€鏌ユ棩蹇?
     */
    @Inject(method={"checkExtraStartConditions"}, at=@At("RETURN"), remap=false)
    private void onCheckExtraStartConditions(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            boolean hasBusiness = request != null && request.extraData != null && request.extraData.contains("BusinessCounter");
            if (hasBusiness && shouldLog(maid)) {
                boolean result = cir.getReturnValue();
                MaidStateManager.CookState cookState = MaidStateManager.cookState(maid, level);
                boolean isPassenger = maid.isPassenger();
                
                String reason = "";
                if (isPassenger) reason += " isPassenger";
                if (cookState != MaidStateManager.CookState.COOK) reason += " cookState=" + cookState;
                
                // 宸插垹闄?ApproachCookBlock.checkExtraStartConditions 鏃ュ織锛岄伩鍏嶅埛灞?
            }
        } catch (Throwable t) {
            // 闈欓粯澶勭悊
        }
    }

    /**
     * 鎷︽埅 tick锛岃緭鍑哄コ浠嗙殑浣嶇疆鍜岀洰鏍囦綅缃?
     */
    @Inject(method={"tick"}, at=@At("HEAD"), remap=false)
    private void onTick(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            boolean hasBusiness = request != null && request.extraData != null && request.extraData.contains("BusinessCounter");
            if (hasBusiness && shouldLog(maid)) {
                PositionTracker targetPos = maid.getBrain().getMemory(ModEntities.TARGET_POS.get()).orElse(null);
                PositionTracker chairPos = maid.getBrain().getMemory(ModEntities.CHAIR_POS.get()).orElse(null);
                
                String targetStr = targetPos != null ? targetPos.currentBlockPosition().toString() : "null";
                String chairStr = chairPos != null ? chairPos.currentBlockPosition().toString() : "null";
                
                // 宸插垹闄?ApproachCookBlock.tick 鏃ュ織锛岄伩鍏嶅埛灞?
            }
        } catch (Throwable t) {
            // 闈欓粯澶勭悊
        }
    }

    /**
     * 鎷︽埅 stop锛岃緭鍑哄仠姝㈠師鍥?
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
                
                // 璁＄畻濂充粏鍒版瀛愮殑璺濈
                double distToChair = -1;
                if (chairPos != null) {
                    BlockPos chair = chairPos.currentBlockPosition();
                    distToChair = Math.sqrt(chair.distSqr(maid.blockPosition()));
                }
                
                MaidRestaurantBusiness.LOGGER.info("[鐑归オ璋冭瘯] ApproachCookBlock.stop: 濂充粏={} 浣嶇疆={} 鐩爣={} 妞呭瓙={} 鍒版瀛愯窛绂?{} 椋熺墿={}",
                    maid.getName().getString(), maid.blockPosition(), targetStr, chairStr,
                    distToChair >= 0 ? String.format("%.2f", distToChair) : "null",
                    request != null ? request.id : "null");
            }
        } catch (Throwable t) {
            // 闈欓粯澶勭悊
        }
    }
}
