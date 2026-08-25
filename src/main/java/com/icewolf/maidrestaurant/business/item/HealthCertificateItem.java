/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid
 *  javax.annotation.Nullable
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.TooltipFlag
 *  net.minecraft.world.item.context.UseOnContext
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraftforge.api.distmarker.Dist
 *  net.minecraftforge.api.distmarker.OnlyIn
 */
package com.icewolf.maidrestaurant.business.item;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

public class HealthCertificateItem
extends Item {
    public static final String TAG_MACHINE_X = "MachineX";
    public static final String TAG_MACHINE_Y = "MachineY";
    public static final String TAG_MACHINE_Z = "MachineZ";
    public static final String TAG_MAID_UUID = "MaidUUID";
    public static final String TAG_MAID_NAME = "MaidName";
    public static final String TAG_HAS_MACHINE = "HasMachine";
    public static final String TAG_HAS_MAID = "HasMaid";

    public HealthCertificateItem() {
        super(new Item.Properties().stacksTo(1));
    }

    @Nullable
    private static UUID getEntityUUID(Entity entity) {
        // Entity.getUUID() 是公共方法，直接调用即可，不需要反射
        return entity.getUUID();
    }

    private static String getEntityName(Entity entity) {
        try {
            return entity.getName().getString();
        }
        catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("Failed to get entity name", (Throwable)e);
            return entity.getType().getDescription().getString();
        }
    }

    public boolean isFoil(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && (tag.getBoolean(TAG_HAS_MACHINE) || tag.getBoolean(TAG_HAS_MAID));
    }

    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(context.getClickedPos());
        if (be != null && be.getClass().getName().contains("OrderMachineBlockEntity")) {
            ItemStack stack = context.getItemInHand();
            CompoundTag tag = stack.getOrCreateTag();
            tag.putInt(TAG_MACHINE_X, context.getClickedPos().getX());
            tag.putInt(TAG_MACHINE_Y, context.getClickedPos().getY());
            tag.putInt(TAG_MACHINE_Z, context.getClickedPos().getZ());
            tag.putBoolean(TAG_HAS_MACHINE, true);
            if (context.getPlayer() != null) {
                context.getPlayer().sendSystemMessage((Component)Component.literal((String)"\u00a7a\u5065\u5eb7\u8bc1\u5df2\u7ed1\u5b9a\u6253\u5355\u673a\u4f4d\u7f6e"));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (player.level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (target instanceof EntityMaid) {
            EntityMaid maid = (EntityMaid)target;
            UUID maidUUID = HealthCertificateItem.getEntityUUID((Entity)maid);
            if (maidUUID == null) {
                player.sendSystemMessage((Component)Component.literal((String)"\u00a7c\u65e0\u6cd5\u83b7\u53d6\u5973\u4ec6UUID"));
                return InteractionResult.FAIL;
            }
            CompoundTag tag = stack.getOrCreateTag();
            tag.putUUID(TAG_MAID_UUID, maidUUID);
            tag.putString(TAG_MAID_NAME, HealthCertificateItem.getEntityName((Entity)maid));
            tag.putBoolean(TAG_HAS_MAID, true);
            player.sendSystemMessage((Component)Component.literal((String)("\u00a7a\u5065\u5eb7\u8bc1\u5df2\u7ed1\u5b9a\u5973\u4ec6: " + HealthCertificateItem.getEntityName((Entity)maid))));
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static boolean hasMachine(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_HAS_MACHINE);
    }

    public static boolean hasMaid(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(TAG_HAS_MAID);
    }

    @Nullable
    public static BlockPos getMachinePos(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_HAS_MACHINE)) {
            return null;
        }
        return new BlockPos(tag.getInt(TAG_MACHINE_X), tag.getInt(TAG_MACHINE_Y), tag.getInt(TAG_MACHINE_Z));
    }

    @Nullable
    public static UUID getMaidUUID(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_HAS_MAID)) {
            return null;
        }
        return tag.getUUID(TAG_MAID_UUID);
    }

    @Nullable
    public static String getMaidName(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.getBoolean(TAG_HAS_MAID)) {
            return null;
        }
        return tag.getString(TAG_MAID_NAME);
    }

    @OnlyIn(value=Dist.CLIENT)
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        CompoundTag tag = stack.getTag();
        // 使用说明（车万女仆饰品系统会自动添加[女仆饰品]标识，这里不重复添加）
        tooltip.add(Component.translatable("tooltips.maid_restaurant_business.health_certificate.usage").withStyle(ChatFormatting.GRAY));
        if (tag != null) {
            tooltip.add(Component.empty());
            // 打单机绑定信息
            if (tag.getBoolean(TAG_HAS_MACHINE)) {
                int x = tag.getInt(TAG_MACHINE_X);
                int y = tag.getInt(TAG_MACHINE_Y);
                int z = tag.getInt(TAG_MACHINE_Z);
                tooltip.add(Component.translatable("tooltips.maid_restaurant_business.health_certificate.bound_machine").withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.literal("  " + x + ", " + y + ", " + z).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("tooltips.maid_restaurant_business.health_certificate.no_machine").withStyle(ChatFormatting.RED));
            }
            // 女仆绑定信息
            if (tag.getBoolean(TAG_HAS_MAID)) {
                String maidName = tag.getString(TAG_MAID_NAME);
                if (maidName == null || maidName.isEmpty()) {
                    maidName = "未知女仆";
                }
                tooltip.add(Component.translatable("tooltips.maid_restaurant_business.health_certificate.bound_maid").withStyle(ChatFormatting.GOLD));
                tooltip.add(Component.literal("  " + maidName).withStyle(ChatFormatting.GRAY));
            } else {
                tooltip.add(Component.translatable("tooltips.maid_restaurant_business.health_certificate.no_maid").withStyle(ChatFormatting.RED));
            }
        }
    }
}
