package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.core.CookingBridge;
import com.icewolf.maidrestaurant.business.core.TaskManager;
import com.mastermarisa.maid_restaurant.api.request.IRequest;
import com.mastermarisa.maid_restaurant.init.ModEntities;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidCookingTask;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.request.ServeRequest;
import com.mastermarisa.maid_restaurant.utils.BehaviorUtils;
import com.mastermarisa.maid_restaurant.utils.BlockUsageManager;
import com.mastermarisa.maid_restaurant.utils.MaidStateManager;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mixin(value={MaidCookingTask.class})
public class MaidCookingTaskMixin {
    // 记录每个女仆上次输出异常停止日志的时间，避免刷屏
    private static final Map<UUID, Long> lastAbnormalStopLog = new HashMap<>();
    private static final long ABNORMAL_STOP_LOG_COOLDOWN = 200L; // 10秒冷却

    private static UUID getEntityUUID(Entity entity) {
        try {
            Method method = Entity.class.getMethod("getUUID", new Class[0]);
            return (UUID)method.invoke(entity, new Object[0]);
        }
        catch (Throwable t) {
            return entity.getUUID();
        }
    }

    /**
     * 拦截 checkExtraStartConditions，输出详细的条件检查日志
     */
    @Inject(method={"checkExtraStartConditions"}, at=@At("RETURN"), remap=false)
    private void onCheckExtraStartConditions(ServerLevel level, EntityMaid maid, CallbackInfoReturnable<Boolean> cir) {
        try {
            UUID maidUUID = getEntityUUID(maid);
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            boolean hasBusiness = request != null && request.extraData != null && request.extraData.contains("BusinessCounter");
            if (hasBusiness) {
                boolean result = cir.getReturnValue();
                // 每100tick输出一次检查结果，避免刷屏
                long currentTick = TaskManager.getInstance().getCurrentTick();
                if (currentTick % 100L == 0L) {
                    // 详细检查每个条件
                    int targetType = BehaviorUtils.getTargetType(maid);
                    boolean hasTargetPos = maid.getBrain().hasMemoryValue(ModEntities.TARGET_POS.get());
                    boolean hasChairPos = maid.getBrain().hasMemoryValue(ModEntities.CHAIR_POS.get());
                    MaidStateManager.CookState cookState = MaidStateManager.cookState(maid, level);
                    
                    String reason = "";
                    if (targetType != 2) reason += " targetType=" + targetType;
                    if (!hasTargetPos) reason += " noTargetPos";
                    if (!hasChairPos) reason += " noChairPos";
                    if (cookState != MaidStateManager.CookState.COOK) reason += " cookState=" + cookState;
                    
                    // 已删除 checkExtraStartConditions 日志，避免刷屏（行为树会频繁调用此方法）
                }
            }
        } catch (Throwable t) {
            // 静默处理
        }
    }

    /**
     * 烹饪任务开始时调用：标记任务为 IN_PROGRESS
     */
    @Inject(method={"start"}, at=@At("HEAD"), remap=false)
    private void onStart(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        try {
            UUID maidUUID = getEntityUUID(maid);
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            boolean hasBusiness = request != null && request.extraData != null && request.extraData.contains("BusinessCounter");
            if (hasBusiness) {
                MaidRestaurantBusiness.LOGGER.info("[烹饪调试] 烹饪任务开始: 女仆={}({}) 食物={} 剩余次数={} cookState={}",
                    maid.getName().getString(), maidUUID,
                    request != null ? request.id : "null",
                    request != null ? request.remain : -1,
                    MaidStateManager.cookState(maid, level));
                // 标记任务为 IN_PROGRESS
                TaskManager.getInstance().startInteraction(maidUUID);
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("[烹饪调试] onStart error", t);
        }
    }

    /**
     * 烹饪任务执行中调用：更新心跳
     * 使用 TaskManager.getCurrentTick() 而不是 level.getGameTime()，确保心跳计算正确
     */
    @Inject(method={"tick"}, at=@At("HEAD"), remap=false)
    private void onTick(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        try {
            UUID maidUUID = getEntityUUID(maid);
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            boolean hasBusiness = request != null && request.extraData != null && request.extraData.contains("BusinessCounter");
            if (hasBusiness) {
                // 使用 TaskManager 的 currentTick 更新心跳，确保计算一致
                long currentTick = TaskManager.getInstance().getCurrentTick();
                TaskManager.getInstance().heartbeat(maidUUID, currentTick);
                // 已删除烹饪进行中日志，避免刷屏（任务状态总览已包含这些信息）
            }
        } catch (Throwable t) {
            // 静默处理，避免影响烹饪
        }
    }

    /**
     * 烹饪任务停止时调用：输出详细的停止原因
     */
    @Inject(method={"stop"}, at=@At("HEAD"), remap=false)
    private void onStop(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        try {
            UUID maidUUID = getEntityUUID(maid);
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            boolean hasBusiness = request != null && request.extraData != null && request.extraData.contains("BusinessCounter");
            if (hasBusiness) {
                long currentTick = TaskManager.getInstance().getCurrentTick();
                Long lastLog = lastAbnormalStopLog.get(maidUUID);
                
                // 详细检查停止原因
                int targetType = BehaviorUtils.getTargetType(maid);
                boolean hasTargetPos = maid.getBrain().hasMemoryValue(ModEntities.TARGET_POS.get());
                boolean hasChairPos = maid.getBrain().hasMemoryValue(ModEntities.CHAIR_POS.get());
                MaidStateManager.CookState cookState = MaidStateManager.cookState(maid, level);
                
                String reason = "";
                if (targetType != 2) reason += " targetType=" + targetType;
                if (!hasTargetPos) reason += " noTargetPos";
                if (!hasChairPos) reason += " noChairPos";
                if (cookState != MaidStateManager.CookState.COOK) reason += " cookState=" + cookState;
                
                if (request != null && request.remain > 0) {
                    // 任务还没完成就停止了
                    if (lastLog == null || currentTick - lastLog > ABNORMAL_STOP_LOG_COOLDOWN) {
                        lastAbnormalStopLog.put(maidUUID, currentTick);
                        MaidRestaurantBusiness.LOGGER.warn("[烹饪调试] 烹饪任务异常停止: 女仆={} 食物={} 剩余次数={}{}",
                            maid.getName().getString(), request.id, request.remain,
                            reason.isEmpty() ? "" : " 原因:" + reason);
                    }
                } else {
                    // 任务完成了正常停止，不输出日志（正常停止是预期行为）
                }
            }
        } catch (Throwable t) {
            // 静默处理
        }
    }

    @Redirect(method={"check"}, at=@At(value="INVOKE", target="Lcom/mastermarisa/maid_restaurant/utils/RequestManager;pop(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;I)Lcom/mastermarisa/maid_restaurant/api/request/IRequest;"), remap=false)
    private IRequest redirectPop(EntityMaid maid, int type) {
        IRequest request = RequestManager.pop((EntityMaid)maid, (int)type);
        if (type == 0 && request instanceof CookRequest) {
            CookRequest cookRequest = (CookRequest)request;
            boolean hasBusiness = cookRequest.extraData != null && cookRequest.extraData.contains("BusinessCounter");
            boolean hasTargets = cookRequest.targets != null && cookRequest.targets.length > 0;
            if (hasBusiness) {
                UUID maidUUID = MaidCookingTaskMixin.getEntityUUID((Entity)maid);
                CookingBridge.pendingServeRequest.add(maidUUID);
                MaidRestaurantBusiness.LOGGER.info("[烹饪调试] 烹饪完成: 女仆={} 食物={} 准备发送配送请求",
                    maid.getName().getString(), cookRequest.id);
                // TaskManager：标记烹饪任务完成（会自动释放厨具占用）
                com.icewolf.maidrestaurant.business.core.TaskManager.getInstance().completeTask(maidUUID);
            }
        } else {
        }
        return request;
    }

    @Redirect(method={"check"}, at=@At(value="INVOKE", target="Lcom/mastermarisa/maid_restaurant/utils/RequestManager;post(Lnet/minecraft/server/level/ServerLevel;Lcom/mastermarisa/maid_restaurant/api/request/IRequest;I)V"), remap=false)
    private void redirectPost(ServerLevel level, IRequest request, int type) {
        if (type == 1 && request instanceof ServeRequest) {
            ServeRequest serveRequest = (ServeRequest)request;
            boolean isPending = serveRequest.provider != null && CookingBridge.pendingServeRequest.contains(serveRequest.provider);
            if (isPending) {
                CookingBridge.pendingServeRequest.remove(serveRequest.provider);
                return;
            }
        }
        RequestManager.post((ServerLevel)level, (IRequest)request, (int)type);
    }
}
