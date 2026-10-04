package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 玩家包裹拆包拦截。
 *
 * 外卖袋物品（TakeoutBagItem）本身带 food 属性、且过期后 OTC 会让玩家把它吃掉。
 * 这里在 {@code Item.use}（进食入口）之前，通过 Forge 交互事件拦截「潜行 + 右键」，
 * 直接走 {@link PlayerOrderManager#unpack} 拆包，避免玩家把整袋菜吃掉。
 *
 * 优先级设为 HIGHEST：整合包内部分模组会在常规优先级把右键事件取消（返回 CONSUME），
 * 导致本监听器收不到事件、且 Item.use 被跳过。提到最高可抢先完成拆包。
 * 事件经 {@code ForgeHooks.onItemRightClick} 在 itemstack.use 之前触发，取消即跳过进食。
 * 三条路径（右键空气 / 右键方块 / 右键实体）都覆盖。
 */
@Mod.EventBusSubscriber(modid = MaidRestaurantBusiness.MOD_ID)
public class PlayerPackageInteractHandler {

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (tryUnpack(event)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (tryUnpack(event)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (tryUnpack(event)) {
            event.setCanceled(true);
            event.setCancellationResult(InteractionResult.SUCCESS);
        }
    }

    /**
     * 满足「潜行 + 手持玩家包裹」则在服务端拆包。
     * 返回 true 表示应取消原始交互（含进食），客户端仅拦截、不执行拆包。
     */
    private static boolean tryUnpack(PlayerInteractEvent event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) return false;
        CompoundTag tag = stack.getTag();
        boolean isPkg = tag != null && tag.getBoolean(PlayerOrderManager.PLAYER_PACKAGE);
        if (!isPkg) return false;
        if (!player.isShiftKeyDown()) return false;
        if (!player.level().isClientSide) {
            PlayerOrderManager.unpack(player.level(), player, stack);
        }
        return true;
    }
}
