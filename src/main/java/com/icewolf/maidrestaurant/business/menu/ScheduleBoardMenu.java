package com.icewolf.maidrestaurant.business.menu;

import com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity;
import com.icewolf.maidrestaurant.business.registry.ModMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;

public class ScheduleBoardMenu extends AbstractContainerMenu {
    private final ScheduleBoardBlockEntity blockEntity;
    private final BlockPos blockPos; // 客户端存储方块位置
    private final ContainerLevelAccess access;
    private final Player player;
    private final boolean isClient;

    // 服务端构造函数
    public ScheduleBoardMenu(int id, Inventory inventory, ScheduleBoardBlockEntity blockEntity) {
        super(ModMenuTypes.SCHEDULE_BOARD.get(), id);
        this.blockEntity = blockEntity;
        this.blockPos = blockEntity.getBlockPos();
        this.player = inventory.player;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());
        this.isClient = false;
    }

    // 客户端构造函数
    public ScheduleBoardMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(ModMenuTypes.SCHEDULE_BOARD.get(), id);
        this.blockPos = buf.readBlockPos();
        BlockEntity be = inventory.player.level().getBlockEntity(this.blockPos);
        if (be instanceof ScheduleBoardBlockEntity) {
            this.blockEntity = (ScheduleBoardBlockEntity)be;
        } else {
            this.blockEntity = null;
        }
        this.player = inventory.player;
        this.access = ContainerLevelAccess.NULL;
        this.isClient = true;
    }

    public BlockPos getBlockPos() {
        return this.blockPos;
    }

    public ScheduleBoardBlockEntity getBlockEntity() {
        return this.blockEntity;
    }

    public boolean stillValid(Player player) {
        if (this.blockEntity != null) {
            return this.blockEntity.getLevel().getBlockEntity(this.blockEntity.getBlockPos()) == this.blockEntity;
        }
        return true; // 客户端没有blockEntity时也允许打开
    }

    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    // 配置读取方法：优先从blockEntity读取，客户端如果blockEntity为null则返回默认值
    public boolean isAutoEnabled() { return blockEntity != null && blockEntity.isAutoEnabled(); }
    public boolean isAutoDelivery() { return blockEntity != null && blockEntity.isAutoDelivery(); }
    public boolean isAutoPackaging() { return blockEntity != null && blockEntity.isAutoPackaging(); }
    public boolean isAutoCooking() { return blockEntity != null && blockEntity.isAutoCooking(); }
    public boolean isAutoPrep() { return blockEntity != null && blockEntity.isAutoPrep(); }
    public boolean isAutoCollect() { return blockEntity != null && blockEntity.isAutoCollect(); }
    public boolean isAutoWash() { return blockEntity != null && blockEntity.isAutoWash(); }
    public int getMinPlatesToWash() { return blockEntity != null ? blockEntity.getMinPlatesToWash() : 3; }
    public int getWorkSchedule() { return blockEntity != null ? blockEntity.getWorkSchedule() : 2; }
    public boolean isBellEnabled() { return blockEntity != null && blockEntity.isBellEnabled(); }
    public String getScheduleName() { return blockEntity != null ? blockEntity.getScheduleName() : "全天"; }
}
