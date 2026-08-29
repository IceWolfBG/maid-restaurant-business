/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.entity.BlockEntityType$Builder
 *  net.neoforged.bus.api.IEventBus
 *  net.neoforged.neoforge.registries.DeferredRegister
 *  net.neoforged.neoforge.registries.NeoNeoNeoForgeRegistries
 *  net.neoforged.neoforge.registries.INeoForgeRegistry
 *  net.neoforged.neoforge.registries.DeferredHolder
 */
package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.block.entity.PublicNoticeBoardBlockEntity;
import com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity;
import com.icewolf.maidrestaurant.business.registry.ModBlocks;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "maid_restaurant_business");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PublicNoticeBoardBlockEntity>> PUBLIC_NOTICE_BOARD = BLOCK_ENTITIES.register("public_notice_board", () -> BlockEntityType.Builder.of(PublicNoticeBoardBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.PUBLIC_NOTICE_BOARD.get()}).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScheduleBoardBlockEntity>> SCHEDULE_BOARD = BLOCK_ENTITIES.register("schedule_board", () -> BlockEntityType.Builder.of(ScheduleBoardBlockEntity::new, (Block[])new Block[]{(Block)ModBlocks.SCHEDULE_BOARD.get()}).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
