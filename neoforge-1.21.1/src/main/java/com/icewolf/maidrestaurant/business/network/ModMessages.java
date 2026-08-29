package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

@EventBusSubscriber(modid = MaidRestaurantBusiness.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModMessages {
    public static final ResourceLocation CHANNEL_ID = ResourceLocation.fromNamespaceAndPath(MaidRestaurantBusiness.MOD_ID, "main");

    @SubscribeEvent
    public static void register(final RegisterPayloadHandlersEvent event) {
        final PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(
            ScheduleBoardUpdatePacket.TYPE,
            ScheduleBoardUpdatePacket.STREAM_CODEC,
            (payload, context) -> {
                context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        payload.handle(serverPlayer);
                    }
                });
            }
        );
        System.out.println("[ModMessages] 网络包注册成功");
    }
}
