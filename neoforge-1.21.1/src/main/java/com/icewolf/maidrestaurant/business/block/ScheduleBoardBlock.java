package com.icewolf.maidrestaurant.business.block;

import com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity;
import com.icewolf.maidrestaurant.business.menu.ScheduleBoardMenu;
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

public class ScheduleBoardBlock extends BaseEntityBlock {
    public static final com.mojang.serialization.MapCodec<ScheduleBoardBlock> CODEC = com.mojang.serialization.MapCodec.unit(ScheduleBoardBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    
    // 壁挂排班表薄板：宽14（x1~15）、高7（y4~11）、厚1.5，贴在方块朝外一侧
    private static final VoxelShape NORTH = Block.box(1.0, 4.0, 14.5, 15.0, 11.0, 16.0);
    private static final VoxelShape SOUTH = Block.box(1.0, 4.0, 0.0, 15.0, 11.0, 1.5);
    private static final VoxelShape WEST = Block.box(14.5, 4.0, 1.0, 16.0, 11.0, 15.0);
    private static final VoxelShape EAST = Block.box(0.0, 4.0, 1.0, 1.5, 11.0, 15.0);

    public ScheduleBoardBlock() {
        super(BlockBehaviour.Properties.of().strength(0.5f).sound(SoundType.WOOD).noOcclusion());
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
        return new ScheduleBoardBlockEntity(pos, state);
    }
    
    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (lvl, pos, st, blockEntity) -> ScheduleBoardBlockEntity.tick(lvl, pos, st, blockEntity);
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            // 【关键】在客户端设置静态变量，因为服务端和客户端是不同进程
            // 服务端设置的静态变量客户端读不到，必须在客户端右键点击时设置
            ScheduleBoardMenu.setPendingBlockPos(pos);
            return InteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof ScheduleBoardBlockEntity)) {
            return InteractionResult.PASS;
        }
        ScheduleBoardBlockEntity board = (ScheduleBoardBlockEntity)be;
        // 尝试自动绑定最近的打单机
        if (!board.hasBoundMachine()) {
            if (board.tryBindNearestMachine(level)) {
                player.sendSystemMessage(Component.literal("§a排班表已自动绑定最近的打单机"));
            } else {
                // 检查是否因为打单机已被其他排班表绑定
                boolean hasMachineButBound = false;
                for (BlockPos checkPos : BlockPos.betweenClosed(pos.offset(-16, -8, -16), pos.offset(16, 8, 16))) {
                    BlockEntity checkBe = level.getBlockEntity(checkPos);
                    if (checkBe != null && checkBe.getClass().getName().contains("OrderMachineBlockEntity")) {
                        hasMachineButBound = true;
                        break;
                    }
                }
                if (hasMachineButBound) {
                    player.sendSystemMessage(Component.literal("§c附近的打单机已被其他排班表绑定，一个打单机只能绑定一个排班表"));
                } else {
                    player.sendSystemMessage(Component.literal("§e未找到打单机，请确保打单机在16格范围内"));
                }
            }
        }
        // 打开GUI（客户端已经在上面设置了静态变量）
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.openMenu(board.getMenuProvider());
        }
        return InteractionResult.SUCCESS;
    }
    
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide) {
                popResource(level, pos, new ItemStack(ModItems.SCHEDULE_BOARD.get()));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    @Override
    public com.mojang.serialization.MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }
}
