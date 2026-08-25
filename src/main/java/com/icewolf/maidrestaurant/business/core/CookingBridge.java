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

    public static void tickCooking(ServerLevel level, BusinessManager manager) {
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
            for (EntityMaid maid : level.getEntitiesOfClass(EntityMaid.class, new AABB(Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY))) {
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
                        // 如果remain已经是1或0，直接移除
                        if (req.remain > 1) {
                            req.remain = 1;
                            req.requested = 1;
                            cancelled++;
                            MaidRestaurantBusiness.LOGGER.info("  -> 女仆{}的请求{}正在烹饪，remain设为1完成当前次: id={}", maid.getName().getString(), i, req.id);
                        } else {
                            handler.removeAt(i);
                            cancelled++;
                            MaidRestaurantBusiness.LOGGER.info("  -> 已移除女仆{}的请求{}: id={}", maid.getName().getString(), i, req.id);
                        }
                    }
                }
            }
            // 2. 取消世界队列中的经营烹饪请求
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
            MaidRestaurantBusiness.LOGGER.info("取消烹饪任务完成: 共取消 {} 个与操作台 {} 相关的经营烹饪请求", cancelled, counterPos);
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
        // 如果已经有备菜任务在执行，跳过（避免任务打架）
        if (prepTasks.containsKey(counterPos)) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 备菜任务执行中，跳过烹饪");
            return;
        }
        if (CookingBridge.tryStartPrep(level, counterPos, remaining)) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 备菜任务已启动，跳过烹饪");
            return;
        }
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
                }
                manager.getActiveOrders().remove(counterPos);
            } else if (!currentOrderId.equals(activeOrderId)) {
                MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 订单已变更 (旧={}, 新={}), 取消旧烹饪任务并移除活跃订单", activeOrderId, currentOrderId);
                CookingBridge.cancelCookRequestsForCounter(level, counterPos);
                manager.getActiveOrders().remove(counterPos);
            } else {
                // 检查是否还有女仆在执行该操作台的烹饪任务
                if (CookingBridge.isAnyMaidCookingForCounter(level, counterPos)) {
                    MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 已有相同活跃订单且女仆仍在烹饪，跳过");
                    return;
                } else {
                    MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 已有相同活跃订单但无女仆在烹饪，移除活跃订单重新检查");
                    manager.getActiveOrders().remove(counterPos);
                }
            }
        }
        if (remaining.values().stream().allMatch(c -> c <= 0)) {
            MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 所有食物都已满足，跳过");
            return;
        }
        // 多厨师优化：统计可用厨师数量，限制每次最多发布的任务数量（避免发布太多导致性能问题）
        int availableCooks = 0;
        if (MaidTracker.maids != null) {
            for (EntityMaid m : MaidTracker.maids) {
                if (m != null && m.isAlive() && m.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5) <= 256.0) {
                    availableCooks++;
                }
            }
        }
        int maxTasksThisTick = Math.max(1, availableCooks);
        int tasksPosted = 0;
        MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 可用厨师{}个，本次最多发布{}个任务", availableCooks, maxTasksThisTick);

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
                int canCookCount = CookingBridge.getMaxCookCount(level, counterPos, match.recipeId, 1);
                if (canCookCount <= 0) {
                    MaidRestaurantBusiness.LOGGER.info("烹饪processCounter: 配方产出={} 连1次都做不了(食材不足), 尝试下一个配方", match.resultCount());
                    continue;
                }
                // 每次只做1次（因为原生AI不会接水，接水是储存附属添加的，每次发布新任务才会触发接水）
                int actualCookTimes = 1;
                MaidRestaurantBusiness.LOGGER.info("烹饪: 食材可做{}次，本次发布1次任务(确保储存附属接水功能每次触发)", canCookCount);
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
                // 发布新任务前检查是否已有女仆在烹饪该操作台的订单
                // 如果有，不取消旧任务、不重置女仆状态，让多个厨师可以同时工作（多厨师并行）
                // 如果没有，只取消旧任务，不重置女仆状态（避免清除大脑记忆导致女仆卡顿）
                // 女仆状态重置只在超时检查时进行（女仆真的卡住超过60秒才重置）
                boolean alreadyCooking = CookingBridge.isAnyMaidCookingForCounter(level, counterPos);
                if (alreadyCooking) {
                    MaidRestaurantBusiness.LOGGER.info("烹饪：已有女仆在烹饪该操作台订单，发布新任务不取消旧任务(多厨师并行)");
                } else {
                    MaidRestaurantBusiness.LOGGER.info("烹饪：发布新任务前先取消操作台 {} 的旧经营烹饪任务(不重置女仆状态避免卡顿)", counterPos);
                    CookingBridge.cancelCookRequestsForCounter(level, counterPos);
                }
                MaidRestaurantBusiness.LOGGER.info("烹饪：发布烹饪任务 recipeId={} itemId={} 需求={} 配方产出={} 烹饪次数={}", match.recipeId, entry.getKey(), entry.getValue(), match.resultCount(), actualCookTimes);
                RequestManager.post((ServerLevel)level, (IRequest)request, (int)0);
                manager.getActiveOrders().put(counterPos, new ActiveOrder(machinePos, counterPos, match.recipeId, nbt, foods, prestige, delivery, level.getGameTime()));
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
            EntityMaid cookMaid = MaidUtils.findCookMaid(level, counterPos, 16);
            if (cookMaid != null && (maidInv = MaidUtils.getInventory(cookMaid)) != null) {
                MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 找到厨师女仆 {}, 背包槽位数={}", cookMaid.getName().getString(), maidInv.getSlots());
                for (int slot = 0; slot < maidInv.getSlots(); ++slot) {
                    ResourceLocation itemId;
                    ItemStack stack = maidInv.getStackInSlot(slot);
                    if (stack.isEmpty() || (itemId = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null) continue;
                    available.merge(itemId.toString(), stack.getCount(), Integer::sum);
                    MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 女仆背包物品 {} x{}", itemId, stack.getCount());
                }
            } else {
                MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 未找到厨师女仆或背包为空, cookMaid={}", cookMaid != null ? cookMaid.getName().getString() : "null");
            }
            MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 最终可用食材 {}", available);
            // 统计每个物品的总需求（遍历所有Ingredient，累加匹配物品的需求）
            HashMap<String, Integer> itemTotalNeeded = new HashMap<String, Integer>();
            for (Ingredient ing : ingredients) {
                for (ItemStack match : ing.getItems()) {
                    ResourceLocation matchId = ForgeRegistries.ITEMS.getKey(match.getItem());
                    if (matchId == null) continue;
                    itemTotalNeeded.merge(matchId.toString(), 1, Integer::sum);
                }
            }
            int maxPossible = maxCount;
            int ingredientIndex = 0;
            for (Map.Entry<String, Integer> entry : itemTotalNeeded.entrySet()) {
                String itemId = entry.getKey();
                int totalNeeded = entry.getValue();
                int have = available.getOrDefault(itemId, 0).intValue();
                // 该物品能做的次数 = 可用数量 / 总需求数量
                int canMake = have / totalNeeded;
                MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 食材{} {} 可用={} 总需求={} 能做={}, 当前maxPossible={}", ingredientIndex, itemId, have, totalNeeded, canMake, maxPossible);
                maxPossible = Math.min(maxPossible, canMake);
                ingredientIndex++;
            }
            // 检查配方是否需要容器（carrier），如碗、盘子等
            // 汤锅、蒸锅等配方可能需要容器来盛食物，如果没有容器会导致烹饪任务卡死
            try {
                java.lang.reflect.Method carrierMethod = recipe.getClass().getMethod("carrier");
                if (carrierMethod != null) {
                    Object carrierResult = carrierMethod.invoke(recipe);
                    int carrierCount = 0;
                    int carrierPerCook = 1; // 每次烹饪默认需要1个容器
                    boolean needsCarrier = false; // 配方是否真的需要容器
                    if (carrierResult instanceof Ingredient carrierIngredient && !carrierIngredient.isEmpty()) {
                        needsCarrier = true;
                        for (ItemStack match : carrierIngredient.getItems()) {
                            ResourceLocation carrierId = ForgeRegistries.ITEMS.getKey(match.getItem());
                            if (carrierId != null) {
                                carrierCount += available.getOrDefault(carrierId.toString(), 0);
                            }
                        }
                    } else if (carrierResult instanceof ItemStack carrierStack && !carrierStack.isEmpty()) {
                        needsCarrier = true;
                        ResourceLocation carrierId = ForgeRegistries.ITEMS.getKey(carrierStack.getItem());
                        if (carrierId != null) {
                            carrierCount = available.getOrDefault(carrierId.toString(), 0);
                            carrierPerCook = Math.max(1, carrierStack.getCount());
                        }
                    }
                    // 只有配方真的需要容器时才检查容器数量
                    if (needsCarrier) {
                        if (carrierCount > 0) {
                            int carrierCanMake = carrierCount / carrierPerCook;
                            MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 容器可用={} 每次需={} 能做={}", carrierCount, carrierPerCook, carrierCanMake);
                            maxPossible = Math.min(maxPossible, carrierCanMake);
                        } else {
                            MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 配方需要容器但无可用容器，设置能做=0");
                            maxPossible = 0;
                        }
                    }
                }
            } catch (NoSuchMethodException e) {
                // 配方没有carrier()方法，不需要容器，正常
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("烹饪食材检测: 检查容器时出错", t);
            }
            MaidRestaurantBusiness.LOGGER.info("烹饪食材检测: 最终maxPossible={}, maxCount={}", maxPossible, maxCount);
            return Math.max(0, maxPossible);
        }
        catch (Throwable t) {
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
            if (!match || !((d = check.distSqr((Vec3i)pos)) < nearestDist)) continue;
            nearestDist = d;
            nearest = check.immutable();
        }
        return nearest;
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
