package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.client.MenuBackgroundCache;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** 服务端 → 客户端：当前服务器图库内的背景图文件名列表。 */
public class MenuBgListPacket {
    private final List<String> names;

    public MenuBgListPacket(List<String> names) {
        this.names = names == null ? new ArrayList<>() : names;
    }

    public static void encode(MenuBgListPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.names.size());
        for (String n : msg.names) buf.writeUtf(n);
    }

    public static MenuBgListPacket decode(FriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(buf.readUtf());
        return new MenuBgListPacket(list);
    }

    public static void handle(MenuBgListPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> MenuBackgroundCache.setServerNames(msg.names));
        ctx.get().setPacketHandled(true);
    }
}
