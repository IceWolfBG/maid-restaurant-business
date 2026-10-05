package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.MenuBgConstants;
import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** 客户端 → 服务端：设置当前手持饭店菜单选用的背景图文件名（空串表示恢复默认）。 */
public class MenuBgSetPacket {
    private final String name;

    public MenuBgSetPacket(String name) {
        this.name = name == null ? "" : name;
    }

    public static void encode(MenuBgSetPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name);
    }

    public static MenuBgSetPacket decode(FriendlyByteBuf buf) {
        return new MenuBgSetPacket(buf.readUtf());
    }

    public static void handle(MenuBgSetPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            ItemStack menu = findHeldMenu(sender);
            if (menu == null) return;
            String value = msg.name.trim();
            if (value.isEmpty() || !MenuBgConstants.isSafeName(value)) {
                if (menu.hasTag()) menu.getTag().remove(MenuBgConstants.NBT_KEY);
            } else {
                menu.getOrCreateTag().putString(MenuBgConstants.NBT_KEY, value);
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
