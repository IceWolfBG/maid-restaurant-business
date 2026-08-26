/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.common.MinecraftForge
 *  net.minecraftforge.event.TickEvent$Phase
 *  net.minecraftforge.event.TickEvent$ServerTickEvent
 *  net.minecraftforge.event.server.ServerStartingEvent
 *  net.minecraftforge.event.server.ServerStoppingEvent
 *  net.minecraftforge.eventbus.api.IEventBus
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.ModLoadingContext
 *  net.minecraftforge.fml.common.Mod
 *  net.minecraftforge.fml.config.IConfigSpec
 *  net.minecraftforge.fml.config.ModConfig$Type
 *  net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext
 *  org.apache.logging.log4j.LogManager
 *  org.apache.logging.log4j.Logger
 */
package com.icewolf.maidrestaurant.business;

import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import com.icewolf.maidrestaurant.business.core.ActivationCache;
import com.icewolf.maidrestaurant.business.core.BusinessManager;
import com.icewolf.maidrestaurant.business.network.ModMessages;
import com.icewolf.maidrestaurant.business.registry.ModBlockEntities;
import com.icewolf.maidrestaurant.business.registry.ModBlocks;
import com.icewolf.maidrestaurant.business.registry.ModCreativeTabs;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import com.icewolf.maidrestaurant.business.registry.ModMenuTypes;
import com.icewolf.maidrestaurant.business.registry.ModSounds;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.IConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(value="maid_restaurant_business")
public class MaidRestaurantBusiness {
    public static final String MOD_ID = "maid_restaurant_business";
    public static final Logger LOGGER = LogManager.getLogger((String)"MaidRestaurantBusiness");
    private static BusinessManager manager;

    public MaidRestaurantBusiness() {
        IEventBus modEventBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModItems.register(modEventBus);
        ModBlocks.register(modEventBus);
        ModBlockEntities.register(modEventBus);
        ModMenuTypes.register(modEventBus);
        ModCreativeTabs.register(modEventBus);
        ModSounds.register(modEventBus);
        ModMessages.register();
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, (IConfigSpec)BusinessConfig.SPEC, "maid_restaurant_business-common.toml");
        MinecraftForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onServerStarting(ServerStartingEvent event) {
        manager = new BusinessManager(event.getServer());
        LOGGER.info("女仆餐厅：经营 已启动");
    }

    @SubscribeEvent
    public void onLevelLoad(LevelEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel serverLevel) {
            ActivationCache.initLevel(serverLevel);
            LOGGER.info("已初始化维度 {} 的激活缓存，共 {} 个已激活打单机",
                    serverLevel.dimension().location(), ActivationCache.getActivatedCount(serverLevel));
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && manager != null) {
            manager.tick(event.getServer());
        }
    }

    public static BusinessManager getManager() {
        return manager;
    }
}
