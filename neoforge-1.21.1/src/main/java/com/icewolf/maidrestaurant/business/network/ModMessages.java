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
        registrar.playToServer(
            PlayerOrderSubmitPacket.TYPE,
            PlayerOrderSubmitPacket.STREAM_CODEC,
            (payload, context) -> {
                context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        payload.handle(serverPlayer);
                    }
                });
            }
        );
        registrar.playToServer(
            MenuRenamePacket.TYPE,
            MenuRenamePacket.STREAM_CODEC,
            (payload, context) -> {
                context.enqueueWork(() -> {
                    if (context.player() instanceof net.minecraft.server.level.ServerPlayer serverPlayer) {
                        payload.handle(serverPlayer);
                    }
                });
            }
        );
        registrar.playToClient(
            MenuBgListPacket.TYPE,
            MenuBgListPacket.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(payload::handleClient)
        );
        registrar.playToServer(
            MenuBgListRequestPacket.TYPE,
            MenuBgListRequestPacket.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) payload.handle(sp);
            })
        );
        registrar.playToServer(
            MenuBgRequestPacket.TYPE,
            MenuBgRequestPacket.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) payload.handle(sp);
            })
        );
        registrar.playToClient(
            MenuBgDataPacket.TYPE,
            MenuBgDataPacket.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(payload::handleClient)
        );
        registrar.playToServer(
            MenuBgUploadPacket.TYPE,
            MenuBgUploadPacket.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) payload.handle(sp);
            })
        );
        registrar.playToServer(
            MenuBgSetPacket.TYPE,
            MenuBgSetPacket.STREAM_CODEC,
            (payload, context) -> context.enqueueWork(() -> {
                if (context.player() instanceof net.minecraft.server.level.ServerPlayer sp) payload.handle(sp);
            })
        );
    }
}
