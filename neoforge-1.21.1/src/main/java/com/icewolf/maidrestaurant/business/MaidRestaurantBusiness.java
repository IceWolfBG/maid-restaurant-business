package com.icewolf.maidrestaurant.business;

import com.icewolf.maidrestaurant.business.block.entity.OrderClipBlockEntity;
import com.icewolf.maidrestaurant.business.config.BatchCookingConfig;
import com.icewolf.maidrestaurant.business.config.AutomationConfig;
import com.icewolf.maidrestaurant.business.config.GameplayConfig;
import com.icewolf.maidrestaurant.business.config.PerformanceConfig;
import com.icewolf.maidrestaurant.business.config.TaskSafetyConfig;
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
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent;
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
        modContainer.registerConfig(ModConfig.Type.COMMON, AutomationConfig.SPEC, "maid_restaurant_business/automation.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, GameplayConfig.SPEC, "maid_restaurant_business/gameplay.toml");
        modContainer.registerConfig(ModConfig.Type.COMMON, PerformanceConfig.SPEC, "maid_restaurant_business/performance.toml");
        // 注册任务安全与超时保护配置（单独的配置文件）
        modContainer.registerConfig(ModConfig.Type.COMMON, TaskSafetyConfig.SPEC, "maid_restaurant_business/safety.toml");
        TaskSafetyConfig.register(modEventBus);
        // 注册酒狐速递站外卖配送配置（单独的配置文件）
        modContainer.registerConfig(ModConfig.Type.COMMON, com.icewolf.maidrestaurant.business.config.TakeoutConfig.SPEC, "maid_restaurant_business/takeout.toml");
        // 多槽厨具（蒸笼/烤箱/烤面包机/搅拌机）单任务批量烹饪配置
        modContainer.registerConfig(ModConfig.Type.COMMON, BatchCookingConfig.SPEC, "maid_restaurant_business/batch_cooking.toml");
        modEventBus.addListener(this::registerCapabilities);
    }

    private void registerCapabilities(RegisterCapabilitiesEvent event) {
        // 挂单夹只暴露只读单槽视图，供其它系统查询夹着的订单，不能从侧面塞入或抽出
        event.registerBlock(Capabilities.ItemHandler.BLOCK,
                (level, pos, state, blockEntity, side) -> blockEntity instanceof OrderClipBlockEntity clip ? clip.getViewHandler() : null,
                ModBlocks.ORDER_CLIP.get());
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
