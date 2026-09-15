/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nullable
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Direction
 *  net.minecraft.network.chat.Component
 *  net.minecraft.util.StringRepresentable
 *  net.minecraft.world.InteractionHand
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.context.BlockPlaceContext
 *  net.minecraft.world.level.BlockGetter
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.LevelAccessor
 *  net.minecraft.world.level.block.BaseEntityBlock
 *  net.minecraft.world.level.block.Block
 *  net.minecraft.world.level.block.Blocks
 *  net.minecraft.world.level.block.HorizontalDirectionalBlock
 *  net.minecraft.world.level.block.Mirror
 *  net.minecraft.world.level.block.RenderShape
 *  net.minecraft.world.level.block.Rotation
 *  net.minecraft.world.level.block.SoundType
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockBehaviour$Properties
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.StateDefinition$Builder
 *  net.minecraft.world.level.block.state.properties.DirectionProperty
 *  net.minecraft.world.level.block.state.properties.EnumProperty
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.phys.BlockHitResult
 *  net.minecraft.world.phys.shapes.CollisionContext
 *  net.minecraft.world.phys.shapes.VoxelShape
 */
package com.icewolf.maidrestaurant.business.block;

import com.icewolf.maidrestaurant.business.block.entity.PublicNoticeBoardBlockEntity;
import com.icewolf.maidrestaurant.business.item.HealthCertificateItem;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

public class PublicNoticeBoardBlock
extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final EnumProperty<Half> HALF = EnumProperty.create((String)"half", Half.class);
    private static final VoxelShape LEFT_NORTH = Block.box((double)0.0, (double)0.0, (double)14.0, (double)16.0, (double)16.0, (double)16.0);
    private static final VoxelShape LEFT_SOUTH = Block.box((double)0.0, (double)0.0, (double)0.0, (double)16.0, (double)16.0, (double)2.0);
    private static final VoxelShape LEFT_WEST = Block.box((double)14.0, (double)0.0, (double)0.0, (double)16.0, (double)16.0, (double)16.0);
    private static final VoxelShape LEFT_EAST = Block.box((double)0.0, (double)0.0, (double)0.0, (double)2.0, (double)16.0, (double)16.0);

    public PublicNoticeBoardBlock() {
        super(BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.METAL).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HALF, Half.LEFT));
    }

    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return switch ((Direction)state.getValue(FACING)) {
            case NORTH -> LEFT_NORTH;
            case SOUTH -> LEFT_SOUTH;
            case WEST -> LEFT_WEST;
            case EAST -> LEFT_EAST;
            default -> LEFT_NORTH;
        };
    }

    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockPos rightPos;
        BlockPos clickedPos = context.getClickedPos();
        Direction facing = context.getHorizontalDirection().getOpposite();
        Level level = context.getLevel();
        // RIGHT半放在玩家右手边（facing的逆时针方向，因为facing是方块面向玩家的方向）
        if (level.getBlockState(rightPos = clickedPos.relative(facing.getCounterClockWise())).canBeReplaced(context) && level.getWorldBorder().isWithinBounds(rightPos)) {
            return this.defaultBlockState().setValue(FACING, facing).setValue(HALF, Half.LEFT);
        }
        return null;
    }

    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            BlockPos rightPos = pos.relative(((Direction)state.getValue(FACING)).getCounterClockWise());
            level.setBlock(rightPos, state.setValue(HALF, Half.RIGHT), 3);
            level.blockUpdated(pos, Blocks.AIR);
            state.updateNeighbourShapes((LevelAccessor)level, pos, 3);
        }
    }

    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (state.getBlock() != newState.getBlock()) {
            if (state.getValue(HALF) == Half.LEFT) {
                BlockPos rightPos;
                BlockState rightState;
                BlockEntity be = level.getBlockEntity(pos);
                if (be instanceof PublicNoticeBoardBlockEntity) {
                    PublicNoticeBoardBlockEntity board = (PublicNoticeBoardBlockEntity)be;
                    for (ItemStack stack : board.getCertificates()) {
                        Block.popResource((Level)level, (BlockPos)pos, (ItemStack)stack);
                    }
                }
                if ((rightState = level.getBlockState(rightPos = pos.relative(((Direction)state.getValue(FACING)).getCounterClockWise()))).is((Block)this)) {
                    level.setBlock(rightPos, Blocks.AIR.defaultBlockState(), 3);
                }
            } else {
                BlockPos leftPos = pos.relative(((Direction)state.getValue(FACING)).getClockWise());
                BlockState leftState = level.getBlockState(leftPos);
                if (leftState.is((Block)this)) {
                    level.setBlock(leftPos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
            super.onRemove(state, level, pos, newState, moved);
        }
    }

    public BlockState rotate(BlockState state, Rotation rotation) {
        return (BlockState)state.setValue(FACING, rotation.rotate((Direction)state.getValue(FACING)));
    }

    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation((Direction)state.getValue(FACING)));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING, HALF});
    }

    @Nullable
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        if (state.getValue(HALF) == Half.LEFT) {
            return new PublicNoticeBoardBlockEntity(pos, state);
        }
        return null;
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(HALF) == Half.RIGHT) {
            BlockPos leftPos = pos.relative(((Direction)state.getValue(FACING)).getClockWise());
            BlockState leftState = level.getBlockState(leftPos);
            if (leftState.is((Block)this)) {
                return leftState.use(level, player, hand, hit.withPosition(leftPos));
            }
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof PublicNoticeBoardBlockEntity)) {
            return InteractionResult.PASS;
        }
        PublicNoticeBoardBlockEntity board = (PublicNoticeBoardBlockEntity)be;
        // 尝试自动绑定最近的打单机
        if (!board.hasBoundMachine()) {
            if (board.tryBindNearestMachine(level)) {
                player.sendSystemMessage((Component)Component.literal((String)"\u00a7a\u516c\u793a\u680f\u5df2\u81ea\u52a8\u7ed1\u5b9a\u6700\u8fd1\u7684\u6253\u5355\u673a"));
            } else {
                player.sendSystemMessage((Component)Component.literal((String)"\u00a7e\u672a\u627e\u5230\u6253\u5355\u673a\uff0c\u8bf7\u786e\u4fdd\u6253\u5355\u673a\u572816\u683c\u8303\u56f4\u5185"));
            }
        }
        ItemStack heldItem = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            ItemStack extracted = board.extractCertificate();
            if (!extracted.isEmpty()) {
                if (!player.getInventory().add(extracted)) {
                    player.drop(extracted, false);
                }
                player.sendSystemMessage((Component)Component.literal((String)"\u00a7a\u5df2\u53d6\u51fa\u5065\u5eb7\u8bc1"));
            }
            return InteractionResult.SUCCESS;
        }
        if (!heldItem.isEmpty() && heldItem.getItem() instanceof HealthCertificateItem) {
            if (board.insertCertificate(heldItem)) {
                heldItem.shrink(1);
                player.sendSystemMessage((Component)Component.literal((String)"\u00a7a\u5065\u5eb7\u8bc1\u5df2\u653e\u5165\u516c\u793a\u680f"));
            } else {
                player.sendSystemMessage((Component)Component.literal((String)"\u00a7c\u516c\u793a\u680f\u5df2\u6ee1"));
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    public static enum Half implements StringRepresentable
    {
        LEFT("left"),
        RIGHT("right");

        private final String name;

        private Half(String name) {
            this.name = name;
        }

        public String getSerializedName() {
            return this.name;
        }
    }
}
