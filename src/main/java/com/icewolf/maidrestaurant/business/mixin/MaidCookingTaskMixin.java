/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid
 *  com.mastermarisa.maid_restaurant.api.request.IRequest
 *  com.mastermarisa.maid_restaurant.maid.task.cook.MaidCookingTask
 *  com.mastermarisa.maid_restaurant.request.CookRequest
 *  com.mastermarisa.maid_restaurant.request.ServeRequest
 *  com.mastermarisa.maid_restaurant.utils.RequestManager
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.Entity
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Redirect
 */
package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.core.CookingBridge;
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
import org.spongepowered.asm.mixin.injection.Redirect;

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

    @Redirect(method={"check"}, at=@At(value="INVOKE", target="Lcom/mastermarisa/maid_restaurant/utils/RequestManager;pop(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;I)Lcom/mastermarisa/maid_restaurant/api/request/IRequest;"), remap=false)
    private IRequest redirectPop(EntityMaid maid, int type) {
        IRequest request = RequestManager.pop((EntityMaid)maid, (int)type);
        com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.info("[Mixin] redirectPop 被调用: maid={}, type={}, requestType={}", maid.getName().getString(), type, request != null ? request.getClass().getSimpleName() : "null");
        if (type == 0 && request instanceof CookRequest) {
            CookRequest cookRequest = (CookRequest)request;
            boolean hasBusiness = cookRequest.extraData != null && cookRequest.extraData.contains("BusinessCounter");
            boolean hasTargets = cookRequest.targets != null && cookRequest.targets.length > 0;
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.info("[Mixin] redirectPop: maid={}, type={}, hasBusiness={}, hasTargets={}, remain={}, targets长度={}", 
                maid.getName().getString(), type, hasBusiness, hasTargets, cookRequest.remain, hasTargets ? cookRequest.targets.length : 0);
            if (hasBusiness) {
                UUID maidUUID = MaidCookingTaskMixin.getEntityUUID((Entity)maid);
                CookingBridge.pendingServeRequest.add(maidUUID);
                // TaskManager：标记烹饪任务完成（会自动释放厨具占用）
                com.icewolf.maidrestaurant.business.core.TaskManager.getInstance().completeTask(maidUUID);
                com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.info("[Mixin] redirectPop: added {} to pendingServeRequest, size={}, TaskManager任务已完成（厨具自动释放）", maidUUID, CookingBridge.pendingServeRequest.size());
            }
        } else {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.info("[Mixin] redirectPop: 不满足释放条件 type={} isCookRequest={}", type, request instanceof CookRequest);
        }
        return request;
    }

    @Redirect(method={"check"}, at=@At(value="INVOKE", target="Lcom/mastermarisa/maid_restaurant/utils/RequestManager;post(Lnet/minecraft/server/level/ServerLevel;Lcom/mastermarisa/maid_restaurant/api/request/IRequest;I)V"), remap=false)
    private void redirectPost(ServerLevel level, IRequest request, int type) {
        if (type == 1 && request instanceof ServeRequest) {
            ServeRequest serveRequest = (ServeRequest)request;
            boolean isPending = serveRequest.provider != null && CookingBridge.pendingServeRequest.contains(serveRequest.provider);
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.info("[Mixin] redirectPost: type={}, provider={}, isPending={}, pendingSize={}", type, serveRequest.provider, isPending, CookingBridge.pendingServeRequest.size());
            if (isPending) {
                CookingBridge.pendingServeRequest.remove(serveRequest.provider);
                com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.info("[Mixin] redirectPost: CANCELLED ServeRequest for provider={}", serveRequest.provider);
                return;
            }
        }
        RequestManager.post((ServerLevel)level, (IRequest)request, (int)type);
    }
}
