/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ServerChunkCache
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.ChunkPos
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.chunk.ChunkAccess
 *  net.minecraft.world.level.chunk.LevelChunk
 *  net.minecraftforge.event.level.ChunkEvent$Load
 *  net.minecraftforge.event.level.ChunkEvent$Unload
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 */
package com.icewolf.maidrestaurant.business.core;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerChunkCache;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="maid_restaurant_business")
public class WorldScanner {
    private static final Map<ServerLevel, Set<BlockPos>> trackedPositions = new ConcurrentHashMap<ServerLevel, Set<BlockPos>>();

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof ServerLevel)) {
            return;
        }
        ServerLevel level = (ServerLevel)levelAccessor;
        ChunkAccess chunkAccess = event.getChunk();
        if (!(chunkAccess instanceof LevelChunk)) {
            return;
        }
        LevelChunk chunk = (LevelChunk)chunkAccess;
        Set set = trackedPositions.computeIfAbsent(level, k -> ConcurrentHashMap.newKeySet());
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            set.add(pos.immutable());
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        LevelAccessor levelAccessor = event.getLevel();
        if (!(levelAccessor instanceof ServerLevel)) {
            return;
        }
        ServerLevel level = (ServerLevel)levelAccessor;
        ChunkAccess chunkAccess = event.getChunk();
        if (!(chunkAccess instanceof LevelChunk)) {
            return;
        }
        LevelChunk chunk = (LevelChunk)chunkAccess;
        Set<BlockPos> set = trackedPositions.get(level);
        if (set == null) {
            return;
        }
        for (BlockPos pos : chunk.getBlockEntitiesPos()) {
            set.remove(pos);
        }
    }

    /**
     * 玩家（或其它实体）放置方块：若新方块带方块实体，立即纳入索引。
     * 修复原先只在区块加载时快照、运行时新放的操作台/打单机永远扫不到的问题。
     * 仅按"是否带方块实体"粗加入，具体类型由 scan(...) 时的 isInstance 过滤，残留位置无害。
     */
    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        if (!event.getPlacedBlock().hasBlockEntity()) {
            return;
        }
        ServerLevel level = (ServerLevel)event.getLevel();
        trackedPositions.computeIfAbsent(level, k -> ConcurrentHashMap.newKeySet())
                .add(event.getPos().immutable());
    }

    /**
     * 玩家破坏方块：从索引移除。非玩家破坏（爆炸等）虽不触发本事件，
     * 但 scan(...) 时 getBlockEntity 返回 null 会自动跳过，不会产生错误绑定。
     */
    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (!(event.getLevel() instanceof ServerLevel)) {
            return;
        }
        Set<BlockPos> set = trackedPositions.get((ServerLevel)event.getLevel());
        if (set != null) {
            set.remove(event.getPos().immutable());
        }
    }

    public static List<BlockPos> scan(ServerLevel level, Class<?> ... targetClasses) {
        ArrayList<BlockPos> result = new ArrayList<BlockPos>();
        Set<BlockPos> set = trackedPositions.get(level);
        if (set == null || set.isEmpty()) {
            try {
                ServerChunkCache chunkCache = level.getChunkSource();
                Field chunkMapField = chunkCache.getClass().getDeclaredField("chunkMap");
                chunkMapField.setAccessible(true);
                Object chunkMap = chunkMapField.get(chunkCache);
                if (chunkMap != null) {
                    Field visibleChunksField = chunkMap.getClass().getDeclaredField("visibleChunks");
                    visibleChunksField.setAccessible(true);
                    Object visibleChunks = visibleChunksField.get(chunkMap);
                    if (visibleChunks instanceof Iterable) {
                        for (Object chunkHolder : (Iterable)visibleChunks) {
                            try {
                                LevelChunk chunk;
                                ChunkPos chunkPos;
                                Field posField = chunkHolder.getClass().getDeclaredField("pos");
                                posField.setAccessible(true);
                                Object posObj = posField.get(chunkHolder);
                                if (!(posObj instanceof ChunkPos) || !level.isLoaded((chunkPos = (ChunkPos)posObj).getWorldPosition()) || (chunk = level.getChunk(chunkPos.x, chunkPos.z)) == null) continue;
                                block5: for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                                    BlockEntity be = level.getBlockEntity(pos);
                                    if (be == null) continue;
                                    for (Class<?> clazz : targetClasses) {
                                        if (!clazz.isInstance(be)) continue;
                                        result.add(pos.immutable());
                                        continue block5;
                                    }
                                }
                            }
                            catch (Throwable throwable) {
                            }
                        }
                    }
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
            return result;
        }
        block7: for (BlockPos pos : set) {
            BlockEntity be;
            if (!level.isLoaded(pos) || (be = level.getBlockEntity(pos)) == null) continue;
            for (Class<?> clazz : targetClasses) {
                if (!clazz.isInstance(be)) continue;
                result.add(pos);
                continue block7;
            }
        }
        return result;
    }
}
