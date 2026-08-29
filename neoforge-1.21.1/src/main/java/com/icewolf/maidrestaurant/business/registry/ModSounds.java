package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(net.minecraft.core.registries.Registries.SOUND_EVENT, MaidRestaurantBusiness.MOD_ID);

    public static final DeferredHolder<SoundEvent, SoundEvent> CLOSING_BELL = SOUND_EVENTS.register("closing_bell",
            () -> SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath(MaidRestaurantBusiness.MOD_ID, "closing_bell")));

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
