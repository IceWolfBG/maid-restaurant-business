/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  javax.annotation.Nullable
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.NonNullList
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.nbt.Tag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.entity.BlockEntityType
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraftforge.items.IItemHandler
 *  net.minecraftforge.items.ItemStackHandler
 */
package com.icewolf.maidrestaurant.business.block.entity;

import com.icewolf.maidrestaurant.business.core.MaidUtils;
import com.icewolf.maidrestaurant.business.item.HealthCertificateItem;
import com.icewolf.maidrestaurant.business.registry.ModBlockEntities;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemStackHandler;

public class PublicNoticeBoardBlockEntity
extends BlockEntity {
    public static final int MAX_CERTIFICATES = 4;
    public static final String TAG_MACHINE_X = "BoundMachineX";
    public static final String TAG_MACHINE_Y = "BoundMachineY";
    public static final String TAG_MACHINE_Z = "BoundMachineZ";
    public static final String TAG_HAS_MACHINE = "HasBoundMachine";
    private static final String BINDING_SOURCE = "public_notice_board";
    private final ItemStackHandler inventory = new ItemStackHandler(4){

        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.getItem() instanceof HealthCertificateItem;
        }

        protected void onContentsChanged(int slot) {
            PublicNoticeBoardBlockEntity.this.setChanged();
        }
    };
    private boolean hasBoundMachine = false;
    @Nullable
    private BlockPos boundMachinePos = null;

    public PublicNoticeBoardBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)ModBlockEntities.PUBLIC_NOTICE_BOARD.get(), pos, state);
    }

    public IItemHandler getInventory() {
        return this.inventory;
    }

    public boolean hasBoundMachine() {
        return this.hasBoundMachine;
    }

    @Nullable
    public BlockPos getBoundMachinePos() {
        return this.boundMachinePos;
    }

    public void bindMachine(BlockPos machinePos) {
        this.boundMachinePos = machinePos;
        this.hasBoundMachine = true;
        this.setChanged();
    }

    public boolean insertCertificate(ItemStack stack) {
        if (!(stack.getItem() instanceof HealthCertificateItem)) {
            return false;
        }
        ItemStack remaining = this.inventory.insertItem(0, stack, false);
        if (remaining.getCount() < stack.getCount()) {
            this.setChanged();
            // 如果公示栏已绑定打单机，且健康证记录了女仆UUID，则绑定女仆
            if (this.hasBoundMachine && this.boundMachinePos != null) {
                UUID maidUUID = HealthCertificateItem.getMaidUUID(stack);
                if (maidUUID != null) {
                    MaidUtils.bindMaidToMachine(maidUUID, this.boundMachinePos, BINDING_SOURCE);
                }
            }
            return true;
        }
        return false;
    }

    public ItemStack extractCertificate() {
        for (int i = 3; i >= 0; --i) {
            ItemStack extracted;
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (stack.isEmpty() || (extracted = this.inventory.extractItem(i, 1, false)).isEmpty()) continue;
            this.setChanged();
            // 解除该健康证记录的女仆的绑定
            UUID maidUUID = HealthCertificateItem.getMaidUUID(extracted);
            if (maidUUID != null) {
                MaidUtils.unbindMaid(maidUUID, BINDING_SOURCE);
            }
            return extracted;
        }
        return ItemStack.EMPTY;
    }
    
    /**
     * 尝试自动绑定最近的打单机（在16格范围内搜索）
     * @return 是否成功绑定
     */
    public boolean tryBindNearestMachine(Level level) {
        if (this.hasBoundMachine) return true;
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(this.worldPosition.offset(-16, -4, -16), this.worldPosition.offset(16, 4, 16))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && be.getClass().getName().contains("OrderMachineBlockEntity")) {
                double dist = pos.distSqr(this.worldPosition);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = pos.immutable();
                }
            }
        }
        if (nearest != null) {
            this.bindMachine(nearest);
            // 重新绑定库存中所有健康证记录的女仆
            for (ItemStack stack : this.getCertificates()) {
                UUID maidUUID = HealthCertificateItem.getMaidUUID(stack);
                if (maidUUID != null) {
                    MaidUtils.bindMaidToMachine(maidUUID, nearest, BINDING_SOURCE);
                }
            }
            return true;
        }
        return false;
    }

    public NonNullList<ItemStack> getCertificates() {
        NonNullList list = NonNullList.create();
        for (int i = 0; i < 4; ++i) {
            ItemStack stack = this.inventory.getStackInSlot(i);
            if (stack.isEmpty()) continue;
            list.add(stack);
        }
        return list;
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        this.inventory.deserializeNBT(tag.getCompound("Inventory"));
        this.hasBoundMachine = tag.getBoolean(TAG_HAS_MACHINE);
        if (this.hasBoundMachine) {
            this.boundMachinePos = new BlockPos(tag.getInt(TAG_MACHINE_X), tag.getInt(TAG_MACHINE_Y), tag.getInt(TAG_MACHINE_Z));
        }
    }

    public void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("Inventory", (Tag)this.inventory.serializeNBT());
        tag.putBoolean(TAG_HAS_MACHINE, this.hasBoundMachine);
        if (this.hasBoundMachine && this.boundMachinePos != null) {
            tag.putInt(TAG_MACHINE_X, this.boundMachinePos.getX());
            tag.putInt(TAG_MACHINE_Y, this.boundMachinePos.getY());
            tag.putInt(TAG_MACHINE_Z, this.boundMachinePos.getZ());
        }
    }

    public Component getDisplayName() {
        return Component.literal((String)"\u516c\u793a\u680f");
    }
}
