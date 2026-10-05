package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.client.MenuBackgroundCache;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

/** 服务端 → 客户端：某背景图的一张分片（index 从 0 起，isLast 标记结束）。 */
public class MenuBgDataPacket {
    private final String name;
    private final int index;
    private final boolean isLast;
    private final byte[] data;

    public MenuBgDataPacket(String name, int index, boolean isLast, byte[] data) {
        this.name = name;
        this.index = index;
        this.isLast = isLast;
        this.data = data;
    }

    public static void encode(MenuBgDataPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name);
        buf.writeVarInt(msg.index);
        buf.writeBoolean(msg.isLast);
        buf.writeByteArray(msg.data);
    }

    public static MenuBgDataPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        int index = buf.readVarInt();
        boolean isLast = buf.readBoolean();
        byte[] data = buf.readByteArray();
        return new MenuBgDataPacket(name, index, isLast, data);
    }

    public static void handle(MenuBgDataPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                MenuBackgroundCache.receiveChunk(msg.name, msg.index, msg.isLast, msg.data));
        ctx.get().setPacketHandled(true);
    }
}
