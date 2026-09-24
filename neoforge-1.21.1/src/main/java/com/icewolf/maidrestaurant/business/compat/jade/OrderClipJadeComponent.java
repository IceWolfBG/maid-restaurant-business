package com.icewolf.maidrestaurant.business.compat.jade;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * 挂单夹 Jade 客户端组件：读取服务端下发的 FoodList，逐行显示“菜品名 ×数量”。
 * 无服务端数据（订单内容本不同步客户端）时不显示任何额外内容。
 */
public class OrderClipJadeComponent implements IBlockComponentProvider {
    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (data == null || !data.contains(OrderClipJadeData.TAG_FOOD_LIST)) {
            return;
        }
        CompoundTag foodList = data.getCompound(OrderClipJadeData.TAG_FOOD_LIST);
        tooltip.add(Component.translatable("jade.maid_restaurant_business.order_requirements"));
        for (String key : foodList.getAllKeys()) {
            int count = foodList.getInt(key);
            tooltip.add(formatLine(key, count));
        }
    }

    private static MutableComponent formatLine(String itemId, int count) {
        Component name;
        ResourceLocation rl = ResourceLocation.tryParse(itemId);
        Item item = rl == null ? null : BuiltInRegistries.ITEM.get(rl);
        if (item == null || item == Items.AIR) {
            name = Component.literal(itemId);
        } else {
            name = new ItemStack(item).getHoverName();
        }
        return Component.literal("  · ")
                .append(name)
                .append(Component.literal(" ×" + count));
    }

    @Override
    public ResourceLocation getUid() {
        return OrderClipJadeData.UID;
    }
}
