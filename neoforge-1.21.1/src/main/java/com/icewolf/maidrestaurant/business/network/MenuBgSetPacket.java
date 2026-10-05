package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.MenuBgConstants;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import com.icewolf.maidrestaurant.business.util.ItemStackUtils;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 客户端 → 服务端：设置当前手持饭店菜单选用的背景图文件名（空串表示恢复默认）。 */
public class MenuBgSetPacket implements CustomPacketPayload {
    public static final Type<MenuBgSetPacket> TYPE =
            new Type<>(ResourceLocation.tryParse("maid_restaurant_business:menu_bg_set"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuBgSetPacket> STREAM_CODEC =
            StreamCodec.of(MenuBgSetPacket::write, MenuBgSetPacket::read);

    private final String name;

    public MenuBgSetPacket(String name) {
        this.name = name == null ? "" : name;
    }

    public static void write(RegistryFriendlyByteBuf buf, MenuBgSetPacket msg) {
        buf.writeUtf(msg.name);
    }

    public static MenuBgSetPacket read(RegistryFriendlyByteBuf buf) {
        return new MenuBgSetPacket(buf.readUtf());
    }

    public void handle(ServerPlayer sender) {
        if (sender == null) return;
        ItemStack menu = findHeldMenu(sender);
        if (menu == null) return;
        String value = this.name.trim();
        if (value.isEmpty() || !MenuBgConstants.isSafeName(value)) {
            CompoundTag tag = ItemStackUtils.getTag(menu);
            if (tag != null) {
                tag.remove(MenuBgConstants.NBT_KEY);
                ItemStackUtils.setTag(menu, tag);
            }
        } else {
            CompoundTag tag = ItemStackUtils.getOrCreateTag(menu);
            tag.putString(MenuBgConstants.NBT_KEY, value);
            ItemStackUtils.setTag(menu, tag);
        }
    }

    private static ItemStack findHeldMenu(ServerPlayer player) {
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
