package com.icewolf.maidrestaurant.business.block;

import com.icewolf.maidrestaurant.business.block.entity.OrderClipBlockEntity;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import com.icewolf.maidrestaurant.business.util.ItemStackUtils;
import com.mojang.serialization.MapCodec;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
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

/**
 * 挂单夹：贴在操作台旁墙上的横向金属夹轨，无 GUI、无碰撞薄板，只夹一张订单。
 *
 * <p>手持订单右键存入（创造模式也消耗一张，一单一客），空手右键取回；外观随 {@link #HAS_ORDER}
 * 在空夹 / 夹着订单两个模型间切换。碰撞与选取箱只覆盖贴墙的夹轨，垂下的订单纸可穿过、不挡路。</p>
 */
public class OrderClipBlock extends BaseEntityBlock {
    public static final MapCodec<OrderClipBlock> CODEC = MapCodec.unit(OrderClipBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty HAS_ORDER = BooleanProperty.create("has_order");

    // 夹轨贴墙：以北面墙为例，轨道位于 z=14~16、y=10~13.5，订单纸模型垂下但不参与碰撞
    private static final VoxelShape NORTH = Block.box(1.0, 10.0, 14.0, 15.0, 13.5, 16.0);
    private static final VoxelShape SOUTH = Block.box(1.0, 10.0, 0.0, 15.0, 13.5, 2.0);
    private static final VoxelShape WEST = Block.box(14.0, 10.0, 1.0, 16.0, 13.5, 15.0);
    private static final VoxelShape EAST = Block.box(0.0, 10.0, 1.0, 2.0, 13.5, 15.0);

    public OrderClipBlock() {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.6f).sound(SoundType.METAL).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(HAS_ORDER, false));
    }

    @Override
    public MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return switch (state.getValue(FACING)) {
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case EAST -> EAST;
            default -> NORTH;
        };
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        Direction facing;
        if (face.getAxis().isHorizontal()) {
            // 贴在被点的那个竖直侧面，朝向与点击面相反
            facing = face.getOpposite();
        } else {
            // 点在上 / 下面时，沿玩家水平朝向贴墙
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
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, HAS_ORDER);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new OrderClipBlockEntity(pos, state);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    /** 空手右键：取回夹着的订单，优先放回当前选中的主手槽。 */
    @Override
    public InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide) {
            return state.getValue(HAS_ORDER) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof OrderClipBlockEntity clip)) {
            return InteractionResult.PASS;
        }
        ItemStack taken = clip.takeOne();
        if (taken.isEmpty()) {
            return InteractionResult.PASS;
        }
        giveBack(player, taken);
        playClipSound(level, pos, false);
        return InteractionResult.SUCCESS;
    }

    /** 手持订单右键：存入一张（创造模式同样消耗）。 */
    @Override
    public ItemInteractionResult useItemOn(ItemStack held, BlockState state, Level level, BlockPos pos,
                                          Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (!isOrderItem(held)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return state.getValue(HAS_ORDER) ? ItemInteractionResult.SKIP_DEFAULT_BLOCK_INTERACTION : ItemInteractionResult.SUCCESS;
        }
        if (!(level.getBlockEntity(pos) instanceof OrderClipBlockEntity clip)) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (clip.storeOne(held.split(1))) {
            playClipSound(level, pos, true);
            return ItemInteractionResult.SUCCESS;
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    /** 取回的订单优先放回玩家当前选中的主手槽（与厨具架交互习惯一致），放不下再进背包。 */
    private static void giveBack(Player player, ItemStack stack) {
        var inventory = player.getInventory();
        int selected = inventory.selected;
        if (inventory.getItem(selected).isEmpty()) {
            inventory.setItem(selected, stack);
            return;
        }
        if (!player.getInventory().add(stack)) {
            player.drop(stack, false);
        }
    }

    private static void playClipSound(Level level, BlockPos pos, boolean add) {
        level.playSound(null, pos, add ? SoundEvents.ITEM_FRAME_ADD_ITEM : SoundEvents.ITEM_FRAME_REMOVE_ITEM,
                SoundSource.BLOCKS, 0.8f, 1.0f);
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moved) {
        if (!state.is(newState.getBlock())) {
            if (!level.isClientSide && level.getBlockEntity(pos) instanceof OrderClipBlockEntity clip) {
                ItemStack content = clip.content();
                if (!content.isEmpty()) {
                    Block.popResource(level, pos, content);
                }
                popResource(level, pos, new ItemStack(ModItems.ORDER_CLIP.get()));
            }
        }
        super.onRemove(state, level, pos, newState, moved);
    }

    /** 判断物品栈是否为下单了订单（带 FoodList 或 OrderId 自定义数据）。 */
    public static boolean isOrderItem(ItemStack stack) {
        if (stack == null || stack.isEmpty()) {
            return false;
        }
        CompoundTag tag = ItemStackUtils.getTag(stack);
        return tag != null && (tag.contains("FoodList") || tag.contains("OrderId"));
    }
}
