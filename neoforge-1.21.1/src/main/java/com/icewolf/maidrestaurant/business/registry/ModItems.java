package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.item.HealthCertificateItem;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(Registries.ITEM, "maid_restaurant_business");
    public static final DeferredHolder<Item, Item> HEALTH_CERTIFICATE = ITEMS.register("health_certificate", HealthCertificateItem::new);
    public static final DeferredHolder<Item, Item> PUBLIC_NOTICE_BOARD = ITEMS.register("public_notice_board", () -> new BlockItem(ModBlocks.PUBLIC_NOTICE_BOARD.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SCHEDULE_BOARD = ITEMS.register("schedule_board", () -> new BlockItem(ModBlocks.SCHEDULE_BOARD.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> JIUHU_STATION = ITEMS.register("jiuhu_station", () -> new BlockItem(ModBlocks.JIUHU_STATION.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
