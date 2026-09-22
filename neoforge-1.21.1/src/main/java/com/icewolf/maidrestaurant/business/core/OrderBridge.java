package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 打单机 / 操作台桥接。
 *
 * <p>重铸自动接单后，本类的 {@link #tickOrders} 只负责维护打单机激活态与“操作台→打单机”归属映射；
 * 真正的取单入台由厨师女仆真实寻路完成（见 {@link OrderFetchBridge}，由 BusinessManager 每 10tick 调度），
 * 到店顾客接待见 {@link WalkInGreetBridge}。</p>
 */
public class OrderBridge {
    // 记录每个打单机的订单刷新时间（key: machinePos.asLong(), value: 刷新时的游戏tick）
    private static final Map<Long, Long> orderRefreshTimes = new HashMap<>();

    // 以打单机为中心局部圆扫附近操作台，替代全局扫描。
    // 半径/垂直与 OTC 的 ModConstants 一致（水平24圆形、垂直±8），按机器缓存1秒，
    // 运行时新放/拆除操作台最迟1秒被识别；key = 维度@打单机坐标。
    private static final Map<String, List<BlockPos>> counterScanCache = new HashMap<>();
    private static final Map<String, Long> counterScanTick = new HashMap<>();
    private static final long COUNTER_SCAN_INTERVAL = 20L;
    private static final int COUNTER_SCAN_RADIUS = 24;
    private static final int COUNTER_SCAN_VERTICAL = 8;

    /**
     * 由 Mixin 调用，记录订单刷新时间；同时令取单入台的成品候选缓存立即失效。
     */
    public static void onOrderMachineRefreshed(Level level, BlockPos pos) {
        if (level != null && !level.isClientSide) {
            long key = pos.asLong();
            long now = level.getGameTime();
            orderRefreshTimes.put(key, now);
            if (level instanceof ServerLevel serverLevel) {
                OrderFetchBridge.invalidate(serverLevel, pos);
            }
        }
    }

    public static void tickOrders(ServerLevel level, BusinessManager manager) {
        List<BlockPos> machines = WorldScanner.scan(level, OrderMachineBlockEntity.class);
        if (machines.isEmpty()) {
            return;
        }
        // 操作台不再全局扫描，改为对每台打单机做局部圆扫（半径24、垂直±8、缓存1秒）。
        // 同一操作台落在多台机器范围内时归最近的一台（保持“最近机器”语义）。
        String dimPrefix = level.dimension().location().toString() + "@";
        HashSet<String> liveScanKeys = new HashSet<String>();
        HashMap<BlockPos, BlockPos> newMapping = new HashMap<BlockPos, BlockPos>();
        HashMap<BlockPos, Double> nearestDistByCounter = new HashMap<BlockPos, Double>();
        for (BlockPos machinePos : machines) {
            liveScanKeys.add(dimPrefix + machinePos.asLong());
            List<BlockPos> localCounters = OrderBridge.scanCountersAround(level, machinePos);
            for (BlockPos counterPos : localCounters) {
                double d = counterPos.distSqr((Vec3i) machinePos);
                Double prev = nearestDistByCounter.get(counterPos);
                if (prev == null || d < prev) {
                    nearestDistByCounter.put(counterPos, d);
                    newMapping.put(counterPos, machinePos);
                }
            }
        }
        // 清理已拆除打单机遗留的扫描缓存（仅本维度）
        counterScanCache.keySet().removeIf(k -> k.startsWith(dimPrefix) && !liveScanKeys.contains(k));
        counterScanTick.keySet().removeIf(k -> k.startsWith(dimPrefix) && !liveScanKeys.contains(k));
        manager.getCounterToMachine().clear();
        manager.getCounterToMachine().putAll(newMapping);
        HashSet<BlockPos> currentActivated = new HashSet<BlockPos>();
        for (BlockPos machinePos : machines) {
            try {
                if (!OrderBridge.isActivated(level, machinePos)) continue;
                currentActivated.add(machinePos);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("Error checking activation at {}", machinePos, t);
            }
        }
        for (BlockPos pos : machines) {
            boolean wasActive = manager.getActivatedMachines().contains(pos);
            boolean isActive = currentActivated.contains(pos);
            if (isActive && !wasActive) {
                OrderBridge.notifyActivation(level, pos, true);
                manager.getActivatedMachines().add(pos);
                continue;
            }
            if (isActive || !wasActive) continue;
            OrderBridge.notifyActivation(level, pos, false);
            manager.getActivatedMachines().remove(pos);
        }
        manager.getActivatedMachines().retainAll(currentActivated);
        // 自动接单（厨师取单入台）已改为厨师真实寻路完成，见 OrderFetchBridge（由 BusinessManager 每 10tick 调度）。
        // tickOrders 现在只负责维护打单机激活态与“操作台→打单机”归属映射，供其它桥复用。
    }

    /**
     * 打单机内订单是否已过“刷新延迟”（acceptDelay）。供 OrderFetchBridge 判断打单机来源候选。
     */
    static boolean isPastAcceptDelay(ServerLevel level, BlockPos machinePos) {
        Long refreshTime = orderRefreshTimes.get(machinePos.asLong());
        if (refreshTime == null) {
            return false;
        }
        return level.getGameTime() - refreshTime >= (long) BusinessConfig.acceptDelay;
    }

    /**
     * 检查操作台和冰箱里有没有订单所需的现成成品食物。
     * 用于取单入台判断：有现成食物才让厨师把订单放进操作台（可以直接打包），否则等待。
     * 使用 TaskManager 中心化缓存（每10tick更新）的操作台 / 冰箱位置，避免重复扫描。
     */
    static boolean hasReadyFood(ServerLevel level, BlockPos machinePos, CompoundTag orderNbt) {
        if (!orderNbt.contains("FoodList")) {
            return false;
        }
        CompoundTag foodList = orderNbt.getCompound("FoodList");
        HashMap<String, Integer> available = new HashMap<String, Integer>();

        for (BlockPos check : TaskManager.getInstance().getCachedCountersAndFridges(machinePos)) {
            try {
                BlockEntity be = level.getBlockEntity(check);
                if (be == null) continue;

                // 只处理操作台和冰箱
                boolean isTarget = be instanceof TakeoutBoxBlockEntity
                        || be.getClass().getSimpleName().equals("RefrigeratorBlockEntity");
                if (!isTarget) continue;

                if (be instanceof net.minecraft.world.Container) {
                    net.minecraft.world.Container container = (net.minecraft.world.Container) be;
                    for (int i = 0; i < container.getContainerSize(); ++i) {
                        ItemStack stack = container.getItem(i);
                        if (stack.isEmpty()) continue;
                        String id = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(stack.getItem())).toString();
                        available.merge(id, stack.getCount(), Integer::sum);
                    }
                } else if (be.getClass().getSimpleName().equals("RefrigeratorBlockEntity")) {
                    // 冰箱没有直接实现Container接口，用反射访问upperInventory和lowerInventory
                    try {
                        java.lang.reflect.Field upperField = be.getClass().getDeclaredField("upperInventory");
                        java.lang.reflect.Field lowerField = be.getClass().getDeclaredField("lowerInventory");
                        upperField.setAccessible(true);
                        lowerField.setAccessible(true);
                        Object upperInv = upperField.get(be);
                        Object lowerInv = lowerField.get(be);
                        if (upperInv instanceof NonNullList) {
                            @SuppressWarnings("unchecked")
                            NonNullList<ItemStack> items = (NonNullList<ItemStack>) upperInv;
                            for (ItemStack stack : items) {
                                if (stack.isEmpty()) continue;
                                String id = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(stack.getItem())).toString();
                                available.merge(id, stack.getCount(), Integer::sum);
                            }
                        }
                        if (lowerInv instanceof NonNullList) {
                            @SuppressWarnings("unchecked")
                            NonNullList<ItemStack> items = (NonNullList<ItemStack>) lowerInv;
                            for (ItemStack stack : items) {
                                if (stack.isEmpty()) continue;
                                String id = Objects.requireNonNull(BuiltInRegistries.ITEM.getKey(stack.getItem())).toString();
                                available.merge(id, stack.getCount(), Integer::sum);
                            }
                        }
                    } catch (Throwable t) {
                        // 反射失败，静默跳过
                    }
                }
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("自动接单: 检查容器 {} 时出错: {}", check, t.toString());
            }
        }

        for (String key : foodList.getAllKeys()) {
            int required = foodList.getInt(key);
            int have = available.getOrDefault(key, 0);
            if (have < required) {
                return false;
            }
        }
        return true;
    }

    public static boolean isActivated(ServerLevel level, BlockPos pos) {
        // 使用集中缓存，O(1)查询，避免每次遍历
        return ActivationCache.isActivated(level, pos);
    }

    private static String getUnlockedFeatures(ServerLevel level, BlockPos pos) {
        if (!BusinessConfig.levelBasedProgression) {
            return "全部功能已开启";
        }
        StringBuilder sb = new StringBuilder("已解锁: ");
        if (ProgressionManager.isDeliveryUnlocked(level, pos)) {
            sb.append("送餐");
        }
        if (ProgressionManager.isCookAndPrepUnlocked(level, pos)) {
            sb.append(" 烹饪/备菜");
        }
        if (ProgressionManager.isDishwashingUnlocked(level, pos)) {
            sb.append(" 洗碗");
        }
        if (ProgressionManager.isAutoOrderUnlocked(level, pos)) {
            sb.append(" 自动接单");
        }
        return sb.toString();
    }

    public static void notifyActivation(ServerLevel level, BlockPos pos, boolean activated) {
        MutableComponent msg;
        if (activated) {
            int rl = ProgressionManager.getRestaurantLevel(level, pos);
            String unlocked = OrderBridge.getUnlockedFeatures(level, pos);
            msg = Component.literal("[女仆餐厅：营业] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("自动化已启动，场馆等级 " + rl + "。" + unlocked).withStyle(ChatFormatting.GREEN));
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.2f);
            level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.3f, 1.0f);
        } else {
            msg = Component.literal("[女仆餐厅：营业] ").withStyle(ChatFormatting.GOLD)
                    .append(Component.literal("自动化已停止（未绑定排班表或排班表未启用自动化）").withStyle(ChatFormatting.GRAY));
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.3f, 0.8f);
        }
        for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr(
                (double) pos.getX() + 0.5, (double) pos.getY() + 0.5, (double) pos.getZ() + 0.5) <= 64.0)) {
            player.displayClientMessage(msg, false);
        }
    }

    /**
     * 以打单机为中心局部圆扫附近操作台（水平半径24圆形、垂直±8），按机器缓存1秒。
     * 运行时新放/拆除操作台最迟1秒被识别。
     */
    static List<BlockPos> scanCountersAround(ServerLevel level, BlockPos machinePos) {
        String key = level.dimension().location().toString() + "@" + machinePos.asLong();
        long now = level.getGameTime();
        List<BlockPos> cached = counterScanCache.get(key);
        Long last = counterScanTick.get(key);
        if (cached != null && last != null && now - last < COUNTER_SCAN_INTERVAL) {
            return cached;
        }
        List<BlockPos> found = new ArrayList<BlockPos>();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int cx = machinePos.getX();
        int cy = machinePos.getY();
        int cz = machinePos.getZ();
        int minY = level.getMinBuildHeight();
        int maxY = level.getMaxBuildHeight();
        int radiusSqr = COUNTER_SCAN_RADIUS * COUNTER_SCAN_RADIUS;
        for (int dx = -COUNTER_SCAN_RADIUS; dx <= COUNTER_SCAN_RADIUS; dx++) {
            for (int dz = -COUNTER_SCAN_RADIUS; dz <= COUNTER_SCAN_RADIUS; dz++) {
                if (dx * dx + dz * dz > radiusSqr) continue;
                for (int dy = -COUNTER_SCAN_VERTICAL; dy <= COUNTER_SCAN_VERTICAL; dy++) {
                    int y = cy + dy;
                    if (y < minY || y >= maxY) continue;
                    m.set(cx + dx, y, cz + dz);
                    if (!level.getBlockState(m).hasBlockEntity()) continue;
                    if (level.getBlockEntity(m) instanceof TakeoutBoxBlockEntity) {
                        found.add(m.immutable());
                    }
                }
            }
        }
        counterScanCache.put(key, found);
        counterScanTick.put(key, now);
        return found;
    }

    static BlockPos findNearestFreeCounter(ServerLevel level, BlockPos machinePos, List<BlockPos> counters, BusinessManager manager) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos counterPos : counters) {
            double dist;
            IItemHandler inv;
            BlockEntity be = level.getBlockEntity(counterPos);
            if (!(be instanceof TakeoutBoxBlockEntity)
                    || (inv = OrderBridge.getItemHandler(be)) == null
                    || !inv.getStackInSlot(0).isEmpty()
                    || !level.getBlockState(counterPos.above()).isAir()
                    || manager.getActiveOrders().containsKey(counterPos)
                    || !((dist = counterPos.distSqr((Vec3i) machinePos)) < nearestDist)) continue;
            nearestDist = dist;
            nearest = counterPos.immutable();
        }
        return nearest;
    }

    /**
     * 操作台当前是否空闲、可直接放入一张订单：是 TakeoutBox、订单槽（槽0）为空、
     * 台上（above）是空气、且没有在制活跃订单。侍者直接放台与厨师取单入台共用同一判定口径。
     */
    public static boolean isCounterFree(ServerLevel level, BlockPos counter, BusinessManager manager) {
        if (counter == null || !(level.getBlockEntity(counter) instanceof TakeoutBoxBlockEntity)) {
            return false;
        }
        IItemHandler inv = OrderBridge.getItemHandler(level.getBlockEntity(counter));
        if (inv == null || !inv.getStackInSlot(0).isEmpty()) {
            return false;
        }
        if (!level.getBlockState(counter.above()).isAir()) {
            return false;
        }
        return manager == null || !manager.getActiveOrders().containsKey(counter);
    }

    /**
     * 反射操作台 inventory，把订单原子写入订单槽（槽0）；仅当槽0为空时写入成功。
     * 侍者接待直接放台、厨师取单入台共用此入口。写入失败（非操作台 / 槽0已占 / 反射失败）返回 false，调用方负责回滚。
     */
    public static boolean putOrderIntoSlot0(ServerLevel level, BlockPos counter, ItemStack order) {
        if (level == null || counter == null || order == null || order.isEmpty()) {
            return false;
        }
        if (!(level.getBlockEntity(counter) instanceof TakeoutBoxBlockEntity)) {
            return false;
        }
        try {
            Field f = TakeoutBoxBlockEntity.class.getDeclaredField("inventory");
            f.setAccessible(true);
            Object invObj = f.get(level.getBlockEntity(counter));
            if (invObj instanceof List<?> raw) {
                @SuppressWarnings("unchecked")
                List<ItemStack> items = (List<ItemStack>) raw;
                if (!items.isEmpty() && items.get(0).isEmpty()) {
                    items.set(0, order.copy());
                    BlockEntity be = level.getBlockEntity(counter);
                    be.setChanged();
                    level.updateNeighbourForOutputSignal(counter, be.getBlockState().getBlock());
                    return true;
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("OrderBridge: 写入操作台槽0失败 counter={}", counter, t);
        }
        return false;
    }

    /** 堂食单成功放入操作台后生成对应顾客；外卖单不生成顾客。 */
    static void spawnCustomerForOrder(ServerLevel level, BlockPos machinePos, CompoundTag nbt) {
        try {
            long expirySys;
            boolean delivery = nbt.getBoolean("Delivery");
            if (delivery) {
                return;
            }
            String orderId = nbt.getString("OrderId");
            String customerName = nbt.getString("CustomerName");
            long expiryTick = nbt.contains("ExpiryTick") ? nbt.getLong("ExpiryTick") : -1L;
            expirySys = nbt.contains("ExpiryTime") ? nbt.getLong("ExpiryTime") : -1L;
            if (orderId.isEmpty()) {
                MaidRestaurantBusiness.LOGGER.warn("订单ID为空，无法生成顾客");
                return;
            }
            FakePlayer fakePlayer = FakePlayerFactory.get(level, new GameProfile(UUID.randomUUID(), "MaidAutoOrder"));
            fakePlayer.moveTo((double) machinePos.getX() + 0.5, (double) machinePos.getY(), (double) machinePos.getZ() + 0.5, 0.0f, 0.0f);
            Class<?> npcManagerClass = Class.forName("cn.breezeth.ordertocook.core.NormalOrderNpcManager");
            Method spawnMethod = npcManagerClass.getMethod("spawn", ServerLevel.class, Player.class, BlockPos.class, String.class, String.class, Long.TYPE, Long.TYPE, CompoundTag.class);
            spawnMethod.invoke(null, level, fakePlayer, machinePos, orderId, customerName, expiryTick, expirySys, nbt);
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("自动接单生成顾客失败: {}", t.getMessage(), t);
        }
    }

    public static IItemHandler getItemHandler(BlockEntity be) {
        if (be == null) {
            return null;
        }
        // 特殊处理：TakeoutBoxBlockEntity的capability可能只暴露物品槽(1-12)，不包含订单槽(0)
        // 直接通过反射访问inventory字段，确保能读取到订单槽
        if (be instanceof TakeoutBoxBlockEntity) {
            try {
                Field inventoryField = TakeoutBoxBlockEntity.class.getDeclaredField("inventory");
                inventoryField.setAccessible(true);
                Object inventory = inventoryField.get(be);
                if (inventory instanceof List) {
                    return new ItemStackHandlerAdapter(be, (List<ItemStack>) inventory);
                }
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("OrderBridge: 访问TakeoutBoxBlockEntity.inventory失败，回退到capability", t);
            }
        }
        try {
            IItemHandler cap = Capabilities.ItemHandler.BLOCK.getCapability(be.getLevel(), be.getBlockPos(), be.getBlockState(), be, null);
            if (cap != null) {
                return cap;
            }
        } catch (Throwable t) {
            // 无 capability，回退
        }
        if (be instanceof IItemHandler) {
            return (IItemHandler) be;
        }
        try {
            Method method = be.getClass().getMethod("getItems");
            Object result = method.invoke(be);
            if (result instanceof NonNullList) {
                return new ItemStackHandlerAdapter(be, (NonNullList<ItemStack>) result);
            }
        } catch (Exception exception) {
            // 忽略
        }
        return null;
    }

    /** 把 OTC 方块内部的 List 物品栏适配成 NeoForge IItemHandler，供抽单 / 放单使用。 */
    private static class ItemStackHandlerAdapter implements IItemHandler {
        private final BlockEntity be;
        private final List<ItemStack> items;

        ItemStackHandlerAdapter(BlockEntity be, List<ItemStack> items) {
            this.be = be;
            this.items = items;
        }

        @Override
        public int getSlots() {
            return this.items.size();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return this.items.get(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = this.items.get(slot);
            if (existing.isEmpty()) {
                if (!simulate) {
                    this.items.set(slot, stack.copy());
                    this.be.setChanged();
                }
                return ItemStack.EMPTY;
            }
            if (ItemStack.isSameItemSameComponents(existing, stack)) {
                int max = Math.min(existing.getMaxStackSize(), this.getSlotLimit(slot));
                int canAdd = max - existing.getCount();
                int toAdd = Math.min(canAdd, stack.getCount());
                if (!simulate && toAdd > 0) {
                    existing.grow(toAdd);
                    this.be.setChanged();
                }
                return stack.getCount() == toAdd ? ItemStack.EMPTY : stack.copyWithCount(stack.getCount() - toAdd);
            }
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack existing = this.items.get(slot);
            if (existing.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int toExtract = Math.min(amount, existing.getCount());
            ItemStack result = existing.copyWithCount(toExtract);
            if (!simulate) {
                existing.shrink(toExtract);
                this.be.setChanged();
            }
            return result;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
