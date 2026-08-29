package com.icewolf.maidrestaurant.business;

import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import com.icewolf.maidrestaurant.business.core.ActivationCache;
import com.icewolf.maidrestaurant.business.core.BusinessManager;
import com.icewolf.maidrestaurant.business.network.ModMessages;
import com.icewolf.maidrestaurant.business.registry.ModBlockEntities;
import com.icewolf.maidrestaurant.business.registry.ModBlocks;
import com.icewolf.maidrestaurant.business.registry.ModCreativeTabs;
import com.icewolf.maidrestaurant.business.registry.ModDataComponents;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import com.icewolf.maidrestaurant.business.registry.ModMenuTypes;
import com.icewolf.maidrestaurant.business.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.event.server.ServerStartingEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(value="maid_restaurant_business")
public class MaidRestaurantBusiness {
    public static final String MOD_ID = "maid_restaurant_business";
    public static final Logger LOGGER = LogManager.getLogger((String)"MaidRestaurantBusiness");
    private static BusinessManager manager;

    public MaidRestaurantBusiness(IEventBus modEventBus, ModContainer modContainer) {
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModSounds.register(modEventBus);
        ModDataComponents.register(modEventBus);
        NeoForge.EVENT_BUS.register(this);

        // 注册配置文件 - NeoForge 1.21.1正确方式：通过ModContainer.registerConfig
        modContainer.registerConfig(ModConfig.Type.COMMON, BusinessConfig.SPEC);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        manager = new BusinessManager(event.getServer());
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ActivationCache.initLevel(serverLevel);
        }
    }

    @SubscribeEvent
    public void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ActivationCache.clearLevel(serverLevel);
        }
    }

    @SubscribeEvent
    public void onServerStopping(ServerStoppingEvent event) {
        manager = null;
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        if (manager != null) {
            manager.tick(event.getServer());
        }
    }

    public static BusinessManager getManager() {
        return manager;
    }
}
