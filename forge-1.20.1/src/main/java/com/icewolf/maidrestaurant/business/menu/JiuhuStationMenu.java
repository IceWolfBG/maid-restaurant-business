package com.icewolf.maidrestaurant.business.menu;

import com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity;
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

public class JiuhuStationMenu extends AbstractContainerMenu {
    private final JiuhuStationBlockEntity blockEntity;
    private final BlockPos blockPos;
    private final ContainerLevelAccess access;
    private final Player player;

    // 服务端构造函数
    public JiuhuStationMenu(int id, Inventory inventory, JiuhuStationBlockEntity blockEntity) {
        super(ModMenuTypes.JIUHU_STATION.get(), id);
        this.blockEntity = blockEntity;
        this.blockPos = blockEntity.getBlockPos();
        this.player = inventory.player;
        this.access = ContainerLevelAccess.create(blockEntity.getLevel(), blockEntity.getBlockPos());

        // 速递站的5个格子（只读显示，不允许玩家交互）
        // 背景图坐标：x=26+i*26, y=28，格子大小20x20，间距6
        for (int i = 0; i < JiuhuStationBlockEntity.SLOT_COUNT; i++) {
            final int slotIndex = i;
            this.addSlot(new Slot(blockEntity, i, 31 + i * 26, 37) {
                @Override
                public boolean mayPlace(ItemStack stack) {
                    return false;
                }

                @Override
                public boolean mayPickup(Player player) {
                    return false;
                }

                @Override
                public ItemStack remove(int amount) {
                    return ItemStack.EMPTY;
                }
            });
        }

        // 玩家背包和快捷栏（移到GUI外，不显示）
        // 因为我们禁用了放入和取出，所以不需要显示
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, -1000, -1000));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, -1000, -1000));
        }
    }

    // 客户端构造函数
    public JiuhuStationMenu(int id, Inventory inventory, FriendlyByteBuf buf) {
        super(ModMenuTypes.JIUHU_STATION.get(), id);
        this.blockPos = buf.readBlockPos();
        BlockEntity be = inventory.player.level().getBlockEntity(this.blockPos);
        if (be instanceof JiuhuStationBlockEntity) {
            this.blockEntity = (JiuhuStationBlockEntity)be;
        } else {
            this.blockEntity = null;
        }
        this.player = inventory.player;
        this.access = ContainerLevelAccess.NULL;

        // 速递站的5个格子（只读）
        for (int i = 0; i < JiuhuStationBlockEntity.SLOT_COUNT; i++) {
            final int slotIndex = i;
            this.addSlot(new Slot(blockEntity != null ? blockEntity : new net.minecraft.world.SimpleContainer(JiuhuStationBlockEntity.SLOT_COUNT), i, 31 + i * 26, 37) {
                @Override
                public boolean mayPlace(ItemStack stack) { return false; }
                @Override
                public boolean mayPickup(Player player) { return false; }
                @Override
                public ItemStack remove(int amount) { return ItemStack.EMPTY; }
            });
        }

        // 玩家背包和快捷栏（移到GUI外）
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(inventory, col + row * 9 + 9, -1000, -1000));
            }
        }
        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(inventory, col, -1000, -1000));
        }
    }

    public BlockPos getBlockPos() { return this.blockPos; }
    public JiuhuStationBlockEntity getBlockEntity() { return this.blockEntity; }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity != null) {
            return this.blockEntity.getLevel().getBlockEntity(this.blockEntity.getBlockPos()) == this.blockEntity;
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    // 获取配送剩余时间（秒）
    public int getRemainingSeconds(int slot) {
        return blockEntity != null ? blockEntity.getRemainingSeconds(slot) : 0;
    }

    // 获取总配送时间（秒），用于计算进度百分比
    public int getTotalSeconds(int slot) {
        return blockEntity != null ? blockEntity.getTotalSeconds(slot) : 0;
    }

    // 获取实际收益
    public int getActualProfit(int slot) {
        return blockEntity != null ? blockEntity.getActualProfit(slot) : 0;
    }
}
