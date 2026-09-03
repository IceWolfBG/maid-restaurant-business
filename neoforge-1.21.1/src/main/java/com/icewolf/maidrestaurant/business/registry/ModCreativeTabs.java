package com.icewolf.maidrestaurant.business.registry;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS = 
        DeferredRegister.create(Registries.CREATIVE_MODE_TAB, "maid_restaurant_business");
    
    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAID_RESTAURANT_BUSINESS_TAB = 
        CREATIVE_MODE_TABS.register("maid_restaurant_business", () -> 
            CreativeModeTab.builder()
                .icon(() -> new ItemStack(ModItems.HEALTH_CERTIFICATE.get()))
                .title(Component.translatable("itemGroup.maid_restaurant_business"))
                .displayItems((parameters, output) -> {
                    output.accept(ModItems.HEALTH_CERTIFICATE.get());
                    output.accept(ModItems.PUBLIC_NOTICE_BOARD.get());
                    output.accept(ModItems.SCHEDULE_BOARD.get());
                    output.accept(ModItems.JIUHU_STATION.get());
                })
                .build()
        );

    public static void register(IEventBus eventBus) {
        CREATIVE_MODE_TABS.register(eventBus);
    }
}
