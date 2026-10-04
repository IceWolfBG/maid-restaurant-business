package com.icewolf.maidrestaurant.business.mixin;

import cn.breezeth.ordertocook.item.TakeoutBagItem;
import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 玩家外卖袋包裹：Shift+右键拆包还原食物；非拆包操作沿用 OTC 原逻辑（玩家订单无 NPC，不会误交付）。 */
@Mixin(TakeoutBagItem.class)
public class TakeoutBagItemMixin {
    @Inject(method = {"use", "m_7203_"}, at = @At("HEAD"), remap = false, cancellable = true)
    private void business$onUse(Level world, Player user, InteractionHand hand,
                                CallbackInfoReturnable<InteractionResultHolder<ItemStack>> cir) {
        ItemStack stack = user.getItemInHand(hand);
        if (business$isPlayerPackage(stack) && user.isShiftKeyDown()) {
            if (!world.isClientSide) {
                PlayerOrderManager.unpack(world, user, stack);
            }
            cir.setReturnValue(InteractionResultHolder.sidedSuccess(stack, world.isClientSide));
        }
    }

    @Inject(method = {"interactLivingEntity", "m_6880_"}, at = @At("HEAD"), remap = false, cancellable = true)
    private void business$onInteract(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (business$isPlayerPackage(stack) && user.isShiftKeyDown()) {
            if (!user.level().isClientSide) {
                PlayerOrderManager.unpack(user.level(), user, stack);
            }
            cir.setReturnValue(InteractionResult.sidedSuccess(user.level().isClientSide));
        }
    }

    private static boolean business$isPlayerPackage(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(PlayerOrderManager.PLAYER_PACKAGE);
    }
}
