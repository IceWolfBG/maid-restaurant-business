package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.menu.JiuhuStationMenu;
import com.icewolf.maidrestaurant.business.menu.ScheduleBoardMenu;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.flag.FeatureFlags;
import net.minecraft.world.inventory.MenuType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENU_TYPES = DeferredRegister.create(Registries.MENU, MaidRestaurantBusiness.MOD_ID);
    public static final DeferredHolder<MenuType<?>, MenuType<ScheduleBoardMenu>> SCHEDULE_BOARD = MENU_TYPES.register("schedule_board", () -> new MenuType<>(ScheduleBoardMenu::create, FeatureFlags.VANILLA_SET));
    public static final DeferredHolder<MenuType<?>, MenuType<JiuhuStationMenu>> JIUHU_STATION = MENU_TYPES.register("jiuhu_station", () -> new MenuType<>(JiuhuStationMenu::create, FeatureFlags.VANILLA_SET));

    public static void register(IEventBus eventBus) {
        MENU_TYPES.register(eventBus);
    }
}
