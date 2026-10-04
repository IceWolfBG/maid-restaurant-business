package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import com.icewolf.maidrestaurant.business.util.ItemStackUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 点单界面设置 → 服务端：自定义该饭店菜单的点单名称（写入手持菜单 NBT 的 MenuTitle）；空名称表示恢复默认。 */
public class MenuRenamePacket implements CustomPacketPayload {
    public static final Type<MenuRenamePacket> TYPE =
            new Type<>(ResourceLocation.tryParse("maid_restaurant_business:menu_rename"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuRenamePacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> buf.writeUtf(msg.title),
                    buf -> new MenuRenamePacket(buf.readUtf())
            );

    private static final int MAX_LEN = 32;
    private final String title;

    public MenuRenamePacket(String title) {
        this.title = title == null ? "" : title.trim();
    }

    public void handle(ServerPlayer sender) {
        if (sender == null) return;
        ItemStack menu = findHeldMenu(sender);
        if (menu == null) return;
        if (this.title.isEmpty()) {
            CompoundTag tag = ItemStackUtils.getTag(menu);
            if (tag != null) {
                tag.remove(PlayerOrderManager.M_MENU_TITLE);
                ItemStackUtils.setTag(menu, tag);
            }
        } else {
            CompoundTag tag = ItemStackUtils.getOrCreateTag(menu);
            String value = this.title.length() > MAX_LEN ? this.title.substring(0, MAX_LEN) : this.title;
            tag.putString(PlayerOrderManager.M_MENU_TITLE, value);
            ItemStackUtils.setTag(menu, tag);
        }
    }

    private ItemStack findHeldMenu(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.RESTAURANT_MENU.get())) return main;
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.RESTAURANT_MENU.get())) return off;
        return null;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
