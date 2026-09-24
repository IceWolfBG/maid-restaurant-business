package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.FoodPlateBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import cn.breezeth.ordertocook.entity.CustomerEntity;
import cn.breezeth.ordertocook.item.TakeoutBagItem;
import cn.breezeth.ordertocook.registry.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper;
import com.icewolf.maidrestaurant.business.config.AutomationConfig;
import com.icewolf.maidrestaurant.business.config.GameplayConfig;
import com.icewolf.maidrestaurant.business.config.PerformanceConfig;
import com.icewolf.maidrestaurant.business.core.CustomerCompat;
import com.mastermarisa.maid_restaurant.maid.TaskWaiter;
import com.mastermarisa.maid_restaurant.utils.BehaviorUtils;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

public class DeliveryBridge {
    private static final String TAG_COUNTER_POS = "BusinessDeliverCounter";
    private static final String TAG_CUSTOMER_ID = "BusinessDeliverCustomerId";
    private static final String TAG_STAGE = "BusinessDeliverStage";
    private static final String TAG_PLATE_PICKUP_RETRY = "BusinessDeliverPlateRetry";
    private static final String TAG_IS_TAKEOUT = "BusinessDeliverIsTakeout";
    private static final String TAG_STATION_POS = "BusinessDeliverStationPos";
    private static final int STAGE_GO_TO_COUNTER = 0;
    private static final int STAGE_GO_TO_CUSTOMER = 1;
    private static final int STAGE_GO_TO_STATION = 2;
    private static final int MAX_PLATE_PICKUP_RETRY = 3;
    private static final float MOVEMENT_SPEED = 0.4f;
    private static final double CLOSE_ENOUGH_DIST = 2.0;

    
    public static void tickDelivery(ServerLevel level, BusinessManager manager) {
        if (!AutomationConfig.waiterDeliver) {
            return;
        }
        try {
            tickMaidDelivery(level, manager);
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("Delivery tick error", t);
        }
    }

    private static int debugTickCounter = 0;

    private static void tickMaidDelivery(ServerLevel level, BusinessManager manager) {
        debugTickCounter++;
        // 使用TaskManager的中心化检索缓存，避免重复获取所有女仆
        List<EntityMaid> allMaids = TaskManager.getInstance().getCachedMaids(level);
        
        // 每100tick打印一次调试信息
        if (debugTickCounter % 100 == 0) {
            int waiterCount = 0;
            for (EntityMaid maid : allMaids) {
                if (isWaiterMaid(maid)) waiterCount++;
            }
        }

        // 1. 处理正在送餐的女仆
        for (EntityMaid maid : allMaids) {
            if (!isWaiterMaid(maid)) continue;
            CompoundTag data = maid.getPersistentData();
            if (!data.contains(TAG_COUNTER_POS)) continue;
            // 确保正在送餐的女仆被标记为忙碌（防止标记丢失导致任务冲突）
            if (!MaidUtils.isOccupied(maid)) {
                MaidUtils.setOccupied(maid, true);
            }
            processDeliveringMaid(level, maid, manager);
        }
        // 2. 统一分配：收集空闲侍者，由 TaskManager 缓存统一扫待配送操作台，逐个定向分配
        //    （不再让每个女仆各自扫餐盘，避免多个侍者抢同一个餐盘）
        java.util.List<EntityMaid> idleWaiters = new java.util.ArrayList<EntityMaid>();
        for (EntityMaid maid : allMaids) {
            if (!isWaiterMaid(maid)) continue;
            CompoundTag data = maid.getPersistentData();
            if (data.contains(TAG_COUNTER_POS)) continue;
            // TaskManager智能任务分配：检查女仆是否有任务在执行，避免任务冲突
            if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) continue;
            if (MaidUtils.isOccupied(maid)) {
                // 幽灵忙碌检测：被标记忙碌但没有实际任务标记，清理后视为空闲
                boolean hasTask = data.contains(TAG_COUNTER_POS) ||
                                  data.contains("BusinessPackCounter") ||
                                  data.contains("BusinessWashCounter") ||
                                  data.contains("BusinessCookCounter");
                if (!hasTask && !MaidUtils.hasTaskTracker(maid.getUUID())) {
                    MaidRestaurantBusiness.LOGGER.warn("送餐: 女仆 {} 被标记为忙碌但没有实际任务，立即清理忙碌标记", maid.getName().getString());
                    MaidUtils.setOccupied(maid, false);
                    idleWaiters.add(maid);
                }
                continue;
            }
            idleWaiters.add(maid);
        }
        assignDeliveryTasks(level, manager, idleWaiters);
    }

    private static boolean isWaiterMaid(EntityMaid maid) {
        try {
            IMaidTask task = maid.getTask();
            if (task == null) return false;
            boolean isWaiter = task instanceof TaskWaiter;
            return isWaiter;
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("isWaiterMaid error for maid {}: {}", maid.getName().getString(), t.toString());
            return false;
        }
    }

    /**
     * 统一分配送餐任务：由 TaskManager 缓存统一收集待配送操作台，逐个定向分配给最近的空闲侍者。
     * 每个操作台只可能被建一个任务、钉给一个女仆，避免多个侍者抢同一个餐盘。
     */
    private static void assignDeliveryTasks(ServerLevel level, BusinessManager manager, java.util.List<EntityMaid> idleWaiters) {
        if (idleWaiters.isEmpty()) return;

        // 外卖袋（速递站方向）优先，其次堂食餐盘
        java.util.List<BlockPos> bagCounters = TaskManager.getInstance().getCachedCountersWithTakeoutBags(level);
        if (bagCounters != null) {
            for (BlockPos counterPos : bagCounters) {
                if (idleWaiters.isEmpty()) break;
                if (TaskManager.getInstance().hasTaskAt(counterPos, TaskManager.TYPE_DELIVERY)) continue;
                EntityMaid maid = nearestWaiter(counterPos, idleWaiters);
                if (maid == null) break;
                if (startDeliveryTo(level, maid, counterPos, manager, true)) {
                    idleWaiters.remove(maid);
                }
            }
        }
        java.util.List<BlockPos> plateCounters = TaskManager.getInstance().getCachedCountersWithPlates(level);
        if (plateCounters != null) {
            for (BlockPos counterPos : plateCounters) {
                if (idleWaiters.isEmpty()) break;
                if (TaskManager.getInstance().hasTaskAt(counterPos, TaskManager.TYPE_DELIVERY)) continue;
                EntityMaid maid = nearestWaiter(counterPos, idleWaiters);
                if (maid == null) break;
                if (startDeliveryTo(level, maid, counterPos, manager, false)) {
                    idleWaiters.remove(maid);
                }
            }
        }
    }

    private static EntityMaid nearestWaiter(BlockPos counterPos, java.util.List<EntityMaid> maids) {
        EntityMaid best = null;
        double bestDist = Double.MAX_VALUE;
        for (EntityMaid maid : maids) {
            double dist = counterPos.distSqr(maid.blockPosition());
            if (dist < bestDist) {
                bestDist = dist;
                best = maid;
            }
        }
        return best;
    }

    /**
     * 把指定操作台的配送任务定向发起给 maid。
     * 校验不通过或任务认领失败时不发寻路、并回滚忙碌标记，返回 false。
     */
    private static boolean startDeliveryTo(ServerLevel level, EntityMaid maid, BlockPos counterPos, BusinessManager manager, boolean isTakeout) {
        // 附近必须有激活打单机
        boolean hasActivatedMachine = false;
        for (BlockPos mp : manager.getActivatedMachines()) {
            if (mp.distSqr((Vec3i) counterPos) <= 64.0) {
                hasActivatedMachine = true;
                break;
            }
        }
        if (!hasActivatedMachine) return false;

        BlockPos machinePos = manager.getCounterToMachine().get(counterPos);
        if (machinePos != null && !ProgressionManager.isDeliveryUnlocked(level, machinePos)) return false;
        if (machinePos != null && !MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_DELIVERY)) return false;

        int boundCount = machinePos != null ? MaidUtils.getWorkerCountForMachine(machinePos) : 0;
        if (boundCount > 0 && !MaidUtils.isMaidBoundToMachine(maid.getUUID(), machinePos)) return false;

        if (machinePos != null && !MaidUtils.canAcceptWorker(level, machinePos)) return false;

        // 外卖袋操作台附近必须有酒狐速递站
        if (isTakeout) {
            com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity station =
                com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity.findNearbyStation(level, counterPos, 24);
            if (station == null) {
                MaidRestaurantBusiness.LOGGER.warn("外卖配送: 操作台 {} 有外卖袋但附近没有速递站", counterPos);
                return false;
            }
        }

        CompoundTag data = maid.getPersistentData();
        data.putLong(TAG_COUNTER_POS, counterPos.asLong());
        data.remove(TAG_CUSTOMER_ID);
        data.putInt(TAG_STAGE, STAGE_GO_TO_COUNTER);
        MaidUtils.setOccupied(maid, true);
        MaidUtils.startTask(maid, machinePos, "delivery", manager.getTickCounter());
        try {
            com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.waiterStartDelivery(maid);
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("waiterStartDelivery 调用失败 maid={} error={}", maid.getName().getString(), e.toString(), e);
        }

        String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_DELIVERY, counterPos, machinePos);
        if (taskId == null) {
            data.remove(TAG_COUNTER_POS);
            data.remove(TAG_STAGE);
            MaidUtils.setOccupied(maid, false);
            return false;
        }
        if (!TaskManager.getInstance().assignSpecificTask(maid.getUUID(), taskId)) {
            TaskManager.getInstance().discardTask(taskId);
            data.remove(TAG_COUNTER_POS);
            data.remove(TAG_STAGE);
            MaidUtils.setOccupied(maid, false);
            return false;
        }

        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET,
            new WalkTarget(counterPos, MOVEMENT_SPEED, 1));
        return true;
    }

    private static void processDeliveringMaid(ServerLevel level, EntityMaid maid, BusinessManager manager) {
        CompoundTag data = maid.getPersistentData();
        int stage = data.getInt(TAG_STAGE);
        BlockPos counterPos = BlockPos.of(data.getLong(TAG_COUNTER_POS));

        // TaskManager心跳更新
        TaskManager.getInstance().heartbeat(maid.getUUID(), manager.getTickCounter());

        // 详细调试日志：每20tick输出一次女仆状态
        if (maid.tickCount % 20 == 0) {
        }

        // 目标消失检测：操作台是否还存在
        if (!(level.getBlockEntity(counterPos) instanceof TakeoutBoxBlockEntity)) {
            MaidRestaurantBusiness.LOGGER.warn("送餐: 女仆 {} 的操作台 {} 已消失，立即结束任务", maid.getName().getString(), counterPos);
            finishDelivery(maid, false);
            return;
        }

        if (stage == STAGE_GO_TO_COUNTER) {
            double dist = maid.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5);
            if (dist <= CLOSE_ENOUGH_DIST * CLOSE_ENOUGH_DIST) {
                ItemStack plate = pickUpPlate(level, counterPos, maid);
                if (!plate.isEmpty()) {
                    // 成功拿到餐盘，重置重试计数
                    data.remove(TAG_PLATE_PICKUP_RETRY);
                    String orderId = "";
                    CompoundTag plateTag = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(plate);
                    if (plateTag != null && plateTag.contains("OrderId")) {
                        orderId = plateTag.getString("OrderId");
                    }
                    LivingEntity customer = null;
                    if (!orderId.isEmpty()) {
                        customer = findCustomerByOrderId(level, counterPos, orderId);
                    }
                    if (customer == null) {
                        MaidRestaurantBusiness.LOGGER.warn("送餐: 未找到匹配顾客 orderId={}, 结束任务", orderId);
                        MaidChatBubbleHelper.waiterCustomerNotFound(maid);
                        finishDelivery(maid, false);
                        return;
                    }
                    data.putString(TAG_CUSTOMER_ID, CustomerCompat.getCustomerId(customer));
                    data.putInt(TAG_STAGE, STAGE_GO_TO_CUSTOMER);
                    BlockPos targetPos = findSafeDeliveryPos(level, customer.blockPosition());
                    maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(targetPos, MOVEMENT_SPEED, 1));
                } else {
                    // 没有餐盘，尝试拿取外卖袋
                    ItemStack takeoutBag = pickUpTakeoutBag(level, counterPos, maid);
                    if (!takeoutBag.isEmpty()) {
                        // 拿到外卖袋，查找附近的酒狐速递站
                        data.putBoolean(TAG_IS_TAKEOUT, true);
                        com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity station = 
                            com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity.findNearbyStation(level, counterPos, 24);
                        if (station != null) {
                            data.putLong(TAG_STATION_POS, station.getBlockPos().asLong());
                            data.putInt(TAG_STAGE, STAGE_GO_TO_STATION);
                            BlockPos stationPos = station.getBlockPos();
                            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stationPos, MOVEMENT_SPEED, 1));
                        } else {
                            MaidRestaurantBusiness.LOGGER.warn("外卖配送: 附近没有酒狐速递站，放弃外卖配送");
                            finishDelivery(maid, false);
                            return;
                        }
                    } else {
                        // 既没有餐盘也没有外卖袋，增加重试计数
                        int retry = data.getInt(TAG_PLATE_PICKUP_RETRY);
                        retry++;
                        data.putInt(TAG_PLATE_PICKUP_RETRY, retry);
                        if (retry >= MAX_PLATE_PICKUP_RETRY) {
                            MaidRestaurantBusiness.LOGGER.warn("送餐: 女仆 {} 连续{}次拿不到餐盘/外卖袋，放弃任务", maid.getName().getString(), MAX_PLATE_PICKUP_RETRY);
                            finishDelivery(maid, false);
                            return;
                        }
                        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(counterPos, MOVEMENT_SPEED, 1));
                    }
                }
            } else {
                // 未到达操作台，重置重试计数
                data.remove(TAG_PLATE_PICKUP_RETRY);
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(counterPos, MOVEMENT_SPEED, 1));
            }
        } else if (stage == STAGE_GO_TO_CUSTOMER) {
            String customerId = data.getString(TAG_CUSTOMER_ID);
            LivingEntity customer = findCustomerById(level, counterPos, customerId);
            if (customer == null) {
                MaidRestaurantBusiness.LOGGER.warn("送餐: 女仆 {} 找不到顾客 customerId={}, 立即结束任务", maid.getName().getString(), customerId);
                MaidChatBubbleHelper.waiterCustomerNotFound(maid);
                finishDelivery(maid, false);
                return;
            }
            // 顾客消失检测：顾客是否还活着
            if (!customer.isAlive()) {
                MaidRestaurantBusiness.LOGGER.warn("送餐: 女仆 {} 的顾客 {} 已死亡，立即结束任务", maid.getName().getString(), customerId);
                finishDelivery(maid, false);
                return;
            }
            BlockPos customerPos = customer.blockPosition();
            BlockPos targetPos = findSafeDeliveryPos(level, customerPos);
            double dist = maid.distanceToSqr(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5);
            // 详细调试日志：每20tick输出一次
            if (maid.tickCount % 20 == 0) {
            }
            if (dist <= CLOSE_ENOUGH_DIST * CLOSE_ENOUGH_DIST * 2.25) {
                deliverToCustomer(level, maid, customer, counterPos, manager);
            } else {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(targetPos, MOVEMENT_SPEED, 1));
            }
        } else if (stage == STAGE_GO_TO_STATION) {
            // 外卖配送：前往酒狐速递站
            if (!data.contains(TAG_STATION_POS)) {
                MaidRestaurantBusiness.LOGGER.warn("没有速递站位置，结束任务");
                finishDelivery(maid, false);
                return;
            }
            BlockPos stationPos = BlockPos.of(data.getLong(TAG_STATION_POS));
            // 速递站消失检测
            BlockEntity stationBe = level.getBlockEntity(stationPos);
            if (!(stationBe instanceof com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity station)) {
                MaidRestaurantBusiness.LOGGER.warn("酒狐速递站 {} 已消失，结束任务", stationPos);
                finishDelivery(maid, false);
                return;
            }
            // 检查速递站是否还有空格
            if (!station.hasEmptySlot()) {
                MaidRestaurantBusiness.LOGGER.warn("酒狐速递站 {} 已满，结束任务", stationPos);
                MaidChatBubbleHelper.waiterStationFull(maid);
                  finishDelivery(maid, false);
                return;
            }
            double dist = maid.distanceToSqr(stationPos.getX() + 0.5, stationPos.getY(), stationPos.getZ() + 0.5);
            if (dist <= CLOSE_ENOUGH_DIST * CLOSE_ENOUGH_DIST) {
                // 到达速递站，放入外卖袋
                BlockPos machinePos = manager.getCounterToMachine().get(counterPos);
                boolean success = deliverToStation(level, maid, station, machinePos);
                if (success) {
                    manager.getActiveOrders().remove(counterPos);
                    manager.getCounterToMachine().remove(counterPos);
                    finishDelivery(maid, true);
                } else {
                    MaidRestaurantBusiness.LOGGER.warn("放入速递站失败，结束任务");
                    finishDelivery(maid, false);
                }
            } else {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(stationPos, MOVEMENT_SPEED, 1));
            }
        }
    }

    private static void deliverToCustomer(ServerLevel level, EntityMaid maid, LivingEntity customer, BlockPos counterPos, BusinessManager manager) {
        // TaskManager：标记开始交互
        TaskManager.getInstance().startInteraction(maid.getUUID());

        CombinedInvWrapper inv = maid.getAvailableInv(false);
        if (inv == null) {
            finishDelivery(maid, false);
            return;
        }
        int plateSlot = -1;
        ItemStack plateStack = ItemStack.EMPTY;
        for (int i = 0; i < inv.getSlots(); ++i) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !s.is((Item) OtcCompat.FOOD_PLATE())) continue;
            plateSlot = i;
            plateStack = s;
            break;
        }
        if (plateStack.isEmpty()) {
            MaidRestaurantBusiness.LOGGER.warn("送餐: 女仆背包中没有餐盘");
            finishDelivery(maid, false);
            return;
        }

        // 检查订单匹配
        String plateOrderId = "";
        CompoundTag plateTag = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(plateStack);
        if (plateTag != null && plateTag.contains("OrderId")) {
            plateOrderId = plateTag.getString("OrderId");
        }
        if (!plateOrderId.isEmpty()) {
            boolean customerMatches = CustomerCompat.hasOrderTag(customer, plateOrderId);
            if (!customerMatches) {
                LivingEntity correctCustomer = findCustomerByOrderId(level, counterPos, plateOrderId);
                if (correctCustomer != null) {
                    customer = correctCustomer;
                } else {
                    MaidRestaurantBusiness.LOGGER.warn("送餐: 没有匹配顾客 orderId={}", plateOrderId);
                    finishDelivery(maid, false);
                    return;
                }
            }
        }

        // 使用女仆主人身份交付
        Player deliverPlayer = getMaidOwner(level, maid);
        boolean isRealPlayer = deliverPlayer != null;
        if (deliverPlayer == null) {
            deliverPlayer = FakePlayerFactory.get((ServerLevel) level, (GameProfile) new GameProfile(UUID.randomUUID(), "MaidWaiter"));
            deliverPlayer.moveTo(maid.getX(), maid.getY(), maid.getZ(), maid.getYRot(), maid.getXRot());
        }

        InteractionResult result = TakeoutBagItem.trySubmitDineInFromEntityUse((ServerLevel) level, (Player) deliverPlayer, (ItemStack) plateStack.copy(), (Entity) customer);
        if (result.consumesAction()) {
            inv.extractItem(plateSlot, 1, false);
            manager.getActiveOrders().remove(counterPos);
            manager.getCounterToMachine().remove(counterPos);
            // 好感度收益加成
            applyFavorabilityBonus(level, maid, plateStack, deliverPlayer);
            // 显示送餐完成气泡
            try {
                com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.waiterDeliveryDone(maid);
            } catch (Exception e) {}
            finishDelivery(maid, true);
        } else {
            // Fallback: 通过反射直接调用completeDelivery，确保收益发放
            MaidRestaurantBusiness.LOGGER.warn("送餐: API交付失败 result={}, 尝试反射调用completeDelivery", result);
            try {
                CompoundTag nbt = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(plateStack);
                if (nbt != null) {
                    // 使用更灵活的方式查找completeDelivery方法（兼容Forge和Fabric的不同参数类型）
                    Method completeDelivery = findCompleteDeliveryMethod();
                    if (completeDelivery != null) {
                        completeDelivery.setAccessible(true);
                        completeDelivery.invoke(null, level, deliverPlayer, plateStack, nbt, (Entity) customer);
                        inv.extractItem(plateSlot, 1, false);
                        manager.getActiveOrders().remove(counterPos);
                        manager.getCounterToMachine().remove(counterPos);
                        // 好感度收益加成
                        applyFavorabilityBonus(level, maid, plateStack, deliverPlayer);
                        // 显示送餐完成气泡
                        try {
                            com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.waiterDeliveryDone(maid);
                        } catch (Exception e) {}
                        finishDelivery(maid, true);
                    } else {
                        // 终极回退：手动发放收益
                        MaidRestaurantBusiness.LOGGER.warn("送餐: 无法找到completeDelivery方法，手动发放收益");
                        manuallyGiveReward(level, deliverPlayer, plateStack, nbt);
                        inv.extractItem(plateSlot, 1, false);
                        manager.getActiveOrders().remove(counterPos);
                        manager.getCounterToMachine().remove(counterPos);
                        // 好感度收益加成
                        applyFavorabilityBonus(level, maid, plateStack, deliverPlayer);
                        finishDelivery(maid, true);
                    }
                } else {
                    MaidRestaurantBusiness.LOGGER.error("送餐: 餐盘没有NBT数据，无法交付");
                    finishDelivery(maid, false);
                }
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("送餐: 反射调用completeDelivery失败", t);
                // 终极回退：手动发放收益
                try {
                    CompoundTag nbt = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(plateStack);
                    if (nbt != null) {
                        MaidRestaurantBusiness.LOGGER.warn("送餐: 反射失败，手动发放收益");
                        manuallyGiveReward(level, deliverPlayer, plateStack, nbt);
                        inv.extractItem(plateSlot, 1, false);
                        manager.getActiveOrders().remove(counterPos);
                        manager.getCounterToMachine().remove(counterPos);
                        // 好感度收益加成
                        applyFavorabilityBonus(level, maid, plateStack, deliverPlayer);
                    }
                } catch (Throwable t2) {
                    MaidRestaurantBusiness.LOGGER.error("送餐: 手动发放收益也失败", t2);
                }
                finishDelivery(maid, false);
            }
        }
    }

    private static Player getMaidOwner(ServerLevel level, EntityMaid maid) {
        try {
            // 直接调用getOwnerUUID()（EntityMaid继承自TamableAnimal，该方法是public的）
            UUID ownerUuid = maid.getOwnerUUID();
            if (ownerUuid == null) {
                MaidRestaurantBusiness.LOGGER.warn("送餐: maid.getOwnerUUID()返回null");
                return null;
            }
            
            if (level.getServer() != null) {
                ServerPlayer realPlayer = level.getServer().getPlayerList().getPlayer(ownerUuid);
                if (realPlayer != null) {
                    return realPlayer;
                }
                // 真实玩家不在线，创建使用主人UUID的FakePlayer
                GameProfile profile = new GameProfile(ownerUuid, "MaidOwner");
                FakePlayer fakePlayer = FakePlayerFactory.get(level, profile);
                fakePlayer.moveTo(maid.getX(), maid.getY(), maid.getZ(), maid.getYRot(), maid.getXRot());
                return fakePlayer;
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("送餐: 获取主人失败", t);
        }
        return null;
    }

    private static void finishDelivery(EntityMaid maid, boolean success) {
        
        // TaskManager：完成或失败任务
        if (success) {
            TaskManager.getInstance().completeTask(maid.getUUID());
        } else {
            TaskManager.getInstance().failTask(maid.getUUID(), "delivery failed");
        }

        CompoundTag data = maid.getPersistentData();
        if (!success) {
            // 失败时清除女仆背包中的餐盘，避免卡住
            CombinedInvWrapper maidInv = maid.getAvailableInv(false);
            if (maidInv != null) {
                int removedPlates = 0;
                for (int i = 0; i < maidInv.getSlots(); ++i) {
                    ItemStack stack = maidInv.getStackInSlot(i);
                    if (stack.isEmpty() || !stack.is((Item) OtcCompat.FOOD_PLATE())) continue;
                    maidInv.extractItem(i, stack.getCount(), false);
                    removedPlates++;
                    break;
                }
                if (removedPlates > 0) {
                }
            }
        }
        
        // 防卡死：强制重置女仆状态（确保不会因为任务失败而卡住）
        TaskSafetyUtils.resetMaidState(maid);
        
        // 验证状态是否已清除
    }

    /**
     * 灵活查找completeDelivery方法（兼容Forge和Fabric的不同参数类型）
     */
    private static Method findCompleteDeliveryMethod() {
        try {
            Method[] methods = TakeoutBagItem.class.getDeclaredMethods();
            for (Method m : methods) {
                if (m.getName().equals("completeDelivery") && m.getParameterCount() == 5) {
                    return m;
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("查找completeDelivery方法失败", t);
        }
        return null;
    }

    /**
     * 手动发放收益（当反射调用 completeDelivery 失败时的终极回退）。
     * 必须真正调用 OTC 的 CoinUtils.giveCoins 发钱；若发钱 API 不可用则不发“到账”消息，避免玩家看到虚假到账提示。
     */
    private static void manuallyGiveReward(ServerLevel level, Player player, ItemStack plateStack, CompoundTag nbt) {
        try {
            int baseCoin = nbt.contains("Prestige") ? nbt.getInt("Prestige") : 0;
            boolean isUrgent = nbt.getBoolean("Urgent");
            String customer = nbt.contains("CustomerName") ? nbt.getString("CustomerName") : "";
            if (customer == null || customer.isBlank()) {
                customer = net.minecraft.network.chat.Component.translatable("keyword.ordertocook.customer").getString();
            }

            // 小费按 OTC 配置计算（反射；读取失败则不计小费，但本金 Prestige 一定发放）
            int tipCoin = computeFallbackTip(level, isUrgent);
            int finalCoin = baseCoin + tipCoin;

            boolean paid = invokeGiveCoins(player, finalCoin);
            if (paid) {
                // 记录声望（失败不影响收款）
                invokeAddPrestige(player, finalCoin);
                // 与 OTC 正常结算一致的到账提示（actionbar）
                if (tipCoin > 0) {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.ordertocook.order_completed_with_tip", finalCoin, customer, tipCoin)
                            .withStyle(net.minecraft.ChatFormatting.GOLD), false);
                } else {
                    player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                            "message.ordertocook.order_complete", finalCoin)
                            .withStyle(net.minecraft.ChatFormatting.GOLD), false);
                }
            } else {
                MaidRestaurantBusiness.LOGGER.error("送餐: 兜底发薪失败，未能向 {} 发放 {} 金币（已避免虚假到账提示）", player.getName().getString(), finalCoin);
            }

            // 消耗餐盘（与 OTC completeDelivery 末尾的 stack.shrink(1) 对齐）
            plateStack.shrink(1);
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("手动发放收益失败", t);
        }
    }

    // ===== OTC 收益 API 反射缓存（跨版本解耦，避免直接引用字段在运行期类型不一致） =====
    private static Method coinGiveMethod;
    private static boolean coinGiveResolved;
    private static Method prestigeAddMethod;
    private static boolean prestigeResolved;

    private static boolean invokeGiveCoins(Player player, int amount) {
        if (amount <= 0) return true;
        if (!coinGiveResolved) {
            coinGiveResolved = true;
            try {
                Class<?> coinUtils = Class.forName("cn.breezeth.ordertocook.util.CoinUtils");
                coinGiveMethod = coinUtils.getMethod("giveCoins", Player.class, int.class);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.warn("送餐: 未找到 OTC CoinUtils.giveCoins，兜底收益将无法发放", t);
            }
        }
        if (coinGiveMethod == null) return false;
        try {
            coinGiveMethod.invoke(null, player, amount);
            return true;
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("送餐: 调用 CoinUtils.giveCoins 失败", t);
            return false;
        }
    }

    private static void invokeAddPrestige(Player player, int amount) {
        if (amount <= 0) return;
        if (!prestigeResolved) {
            prestigeResolved = true;
            try {
                Class<?> prestigeManager = Class.forName("cn.breezeth.ordertocook.core.PrestigeManager");
                prestigeAddMethod = prestigeManager.getMethod("addPlayerPrestige", Player.class, int.class);
            } catch (Throwable ignored) {}
        }
        if (prestigeAddMethod == null) return;
        try {
            prestigeAddMethod.invoke(null, player, amount);
        } catch (Throwable ignored) {}
    }

    /** 反射读取 OTC 配置计算小费，复刻 TakeoutBagItem.completeDelivery 的普通/急单/下雨逻辑；任何失败返回 0。 */
    private static int computeFallbackTip(ServerLevel level, boolean isUrgent) {
        try {
            Class<?> cfgManager = Class.forName("cn.breezeth.ordertocook.config.ConfigManager");
            Object cfg = cfgManager.getMethod("get").invoke(null);
            if (cfg == null) return 0;
            Class<?> cfgClass = cfg.getClass();
            double chance = isUrgent ? reflectDouble(cfg, cfgClass, "tipUrgentChance") : reflectDouble(cfg, cfgClass, "tipNormalChance");
            int min = level.isRaining() ? reflectInt(cfg, cfgClass, "rainTipMin") : reflectInt(cfg, cfgClass, "tipMin");
            int max = level.isRaining() ? reflectInt(cfg, cfgClass, "rainTipMax") : reflectInt(cfg, cfgClass, "tipMax");
            if (level.random.nextDouble() < chance) {
                int span = Math.max(1, max - min + 1);
                return min + level.random.nextInt(span);
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private static double reflectDouble(Object cfg, Class<?> cfgClass, String name) throws Exception {
        java.lang.reflect.Field f = findField(cfgClass, name);
        return f != null ? f.getDouble(cfg) : 0.0;
    }

    private static int reflectInt(Object cfg, Class<?> cfgClass, String name) throws Exception {
        java.lang.reflect.Field f = findField(cfgClass, name);
        return f != null ? f.getInt(cfg) : 0;
    }

    private static java.lang.reflect.Field findField(Class<?> type, String name) {
        for (Class<?> c = type; c != null; c = c.getSuperclass()) {
            try {
                java.lang.reflect.Field f = c.getDeclaredField(name);
                f.setAccessible(true);
                return f;
            } catch (NoSuchFieldException ignored) {}
        }
        return null;
    }

    /**
     * 好感度收益加成：根据女仆好感度等级给玩家额外金币
     * 公共方法，供两个送餐系统（DeliveryBridge和MaidDeliverOrderTask）调用
     */
    public static void applyFavorabilityBonus(ServerLevel level, EntityMaid maid, ItemStack plateStack, Player deliverPlayer) {
        try {
            double bonusPerLevel = GameplayConfig.favorabilityBonus;
            // 确定给哪个玩家发额外金币：优先女仆主人，其次deliverPlayer（如果是真实玩家）
            Player bonusPlayer = getMaidOwner(level, maid);
            if (bonusPlayer == null && deliverPlayer instanceof ServerPlayer) {
                bonusPlayer = deliverPlayer;
            }
            if (bonusPerLevel > 0 && bonusPlayer != null) {
                // 获取女仆好感度等级（0-3）
                int favorability = maid.getFavorability();
                int favorLevel = favorability < 64 ? 0 : (favorability < 192 ? 1 : (favorability < 384 ? 2 : 3));
                if (favorLevel > 0) {
                    // 从餐盘NBT中获取订单基础报酬
                    CompoundTag plateTag = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(plateStack);
                    int baseCoin = plateTag != null ? plateTag.getInt("Prestige") : 0;
                    if (baseCoin > 0) {
                        // 按好感度等级概率触发小费
                        // 1级: 15%, 2级: 20%, 3级及以上: 30%
                        double triggerChance;
                        if (favorLevel >= 3) {
                            triggerChance = 0.30;
                        } else if (favorLevel == 2) {
                            triggerChance = 0.20;
                        } else {
                            triggerChance = 0.15;
                        }
                        double roll = level.getRandom().nextDouble();
                        if (roll > triggerChance) {
                            return;
                        }
                        // 向上取整，确保至少给1金币
                        int bonusCoin = (int)Math.ceil(baseCoin * favorLevel * bonusPerLevel);
                        // 最高小费不超过基础收益的300%
                        int maxBonus = baseCoin * 3;
                        if (bonusCoin > maxBonus) {
                            bonusCoin = maxBonus;
                        }
                        if (bonusCoin > 0) {
                            // 给玩家额外金币
                            Class<?> coinUtils = Class.forName("cn.breezeth.ordertocook.util.CoinUtils");
                            java.lang.reflect.Method giveCoins = coinUtils.getMethod("giveCoins", net.minecraft.world.entity.player.Player.class, int.class);
                            giveCoins.invoke(null, bonusPlayer, bonusCoin);
                            // 发送可爱的提示句子
                            String[] cuteMessages = {
                                "因为女仆的可爱，顾客多给了" + bonusCoin + "小费~",
                                "女仆的微笑暴击，额外获得" + bonusCoin + "收益！",
                                "被女仆的可爱治愈了，多付了" + bonusCoin + "~",
                                "女仆的元气满满，顾客多给了" + bonusCoin + "小费！",
                                "因为女仆的贴心服务，多拿了" + bonusCoin + "收益~"
                            };
                            String message = cuteMessages[level.getRandom().nextInt(cuteMessages.length)];
                            bonusPlayer.displayClientMessage(net.minecraft.network.chat.Component.literal(message).withStyle(net.minecraft.ChatFormatting.LIGHT_PURPLE), false);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("好感度加成: failed", t);
        }
    }

    private static BlockPos findCounterWithPlate(ServerLevel level, EntityMaid maid, BusinessManager manager) {
        BlockPos maidPos = maid.blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        if (debugTickCounter % 100 == 0) {
        }

        // 先尝试使用TaskManager的中心化检索缓存
        List<BlockPos> countersWithPlates = TaskManager.getInstance().getCachedCountersWithPlates(level);
        if (countersWithPlates != null && !countersWithPlates.isEmpty()) {
            if (debugTickCounter % 100 == 0) {
            }
            for (BlockPos counterPos : countersWithPlates) {
                double dist = counterPos.distSqr((Vec3i) maidPos);
                if (dist > 256.0) continue; // 16格范围内
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = counterPos;
                }
            }
            if (nearest != null) return nearest;
        }

        // 缓存为空或没有找到，使用原来的检索逻辑
        int chunkX = maidPos.getX() >> 4;
        int chunkZ = maidPos.getZ() >> 4;
        for (int cx = chunkX - 2; cx <= chunkX + 2; ++cx) {
            for (int cz = chunkZ - 2; cz <= chunkZ + 2; ++cz) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    BlockEntity be = chunk.getBlockEntity(pos);
                    if (!(be instanceof TakeoutBoxBlockEntity)) continue;
                    BlockPos counterPos = pos.immutable();
                    double dist = counterPos.distSqr((Vec3i) maidPos);
                    if (dist > 256.0) continue;
                    // 只检查操作台正上方一个格子（打包好的餐盘就在这里）
                    BlockPos abovePos = counterPos.above();
                    BlockEntity plateBe = level.getBlockEntity(abovePos);
                    if (!(plateBe instanceof FoodPlateBlockEntity)) continue;
                    FoodPlateBlockEntity plateEntity = (FoodPlateBlockEntity) plateBe;
                    if (plateEntity.getPlateStack().isEmpty()) continue;
                    // 跳过顾客正在吃的餐盘，只收待配送的餐盘
                    if (plateEntity.isEatingSequenceActive()) continue;
                    if (dist < nearestDist) {
                        nearestDist = dist;
                        nearest = counterPos;
                    }
                }
            }
        }
        return nearest;
    }

    private static ItemStack pickUpPlate(ServerLevel level, BlockPos counterPos, EntityMaid maid) {
        CombinedInvWrapper inv = maid.getAvailableInv(false);
        if (inv == null) return ItemStack.EMPTY;
        // 只检查操作台正上方一个格子（打包好的餐盘就在这里）
        BlockPos abovePos = counterPos.above();
        BlockEntity be = level.getBlockEntity(abovePos);
        if (!(be instanceof FoodPlateBlockEntity)) return ItemStack.EMPTY;
        FoodPlateBlockEntity plateBe = (FoodPlateBlockEntity) be;
        ItemStack plateStack = plateBe.getPlateStack().copy();
        if (plateStack.isEmpty()) return ItemStack.EMPTY;
        // 跳过顾客正在吃的餐盘，只收待配送的餐盘
        if (plateBe.isEatingSequenceActive()) return ItemStack.EMPTY;
        ItemStack remainder = ItemHandlerHelper.insertItemStacked((IItemHandler) inv, (ItemStack) plateStack, true);
        if (!remainder.isEmpty()) {
            MaidRestaurantBusiness.LOGGER.warn("送餐: 女仆背包已满, 无法拿起餐盘");
            return ItemStack.EMPTY;
        }
        plateBe.setPlateStack(ItemStack.EMPTY);
        level.removeBlock(abovePos, false);
        ItemHandlerHelper.insertItemStacked((IItemHandler) inv, (ItemStack) plateStack, false);
        return plateStack;
    }

    private static LivingEntity findCustomerByOrderId(ServerLevel level, BlockPos counterPos, String orderId) {
        return CustomerCompat.findCustomerByOrderId(level, counterPos, orderId, 32.0);
    }

    private static LivingEntity findCustomerById(ServerLevel level, BlockPos counterPos, String customerId) {
        return CustomerCompat.findCustomerById(level, counterPos, customerId, 32.0);
    }

    private static BlockPos findSafeDeliveryPos(ServerLevel level, BlockPos customerPos) {
        if (isChairBlock(level, customerPos)) {
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    if (dx == 0 && dz == 0) continue;
                    BlockPos checkPos = customerPos.offset(dx, 0, dz);
                    if (isChairBlock(level, checkPos)) continue;
                    if (!level.getBlockState(checkPos).isAir()) continue;
                    return checkPos;
                }
            }
        }
        return customerPos;
    }

    private static boolean isChairBlock(ServerLevel level, BlockPos pos) {
        try {
            BlockState state = level.getBlockState(pos);
            String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            return blockName.equals("ordertocook:chair") || blockName.contains("chair");
        } catch (Exception e) {
            return false;
        }
    }

    // ========== 外卖配送（酒狐速递站） ==========

    /**
     * 查找操作台上的外卖袋
     */
    private static BlockPos findCounterWithTakeoutBag(ServerLevel level, EntityMaid maid, BusinessManager manager) {
        BlockPos maidPos = maid.blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;

        // 使用TaskManager的中心化缓存（每10tick更新一次），避免每个女仆重复扫描
        List<BlockPos> cachedCounters = TaskManager.getInstance().getCachedCountersWithTakeoutBags(level);
        if (cachedCounters != null && !cachedCounters.isEmpty()) {
            for (BlockPos counterPos : cachedCounters) {
                double dist = counterPos.distSqr((Vec3i) maidPos);
                if (dist > 576.0) continue;
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = counterPos;
                }
            }
            if (nearest != null) return nearest;
        }

        // 缓存为空或没有找到，使用原来的扫描逻辑
        int chunkX = maidPos.getX() >> 4;
        int chunkZ = maidPos.getZ() >> 4;
        int scannedCounters = 0;
        for (int cx = chunkX - 2; cx <= chunkX + 2; ++cx) {
            for (int cz = chunkZ - 2; cz <= chunkZ + 2; ++cz) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    BlockEntity be = chunk.getBlockEntity(pos);
                    if (!(be instanceof TakeoutBoxBlockEntity)) continue;
                    scannedCounters++;
                    BlockPos counterPos = pos.immutable();
                    double dist = counterPos.distSqr((Vec3i) maidPos);
                    if (dist > 576.0) continue;
                    if (hasTakeoutBagInCounter(level, counterPos)) {
                        if (dist < nearestDist) {
                            nearestDist = dist;
                            nearest = counterPos;
                        }
                    }
                }
            }
        }
        return nearest;
    }

    /**
     * 检查操作台是否有外卖袋（跟餐盘逻辑一样，检测TakeoutBagBlockEntity）
     */
    private static boolean hasTakeoutBagInCounter(ServerLevel level, BlockPos counterPos) {
        try {
            // 只检查操作台正上方一个格子
            BlockPos abovePos = counterPos.above();
            BlockEntity be = level.getBlockEntity(abovePos);
            if (be == null) return false;
            String className = be.getClass().getSimpleName();
            if (!className.contains("Takeout") && !className.contains("Bag")) return false;
            
            ItemStack bagStack = getTakeoutBagStack(be);
            return !bagStack.isEmpty() && isTakeoutBag(bagStack);
        } catch (Throwable t) {
            return false;
        }
    }

    /**
     * 检查物品是否是外卖袋
     */
    private static boolean isTakeoutBag(ItemStack stack) {
        if (stack.isEmpty()) return false;
        try {
            return stack.getItem() instanceof TakeoutBagItem;
        } catch (Throwable t) {
            String itemName = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
            return itemName.contains("takeout") || itemName.contains("bag");
        }
    }

    private static ItemStack getTakeoutBagStack(BlockEntity be) {
        try {
            for (java.lang.reflect.Method m : be.getClass().getMethods()) {
                if (m.getName().equals("getBagStack") || m.getName().equals("getTakeoutStack") || 
                    m.getName().equals("getItemStack") || m.getName().equals("getPlateStack")) {
                    Object result = m.invoke(be);
                    if (result instanceof ItemStack) {
                        return (ItemStack) result;
                    }
                }
            }
        } catch (Exception e) {
            // 忽略错误
        }
        return ItemStack.EMPTY;
    }

    /**
     * 从操作台拿取外卖袋（跟餐盘逻辑一样，直接操作TakeoutBagBlockEntity）
     */
    private static ItemStack pickUpTakeoutBag(ServerLevel level, BlockPos counterPos, EntityMaid maid) {
        CombinedInvWrapper inv = maid.getAvailableInv(false);
        if (inv == null) return ItemStack.EMPTY;
        try {
            for (int dy = 0; dy <= 2; ++dy) {
                for (int dx = -2; dx <= 2; ++dx) {
                    for (int dz = -2; dz <= 2; ++dz) {
                        BlockPos abovePos = counterPos.offset(dx, dy, dz);
                        BlockEntity be = level.getBlockEntity(abovePos);
                        if (!(be instanceof cn.breezeth.ordertocook.block.entity.TakeoutBagBlockEntity bagBe)) continue;
                        
                        ItemStack bagStack = bagBe.getBagStack().copy();
                        if (bagStack.isEmpty()) continue;
                        
                        ItemStack remainder = ItemHandlerHelper.insertItemStacked((IItemHandler) inv, bagStack, true);
                        if (!remainder.isEmpty()) {
                            MaidRestaurantBusiness.LOGGER.warn("外卖配送: 女仆背包已满, 无法拿起外卖袋");
                            return ItemStack.EMPTY;
                        }
                        
                        bagBe.setBagStack(ItemStack.EMPTY);
                        level.removeBlock(abovePos, false);
                        ItemHandlerHelper.insertItemStacked((IItemHandler) inv, bagStack, false);
                        return bagStack;
                    }
                }
            }
            // 检查周围的掉落物
            for (net.minecraft.world.entity.item.ItemEntity itemEntity : level.getEntitiesOfClass(net.minecraft.world.entity.item.ItemEntity.class, new AABB(counterPos).inflate(3.0))) {
                ItemStack stack = itemEntity.getItem();
                if (isTakeoutBag(stack)) {
                    ItemStack copy = stack.copy();
                    ItemStack remainder = ItemHandlerHelper.insertItemStacked((IItemHandler) inv, copy, true);
                    if (!remainder.isEmpty()) continue;
                    itemEntity.discard();
                    ItemHandlerHelper.insertItemStacked((IItemHandler) inv, copy, false);
                    return copy;
                }
            }
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("外卖袋拿取错误: {}", e.toString());
        }
        return ItemStack.EMPTY;
    }

    /**
     * 清空外卖袋BlockEntity
     */
    private static void clearTakeoutBag(BlockEntity be, BlockPos pos, ServerLevel level) {
        try {
            for (java.lang.reflect.Method m : be.getClass().getMethods()) {
                if (m.getName().equals("setBagStack") || m.getName().equals("setTakeoutStack") || 
                    m.getName().equals("setItemStack") || m.getName().equals("setPlateStack")) {
                    m.invoke(be, ItemStack.EMPTY);
                    return;
                }
            }
            if (be instanceof net.minecraft.world.Container container) {
                for (int i = 0; i < container.getContainerSize(); i++) {
                    container.setItem(i, ItemStack.EMPTY);
                }
            }
        } catch (Exception e) {
            // 静默失败
        }
    }

    /**
     * 将外卖袋放入酒狐速递站
     */
    private static boolean deliverToStation(ServerLevel level, EntityMaid maid, 
            com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity station, BlockPos machinePos) {
        try {
            CombinedInvWrapper inv = maid.getAvailableInv(false);
            if (inv == null) {
                MaidRestaurantBusiness.LOGGER.warn("deliverToStation: 女仆背包为空");
                return false;
            }
            int bagSlot = -1;
            ItemStack bagStack = ItemStack.EMPTY;
            for (int i = 0; i < inv.getSlots(); ++i) {
                ItemStack s = inv.getStackInSlot(i);
                if (s.isEmpty()) continue;
                boolean isBag = isTakeoutBag(s);
                if (!isBag) continue;
                bagSlot = i;
                bagStack = s;
                break;
            }
            if (bagStack.isEmpty()) {
                MaidRestaurantBusiness.LOGGER.warn("deliverToStation: 女仆背包中没有外卖袋");
                return false;
            }
            java.util.UUID ownerUuid = maid.getOwnerUUID();
            boolean success = station.addDeliveryBag(bagStack.copy(), machinePos, ownerUuid);
            if (success) {
                inv.extractItem(bagSlot, 1, false);
                // 手臂摇摆动画：女仆把外卖袋放入速递站
                try {
                    maid.swing(net.minecraft.world.InteractionHand.OFF_HAND);
                } catch (Throwable t) {}
                return true;
            } else {
                MaidRestaurantBusiness.LOGGER.warn("station.addDeliveryBag失败，速递站可能已满");
                return false;
            }
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("外卖配送放入速递站错误: {}", e.toString(), e);
            return false;
        }
    }
}
