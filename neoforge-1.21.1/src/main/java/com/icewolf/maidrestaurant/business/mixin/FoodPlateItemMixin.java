package com.icewolf.maidrestaurant.business.mixin;

import cn.breezeth.ordertocook.item.FoodPlateItem;
import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 玩家餐盘包裹：Shift+右键拆包；在选定送餐点右键则放置/入容器完成交付。 */
@Mixin(FoodPlateItem.class)
public class FoodPlateItemMixin {
    @Inject(method = "use", at = @At("HEAD"), cancellable = true)
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

    @Inject(method = "interactLivingEntity", at = @At("HEAD"), cancellable = true)
    private void business$onInteract(ItemStack stack, Player user, LivingEntity entity, InteractionHand hand,
                                     CallbackInfoReturnable<InteractionResult> cir) {
        if (business$isPlayerPackage(stack) && user.isShiftKeyDown()) {
            if (!user.level().isClientSide) {
                PlayerOrderManager.unpack(user.level(), user, stack);
            }
            cir.setReturnValue(InteractionResult.sidedSuccess(user.level().isClientSide));
        }
    }

    @Inject(method = "useOn", at = @At("HEAD"), cancellable = true)
    private void business$onUseOn(UseOnContext ctx, CallbackInfoReturnable<InteractionResult> cir) {
        ItemStack stack = ctx.getItemInHand();
        Player user = ctx.getPlayer();
        if (!business$isPlayerPackage(stack) || user == null) return;
        Level world = ctx.getLevel();

        if (user.isShiftKeyDown()) {
            if (!world.isClientSide) {
                PlayerOrderManager.unpack(world, user, stack);
            }
            cir.setReturnValue(InteractionResult.sidedSuccess(world.isClientSide));
            return;
        }

        if (!world.isClientSide && world instanceof ServerLevel sl) {
            CompoundTag tag = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(stack);
            CompoundTag dp = tag.getCompound(PlayerOrderManager.PLAYER_DELIVERY_POS);
            BlockPos target = new BlockPos(dp.getInt("x"), dp.getInt("y"), dp.getInt("z"));
            BlockPos clicked = ctx.getClickedPos();
            BlockPlaceContext placeCtx = new BlockPlaceContext(ctx);
            BlockPos intended = world.getBlockState(clicked).canBeReplaced(placeCtx)
                ? clicked
                : clicked.relative(ctx.getClickedFace());
            if (intended.equals(target)) {
                boolean ok = PlayerOrderManager.placePackageAt(sl, stack, target);
                cir.setReturnValue(ok ? InteractionResult.CONSUME : InteractionResult.FAIL);
            }
        }
    }

    private static boolean business$isPlayerPackage(ItemStack stack) {
        CompoundTag tag = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(stack);
        return tag != null && tag.getBoolean(PlayerOrderManager.PLAYER_PACKAGE);
    }
}
