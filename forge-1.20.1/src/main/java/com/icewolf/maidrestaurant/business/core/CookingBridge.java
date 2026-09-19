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
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import com.icewolf.maidrestaurant.business.core.CookingDeviceStatsManager;
import com.icewolf.maidrestaurant.business.core.ActiveOrder;
import com.icewolf.maidrestaurant.business.core.BusinessManager;
import com.icewolf.maidrestaurant.business.core.MaidUtils;
import com.icewolf.maidrestaurant.business.core.OrderBridge;
import com.icewolf.maidrestaurant.business.core.ProgressionManager;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.api.request.IRequest;
import com.mastermarisa.maid_restaurant.event.MaidTracker;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.request.CookRequestHandler;
import com.mastermarisa.maid_restaurant.request.world.WorldCookRequestPool;
import com.mastermarisa.maid_restaurant.utils.CookTasks;
import com.mastermarisa.maid_restaurant.utils.MaidStorages;
import com.mastermarisa.maid_restaurant.utils.component.StackPredicate;
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
    // 记录最近一次食材不足时缺少的食材名称（最多2种）
    private static List<String> lastMissingIngredients = new ArrayList<>();
    // 食材不足冷却机制
    private static final Map<Long, Long> insufficientIngredientsCooldown = new ConcurrentHashMap<>();
    private static final long INSUFFICIENT_INGREDIENTS_COOLDOWN_TICKS = 100L;

    public static void tickCooking(ServerLevel level, BusinessManager manager) {
        // 清空本tick已发布任务缓存（每个tick只清空一次）
        long currentTick = level.getGameTime();
        if (currentTick != lastPublishedTick) {
            publishedThisTick.clear();
            lastPublishedTick = currentTick;
        }
        CookingBridge.tickPrepTasks(level);
        CookingBridge.updateBusinessCookMaids(level);

        // 性能优化：没有空闲厨师时直接返回，避免无用的食材检查和可做部分计算
        // 备菜任务超时检测和厨师列表更新是必要的维护工作，已在上面执行
        if (!hasIdleChef(level)) {
            return;
        }

        int counterCount = manager.getCounterToMachine().size();
        if (counterCount > 0) {
        }
        for (Map.Entry<BlockPos, BlockPos> entry : manager.getCounterToMachine().entrySet()) {
            BlockPos counterPos = entry.getKey();
            BlockPos machinePos = entry.getValue();
            try {
                // 排班表配置检查：如果附近有排班表且关闭了自动烹饪，则跳过
                if (!MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_COOKING)) {
                    continue;
                }
                // 食材不足冷却检查
                Long cooldownUntil = insufficientIngredientsCooldown.get(counterPos.asLong());
                if (cooldownUntil != null && currentTick < cooldownUntil) {
                    continue;
                }
                if (cooldownUntil != null) {
                    insufficientIngredientsCooldown.remove(counterPos.asLong());
                }
                CookingBridge.processCounter(level, counterPos, machinePos, manager);
            }
            catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("Error processing counter at {}", counterPos, t);
            }
        }
    }


    /**
     * 检查是否有空闲的厨师女仆
     * 性能优化：没有空闲厨师时，tickCooking直接返回，避免无用的食材检查和可做部分计算
     * @return true表示有空闲厨师，false表示所有厨师都在忙
     */
    private static boolean hasIdleChef(ServerLevel level) {
        try {
            // 使用TaskManager的中心化检索缓存，避免重复获取所有女仆
            List<EntityMaid> maids = TaskManager.getInstance().getCachedMaids(level);
            if (maids == null || maids.isEmpty()) {
                return false;
            }
            int idleCooks = 0;
            // 检查是否有空闲的厨师女仆
            for (EntityMaid maid : maids) {
                if (maid == null || !maid.isAlive()) continue;
                // 必须是厨师职业
                if (!MaidUtils.isCookMaid(maid)) continue;
                // 没有正在执行的任务
                if (!TaskManager.getInstance().hasMaidTask(maid.getUUID())) {
                    idleCooks++;
                }
            }
            return idleCooks > 0;
        } catch (Throwable t) {
            // 出错时默认返回true，避免影响正常功能
            return true;
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
                        } else {
                            // remain == 1，女仆正在做最后一次，不取消，让她完成
                            cancelled++;
                        }
                    }
                }
            }
            // 2. 取消世界队列中的经营烹饪请求（这些还没有分配给女仆，可以安全取消）
            WorldCookRequestPool pool = WorldCookRequestPool.get(level);
            if (pool != null && pool.requests != null && !pool.requests.isEmpty()) {
                for (int i = pool.requests.size() - 1; i >= 0; i--) {
                    CookRequest req = pool.requests.get(i);
                    if (req != null && req.extraData != null && req.extraData.contains("BusinessCounter") && req.extraData.getLong("BusinessCounter") == counterLong) {
                        pool.requests.remove(i);
                        cancelled++;
                    }
                }
                pool.setDirty();
            }
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
                    }
                }
                pool.setDirty();
            }
            if (cleaned > 0) {
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
                }
            }
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
                if (maid != null) {
                    TaskManager.getInstance().failTask(maid.getUUID(), "prep target missing");
                }
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
                        break;
                    }
                    MaidRestaurantBusiness.LOGGER.warn("备菜：从容器提取失败，结束任务 女仆={}", maid.getName().getString());
                    TaskManager.getInstance().failTask(maid.getUUID(), "extract from container failed");
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
                    IItemHandler counterInv;
                    BlockEntity be = level.getBlockEntity(counterPos);
                    counterInv = be != null ? OrderBridge.getItemHandler(be) : null;
                    // 1) 优先把成品放进操作台
                    int inserted = 0;
                    if (counterInv != null) {
                        inserted = MaidUtils.transferFromMaid(maid, task.itemId, task.needed, counterInv);
                    }
                    // 2) 操作台成品格放不下（已满）时，剩余成品转入操作台范围内、OTC打包时能取到的冰箱。
                    //    女仆不额外寻路，仍站在操作台旁、对操作台做放入动作，只是物品实际落进冰箱，防止卡死。
                    int toFridge = 0;
                    if (inserted < task.needed) {
                        int left = task.needed - inserted;
                        toFridge = CookingBridge.depositMaidFoodToNearbyFridges(level, counterPos, maid, task.itemId, left);
                        if (inserted + toFridge < task.needed) {
                            MaidRestaurantBusiness.LOGGER.warn("[备菜] 操作台已满且附近没有可放的冰箱，剩余成品留在女仆背包(不卡死): 女仆={} 食物={} 已放={} 需求={}",
                                maid.getName().getString(), task.itemId, inserted + toFridge, task.needed);
                        }
                    }
                    // 手臂摇摆动画：女仆对着操作台做放入动作（无论成品最终进操作台还是冰箱）
                    try {
                        maid.swing(net.minecraft.world.InteractionHand.OFF_HAND);
                    } catch (Throwable t) {}
                    if (task.foods != null) {
                        CookRequest request;
                        LinkedHashMap<String, Integer> remaining = new LinkedHashMap<String, Integer>(task.foods);
                        if (counterInv != null) {
                            for (int slot = 0; slot < counterInv.getSlots(); ++slot) {
                                ResourceLocation itemId;
                                ItemStack stack = counterInv.getStackInSlot(slot);
                                if (stack.isEmpty() || (itemId = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null) continue;
                                remaining.computeIfPresent(itemId.toString(), (k, v) -> Math.max(0, v - stack.getCount()));
                            }
                        }
                        // 计入冰箱里的成品（含本次与历史转入），口径与 processCounter / OTC 打包取餐一致
                        CookingBridge.countFridgeReadyFood(level, counterPos, remaining);
                        if (remaining.values().stream().allMatch(c -> c <= 0) && (request = (CookRequest)RequestManager.peek((EntityMaid)maid, (int)0)) != null && request.extraData != null && request.extraData.contains("BusinessCounter")) {
                            RequestManager.pop((EntityMaid)maid, (int)0);
                        }
                    }
                    TaskManager.getInstance().completeTask(maid.getUUID());
                    task.cleanup();
                    it.remove();
                }
            }
        }
    }

    /** 冰箱位置扫描结果的短期缓存，避免操作台满时每轮都做一次24格球形扫描 */
    private static final class FridgeCache {
        long gameTime;
        final List<BlockPos> positions = new ArrayList<BlockPos>();
    }
    private static final Map<String, FridgeCache> fridgeScanCache = new java.util.concurrent.ConcurrentHashMap<String, FridgeCache>();
    private static final Map<Class<?>, java.lang.reflect.Field[]> fridgeFieldCache = new java.util.concurrent.ConcurrentHashMap<Class<?>, java.lang.reflect.Field[]>();

    /**
     * 返回操作台 24 格球形范围内、OTC 打包能取到的冰箱位置（由近到远）。
     * 半径必须与 TakeoutBoxBlockEntity 硬编码的 24 严格一致；结果按 维度+操作台 缓存 60tick（冰箱是静态方块）。
     */
    private static List<BlockPos> nearbyFridgePositions(ServerLevel level, BlockPos origin) {
        String key = level.dimension().location().toString() + "@" + origin.asLong();
        long now = level.getGameTime();
        FridgeCache cached = fridgeScanCache.get(key);
        if (cached != null && now - cached.gameTime < 60L) {
            return cached.positions;
        }
        List<BlockPos> positions = new ArrayList<BlockPos>();
        int radius = 24;
        int radiusSq = radius * radius;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int dy = -radius; dy <= radius; ++dy) {
            for (int dx = -radius; dx <= radius; ++dx) {
                for (int dz = -radius; dz <= radius; ++dz) {
                    if (dx * dx + dy * dy + dz * dz > radiusSq) continue;
                    cursor.set(origin.getX() + dx, origin.getY() + dy, origin.getZ() + dz);
                    BlockEntity fbe = level.getBlockEntity(cursor);
                    if (fbe != null && fbe.getClass().getSimpleName().equals("RefrigeratorBlockEntity")) {
                        positions.add(cursor.immutable());
                    }
                }
            }
        }
        positions.sort(java.util.Comparator.comparingLong(p -> {
            long dx = p.getX() - origin.getX();
            long dy = p.getY() - origin.getY();
            long dz = p.getZ() - origin.getZ();
            return dx * dx + dy * dy + dz * dz;
        }));
        FridgeCache fresh = new FridgeCache();
        fresh.gameTime = now;
        fresh.positions.addAll(positions);
        fridgeScanCache.put(key, fresh);
        return positions;
    }

    /** 反射拿到一台冰箱的 upper/lower 两个 Container，包成 IItemHandler（先 upper 后 lower）；反射字段按 Class 缓存 */
    private static IItemHandler[] fridgeHandlers(ServerLevel level, BlockPos pos) {
        BlockEntity be = level.getBlockEntity(pos);
        if (be == null || !be.getClass().getSimpleName().equals("RefrigeratorBlockEntity")) return null;
        java.lang.reflect.Field[] fields = fridgeFieldCache.computeIfAbsent(be.getClass(), clz -> {
            try {
                java.lang.reflect.Field upper = clz.getDeclaredField("upperContainer");
                java.lang.reflect.Field lower = clz.getDeclaredField("lowerContainer");
                upper.setAccessible(true);
                lower.setAccessible(true);
                return new java.lang.reflect.Field[]{upper, lower};
            } catch (Throwable t) {
                return null;
            }
        });
        if (fields == null) return null;
        try {
            List<IItemHandler> out = new ArrayList<IItemHandler>(2);
            for (java.lang.reflect.Field field : fields) {
                Object containerObj = field.get(be);
                if (containerObj instanceof net.minecraft.world.Container) {
                    out.add(new net.minecraftforge.items.wrapper.InvWrapper((net.minecraft.world.Container) containerObj));
                }
            }
            return out.toArray(new IItemHandler[0]);
        } catch (Throwable t) {
            return null;
        }
    }

    /** simulate：某物品栏是否还能收下至少 1 个 probe（不实际移动） */
    private static boolean handlerCanAccept(IItemHandler handler, ItemStack probe) {
        if (handler == null || probe == null || probe.isEmpty()) return false;
        for (int slot = 0; slot < handler.getSlots(); ++slot) {
            ItemStack leftover = handler.insertItem(slot, probe.copy(), true);
            if (leftover.isEmpty()) return true;
        }
        return false;
    }

    /**
     * 备菜前置可行性：操作台成品格，或其 24 格内任一冰箱，是否还能收下至少 1 个指定成品。
     * 都放不下时返回 false，tryStartPrep 据此跳过、不发起备菜，避免女仆跑到操作台扑空空转、反复刷日志。
     * 拿不到物品实例或检测异常时返回 true（不拦截，保持旧行为，宁可多试也不误卡）。
     */
    private static boolean canDepositPrepFood(ServerLevel level, BlockPos counterPos, String itemId) {
        try {
            net.minecraft.world.item.Item item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
            if (item == null) return true;
            ItemStack probe = new ItemStack(item, 1);
            BlockEntity be = level.getBlockEntity(counterPos);
            IItemHandler counterInv = be != null ? OrderBridge.getItemHandler(be) : null;
            if (handlerCanAccept(counterInv, probe)) return true;
            for (BlockPos fp : nearbyFridgePositions(level, counterPos)) {
                IItemHandler[] handlers = fridgeHandlers(level, fp);
                if (handlers == null) continue;
                for (IItemHandler handler : handlers) {
                    if (handlerCanAccept(handler, probe)) return true;
                }
            }
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 备菜兜底：操作台成品格放不下时，把女仆背包里的成品转入操作台 24 格球形范围内、
     * OTC 打包时能取到的冰箱（RefrigeratorBlockEntity）。女仆不额外寻路，仍在操作台旁操作。
     * @return 实际转入冰箱的数量；没有冰箱 / 冰箱也满 / 旧版OTC字段不同 时返回 0（成品留在女仆背包，不卡死）
     */
    private static int depositMaidFoodToNearbyFridges(ServerLevel level, BlockPos origin, EntityMaid maid, String itemId, int amount) {
        if (amount <= 0) {
            return 0;
        }
        int total = 0;
        try {
            for (BlockPos fp : nearbyFridgePositions(level, origin)) {
                if (total >= amount) break;
                IItemHandler[] handlers = fridgeHandlers(level, fp);
                if (handlers == null) continue;
                for (IItemHandler fridgeHandler : handlers) {
                    if (total >= amount) break;
                    try {
                        int moved = MaidUtils.transferFromMaid(maid, itemId, amount - total, fridgeHandler);
                        total += moved;
                    } catch (Throwable t) {
                        MaidRestaurantBusiness.LOGGER.warn("[备菜] 写入冰箱失败 @{}: {}", fp, t.toString());
                    }
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("[备菜] 转存冰箱流程异常", t);
        }
        return total;
    }

    /**
     * 反射 OTC IngredientSourceCompatApi.countAllNearby，把操作台 searchRange 范围内冰箱里的成品
     * 从 remaining 中抵扣，口径与 processCounter 的成品判断、OTC 操作台打包取餐完全一致。
     */
    private static void countFridgeReadyFood(ServerLevel level, BlockPos counterPos, LinkedHashMap<String, Integer> remaining) {
        try {
            Class<?> apiClass = Class.forName("cn.breezeth.ordertocook.api.IngredientSourceCompatApi");
            java.lang.reflect.Method countAllNearby = apiClass.getMethod("countAllNearby", net.minecraft.world.level.Level.class, BlockPos.class, int.class, java.util.Collection.class);
            Object result = countAllNearby.invoke(null, level, counterPos, 24, remaining.keySet());
            if (result instanceof Map) {
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) result).entrySet()) {
                    if (entry.getKey() == null || !(entry.getValue() instanceof Integer)) continue;
                    String key = entry.getKey().toString();
                    int count = (Integer) entry.getValue();
                    if (count > 0 && remaining.containsKey(key)) {
                        remaining.put(key, Math.max(0, remaining.get(key) - count));
                    }
                }
            }
        } catch (ClassNotFoundException cn) {
            // 旧版 OTC 没有 IngredientSourceCompatApi，正常，忽略
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("[备菜] 统计冰箱成品出错", t);
        }
    }

    private static boolean processCounter(ServerLevel level, BlockPos counterPos, BlockPos machinePos, BusinessManager manager) {
        boolean postedAnyTask = false;
        BlockEntity be = level.getBlockEntity(counterPos);
        if (!(be instanceof TakeoutBoxBlockEntity)) {
            return false;
        }
        IItemHandler inv = OrderBridge.getItemHandler(be);
        if (inv == null) {
            return false;
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
                CookingBridge.cancelCookRequestsForCounter(level, counterPos);
                manager.getActiveOrders().remove(counterPos);
            }
            // 清除食材不足冷却（新订单可能需要不同的食材）
            insufficientIngredientsCooldown.remove(counterPos.asLong());
            return false;
        }
        CompoundTag nbt = orderStack.getTag();
        if (nbt == null || !nbt.contains("FoodList")) {
            return false;
        }
        if (!ProgressionManager.isCookAndPrepUnlocked(level, machinePos)) {
            return false;
        }
        if (!OrderBridge.isActivated(level, machinePos)) {
            return false;
        }
        CompoundTag foodList = nbt.getCompound("FoodList");
        int prestige = nbt.getInt("Prestige");
        boolean delivery = nbt.getBoolean("Delivery");
        LinkedHashMap<String, Integer> foods = new LinkedHashMap<String, Integer>();
        for (String key : foodList.getAllKeys()) {
            foods.put(key, foodList.getInt(key));
        }
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
                Object fridgeResult = countAllNearby.invoke(null, level, counterPos, 24, foods.keySet());
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
                    }
                }
            }
        } catch (ClassNotFoundException e) {
            // otc版本较旧，没有IngredientSourceCompatApi，正常
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("烹饪：检测冰箱成品食物时出错", t);
        }
        // 注意：不再每次都取消所有任务，否则任务刚发布就被取消，女仆永远无法开始烹饪
        // 残留任务问题已通过之前的清理解决，后续只在订单变更或超时时才取消
        // 【顺序关键】此时remaining只扣减了"操作台+容器/冰箱"成品，尚未扣女仆背包。
        // tryStartPrep正是靠remaining>0来识别"女仆背包里已有、但操作台还没有"的待备成品，所以必须先备菜。
        if (!prepTasks.containsKey(counterPos)) {
            CookingBridge.tryStartPrep(level, counterPos, remaining);
        }
        // 备菜调度之后，再把本范围内"厨师女仆"背包里的成品从remaining扣减，这份remaining供下方
        // 烹饪任务发布使用，避免成品已在某厨师背包、却又重做一份。只统计厨师女仆，不统计侍者。
        // 必须放在tryStartPrep之后：若先扣减，背包成品对应remaining会变0，tryStartPrep反而识别不到待备成品、导致第一次后不再备菜。
        deductCookMaidBackpackItems(level, counterPos, remaining);
        if (manager.getActiveOrders().containsKey(counterPos)) {
            ActiveOrder active = manager.getActiveOrders().get(counterPos);
            String currentOrderId = nbt.getString("OrderId");
            String activeOrderId = active.orderNbt != null ? active.orderNbt.getString("OrderId") : "";
            // 超时检查：超过1200 tick（60秒）没完成，认为女仆卡住了，取消重发
            long elapsed = level.getGameTime() - active.createdTick;
            if (elapsed > 1200) {
                // 重要：在取消任务前，先检查是否有女仆正在烹饪
                // 如果有女仆正在烹饪（有CookRequest或坐在椅子上），不要重置她，延长超时时间
                boolean hasMaidCooking = false;
                for (EntityMaid m : TaskManager.getInstance().getCachedMaidsForMachine(level, machinePos)) {
                    if (m != null && m.isAlive() && m.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5) <= 576.0) {
                        // 检查女仆是否有CookRequest
                        try {
                            com.mastermarisa.maid_restaurant.request.CookRequest cookReq = 
                                (com.mastermarisa.maid_restaurant.request.CookRequest)
                                com.mastermarisa.maid_restaurant.utils.RequestManager.peek(m, 0);
                            if (cookReq != null) {
                                hasMaidCooking = true;
                                break;
                            }
                        } catch (Throwable t) {}
                        // 检查女仆是否坐在椅子上（正在烹饪）
                        if (m.isPassenger()) {
                            hasMaidCooking = true;
                            break;
                        }
                    }
                }
                if (hasMaidCooking) {
                    // 有女仆正在烹饪，延长超时时间（重置createdTick），避免中断正常烹饪
                    active.createdTick = level.getGameTime();
                } else {
                    // 确实没有女仆在烹饪，才取消重发
                    CookingBridge.cancelCookRequestsForCounter(level, counterPos);
                    manager.getActiveOrders().remove(counterPos);
                }
            } else if (!currentOrderId.equals(activeOrderId)) {
                CookingBridge.cancelCookRequestsForCounter(level, counterPos);
                manager.getActiveOrders().remove(counterPos);
                // 清除食材不足冷却（新订单可能需要不同的食材）
                insufficientIngredientsCooldown.remove(counterPos.asLong());
            }
            // 注意：不再检查"已有相同活跃订单且女仆仍在烹饪"就直接跳过
            // 因为女仆可能只在做某一种食物，其他食物仍需要处理
            // 后面的 isAnyMaidCookingForItem 和 getCookingCountForItem 会避免同一种食物重复发布
        }
        if (remaining.values().stream().allMatch(c -> c <= 0)) {
            return false;
        }
        // 多厨师优化：统计真正空闲的厨师数量（没有烹饪任务的厨师）
        // 避免所有任务都被第一个空闲厨师领取
        // 注意：只统计当前任务是TaskCook的女仆（厨师女仆），不统计侍者女仆
        int availableCooks = 0;
        int totalCooks = 0;
        List<EntityMaid> idleCooks = new ArrayList<>();
        // 使用TaskManager的中心化检索缓存（以激活的打单机为中心搜索）
        for (EntityMaid m : TaskManager.getInstance().getCachedMaidsForMachine(level, machinePos)) {
            if (m != null && m.isAlive() && m.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5) <= 576.0) {
                // 只统计厨师女仆（当前任务是TaskCook）
                // 通过类名判断，避免编译时依赖问题
                String taskClassName = m.getTask() != null ? m.getTask().getClass().getSimpleName() : "";
                boolean isCook = "TaskCook".equals(taskClassName);
                if (!isCook) {
                    continue;
                }
                totalCooks++;
                // 检查厨师是否有烹饪任务
                CookRequestHandler handler = CookRequestHandler.getOrCreate(m);
                int taskCount = handler != null ? handler.size() : -1;
                // 正在备菜的女仆isOccupied=true，不计入空闲厨师，避免给备菜中的女仆重复派烹饪任务
                // 注意：背包里有成品但还没轮到备菜的厨师仍可进名单——其成品已在remaining中抵扣，
                // 发布循环只会让她做"真正还缺"的其他菜，不会重做自己背包里已有的菜
                boolean maidOccupied = false;
                try { maidOccupied = MaidUtils.isOccupied(m); } catch (Throwable t) {}
                if ((handler == null || handler.size() == 0) && !maidOccupied) {
                    availableCooks++;
                    idleCooks.add(m);
                }
            }
        }
        // 每个操作台每次扫描最多发布1个烹饪任务：把任务错开，避免多个女仆同时抢占同一厨具
        int maxTasksThisTick = 1;
        int tasksPosted = 0;

        // 空闲厨师派单优先级（只排序一次，循环里按此顺序挑"确实能做这道菜"的厨师）：
        // 手上任务少的优先，任务数相同时离操作台近的优先
        idleCooks.sort((a, b) -> {
            CookRequestHandler ha = CookRequestHandler.getOrCreate(a);
            CookRequestHandler hb = CookRequestHandler.getOrCreate(b);
            int ta = ha != null ? ha.size() : 0;
            int tb = hb != null ? hb.size() : 0;
            if (ta != tb) return Integer.compare(ta, tb);
            return Double.compare(
                a.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5),
                b.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5));
        });

        // 本操作台正在备菜的任务：其itemId对应的成品已在某女仆背包里、正在送去操作台，
        // 在备菜完成（成品进操作台、需求扣减）之前，不能再为同一种食物重复发布烹饪任务，
        // 否则会出现"刚做完还没放进操作台，又让另一个厨师重做一遍"的重复发布。
        PrepTask activePrepForCounter = prepTasks.get(counterPos);
        for (Map.Entry entry : remaining.entrySet()) {
            if (tasksPosted >= maxTasksThisTick) {
                break;
            }
            if ((Integer)entry.getValue() <= 0) continue;
            // 该食物正有女仆备菜中，跳过（其他食物仍可正常发布给空闲厨师）
            if (activePrepForCounter != null && entry.getKey().equals(activePrepForCounter.itemId)) {
                continue;
            }
            List<RecipeMatch> allMatches = CookingBridge.findAllRecipes(level, (String)entry.getKey(), (Integer)entry.getValue());
            if (allMatches.isEmpty()) {
                continue;
            }
            boolean postedThisItem = false;
            // 先按"订单需求 - 已在做产出 - 本tick已发布产出"判断这道菜是否还需要做。
            // 若已被在做的任务满足，直接跳过该食物（不进入厨具/食材判断、不弹"厨具都在用"气泡），
            // 修复"订单只要1个、a已占用唯一厨具在做、b仍喊没有空闲厨具"的误报。
            int demand = (Integer) entry.getValue();
            int cookingOutputForItem = CookingBridge.getCookingOutputForItem(level, counterPos, (String) entry.getKey());
            String tickKey = counterPos.asLong() + "|" + entry.getKey();
            int publishedThisTickOutput = publishedThisTick.getOrDefault(tickKey, 0);
            int remainingOutput = demand - cookingOutputForItem - publishedThisTickOutput;
            if (remainingOutput <= 0) {
                continue;
            }
            for (RecipeMatch match : allMatches) {
                // ===== ① 先厨具：按打单机隔离的"厨具总数 vs 活跃任务数"判断，没有对应厨具/没有空闲厨具直接提示 =====
                String deviceType = null;
                try {
                    String taskClassName = CookTasks.getTask(match.recipeType()).getClass().getSimpleName();
                    deviceType = CookingDeviceStatsManager.getDeviceTypeFromTaskClass(taskClassName);
                    // 注意：不在此处用isDeviceOccupied提前continue，否则厨具被占用时会跳过canPublishTask，
                    // 导致"没有空闲厨具"气泡无法触发。canPublishTask内部已按"厨具总数 vs 活跃任务数"判断，等价且更准确。
                    if (deviceType != null && !CookingDeviceStatsManager.getInstance().canPublishTask(machinePos, deviceType, level)) {
                        if (!idleCooks.isEmpty()) {
                            String deviceName = deviceDisplayName(deviceType);
                            CookingDeviceStatsManager.StationStats stats = CookingDeviceStatsManager.getInstance().getStationStats(machinePos);
                            int totalDevices = stats != null ? stats.getDeviceCount(deviceType) : 0;
                            com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.onStateChanged(idleCooks.get(0));
                            if (totalDevices == 0) {
                                com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.chefNoDeviceAtAll(idleCooks.get(0), deviceName);
                            } else {
                                com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.chefNoDeviceBusy(idleCooks.get(0), deviceName);
                            }
                        }
                        continue;
                    }
                } catch (Exception e) {
                    // 厨具统计检查异常，忽略（fail-open，不影响正常烹饪流程）
                }

                // ===== ② 定位本店铺范围内的具体厨具（与厨具统计同一中心/范围/精确判定，统计说有就一定能找到）=====
                BlockPos cookPos = CookingBridge.findCookingDevice(level, machinePos, match.recipeType);
                if (cookPos == null) {
                    continue;
                }

                // ===== ③ 后食材（按厨师个体判定）：逐位空闲厨师算"公共物资+她自己背包"能做几锅，
                //          只把任务派给确实能做的那位，避免私料/碗在 A 身上却派给 B 导致取不到料卡住 =====
                java.util.LinkedHashMap<java.util.UUID, Integer> perMaid;
                try {
                    perMaid = CookingBridge.getCookCountByMaid(level, counterPos, match.recipeId, 1, idleCooks);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("烹饪食材检查: getCookCountByMaid抛出异常，配方={}", match.recipeId, t);
                    perMaid = new java.util.LinkedHashMap<>();
                }
                EntityMaid targetMaid = null;
                int chosenCanMake = 0;
                for (EntityMaid m : idleCooks) {
                    Integer c = perMaid.get(m.getUUID());
                    if (c != null && c >= 1) {
                        targetMaid = m;
                        chosenCanMake = c;
                        break;
                    }
                }
                if (targetMaid == null) {
                    // 没有任何空闲厨师能做：食材/碗/汤底不足，显示气泡（给第一个空闲厨师）
                    if (!idleCooks.isEmpty()) {
                        try {
                            com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.onStateChanged(idleCooks.get(0));
                            String missingMsg = lastMissingIngredients.isEmpty() ? "食材不够了" : String.join("、", lastMissingIngredients);
                            // 多种样式随机选择，增加差异化
                            String[] noIngredientsMessages = new String[]{
                                "缺少" + missingMsg + "...(；′⌒`)",
                                "需要" + missingMsg + "呢...",
                                "这个..." + missingMsg + "不太够呀",
                                missingMsg + "好像没有了呢...(´；ω；`)"
                            };
                            com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.showCustomBubble(idleCooks.get(0), "chef_no_ingredients", noIngredientsMessages, 100);
                        } catch (Exception e) {}
                    }
                    continue;
                }

                // ===== ④ 计算本次任务次数/产出（最大效率化：一次任务做尽可能多的次数）=====
                // 剩余需求 remainingOutput / demand / cookingOutputForItem 已在配方循环外预算
                int recipeOutput = match.resultCount();
                // 本次任务的产出量 = min(被选中厨师能做的产出量, 还需要的产出量)
                int maxOutputThisTask = chosenCanMake * recipeOutput;
                int taskOutput = Math.min(maxOutputThisTask, remainingOutput);
                // 本次烹饪次数 = ceil(本次产出量 / 配方产出量)
                int cookTimesThisTask = (int)Math.ceil((double)taskOutput / recipeOutput);

                // ===== ⑤ 构建任务请求 =====
                CookRequest request = new CookRequest();
                request.id = match.recipeId;
                request.type = match.recipeType;
                request.remain = cookTimesThisTask;
                request.requested = cookTimesThisTask;
                request.targets = new long[]{cookPos.asLong()};
                request.extraData = new CompoundTag();
                request.extraData.putLong("BusinessCounter", counterPos.asLong());
                request.extraData.putLong("BusinessMachine", machinePos.asLong());
                request.extraData.putString("BusinessOrderId", nbt.getString("OrderId"));
                request.extraData.putString("BusinessItemId", (String)entry.getKey());
                request.extraData.putInt("BusinessRecipeOutput", match.resultCount()); // 存储配方产出量，用于统计总产出量

                // 重要：不在有订单时取消旧任务，避免烹饪过程中被取消
                // 旧任务的取消只在操作台没有订单时进行（由BusinessManager调用）
                // targetMaid 已在③按"她确实能做这道菜"选定（idleCooks 已按任务数/距离排序，取其中第一个能做的）
                idleCooks.remove(targetMaid);
                
                if (targetMaid == null) {
                    // 没有空闲厨师，跳过此任务（不回退到RequestManager.post，避免分配给已有任务的厨师）
                    continue;
                }
                // 再次确认厨师确实没有任务（防止任务堆叠）
                CookRequestHandler finalHandler = CookRequestHandler.getOrCreate(targetMaid);
                int finalTaskCount = finalHandler != null ? finalHandler.size() : -1;
                if (finalTaskCount > 0) {
                    MaidRestaurantBusiness.LOGGER.warn("烹饪: 厨师 {} 在分配前已有{}个任务，跳过分配，防止任务堆叠", targetMaid.getName().getString(), finalTaskCount);
                    continue;
                }
                if (finalHandler == null) {
                    MaidRestaurantBusiness.LOGGER.error("烹饪: 无法获取厨师 {} 的CookRequestHandler，跳过此任务", targetMaid.getName().getString());
                    continue;
                }

                // ① 先在 TaskManager 创建任务（同一厨具已有进行中任务时返回 null；此时请求尚未交给厨师，零污染）
                String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_COOKING, cookPos, machinePos, deviceType);
                if (taskId == null) {
                    // 同一厨具已有进行中的烹饪任务，跳过本次发布
                    continue;
                }
                // ② 定向分配给本订单选定的厨师（不用全局“最近任务”，避免抢别的订单/厨具）
                if (!TaskManager.getInstance().assignSpecificTask(targetMaid.getUUID(), taskId)) {
                    TaskManager.getInstance().discardTask(taskId);
                    MaidRestaurantBusiness.LOGGER.warn("烹饪: 定向分配失败，放弃该任务 厨师={}", targetMaid.getName().getString());
                    continue;
                }
                // ③ 占用厨具；失败则整体回滚任务（此时请求还没交给厨师，无需回滚 handler）
                if (cookPos != null && !TaskManager.getInstance().occupyDevice(cookPos, taskId, targetMaid.getUUID())) {
                    MaidRestaurantBusiness.LOGGER.warn("烹饪: 厨具 {} 占用失败，回滚任务 {} 厨师={}", cookPos, taskId, targetMaid.getName().getString());
                    TaskManager.getInstance().failTask(targetMaid.getUUID(), "厨具占用失败");
                    continue;
                }

                // ④ 建任务、定向分配、占厨具全部成功后，才把烹饪请求真正交给厨师
                finalHandler.add(request);
                // 显示厨师开始烹饪气泡
                try {
                    com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.onStateChanged(targetMaid);
                    com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.chefStartCooking(targetMaid);
                } catch (Exception e) {}
                manager.getActiveOrders().put(counterPos, new ActiveOrder(machinePos, counterPos, match.recipeId, nbt, foods, prestige, delivery, level.getGameTime()));

                // 更新本tick已发布任务缓存（防止同一个tick内重复发布同一个食物的任务）
                // 缓存的是产出量，不是烹饪次数
                publishedThisTick.put(tickKey, publishedThisTickOutput + taskOutput);

                postedThisItem = true;
                tasksPosted++;
                postedAnyTask = true;
                break;
            }
        }
        if (!postedAnyTask) {
            insufficientIngredientsCooldown.put(counterPos.asLong(), level.getGameTime() + INSUFFICIENT_INGREDIENTS_COOLDOWN_TICKS);
        }
        return postedAnyTask;
    }

    /**
     * 把本打单机范围内、厨师女仆背包里已经做好的成品算作"已有库存"，从需求remaining中扣减。
     * 这些成品虽还没备进操作台，但已经做出来、会由备菜流程交付，不能再为它们重复发布烹饪任务。
     * 严格只统计厨师女仆（TaskCook / isCookMaid），不统计侍者女仆；范围与tryStartPrep一致（24格）。
     * 每轮实时计算：女仆走远/死亡掉出范围后其背包成品不再抵扣，会自动补做，不会漏单。
     */
    private static void deductCookMaidBackpackItems(ServerLevel level, BlockPos counterPos, Map<String, Integer> remaining) {
        try {
            for (EntityMaid m : TaskManager.getInstance().getCachedMaidsForMachine(level, counterPos)) {
                if (m == null || !m.isAlive()) continue;
                // 只统计厨师女仆，排除侍者
                if (!MaidUtils.isCookMaid(m)) continue;
                if (m.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5) > 576.0) continue;
                IItemHandler maidInv = MaidUtils.getInventory(m);
                if (maidInv == null) continue;
                for (int slot = 0; slot < maidInv.getSlots(); ++slot) {
                    ItemStack stack = maidInv.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;
                    ResourceLocation itemId = ForgeRegistries.ITEMS.getKey(stack.getItem());
                    if (itemId == null) continue;
                    String idStr = itemId.toString();
                    if (remaining.containsKey(idStr) && remaining.get(idStr) > 0) {
                        remaining.computeIfPresent(idStr, (k, v) -> Math.max(0, v - stack.getCount()));
                    }
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("烹饪：统计厨师女仆背包成品时出错", t);
        }
    }

    private static boolean tryStartPrep(ServerLevel level, BlockPos counterPos, Map<String, Integer> remaining) {
        // 多厨师优化：遍历所有厨师，找到第一个背包里有成品食物的厨师
        // 而不是只检查最近的一个厨师，避免其他厨师背包有食物但不备菜的问题
        List<EntityMaid> allCooks = new ArrayList<>();
        // 使用TaskManager的中心化检索缓存（以激活的打单机为中心搜索）
            for (EntityMaid m : TaskManager.getInstance().getCachedMaidsForMachine(level, counterPos)) {
                if (m != null && m.isAlive() && m.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5) <= 576.0) {
                    allCooks.add(m);
                }
            }
        // 按距离排序，优先用最近的厨师
        allCooks.sort((a, b) -> Double.compare(a.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5),
                b.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5)));

        for (EntityMaid maid : allCooks) {
            // 备菜是厨师的活，只选厨师女仆（TaskCook），排除侍者女仆
            if (!MaidUtils.isCookMaid(maid)) continue;
            // 备菜只能选真正空闲的女仆，跳过以下忙碌状态，避免"烹饪做到一半被拉去备菜"的打断：
            // 1) 女仆餐厅CookRequestHandler里还有烹饪请求（正在做菜）
            // 2) isOccupied（已经在备菜）
            // 3) TaskManager里还有任务（烹饪/打包/配送等）
            CookRequestHandler busyHandler = CookRequestHandler.getOrCreate(maid);
            if (busyHandler != null && busyHandler.size() > 0) continue;
            if (MaidUtils.isOccupied(maid)) continue;
            try {
                if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) continue;
            } catch (Throwable t) {}
            IItemHandler maidInv = MaidUtils.getInventory(maid);
            if (maidInv == null) continue;
            for (int slot = 0; slot < maidInv.getSlots(); ++slot) {
                int toTake;
                String idStr;
                ResourceLocation itemId;
                ItemStack stack = maidInv.getStackInSlot(slot);
                if (stack.isEmpty() || (itemId = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null || !remaining.containsKey(idStr = itemId.toString()) || remaining.get(idStr) <= 0 || (toTake = Math.min(remaining.get(idStr), stack.getCount())) <= 0) continue;
                // 目的地可行性：操作台成品格和24格内冰箱都放不下这份成品时，先不发起备菜，
                // 避免女仆跑到操作台扑空、任务反复重建空转刷日志；等槽位腾空后下一轮(≤10tick)自然发起
                if (!CookingBridge.canDepositPrepFood(level, counterPos, idStr)) {
                    // 持续放不下时用错误气泡提示玩家清理操作台（目的地容量与具体女仆无关，本轮不再尝试其他女仆）
                    com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.onStateChanged(maid);
                    com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.chefCounterFull(maid);
                    return false;
                }
                // 只取消当前女仆的经营烹饪任务（用RequestManager.pop触发Mixin拦截，防止食物被交给侍者）
                // 注意：不能调用cancelCookRequestsForCounter，因为它直接removeAt不会触发Mixin，
                // 导致pendingServeRequest没有该女仆UUID，后续ServeRequest不被拦截，食物被丢给侍者
                // 其他厨师的任务让它们自然完成（多厨师并行）
                for (int attempt = 0; attempt < 5; attempt++) {
                    CookRequest req = (CookRequest) RequestManager.peek(maid, 0);
                    if (req != null && req.extraData != null && req.extraData.contains("BusinessCounter") && req.extraData.getLong("BusinessCounter") == counterPos.asLong()) {
                        RequestManager.pop(maid, 0);
                    } else {
                        break;
                    }
                }
                prepTasks.put(counterPos, new PrepTask(null, idStr, toTake, maid, remaining));
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

    @SuppressWarnings({"unchecked", "rawtypes"})
    /**
     * 按厨师个体判定可做锅数。
     * 传入发布段筛好的"空闲厨师"候选名单（TaskCook 职业 + 无烹饪任务 + 未 occupied + 24格内，顺序即派单优先级），
     * 对每位厨师用"公共容器物资 + 她自己背包 + 她自己空桶的流体兜底"各算能做几锅，返回 女仆UUID->锅数（保持入参顺序）。
     * 派单时只把任务交给确实能做的那位厨师，从根本上避免"私料/碗在 A 身上、任务却派给 B 导致取不到料卡住"。
     * 缺料气泡的缺失项仍按"公共容器 + 全部空闲厨师背包"汇总反查（整店口径），方便玩家一次补全。
     */
    private static java.util.LinkedHashMap<java.util.UUID, Integer> getCookCountByMaid(ServerLevel level, BlockPos counterPos, ResourceLocation recipeId, int maxCount, List<EntityMaid> candidateCooks) {
        java.util.LinkedHashMap<java.util.UUID, Integer> result = new java.util.LinkedHashMap<>();
        try {
            Recipe recipe = level.getRecipeManager().byKey(recipeId).orElse(null);
            // 每次调用都清空缺少食材记录，避免上一次的残留
            lastMissingIngredients.clear();
            if (recipe == null || candidateCooks == null || candidateCooks.isEmpty()) {
                return result;
            }
            ICookTask cookTask = CookTasks.getTask((RecipeType) recipe.getType());
            if (cookTask == null) {
                return result;
            }

            // ① 权威需求列表：直接复用女仆餐厅本体拼好的谓词，
            //    已包含 普通食材 + 碗/容器(carrier，按产出份数重复) + 汤锅汤底 + 煎锅油脂，
            //    农夫乐事厨锅的 getOutputContainer 也由本体 CookingPotCookTask 打包在内。
            List<StackPredicate> required;
            try {
                required = cookTask.getIngredients(recipe, level);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 本体getIngredients调用失败，回退为仅普通食材，配方={}", recipeId, t);
                required = new ArrayList<>();
                for (Object ingObj : recipe.getIngredients()) {
                    if (ingObj instanceof Ingredient ing && !ing.isEmpty()) {
                        required.add(new StackPredicate(ing));
                    }
                }
            }
            // 异构谓词归一化：汤锅汤底本体返回的是 Predicate<ISoupBase>（不接受 ItemStack，直接 test 会抛 ClassCastException），
            // 这里转成等价的"汤底桶"物品谓词，让碗(carrier)与汤底都能被逐锅贪心按物品统一判定，避免整道菜被异常判成0
            required = normalizeIngredients(recipe, required);
            if (required == null || required.isEmpty()) {
                for (EntityMaid c : candidateCooks) {
                    if (c != null) result.put(c.getUUID(), maxCount);
                }
                return result;
            }

            // ② 命名候选（仅用于缺料气泡命名，判定一律以本体谓词为准）
            List<Item> namedItems = new ArrayList<>();
            List<String> namedLabels = new ArrayList<>();
            buildNamedCandidates(recipe, namedItems, namedLabels);

            // OTC 冰箱检索需要的物品 id 集合：普通食材 + 碗/汤底/油脂候选
            java.util.Set<String> fridgeIds = new java.util.HashSet<>();
            for (Object ingObj : recipe.getIngredients()) {
                if (ingObj instanceof Ingredient ing && !ing.isEmpty()) {
                    for (ItemStack match : ing.getItems()) {
                        ResourceLocation mid = ForgeRegistries.ITEMS.getKey(match.getItem());
                        if (mid != null) fridgeIds.add(mid.toString());
                    }
                }
            }
            for (Item it : namedItems) {
                ResourceLocation mid = ForgeRegistries.ITEMS.getKey(it);
                if (mid != null) fridgeIds.add(mid.toString());
            }

            // ③ 共享容器（操作台 ±24 / y±8），按 Item 汇总（公共物资，所有厨师都可取用）
            Map<Item, Integer> shared = new HashMap<>();
            for (BlockPos check : BlockPos.betweenClosed(counterPos.offset(-24, -8, -24), counterPos.offset(24, 8, 24))) {
                IItemHandler inv;
                if (check.equals(counterPos) || (inv = MaidStorages.tryGetHandler((Level) level, check)) == null) continue;
                for (int slot = 0; slot < inv.getSlots(); ++slot) {
                    ItemStack stack = inv.getStackInSlot(slot);
                    if (stack.isEmpty()) continue;
                    shared.merge(stack.getItem(), stack.getCount(), Integer::sum);
                }
            }
            // OTC 冰箱等食材来源（IngredientSourceCompatApi）
            try {
                Class<?> ingredientSourceApi = Class.forName("cn.breezeth.ordertocook.api.IngredientSourceCompatApi");
                java.lang.reflect.Method countAllNearby = ingredientSourceApi.getMethod("countAllNearby", Level.class, BlockPos.class, int.class, java.util.Collection.class);
                if (!fridgeIds.isEmpty()) {
                    Object fridgeResult = countAllNearby.invoke(null, level, counterPos, 24, fridgeIds);
                    if (fridgeResult instanceof Map<?, ?> fridgeMap) {
                        for (Map.Entry<?, ?> entry : fridgeMap.entrySet()) {
                            if (entry.getKey() == null || !(entry.getValue() instanceof Integer cnt) || cnt <= 0) continue;
                            Item it = ForgeRegistries.ITEMS.getValue(ResourceLocation.tryParse(entry.getKey().toString()));
                            if (it != null) shared.merge(it, cnt, Integer::sum);
                        }
                    }
                }
            } catch (ClassNotFoundException e) {
                // 旧版 OTC 没有该 API，正常
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 冰箱检索出错", t);
            }

            // ④ 逐厨师判定（候选由发布段传入，已是 TaskCook 职业 + 空闲 + 24格内，顺序即派单优先级）：
            //    每位厨师用"公共容器 + 她自己背包 + 她自己空桶的流体兜底"各算能做几锅，私料只记在她名下。
            Map<Item, Integer> totalAvailable = new HashMap<>(shared);
            for (EntityMaid maid : candidateCooks) {
                if (maid == null || !maid.isAlive()) continue;
                IItemHandler maidInv = MaidUtils.getInventory(maid);
                Map<Item, Integer> maidAvailable = new HashMap<>(shared);
                if (maidInv != null) {
                    for (int slot = 0; slot < maidInv.getSlots(); ++slot) {
                        ItemStack stack = maidInv.getStackInSlot(slot);
                        if (stack.isEmpty()) continue;
                        maidAvailable.merge(stack.getItem(), stack.getCount(), Integer::sum);
                        totalAvailable.merge(stack.getItem(), stack.getCount(), Integer::sum);
                    }
                }
                // 存储附属在场时：仅当"该厨师"背包有空桶、附近流体存储有足量水/岩浆，才为她虚拟汤底桶
                // （未安装存储附属时该方法什么都不做，维持必须已有现成桶的原版行为）
                applyStorageFluidBonus(level, counterPos, required, maidInv, maidAvailable);
                int canMake = countCooksByGreedy(required, maidAvailable, maxCount);
                result.put(maid.getUUID(), Math.max(0, canMake));
            }

            // ⑤ 没有任何空闲厨师能做时，用与"能否制作判定"完全相同的逐锅贪心反查卡住的需求谓词，
            //    再逐个虚拟满足以列出最多3种独立缺失。这样数量不足（如碗要3个只有1个）、替代原料、
            //    汤底桶、油脂等情形都与判定同源，不会再出现"明明缺碗却漏报/只报缺普通食材"的问题。
            boolean anyCanMake = false;
            for (Integer c : result.values()) {
                if (c != null && c > 0) { anyCanMake = true; break; }
            }
            if (!anyCanMake) {
                Map<Item, Integer> virtual = new HashMap<>(totalAvailable);
                java.util.Set<String> usedNames = new java.util.HashSet<>();
                int guard = 0;
                while (lastMissingIngredients.size() < 3 && guard++ < required.size() + 2) {
                    StackPredicate[] firstFailed = new StackPredicate[1];
                    int made = countCooksByGreedy(required, virtual, 1, firstFailed);
                    if (made >= 1 || firstFailed[0] == null) break;
                    StackPredicate failed = firstFailed[0];
                    String name = null;
                    Item sampleItem = null;
                    for (int i = 0; i < namedItems.size(); i++) {
                        Item cand = namedItems.get(i);
                        if (cand != null && failed.test(new ItemStack(cand, 1))) {
                            name = namedLabels.get(i);
                            sampleItem = cand;
                            break;
                        }
                    }
                    if (sampleItem == null) {
                        // 命名候选都匹配不上（理论上不应发生），兜底并终止避免死循环
                        if (usedNames.add("材料")) lastMissingIngredients.add("材料");
                        break;
                    }
                    if (name == null) name = "材料";
                    if (usedNames.add(name)) lastMissingIngredients.add(name);
                    // 虚拟满足该需求一份，继续定位下一个独立缺失项
                    virtual.merge(sampleItem, 1, Integer::sum);
                }
            }
            return result;
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("烹饪食材检测: getCookCountByMaid抛出异常，配方={}", recipeId, t);
            return new java.util.LinkedHashMap<>();
        }
    }

    /**
     * 逐锅贪心扣减：一口锅让需求列表中每个谓词各匹配 1 个物品（同一物品实例不重复分配），
     * 能连续满足几口锅即返回几。天然正确处理替代原料（鸡蛋/海龟蛋同谓词）、碗×N、汤底、油脂。
     */
    /**
     * 把本体 getIngredients 中"不接受 ItemStack 的异构谓词"统一归一化为物品谓词。
     * 目前唯一的异构谓词是汤锅汤底：本体用 StackPredicate.of(Predicate&lt;ISoupBase&gt;) 表达，
     * 对它调用 test(ItemStack) 会抛 ClassCastException。这里探测后将其替换为等价的"汤底桶"
     * （水桶/岩浆桶/牛奶桶，由森罗 SoupBaseManager.getDisplayStack() 决定）物品谓词；
     * 普通食材、碗(carrier)、煎锅油脂(TagKey)、农夫厨锅容器(Item) 本身就吃 ItemStack，原样保留。
     */
    private static List<StackPredicate> normalizeIngredients(Recipe recipe, List<StackPredicate> raw) {
        List<StackPredicate> out = new ArrayList<>();
        if (raw == null) return out;
        for (StackPredicate p : raw) {
            if (p == null) continue;
            if (isItemStackPredicate(p)) {
                out.add(p);
                continue;
            }
            StackPredicate soup = toSoupBaseItemPredicate(recipe);
            if (soup != null) {
                out.add(soup);
            } else {
                // 兜底：森罗汤锅的汤底总归是水桶/岩浆桶/牛奶桶之一
                out.add(new StackPredicate((java.util.function.Predicate<ItemStack>) s ->
                        s.getItem() == net.minecraft.world.item.Items.WATER_BUCKET
                                || s.getItem() == net.minecraft.world.item.Items.LAVA_BUCKET
                                || s.getItem() == net.minecraft.world.item.Items.MILK_BUCKET));
                MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 无法解析汤底代表物品，回退为任意汤底桶判定，配方={}", recipe.getId());
            }
        }
        return out;
    }

    /** 探针：该谓词是否接受 ItemStack。物品谓词对任意探针只返回 true/false；汤底 ISoupBase 谓词会抛 ClassCastException。 */
    private static boolean isItemStackPredicate(StackPredicate p) {
        try {
            p.test(new ItemStack(net.minecraft.world.item.Items.STONE));
            return true;
        } catch (ClassCastException e) {
            return false;
        } catch (Throwable t) {
            return true;
        }
    }

    /** 反射森罗汤锅配方 soupBase()，返回等价的汤底桶物品谓词；非汤锅或解析失败返回 null。 */
    private static StackPredicate toSoupBaseItemPredicate(Recipe recipe) {
        try {
            java.lang.reflect.Method soupBaseMethod = recipe.getClass().getMethod("soupBase");
            Object rl = soupBaseMethod.invoke(recipe);
            if (rl instanceof ResourceLocation loc) {
                Class<?> soupBaseManager = Class.forName("com.github.ysbbbbbb.kaleidoscopecookery.crafting.soupbase.SoupBaseManager");
                Object base = soupBaseManager.getMethod("getSoupBase", ResourceLocation.class).invoke(null, loc);
                if (base != null) {
                    Object display = base.getClass().getMethod("getDisplayStack").invoke(base);
                    if (display instanceof ItemStack ds && !ds.isEmpty()) {
                        return new StackPredicate(ds.copy());
                    }
                }
            }
        } catch (NoSuchMethodException | ClassNotFoundException e) {
            // 非森罗汤锅配方，正常
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 解析汤底代表物品出错", t);
        }
        return null;
    }

    private static int countCooksByGreedy(List<StackPredicate> required, Map<Item, Integer> availableIn, int maxCount) {
        return countCooksByGreedy(required, availableIn, maxCount, null);
    }

    /**
     * 逐锅贪心扣减：一口锅让需求列表中每个谓词各匹配 1 个物品（同一物品实例不重复分配），
     * 能连续满足几口锅即返回几。天然正确处理替代原料（鸡蛋/海龟蛋同谓词）、碗×N、汤底、油脂。
     * firstFailedOut（长度1，可为null）用于回传"第一口锅就卡住"的那个需求谓词，供缺料命名同源反查。
     */
    private static int countCooksByGreedy(List<StackPredicate> required, Map<Item, Integer> availableIn, int maxCount, StackPredicate[] firstFailedOut) {
        if (required.isEmpty()) return maxCount;
        Map<Item, Integer> counts = new HashMap<>(availableIn);
        int made = 0;
        while (made < maxCount) {
            for (StackPredicate predicate : required) {
                Item take = null;
                for (Map.Entry<Item, Integer> e : counts.entrySet()) {
                    if (e.getValue() != null && e.getValue() > 0 && predicate.test(new ItemStack(e.getKey(), 1))) {
                        take = e.getKey();
                        break;
                    }
                }
                if (take == null) {
                    if (firstFailedOut != null) firstFailedOut[0] = predicate;
                    return made;
                }
                counts.merge(take, -1, Integer::sum);
            }
            made++;
        }
        return made;
    }

    // ====== 存储附属(maid_restaurant_storage)流体兜底：空桶 + 附近流体存储 即视为汤底桶可满足 ======
    private static Boolean storageModLoadedCache = null;
    private static java.lang.reflect.Method rsHasEnoughFluidMethod = null;
    private static boolean rsReflectionResolved = false;
    private static final Map<String, Boolean> fluidAvailableCache = new HashMap<>();
    private static final Map<String, Long> fluidAvailableTick = new HashMap<>();

    /**
     * 仅当安装了"女仆餐厅：存储"附属时启用：若该厨师背包有空桶、操作台附近流体存储里有足量水/岩浆，
     * 就把对应满桶虚拟计入她的可用材料，让需要汤底的汤锅任务能发布；真正的接水由存储附属在运行时完成。
     * 未安装存储附属时本方法什么都不做（维持原版必须已有现成桶的行为）。奶等生物制品不是流体，不在此处理。
     */
    private static void applyStorageFluidBonus(ServerLevel level, BlockPos center, List<StackPredicate> required,
                                               IItemHandler maidInv, Map<Item, Integer> available) {
        try {
            if (storageModLoadedCache == null) {
                storageModLoadedCache = net.minecraftforge.fml.ModList.get().isLoaded("maid_restaurant_storage");
            }
            if (!storageModLoadedCache || maidInv == null) return;

            if (!rsReflectionResolved) {
                rsReflectionResolved = true;
                try {
                    // 直接复用存储附属的 public static hasEnoughFluid(Level,BlockPos,FluidStack,int)
                    Class<?> storages = Class.forName("com.example.maidrestaurant.rscompat.fluid.MaidFluidStorages");
                    rsHasEnoughFluidMethod = storages.getMethod("hasEnoughFluid",
                            Level.class, BlockPos.class, net.minecraftforge.fluids.FluidStack.class, int.class);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 存储附属流体API不可用", t);
                }
            }
            if (rsHasEnoughFluidMethod == null) return;

            ItemStack waterBucket = new ItemStack(net.minecraft.world.item.Items.WATER_BUCKET);
            ItemStack lavaBucket = new ItemStack(net.minecraft.world.item.Items.LAVA_BUCKET);
            ItemStack emptyBucket = new ItemStack(net.minecraft.world.item.Items.BUCKET);

            for (StackPredicate predicate : required) {
                // 已有现成满桶能满足该需求，无需虚拟
                if (predicateMatchedByAvailable(predicate, available)) continue;

                net.minecraft.world.level.material.Fluid fluid = null;
                Item filled = null;
                // 与存储附属 findSampleForPredicate 同一组候选：谓词接受满桶但不接受空桶，才认定它是"要一桶该流体"
                if (predicate.test(waterBucket) && !predicate.test(emptyBucket)) {
                    fluid = net.minecraft.world.level.material.Fluids.WATER;
                    filled = net.minecraft.world.item.Items.WATER_BUCKET;
                } else if (predicate.test(lavaBucket) && !predicate.test(emptyBucket)) {
                    fluid = net.minecraft.world.level.material.Fluids.LAVA;
                    filled = net.minecraft.world.item.Items.LAVA_BUCKET;
                }
                if (fluid == null) continue;
                int amount = net.minecraftforge.fluids.FluidType.BUCKET_VOLUME;

                // 厨师背包必须有空桶（她要拿去流体存储接）
                if (!maidHasItem(maidInv, net.minecraft.world.item.Items.BUCKET)) continue;
                // 操作台附近流体存储必须有足量对应流体
                if (!nearbyFluidAvailable(level, center, fluid, amount)) continue;

                // 虚拟计入1个满桶（一口锅一个汤底；空桶接完会返还，可反复使用）
                available.merge(filled, 1, Integer::sum);
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 存储附属流体兜底判断出错", t);
        }
    }

    private static boolean predicateMatchedByAvailable(StackPredicate predicate, Map<Item, Integer> available) {
        for (Map.Entry<Item, Integer> e : available.entrySet()) {
            if (e.getValue() != null && e.getValue() > 0 && predicate.test(new ItemStack(e.getKey(), 1))) {
                return true;
            }
        }
        return false;
    }

    private static boolean maidHasItem(IItemHandler inv, net.minecraft.world.item.Item item) {
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack s = inv.getStackInSlot(i);
            if (!s.isEmpty() && s.getItem() == item) return true;
        }
        return false;
    }

    /** 附近是否有流体存储含足量指定流体；结果按(维度,中心,流体)缓存20tick，避免每个食物重复扫描。 */
    private static boolean nearbyFluidAvailable(ServerLevel level, BlockPos center,
                                                net.minecraft.world.level.material.Fluid fluid, int amount) {
        try {
            String key = level.dimension().location() + "|" + center.asLong() + "|" + (fluid == net.minecraft.world.level.material.Fluids.WATER ? "w" : "l");
            long now = level.getGameTime();
            Long cachedTick = fluidAvailableTick.get(key);
            if (cachedTick != null && now - cachedTick < 20L) {
                return Boolean.TRUE.equals(fluidAvailableCache.get(key));
            }
            boolean found = false;
            int range = BusinessConfig.dishScanRange;
            net.minecraftforge.fluids.FluidStack probe = new net.minecraftforge.fluids.FluidStack(fluid, amount);
            for (BlockPos check : BlockPos.betweenClosed(center.offset(-range, -4, -range), center.offset(range, 4, range))) {
                Object ok = rsHasEnoughFluidMethod.invoke(null, level, check.immutable(), probe, amount);
                if (Boolean.TRUE.equals(ok)) {
                    found = true;
                    break;
                }
            }
            fluidAvailableTick.put(key, now);
            fluidAvailableCache.put(key, found);
            return found;
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 构建缺料命名候选（与本体谓词靠 StackPredicate.test 反查对应，不参与能否制作的判定）。
     * 覆盖：普通食材、森罗 carrier() 碗/盘、农夫乐事 getOutputContainer() 容器、森罗 soupBase() 汤底桶、油脂。
     */
    private static void buildNamedCandidates(Recipe recipe, List<Item> items, List<String> labels) {
        java.util.Set<Item> dedup = new java.util.HashSet<>();
        // 普通食材（含替代原料，取第一种命名）
        for (Object ingObj : recipe.getIngredients()) {
            if (ingObj instanceof Ingredient ing && !ing.isEmpty() && ing.getItems().length > 0) {
                ItemStack first = ing.getItems()[0];
                if (!first.isEmpty() && dedup.add(first.getItem())) {
                    items.add(first.getItem());
                    labels.add(first.getHoverName().getString());
                }
            }
        }
        // 森罗物语 carrier()：碗/盘（Ingredient 或 ItemStack）
        try {
            java.lang.reflect.Method carrierMethod = recipe.getClass().getMethod("carrier");
            Object carrier = carrierMethod.invoke(recipe);
            if (carrier instanceof Ingredient cIng && !cIng.isEmpty() && cIng.getItems().length > 0) {
                ItemStack first = cIng.getItems()[0];
                if (!first.isEmpty() && dedup.add(first.getItem())) {
                    items.add(first.getItem());
                    labels.add(first.getHoverName().getString());
                }
            } else if (carrier instanceof ItemStack cStack && !cStack.isEmpty() && dedup.add(cStack.getItem())) {
                items.add(cStack.getItem());
                labels.add(cStack.getHoverName().getString());
            }
        } catch (NoSuchMethodException ignore) {
            // 该配方无 carrier()，正常
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 解析carrier命名出错", t);
        }
        // 农夫乐事厨锅 getOutputContainer()
        try {
            java.lang.reflect.Method outMethod = recipe.getClass().getMethod("getOutputContainer");
            Object out = outMethod.invoke(recipe);
            if (out instanceof ItemStack outStack && !outStack.isEmpty() && dedup.add(outStack.getItem())) {
                items.add(outStack.getItem());
                labels.add(outStack.getHoverName().getString());
            }
        } catch (NoSuchMethodException ignore) {
            // 非农夫乐事厨锅配方，正常
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 解析输出容器命名出错", t);
        }
        // 森罗物语汤锅汤底 soupBase() -> SoupBaseManager.getSoupBase(...).getDisplayStack()
        try {
            java.lang.reflect.Method soupBaseMethod = recipe.getClass().getMethod("soupBase");
            Object soupRl = soupBaseMethod.invoke(recipe);
            if (soupRl instanceof ResourceLocation rl) {
                Class<?> soupBaseManager = Class.forName("com.github.ysbbbbbb.kaleidoscopecookery.crafting.soupbase.SoupBaseManager");
                java.lang.reflect.Method getSoupBase = soupBaseManager.getMethod("getSoupBase", ResourceLocation.class);
                Object soupBase = getSoupBase.invoke(null, rl);
                if (soupBase != null) {
                    Object display = soupBase.getClass().getMethod("getDisplayStack").invoke(soupBase);
                    if (display instanceof ItemStack dStack && !dStack.isEmpty() && dedup.add(dStack.getItem())) {
                        items.add(dStack.getItem());
                        labels.add(dStack.getHoverName().getString());
                    }
                }
            }
        } catch (NoSuchMethodException | ClassNotFoundException ignore) {
            // 非森罗汤锅配方或无汤底管理，正常
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 解析汤底命名出错", t);
        }
        // 油脂（kaleidoscope_cookery:oil 标签内任取一种代表物品，名称固定“油脂”）
        Item oil = getOilSampleItem();
        if (oil != null && dedup.add(oil)) {
            items.add(oil);
            labels.add("油脂");
        }
    }

    private static Item cachedOilItem;
    private static boolean oilItemResolved = false;

    private static Item getOilSampleItem() {
        if (!oilItemResolved) {
            oilItemResolved = true;
            try {
                net.minecraft.tags.TagKey<Item> oilTag = net.minecraft.tags.TagKey.create(
                        net.minecraft.core.registries.Registries.ITEM,
                        new ResourceLocation("kaleidoscope_cookery", "oil"));
                for (Item it : ForgeRegistries.ITEMS) {
                    if (it.getDefaultInstance().is(oilTag)) {
                        cachedOilItem = it;
                        break;
                    }
                }
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 解析油脂标签出错", t);
            }
        }
        return cachedOilItem;
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

    /**
     * 在该打单机范围内定位一个具体厨具。
     * 厨具类型统一用 CookingDeviceStatsManager 的精确类名映射判定（Stockpot/CookingPot/Pot/Steamer 互斥），
     * 不再用简单的 contains("Pot")，避免农夫乐事厨锅(CookingPot) 与森罗炒锅(Pot) 互相误配；
     * 中心与范围也与厨具数量统计完全一致（打单机 ±dishScanRange、y±4），
     * 保证"统计说有空闲厨具"时这里一定能找到，不会再静默 continue。
     */
    private static BlockPos findCookingDevice(ServerLevel level, BlockPos machinePos, RecipeType<?> type) {
        com.mastermarisa.maid_restaurant.api.ICookTask cookTask = CookTasks.getTask(type);
        if (cookTask == null) return null;
        String taskClass = cookTask.getClass().getSimpleName();
        String neededType = CookingDeviceStatsManager.getDeviceTypeFromTaskClass(taskClass);
        int range = BusinessConfig.dishScanRange;
        // 四类锅（汤锅/厨锅/炒锅/蒸笼）：精确类名匹配 + 我们自己的占用管理
        if (neededType != null) {
            BlockPos nearest = null;
            double nearestDist = Double.MAX_VALUE;
            // 多厨师并行优化：优先选择没有被占用的厨具
            BlockPos nearestFree = null;
            double nearestFreeDist = Double.MAX_VALUE;
            for (BlockPos check : BlockPos.betweenClosed(machinePos.offset(-range, -4, -range), machinePos.offset(range, 4, range))) {
                BlockEntity be = level.getBlockEntity(check);
                if (be == null) continue;
                // 精确匹配：方块实体的厨具类型必须与任务所需类型完全一致
                String beType = CookingDeviceStatsManager.getDeviceTypeFromClassName(be.getClass().getName());
                if (beType == null || !beType.equals(neededType)) continue;
                double d = check.distSqr((Vec3i) machinePos);
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
            return nearestFree != null ? nearestFree : nearest;
        }
        // 非四类锅、但女仆餐厅已注册的设备（烘焙坊：烤箱Oven/搅拌机Blender/烤面包机Toaster/玻璃杯饮品）：
        // 类型判断完全交给该 task 自己的 isValidWorkBlock，不硬编码类名，以后女仆餐厅新增兼容设备自动支持。
        // 仍以打单机为中心、按 dishScanRange 扫描；一次只派一个任务（occupyDevice 按坐标占用）。
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos check : BlockPos.betweenClosed(machinePos.offset(-range, -4, -range), machinePos.offset(range, 4, range))) {
            try {
                if (!cookTask.isValidWorkBlock(level, null, check)) continue;
                if (TaskManager.getInstance().isDeviceOccupied(check.immutable())) continue;
                double d = check.distSqr((Vec3i) machinePos);
                if (d < bestDist) {
                    bestDist = d;
                    best = check.immutable();
                }
            } catch (Throwable ignore) {}
        }
        return best;
    }

    /** 厨具类型 -> 中文名（用于气泡提示）。 */
    private static String deviceDisplayName(String deviceType) {
        if ("Stockpot".equals(deviceType)) return "汤锅";
        if ("CookingPot".equals(deviceType)) return "厨锅";
        if ("Pot".equals(deviceType)) return "炒锅";
        if ("Steamer".equals(deviceType)) return "蒸笼";
        return "厨具";
    }

    /**
     * 注意：maxParallel应该基于总厨具数量，因为每个任务会占用一个厨具
     */
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
                try {
                    // 调用TaskSafetyUtils彻底重置女仆状态
                    TaskSafetyUtils.resetMaidState(maid);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("备菜任务清理时TaskSafetyUtils.resetMaidState失败", t);
                    MaidUtils.setOccupied(maid, false);
                }
            }
        }
    }

    private record RecipeMatch(ResourceLocation recipeId, RecipeType<?> recipeType, int resultCount) {
    }
}
