package com.icewolf.maidrestaurant.business.mixin;

import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import cn.breezeth.ordertocook.core.ModConstants;
import cn.breezeth.ordertocook.util.DataCompat;
import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 玩家订单打包挂钩：OTC 外卖袋只逐字段挑选部分 NBT，
 * 这里拦截 DataCompat.set，在写入袋 NBT 前补上 FoodList 与玩家订单字段，使其成为可拆包的玩家包裹。
 */
@Mixin(TakeoutBoxBlockEntity.class)
public abstract class TakeoutBoxBlockEntityMixin {
    @Shadow
    private NonNullList<ItemStack> inventory;

    @Redirect(
        method = "tryPackOrder",
        at = @At(
            value = "INVOKE",
            target = "Lcn/breezeth/ordertocook/util/DataCompat;set(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/nbt/CompoundTag;)V"
        ),
        remap = false
    )
    private void business$augmentPlayerBag(ItemStack bag, CompoundTag bagNbt) {
        ItemStack orderStack = this.inventory.get(0);
        CompoundTag o = orderStack.getTag();
        boolean isPlayer = o != null && o.getBoolean(PlayerOrderManager.PLAYER_ORDER);
        if (isPlayer) {
            if (o.contains(ModConstants.NBT_FOOD_LIST)) {
                bagNbt.put(ModConstants.NBT_FOOD_LIST,
                        o.getCompound(ModConstants.NBT_FOOD_LIST).copy());
            }
            bagNbt.putBoolean(PlayerOrderManager.PLAYER_ORDER, true);
            bagNbt.putBoolean(PlayerOrderManager.PLAYER_PACKAGE, true);
            if (o.hasUUID(PlayerOrderManager.BUYER_UUID)) {
                bagNbt.putUUID(PlayerOrderManager.BUYER_UUID,
                        o.getUUID(PlayerOrderManager.BUYER_UUID));
            }
            if (o.contains(PlayerOrderManager.BUYER_NAME)) {
                bagNbt.putString(PlayerOrderManager.BUYER_NAME,
                        o.getString(PlayerOrderManager.BUYER_NAME));
            }
            if (o.contains(PlayerOrderManager.PLAYER_DELIVERY_POS)) {
                bagNbt.put(PlayerOrderManager.PLAYER_DELIVERY_POS,
                        o.getCompound(PlayerOrderManager.PLAYER_DELIVERY_POS).copy());
            }
            // 补传托管所需字段：订单号 / 到期 tick / 金额 / 打单机坐标与维度
            if (o.contains(ModConstants.NBT_ORDER_ID))
                bagNbt.putString(ModConstants.NBT_ORDER_ID, o.getString(ModConstants.NBT_ORDER_ID));
            if (o.contains(ModConstants.NBT_EXPIRY_TICK))
                bagNbt.putLong(ModConstants.NBT_EXPIRY_TICK, o.getLong(ModConstants.NBT_EXPIRY_TICK));
            if (o.contains(ModConstants.NBT_PRESTIGE))
                bagNbt.putInt(ModConstants.NBT_PRESTIGE, o.getInt(ModConstants.NBT_PRESTIGE));
            if (o.contains(ModConstants.NBT_MACHINE_POS))
                bagNbt.putLong(ModConstants.NBT_MACHINE_POS, o.getLong(ModConstants.NBT_MACHINE_POS));
            if (o.contains(ModConstants.NBT_MACHINE_DIM))
                bagNbt.putString(ModConstants.NBT_MACHINE_DIM, o.getString(ModConstants.NBT_MACHINE_DIM));
            // 打包完成：托管记录订单期 -> 已打包
            try {
                net.minecraft.world.level.Level lvl =
                        ((net.minecraft.world.level.block.entity.BlockEntity)(Object) this).getLevel();
                if (lvl instanceof net.minecraft.server.level.ServerLevel sl) {
                    String oid = o.getString(ModConstants.NBT_ORDER_ID);
                    if (!oid.isEmpty())
                        com.icewolf.maidrestaurant.business.core.PlayerOrderEscrow
                                .get(sl.getServer()).markPacked(oid);
                }
            } catch (Throwable ignored) {}
        }
        DataCompat.set(bag, bagNbt);
    }
}
