package com.icewolf.maidrestaurant.business.client;

import com.icewolf.maidrestaurant.business.client.screen.JiuhuStationScreen;
import com.icewolf.maidrestaurant.business.client.screen.ScheduleBoardScreen;
import com.icewolf.maidrestaurant.business.menu.JiuhuStationMenu;
import com.icewolf.maidrestaurant.business.menu.ScheduleBoardMenu;
import com.icewolf.maidrestaurant.business.registry.ModMenuTypes;
import net.minecraft.client.gui.screens.MenuScreens;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;

import java.lang.reflect.Method;

@EventBusSubscriber(modid = "maid_restaurant_business", bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientSetup {
    @SubscribeEvent
    @SuppressWarnings({"unchecked", "rawtypes"})
    public static void onClientSetup(FMLClientSetupEvent event) {
        try {
            Method registerMethod = MenuScreens.class.getDeclaredMethod("register", MenuType.class, MenuScreens.ScreenConstructor.class);
            registerMethod.setAccessible(true);

            // 注册排班表Screen
            MenuScreens.ScreenConstructor scheduleConstructor = (menu, inv, title) ->
                new ScheduleBoardScreen((ScheduleBoardMenu) menu, inv, title);
            registerMethod.invoke(null, ModMenuTypes.SCHEDULE_BOARD.get(), scheduleConstructor);

            // 注册酒狐速递站Screen
            MenuScreens.ScreenConstructor jiuhuConstructor = (menu, inv, title) ->
                new JiuhuStationScreen((JiuhuStationMenu) menu, inv, title);
            registerMethod.invoke(null, ModMenuTypes.JIUHU_STATION.get(), jiuhuConstructor);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
