package com.icewolf.maidrestaurant.business.mixin;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import com.icewolf.maidrestaurant.business.core.OrderBridge;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 注入OrderMachineBlockEntity.refreshOrders()方法
 * 在订单刷新完成后触发我们的逻辑，实现"订单刷新后延迟接单"
 */
@Mixin(OrderMachineBlockEntity.class)
public class OrderMachineBlockEntityMixin {
    
    /**
     * 在refreshOrders()方法执行完成后注入
     * 记录订单刷新时间，用于自动接单延迟计算
     */
    @Inject(method = "refreshOrders", at = @At("TAIL"), remap = false)
    private void onRefreshOrders(CallbackInfo ci) {
        try {
            OrderMachineBlockEntity be = (OrderMachineBlockEntity)(Object)this;
            Level level = be.getLevel();
            BlockPos pos = be.getBlockPos();
            
            if (level != null && !level.isClientSide) {
                // 通知OrderBridge订单已刷新，记录刷新时间
                OrderBridge.onOrderMachineRefreshed(level, pos);
            }
        } catch (Throwable t) {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error(
                "[订单刷新] Mixin注入失败", t);
        }
    }
}
