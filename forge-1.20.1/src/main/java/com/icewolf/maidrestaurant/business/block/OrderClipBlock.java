package com.icewolf.maidrestaurant.business.block;

import com.icewolf.maidrestaurant.business.block.entity.OrderClipBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
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
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;

/**
 * 挂单夹：贴墙的薄型铝合金夹轨，夹一张下单了(OTC)订单。
 * 无 GUI；仅贴墙夹轨有碰撞 / 可点选，垂下的订单纸可穿过且超出判定箱照常渲染；空手取、持订单存。
 * 订单具体内容不同步客户端，用 has_order 切换外观。
 */
public class OrderClipBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HAS_ORDER = BooleanProperty.create("has_order");

    // 碰撞 / 选取箱只覆盖贴墙的那根横向夹轨（模型 y≈10.5~13）；
    // 下方垂下的订单纸不属于判定箱，可穿过、可被遮挡，但仍随模型正常渲染（渲染与 shape 相互独立）。
    private static final VoxelShape NORTH = Block.box(1.0D, 10.0D, 14.0D, 15.0D, 13.5D, 16.0D);
    private static final VoxelShape SOUTH = Block.box(1.0D, 10.0D, 0.0D, 15.0D, 13.5D, 2.0D);
    private static final VoxelShape EAST = Block.box(0.0D, 10.0D, 1.0D, 2.0D, 13.5D, 15.0D);
    private static final VoxelShape WEST = Block.box(14.0D, 10.0D, 1.0D, 16.0D, 13.5D, 15.0D);

    public OrderClipBlock() {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.WOOD)
                .strength(1.0F)
                .sound(SoundType.WOOD)
                .noOcclusion());
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(HAS_ORDER, Boolean.FALSE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_ORDER);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        switch (state.getValue(FACING)) {
            case SOUTH: return SOUTH;
            case EAST: return EAST;
            case WEST: return WEST;
            case NORTH:
            default: return NORTH;
        }
    }

    // 不重写 getCollisionShape：碰撞箱默认等于 getShape，即只有贴墙夹轨有碰撞；
    // 垂下的订单纸区域无碰撞、可穿过，与森罗厨具架一致。

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing;
        if (face.getAxis().isHorizontal()) {
            facing = face;
        } else {
            facing = context.getHorizontalDirection().getOpposite();
        }
        return this.defaultBlockState().setValue(FACING, facing);
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrderClipBlockEntity(pos, state);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (!(be instanceof OrderClipBlockEntity clip)) {
            return InteractionResult.PASS;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean isOrder = isOrderItem(held);
        boolean canStore = isOrder && clip.isEmpty();
        boolean canTake = held.isEmpty() && !clip.isEmpty();
        if (!canStore && !canTake) {
            return InteractionResult.PASS;
        }

        // 客户端只负责摆手，实际存取一律在服务端权威执行
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }

        if (canStore) {
            ItemStack stored = held.split(1);
            if (held.isEmpty()) {
                player.setItemInHand(hand, ItemStack.EMPTY);
            }
            clip.storeOne(stored);
            // 一张订单对应一位顾客，无论创造/生存都从手持消耗一张
            playClipSound(level, pos, true);
            player.inventoryMenu.broadcastChanges();
            return InteractionResult.CONSUME;
        } else {
            ItemStack got = clip.takeOne();
            if (!got.isEmpty()) {
                giveBack(player, hand, got);
                playClipSound(level, pos, false);
                player.inventoryMenu.broadcastChanges();
            }
            return InteractionResult.CONSUME;
        }
    }

    /**
     * 取下的订单优先回到当前主手槽（空手右键时即玩家选中的快捷栏位）；
     * 主手意外被占用则放进背包，放不下掉在地上。
     */
    private static void giveBack(Player player, InteractionHand hand, ItemStack stack) {
        if (hand == InteractionHand.MAIN_HAND && player.getMainHandItem().isEmpty()) {
            player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        } else if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof OrderClipBlockEntity clip) {
                ItemStack order = clip.content();
                if (!order.isEmpty()) {
                    Block.popResource(level, pos, order);
                }
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    public static boolean isOrderItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = stack.getTag();
        return tag != null && (tag.contains("FoodList") || tag.contains("OrderId"));
    }

    private static void playClipSound(Level level, BlockPos pos, boolean add) {
        if (level.isClientSide) {
            return;
        }
        level.playSound(null, pos,
                add ? SoundEvents.ITEM_FRAME_ADD_ITEM : SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.BLOCKS, 0.7F, add ? 1.0F : 0.9F);
    }
}
