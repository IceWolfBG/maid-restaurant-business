package com.icewolf.maidrestaurant.business.util;

import cn.breezeth.ordertocook.util.DataCompat;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

/**
 * 物品NBT工具类
 * 使用otc的DataCompat类来读取和写入物品的自定义数据
 * 兼容Minecraft 1.21.1的Data Components系统
 */
public class ItemStackUtils {
    @Nullable
    public static CompoundTag getTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        return DataCompat.copy(stack);
    }

    public static CompoundTag getOrCreateTag(ItemStack stack) {
        CompoundTag tag = DataCompat.copy(stack);
        if (tag == null) {
            tag = new CompoundTag();
            DataCompat.set(stack, tag);
        }
        return tag;
    }

    public static boolean hasTag(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return false;
        return DataCompat.copy(stack) != null;
    }

    public static void setTag(ItemStack stack, @Nullable CompoundTag tag) {
        if (stack == null || stack.isEmpty()) return;
        if (tag == null) {
            stack.remove(DataComponents.CUSTOM_DATA);
        } else {
            DataCompat.set(stack, tag);
        }
    }
}
