package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, MaidRestaurantBusiness.MOD_ID);

    public static final RegistryObject<SoundEvent> CLOSING_BELL = SOUND_EVENTS.register("closing_bell",
            () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(MaidRestaurantBusiness.MOD_ID, "closing_bell")));

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
