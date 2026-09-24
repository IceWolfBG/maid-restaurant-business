package com.icewolf.maidrestaurant.business.compat.jade;

import com.icewolf.maidrestaurant.business.block.entity.OrderClipBlockEntity;
import com.icewolf.maidrestaurant.business.util.ItemStackUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IServerDataProvider;

/**
 * 挂单夹 Jade 服务端数据提供者：读取夹着的订单，把 FoodList（菜品 id -> 数量）写入同步数据。
 * 仅在 Jade 请求挂单夹时调用，频率由 Jade 控制，不增加常驻开销。
 */
public class OrderClipJadeData implements IServerDataProvider<BlockAccessor> {
    public static final ResourceLocation UID =
            ResourceLocation.fromNamespaceAndPath("maid_restaurant_business", "order_clip");
    public static final String TAG_FOOD_LIST = "FoodList";

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (!(accessor.getBlockEntity() instanceof OrderClipBlockEntity clip)) {
            return;
        }
        ItemStack order = clip.content();
        if (order.isEmpty()) {
            return;
        }
        CompoundTag tag = ItemStackUtils.getTag(order);
        if (tag == null || !tag.contains(TAG_FOOD_LIST)) {
            return;
        }
        data.put(TAG_FOOD_LIST, tag.getCompound(TAG_FOOD_LIST).copy());
    }

    @Override
    public ResourceLocation getUid() {
        return UID;
    }
}
