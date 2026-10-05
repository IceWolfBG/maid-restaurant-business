package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.MenuBgConstants;
import com.icewolf.maidrestaurant.business.core.MenuBgManager;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** 客户端 → 服务端：按文件名请求某背景图的字节（服务端分片用 MenuBgDataPacket 回传）。 */
public class MenuBgRequestPacket implements CustomPacketPayload {
    public static final Type<MenuBgRequestPacket> TYPE =
            new Type<>(ResourceLocation.tryParse("maid_restaurant_business:menu_bg_req"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuBgRequestPacket> STREAM_CODEC =
            StreamCodec.of(MenuBgRequestPacket::write, MenuBgRequestPacket::read);

    private final String name;

    public MenuBgRequestPacket(String name) {
        this.name = name == null ? "" : name;
    }

    public static void write(RegistryFriendlyByteBuf buf, MenuBgRequestPacket msg) {
        buf.writeUtf(msg.name);
    }

    public static MenuBgRequestPacket read(RegistryFriendlyByteBuf buf) {
        return new MenuBgRequestPacket(buf.readUtf());
    }

    public void handle(ServerPlayer sender) {
        if (sender == null) return;
        if (!MenuBgConstants.isSafeName(this.name)) return;
        byte[] data = MenuBgManager.readBytes(this.name);
        if (data == null || data.length == 0) return;
        int total = data.length;
        int chunks = (total + MenuBgConstants.CHUNK_SIZE - 1) / MenuBgConstants.CHUNK_SIZE;
        for (int i = 0; i < chunks; i++) {
            int off = i * MenuBgConstants.CHUNK_SIZE;
            int len = Math.min(MenuBgConstants.CHUNK_SIZE, total - off);
            byte[] slice = new byte[len];
            System.arraycopy(data, off, slice, 0, len);
            boolean isLast = (i == chunks - 1);
            PacketDistributor.sendToPlayer(sender, new MenuBgDataPacket(this.name, i, isLast, slice));
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
