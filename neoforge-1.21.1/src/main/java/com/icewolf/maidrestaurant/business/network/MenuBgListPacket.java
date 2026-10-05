package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.client.MenuBackgroundCache;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** 服务端 → 客户端：当前服务器图库内的背景图文件名列表。 */
public class MenuBgListPacket implements CustomPacketPayload {
    public static final Type<MenuBgListPacket> TYPE =
            new Type<>(ResourceLocation.tryParse("maid_restaurant_business:menu_bg_list"));

    public static final StreamCodec<RegistryFriendlyByteBuf, MenuBgListPacket> STREAM_CODEC =
            StreamCodec.of(MenuBgListPacket::write, MenuBgListPacket::read);

    private final List<String> names;

    public MenuBgListPacket(List<String> names) {
        this.names = names == null ? new ArrayList<>() : names;
    }

    public static void write(RegistryFriendlyByteBuf buf, MenuBgListPacket msg) {
        buf.writeVarInt(msg.names.size());
        for (String n : msg.names) buf.writeUtf(n);
    }

    public static MenuBgListPacket read(RegistryFriendlyByteBuf buf) {
        int n = buf.readVarInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < n; i++) list.add(buf.readUtf());
        return new MenuBgListPacket(list);
    }

    public void handleClient() {
        MenuBackgroundCache.setServerNames(this.names);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
