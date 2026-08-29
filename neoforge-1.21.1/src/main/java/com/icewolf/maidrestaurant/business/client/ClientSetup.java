package com.icewolf.maidrestaurant.business.client;

import com.icewolf.maidrestaurant.business.client.screen.ScheduleBoardScreen;
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
            System.out.println("[ClientSetup] 开始注册ScheduleBoardScreen");
            Method registerMethod = MenuScreens.class.getDeclaredMethod("register", MenuType.class, MenuScreens.ScreenConstructor.class);
            registerMethod.setAccessible(true);
            MenuScreens.ScreenConstructor constructor = (menu, inv, title) -> {
                System.out.println("[ClientSetup] ScreenConstructor被调用，menu=" + menu);
                return new ScheduleBoardScreen((ScheduleBoardMenu) menu, inv, title);
            };
            registerMethod.invoke(null, ModMenuTypes.SCHEDULE_BOARD.get(), constructor);
            System.out.println("[ClientSetup] ScheduleBoardScreen注册成功");
        } catch (Exception e) {
            System.out.println("[ClientSetup] ScheduleBoardScreen注册失败: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
