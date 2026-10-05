package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.client.MenuBackgroundCache;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端 → 客户端：某背景图的一张分片（index 从 0 起，isLast 标记结束）。 */
public class MenuBgDataPacket implements CustomPacketPayload {
    public static final Type<MenuBgDataPacket> TYPE =
            new Type<>(ResourceLocation.tryParse("maid_restaurant_business:menu_bg_data"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuBgDataPacket> STREAM_CODEC =
            StreamCodec.of(MenuBgDataPacket::write, MenuBgDataPacket::read);

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

    public static void write(RegistryFriendlyByteBuf buf, MenuBgDataPacket msg) {
        buf.writeUtf(msg.name);
        buf.writeVarInt(msg.index);
        buf.writeBoolean(msg.isLast);
        buf.writeByteArray(msg.data);
    }

    public static MenuBgDataPacket read(RegistryFriendlyByteBuf buf) {
        String name = buf.readUtf();
        int index = buf.readVarInt();
        boolean isLast = buf.readBoolean();
        byte[] data = buf.readByteArray();
        return new MenuBgDataPacket(name, index, isLast, data);
    }

    public void handleClient() {
        MenuBackgroundCache.receiveChunk(this.name, this.index, this.isLast, this.data);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
