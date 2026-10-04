package com.icewolf.maidrestaurant.business.item;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import com.icewolf.maidrestaurant.business.client.screen.RestaurantOrderScreen;
import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * 饭店菜单：玩家之间相互下单买菜的工具。
 * 右键打单机读取该店可售菜品快照；右键方块标记送餐点；右键空气打开点单界面。
 */
public class RestaurantMenuItem extends Item {
    public RestaurantMenuItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        Player player = ctx.getPlayer();
        if (player == null) return InteractionResult.PASS;
        BlockPos clicked = ctx.getClickedPos();
        if (level instanceof ServerLevel sl) {
            if (sl.getBlockEntity(clicked) instanceof OrderMachineBlockEntity) {
                PlayerOrderManager.bindMachine(sl, player, ctx.getHand(), clicked);
            } else {
                PlayerOrderManager.selectDeliveryPoint(sl, player, ctx.getHand(), clicked, ctx.getClickedFace());
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            if (PlayerOrderManager.canOpenOrderMenu(stack)) {
                openScreen(stack);
            } else {
                player.displayClientMessage(Component.translatable("message.business.menu.need_setup"), true);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @OnlyIn(Dist.CLIENT)
    private static void openScreen(ItemStack stack) {
        Minecraft.getInstance().setScreen(new RestaurantOrderScreen(stack));
    }

    @Override
    public void appendHoverText(ItemStack stack, net.minecraft.world.item.Item.TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.translatable("tooltip.business.restaurant_menu").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.business.restaurant_menu_wip").withStyle(ChatFormatting.DARK_RED));
    }
}
