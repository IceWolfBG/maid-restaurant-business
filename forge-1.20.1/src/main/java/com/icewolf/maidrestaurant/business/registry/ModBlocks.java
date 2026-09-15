package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.block.JiuhuStationBlock;
import com.icewolf.maidrestaurant.business.block.PublicNoticeBoardBlock;
import com.icewolf.maidrestaurant.business.block.ScheduleBoardBlock;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryObject;

public class ModBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create((IForgeRegistry)ForgeRegistries.BLOCKS, (String)"maid_restaurant_business");
    public static final RegistryObject<Block> PUBLIC_NOTICE_BOARD = BLOCKS.register("public_notice_board", PublicNoticeBoardBlock::new);
    public static final RegistryObject<Block> SCHEDULE_BOARD = BLOCKS.register("schedule_board", ScheduleBoardBlock::new);
    public static final RegistryObject<Block> JIUHU_STATION = BLOCKS.register("jiuhu_station", JiuhuStationBlock::new);

    public static void register(IEventBus eventBus) {
        BLOCKS.register(eventBus);
    }
}
