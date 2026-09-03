package com.icewolf.maidrestaurant.business.block.entity;

import com.icewolf.maidrestaurant.business.config.TakeoutConfig;
import com.icewolf.maidrestaurant.business.menu.JiuhuStationMenu;
import com.icewolf.maidrestaurant.business.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public class JiuhuStationBlockEntity extends BlockEntity implements Container {
    public static final int SLOT_COUNT = 5;
    public static final String TAG_ITEMS = "Items";
    public static final String TAG_DELIVERY_TIMES = "DeliveryTimes";
    public static final String TAG_TOTAL_DELIVERY_TIMES = "TotalDeliveryTimes";
    public static final String TAG_BASE_PROFITS = "BaseProfits";
    public static final String TAG_MACHINE_POS = "MachinePos";
    public static final String TAG_OWNER_UUID = "OwnerUUID";
    public static final String TAG_CUSTOMER_POS = "CustomerPos";

    // 配送配置已移至 TakeoutConfig（独立配置文件 maid_restaurant_business-takeout.toml）

    private final ItemStack[] items = new ItemStack[SLOT_COUNT];
    private final int[] deliveryTimes = new int[SLOT_COUNT]; // 剩余配送时间（tick）
    private final int[] totalDeliveryTimes = new int[SLOT_COUNT]; // 总配送时间（tick），用于进度计算
    private final int[] baseProfits = new int[SLOT_COUNT]; // 基础收益
    @Nullable
    private BlockPos machinePos = null; // 关联的打单机位置（用于获取等级和玩家）
    @Nullable
    private java.util.UUID ownerUuid = null; // 女仆主人的UUID（用于收益）
    @Nullable
    private BlockPos customerPos = null; // 顾客坐标（用于计算配送距离）

    public JiuhuStationBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)ModBlockEntities.JIUHU_STATION.get(), pos, state);
        for (int i = 0; i < SLOT_COUNT; i++) {
            items[i] = ItemStack.EMPTY;
            deliveryTimes[i] = 0;
            baseProfits[i] = 0;
        }
    }

    // ========== Container 接口 ==========
    @Override
    public int getContainerSize() { return SLOT_COUNT; }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) return false;
        }
        return true;
    }

    @Override
    public ItemStack getItem(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return ItemStack.EMPTY;
        return items[slot];
    }

    @Override
    public ItemStack removeItem(int slot, int amount) {
        if (slot < 0 || slot >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack stack = items[slot];
        if (stack.isEmpty()) return ItemStack.EMPTY;
        // 正常实现移除逻辑，防止玩家拿取由Slot的mayPickup=false控制
        ItemStack result = stack.copy();
        result.setCount(Math.min(amount, stack.getCount()));
        stack.shrink(amount);
        if (stack.isEmpty()) {
            items[slot] = ItemStack.EMPTY;
            deliveryTimes[slot] = 0;
            baseProfits[slot] = 0;
        }
        setChanged();
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return ItemStack.EMPTY;
        ItemStack stack = items[slot];
        if (stack.isEmpty()) return ItemStack.EMPTY;
        items[slot] = ItemStack.EMPTY;
        deliveryTimes[slot] = 0;
        baseProfits[slot] = 0;
        return stack;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        items[slot] = stack;
        if (!stack.isEmpty() && deliveryTimes[slot] == 0) {
            // 新放入外卖袋，初始化配送计时
            initDelivery(slot, stack);
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.level.getBlockEntity(this.worldPosition) != this) return false;
        return player.distanceToSqr((double)this.worldPosition.getX() + 0.5, (double)this.worldPosition.getY() + 0.5, (double)this.worldPosition.getZ() + 0.5) <= 64.0;
    }

    @Override
    public void clearContent() {
        for (int i = 0; i < SLOT_COUNT; i++) {
            items[i] = ItemStack.EMPTY;
            deliveryTimes[i] = 0;
            baseProfits[i] = 0;
        }
    }

    // ========== 配送逻辑 ==========

    /**
     * 女仆放入外卖袋时调用，初始化配送计时
     * @param stack 外卖袋
     * @param machinePos 关联的打单机位置
     * @param ownerUuid 女仆主人的UUID（用于收益）
     */
    public boolean addDeliveryBag(ItemStack stack, @Nullable BlockPos machinePos, @Nullable java.util.UUID ownerUuid) {
        // 找空格子
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (items[i].isEmpty()) {
                items[i] = stack.copy();
                this.machinePos = machinePos;
                this.ownerUuid = ownerUuid;
                initDelivery(i, stack);
                setChanged();
                // 同步数据到客户端，让GUI能显示外卖袋
                if (level != null && !level.isClientSide) {
                    level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
                }
                return true;
            }
        }
        return false; // 已满
    }

    /**
     * 检查是否有空格子
     */
    public boolean hasEmptySlot() {
        for (ItemStack stack : items) {
            if (stack.isEmpty()) return true;
        }
        return false;
    }

    /**
     * 初始化配送计时
     */
    private void initDelivery(int slot, ItemStack stack) {
        try {
            // 通过DataCompat获取外卖袋NBT（和OTC源码一致）
            CompoundTag tag = null;
            try {
                Class<?> dataCompatClass = Class.forName("cn.breezeth.ordertocook.util.DataCompat");
                java.lang.reflect.Method copyMethod = dataCompatClass.getMethod("copy", ItemStack.class);
                Object result = copyMethod.invoke(null, stack);
                if (result instanceof CompoundTag) {
                    tag = (CompoundTag) result;
                }
            } catch (Exception e) {
                // DataCompat不可用，1.21.1使用Data Components
                try {
                    net.minecraft.world.item.component.CustomData customData = stack.get(net.minecraft.core.component.DataComponents.CUSTOM_DATA);
                    if (customData != null) {
                        tag = customData.copyTag();
                    }
                } catch (Exception e2) {
                    tag = null;
                }
            }
            
            int deliveryDist = 50; // 默认距离50格
            int profit = 10; // 默认收益
            customerPos = null;

            if (tag != null) {
                // 读取顾客坐标（字段名是delivery_pos，CompoundTag包含x和z）
                if (tag.contains("delivery_pos")) {
                    CompoundTag posTag = tag.getCompound("delivery_pos");
                    if (posTag.contains("x") && posTag.contains("z")) {
                        int cx = posTag.getInt("x");
                        int cz = posTag.getInt("z");
                        // y坐标用配送站的y坐标（外卖袋NBT中没有y）
                        customerPos = new BlockPos(cx, this.worldPosition.getY(), cz);
                        // 计算配送站到顾客的x、z距离
                        int dx = Math.abs(cx - this.worldPosition.getX());
                        int dz = Math.abs(cz - this.worldPosition.getZ());
                        deliveryDist = dx + dz; // 曼哈顿距离
                    }
                } else {
                    com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.warn("酒狐速递站: 外卖袋中没有delivery_pos字段，使用默认距离 {} 格", deliveryDist);
                }
                
                // 读取收益（字段名是Prestige，不是profit）
                if (tag.contains("Prestige")) {
                    profit = tag.getInt("Prestige");
                } else {
                    com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.warn("酒狐速递站: 外卖袋中没有Prestige字段，使用默认收益 {}", profit);
                }
            }

            // 获取打单机等级
            int machineLevel = 0;
            if (machinePos != null && level != null) {
                BlockEntity machineBe = level.getBlockEntity(machinePos);
                if (machineBe != null) {
                    try {
                        java.lang.reflect.Method getLevelMethod = machineBe.getClass().getMethod("getRestaurantLevel");
                        Object result = getLevelMethod.invoke(machineBe);
                        if (result instanceof Integer) {
                            machineLevel = (Integer) result;
                        }
                    } catch (Exception e) {
                        // 忽略，使用默认0级
                    }
                }
            }

            // 计算配送时间：距离 / 速度（使用外卖配置文件中的值）
            int speed = TakeoutConfig.baseDeliverySpeed + machineLevel * TakeoutConfig.speedPerLevel;
            int deliverySeconds = Math.max(TakeoutConfig.minDeliverySeconds, Math.min(TakeoutConfig.maxDeliverySeconds, deliveryDist / speed));
            deliveryTimes[slot] = deliverySeconds * 20;
            totalDeliveryTimes[slot] = deliverySeconds * 20;

            // 计算手续费和实际收益（使用外卖配置文件中的值）
            double fee = Math.max(TakeoutConfig.minFee, TakeoutConfig.baseFee - machineLevel * TakeoutConfig.feePerLevel);
            baseProfits[slot] = Math.max(1, (int)Math.floor(profit * (1.0 - fee)));

        } catch (Exception e) {
            com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error("酒狐速递站: initDelivery错误", e);
            deliveryTimes[slot] = TakeoutConfig.minDeliverySeconds * 20;
            totalDeliveryTimes[slot] = TakeoutConfig.minDeliverySeconds * 20;
            baseProfits[slot] = 1;
        }
    }

    /**
     * 获取配送进度（0-1）
     */
    public float getDeliveryProgress(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return 0;
        if (items[slot].isEmpty() || totalDeliveryTimes[slot] <= 0) return 0;
        if (deliveryTimes[slot] <= 0) return 1.0f; // 已完成
        return 1.0f - ((float)deliveryTimes[slot] / (float)totalDeliveryTimes[slot]);
    }

    /**
     * 获取总配送时间（秒）
     */
    public int getTotalSeconds(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return 0;
        return totalDeliveryTimes[slot] / 20;
    }

    /**
     * 获取剩余配送时间（秒）
     */
    public int getRemainingSeconds(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return 0;
        return deliveryTimes[slot] / 20;
    }

    /**
     * 获取实际收益
     */
    public int getActualProfit(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return 0;
        return baseProfits[slot];
    }

    /**
     * 结算配送完成的订单
     */
    private void completeDelivery(int slot) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        ItemStack stack = items[slot];
        if (stack.isEmpty()) return;

        int profit = baseProfits[slot];

        // 用女仆主人的UUID给收益
        if (level != null && !level.isClientSide && ownerUuid != null) {
            net.minecraft.server.level.ServerPlayer owner = level.getServer().getPlayerList().getPlayer(ownerUuid);
            if (owner != null) {
                try {
                    // 调用OTC的CoinUtils给收益（注意参数是Player不是ServerPlayer）
                    Class<?> coinUtilsClass = Class.forName("cn.breezeth.ordertocook.util.CoinUtils");
                    java.lang.reflect.Method giveCoinsMethod = coinUtilsClass.getMethod("giveCoins", net.minecraft.world.entity.player.Player.class, int.class);
                    giveCoinsMethod.invoke(null, owner, profit);
                    
                    // 复用OTC原版的订单完成消息提示（翻译键 message.ordertocook.order_complete）
                    owner.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.ordertocook.order_complete", profit).withStyle(net.minecraft.ChatFormatting.GOLD), false);
                } catch (Exception e) {
                    com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error("酒狐速递站: CoinUtils给收益失败", e);
                    // 如果CoinUtils不可用，直接给玩家经验值
                    owner.giveExperiencePoints(profit);
                }
            } else {
                com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.warn("酒狐速递站: 找不到玩家 {}, 收益未发放", ownerUuid);
            }
        }

        // 清空格子
        items[slot] = ItemStack.EMPTY;
        deliveryTimes[slot] = 0;
        baseProfits[slot] = 0;
        setChanged();
    }

    // ========== Tick ==========
    private int syncCounter = 0;
    
    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        if (!(blockEntity instanceof JiuhuStationBlockEntity station)) return;
        if (level.isClientSide) return;

        boolean changed = false;
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!station.items[i].isEmpty() && station.deliveryTimes[i] > 0) {
                station.deliveryTimes[i]--;
                changed = true;
                if (station.deliveryTimes[i] <= 0) {
                    station.completeDelivery(i);
                }
            }
        }
        if (changed) {
            station.setChanged();
            // 每10tick同步一次到客户端，避免每tick都同步导致性能问题
            station.syncCounter++;
            if (station.syncCounter >= 10) {
                station.syncCounter = 0;
                level.sendBlockUpdated(pos, state, state, 3);
            }
        }
    }

    // ========== NBT ==========
    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.loadAdditional(tag, provider);
        // 使用NonNullList来加载物品
        net.minecraft.core.NonNullList<ItemStack> loadedItems = net.minecraft.core.NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        ContainerHelper.loadAllItems(tag, loadedItems, provider);
        for (int i = 0; i < SLOT_COUNT; i++) {
            items[i] = loadedItems.get(i);
            deliveryTimes[i] = 0;
            baseProfits[i] = 0;
        }
        if (tag.contains(TAG_DELIVERY_TIMES)) {
            int[] times = tag.getIntArray(TAG_DELIVERY_TIMES);
            for (int i = 0; i < times.length && i < SLOT_COUNT; i++) {
                deliveryTimes[i] = times[i];
            }
        }
        if (tag.contains(TAG_TOTAL_DELIVERY_TIMES)) {
            int[] times = tag.getIntArray(TAG_TOTAL_DELIVERY_TIMES);
            for (int i = 0; i < times.length && i < SLOT_COUNT; i++) {
                totalDeliveryTimes[i] = times[i];
            }
        }
        if (tag.contains(TAG_BASE_PROFITS)) {
            int[] profits = tag.getIntArray(TAG_BASE_PROFITS);
            for (int i = 0; i < profits.length && i < SLOT_COUNT; i++) {
                baseProfits[i] = profits[i];
            }
        }
        if (tag.contains(TAG_MACHINE_POS)) {
            int[] posArr = tag.getIntArray(TAG_MACHINE_POS);
            if (posArr.length == 3) {
                machinePos = new BlockPos(posArr[0], posArr[1], posArr[2]);
            }
        }
        if (tag.contains(TAG_OWNER_UUID)) {
            try {
                ownerUuid = tag.getUUID(TAG_OWNER_UUID);
            } catch (Exception e) {
                ownerUuid = null;
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider provider) {
        super.saveAdditional(tag, provider);
        // 使用NonNullList来保存物品
        net.minecraft.core.NonNullList<ItemStack> saveItems = net.minecraft.core.NonNullList.withSize(SLOT_COUNT, ItemStack.EMPTY);
        for (int i = 0; i < SLOT_COUNT; i++) {
            saveItems.set(i, items[i]);
        }
        ContainerHelper.saveAllItems(tag, saveItems, true, provider);
        tag.putIntArray(TAG_DELIVERY_TIMES, deliveryTimes);
        tag.putIntArray(TAG_TOTAL_DELIVERY_TIMES, totalDeliveryTimes);
        tag.putIntArray(TAG_BASE_PROFITS, baseProfits);
        if (machinePos != null) {
            tag.putIntArray(TAG_MACHINE_POS, new int[]{machinePos.getX(), machinePos.getY(), machinePos.getZ()});
        }
        if (ownerUuid != null) {
            tag.putUUID(TAG_OWNER_UUID, ownerUuid);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider provider) {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag, provider);
        return tag;
    }

    // ========== 静态工具方法 ==========

    /**
     * 查找附近的酒狐速递站
     */
    @Nullable
    public static JiuhuStationBlockEntity findNearbyStation(Level level, BlockPos centerPos, int range) {
        if (level == null || centerPos == null) return null;
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(centerPos.offset(-range, -range / 2, -range), centerPos.offset(range, range / 2, range))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof JiuhuStationBlockEntity station) {
                if (!station.hasEmptySlot()) continue;
                double dist = pos.distSqr(centerPos);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = pos.immutable();
                }
            }
        }
        if (nearest != null) {
            BlockEntity be = level.getBlockEntity(nearest);
            if (be instanceof JiuhuStationBlockEntity station) {
                return station;
            }
        }
        return null;
    }

    // ========== MenuProvider ==========
    public MenuProvider getMenuProvider() {
        return new MenuProvider() {
            public Component getDisplayName() {
                return Component.literal("酒狐速递站");
            }

            @Nullable
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new JiuhuStationMenu(id, inventory, JiuhuStationBlockEntity.this);
            }
        };
    }
}
