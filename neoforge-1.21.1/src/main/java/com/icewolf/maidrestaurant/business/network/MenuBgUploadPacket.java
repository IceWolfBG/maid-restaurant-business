package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.MenuBgConstants;
import com.icewolf.maidrestaurant.business.core.MenuBgManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;

/** 客户端 → 服务端：上传一张背景图（分片）。收齐后写入服务器图库并广播最新列表。 */
public class MenuBgUploadPacket implements CustomPacketPayload {
    public static final Type<MenuBgUploadPacket> TYPE =
            new Type<>(ResourceLocation.tryParse("maid_restaurant_business:menu_bg_upload"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuBgUploadPacket> STREAM_CODEC =
            StreamCodec.of(MenuBgUploadPacket::write, MenuBgUploadPacket::read);

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

    public static void write(RegistryFriendlyByteBuf buf, MenuBgUploadPacket msg) {
        buf.writeUtf(msg.name);
        buf.writeVarInt(msg.index);
        buf.writeBoolean(msg.isLast);
        buf.writeByteArray(msg.data);
    }

    public static MenuBgUploadPacket read(RegistryFriendlyByteBuf buf) {
        String name = buf.readUtf();
        int index = buf.readVarInt();
        boolean isLast = buf.readBoolean();
        byte[] data = buf.readByteArray();
        return new MenuBgUploadPacket(name, index, isLast, data);
    }

    /** 按 玩家名+文件名 暂存分片，避免多人同时上传同名互相干扰。 */
    private static final Map<String, ByteArrayOutputStream> BUF = new ConcurrentHashMap<>();

    public void handle(ServerPlayer sender) {
        if (sender == null) return;
        if (!MenuBgConstants.isSafeName(this.name)) return;
        String key = sender.getName().getString() + "\u0000" + this.name;
        ByteArrayOutputStream bos = BUF.computeIfAbsent(key, k -> new ByteArrayOutputStream());
        try {
            bos.write(this.data);
        } catch (IOException e) {
            BUF.remove(key);
            return;
        }
        if (!this.isLast) return;

        byte[] all = bos.toByteArray();
        BUF.remove(key);
        if (all.length > MenuBgConstants.MAX_FILE_BYTES) return; // 双保险，超限丢弃
        if (MenuBgManager.saveBytes(this.name, all)) {
            List<String> names = MenuBgManager.listNames();
            MenuBgListPacket list = new MenuBgListPacket(names);
            for (ServerPlayer p : sender.getServer().getPlayerList().getPlayers()) {
                PacketDistributor.sendToPlayer(p, list);
            }
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
