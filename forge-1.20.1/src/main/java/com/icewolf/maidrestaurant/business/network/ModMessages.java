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
    }
}
