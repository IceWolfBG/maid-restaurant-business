package com.icewolf.maidrestaurant.business.block.entity;

import com.icewolf.maidrestaurant.business.config.TakeoutConfig;
import com.icewolf.maidrestaurant.business.menu.JiuhuStationMenu;
import com.icewolf.maidrestaurant.business.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
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

    // 配送配置已移至 TakeoutConfig（独立配置文件 maid_restaurant_business-takeout.toml）

    private final ItemStack[] items = new ItemStack[SLOT_COUNT];
    private final int[] deliveryTimes = new int[SLOT_COUNT]; // 剩余配送时间（tick）
    private final int[] totalDeliveryTimes = new int[SLOT_COUNT]; // 总配送时间（tick），用于进度计算
    private final int[] baseProfits = new int[SLOT_COUNT]; // 基础收益
    // 每个槽位独立的归属信息：避免多个外卖袋先后放入时互相覆盖，
    // 导致所有配送都把收益算给最后一个放入袋子的女仆主人
    private final BlockPos[] machinePositions = new BlockPos[SLOT_COUNT]; // 各槽关联打单机（仅用于补皮革货架）
    private final java.util.UUID[] ownerUuids = new java.util.UUID[SLOT_COUNT]; // 各槽女仆主人UUID（结算收益用）

    // 速递站自身等级：手持 OTC 升级装置右键速递站升级，持久化，决定配送速度与手续费。
    // 不再反射关联打单机取等级（速递站并未绑定打单机，旧逻辑永远取到 0 级）。
    private int upgradeLevel = 0;
    public static final String TAG_UPGRADE_LEVEL = "UpgradeLevel";

    public static final String TAG_OWNER_UUIDS = "OwnerUUIDs";
    public static final String TAG_MACHINE_POSITIONS = "MachinePositions";

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
        // 速递站的格子是只读的，玩家不能手动拿取
        // 但女仆放入时需要调用setItem，所以这里返回空表示不能拿取
        return ItemStack.EMPTY;
    }

    @Override
    public ItemStack removeItemNoUpdate(int slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void setItem(int slot, ItemStack stack) {
        if (slot < 0 || slot >= SLOT_COUNT) return;
        items[slot] = stack;
        if (!stack.isEmpty() && deliveryTimes[slot] == 0) {
            // 新放入外卖袋，初始化配送计时
            initDelivery(slot, stack, machinePositions[slot]);
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
            totalDeliveryTimes[i] = 0;
            baseProfits[i] = 0;
            machinePositions[i] = null;
            ownerUuids[i] = null;
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
                this.machinePositions[i] = machinePos;
                this.ownerUuids[i] = ownerUuid;
                initDelivery(i, stack, machinePos);
                // 外卖袋成功放入（刚消耗1个皮革打包）：立即触发一次关联打单机的包装货架补皮革检查
                // 放在放入时而非配送结算时，是因为配送要等较长时间，补货应尽早进行
                try {
                    if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && machinePos != null) {
                        com.icewolf.maidrestaurant.business.core.RestockBridge.requestCheck(serverLevel, machinePos);
                    }
                } catch (Throwable t) {}
                setChanged();
                // 立即向客户端同步一次，让外卖袋放入即时显示（不必等配送计时的5tick同步节奏）
                if (level != null && !level.isClientSide) {
                    BlockState cur = getBlockState();
                    level.sendBlockUpdated(worldPosition, cur, cur, 3);
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

    public int getUpgradeLevel() { return upgradeLevel; }

    /**
     * 手持 OTC 升级装置右键速递站：消耗 1 个升级装置，速递站等级 +1。
     * 满级时不消耗、仅提示。等级决定配送速度与手续费。
     * @return true 表示本次右键被升级流程处理（调用方应返回成功、不再打开界面）
     */
    public boolean tryUpgrade(net.minecraft.world.entity.player.Player player, net.minecraft.world.InteractionHand hand) {
        if (level == null || level.isClientSide) return false;
        ItemStack box = player.getItemInHand(hand);

        if (upgradeLevel >= TakeoutConfig.maxUpgradeLevel) {
            player.displayClientMessage(Component.literal("酒狐速递站已经是最高等级啦（" + TakeoutConfig.maxUpgradeLevel + "级）")
                    .withStyle(net.minecraft.ChatFormatting.YELLOW), true);
            return true; // 拦截右键，不打开界面、不消耗
        }

        upgradeLevel++;

        // 升级反馈：云屑粒子 + 锻造台/经验音效（与 OTC 升级装置 finishUpgrade 风格一致）
        double x = this.worldPosition.getX() + 0.5;
        double y = this.worldPosition.getY() + 0.75;
        double z = this.worldPosition.getZ() + 0.5;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CLOUD, x, y, z, 28, 0.35, 0.35, 0.35, 0.025);
        }
        level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.ANVIL_USE,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.55f, 1.45f);
        level.playSound(null, this.worldPosition, net.minecraft.sounds.SoundEvents.EXPERIENCE_ORB_PICKUP,
                net.minecraft.sounds.SoundSource.BLOCKS, 0.45f, 1.8f);

        // 创造模式不消耗，生存消耗 1 个升级装置
        if (!player.getAbilities().instabuild) {
            box.shrink(1);
        }

        player.displayClientMessage(Component.literal("酒狐速递站升级！当前等级 " + upgradeLevel + " 级")
                .withStyle(net.minecraft.ChatFormatting.GOLD), true);

        setChanged();
        BlockState curState = getBlockState();
        level.sendBlockUpdated(this.worldPosition, curState, curState, 3);
        return true;
    }

    /**
     * 初始化配送计时
     */
    private void initDelivery(int slot, ItemStack stack, @Nullable BlockPos machinePos) {
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
                // DataCompat不可用，直接用stack.getTag()
                tag = stack.getTag();
            }
            
            int deliveryDist = 50; // 默认距离50格
            int profit = 10; // 默认收益
            BlockPos customerPos = null;

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

            // 速递站自身等级（用 OTC 升级装置右键升级，持久化），不再反射关联打单机取等级。
            int levelForStats = this.upgradeLevel;

            // 计算配送时间：距离 / 速度（使用外卖配置文件中的值）
            int speed = TakeoutConfig.baseDeliverySpeed + levelForStats * TakeoutConfig.speedPerLevel;
            int deliverySeconds = Math.max(TakeoutConfig.minDeliverySeconds, Math.min(TakeoutConfig.maxDeliverySeconds, deliveryDist / speed));
            deliveryTimes[slot] = deliverySeconds * 20;
            totalDeliveryTimes[slot] = deliverySeconds * 20;

            // 计算手续费和实际收益（使用外卖配置文件中的值）
            double fee = Math.max(TakeoutConfig.minFee, TakeoutConfig.baseFee - levelForStats * TakeoutConfig.feePerLevel);
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
        java.util.UUID owner = ownerUuids[slot];

        // 用"放入该外卖袋的女仆主人"UUID结算（每槽独立，互不覆盖）。
        // 主人在线直接发；主人离线时用同一UUID的FakePlayer补发，OTC金币按UUID记账，主人上线后到账。
        if (level != null && !level.isClientSide && owner != null && level.getServer() != null) {
            net.minecraft.server.level.ServerPlayer online = level.getServer().getPlayerList().getPlayer(owner);
            net.minecraft.world.entity.player.Player payee = online;
            if (payee == null && level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
                try {
                    payee = net.minecraftforge.common.util.FakePlayerFactory.get(
                            serverLevel, new com.mojang.authlib.GameProfile(owner, "MaidOwner"));
                } catch (Throwable t) {
                    com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error("酒狐速递站: 创建离线主人FakePlayer失败", t);
                }
            }
            if (payee != null) {
                try {
                    // 调用OTC的CoinUtils给收益（参数是Player不是ServerPlayer，FakePlayer也可）
                    Class<?> coinUtilsClass = Class.forName("cn.breezeth.ordertocook.util.CoinUtils");
                    java.lang.reflect.Method giveCoinsMethod = coinUtilsClass.getMethod("giveCoins", net.minecraft.world.entity.player.Player.class, int.class);
                    giveCoinsMethod.invoke(null, payee, profit);
                    // 仅在线主人发可见提示（复用OTC原版翻译键 message.ordertocook.order_complete）
                    if (online != null) {
                        online.displayClientMessage(net.minecraft.network.chat.Component.translatable("message.ordertocook.order_complete", profit).withStyle(net.minecraft.ChatFormatting.GOLD), false);
                    }
                } catch (Exception e) {
                    com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.error("酒狐速递站: CoinUtils给收益失败", e);
                    payee.giveExperiencePoints(profit);
                }
            } else {
                com.icewolf.maidrestaurant.business.MaidRestaurantBusiness.LOGGER.warn("酒狐速递站: 找不到主人 {} 且无法补发，收益未发放", owner);
            }
        }

        // 清空格子及其归属
        items[slot] = ItemStack.EMPTY;
        deliveryTimes[slot] = 0;
        totalDeliveryTimes[slot] = 0;
        baseProfits[slot] = 0;
        machinePositions[slot] = null;
        ownerUuids[slot] = null;
        setChanged();
    }

    // ========== Tick ==========
    // 配送进度向客户端同步的间隔（tick）。配送计时每tick推进，但整格NBT同步从每tick降到每5tick（4Hz）：
    // 进度条按整数秒显示，0.25秒一跳视觉无差异，却把同步包与getUpdateTag序列化开销降到约1/5。
    private static final long SYNC_INTERVAL = 5L;

    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        if (!(blockEntity instanceof JiuhuStationBlockEntity station)) return;
        if (level.isClientSide) return;

        boolean debug = false;
        try { debug = com.icewolf.maidrestaurant.business.config.PerformanceConfig.debugPerformance; } catch (Throwable ignored) {}
        long gt = level.getGameTime();

        boolean delivering = false; // 本tick是否有外卖袋正在配送（旧逻辑此刻本应发一个同步包）
        boolean completed = false;  // 本tick是否有订单刚好结算（结算当刻必须立即同步一次）
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!station.items[i].isEmpty() && station.deliveryTimes[i] > 0) {
                station.deliveryTimes[i]--;
                delivering = true;
                if (station.deliveryTimes[i] <= 0) {
                    station.completeDelivery(i); // 内部已 setChanged()
                    completed = true;
                }
            }
        }

        if (debug && delivering) com.icewolf.maidrestaurant.business.core.TaskManager.perfSyncWould++;

        // 计时推进期间落盘标记也降到每5tick一次（结算当刻 completeDelivery 已自行 setChanged）
        if (delivering && gt % SYNC_INTERVAL == 0L) {
            station.setChanged();
        }
        // 向客户端同步：结算当刻强制一次，其余配送中按5tick节奏；保证结算/放入不丢包
        boolean shouldSync = completed || (delivering && gt % SYNC_INTERVAL == 0L);
        if (shouldSync) {
            level.sendBlockUpdated(pos, state, state, 3);
            if (debug) com.icewolf.maidrestaurant.business.core.TaskManager.perfSyncSent++;
        }
    }

    // ========== NBT ==========
    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        this.upgradeLevel = tag.contains(TAG_UPGRADE_LEVEL) ? tag.getInt(TAG_UPGRADE_LEVEL) : 0;
        ListTag itemsList = tag.getList(TAG_ITEMS, Tag.TAG_COMPOUND);
        for (int i = 0; i < SLOT_COUNT; i++) {
            items[i] = ItemStack.EMPTY;
            deliveryTimes[i] = 0;
            baseProfits[i] = 0;
        }
        for (int i = 0; i < itemsList.size() && i < SLOT_COUNT; i++) {
            CompoundTag itemTag = itemsList.getCompound(i);
            int slot = itemTag.getByte("Slot");
            if (slot >= 0 && slot < SLOT_COUNT) {
                items[slot] = ItemStack.of(itemTag);
            }
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
        // 每槽归属（新格式：按槽位存列表）
        java.util.Map<Integer, BlockPos> machineBySlot = new java.util.HashMap<>();
        java.util.Map<Integer, java.util.UUID> ownerBySlot = new java.util.HashMap<>();
        if (tag.contains(TAG_MACHINE_POSITIONS)) {
            ListTag posList = tag.getList(TAG_MACHINE_POSITIONS, Tag.TAG_COMPOUND);
            for (int i = 0; i < posList.size(); i++) {
                CompoundTag pt = posList.getCompound(i);
                int s = pt.getByte("Slot");
                if (s >= 0 && s < SLOT_COUNT && pt.contains("x") && pt.contains("y") && pt.contains("z")) {
                    machineBySlot.put(s, new BlockPos(pt.getInt("x"), pt.getInt("y"), pt.getInt("z")));
                }
            }
        }
        if (tag.contains(TAG_OWNER_UUIDS)) {
            ListTag ownerList = tag.getList(TAG_OWNER_UUIDS, Tag.TAG_COMPOUND);
            for (int i = 0; i < ownerList.size(); i++) {
                CompoundTag ot = ownerList.getCompound(i);
                int s = ot.getByte("Slot");
                if (s >= 0 && s < SLOT_COUNT && ot.contains("UUID")) {
                    ownerBySlot.put(s, ot.getUUID("UUID"));
                }
            }
        }
        // 旧存档兼容：旧版本只存单值 ownerUuid/machinePos，迁移到第一个仍有外卖袋的槽位
        if (ownerBySlot.isEmpty() && tag.contains(TAG_OWNER_UUID)) {
            try {
                java.util.UUID legacyOwner = tag.getUUID(TAG_OWNER_UUID);
                for (int s = 0; s < SLOT_COUNT; s++) {
                    if (!items[s].isEmpty()) { ownerBySlot.put(s, legacyOwner); break; }
                }
            } catch (Exception ignored) {}
        }
        if (machineBySlot.isEmpty() && tag.contains(TAG_MACHINE_POS)) {
            int[] posArr = tag.getIntArray(TAG_MACHINE_POS);
            if (posArr.length == 3) {
                BlockPos legacyMachine = new BlockPos(posArr[0], posArr[1], posArr[2]);
                for (int s = 0; s < SLOT_COUNT; s++) {
                    if (!items[s].isEmpty()) { machineBySlot.put(s, legacyMachine); break; }
                }
            }
        }
        for (int s = 0; s < SLOT_COUNT; s++) {
            machinePositions[s] = machineBySlot.get(s);
            ownerUuids[s] = ownerBySlot.get(s);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putInt(TAG_UPGRADE_LEVEL, upgradeLevel);
        ListTag itemsList = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (!items[i].isEmpty()) {
                CompoundTag itemTag = new CompoundTag();
                itemTag.putByte("Slot", (byte)i);
                items[i].save(itemTag);
                itemsList.add(itemTag);
            }
        }
        tag.put(TAG_ITEMS, itemsList);
        tag.putIntArray(TAG_DELIVERY_TIMES, deliveryTimes);
        tag.putIntArray(TAG_TOTAL_DELIVERY_TIMES, totalDeliveryTimes);
        tag.putIntArray(TAG_BASE_PROFITS, baseProfits);
        // 每槽归属写为列表
        ListTag machineList = new ListTag();
        ListTag ownerList = new ListTag();
        for (int i = 0; i < SLOT_COUNT; i++) {
            if (machinePositions[i] != null) {
                CompoundTag pt = new CompoundTag();
                pt.putByte("Slot", (byte) i);
                pt.putInt("x", machinePositions[i].getX());
                pt.putInt("y", machinePositions[i].getY());
                pt.putInt("z", machinePositions[i].getZ());
                machineList.add(pt);
            }
            if (ownerUuids[i] != null) {
                CompoundTag ot = new CompoundTag();
                ot.putByte("Slot", (byte) i);
                ot.putUUID("UUID", ownerUuids[i]);
                ownerList.add(ot);
            }
        }
        tag.put(TAG_MACHINE_POSITIONS, machineList);
        tag.put(TAG_OWNER_UUIDS, ownerList);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }

    // ========== 静态工具方法 ==========

    /**
     * 查找附近的酒狐速递站
     */
    @Nullable
    public static JiuhuStationBlockEntity findNearbyStation(Level level, BlockPos centerPos, int range) {
        if (level == null || centerPos == null) return null;

        boolean debug = false;
        try { debug = com.icewolf.maidrestaurant.business.config.PerformanceConfig.debugPerformance; } catch (Throwable ignored) {}
        long startNanos = debug ? System.nanoTime() : 0L;
        if (debug) com.icewolf.maidrestaurant.business.core.TaskManager.perfStationScans++;

        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        int yHalf = range / 2;

        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            // 区块级遍历：只扫描与搜索立方相交的已加载chunk（getChunkNow不强制加载），
            // 从原先逐坐标 getBlockEntity（range=24时约49*25*49≈6万次）降到只检查几十个方块实体，结果完全等价。
            int minCX = (centerPos.getX() - range) >> 4;
            int maxCX = (centerPos.getX() + range) >> 4;
            int minCZ = (centerPos.getZ() - range) >> 4;
            int maxCZ = (centerPos.getZ() + range) >> 4;
            int chunkCount = 0;
            int beCount = 0;
            for (int cx = minCX; cx <= maxCX; cx++) {
                for (int cz = minCZ; cz <= maxCZ; cz++) {
                    net.minecraft.world.level.chunk.LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(cx, cz);
                    if (chunk == null) continue; // 未加载，跳过，与原立方扫描在未加载区取不到BE一致
                    chunkCount++;
                    for (BlockEntity be : chunk.getBlockEntities().values()) {
                        beCount++;
                        if (!(be instanceof JiuhuStationBlockEntity station)) continue;
                        BlockPos p = be.getBlockPos();
                        int dx = Math.abs(p.getX() - centerPos.getX());
                        int dy = Math.abs(p.getY() - centerPos.getY());
                        int dz = Math.abs(p.getZ() - centerPos.getZ());
                        if (dx > range || dz > range || dy > yHalf) continue;
                        if (!station.hasEmptySlot()) continue;
                        double dist = p.distSqr(centerPos);
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = p.immutable();
                        }
                    }
                }
            }
            if (debug) {
                com.icewolf.maidrestaurant.business.core.TaskManager.perfStationChunks += chunkCount;
                com.icewolf.maidrestaurant.business.core.TaskManager.perfStationBEs += beCount;
                com.icewolf.maidrestaurant.business.core.TaskManager.perfStationScanNanos += System.nanoTime() - startNanos;
            }
        } else {
            // 客户端或非ServerLevel兜底：保留原逐坐标立方扫描
            for (BlockPos pos : BlockPos.betweenClosed(centerPos.offset(-range, -yHalf, -range), centerPos.offset(range, yHalf, range))) {
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
