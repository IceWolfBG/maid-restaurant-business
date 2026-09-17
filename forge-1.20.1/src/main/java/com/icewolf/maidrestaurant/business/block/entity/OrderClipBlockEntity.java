package com.icewolf.maidrestaurant.business.block.entity;

import com.icewolf.maidrestaurant.business.block.OrderClipBlock;
import com.icewolf.maidrestaurant.business.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 挂单夹方块实体：单个槽位，夹一张下单了(OTC)订单。
 * 无 GUI；订单具体内容不同步客户端，仅用 has_order 方块状态表达外观。
 * 对外暴露一个只读单槽物品 capability，供 Jade/WTHIT 等探测模组显示夹着的订单；
 * 漏斗等自动化无法抽插（存取只能通过右键）。
 */
public class OrderClipBlockEntity extends BlockEntity {
    private static final String TAG_ORDER = "Order";

    private ItemStack order = ItemStack.EMPTY;

    // 只读单槽：镜像 order，拒绝任何插入/抽出，仅用于探测模组展示
    private final IItemHandler viewHandler = new IItemHandler() {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        @NotNull
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? order : ItemStack.EMPTY;
        }

        @Override
        @NotNull
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack; // 只读：拒绝插入
        }

        @Override
        @NotNull
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY; // 只读：拒绝抽出
        }

        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot == 0 && OrderClipBlock.isOrderItem(stack);
        }
    };

    private LazyOptional<IItemHandler> itemHandlerCap = LazyOptional.of(() -> viewHandler);

    public OrderClipBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORDER_CLIP.get(), pos, state);
    }

    public boolean isEmpty() {
        return order.isEmpty();
    }

    /**
     * 夹上一张订单（传入栈应为 1 个）。已有订单时返回 false。
     */
    public boolean storeOne(ItemStack one) {
        if (one == null || one.isEmpty() || !order.isEmpty()) {
            return false;
        }
        order = one;
        syncState();
        return true;
    }

    /**
     * 取下订单；夹上为空时返回空栈。
     */
    public ItemStack takeOne() {
        if (order.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack result = order;
        order = ItemStack.EMPTY;
        syncState();
        return result;
    }

    /** 夹着的订单副本（破坏方块时掉落用）。 */
    public ItemStack content() {
        return order.isEmpty() ? ItemStack.EMPTY : order.copy();
    }

    private void syncState() {
        setChanged();
        if (level != null && !level.isClientSide) {
            BlockState state = getBlockState();
            if (state.hasProperty(OrderClipBlock.HAS_ORDER)) {
                level.setBlock(worldPosition, state.setValue(OrderClipBlock.HAS_ORDER, !order.isEmpty()), 3);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put(TAG_ORDER, order.save(new CompoundTag()));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.order = ItemStack.of(tag.getCompound(TAG_ORDER));
    }

    @Override
    public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return itemHandlerCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemHandlerCap.invalidate();
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        itemHandlerCap = LazyOptional.of(() -> viewHandler);
    }
}
