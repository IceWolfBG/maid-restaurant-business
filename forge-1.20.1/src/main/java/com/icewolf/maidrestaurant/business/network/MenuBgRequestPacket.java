package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.MenuBgConstants;
import com.icewolf.maidrestaurant.business.core.MenuBgManager;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** 客户端 → 服务端：按文件名请求某背景图的字节（服务端分片用 MenuBgDataPacket 回传）。 */
public class MenuBgRequestPacket {
    private final String name;

    public MenuBgRequestPacket(String name) {
        this.name = name == null ? "" : name;
    }

    public static void encode(MenuBgRequestPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name);
    }

    public static MenuBgRequestPacket decode(FriendlyByteBuf buf) {
        return new MenuBgRequestPacket(buf.readUtf());
    }

    public static void handle(MenuBgRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!MenuBgConstants.isSafeName(msg.name)) return;
            byte[] data = MenuBgManager.readBytes(msg.name);
            if (data == null || data.length == 0) return;
            int total = data.length;
            int chunks = (total + MenuBgConstants.CHUNK_SIZE - 1) / MenuBgConstants.CHUNK_SIZE;
            for (int i = 0; i < chunks; i++) {
                int off = i * MenuBgConstants.CHUNK_SIZE;
                int len = Math.min(MenuBgConstants.CHUNK_SIZE, total - off);
                byte[] slice = new byte[len];
                System.arraycopy(data, off, slice, 0, len);
                boolean isLast = (i == chunks - 1);
                ModMessages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender),
                        new MenuBgDataPacket(msg.name, i, isLast, slice));
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
