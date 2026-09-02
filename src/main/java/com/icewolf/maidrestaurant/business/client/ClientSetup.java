package com.icewolf.maidrestaurant.business.client;

import com.icewolf.maidrestaurant.business.client.screen.JiuhuStationScreen;
import com.icewolf.maidrestaurant.business.client.screen.ScheduleBoardScreen;
import com.icewolf.maidrestaurant.business.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = "maid_restaurant_business", bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    public static void onClientSetup(FMLClientSetupEvent event) {
        MenuScreens.register(ModMenuTypes.SCHEDULE_BOARD.get(), ScheduleBoardScreen::new);
        MenuScreens.register(ModMenuTypes.JIUHU_STATION.get(), JiuhuStationScreen::new);
    }
}
