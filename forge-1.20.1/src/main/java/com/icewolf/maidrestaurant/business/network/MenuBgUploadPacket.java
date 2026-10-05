package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.MenuBgConstants;
import com.icewolf.maidrestaurant.business.core.MenuBgManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** 客户端 → 服务端：上传一张背景图（分片）。收齐后写入服务器图库并广播最新列表。 */
public class MenuBgUploadPacket {
    private final String name;
    private final int index;
    private final boolean isLast;
    private final byte[] data;

    public MenuBgUploadPacket(String name, int index, boolean isLast, byte[] data) {
        this.name = name;
        this.index = index;
        this.isLast = isLast;
        this.data = data;
    }

    public static void encode(MenuBgUploadPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.name);
        buf.writeVarInt(msg.index);
        buf.writeBoolean(msg.isLast);
        buf.writeByteArray(msg.data);
    }

    public static MenuBgUploadPacket decode(FriendlyByteBuf buf) {
        String name = buf.readUtf();
        int index = buf.readVarInt();
        boolean isLast = buf.readBoolean();
        byte[] data = buf.readByteArray();
        return new MenuBgUploadPacket(name, index, isLast, data);
    }

    /** 按 玩家名+文件名 暂存分片，避免多人同时上传同名互相干扰。 */
    private static final Map<String, ByteArrayOutputStream> BUF = new ConcurrentHashMap<>();

    public static void handle(MenuBgUploadPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            if (!MenuBgConstants.isSafeName(msg.name)) return;
            String key = sender.getName().getString() + "\u0000" + msg.name;
            ByteArrayOutputStream bos = BUF.computeIfAbsent(key, k -> new ByteArrayOutputStream());
            try {
                bos.write(msg.data);
            } catch (IOException e) {
                BUF.remove(key);
                return;
            }
            if (!msg.isLast) return;

            byte[] all = bos.toByteArray();
            BUF.remove(key);
            if (all.length > MenuBgConstants.MAX_FILE_BYTES) return; // 双保险，超限丢弃
            if (MenuBgManager.saveBytes(msg.name, all)) {
                List<String> names = MenuBgManager.listNames();
                MenuBgListPacket list = new MenuBgListPacket(names);
                for (ServerPlayer p : sender.getServer().getPlayerList().getPlayers()) {
                    ModMessages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> p), list);
                }
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
