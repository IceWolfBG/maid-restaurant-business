package com.icewolf.maidrestaurant.business.registry;

import com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity;
import com.icewolf.maidrestaurant.business.block.entity.PublicNoticeBoardBlockEntity;
import com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredHolder;

public class ModBlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES = DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, "maid_restaurant_business");
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<PublicNoticeBoardBlockEntity>> PUBLIC_NOTICE_BOARD = BLOCK_ENTITIES.register("public_notice_board", () -> BlockEntityType.Builder.of(PublicNoticeBoardBlockEntity::new, ModBlocks.PUBLIC_NOTICE_BOARD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<ScheduleBoardBlockEntity>> SCHEDULE_BOARD = BLOCK_ENTITIES.register("schedule_board", () -> BlockEntityType.Builder.of(ScheduleBoardBlockEntity::new, ModBlocks.SCHEDULE_BOARD.get()).build(null));
    public static final DeferredHolder<BlockEntityType<?>, BlockEntityType<JiuhuStationBlockEntity>> JIUHU_STATION = BLOCK_ENTITIES.register("jiuhu_station", () -> BlockEntityType.Builder.of(JiuhuStationBlockEntity::new, ModBlocks.JIUHU_STATION.get()).build(null));

    public static void register(IEventBus eventBus) {
        BLOCK_ENTITIES.register(eventBus);
    }
}
