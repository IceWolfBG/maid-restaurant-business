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

/** 客户端 → 服务端：请求当前服务器图库的文件名列表。 */
public class MenuBgListRequestPacket implements CustomPacketPayload {
    public static final Type<MenuBgListRequestPacket> TYPE =
            new Type<>(ResourceLocation.tryParse("maid_restaurant_business:menu_bg_list_req"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuBgListRequestPacket> STREAM_CODEC =
            StreamCodec.of((buf, msg) -> {}, buf -> new MenuBgListRequestPacket());

    public MenuBgListRequestPacket() {}

    public void handle(ServerPlayer sender) {
        if (sender == null) return;
        List<String> names = MenuBgManager.listNames();
        // 模组 jar 内置背景始终可选，且不占用服务器图库目录
        if (!names.contains(MenuBgConstants.BUILTIN_NAME)) {
            names.add(0, MenuBgConstants.BUILTIN_NAME);
        }
        PacketDistributor.sendToPlayer(sender, new MenuBgListPacket(names));
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
