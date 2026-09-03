package com.icewolf.maidrestaurant.business.block;

import com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity;
import com.icewolf.maidrestaurant.business.menu.JiuhuStationMenu;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class JiuhuStationBlock extends BaseEntityBlock {
    public static final com.mojang.serialization.MapCodec<JiuhuStationBlock> CODEC = com.mojang.serialization.MapCodec.unit(JiuhuStationBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    // 碰撞箱：主体z=8-16(深8)，屋檐/托架z=6-7.9，总深度10
    private static final VoxelShape NORTH = Block.box(0.0, 0.0, 6.0, 16.0, 16.0, 16.0);
    private static final VoxelShape SOUTH = Block.box(0.0, 0.0, 0.0, 16.0, 16.0, 10.0);
    private static final VoxelShape WEST = Block.box(6.0, 0.0, 0.0, 16.0, 16.0, 16.0);
    private static final VoxelShape EAST = Block.box(0.0, 0.0, 0.0, 10.0, 16.0, 16.0);

    public JiuhuStationBlock() {
        super(BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOD).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch ((Direction)state.getValue(FACING)) {
            case NORTH -> NORTH;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction[] directions = context.getNearestLookingDirections();
        for (Direction direction : directions) {
            if (direction.getAxis().isHorizontal()) {
                Direction facing = direction.getOpposite();
                return this.defaultBlockState().setValue(FACING, facing);
            }
        }
        return this.defaultBlockState().setValue(FACING, Direction.NORTH);
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new JiuhuStationBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, blockEntity) -> JiuhuStationBlockEntity.tick(lvl, pos, st, blockEntity);
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            // 在客户端设置静态变量，服务端和客户端是不同进程
            JiuhuStationMenu.setPendingBlockPos(pos);
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof JiuhuStationBlockEntity)) {
            return InteractionResult.PASS;
        }
        JiuhuStationBlockEntity station = (JiuhuStationBlockEntity)be;
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(station.getMenuProvider());
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof JiuhuStationBlockEntity station) {
                    // 掉落所有外卖袋
                    for (int i = 0; i < station.getContainerSize(); i++) {
                        ItemStack stack = station.getItem(i);
                        if (!stack.isEmpty()) {
                            popResource(level, pos, stack);
                        }
                    }
                }
                popResource(level, pos, new ItemStack(ModItems.JIUHU_STATION.get()));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }
}
