/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity
 *  cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity
 *  cn.breezeth.ordertocook.registry.ModItems
 *  com.mastermarisa.maid_restaurant.utils.MaidStorages
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.ChatFormatting
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.NonNullList
 *  net.minecraft.core.Vec3i
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.network.chat.Component
 *  net.minecraft.network.chat.MutableComponent
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.sounds.SoundEvents
 *  net.minecraft.sounds.SoundSource
 *  net.minecraft.world.entity.decoration.ItemFrame
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.phys.AABB
 *  net.neoforged.neoforge.capabilities.Capabilities
 *  net.neoforged.neoforge.common.util.FakePlayer
 *  net.neoforged.neoforge.common.util.FakePlayerFactory
 *  net.neoforged.neoforge.common.util.LazyOptional
 *  net.neoforged.neoforge.items.IItemHandler
 *  net.neoforged.neoforge.items.ItemHandlerHelper
 *  net.neoforged.neoforge.registries.NeoNeoNeoForgeRegistries
 */
package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import cn.breezeth.ordertocook.registry.ModItems;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import com.icewolf.maidrestaurant.business.core.BusinessManager;
import com.icewolf.maidrestaurant.business.core.ProgressionManager;
import com.icewolf.maidrestaurant.business.core.WorldScanner;
import com.mastermarisa.maid_restaurant.utils.MaidStorages;
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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.decoration.ItemFrame;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

public class OrderBridge {
    // 记录每个打单机的订单刷新时间（key: machinePos.asLong(), value: 刷新时的游戏tick）
    private static final Map<Long, Long> orderRefreshTimes = new HashMap<>();
    
    /**
     * 由Mixin调用，记录订单刷新时间
     */
    public static void onOrderMachineRefreshed(Level level, BlockPos pos) {
        if (level != null && !level.isClientSide) {
            long key = pos.asLong();
            long now = level.getGameTime();
            orderRefreshTimes.put(key, now);
        }
    }
    
    public static void tickOrders(ServerLevel level, BusinessManager manager) {
        List<BlockPos> machines = WorldScanner.scan(level, OrderMachineBlockEntity.class);
        List<BlockPos> counters = WorldScanner.scan(level, TakeoutBoxBlockEntity.class);
        if (machines.isEmpty()) {
            return;
        }
        HashMap<BlockPos, BlockPos> newMapping = new HashMap<BlockPos, BlockPos>();
        for (BlockPos counterPos : counters) {
            BlockPos nearestMachine = null;
            double nearestDist = Double.MAX_VALUE;
            for (BlockPos machinePos : machines) {
                double d = counterPos.distSqr((Vec3i)machinePos);
                if (!(d < nearestDist) || !(d <= 64.0)) continue;
                nearestDist = d;
                nearestMachine = machinePos;
            }
            if (nearestMachine == null) continue;
            newMapping.put(counterPos, nearestMachine);
        }
        manager.getCounterToMachine().clear();
        manager.getCounterToMachine().putAll(newMapping);
        HashSet<BlockPos> currentActivated = new HashSet<BlockPos>();
        for (BlockPos machinePos : machines) {
            try {
                if (!OrderBridge.isActivated(level, machinePos)) continue;
                currentActivated.add(machinePos);
            }
            catch (Throwable t) {
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
        // 全局开关关闭，所有打单机都不自动接单
        if (!BusinessConfig.autoAccept) {
            return;
        }
        for (BlockPos machinePos : machines) {
            if (!currentActivated.contains(machinePos)) continue;
            // 检查排班表的自动接单开关（没有排班表默认开启，有排班表则按排班表设置）
            if (!MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_ACCEPT)) {
                continue;
            }
            try {
                OrderBridge.processMachine(level, machinePos, counters, manager);
            }
            catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("Error processing order machine at {}", machinePos, t);
            }
        }
    }

    private static boolean processMachine(ServerLevel level, BlockPos machinePos, List<BlockPos> counters, BusinessManager manager) {
        BlockPos counterPos;
        BlockEntity be = level.getBlockEntity(machinePos);
        if (!(be instanceof OrderMachineBlockEntity)) {
            return false;
        }
        if (!OrderBridge.isActivated(level, machinePos)) {
            return false;
        }
        if (!ProgressionManager.isAutoOrderUnlocked(level, machinePos)) {
            return true;
        }
        long now = level.getGameTime();
        // 基于订单刷新时间的延迟：只有订单刷新后超过acceptDelay才接单
        Long refreshTime = orderRefreshTimes.get(machinePos.asLong());
        if (refreshTime == null) {
            // 还没有检测到订单刷新，不接单
            return true;
        }
        long elapsed = now - refreshTime;
        if (elapsed < (long)BusinessConfig.acceptDelay) {
            return true;
        }
        IItemHandler machineInv = OrderBridge.getItemHandler(be);
        if (machineInv == null) {
            return true;
        }
        ArrayList<OrderEntry> orders = new ArrayList<OrderEntry>();
        for (int i = 0; i < machineInv.getSlots(); ++i) {
            CompoundTag nbt;
            ItemStack stack = machineInv.getStackInSlot(i);
            if (stack.isEmpty() || !stack.is((Item)OtcCompat.ORDER()) || (nbt = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(stack)) == null) continue;
            orders.add(new OrderEntry(i, stack, nbt));
        }
        if (orders.isEmpty()) {
            return true;
        }
        long activeCount = manager.getActiveOrders().values().stream().filter(o -> o.machinePos.equals(machinePos)).count();
        if (activeCount >= (long)BusinessConfig.maxPendingOrders) {
            return true;
        }
        ArrayList<OrderEntry> candidates = new ArrayList<OrderEntry>();
        for (OrderEntry orderEntry : orders) {
            boolean bl = orderEntry.nbt.getBoolean("Delivery");
            if (bl && !BusinessConfig.acceptDelivery || !orderEntry.nbt.contains("FoodList")) continue;
            candidates.add(orderEntry);
        }
        if (candidates.isEmpty()) {
            return true;
        }
        // 自动接单时检查操作台和冰箱里有没有现成的成品食物
        // 有就接（可以直接打包送餐），没有就不接（避免订单卡在操作台）
        ArrayList<OrderEntry> readyOrders = new ArrayList<OrderEntry>();
        for (OrderEntry orderEntry : candidates) {
            if (OrderBridge.hasReadyFood(level, machinePos, orderEntry.nbt)) {
                readyOrders.add(orderEntry);
            }
        }
        if (readyOrders.isEmpty()) {
            return true;
        }
        candidates = readyOrders;
        if (BusinessConfig.priorityMode == BusinessConfig.PriorityMode.PRESTIGE) {
            candidates.sort((a, b) -> Integer.compare(b.nbt.getInt("Prestige"), a.nbt.getInt("Prestige")));
        }
        if ((counterPos = OrderBridge.findNearestFreeCounter(level, machinePos, counters, manager)) == null) {
            return true;
        }
        OrderEntry orderEntry = (OrderEntry)candidates.get(0);
        OrderBridge.transferOrderToCounter(level, machineInv, orderEntry, counterPos, machinePos);
        manager.getOrderCooldowns().put(machinePos, now);
        manager.getCounterToMachine().put(counterPos, machinePos);
        return true;
    }

    private static boolean canFulfillOrder(ServerLevel level, BlockPos machinePos, CompoundTag orderNbt) {
        if (!orderNbt.contains("FoodList")) {
            return false;
        }
        CompoundTag foodList = orderNbt.getCompound("FoodList");
        int range = BusinessConfig.searchRange;
        HashMap<String, Integer> available = new HashMap<String, Integer>();
        for (BlockPos check : BlockPos.betweenClosed((BlockPos)machinePos.offset(-range, -range / 2, -range), (BlockPos)machinePos.offset(range, range / 2, range))) {
            try {
                IItemHandler handler = MaidStorages.tryGetHandler((Level)level, (BlockPos)check);
                if (handler == null) continue;
                for (int i = 0; i < handler.getSlots(); ++i) {
                    ItemStack stack = handler.getStackInSlot(i);
                    if (stack.isEmpty()) continue;
                    String id = Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())).toString();
                    available.merge(id, stack.getCount(), Integer::sum);
                }
            }
            catch (Throwable handler) {
            }
        }
        for (String key : foodList.getAllKeys()) {
            int required = foodList.getInt(key);
            if (available.getOrDefault(key, 0) >= required) continue;
            return false;
        }
        return true;
    }
    
    /**
     * 检查操作台和冰箱里有没有订单所需的现成成品食物
     * 用于自动接单判断：有现成食物就接（可以直接打包），没有就不接
     */
    private static boolean hasReadyFood(ServerLevel level, BlockPos machinePos, CompoundTag orderNbt) {
        if (!orderNbt.contains("FoodList")) {
            return false;
        }
        CompoundTag foodList = orderNbt.getCompound("FoodList");
        int range = BusinessConfig.searchRange;
        HashMap<String, Integer> available = new HashMap<String, Integer>();
        int containerCount = 0;
        
        // 只检测操作台（TakeoutBoxBlockEntity）和冰箱（RefrigeratorBlockEntity），其他容器都不检测
        for (BlockPos check : BlockPos.betweenClosed(
            (BlockPos)machinePos.offset(-range, -range, -range), 
            (BlockPos)machinePos.offset(range, range, range))) {
            try {
                BlockEntity be = level.getBlockEntity(check);
                if (be == null) continue;
                
                // 只处理操作台和冰箱
                boolean isTarget = be instanceof TakeoutBoxBlockEntity || 
                                   be.getClass().getSimpleName().equals("RefrigeratorBlockEntity");
                if (!isTarget) continue;
                
                // 操作台实现了Container接口，直接处理
                if (be instanceof net.minecraft.world.Container) {
                    net.minecraft.world.Container container = (net.minecraft.world.Container)be;
                    containerCount++;
                    for (int i = 0; i < container.getContainerSize(); ++i) {
                        ItemStack stack = container.getItem(i);
                        if (stack.isEmpty()) continue;
                        String id = Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())).toString();
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
                        if (upperInv instanceof net.minecraft.core.NonNullList) {
                            @SuppressWarnings("unchecked")
                            net.minecraft.core.NonNullList<ItemStack> items = (net.minecraft.core.NonNullList<ItemStack>) upperInv;
                            for (ItemStack stack : items) {
                                if (stack.isEmpty()) continue;
                                String id = Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())).toString();
                                available.merge(id, stack.getCount(), Integer::sum);
                            }
                        }
                        if (lowerInv instanceof net.minecraft.core.NonNullList) {
                            @SuppressWarnings("unchecked")
                            net.minecraft.core.NonNullList<ItemStack> items = (net.minecraft.core.NonNullList<ItemStack>) lowerInv;
                            for (ItemStack stack : items) {
                                if (stack.isEmpty()) continue;
                                String id = Objects.requireNonNull(net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem())).toString();
                                available.merge(id, stack.getCount(), Integer::sum);
                            }
                        }
                        containerCount++;
                    } catch (Throwable t) {
                        // 反射失败，静默跳过
                    }
                }
            }
            catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("自动接单: 检查容器 {} 时出错: {}", check, t.toString());
            }
        }
        
        
        // 检查是否有足够的成品食物
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
            return "\u5168\u90e8\u529f\u80fd\u5df2\u5f00\u542f";
        }
        StringBuilder sb = new StringBuilder("\u5df2\u89e3\u9501: ");
        if (ProgressionManager.isDeliveryUnlocked(level, pos)) {
            sb.append("\u9001\u9910");
        }
        if (ProgressionManager.isCookAndPrepUnlocked(level, pos)) {
            sb.append(" \u70f9\u996a/\u5907\u83dc");
        }
        if (ProgressionManager.isDishwashingUnlocked(level, pos)) {
            sb.append(" \u6d17\u7897");
        }
        if (ProgressionManager.isAutoOrderUnlocked(level, pos)) {
            sb.append(" \u81ea\u52a8\u63a5\u5355");
        }
        return sb.toString();
    }

    public static void notifyActivation(ServerLevel level, BlockPos pos, boolean activated) {
        MutableComponent msg;
        if (activated) {
            int rl = ProgressionManager.getRestaurantLevel(level, pos);
            String unlocked = OrderBridge.getUnlockedFeatures(level, pos);
            msg = Component.literal((String)"[\u5973\u4ec6\u9910\u5385\uff1a\u7ecf\u8425] ").withStyle(ChatFormatting.GOLD).append((Component)Component.literal((String)("\u81ea\u52a8\u5316\u5df2\u542f\u52a8\uff0c\u573a\u9986\u7b49\u7ea7 " + rl + "\u3002" + unlocked)).withStyle(ChatFormatting.GREEN));
            level.playSound(null, pos, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.BLOCKS, 0.5f, 1.2f);
            level.playSound(null, pos, SoundEvents.VILLAGER_YES, SoundSource.BLOCKS, 0.3f, 1.0f);
        } else {
            msg = Component.literal((String)"[\u5973\u4ec6\u9910\u5385\uff1a\u7ecf\u8425] ").withStyle(ChatFormatting.GOLD).append((Component)Component.literal((String)"\u81ea\u52a8\u5316\u5df2\u505c\u6b62\uff08\u672a\u7ed1\u5b9a\u6392\u73ed\u8868\u6216\u6392\u73ed\u8868\u672a\u542f\u7528\u81ea\u52a8\u5316\uff09").withStyle(ChatFormatting.GRAY));
            level.playSound(null, pos, SoundEvents.VILLAGER_NO, SoundSource.BLOCKS, 0.3f, 0.8f);
        }
        for (ServerPlayer player : level.getPlayers(p -> p.distanceToSqr((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5) <= 64.0)) {
            player.displayClientMessage((Component)msg, false);
        }
    }

    private static BlockPos findNearestFreeCounter(ServerLevel level, BlockPos machinePos, List<BlockPos> counters, BusinessManager manager) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos counterPos : counters) {
            double dist;
            IItemHandler inv;
            BlockEntity be = level.getBlockEntity(counterPos);
            if (!(be instanceof TakeoutBoxBlockEntity) || (inv = OrderBridge.getItemHandler(be)) == null || !inv.getStackInSlot(0).isEmpty() || !level.getBlockState(counterPos.above()).isAir() || manager.getActiveOrders().containsKey(counterPos) || !((dist = counterPos.distSqr((Vec3i)machinePos)) < nearestDist)) continue;
            nearestDist = dist;
            nearest = counterPos.immutable();
        }
        return nearest;
    }

    private static void transferOrderToCounter(ServerLevel level, IItemHandler machineInv, OrderEntry entry, BlockPos counterPos, BlockPos machinePos) {
        ItemStack orderStack = machineInv.extractItem(entry.slot, 1, false);
        if (orderStack.isEmpty()) {
            return;
        }
        BlockEntity counterBe = level.getBlockEntity(counterPos);
        if (counterBe == null) {
            return;
        }
        IItemHandler counterInv = OrderBridge.getItemHandler(counterBe);
        if (counterInv != null) {
            ItemHandlerHelper.insertItem((IItemHandler)counterInv, (ItemStack)orderStack, (boolean)false);
        }
        OrderBridge.spawnCustomerForOrder(level, machinePos, entry.nbt);
        level.updateNeighbourForOutputSignal(counterPos, counterBe.getBlockState().getBlock());
    }

    private static void spawnCustomerForOrder(ServerLevel level, BlockPos machinePos, CompoundTag nbt) {
        try {
            long expirySys;
            boolean delivery = nbt.getBoolean("Delivery");
            if (delivery) {
                return;
            }
            String orderId = nbt.getString("OrderId");
            String customerName = nbt.getString("CustomerName");
            long expiryTick = nbt.contains("ExpiryTick") ? nbt.getLong("ExpiryTick") : -1L;
            long l = expirySys = nbt.contains("ExpiryTime") ? nbt.getLong("ExpiryTime") : -1L;
            if (orderId.isEmpty()) {
                MaidRestaurantBusiness.LOGGER.warn("\u8ba2\u5355ID\u4e3a\u7a7a\uff0c\u65e0\u6cd5\u751f\u6210\u987e\u5ba2");
                return;
            }
            FakePlayer fakePlayer = FakePlayerFactory.get((ServerLevel)level, (GameProfile)new GameProfile(UUID.randomUUID(), "MaidAutoOrder"));
            fakePlayer.moveTo((double)machinePos.getX() + 0.5, (double)machinePos.getY(), (double)machinePos.getZ() + 0.5, 0.0f, 0.0f);
            Class<?> npcManagerClass = Class.forName("cn.breezeth.ordertocook.core.NormalOrderNpcManager");
            Method spawnMethod = npcManagerClass.getMethod("spawn", ServerLevel.class, Player.class, BlockPos.class, String.class, String.class, Long.TYPE, Long.TYPE, CompoundTag.class);
            spawnMethod.invoke(null, level, fakePlayer, machinePos, orderId, customerName, expiryTick, expirySys, nbt);
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("\u81ea\u52a8\u63a5\u5355\u751f\u6210\u987e\u5ba2\u5931\u8d25: {}", t.getMessage(), t);
        }
    }

    public static IItemHandler getItemHandler(BlockEntity be) {
        if (be == null) {
            return null;
        }
        // 特殊处理：TakeoutBoxBlockEntity的capability可能只暴露物品槽(1-12)，不包含订单槽(0)
        // 直接通过反射访问inventory字段，确保能读取到订单槽
        // 同时支持Forge版本(NonNullList)和Fabric版本(DefaultedList)
        if (be instanceof TakeoutBoxBlockEntity) {
            try {
                Field inventoryField = TakeoutBoxBlockEntity.class.getDeclaredField("inventory");
                inventoryField.setAccessible(true);
                Object inventory = inventoryField.get(be);
                if (inventory instanceof List) {
                    return new ItemStackHandlerAdapter(be, (List<ItemStack>)inventory);
                }
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("OrderBridge: 访问TakeoutBoxBlockEntity.inventory失败，回退到capability", t);
            }
        }
        try {
            IItemHandler cap = net.neoforged.neoforge.capabilities.Capabilities.ItemHandler.BLOCK.getCapability(be.getLevel(), be.getBlockPos(), be.getBlockState(), be, null);
            if (cap != null) {
                return cap;
            }
        }
        catch (Throwable cap) {
            // empty catch block
        }
        if (be instanceof IItemHandler) {
            IItemHandler handler = (IItemHandler)be;
            return handler;
        }
        try {
            Method method = be.getClass().getMethod("getItems", new Class[0]);
            Object result = method.invoke(be, new Object[0]);
            if (result instanceof NonNullList) {
                NonNullList list = (NonNullList)result;
                return new ItemStackHandlerAdapter(be, list);
            }
        }
        catch (Exception exception) {
            // empty catch block
        }
        return null;
    }

    private record OrderEntry(int slot, ItemStack stack, CompoundTag nbt) {
    }

    private static class ItemStackHandlerAdapter
    implements IItemHandler {
        private final BlockEntity be;
        private final List<ItemStack> items;

        ItemStackHandlerAdapter(BlockEntity be, List<ItemStack> items) {
            this.be = be;
            this.items = items;
        }

        public int getSlots() {
            return this.items.size();
        }

        public ItemStack getStackInSlot(int slot) {
            return (ItemStack)this.items.get(slot);
        }

        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            ItemStack existing = (ItemStack)this.items.get(slot);
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

        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack existing = (ItemStack)this.items.get(slot);
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

        public int getSlotLimit(int slot) {
            return 64;
        }

        public boolean isItemValid(int slot, ItemStack stack) {
            return true;
        }
    }
}
