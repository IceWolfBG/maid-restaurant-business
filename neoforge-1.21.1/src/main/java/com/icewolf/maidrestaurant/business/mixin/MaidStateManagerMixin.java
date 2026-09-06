package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.utils.MaidStateManager;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 修复女仆餐厅MaidStateManager.cookState的bug：
 * 当配方不存在时，直接调用Optional.get()会导致NoSuchElementException崩溃
 * 在方法开始时检查配方是否存在，如果不存在则返回IDLE状态
 */
@Mixin(value = {MaidStateManager.class})
public class MaidStateManagerMixin {

    @Inject(method = {"cookState"}, at = {@At("HEAD")}, cancellable = true, remap = false)
    private static void business$checkRecipeExists(EntityMaid maid, Level level, CallbackInfoReturnable<MaidStateManager.CookState> cir) {
        try {
            // 获取女仆的烹饪请求
            Object request = RequestManager.peek(maid, 0);
            if (request == null) {
                return; // 没有请求，让原方法处理
            }
            if (!(request instanceof CookRequest)) {
                return; // 不是烹饪请求，让原方法处理
            }

            CookRequest cookRequest = (CookRequest) request;
            if (cookRequest.id == null) {
                return; // 没有配方ID，让原方法处理
            }

            // 检查配方是否存在
            boolean recipeExists = level.getRecipeManager().byKey(cookRequest.id).isPresent();
            if (!recipeExists) {
                // 配方不存在，返回IDLE状态，避免Optional.get()崩溃
                MaidRestaurantBusiness.LOGGER.warn("烹饪配方不存在，跳过烹饪状态检查: {}", cookRequest.id);
                cir.setReturnValue(MaidStateManager.CookState.IDLE);
            }
        } catch (Exception e) {
            // 检查过程中出现异常，让原方法处理（避免我们的代码导致新问题）
            MaidRestaurantBusiness.LOGGER.warn("检查烹饪配方时出现异常: {}", e.getMessage());
        }
    }
}
