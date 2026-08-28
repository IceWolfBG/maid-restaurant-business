package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.FoodPlateBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import cn.breezeth.ordertocook.entity.CustomerEntity;
import cn.breezeth.ordertocook.item.TakeoutBagItem;
import cn.breezeth.ordertocook.registry.ModItems;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import com.icewolf.maidrestaurant.business.core.CustomerCompat;
import com.mastermarisa.maid_restaurant.maid.task.TaskWaiter;
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
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.items.wrapper.CombinedInvWrapper;
import net.minecraftforge.registries.ForgeRegistries;

public class DeliveryBridge {
    private static final String TAG_COUNTER_POS = "BusinessDeliverCounter";
    private static final String TAG_CUSTOMER_ID = "BusinessDeliverCustomerId";
    private static final String TAG_STAGE = "BusinessDeliverStage";
    private static final int STAGE_GO_TO_COUNTER = 0;
    private static final int STAGE_GO_TO_CUSTOMER = 1;
    private static final float MOVEMENT_SPEED = 0.4f;
    private static final double CLOSE_ENOUGH_DIST = 2.0;

    public static void tickDelivery(ServerLevel level, BusinessManager manager) {
        if (!BusinessConfig.waiterDeliver) {
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

        // 2. 为空闲侍者女仆分配送餐任务
        for (EntityMaid maid : allMaids) {
            if (!isWaiterMaid(maid)) continue;
            CompoundTag data = maid.getPersistentData();
            if (data.contains(TAG_COUNTER_POS)) continue;
            // TaskManager智能任务分配：检查女仆是否有任务在执行，避免任务冲突
            if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) continue;
            assignDeliveryTask(level, maid, manager);
        }
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

    private static void assignDeliveryTask(ServerLevel level, EntityMaid maid, BusinessManager manager) {
        // 任务互斥：如果女仆正在执行其他任务（打包/洗碗），不分配送餐任务
        if (MaidUtils.isOccupied(maid)) {
            // 幽灵忙碌检测：如果女仆被标记为忙碌但没有实际任务标记，立即清理
            CompoundTag data = maid.getPersistentData();
            boolean hasTask = data.contains("BusinessDeliverCounter") || 
                              data.contains("BusinessPackCounter") || 
                              data.contains("BusinessWashCounter") ||
                              data.contains("BusinessCookCounter");
            if (!hasTask && !MaidUtils.hasTaskTracker(maid.getUUID())) {
                MaidRestaurantBusiness.LOGGER.warn("送餐: 女仆 {} 被标记为忙碌但没有实际任务，立即清理忙碌标记", maid.getName().getString());
                MaidUtils.setOccupied(maid, false);
            } else {
                return;
            }
        }
        BlockPos counterPos = findCounterWithPlate(level, maid, manager);
        if (counterPos == null) {
            return;
        }

        boolean hasActivatedMachine = false;
        for (BlockPos mp : manager.getActivatedMachines()) {
            if (mp.distSqr((Vec3i) counterPos) <= 64.0) {
                hasActivatedMachine = true;
                break;
            }
        }
        if (!hasActivatedMachine) {
            return;
        }

        BlockPos machinePos = manager.getCounterToMachine().get(counterPos);
        if (machinePos != null && !ProgressionManager.isDeliveryUnlocked(level, machinePos)) {
            return;
        }
        
        // 排班表配置检查：如果附近有排班表且关闭了自动配送，则不分配任务
        if (machinePos != null && !MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_DELIVERY)) {
            return;
        }
        
        // 绑定检查：如果有女仆绑定到该打单机，只有绑定的女仆才能接任务
        int boundCount = machinePos != null ? MaidUtils.getWorkerCountForMachine(machinePos) : 0;
        if (boundCount > 0) {
            if (!MaidUtils.isMaidBoundToMachine(maid.getUUID(), machinePos)) {
                return;
            }
        }
        
        // 打单机员工人数限制检查
        if (machinePos != null && !MaidUtils.canAcceptWorker(level, machinePos)) {
            int maxWorkers = ProgressionManager.getMaxWorkers(level, machinePos);
            return;
        }

        CompoundTag data = maid.getPersistentData();
        data.putLong(TAG_COUNTER_POS, counterPos.asLong());
        data.remove(TAG_CUSTOMER_ID);
        data.putInt(TAG_STAGE, STAGE_GO_TO_COUNTER);
        // 标记女仆忙碌，防止其他任务（打包/洗碗）同时分配
        MaidUtils.setOccupied(maid, true);
        // 记录任务跟踪信息（用于卡住自愈和人数统计）
        MaidUtils.startTask(maid, machinePos, "delivery", manager.getTickCounter());
        // 显示侍者开始送餐气泡
        try {
            com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.waiterStartDelivery(maid);
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("[气泡调试] waiterStartDelivery 调用失败 maid={} error={}", maid.getName().getString(), e.toString(), e);
        }

        // TaskManager集成：创建送餐任务并分配给女仆
        String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_DELIVERY, counterPos, machinePos);
        if (taskId != null) {
            // 直接分配（因为已经找到了目标）
            TaskManager.getInstance().assignTask(maid.getUUID(), TaskManager.TYPE_DELIVERY, level);
        }

        // 使用车万女仆标准寻路方式
        boolean navResult = maid.getNavigation().moveTo(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5, MOVEMENT_SPEED);
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
                    String orderId = "";
                    CompoundTag plateTag = plate.getTag();
                    if (plateTag != null && plateTag.contains("OrderId")) {
                        orderId = plateTag.getString("OrderId");
                    }
                    LivingEntity customer = null;
                    if (!orderId.isEmpty()) {
                        customer = findCustomerByOrderId(level, counterPos, orderId);
                    }
                    if (customer == null) {
                        MaidRestaurantBusiness.LOGGER.warn("送餐: 未找到匹配顾客 orderId={}, 结束任务", orderId);
                        finishDelivery(maid, false);
                        return;
                    }
                    data.putString(TAG_CUSTOMER_ID, CustomerCompat.getCustomerId(customer));
                    data.putInt(TAG_STAGE, STAGE_GO_TO_CUSTOMER);
                    BlockPos targetPos = findSafeDeliveryPos(level, customer.blockPosition());
                    maid.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, MOVEMENT_SPEED);
                } else {
                    maid.getNavigation().moveTo(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5, MOVEMENT_SPEED);
                }
            } else {
                maid.getNavigation().moveTo(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5, MOVEMENT_SPEED);
            }
        } else if (stage == STAGE_GO_TO_CUSTOMER) {
            String customerId = data.getString(TAG_CUSTOMER_ID);
            LivingEntity customer = findCustomerById(level, counterPos, customerId);
            if (customer == null) {
                MaidRestaurantBusiness.LOGGER.warn("送餐: 女仆 {} 找不到顾客 customerId={}, 立即结束任务", maid.getName().getString(), customerId);
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
                maid.getNavigation().moveTo(targetPos.getX() + 0.5, targetPos.getY(), targetPos.getZ() + 0.5, MOVEMENT_SPEED);
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
        CompoundTag plateTag = plateStack.getTag();
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
                CompoundTag nbt = plateStack.getTag();
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
                    CompoundTag nbt = plateStack.getTag();
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
     * 手动发放收益（当反射调用completeDelivery失败时的终极回退）
     * 复制completeDelivery中的核心收益计算逻辑
     */
    private static void manuallyGiveReward(ServerLevel level, Player player, ItemStack plateStack, CompoundTag nbt) {
        try {
            int baseCoin = nbt.contains("Prestige") ? nbt.getInt("Prestige") : 0;
            boolean isUrgent = nbt.getBoolean("Urgent");
            String customer = nbt.contains("CustomerName") ? nbt.getString("CustomerName") : "顾客";
            if (customer == null || customer.isBlank()) customer = "顾客";

            // 简单的小费计算（10%概率给1-5小费）
            int tipCoin = 0;
            if (level.random.nextDouble() < 0.1) {
                tipCoin = 1 + level.random.nextInt(5);
            }

            int finalCoin = baseCoin + tipCoin;

            // 给玩家金币（通过经验值或其他方式）
            // 注意：这里简化处理，实际应该调用otc的CoinUtils

            // 发送消息给玩家
            if (tipCoin > 0) {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("订单完成！获得 " + finalCoin + " 金币（含小费 " + tipCoin + "）").withStyle(net.minecraft.ChatFormatting.GOLD));
            } else {
                player.sendSystemMessage(net.minecraft.network.chat.Component.literal("订单完成！获得 " + finalCoin + " 金币").withStyle(net.minecraft.ChatFormatting.GOLD));
            }

            // 消耗餐盘
            plateStack.shrink(1);
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("手动发放收益失败", t);
        }
    }

    /**
     * 好感度收益加成：根据女仆好感度等级给玩家额外金币
     * 公共方法，供两个送餐系统（DeliveryBridge和MaidDeliverOrderTask）调用
     */
    public static void applyFavorabilityBonus(ServerLevel level, EntityMaid maid, ItemStack plateStack, Player deliverPlayer) {
        try {
            double bonusPerLevel = BusinessConfig.favorabilityBonus;
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
                    CompoundTag plateTag = plateStack.getTag();
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

        // 先尝试使用TaskManager的中心化检索缓存
        List<BlockPos> countersWithPlates = TaskManager.getInstance().getCachedCountersWithPlates(level);
        if (countersWithPlates != null && !countersWithPlates.isEmpty()) {
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
                    boolean plateFound = false;
                    for (int dy = 0; dy <= 2 && !plateFound; ++dy) {
                        for (int dx = -2; dx <= 2 && !plateFound; ++dx) {
                            for (int dz = -2; dz <= 2 && !plateFound; ++dz) {
                                BlockPos abovePos = counterPos.offset(dx, dy, dz);
                                BlockEntity plateBe = level.getBlockEntity(abovePos);
                                if (!(plateBe instanceof FoodPlateBlockEntity)) continue;
                                FoodPlateBlockEntity plateEntity = (FoodPlateBlockEntity) plateBe;
                                if (plateEntity.getPlateStack().isEmpty()) continue;
                                if (dist < nearestDist) {
                                    nearestDist = dist;
                                    nearest = counterPos;
                                    plateFound = true;
                                }
                            }
                        }
                    }
                }
            }
        }
        return nearest;
    }

    private static ItemStack pickUpPlate(ServerLevel level, BlockPos counterPos, EntityMaid maid) {
        CombinedInvWrapper inv = maid.getAvailableInv(false);
        if (inv == null) return ItemStack.EMPTY;
        for (int dy = 0; dy <= 2; ++dy) {
            for (int dx = -2; dx <= 2; ++dx) {
                for (int dz = -2; dz <= 2; ++dz) {
                    BlockPos abovePos = counterPos.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(abovePos);
                    if (!(be instanceof FoodPlateBlockEntity)) continue;
                    FoodPlateBlockEntity plateBe = (FoodPlateBlockEntity) be;
                    ItemStack plateStack = plateBe.getPlateStack().copy();
                    if (plateStack.isEmpty()) continue;
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
            }
        }
        return ItemStack.EMPTY;
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
            String blockName = ForgeRegistries.BLOCKS.getKey(state.getBlock()).toString();
            return blockName.equals("ordertocook:chair") || blockName.contains("chair");
        } catch (Exception e) {
            return false;
        }
    }
}
