package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.core.CookingBridge;
import com.icewolf.maidrestaurant.business.core.TaskManager;
import com.mastermarisa.maid_restaurant.api.request.IRequest;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidCookingTask;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.request.ServeRequest;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import java.lang.reflect.Method;
import java.util.UUID;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value={MaidCookingTask.class})
public class MaidCookingTaskMixin {
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
     * 烹饪任务开始：把营业中任务从 ASSIGNED 标记为 IN_PROGRESS。
     * 这样 failTask 的 IN_PROGRESS 铁律对烹饪生效，且进入 IN_PROGRESS 后不再受 ASSIGNED 硬超时约束。
     */
    @Inject(method={"start"}, at=@At("HEAD"), remap=false)
    private void onStart(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            if (request != null && request.extraData != null && request.extraData.contains("BusinessCounter")) {
                TaskManager.getInstance().startInteraction(getEntityUUID(maid));
            }
        } catch (Throwable t) {
        }
    }

    /**
     * 烹饪执行中：以 TaskManager 当前 tick 更新心跳，保证卡住检测与超时判断口径一致。
     */
    @Inject(method={"tick"}, at=@At("HEAD"), remap=false)
    private void onTick(ServerLevel level, EntityMaid maid, long gameTime, CallbackInfo ci) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            if (request != null && request.extraData != null && request.extraData.contains("BusinessCounter")) {
                long currentTick = TaskManager.getInstance().getCurrentTick();
                TaskManager.getInstance().heartbeat(getEntityUUID(maid), currentTick);
            }
        } catch (Throwable t) {
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
