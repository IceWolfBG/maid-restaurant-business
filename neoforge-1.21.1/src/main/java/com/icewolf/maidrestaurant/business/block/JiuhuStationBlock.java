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
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

public class JiuhuStationBlock extends BaseEntityBlock {
    public static final com.mojang.serialization.MapCodec<JiuhuStationBlock> CODEC = com.mojang.serialization.MapCodec.unit(JiuhuStationBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;

    public JiuhuStationBlock() {
        super(BlockBehaviour.Properties.of().strength(1.0f).sound(SoundType.WOOD).noOcclusion());
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    // 选中/碰撞箱直接用一整个方块（屋顶外沿也在整格内，避免判定与模型不符）
    public VoxelShape getShape(BlockState state, BlockGetter world, BlockPos pos, CollisionContext context) {
        return Shapes.block();
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

    // OTC 升级装置（otc_upgrade_box）：手持右键速递站时消耗一个、升一级，不打开界面
    private static net.minecraft.world.item.Item otcUpgradeBoxItem() {
        return net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("ordertocook", "otc_upgrade_box"));
    }

    private static boolean isUpgradeBox(ItemStack stack) {
        return !stack.isEmpty() && stack.is(otcUpgradeBoxItem());
    }

    // 手持物品右键（1.21 拆分出的 useItemOn）：只有 OTC 升级装置在此处理（消耗一个、升一级），
    // 其余物品返回 PASS，交给物品自身 useOn / 空手 useWithoutItem 的默认流程。
    @Override
    protected net.minecraft.world.ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                        Player player, InteractionHand hand, BlockHitResult hit) {
        if (!isUpgradeBox(stack)) {
            // 非升级装置：交回默认流程（继续空手 useWithoutItem 开界面 / 物品自身 useOn）
            return net.minecraft.world.ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        if (level.isClientSide) {
            return net.minecraft.world.ItemInteractionResult.SUCCESS;
        }
        BlockEntity be = level.getBlockEntity(pos);
        if (be instanceof JiuhuStationBlockEntity station) {
            station.tryUpgrade(player, hand);
        }
        return net.minecraft.world.ItemInteractionResult.SUCCESS;
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
