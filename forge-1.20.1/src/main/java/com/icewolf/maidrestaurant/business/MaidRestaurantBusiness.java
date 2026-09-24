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

import com.icewolf.maidrestaurant.business.config.BatchCookingConfig;
import com.icewolf.maidrestaurant.business.config.AutomationConfig;
import com.icewolf.maidrestaurant.business.config.GameplayConfig;
import com.icewolf.maidrestaurant.business.config.PerformanceConfig;
import com.icewolf.maidrestaurant.business.config.TaskSafetyConfig;
import com.icewolf.maidrestaurant.business.config.TakeoutConfig;
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
        // 营业中的所有配置统一收纳到 config/maid_restaurant_business/ 子文件夹，避免 config 根目录杂乱
        // 自动化功能开关
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, (IConfigSpec)AutomationConfig.SPEC, "maid_restaurant_business/automation.toml");
        // 玩法与成长（等级解锁/订单调度/收益加成/洗碗阈值/气泡冷却）
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, (IConfigSpec)GameplayConfig.SPEC, "maid_restaurant_business/gameplay.toml");
        // 扫描范围与性能调试
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, (IConfigSpec)PerformanceConfig.SPEC, "maid_restaurant_business/performance.toml");
        // 任务安全与超时保护配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, (IConfigSpec)TaskSafetyConfig.SPEC, "maid_restaurant_business/safety.toml");
        // 酒狐速递站（外卖配送）配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, (IConfigSpec)TakeoutConfig.SPEC, "maid_restaurant_business/takeout.toml");
        // 多槽厨具（蒸笼/烤箱/烤面包机/搅拌机）单任务批量烹饪配置
        ModLoadingContext.get().registerConfig(ModConfig.Type.COMMON, (IConfigSpec)BatchCookingConfig.SPEC, "maid_restaurant_business/batch_cooking.toml");
        MinecraftForge.EVENT_BUS.register(this);
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && manager != null) {
            manager.tick(event.getServer());
        }
    }

    public static BusinessManager getManager() {
        return manager;
    }
}
