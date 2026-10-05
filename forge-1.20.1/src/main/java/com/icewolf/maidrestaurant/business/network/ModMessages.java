package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class ModMessages {
    private static final String PROTOCOL_VERSION = "1";
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
        new ResourceLocation(MaidRestaurantBusiness.MOD_ID, "main"),
        () -> PROTOCOL_VERSION,
        PROTOCOL_VERSION::equals,
        PROTOCOL_VERSION::equals
    );

    private static int id = 0;

    public static void register() {
        INSTANCE.registerMessage(id++, ScheduleBoardUpdatePacket.class,
            ScheduleBoardUpdatePacket::encode,
            ScheduleBoardUpdatePacket::decode,
            ScheduleBoardUpdatePacket::handle);
        INSTANCE.registerMessage(id++, PlayerOrderSubmitPacket.class,
            PlayerOrderSubmitPacket::encode,
            PlayerOrderSubmitPacket::decode,
            PlayerOrderSubmitPacket::handle);
        INSTANCE.registerMessage(id++, MenuRenamePacket.class,
            MenuRenamePacket::encode,
            MenuRenamePacket::decode,
            MenuRenamePacket::handle);
        INSTANCE.registerMessage(id++, MenuBgListPacket.class,
            MenuBgListPacket::encode,
            MenuBgListPacket::decode,
            MenuBgListPacket::handle);
        INSTANCE.registerMessage(id++, MenuBgListRequestPacket.class,
            MenuBgListRequestPacket::encode,
            MenuBgListRequestPacket::decode,
            MenuBgListRequestPacket::handle);
        INSTANCE.registerMessage(id++, MenuBgRequestPacket.class,
            MenuBgRequestPacket::encode,
            MenuBgRequestPacket::decode,
            MenuBgRequestPacket::handle);
        INSTANCE.registerMessage(id++, MenuBgDataPacket.class,
            MenuBgDataPacket::encode,
            MenuBgDataPacket::decode,
            MenuBgDataPacket::handle);
        INSTANCE.registerMessage(id++, MenuBgUploadPacket.class,
            MenuBgUploadPacket::encode,
            MenuBgUploadPacket::decode,
            MenuBgUploadPacket::handle);
        INSTANCE.registerMessage(id++, MenuBgSetPacket.class,
            MenuBgSetPacket::encode,
            MenuBgSetPacket::decode,
            MenuBgSetPacket::handle);
    }
}
