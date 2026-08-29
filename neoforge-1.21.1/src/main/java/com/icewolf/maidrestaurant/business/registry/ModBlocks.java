/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.world.level.block.Block
 *  net.neoforged.bus.api.IEventBus
 *  net.neoforged.neoforge.registries.DeferredRegister
 *  net.neoforged.neoforge.registries.NeoNeoNeoForgeRegistries
 *  net.neoforged.neoforge.registries.INeoForgeRegistry
 *  net.neoforged.neoforge.registries.DeferredHolder
 */
package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.block.PublicNoticeBoardBlock;
import com.icewolf.maidrestaurant.business.block.ScheduleBoardBlock;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, "maid_restaurant_business");
    public static final DeferredHolder<Block, Block> PUBLIC_NOTICE_BOARD = BLOCKS.register("public_notice_board", PublicNoticeBoardBlock::new);
    public static final DeferredHolder<Block, Block> SCHEDULE_BOARD = BLOCKS.register("schedule_board", ScheduleBoardBlock::new);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
