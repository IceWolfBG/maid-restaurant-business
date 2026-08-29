/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.BlockItem
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.level.block.Block
 *  net.neoforged.bus.api.IEventBus
 *  net.neoforged.neoforge.registries.DeferredRegister
 *  net.neoforged.neoforge.registries.NeoNeoNeoForgeRegistries
 *  net.neoforged.neoforge.registries.INeoForgeRegistry
 *  net.neoforged.neoforge.registries.DeferredHolder
 */
package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.item.HealthCertificateItem;
import com.icewolf.maidrestaurant.business.registry.ModBlocks;
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
    public static final DeferredHolder<Item, Item> PUBLIC_NOTICE_BOARD = ITEMS.register("public_notice_board", () -> new BlockItem((Block)ModBlocks.PUBLIC_NOTICE_BOARD.get(), new Item.Properties()));
    public static final DeferredHolder<Item, Item> SCHEDULE_BOARD = ITEMS.register("schedule_board", () -> new BlockItem((Block)ModBlocks.SCHEDULE_BOARD.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
