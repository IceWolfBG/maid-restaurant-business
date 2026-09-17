package com.icewolf.maidrestaurant.business.block.entity;

import com.icewolf.maidrestaurant.business.block.OrderClipBlock;
import com.icewolf.maidrestaurant.business.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 挂单夹方块实体：只保存一张订单。不同步订单内容到客户端（外观完全由 HAS_ORDER 方块状态决定），
 * 对外暴露的 capability 是只读单槽视图，仅供其它系统查询，不能从侧面塞入 / 抽出。
 */
public class OrderClipBlockEntity extends BlockEntity {
    private static final String TAG_ORDER = "Order";

    private ItemStack order = ItemStack.EMPTY;

    public OrderClipBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ORDER_CLIP.get(), pos, state);
    }

    public boolean isEmpty() {
        return this.order.isEmpty();
    }

    /** 存入一张订单；已有订单或物品不是订单时失败（不吞物品）。 */
    public boolean storeOne(ItemStack stack) {
        if (stack == null || stack.isEmpty() || !this.order.isEmpty() || !OrderClipBlock.isOrderItem(stack)) {
            return false;
        }
        this.order = stack.copy();
        syncState();
        return true;
    }

    /** 取出并清空订单；没有则返回空。 */
    public ItemStack takeOne() {
        if (this.order.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = this.order.copy();
        this.order = ItemStack.EMPTY;
        syncState();
        return out;
    }

    /** 只读查看夹着的订单（副本），不改变内容。 */
    public ItemStack content() {
        return this.order.isEmpty() ? ItemStack.EMPTY : this.order.copy();
    }

    private void syncState() {
        if (this.level != null && !this.level.isClientSide) {
            BlockState state = getBlockState();
            boolean has = !this.order.isEmpty();
            if (state.hasProperty(OrderClipBlock.HAS_ORDER) && state.getValue(OrderClipBlock.HAS_ORDER) != has) {
                this.level.setBlock(this.worldPosition, state.setValue(OrderClipBlock.HAS_ORDER, has), 3);
            }
            setChanged();
        }
    }

    /** 只读单槽 capability：insert 原样返回、extract 永远为空，防止外部绕过交互逻辑。 */
    public IItemHandler getViewHandler() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 1;
            }

            @Override
            public ItemStack getStackInSlot(int slot) {
                return slot == 0 ? OrderClipBlockEntity.this.order : ItemStack.EMPTY;
            }

            @Override
            public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
                return stack;
            }

            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 1;
            }

            @Override
            public boolean isItemValid(int slot, ItemStack stack) {
                return OrderClipBlock.isOrderItem(stack);
            }
        };
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        if (!this.order.isEmpty()) {
            tag.put(TAG_ORDER, this.order.saveOptional(registries));
        }
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(TAG_ORDER)) {
            this.order = ItemStack.parse(registries, tag.getCompound(TAG_ORDER)).orElse(ItemStack.EMPTY);
        } else {
            this.order = ItemStack.EMPTY;
        }
    }

    @Nullable
    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        // 仅同步“是否夹着订单”的外观状态，不同步订单内容
        CompoundTag tag = super.getUpdateTag(registries);
        tag.putBoolean("HasOrder", !this.order.isEmpty());
        return tag;
    }
}
