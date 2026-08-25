/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.entity.BlockEntityType$Builder
 *  net.minecraftforge.eventbus.api.IEventBus
 *  net.minecraftforge.registries.DeferredRegister
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.registries.IForgeRegistry
 *  net.minecraftforge.registries.RegistryObject
 */
package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.block.entity.PublicNoticeBoardBlockEntity;
import com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity;
import com.icewolf.maidrestaurant.business.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create((IForgeRegistry)ForgeRegistries.BLOCK_ENTITY_TYPES, (String)"maid_restaurant_business");
    public static final RegistryObject<BlockEntityType<PublicNoticeBoardBlockEntity>> PUBLIC_NOTICE_BOARD = BLOCK_ENTITIES.register("public_notice_board", () -> BlockEntityType.Builder.of(PublicNoticeBoardBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.PUBLIC_NOTICE_BOARD.get()}).build(null));
    public static final RegistryObject<BlockEntityType<ScheduleBoardBlockEntity>> SCHEDULE_BOARD = BLOCK_ENTITIES.register("schedule_board", () -> BlockEntityType.Builder.of(ScheduleBoardBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.SCHEDULE_BOARD.get()}).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
