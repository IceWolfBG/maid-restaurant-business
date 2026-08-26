/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity
 *  cn.breezeth.ordertocook.registry.ModItems
 *  com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid
 *  com.mastermarisa.maid_restaurant.api.request.IRequest
 *  com.mastermarisa.maid_restaurant.request.CookRequest
 *  com.mastermarisa.maid_restaurant.utils.CookTasks
 *  com.mastermarisa.maid_restaurant.utils.MaidStorages
 *  com.mastermarisa.maid_restaurant.utils.RequestManager
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Vec3i
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.item.crafting.Ingredient
 *  net.minecraft.world.item.crafting.Recipe
 *  net.minecraft.world.item.crafting.RecipeType
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.phys.AABB
 *  net.minecraftforge.items.IItemHandler
 *  net.minecraftforge.items.ItemHandlerHelper
 *  net.minecraftforge.registries.ForgeRegistries
 */
package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import cn.breezeth.ordertocook.registry.ModItems;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.core.ActiveOrder;
import com.icewolf.maidrestaurant.business.core.BusinessManager;
import com.icewolf.maidrestaurant.business.core.MaidUtils;
import com.icewolf.maidrestaurant.business.core.OrderBridge;
import com.icewolf.maidrestaurant.business.core.ProgressionManager;
import com.mastermarisa.maid_restaurant.api.request.IRequest;
import com.mastermarisa.maid_restaurant.event.MaidTracker;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.request.CookRequestHandler;
import com.mastermarisa.maid_restaurant.request.world.WorldCookRequestPool;
import com.mastermarisa.maid_restaurant.utils.CookTasks;
import com.mastermarisa.maid_restaurant.utils.MaidStorages;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;

public class CookingBridge {
    private static final int STATE_GO_TO_CONTAINER = 0;
    private static final int STATE_EXTRACT = 1;
    private static final int STATE_GO_TO_COUNTER = 2;
    private static final int STATE_INSERT = 3;
    private static final Map<BlockPos, PrepTask> prepTasks = new HashMap<BlockPos, PrepTask>();
    public static final Set<UUID> businessCookMaids = ConcurrentHashMap.newKeySet();
    public static final Set<UUID> pendingServeRequest = ConcurrentHashMap.newKeySet();
    private static final Map<UUID, Long> pendingRemoval = new ConcurrentHashMap<UUID, Long>();
    private static final long REMOVAL_DELAY_TICKS = 100L;
    // 本tick已发布的烹饪任务缓存（防止同一个tick内重复发布同一个食物的任务）
    // key: counterPos.asLong() + "|" + itemId
    // value: 已发布的总烹饪次数
    private static final Map<String, Integer> publishedThisTick = new HashMap<>();
    private static long lastPublishedTick = -1;

    public static void tickCooking(ServerLevel level, BusinessManager manager) {
        // 清空本tick已发布任务缓存（每个tick只清空一次）
        long currentTick = level.getGameTime();
        if (currentTick != lastPublishedTick) {
            publishedThisTick.clear();
            lastPublishedTick = currentTick;
        }
        CookingBridge.tickPrepTasks(level);
        CookingBridge.updateBusinessCookMaids(level);
        int counterCount = manager.getCounterToMachine().size();
        if (counterCount > 0) {
            MaidRestaurantBusiness.LOGGER.info("烹饪tick: counterToMachine大小={}, activatedMachines大小={}", counterCount, manager.getActivatedMachines().size());
        }
        for (Map.Entry<BlockPos, BlockPos> entry : manager.getCounterToMachine().entrySet()) {
            BlockPos counterPos = entry.getKey();
            BlockPos machinePos = entry.getValue();
            try {
                // 排班表配置检查：如果附近有排班表且关闭了自动烹饪，则跳过
                if (!MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_COOKING)) {
                    continue;
                }
                MaidRestaurantBusiness.LOGGER.info("烹饪tick: 处理操作台 {} -> 打单机 {}", counterPos, machinePos);
                CookingBridge.processCounter(level, counterPos, machinePos, manager);
            }
            catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("Error processing counter at {}", counterPos, t);
            }
        }
    }

    private static void updateBusinessCookMaids(ServerLevel level) {
        try {
            long currentTime = level.getGameTime();
            // 使用TaskManager的中心化检索缓存，避免重复获取所有女仆
            for (EntityMaid maid : TaskManager.getInstance().getCachedMaids(level)) {
                CookRequest request = (CookRequest)RequestManager.peek((EntityMaid)maid, (int)0);
                if (request != null && request.extraData != null && request.extraData.contains("BusinessCounter")) {
                    businessCookMaids.add(maid.getUUID());
                    pendingRemoval.remove(maid.getUUID());
                    continue;
                }
                if (!businessCookMaids.contains(maid.getUUID())) continue;
                if (!pendingRemoval.containsKey(maid.getUUID())) {
                    pendingRemoval.put(maid.getUUID(), currentTime);
                }
                if (currentTime - pendingRemoval.get(maid.getUUID()) <= 100L) continue;
                businessCookMaids.remove(maid.getUUID());
                pendingRemoval.remove(maid.getUUID());
            }
        }
        catch (Throwable throwable) {
            // empty catch block
        }
    }

    public static void cancelCookRequestsForCounter(ServerLevel level, BlockPos counterPos) {
        try {
            long counterLong = counterPos.asLong();
            int cancelled = 0;
            // 用女仆餐厅的MaidTracker获取所有女仆（而不是level.getEntitiesOfClass，后者用无限AABB返回0个）
            List<EntityMaid> allMaids = MaidTracker.maids != null ? new ArrayList<>(MaidTracker.maids) : new ArrayList<>();
            MaidRestaurantBusiness.LOGGER.info("取消烹饪任务: 操作台={}, MaidTracker找到{}个女仆", counterPos, allMaids.size());
            // 1. 处理所有女仆身上的经营烹饪请求
            // 对于正在进行的任务，不直接移除，而是将remain设为1让女仆完成当前这一次烹饪（避免食物卡在锅里）
            // 对于还没开始的任务（remain > 1），将remain设为1，只让女仆完成当前这一次
            for (EntityMaid maid : allMaids) {
                if (maid == null || !maid.isAlive()) continue;
                CookRequestHandler handler = CookRequestHandler.getOrCreate(maid);
                if (handler == null) {
                    continue;
                }
                int size = handler.size();
                if (size == 0) continue;
                MaidRestaurantBusiness.LOGGER.info("取消烹饪任务: 女仆 {} 有{}个请求", maid.getName().getString(), size);
                // 从后往前遍历处理经营任务
                for (int i = size - 1; i >= 0; i--) {
                    CookRequest req = handler.getAt(i);
                    if (req == null) continue;
                    boolean hasCounter = req.extraData != null && req.extraData.contains("BusinessCounter");
                    long reqCounter = hasCounter ? req.extraData.getLong("BusinessCounter") : -1;
                    if (hasCounter && reqCounter == counterLong) {
                        // 如果任务还剩多次，说明女仆可能正在烹饪，将remain设为1让她完成当前这一次
                        // 如果remain已经是1，说明女仆正在做最后一次，不取消（避免食材卡在锅里）
                        if (req.remain > 1) {
                            req.remain = 1;
                            req.requested = 1;
                            cancelled++;
                            MaidRestaurantBusiness.LOGGER.info("  -> 女仆{}的请求{}正在烹饪，remain设为1完成当前次: id={}", maid.getName().getString(), i, req.id);
                        } else {
                            // remain == 1，女仆正在做最后一次，不取消，让她完成
                            cancelled++;
                            MaidRestaurantBusiness.LOGGER.info("  -> 女仆{}的请求{}正在做最后一次(remain=1)，保留不取消: id={}", maid.getName().getString(), i, req.id);
                        }
                    }
                }
            }
            // 2. 取消世界队列中的经营烹饪请求（这些还没有分配给女仆，可以安全取消）
            WorldCookRequestPool pool = WorldCookRequestPool.get(level);
            if (pool != null && pool.requests != null && !pool.requests.isEmpty()) {
                MaidRestaurantBusiness.LOGGER.info("取消烹饪任务: 世界队列有{}个请求", pool.requests.size());
                for (int i = pool.requests.size() - 1; i >= 0; i--) {
                    CookRequest req = pool.requests.get(i);
                    if (req != null && req.extraData != null && req.extraData.contains("BusinessCounter") && req.extraData.getLong("BusinessCounter") == counterLong) {
                        pool.requests.remove(i);
                        cancelled++;
                        MaidRestaurantBusiness.LOGGER.info("  -> 已移除世界队列请求{}: id={}", i, req.id);
                    }
                }
                pool.setDirty();
            }
            MaidRestaurantBusiness.LOGGER.info("取消烹饪任务完成: 共处理 {} 个与操作台 {} 相关的经营烹饪请求（女仆身上的任务保留完成当前次）", cancelled, counterPos);
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("取消烹饪请求失败", t);
        }
    }

    /**
     * 检查是否有女仆正在做该操作台的经营烹饪任务（检查女仆handler和世界队列）
     */
    public static boolean isAnyMaidCookingForCounter(ServerLevel level, BlockPos counterPos) {
        try {
            long counterLong = counterPos.asLong();
            // 1. 检查所有女仆的handler
            if (MaidTracker.maids != null) {
                for (EntityMaid maid : MaidTracker.maids) {
                    if (maid == null || !maid.isAlive()) continue;
                    CookRequestHandler handler = CookRequestHandler.getOrCreate(maid);
                    if (handler == null) continue;
                    for (int i = 0; i < handler.size(); i++) {
                        CookRequest req = handler.getAt(i);
                        if (req != null && req.extraData != null && req.extraData.contains("BusinessCounter") && req.extraData.getLong("BusinessCounter") == counterLong) {
                            return true;
                        }
                    }
                }
            }
            // 2. 检查世界队列
            WorldCookRequestPool pool = WorldCookRequestPool.get(level);
            if (pool != null && pool.requests != null) {
                for (CookRequest req : pool.requests) {
                    if (req != null && req.extraData != null && req.extraData.contains("BusinessCounter") && req.extraData.getLong("BusinessCounter") == counterLong) {
                        return true;
                    }
                }
            }
        } catch (Throwable t) {}
        return false;
    }

    /**
     * 定期清理残留的烹饪任务（每60秒调用一次）
     * 清理那些操作台不在活跃订单列表中的任务，以及女仆已消失但任务仍存在的情况
     * @param activeCounters 当前有活跃订单的操作台集合
     */
    public static void cleanupStaleTasks(ServerLevel level, java.util.Set<BlockPos> activeCounters) {
        try {
            int cleaned = 0;
            java.util.Set<Long> activeCounterLongs = new java.util.HashSet<>();
            for (BlockPos pos : activeCounters) {
                activeCounterLongs.add(pos.asLong());
            }
            // 1. 清理所有女仆handler中的残留任务
            if (MaidTracker.maids != null) {
                for (EntityMaid maid : MaidTracker.maids) {
                    if (maid == null || !maid.isAlive()) continue;
                    CookRequestHandler handler = CookRequestHandler.getOrCreate(maid);
                    if (handler == null || handler.size() == 0) continue;
                    for (int i = handler.size() - 1; i >= 0; i--) {
                        CookRequest req = handler.getAt(i);
                        if (req == null || req.extraData == null || !req.extraData.contains("BusinessCounter")) continue;
                        long reqCounter = req.extraData.getLong("BusinessCounter");
                        // 如果操作台不在活跃订单列表中，说明是残留任务，清理它
                        if (!activeCounterLongs.contains(reqCounter)) {
                            handler.removeAt(i);
                            cleaned++;
                            MaidRestaurantBusiness.LOGGER.info("清理残留任务: 女仆{}的任务 recipeId={} 操作台={} 不在活跃订单列表中",
                                maid.getName().getString(), req.id, BlockPos.of(reqCounter));
                        }
                    }
                }
            }
            // 2. 清理世界队列中的残留任务
            WorldCookRequestPool pool = WorldCookRequestPool.get(level);
            if (pool != null && pool.requests != null && !pool.requests.isEmpty()) {
                for (int i = pool.requests.size() - 1; i >= 0; i--) {
                    CookRequest req = pool.requests.get(i);
                    if (req == null || req.extraData == null || !req.extraData.contains("BusinessCounter")) continue;
                    long reqCounter = req.extraData.getLong("BusinessCounter");
                    if (!activeCounterLongs.contains(reqCounter)) {
                        pool.requests.remove(i);
                        cleaned++;
                        MaidRestaurantBusiness.LOGGER.info("清理残留任务: 世界队列中的任务 recipeId={} 操作台={} 不在活跃订单列表中",
                            req.id, BlockPos.of(reqCounter));
                    }
                }
                pool.setDirty();
            }
            if (cleaned > 0) {
                MaidRestaurantBusiness.LOGGER.info("定期清理残留任务完成: 共清理{}个残留任务", cleaned);
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("定期清理残留任务时出错", t);
        }
    }

    /**
     * 检查是否有女仆正在做该食物（用于多厨师并行时避免同一种食物重复创建任务）
     * 简化实现：检查recipeId是否包含目标食物的名称
     */
    public static boolean isAnyMaidCookingForItem(ServerLevel level, BlockPos counterPos, String itemId) {
        try {
            long counterLong = counterPos.asLong();
            // 提取物品名称（去掉命名空间前缀）
            String itemName = itemId.contains(":") ? itemId.substring(itemId.indexOf(":") + 1) : itemId;
            // 1. 检查所有女仆的handler
            if (MaidTracker.maids != null) {
                for (EntityMaid maid : MaidTracker.maids) {
                    if (maid == null || !maid.isAlive()) continue;
                    CookRequestHandler handler = CookRequestHandler.getOrCreate(maid);
                    if (handler == null) continue;
                    for (int i = 0; i < handler.size(); i++) {
                        CookRequest req = handler.getAt(i);
                        if (req != null && req.extraData != null && req.extraData.contains("BusinessCounter") && req.extraData.getLong("BusinessCounter") == counterLong) {
                            // 检查recipeId是否包含目标食物名称
                            if (req.id != null && req.id.toString().contains(itemName)) {
                                return true;
                            }
                        }
                    }
                }
            }
            // 2. 检查世界队列
            WorldCookRequestPool pool = WorldCookRequestPool.get(level);
            if (pool != null && pool.requests != null) {
                for (CookRequest req : pool.requests) {
                    if (req != null && req.extraData != null && req.extraData.contains("BusinessCounter") && req.extraData.getLong("BusinessCounter") == counterLong) {
                        if (req.id != null && req.id.toString().contains(itemName)) {
                            return true;
                        }
                    }
                }
            }
        } catch (Throwable t) {}
        return false;
    }

    /**
     * 计算已经在做的该食物的总产出量（用于多厨师并行时避免重复创建任务）
     * 重要：统计的是产出量（requested * 配方产出量），而不是烹饪次数
     * 因为不同配方的产出量不同，只统计烹饪次数会导致重复发布任务
     */
    public static int getCookingOutputForItem(ServerLevel level, BlockPos counterPos, String itemId) {
        try {
            long counterLong = counterPos.asLong();
            int totalOutput = 0;
            // 1. 检查所有女仆的handler
            if (MaidTracker.maids != null) {
                for (EntityMaid maid : MaidTracker.maids) {
                    if (maid == null || !maid.isAlive()) continue;
                    CookRequestHandler handler = CookRequestHandler.getOrCreate(maid);
                    if (handler == null) continue;
                    for (int i = 0; i < handler.size(); i++) {
                        CookRequest req = handler.getAt(i);
                        if (req == null || req.extraData == null) continue;
                        if (!req.extraData.contains("BusinessCounter")) continue;
                        if (req.extraData.getLong("BusinessCounter") != counterLong) continue;
                        String reqItemId = req.extraData.contains("BusinessItemId") ? req.extraData.getString("BusinessItemId") : "";
                        if (!itemId.equals(reqItemId)) continue;
                        // 统计产出量：requested * 配方产出量
                        int recipeOutput = req.extraData.contains("BusinessRecipeOutput") ? req.extraData.getInt("BusinessRecipeOutput") : 1;
                        int taskOutput = req.requested * recipeOutput;
                        totalOutput += taskOutput;
                        MaidRestaurantBusiness.LOGGER.info("烹饪产出统计: 女仆{} 任务 requested={} recipeOutput={} taskOutput={} (总产出+{})", 
                            maid.getName().getString(), req.requested, recipeOutput, taskOutput, taskOutput);
                    }
                }
            }
            // 2. 检查世界队列
            WorldCookRequestPool pool = WorldCookRequestPool.get(level);
            if (pool != null && pool.requests != null) {
                for (CookRequest req : pool.requests) {
                    if (req == null || req.extraData == null) continue;
                    if (!req.extraData.contains("BusinessCounter")) continue;
                    if (req.extraData.getLong("BusinessCounter") != counterLong) continue;
                    String reqItemId = req.extraData.contains("BusinessItemId") ? req.extraData.getString("BusinessItemId") : "";
                    if (!itemId.equals(reqItemId)) continue;
                    int recipeOutput = req.extraData.contains("BusinessRecipeOutput") ? req.extraData.getInt("BusinessRecipeOutput") : 1;
                    int taskOutput = req.requested * recipeOutput;
                    totalOutput += taskOutput;
                    MaidRestaurantBusiness.LOGGER.info("烹饪产出统计: 世界队列任务 requested={} recipeOutput={} taskOutput={} (总产出+{})", 
                        req.requested, recipeOutput, taskOutput, taskOutput);
                }
            }
            MaidRestaurantBusiness.LOGGER.info("烹饪产出统计: 物品{} 已在做总产出量={}", itemId, totalOutput);
            return totalOutput;
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("烹饪产出统计: getCookingOutputForItem抛出异常", t);
            return 0;
        }
    }

    private static void tickPrepTasks(ServerLevel level) {
        Iterator<Map.Entry<BlockPos, PrepTask>> it = prepTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, PrepTask> entry = it.next();
            BlockPos counterPos = entry.getKey();
            PrepTask task = entry.getValue();
            EntityMaid maid = (EntityMaid)task.maidRef.get();
            if (maid == null || !(level.getBlockEntity(counterPos) instanceof TakeoutBoxBlockEntity)) {
                task.cleanup();
                it.remove();
                continue;
            }
            long now = level.getGameTime();
            if (now - task.lastChange < 10L) continue;
            switch (task.state) {
                case 0: {
                    if (task.containerPos == null) {
                        task.state = 2;
                        task.lastChange = now;
                        break;
                    }
                    if (MaidUtils.isNear(maid, task.containerPos, 3.0)) {
                        task.state = 1;
                        task.lastChange = now;
                        break;
                    }
                    MaidUtils.moveToSide(maid, task.containerPos, 0.3);
                    break;
                }
                case 1: {
                    if (CookingBridge.extractFromContainer(level, maid, task)) {
                        task.state = 2;
                        task.lastChange = now;
                        MaidRestaurantBusiness.LOGGER.info("\u5907\u83dc\uff1a\u63d0\u53d6 {} x{}", task.itemId, task.needed);
                        break;
                    }
                    task.cleanup();
                    it.remove();
                    break;
                }
                case 2: {
                    if (MaidUtils.isNear(maid, counterPos, 3.0)) {
                        task.state = 3;
                        task.lastChange = now;
                        break;
                    }
                    MaidUtils.moveToSide(maid, counterPos, 0.3);
                    break;
                }
                case 3: {
                    int inserted;
                    IItemHandler counterInv;
                    BlockEntity be = level.getBlockEntity(counterPos);
                    IItemHandler iItemHandler = counterInv = be != null ? OrderBridge.getItemHandler(be) : null;
                    if (counterInv != null && (inserted = MaidUtils.transferFromMaid(maid, task.itemId, task.needed, counterInv)) > 0) {
                        MaidRestaurantBusiness.LOGGER.info("\u5907\u83dc\uff1a\u653e\u5165\u6253\u5305\u53f0 {} x{}", task.itemId, inserted);
                    }
                    if (task.foods != null && counterInv != null) {
                        CookRequest request;
                        LinkedHashMap<String, Integer> remaining = new LinkedHashMap<String, Integer>(task.foods);
                        for (int slot = 0; slot < counterInv.getSlots(); ++slot) {
                            ResourceLocation itemId;
                            ItemStack stack = counterInv.getStackInSlot(slot);
                            if (stack.isEmpty() || (itemId = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null) continue;
                            remaining.computeIfPresent(itemId.toString(), (k, v) -> Math.max(0, v - stack.getCount()));
                        }
                        if (remaining.values().stream().allMatch(c -> c <= 0) && (request = (CookRequest)RequestManager.peek((EntityMaid)maid, (int)0)) != null && request.extraData != null && request.extraData.contains("BusinessCounter")) {
                            RequestManager.pop((EntityMaid)maid, (int)0);
                            MaidRestaurantBusiness.LOGGER.info("\u5907\u83dc\uff1a\u8ba2\u5355\u5df2\u6ee1\u8db3\uff0c\u53d6\u6d88\u70f9\u996a\u4efb\u52a1");
                        }
                    }
                    task.cleanup();
                    it.remove();
                }
            }
        }
    }

    private static void processCounter(ServerLevel level, BlockPos counterPos, BlockPos machinePos, BusinessManager manager) {
        BlockEntity be = level.getBlockEntity(counterPos);
        if (!(be instanceof TakeoutBoxBlockEntity)) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 操作台不是TakeoutBoxBlockEntity, be={}", be != null ? be.getClass().getSimpleName() : "null");
            return;
        }
        IItemHandler inv = OrderBridge.getItemHandler(be);
        if (inv == null) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 无法获取物品处理器");
            return;
        }
        // 扫描所有槽位查找订单物品
        ItemStack orderStack = ItemStack.EMPTY;
        int orderSlot = -1;
        StringBuilder slotInfo = new StringBuilder();
        for (int slot = 0; slot < inv.getSlots(); ++slot) {
            ItemStack stack = inv.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                ResourceLocation sid = ForgeRegistries.ITEMS.getKey(stack.getItem());
                slotInfo.append(" [").append(slot).append("]=").append(sid).append("x").append(stack.getCount());
                if (stack.is(OtcCompat.ORDER())) {
                    orderStack = stack;
                    orderSlot = slot;
                }
            }
        }
        if (orderStack.isEmpty()) {
            // 操作台没有订单了，清理活跃订单并取消相关烹饪任务
            if (manager.getActiveOrders().containsKey(counterPos)) {
                MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 操作台无订单，清理活跃订单并取消烹饪任务 counter={}", counterPos);
                CookingBridge.cancelCookRequestsForCounter(level, counterPos);
                manager.getActiveOrders().remove(counterPos);
            }
            return;
        }
        MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 在槽位{}找到订单, 槽位内容:{}", orderSlot, slotInfo);
        CompoundTag nbt = orderStack.getTag();
        if (nbt == null || !nbt.contains("FoodList")) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 订单没有FoodList NBT, nbt={}", nbt != null ? nbt.getAllKeys() : "null");
            return;
        }
        if (!ProgressionManager.isCookAndPrepUnlocked(level, machinePos)) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 等级未解锁烹饪和备菜");
            return;
        }
        if (!OrderBridge.isActivated(level, machinePos)) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 打单机未激活");
            return;
        }
        CompoundTag foodList = nbt.getCompound("FoodList");
        int prestige = nbt.getInt("Prestige");
        boolean delivery = nbt.getBoolean("Delivery");
        LinkedHashMap<String, Integer> foods = new LinkedHashMap<String, Integer>();
        for (String key : foodList.getAllKeys()) {
            foods.put(key, foodList.getInt(key));
        }
        MaidRestaurantBusiness.LOGGER.info("\u70f9\u996a\uff1a\u8ba2\u5355\u9700\u6c42 foods={}", foods);
        LinkedHashMap<String, Integer> remaining = new LinkedHashMap<String, Integer>(foods);
        for (int slot = 0; slot < inv.getSlots(); ++slot) {
            ResourceLocation itemId;
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty() || (itemId = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null) continue;
            remaining.computeIfPresent(itemId.toString(), (k, v) -> Math.max(0, v - stack.getCount()));
        }
        // 检测冰箱等食材来源容器中的成品食物（订单所需的食物本身），如果冰箱里有就不需要烹饪
        try {
            Class<?> ingredientSourceApi = Class.forName("cn.breezeth.ordertocook.api.IngredientSourceCompatApi");
            java.lang.reflect.Method countAllNearby = ingredientSourceApi.getMethod("countAllNearby", net.minecraft.world.level.Level.class, BlockPos.class, int.class, java.util.Collection.class);
            if (countAllNearby != null && !foods.isEmpty()) {
                Object fridgeResult = countAllNearby.invoke(null, level, counterPos, 16, foods.keySet());
                if (fridgeResult instanceof Map) {
                    @SuppressWarnings("unchecked")
                    Map<String, Integer> fridgeItems = (Map<String, Integer>)fridgeResult;
                    int fridgeTotal = 0;
                    for (Map.Entry<String, Integer> entry : fridgeItems.entrySet()) {
                        if (entry.getValue() != null && entry.getValue() > 0 && remaining.containsKey(entry.getKey())) {
                            int before = remaining.get(entry.getKey());
                            remaining.put(entry.getKey(), Math.max(0, before - entry.getValue()));
                            fridgeTotal += entry.getValue();
                        }
                    }
                    if (fridgeTotal > 0) {
                        MaidRestaurantBusiness.LOGGER.info("烹饪：从冰箱检测到 {} 个成品食物，已从需求中扣除", fridgeTotal);
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            // otc版本较旧，没有IngredientSourceCompatApi，正常
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("烹饪：检测冰箱成品食物时出错", t);
        }
        MaidRestaurantBusiness.LOGGER.info("烹饪：操作台+冰箱已有食物后 remaining={}", remaining);
        // 注意：不再每次都取消所有任务，否则任务刚发布就被取消，女仆永远无法开始烹饪
        // 残留任务问题已通过之前的清理解决，后续只在订单变更或超时时才取消
        // 如果已经有备菜任务在执行，跳过（避免任务打架）
        if (prepTasks.containsKey(counterPos)) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 备菜任务执行中，跳过烹饪");
            return;
        }
        if (CookingBridge.tryStartPrep(level, counterPos, remaining)) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 备菜任务已启动，跳过烹饪");
            return;
        }
        MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 备菜检查完成，未启动备菜任务，继续烹饪检查");
        if (manager.getActiveOrders().containsKey(counterPos)) {
            ActiveOrder active = manager.getActiveOrders().get(counterPos);
            String currentOrderId = nbt.getString("OrderId");
            String activeOrderId = active.orderNbt != null ? active.orderNbt.getString("OrderId") : "";
            // 超时检查：超过1200 tick（60秒）没完成，认为女仆卡住了，取消重发
            long elapsed = level.getGameTime() - active.createdTick;
            if (elapsed > 1200) {
                MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 活跃订单超时({}tick), 取消旧烹饪任务并移除活跃订单", elapsed);
                CookingBridge.cancelCookRequestsForCounter(level, counterPos);
                // 重置卡住的女仆状态
                EntityMaid stuckMaid = MaidUtils.findCookMaid(level, counterPos, 16);
                if (stuckMaid != null) {
                    MaidUtils.resetMaidState(level, stuckMaid);
                    MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 重置卡住的女仆 {}", stuckMaid.getName().getString());
                }
                manager.getActiveOrders().remove(counterPos);
            } else if (!currentOrderId.equals(activeOrderId)) {
                MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 订单已变更 (旧={}, 新={}), 取消旧烹饪任务并移除活跃订单", activeOrderId, currentOrderId);
                CookingBridge.cancelCookRequestsForCounter(level, counterPos);
                manager.getActiveOrders().remove(counterPos);
            }
            // 注意：不再检查"已有相同活跃订单且女仆仍在烹饪"就直接跳过
            // 因为女仆可能只在做某一种食物，其他食物仍需要处理
            // 后面的 isAnyMaidCookingForItem 和 getCookingCountForItem 会避免同一种食物重复发布
        }
        MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 活跃订单检查完成，继续食物需求检查");
        if (remaining.values().stream().allMatch(c -> c <= 0)) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 所有食物都已满足，跳过");
            return;
        }
        MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 食物需求检查完成，有未满足的食物，开始统计空闲厨师");
        // 多厨师优化：统计真正空闲的厨师数量（没有烹饪任务的厨师）
        // 避免所有任务都被第一个空闲厨师领取
        // 注意：只统计当前任务是TaskCook的女仆（厨师女仆），不统计侍者女仆
        int availableCooks = 0;
        int totalCooks = 0;
        List<EntityMaid> idleCooks = new ArrayList<>();
        if (MaidTracker.maids != null) {
            for (EntityMaid m : MaidTracker.maids) {
                if (m != null && m.isAlive() && m.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5) <= 256.0) {
                    // 只统计厨师女仆（当前任务是TaskCook）
                    // 通过类名判断，避免编译时依赖问题
                    String taskClassName = m.getTask() != null ? m.getTask().getClass().getSimpleName() : "";
                    boolean isCook = "TaskCook".equals(taskClassName);
                    if (!isCook) {
                        MaidRestaurantBusiness.LOGGER.debug("烹饪processCounter: 女仆{} 不是厨师(任务={})，跳过", m.getName().getString(), taskClassName);
                        continue;
                    }
                    totalCooks++;
                    // 检查厨师是否有烹饪任务
                    CookRequestHandler handler = CookRequestHandler.getOrCreate(m);
                    int taskCount = handler != null ? handler.size() : -1;
                    MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 厨师{} 任务数={} 是否空闲={}", m.getName().getString(), taskCount, taskCount == 0);
                    if (handler == null || handler.size() == 0) {
                        availableCooks++;
                        idleCooks.add(m);
                    }
                }
            }
        }
        int maxTasksThisTick = Math.max(1, availableCooks);
        int tasksPosted = 0;
        MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 总厨师{}个，空闲厨师{}个，空闲厨师列表={}，本次最多发布{}个任务", 
            totalCooks, availableCooks, idleCooks.stream().map(m -> m.getName().getString()).collect(java.util.stream.Collectors.toList()), maxTasksThisTick);

        for (Map.Entry entry : remaining.entrySet()) {
            if (tasksPosted >= maxTasksThisTick) {
                MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 已达本次任务上限({})，剩余食物下次处理", maxTasksThisTick);
                break;
            }
            BlockPos cookPos;
            if ((Integer)entry.getValue() <= 0) continue;
            List<RecipeMatch> allMatches = CookingBridge.findAllRecipes(level, (String)entry.getKey(), (Integer)entry.getValue());
            if (allMatches.isEmpty()) {
                MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 未找到配方 item={}, 需求={}", entry.getKey(), entry.getValue());
                continue;
            }
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 找到{}个配方 item={}, 按产出从大到小尝试", allMatches.size(), entry.getKey());
            boolean postedThisItem = false;
            for (RecipeMatch match : allMatches) {
                // 只做能做的部分：检查食材能做多少次，能做至少1次才发布任务
                int canCookCount = 0;
                try {
                    canCookCount = CookingBridge.getMaxCookCount(level, counterPos, match.recipeId, 1);
                    MaidRestaurantBusiness.LOGGER.info("烹饪食材检查: 配方={} 可做次数={}", match.recipeId, canCookCount);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("烹饪食材检查: getMaxCookCount抛出异常，默认返回1确保基本功能正常，配方={}", match.recipeId, t);
                    canCookCount = 1;
                }
                if (canCookCount <= 0) {
                    MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 配方产出={} 连1次都做不了(食材不足), 尝试下一个配方, 配方={}", match.resultCount(), match.recipeId);
                    continue;
                }
                // 最大效率化：一次任务做尽可能多的次数
                // 计算需要的烹饪次数：neededCookTimes = ceil(demand / recipeOutput)
                int demand = (Integer)entry.getValue();
                int recipeOutput = match.resultCount();
                int neededCookTimes = (int)Math.ceil((double)demand / recipeOutput);
                // 一次任务实际做的次数 = min(食材能做的次数, 需要的烹饪次数)
                int actualCookTimes = Math.min(canCookCount, neededCookTimes);
                MaidRestaurantBusiness.LOGGER.info("烹饪: 需求{}个，配方产出{}个，需要烹饪{}次，食材可做{}次，本次任务做{}次(产出{}个)", 
                    demand, recipeOutput, neededCookTimes, canCookCount, actualCookTimes, actualCookTimes * recipeOutput);
                if ((cookPos = CookingBridge.findCookingDevice(level, counterPos, match.recipeType)) == null) {
                    MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 未找到烹饪设备 recipeType={}", match.recipeType);
                    continue;
                }
                CookRequest request = new CookRequest();
                request.id = match.recipeId;
                request.type = match.recipeType;
                request.remain = actualCookTimes;
                request.requested = actualCookTimes;
                request.targets = new long[]{cookPos.asLong()};
                request.extraData = new CompoundTag();
                request.extraData.putLong("BusinessCounter", counterPos.asLong());
                request.extraData.putLong("BusinessMachine", machinePos.asLong());
                request.extraData.putString("BusinessOrderId", nbt.getString("OrderId"));
                request.extraData.putString("BusinessItemId", (String)entry.getKey());
                request.extraData.putInt("BusinessRecipeOutput", match.resultCount()); // 存储配方产出量，用于统计总产出量
                
                // 多厨师并行优化：统计已在做该食物的总产出量（而不是烹饪次数，因为不同配方产出量不同）
                int cookingOutputForItem = CookingBridge.getCookingOutputForItem(level, counterPos, (String)entry.getKey());
                // 加上本tick已发布的产出量
                String tickKey = counterPos.asLong() + "|" + entry.getKey();
                int publishedThisTickOutput = publishedThisTick.getOrDefault(tickKey, 0);
                cookingOutputForItem += publishedThisTickOutput;
                
                // 计算还需要的产出量（demand和recipeOutput在上面已经定义）
                int remainingOutput = demand - cookingOutputForItem;
                
                MaidRestaurantBusiness.LOGGER.info("烹饪：食物{} 需求{}个，配方产出{}个，已在做产出{}个(handler{}+本tick已发布{})，还需产出{}个，食材可做{}次(产出{}个)",
                    entry.getKey(), demand, recipeOutput, cookingOutputForItem, (cookingOutputForItem - publishedThisTickOutput), publishedThisTickOutput, remainingOutput, canCookCount, canCookCount * recipeOutput);
                
                if (remainingOutput <= 0) {
                    MaidRestaurantBusiness.LOGGER.info("烹饪：食物{} 已满足需求(需求{}个，已在做产出{}个)，跳过", entry.getKey(), demand, cookingOutputForItem);
                    continue;
                }
                
                // 本次任务的产出量 = min(食材能做的产出量, 还需要的产出量)
                int maxOutputThisTask = canCookCount * recipeOutput;
                int taskOutput = Math.min(maxOutputThisTask, remainingOutput);
                // 本次烹饪次数 = ceil(本次产出量 / 配方产出量)
                int cookTimesThisTask = (int)Math.ceil((double)taskOutput / recipeOutput);
                
                MaidRestaurantBusiness.LOGGER.info("烹饪：食物{} 本次任务产出{}个，烹饪{}次", entry.getKey(), taskOutput, cookTimesThisTask);

                // 空闲厨具检查：使用TaskManager统一管理厨具占用状态
                // 避免只有一个汤锅却给两个厨师都发布任务导致卡住
                if (cookPos != null && TaskManager.getInstance().isDeviceOccupied(cookPos)) {
                    MaidRestaurantBusiness.LOGGER.info("烹饪：食物{} 最近的厨具{}已被TaskManager标记占用，跳过本次发布，等任务完成后自动释放", entry.getKey(), cookPos);
                    continue;
                }

                // 更新request的烹饪次数为实际烹饪次数
                request.remain = cookTimesThisTask;
                request.requested = cookTimesThisTask;

                // 重要：不在有订单时取消旧任务，避免烹饪过程中被取消
                // 旧任务的取消只在操作台没有订单时进行（由BusinessManager调用）
                MaidRestaurantBusiness.LOGGER.info("烹饪：发布烹饪任务 recipeId={} itemId={} 需求={} 配方产出={} 本次烹饪次数={} 本次产出={}", match.recipeId, entry.getKey(), entry.getValue(), match.resultCount(), cookTimesThisTask, taskOutput);
                // 手动任务分配：直接把任务添加到指定厨师的handler中，绕过RequestManager的随机分配
                // 确保不同的任务分配给不同的厨师，实现真正的多厨师并行
                EntityMaid targetMaid = null;
                if (!idleCooks.isEmpty()) {
                    // 多厨师并行优化：优先选择任务较少的厨师，然后按距离排序
                    // 这样可以避免所有任务都分配给同一个厨师
                    idleCooks.sort((a, b) -> {
                        CookRequestHandler handlerA = CookRequestHandler.getOrCreate(a);
                        CookRequestHandler handlerB = CookRequestHandler.getOrCreate(b);
                        int taskCountA = handlerA != null ? handlerA.size() : 0;
                        int taskCountB = handlerB != null ? handlerB.size() : 0;
                        // 优先选择任务较少的厨师
                        if (taskCountA != taskCountB) {
                            return Integer.compare(taskCountA, taskCountB);
                        }
                        // 任务数相同时，按距离排序
                        return Double.compare(
                            a.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5),
                            b.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5));
                    });
                    targetMaid = idleCooks.remove(0);
                    CookRequestHandler targetHandler = CookRequestHandler.getOrCreate(targetMaid);
                    int targetTaskCount = targetHandler != null ? targetHandler.size() : 0;
                    MaidRestaurantBusiness.LOGGER.info("烹饪: 手动分配任务给厨师 {} (当前任务数={}, 剩余空闲厨师{}个)", 
                        targetMaid.getName().getString(), targetTaskCount, idleCooks.size());
                }
                
                if (targetMaid != null) {
                    // 再次确认厨师确实没有任务（防止任务堆叠）
                    CookRequestHandler finalHandler = CookRequestHandler.getOrCreate(targetMaid);
                    int finalTaskCount = finalHandler != null ? finalHandler.size() : -1;
                    if (finalTaskCount > 0) {
                        MaidRestaurantBusiness.LOGGER.warn("烹饪: 厨师 {} 在分配前已有{}个任务，跳过分配，防止任务堆叠", targetMaid.getName().getString(), finalTaskCount);
                        continue;
                    }
                    // 直接添加到指定厨师的handler中（参考RequestManager.tryDistributeCookRequest的实现）
                    if (finalHandler != null) {
                        finalHandler.add(request);
                        MaidRestaurantBusiness.LOGGER.info("烹饪: 已直接添加任务到厨师 {} 的handler，任务详情: recipeId={}, remain={}, requested={}", 
                            targetMaid.getName().getString(), request.id, request.remain, request.requested);
                    } else {
                        MaidRestaurantBusiness.LOGGER.error("烹饪: 无法获取厨师 {} 的CookRequestHandler，跳过此任务", targetMaid.getName().getString());
                        continue;
                    }
                } else {
                    // 没有空闲厨师，跳过此任务（不回退到RequestManager.post，避免分配给已有任务的厨师）
                    MaidRestaurantBusiness.LOGGER.info("烹饪: 没有空闲厨师，跳过此任务（不回退到自动分配，避免任务堆叠）");
                    continue;
                }
                manager.getActiveOrders().put(counterPos, new ActiveOrder(machinePos, counterPos, match.recipeId, nbt, foods, prestige, delivery, level.getGameTime()));

                // TaskManager集成：创建烹饪任务
                String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_COOKING, cookPos, machinePos);
                if (taskId != null && targetMaid != null) {
                    // 手动分配任务给指定厨师
                    TaskManager.getInstance().assignTask(targetMaid.getUUID(), TaskManager.TYPE_COOKING, level);
                    MaidRestaurantBusiness.LOGGER.info("烹饪: TaskManager创建任务 {} 分配给厨师 {}", taskId, targetMaid.getName().getString());
                } else if (taskId != null) {
                    MaidRestaurantBusiness.LOGGER.info("烹饪: TaskManager创建任务 {} (等待自动分配)", taskId, match.recipeId);
                }

                // 使用TaskManager统一管理厨具占用状态（任务完成/取消时自动释放）
                if (cookPos != null && taskId != null) {
                    UUID occupantUUID = targetMaid != null ? targetMaid.getUUID() : null;
                    boolean occupied = TaskManager.getInstance().occupyDevice(cookPos, taskId, occupantUUID);
                    MaidRestaurantBusiness.LOGGER.info("烹饪: TaskManager标记厨具 {} 为被占用，任务={} 占用者={}, 结果={}", cookPos, taskId, occupantUUID, occupied);
                    if (!occupied) {
                        // 占用失败，说明厨具已经被其他任务占用了，需要取消刚发布的任务
                        MaidRestaurantBusiness.LOGGER.warn("烹饪: 厨具 {} 占用失败，取消刚发布的任务（任务={} 厨师={}）", cookPos, taskId, targetMaid != null ? targetMaid.getName().getString() : "null");
                        if (targetMaid != null) {
                            CookRequestHandler handler = CookRequestHandler.getOrCreate(targetMaid);
                            if (handler != null) {
                                // 从handler中移除刚发布的任务
                                for (int i = handler.size() - 1; i >= 0; i--) {
                                    CookRequest req = handler.getAt(i);
                                    if (req != null && req.extraData != null && req.extraData.contains("BusinessOrderId") && 
                                        req.extraData.getString("BusinessOrderId").equals(nbt.getString("OrderId"))) {
                                        handler.removeAt(i);
                                        MaidRestaurantBusiness.LOGGER.info("烹饪: 已从厨师 {} 的handler中移除占用失败的任务", targetMaid.getName().getString());
                                        break;
                                    }
                                }
                            }
                        }
                        // 取消TaskManager任务
                        TaskManager.getInstance().failTask(occupantUUID, "厨具占用失败");
                        continue; // 跳过当前食物，继续检查其他食物
                    }
                }

                // 更新本tick已发布任务缓存（防止同一个tick内重复发布同一个食物的任务）
                // 缓存的是产出量，不是烹饪次数
                publishedThisTick.put(tickKey, publishedThisTickOutput + taskOutput);
                MaidRestaurantBusiness.LOGGER.info("烹饪: 更新本tick已发布缓存 {} = {} 产出(原{}+本次{})", tickKey, publishedThisTickOutput + taskOutput, publishedThisTickOutput, taskOutput);

                postedThisItem = true;
                tasksPosted++;
                break;
            }
            if (!postedThisItem) {
                MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 物品{} 所有配方都做不了(食材不足或无设备)，跳过", entry.getKey());
            }
        }
    }

    private static boolean tryStartPrep(ServerLevel level, BlockPos counterPos, Map<String, Integer> remaining) {
        // 多厨师优化：遍历所有厨师，找到第一个背包里有成品食物的厨师
        // 而不是只检查最近的一个厨师，避免其他厨师背包有食物但不备菜的问题
        List<EntityMaid> allCooks = new ArrayList<>();
        if (MaidTracker.maids != null) {
            for (EntityMaid m : MaidTracker.maids) {
                if (m != null && m.isAlive() && m.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5) <= 256.0) {
                    allCooks.add(m);
                }
            }
        }
        // 按距离排序，优先用最近的厨师
        allCooks.sort((a, b) -> Double.compare(a.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5),
                b.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5)));

        for (EntityMaid maid : allCooks) {
            IItemHandler maidInv = MaidUtils.getInventory(maid);
            if (maidInv == null) continue;
            for (int slot = 0; slot < maidInv.getSlots(); ++slot) {
                int toTake;
                String idStr;
                ResourceLocation itemId;
                ItemStack stack = maidInv.getStackInSlot(slot);
                if (stack.isEmpty() || (itemId = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null || !remaining.containsKey(idStr = itemId.toString()) || remaining.get(idStr) <= 0 || (toTake = Math.min(remaining.get(idStr), stack.getCount())) <= 0) continue;
                // 只取消当前女仆的经营烹饪任务（用RequestManager.pop触发Mixin拦截，防止食物被交给侍者）
                // 注意：不能调用cancelCookRequestsForCounter，因为它直接removeAt不会触发Mixin，
                // 导致pendingServeRequest没有该女仆UUID，后续ServeRequest不被拦截，食物被丢给侍者
                // 其他厨师的任务让它们自然完成（多厨师并行）
                for (int attempt = 0; attempt < 5; attempt++) {
                    CookRequest req = (CookRequest) RequestManager.peek(maid, 0);
                    if (req != null && req.extraData != null && req.extraData.contains("BusinessCounter") && req.extraData.getLong("BusinessCounter") == counterPos.asLong()) {
                        RequestManager.pop(maid, 0);
                        MaidRestaurantBusiness.LOGGER.info("备菜：取消女仆{}烹饪任务(remain={})，优先备菜", maid.getName().getString(), req.remain);
                    } else {
                        break;
                    }
                }
                prepTasks.put(counterPos, new PrepTask(null, idStr, toTake, maid, remaining));
                MaidRestaurantBusiness.LOGGER.info("备菜：发起任务(女仆{}背包) 物品={} 数量={}", maid.getName().getString(), idStr, toTake);
                return true;
            }
        }
        return false;
    }

    private static boolean extractFromContainer(ServerLevel level, EntityMaid maid, PrepTask task) {
        BlockEntity be = level.getBlockEntity(task.containerPos);
        if (be == null) {
            return false;
        }
        IItemHandler inv = OrderBridge.getItemHandler(be);
        if (inv == null) {
            return false;
        }
        IItemHandler maidInv = MaidUtils.getInventory(maid);
        if (maidInv == null) {
            return false;
        }
        for (int slot = 0; slot < inv.getSlots(); ++slot) {
            ResourceLocation itemId;
            ItemStack stack = inv.getStackInSlot(slot);
            if (stack.isEmpty() || (itemId = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null || !itemId.toString().equals(task.itemId)) continue;
            ItemStack extracted = inv.extractItem(slot, task.needed, false);
            if (extracted.isEmpty()) {
                return false;
            }
            ItemHandlerHelper.insertItemStacked((IItemHandler)maidInv, (ItemStack)extracted, (boolean)false);
            return true;
        }
        return false;
    }

    private static int getMaxCookCount(ServerLevel level, BlockPos counterPos, ResourceLocation recipeId, int maxCount) {
        try {
            IItemHandler maidInv;
            Recipe recipe = level.getRecipeManager().byKey(recipeId).orElse(null);
            if (recipe == null) {
                return 0;
            }
            if (CookTasks.getTask((RecipeType)recipe.getType()) == null) {
                return 0;
            }
            ArrayList<Ingredient> ingredients = new ArrayList<Ingredient>();
            for (Object ingObj : recipe.getIngredients()) {
                if (!(ingObj instanceof Ingredient)) continue;
                Ingredient ing = (Ingredient) ingObj;
                if (ing.isEmpty()) continue;
                ingredients.add(ing);
            }
            if (ingredients.isEmpty()) {
                return maxCount;
            }
            HashMap<String, Integer> available = new HashMap<String, Integer>();
            // 先收集配方中所有可能的物品ID（用于冰箱检测）
            java.util.Set<String> recipeItemIds = new java.util.HashSet<>();
            for (Ingredient ing : ingredients) {
                for (ItemStack match : ing.getItems()) {
                    ResourceLocation matchId = ForgeRegistries.ITEMS.getKey(match.getItem());
                    if (matchId != null) recipeItemIds.add(matchId.toString());
                }
            }
            for (BlockPos check : BlockPos.betweenClosed((BlockPos)counterPos.offset(-16, -8, -16), (BlockPos)counterPos.offset(16, 8, 16))) {
                IItemHandler inv;
                if (check.equals(counterPos) || (inv = MaidStorages.tryGetHandler((Level)level, (BlockPos)check)) == null) continue;
                for (int slot = 0; slot < inv.getSlots(); ++slot) {
                    ResourceLocation itemId;
                    ItemStack stack = inv.getStackInSlot(slot);
                    if (stack.isEmpty() || (itemId = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null) continue;
                    available.merge(itemId.toString(), stack.getCount(), Integer::sum);
                }
            }
            // 检测otc冰箱等食材来源容器中的食材（用otc提供的IngredientSourceCompatApi）
            try {
                Class<?> ingredientSourceApi = Class.forName("cn.breezeth.ordertocook.api.IngredientSourceCompatApi");
                java.lang.reflect.Method countAllNearby = ingredientSourceApi.getMethod("countAllNearby", net.minecraft.world.level.Level.class, BlockPos.class, int.class, java.util.Collection.class);
                if (countAllNearby != null && !recipeItemIds.isEmpty()) {
                    Object fridgeResult = countAllNearby.invoke(null, level, counterPos, 16, recipeItemIds);
                    if (fridgeResult instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Integer> fridgeItems = (Map<String, Integer>)fridgeResult;
                        int fridgeTotal = 0;
                        for (Map.Entry<String, Integer> entry : fridgeItems.entrySet()) {
                            if (entry.getValue() != null && entry.getValue() > 0) {
                                available.merge(entry.getKey(), entry.getValue(), Integer::sum);
                                fridgeTotal += entry.getValue();
                            }
                        }
                        if (fridgeTotal > 0) {
                            MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 从冰箱等食材来源检测到 {} 个物品", fridgeTotal);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // otc版本较旧，没有IngredientSourceCompatApi，正常
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 检测冰箱食材时出错", t);
            }
            // 第二阶段：按女仆个体计算，检查所有厨师女仆，只要有一个能做就返回至少1次
            // 0. 先统计每个物品的总需求（遍历所有Ingredient，累加匹配物品的需求）
            HashMap<String, Integer> itemTotalNeeded = new HashMap<String, Integer>();
            for (Ingredient ing : ingredients) {
                for (ItemStack match : ing.getItems()) {
                    ResourceLocation matchId = ForgeRegistries.ITEMS.getKey(match.getItem());
                    if (matchId == null) continue;
                    itemTotalNeeded.merge(matchId.toString(), 1, Integer::sum);
                }
            }

            // 检查配方是否需要容器（carrier），如碗、盘子等
            boolean needsCarrier = false;
            int carrierPerCook = 1;
            java.util.Set<String> carrierItemIds = new java.util.HashSet<>();
            try {
                java.lang.reflect.Method carrierMethod = recipe.getClass().getMethod("carrier");
                if (carrierMethod != null) {
                    Object carrierResult = carrierMethod.invoke(recipe);
                    if (carrierResult instanceof Ingredient carrierIngredient && !carrierIngredient.isEmpty()) {
                        needsCarrier = true;
                        for (ItemStack match : carrierIngredient.getItems()) {
                            ResourceLocation carrierId = ForgeRegistries.ITEMS.getKey(match.getItem());
                            if (carrierId != null) carrierItemIds.add(carrierId.toString());
                        }
                    } else if (carrierResult instanceof ItemStack carrierStack && !carrierStack.isEmpty()) {
                        needsCarrier = true;
                        ResourceLocation carrierId = ForgeRegistries.ITEMS.getKey(carrierStack.getItem());
                        if (carrierId != null) carrierItemIds.add(carrierId.toString());
                        carrierPerCook = Math.max(1, carrierStack.getCount());
                    }
                }
            } catch (NoSuchMethodException e) {
                // 配方没有carrier()方法，不需要容器，正常
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 检查容器时出错", t);
            }

            // 1. 先计算周围容器的共享食材（所有女仆都可以去拿）
            HashMap<String, Integer> sharedAvailable = new HashMap<String, Integer>(available);

            // 2. 遍历所有厨师女仆，检查是否有女仆能做至少1次
            int maxCanMakeByAnyMaid = 0;
            List<EntityMaid> allCooks = new ArrayList<>();
            if (MaidTracker.maids != null) {
                for (EntityMaid m : MaidTracker.maids) {
                    if (m != null && m.isAlive() && m.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5) <= 256.0) {
                        allCooks.add(m);
                    }
                }
            }
            MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 找到{}个可用厨师女仆", allCooks.size());

            for (EntityMaid maid : allCooks) {
                maidInv = MaidUtils.getInventory(maid);
                if (maidInv == null) continue;

                // 该女仆的可用食材 = 共享容器食材 + 她自己的背包食材
                HashMap<String, Integer> maidAvailable = new HashMap<String, Integer>(sharedAvailable);
                for (int slot = 0; slot < maidInv.getSlots(); ++slot) {
                    ResourceLocation itemId;
                    ItemStack stack = maidInv.getStackInSlot(slot);
                    if (stack.isEmpty() || (itemId = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null) continue;
                    maidAvailable.merge(itemId.toString(), stack.getCount(), Integer::sum);
                }

                // 计算该女仆能做多少次（食材检查）
                int maidCanMake = maxCount;
                for (Map.Entry<String, Integer> entry : itemTotalNeeded.entrySet()) {
                    String itemId = entry.getKey();
                    int totalNeeded = entry.getValue();
                    int have = maidAvailable.getOrDefault(itemId, 0).intValue();
                    int canMake = have / totalNeeded;
                    maidCanMake = Math.min(maidCanMake, canMake);
                }

                // 检查容器需求
                if (needsCarrier && maidCanMake > 0) {
                    int carrierHave = 0;
                    for (String carrierId : carrierItemIds) {
                        carrierHave += maidAvailable.getOrDefault(carrierId, 0);
                    }
                    maidCanMake = Math.min(maidCanMake, carrierHave / carrierPerCook);
                }

                MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 女仆{} 能做{}次", maid.getName().getString(), maidCanMake);
                maxCanMakeByAnyMaid = Math.max(maxCanMakeByAnyMaid, maidCanMake);
            }

            MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 所有女仆中最大能做次数={}", maxCanMakeByAnyMaid);
            return maxCanMakeByAnyMaid;
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("烹饪食材检测: getMaxCookCount抛出异常，配方={}", recipeId, t);
            return 0;
        }
    }

    private static List<RecipeMatch> findAllRecipes(ServerLevel level, String itemId, int needed) {
        ResourceLocation rl = ResourceLocation.tryParse((String)itemId);
        if (rl == null) {
            return new ArrayList<RecipeMatch>();
        }
        ArrayList<RecipeMatch> matches = new ArrayList<RecipeMatch>();
        for (Recipe recipe : level.getRecipeManager().getRecipes()) {
            try {
                ResourceLocation resultId;
                ItemStack result;
                if (CookTasks.getTask((RecipeType)recipe.getType()) == null || (result = recipe.getResultItem(level.registryAccess())).isEmpty() || (resultId = ForgeRegistries.ITEMS.getKey(result.getItem())) == null || !resultId.equals(rl)) continue;
                matches.add(new RecipeMatch(recipe.getId(), recipe.getType(), result.getCount()));
            }
            catch (Throwable result) {}
        }
        // 智能配方选择：优先选产出不超过需求的配方中最大的，都超过时选最小的
        matches.sort((a, b) -> {
            boolean aOver = a.resultCount() > needed;
            boolean bOver = b.resultCount() > needed;
            if (aOver && !bOver) return 1;   // a超过需求，b不超过，b优先
            if (!aOver && bOver) return -1;  // a不超过，b超过，a优先
            if (!aOver && !bOver) {
                // 都不超过需求，选产出最大的
                return Integer.compare(b.resultCount(), a.resultCount());
            } else {
                // 都超过需求，选产出最小的
                return Integer.compare(a.resultCount(), b.resultCount());
            }
        });
        return matches;
    }

    private static BlockPos findCookingDevice(ServerLevel level, BlockPos pos, RecipeType<?> type) {
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        // 多厨师并行优化：优先选择没有被占用的厨具
        BlockPos nearestFree = null;
        double nearestFreeDist = Double.MAX_VALUE;
        String taskClass = CookTasks.getTask(type).getClass().getSimpleName();
        for (BlockPos check : BlockPos.betweenClosed((BlockPos)pos.offset(-8, -4, -8), (BlockPos)pos.offset(8, 4, 8))) {
            double d;
            BlockEntity be = level.getBlockEntity(check);
            if (be == null) continue;
            String cn = be.getClass().getName();
            boolean match = false;
            if (taskClass.contains("Stockpot")) {
                match = cn.contains("StockpotBlockEntity");
            } else if (taskClass.contains("Pot")) {
                match = cn.contains("CookingPotBlockEntity") || cn.contains("PotBlockEntity");
            } else if (taskClass.contains("Steamer")) {
                match = cn.contains("SteamerBlockEntity");
            }
            if (!match) continue;
            d = check.distSqr((Vec3i)pos);
            // 检查厨具是否被占用（使用TaskManager统一管理）
            boolean occupied = TaskManager.getInstance().isDeviceOccupied(check.immutable());
            if (!occupied && d < nearestFreeDist) {
                nearestFreeDist = d;
                nearestFree = check.immutable();
            }
            if (d < nearestDist) {
                nearestDist = d;
                nearest = check.immutable();
            }
        }
        // 优先返回没有被占用的厨具，如果没有则返回最近的厨具
        BlockPos result = nearestFree != null ? nearestFree : nearest;
        if (result != null) {
            MaidRestaurantBusiness.LOGGER.info("烹饪: 选择厨具 {} (是否空闲={}, 距离={})", result, nearestFree != null, result.distSqr(pos));
        }
        return result;
    }

    /**
     * 统计指定类型的总厨具数量（用于多厨师并行控制的maxParallel计算）
     * 注意：maxParallel应该基于总厨具数量，因为每个任务会占用一个厨具
     */
    private static int countTotalCookingDevices(ServerLevel level, BlockPos pos, RecipeType<?> type) {
        int count = 0;
        String taskClass = CookTasks.getTask(type).getClass().getSimpleName();
        for (BlockPos check : BlockPos.betweenClosed((BlockPos)pos.offset(-8, -4, -8), (BlockPos)pos.offset(8, 4, 8))) {
            BlockEntity be = level.getBlockEntity(check);
            if (be == null) continue;
            String cn = be.getClass().getName();
            boolean match = false;
            if (taskClass.contains("Stockpot")) {
                match = cn.contains("StockpotBlockEntity");
            } else if (taskClass.contains("Pot")) {
                match = cn.contains("CookingPotBlockEntity") || cn.contains("PotBlockEntity");
            } else if (taskClass.contains("Steamer")) {
                match = cn.contains("SteamerBlockEntity");
            }
            if (match) count++;
        }
        MaidRestaurantBusiness.LOGGER.info("烹饪: 总厨具统计 类型={} 总数={}", taskClass, count);
        return count;
    }

    /**
     * 统计指定类型的可用厨具数量（用于发布任务时选择可用厨具）
     * 多厨师并行优化：只统计没有被占用的厨具
     */
    private static int countAvailableCookingDevices(ServerLevel level, BlockPos pos, RecipeType<?> type) {
        int count = 0;
        int totalCount = 0;
        String taskClass = CookTasks.getTask(type).getClass().getSimpleName();
        for (BlockPos check : BlockPos.betweenClosed((BlockPos)pos.offset(-8, -4, -8), (BlockPos)pos.offset(8, 4, 8))) {
            BlockEntity be = level.getBlockEntity(check);
            if (be == null) continue;
            String cn = be.getClass().getName();
            boolean match = false;
            if (taskClass.contains("Stockpot")) {
                match = cn.contains("StockpotBlockEntity");
            } else if (taskClass.contains("Pot")) {
                match = cn.contains("CookingPotBlockEntity") || cn.contains("PotBlockEntity");
            } else if (taskClass.contains("Steamer")) {
                match = cn.contains("SteamerBlockEntity");
            }
            if (match) {
                totalCount++;
                // 只统计没有被占用的厨具
                if (!CookingDeviceManager.getInstance().isOccupied(check)) {
                    count++;
                }
            }
        }
        MaidRestaurantBusiness.LOGGER.info("烹饪: 可用厨具统计 总数={}, 空闲={}", totalCount, count);
        return count;
    }

    private static class PrepTask {
        final BlockPos containerPos;
        final String itemId;
        final int needed;
        final Map<String, Integer> foods;
        int state;
        long lastChange;
        final WeakReference<EntityMaid> maidRef;

        PrepTask(BlockPos containerPos, String itemId, int needed, EntityMaid maid, Map<String, Integer> foods) {
            this.containerPos = containerPos;
            this.itemId = itemId;
            this.needed = needed;
            this.foods = foods;
            this.state = 0;
            this.lastChange = 0L;
            this.maidRef = new WeakReference<EntityMaid>(maid);
            MaidUtils.setOccupied(maid, true);
        }

        void cleanup() {
            EntityMaid maid = (EntityMaid)this.maidRef.get();
            if (maid != null) {
                MaidUtils.setOccupied(maid, false);
            }
        }
    }

    private record RecipeMatch(ResourceLocation recipeId, RecipeType<?> recipeType, int resultCount) {
    }
}
