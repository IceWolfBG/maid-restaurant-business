package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.mojang.serialization.Codec;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

import java.util.function.Supplier;

public class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS = DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, MaidRestaurantBusiness.MOD_ID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<CompoundTag>> CUSTOM_DATA = DATA_COMPONENTS.register("custom_data",
            () -> DataComponentType.<CompoundTag>builder().persistent(CompoundTag.CODEC).build());

    public static void register(net.neoforged.bus.api.IEventBus eventBus) {
        DATA_COMPONENTS.register(eventBus);
    }
}
