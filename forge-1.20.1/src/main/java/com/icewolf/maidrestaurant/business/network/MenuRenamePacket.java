package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** 点单界面设置 → 服务端：自定义该饭店菜单的点单名称（写入手持菜单 NBT 的 MenuTitle）；空名称表示恢复默认。 */
public class MenuRenamePacket {
    private static final int MAX_LEN = 32;
    private final String title;

    public MenuRenamePacket(String title) {
        this.title = title == null ? "" : title.trim();
    }

    public static void encode(MenuRenamePacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.title);
    }

    public static MenuRenamePacket decode(FriendlyByteBuf buf) {
        return new MenuRenamePacket(buf.readUtf());
    }

    public static void handle(MenuRenamePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            ItemStack menu = findHeldMenu(sender);
            if (menu == null) return;
            if (msg.title.isEmpty()) {
                if (menu.hasTag()) menu.getTag().remove(PlayerOrderManager.M_MENU_TITLE);
            } else {
                String value = msg.title.length() > MAX_LEN ? msg.title.substring(0, MAX_LEN) : msg.title;
                menu.getOrCreateTag().putString(PlayerOrderManager.M_MENU_TITLE, value);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    private static ItemStack findHeldMenu(ServerPlayer player) {
        ItemStack main = player.getMainHandItem();
        if (main.is(ModItems.RESTAURANT_MENU.get())) return main;
        ItemStack off = player.getOffhandItem();
        if (off.is(ModItems.RESTAURANT_MENU.get())) return off;
        return null;
    }
}
