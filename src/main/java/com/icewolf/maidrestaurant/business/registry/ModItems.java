/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.item.BlockItem
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.Item$Properties
 *  net.minecraft.world.level.block.Block
 *  net.minecraftforge.eventbus.api.IEventBus
 *  net.minecraftforge.registries.DeferredRegister
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.registries.IForgeRegistry
 *  net.minecraftforge.registries.RegistryObject
 */
package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.item.HealthCertificateItem;
import com.icewolf.maidrestaurant.business.registry.ModBlocks;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create((IForgeRegistry)ForgeRegistries.ITEMS, (String)"maid_restaurant_business");
    public static final RegistryObject<Item> HEALTH_CERTIFICATE = ITEMS.register("health_certificate", HealthCertificateItem::new);
    public static final RegistryObject<Item> PUBLIC_NOTICE_BOARD = ITEMS.register("public_notice_board", () -> new BlockItem((Block)ModBlocks.PUBLIC_NOTICE_BOARD.get(), new Item.Properties()));
    public static final RegistryObject<Item> SCHEDULE_BOARD = ITEMS.register("schedule_board", () -> new BlockItem((Block)ModBlocks.SCHEDULE_BOARD.get(), new Item.Properties()));

    public static void register(IEventBus eventBus) {
        ITEMS.register(eventBus);
    }
}
