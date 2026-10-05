package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.MenuBgConstants;
import com.icewolf.maidrestaurant.business.core.MenuBgManager;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

/** 客户端 → 服务端：请求当前服务器图库的文件名列表。 */
public class MenuBgListRequestPacket {
    public MenuBgListRequestPacket() {}

    public static void encode(MenuBgListRequestPacket msg, FriendlyByteBuf buf) {}

    public static MenuBgListRequestPacket decode(FriendlyByteBuf buf) {
        return new MenuBgListRequestPacket();
    }

    public static void handle(MenuBgListRequestPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null) return;
            List<String> names = MenuBgManager.listNames();
            // 模组 jar 内置背景始终可选，且不占用服务器图库目录
            if (!names.contains(MenuBgConstants.BUILTIN_NAME)) {
                names.add(0, MenuBgConstants.BUILTIN_NAME);
            }
            ModMessages.INSTANCE.send(PacketDistributor.PLAYER.with(() -> sender), new MenuBgListPacket(names));
        });
        ctx.get().setPacketHandled(true);
    }
}
