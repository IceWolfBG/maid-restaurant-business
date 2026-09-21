package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import cn.breezeth.ordertocook.core.ModConstants;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.block.OrderClipBlock;
import com.icewolf.maidrestaurant.business.block.entity.OrderClipBlockEntity;
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 厨师“取单入台”桥（重铸自动接单沉浸感的阶段④）。
 *
 * <p>取代旧 {@link OrderBridge} 把订单从打单机<b>瞬移</b>进操作台的做法，改为由厨师女仆真实寻路完成：</p>
 * <ul>
 *   <li><b>打单机订单（MACHINE）</b>：厨师先寻路到打单机，从槽内抽出 1 张订单放进自己随身背包，
 *       再寻路到空闲操作台，把订单放入操作台订单槽（槽 0）。两段寻路；订单抽出后若任务失败 / 超时，
 *       订单在女仆当前位置掉落，<b>不退回打单机</b>（玩家取出的订单也无法塞回打单机）。</li>
 *   <li><b>挂单夹订单（CLIP，侍者接待到店顾客夹上的单）</b>：厨师只寻路到空闲操作台，到达交互时
 *       直接把挂单夹上的订单传送到操作台槽 0（夹→台，不经过背包、不落地）；放台失败则回滚到原夹。</li>
 * </ul>
 *
 * <p><b>只送成品已齐的单：</b>仅当操作台 / 冰箱里已有满足订单 FoodList 的成品时才派单（复用
 * {@link OrderBridge#hasReadyFood}）。“打单机槽 / 挂单夹 / 空闲台”这些轻量状态每 10tick 随调度检测，
 * 而“成品是否齐全”这一需要遍历操作台 + 冰箱的重检测，对每台激活打单机做 200tick（10s）冷却缓存，
 * 在打单机刷新订单、侍者夹单、厨师取单成功时立即失效；缓存只用于产生候选，真正放台入槽 0 前
 * 还会对具体订单实时复核一次。</p>
 *
 * <p>每台激活打单机每轮最多派一个取单任务；订单按 orderId（打单机）或挂单夹坐标（夹）锁定，
 * 防止多女仆 / 多轮重复处理。堂食打单机订单放台成功后才生成顾客（沿用旧逻辑）；挂单夹上的
 * walk-in 订单顾客在接待阶段已存在，不再生成。全部服务端权威、按激活打单机隔离、局部扫描。</p>
 */
public class OrderFetchBridge {

    // 女仆 PersistentData 键
    private static final String F_MACHINE = "BusinessFetchMachine";
    private static final String F_COUNTER = "BusinessFetchCounter";
    private static final String F_SOURCE = "BusinessFetchSource";
    private static final String F_STAGE = "BusinessFetchStage";
    private static final String F_SLOT = "BusinessFetchSlot";
    private static final String F_CLIP = "BusinessFetchClip";
    private static final String F_ORDER_ID = "BusinessFetchOrderId";
    private static final String F_DELIVERY = "BusinessFetchDelivery";
    private static final String F_START = "BusinessFetchStart";
    private static final String F_WAIT_SINCE = "BusinessFetchWaitSince";
    private static final String F_LOCK = "BusinessFetchLock";

    private static final byte SRC_MACHINE = 0;
    private static final byte SRC_CLIP = 1;

    private static final int STAGE_TO_SOURCE = 0;
    private static final int STAGE_TO_COUNTER = 1;

    private static final float MOVEMENT_SPEED = 0.4f;
    private static final double CLOSE_ENOUGH_DIST = 2.5;

    // 成品就绪重检测冷却（10s）
    private static final long READY_CACHE_INTERVAL = 200L;
    // 到达操作台后等待空台 / 成品的上限（10s）
    private static final long WAIT_TIMEOUT_TICKS = 200L;
    // 整个取单任务总时长保底（30s）
    private static final long TOTAL_TIMEOUT_TICKS = 600L;

    /** 候选订单（打单机槽 或 挂单夹）。 */
    private static final class Candidate {
        final boolean fromClip;
        final int machineSlot;
        final long clipPos;
        final String orderId;
        final boolean delivery;
        final int prestige;
        final CompoundTag nbt;

        Candidate(boolean fromClip, int machineSlot, long clipPos, String orderId,
                  boolean delivery, int prestige, CompoundTag nbt) {
            this.fromClip = fromClip;
            this.machineSlot = machineSlot;
            this.clipPos = clipPos;
            this.orderId = orderId;
            this.delivery = delivery;
            this.prestige = prestige;
            this.nbt = nbt;
        }

        String lockKey(String dim) {
            return dim + "|" + (fromClip ? "C:" + clipPos : "M:" + orderId);
        }
    }

    // key = dim@machineLong
    private static final Map<String, List<Candidate>> readyCache = new HashMap<>();
    private static final Map<String, Long> readyCacheTick = new HashMap<>();
    // 全局进行中的订单锁定，防多女仆 / 多轮重复
    private static final Set<String> lockedKeys = ConcurrentHashMap.newKeySet();

    /** 打单机订单刷新 / 挂单夹变化 / 取单成功时调用，令该机器的成品候选缓存立即失效。 */
    public static void invalidate(ServerLevel level, BlockPos machine) {
        if (level == null || machine == null || level.isClientSide) {
            return;
        }
        String key = level.dimension().location().toString() + "@" + machine.asLong();
        readyCache.remove(key);
        readyCacheTick.remove(key);
    }

    public static void tickFetch(ServerLevel level, BusinessManager manager) {
        try {
            List<EntityMaid> allMaids = TaskManager.getInstance().getCachedMaids(level);

            // 1. 推进在途取单的厨师
            for (EntityMaid maid : allMaids) {
                if (!MaidUtils.isCookMaid(maid)) {
                    continue;
                }
                CompoundTag data = maid.getPersistentData();
                if (!data.contains(F_MACHINE)) {
                    continue;
                }
                if (!MaidUtils.isOccupied(maid)) {
                    MaidUtils.setOccupied(maid, true);
                }
                processFetchingMaid(level, maid, manager);
            }

            // 2. 为空闲厨师派单（每台激活打单机每轮最多一单）
            for (BlockPos machine : ActivationCache.getActivatedMachines(level)) {
                try {
                    dispatchForMachine(level, machine, allMaids, manager);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.error("取单入台: 机器 {} 派单异常", machine, t);
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("取单入台 tick 出错", t);
        }
    }

    private static void dispatchForMachine(ServerLevel level, BlockPos machine,
                                           List<EntityMaid> allMaids, BusinessManager manager) {
        if (!MaidUtils.isScheduleBoardEnabled(level, machine, MaidUtils.SCHED_AUTO_ACCEPT)) {
            return;
        }
        if (!ProgressionManager.isAutoOrderUnlocked(level, machine)) {
            return;
        }
        String dim = level.dimension().location().toString();

        // 该机器在途取单任务数（每机器一单）
        int inFlight = 0;
        for (EntityMaid maid : allMaids) {
            CompoundTag d = maid.getPersistentData();
            if (d.contains(F_MACHINE) && d.getLong(F_MACHINE) == machine.asLong()) {
                inFlight++;
            }
        }
        if (inFlight > 0) {
            return;
        }
        // 同时处理订单上限：台里在制订单 + 在途取单
        long activeCount = manager.getActiveOrders().values().stream()
                .filter(o -> o.machinePos.equals(machine)).count();
        if (activeCount + inFlight >= BusinessConfig.maxPendingOrders) {
            return;
        }

        List<Candidate> candidates = getOrComputeReady(level, machine);
        if (candidates.isEmpty()) {
            return;
        }
        List<BlockPos> counters = OrderBridge.scanCountersAround(level, machine);

        for (Candidate cand : candidates) {
            String lockKey = cand.lockKey(dim);
            if (lockedKeys.contains(lockKey)) {
                continue;
            }
            // 派单前实时复核成品（10s 缓存只产候选）
            if (!OrderBridge.hasReadyFood(level, machine, cand.nbt)) {
                continue;
            }
            BlockPos counter = OrderBridge.findNearestFreeCounter(level, machine, counters, manager);
            if (counter == null) {
                return; // 没有空台，这台机器本轮不必再看后续候选
            }
            EntityMaid cook = findFreeCook(level, machine, allMaids, !cand.fromClip);
            if (cook == null) {
                return; // 没有可用厨师，等下一轮
            }

            // 锁定 + 派单
            lockedKeys.add(lockKey);
            BlockPos sourcePos = cand.fromClip ? counter : machine;
            CompoundTag data = cook.getPersistentData();
            data.putLong(F_MACHINE, machine.asLong());
            data.putLong(F_COUNTER, counter.asLong());
            data.putByte(F_SOURCE, cand.fromClip ? SRC_CLIP : SRC_MACHINE);
            data.putInt(F_STAGE, cand.fromClip ? STAGE_TO_COUNTER : STAGE_TO_SOURCE);
            data.putInt(F_SLOT, cand.machineSlot);
            data.putLong(F_CLIP, cand.clipPos);
            data.putString(F_ORDER_ID, cand.orderId == null ? "" : cand.orderId);
            data.putBoolean(F_DELIVERY, cand.delivery);
            data.putLong(F_START, level.getGameTime());
            data.remove(F_WAIT_SINCE);
            data.putString(F_LOCK, lockKey);

            MaidUtils.setOccupied(cook, true);
            MaidUtils.startTask(cook, machine, "fetch_order", manager.getTickCounter());
            String taskId = TaskManager.getInstance()
                    .createTask(TaskManager.TYPE_FETCH_ORDER, sourcePos, machine);
            if (taskId != null) {
                TaskManager.getInstance().assignTask(cook.getUUID(), TaskManager.TYPE_FETCH_ORDER, level);
            }
            MaidUtils.moveToSide(cook, sourcePos, MOVEMENT_SPEED);

            return; // 每机器每轮一单
        }
    }

    /** 计算（或读缓存）某台机器“成品已齐、可立即送台”的候选订单，挂单夹 walk-in 单优先。 */
    private static List<Candidate> getOrComputeReady(ServerLevel level, BlockPos machine) {
        String key = level.dimension().location().toString() + "@" + machine.asLong();
        long now = level.getGameTime();
        Long last = readyCacheTick.get(key);
        List<Candidate> cached = readyCache.get(key);
        if (cached != null && last != null && now - last < READY_CACHE_INTERVAL) {
            return cached;
        }

        List<Candidate> list = new ArrayList<>();
        Item orderItem = OtcCompat.ORDER();

        // 一、打单机内订单（需过刷新延迟）
        if (OrderBridge.isPastAcceptDelay(level, machine) && orderItem != null
                && level.getBlockEntity(machine) instanceof OrderMachineBlockEntity mbe) {
            IItemHandler machineInv = OrderBridge.getItemHandler(mbe);
            if (machineInv != null) {
                for (int slot = 0; slot < machineInv.getSlots(); slot++) {
                    ItemStack stack = machineInv.getStackInSlot(slot);
                    if (stack.isEmpty() || !stack.is(orderItem)) {
                        continue;
                    }
                    CompoundTag nbt = stack.getTag();
                    if (nbt == null || !nbt.contains("FoodList")) {
                        continue;
                    }
                    boolean delivery = nbt.getBoolean("Delivery");
                    if (delivery && !BusinessConfig.acceptDelivery) {
                        continue;
                    }
                    if (!OrderBridge.hasReadyFood(level, machine, nbt)) {
                        continue;
                    }
                    String orderId = nbt.getString("OrderId");
                    list.add(new Candidate(false, slot, 0L, orderId, delivery,
                            nbt.getInt("Prestige"), nbt.copy()));
                }
            }
        }

        // 二、归属该机器、夹了订单的挂单夹
        for (BlockPos clipPos : TaskManager.getInstance().getCachedClipsWithOrder(level, machine)) {
            if (!(level.getBlockEntity(clipPos) instanceof OrderClipBlockEntity clipBe) || clipBe.isEmpty()) {
                continue;
            }
            ItemStack stack = clipBe.content();
            if (stack.isEmpty() || !OrderClipBlock.isOrderItem(stack)) {
                continue;
            }
            CompoundTag nbt = stack.getTag();
            if (nbt == null || !nbt.contains("FoodList")) {
                continue;
            }
            boolean delivery = nbt.getBoolean("Delivery");
            if (delivery && !BusinessConfig.acceptDelivery) {
                continue;
            }
            if (!OrderBridge.hasReadyFood(level, machine, nbt)) {
                continue;
            }
            String orderId = nbt.getString("OrderId");
            list.add(new Candidate(true, -1, clipPos.asLong(), orderId, delivery,
                    nbt.getInt("Prestige"), nbt.copy()));
        }

        // 挂单夹（到店加急）优先；PRESTIGE 模式下同类再按声望降序，否则保持稳定的扫描顺序
        list.sort((a, b) -> {
            if (a.fromClip != b.fromClip) {
                return a.fromClip ? -1 : 1;
            }
            if (BusinessConfig.priorityMode == BusinessConfig.PriorityMode.PRESTIGE) {
                return Integer.compare(b.prestige, a.prestige);
            }
            return 0;
        });

        readyCache.put(key, list);
        readyCacheTick.put(key, now);
        return list;
    }

    private static void processFetchingMaid(ServerLevel level, EntityMaid maid, BusinessManager manager) {
        CompoundTag data = maid.getPersistentData();
        TaskManager.getInstance().heartbeat(maid.getUUID(), manager.getTickCounter());

        long now = level.getGameTime();
        if (data.contains(F_START) && now - data.getLong(F_START) > TOTAL_TIMEOUT_TICKS) {
            MaidRestaurantBusiness.LOGGER.warn("取单入台: 厨师 {} 总时长超限，放弃", maid.getName().getString());
            giveUp(level, maid, data, "总时长超限");
            return;
        }

        BlockPos machine = BlockPos.of(data.getLong(F_MACHINE));
        BlockPos counter = BlockPos.of(data.getLong(F_COUNTER));
        boolean fromClip = data.getByte(F_SOURCE) == SRC_CLIP;
        int stage = data.getInt(F_STAGE);
        String orderId = data.getString(F_ORDER_ID);

        // 阶段 0（仅打单机单）：走到打单机，抽出订单进背包
        if (!fromClip && stage == STAGE_TO_SOURCE) {
            if (!(level.getBlockEntity(machine) instanceof OrderMachineBlockEntity)) {
                giveUp(level, maid, data, "打单机消失");
                return;
            }
            double d = maid.distanceToSqr(machine.getX() + 0.5, machine.getY(), machine.getZ() + 0.5);
            if (d > CLOSE_ENOUGH_DIST * CLOSE_ENOUGH_DIST) {
                MaidUtils.moveToSide(maid, machine, MOVEMENT_SPEED);
                return;
            }
            if (!takeOrderFromMachine(level, maid, machine, orderId, data)) {
                giveUp(level, maid, data, "打单机内订单已不存在");
                return;
            }
            data.putInt(F_STAGE, STAGE_TO_COUNTER);
            data.remove(F_WAIT_SINCE);
            MaidUtils.moveToSide(maid, counter, MOVEMENT_SPEED);
            return;
        }

        // 阶段 1：走到操作台，把订单放入槽 0
        // 台失效 / 槽 0 被占：优先换一台空台
        if (!isCounterFree(level, counter)) {
            BlockPos alt = OrderBridge.findNearestFreeCounter(
                    level, machine, OrderBridge.scanCountersAround(level, machine), manager);
            if (alt != null) {
                counter = alt.immutable();
                data.putLong(F_COUNTER, counter.asLong());
                data.remove(F_WAIT_SINCE);
                MaidUtils.moveToSide(maid, counter, MOVEMENT_SPEED);
                return;
            }
            if (waitOrGiveUp(level, maid, data, "空闲操作台")) {
                return;
            }
            return;
        }

        double cd = maid.distanceToSqr(counter.getX() + 0.5, counter.getY(), counter.getZ() + 0.5);
        if (cd > CLOSE_ENOUGH_DIST * CLOSE_ENOUGH_DIST) {
            MaidUtils.moveToSide(maid, counter, MOVEMENT_SPEED);
            return;
        }

        // 取得订单引用与实时 NBT（此步不移除源物品）
        ItemStack order;
        CompoundTag nbt;
        BlockPos clipPos = fromClip ? BlockPos.of(data.getLong(F_CLIP)) : null;
        if (fromClip) {
            if (!(level.getBlockEntity(clipPos) instanceof OrderClipBlockEntity clipCheck) || clipCheck.isEmpty()) {
                giveUp(level, maid, data, "挂单夹订单已不存在");
                return;
            }
            order = clipCheck.content();
            if (order.isEmpty() || !orderId.equals(order.getTag() == null ? "" : order.getTag().getString("OrderId"))) {
                giveUp(level, maid, data, "挂单夹订单不匹配");
                return;
            }
            nbt = order.getTag();
        } else {
            IItemHandler inv = MaidUtils.getInventory(maid);
            ItemStack found = findOrderInInventory(inv, orderId);
            if (found.isEmpty()) {
                // 订单已不在背包（玩家取走接手），正常结束
                finish(level, maid, true);
                return;
            }
            nbt = found.getTag();
        }
        if (nbt == null) {
            giveUp(level, maid, data, "订单NBT缺失");
            return;
        }

        // 放台前实时复核成品是否仍齐全
        if (!OrderBridge.hasReadyFood(level, machine, nbt)) {
            if (waitOrGiveUp(level, maid, data, "成品备齐")) {
                return;
            }
            return;
        }

        // 原子转移：先从源取出，再写入台槽 0，失败回滚
        ItemStack moving;
        boolean moved = false;
        if (fromClip) {
            if (!(level.getBlockEntity(clipPos) instanceof OrderClipBlockEntity clipBe)) {
                giveUp(level, maid, data, "挂单夹消失");
                return;
            }
            moving = clipBe.takeOne();
            if (moving.isEmpty()) {
                giveUp(level, maid, data, "挂单夹取单失败");
                return;
            }
            if (putOrderIntoSlot0(level, counter, moving)) {
                moved = true;
            } else if (!clipBe.storeOne(moving)) {
                // 放台失败且回滚原夹也失败，掉在夹的位置防止吞单
                Block.popResource(level, clipPos, moving);
            }
        } else {
            IItemHandler inv = MaidUtils.getInventory(maid);
            moving = extractOrderFromInventory(inv, orderId);
            if (moving.isEmpty()) {
                finish(level, maid, true); // 玩家取走接手
                return;
            }
            if (putOrderIntoSlot0(level, counter, moving)) {
                moved = true;
            } else {
                // 回滚到女仆背包，放不下则掉在台上
                ItemStack rem = ItemHandlerHelper.insertItemStacked(inv, moving, false);
                if (!rem.isEmpty()) {
                    Block.popResource(level, counter, rem);
                }
            }
        }

        if (!moved) {
            // 槽 0 刚被占：下一拍会重新找空台
            maid.getNavigation().stop();
            if (!data.contains(F_WAIT_SINCE)) {
                data.putLong(F_WAIT_SINCE, now);
            } else if (now - data.getLong(F_WAIT_SINCE) > WAIT_TIMEOUT_TICKS) {
                giveUp(level, maid, data, "操作台槽位");
                return;
            }
            return;
        }

        // 成功入台
        BlockEntity counterBe = level.getBlockEntity(counter);
        if (counterBe != null) {
            counterBe.setChanged();
            level.updateNeighbourForOutputSignal(counter, counterBe.getBlockState().getBlock());
        }
        maid.swing(InteractionHand.OFF_HAND);
        boolean delivery = data.getBoolean(F_DELIVERY);
        // 只有打单机来源的堂食单需要在这里生成顾客；挂单夹的 walk-in 顾客接待时已存在
        if (!fromClip && !delivery) {
            OrderBridge.spawnCustomerForOrder(level, machine, nbt);
        }
        invalidate(level, machine);
        finish(level, maid, true);
    }

    /** 阶段0：从打单机抽出订单放进女仆背包。 */
    private static boolean takeOrderFromMachine(ServerLevel level, EntityMaid maid,
                                                BlockPos machine, String orderId, CompoundTag data) {
        if (!(level.getBlockEntity(machine) instanceof OrderMachineBlockEntity mbe)) {
            return false;
        }
        IItemHandler machineInv = OrderBridge.getItemHandler(mbe);
        if (machineInv == null) {
            return false;
        }
        Item orderItem = OtcCompat.ORDER();
        ItemStack extracted = ItemStack.EMPTY;
        int fallbackSlot = data.getInt(F_SLOT);
        for (int slot = 0; slot < machineInv.getSlots(); slot++) {
            ItemStack stack = machineInv.getStackInSlot(slot);
            if (stack.isEmpty() || (orderItem != null && !stack.is(orderItem))) {
                continue;
            }
            CompoundTag t = stack.getTag();
            String id = t != null ? t.getString("OrderId") : "";
            if (orderId != null && !orderId.isEmpty() && !orderId.equals(id)) {
                continue;
            }
            extracted = machineInv.extractItem(slot, 1, false);
            if (!extracted.isEmpty()) {
                break;
            }
        }
        if (extracted.isEmpty() && fallbackSlot >= 0 && fallbackSlot < machineInv.getSlots()) {
            ItemStack stack = machineInv.getStackInSlot(fallbackSlot);
            if (!stack.isEmpty() && (orderItem == null || stack.is(orderItem))) {
                extracted = machineInv.extractItem(fallbackSlot, 1, false);
            }
        }
        if (extracted.isEmpty()) {
            return false;
        }
        // 与玩家在打单机 GUI 中接单完全一致：补全订单 NBT，再清空本批其余未选订单并把打单机动画复位为 IDLE。
        // 女仆直接走物品栏抽取不会触发 GUI 的 onTake；若不调用，同一批刷新的订单会被女仆连续接走，
        // 也可能出现女仆和玩家各接一张的超模情况。
        enrichAcceptedOrderNbt(level, machine, mbe, extracted);
        mbe.onOrderAccepted();
        invalidate(level, machine);
        IItemHandler maidInv = MaidUtils.getInventory(maid);
        if (maidInv == null) {
            Block.popResource(level, machine, extracted);
            return true;
        }
        ItemStack rem = ItemHandlerHelper.insertItemStacked(maidInv, extracted, false);
        if (!rem.isEmpty()) {
            Block.popResource(level, machine, rem);
        }
        return true;
    }

    /**
     * 复刻 OrderMachineScreenHandler.OrderSlot#onTake 对“被接走的那张订单”补写的 NBT：
     * 机器 ID（排行榜归属，缺失则分配）、机器坐标 / 维度、外卖配送距离、订单类型。
     */
    private static void enrichAcceptedOrderNbt(ServerLevel level, BlockPos machine,
                                               OrderMachineBlockEntity mbe, ItemStack order) {
        CompoundTag nbt = order.getTag();
        if (nbt == null) {
            nbt = new CompoundTag();
            order.setTag(nbt);
        }
        nbt.putInt(ModConstants.NBT_MACHINE_ID, mbe.ensureMachineId(level));
        nbt.putLong(ModConstants.NBT_MACHINE_POS, machine.asLong());
        nbt.putString(ModConstants.NBT_MACHINE_DIM, level.dimension().location().toString());
        if (nbt.getBoolean(ModConstants.NBT_DELIVERY)
                && nbt.contains(ModConstants.NBT_DELIVERY_POS)
                && !nbt.contains(ModConstants.NBT_DELIVERY_DIST)) {
            CompoundTag dp = nbt.getCompound(ModConstants.NBT_DELIVERY_POS);
            int dx = dp.getInt(ModConstants.NBT_X) - machine.getX();
            int dz = dp.getInt(ModConstants.NBT_Z) - machine.getZ();
            nbt.putInt(ModConstants.NBT_DELIVERY_DIST, (int) Math.round(Math.sqrt(dx * dx + dz * dz)));
        }
        if (!nbt.contains(ModConstants.NBT_ORDER_TYPE) || nbt.getInt(ModConstants.NBT_ORDER_TYPE) != 1) {
            nbt.putInt(ModConstants.NBT_ORDER_TYPE, 0);
        }
    }

    /** 反射操作台 inventory，把订单强制写入订单槽（槽 0）；槽 0 非空返回 false。 */
    private static boolean putOrderIntoSlot0(ServerLevel level, BlockPos counter, ItemStack order) {
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
                    level.getBlockEntity(counter).setChanged();
                    return true;
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("取单入台: 反射操作台 inventory 失败", t);
        }
        return false;
    }

    private static boolean isCounterFree(ServerLevel level, BlockPos counter) {
        if (!(level.getBlockEntity(counter) instanceof TakeoutBoxBlockEntity counterBe)) {
            return false;
        }
        IItemHandler inv = OrderBridge.getItemHandler(counterBe);
        if (inv == null || !inv.getStackInSlot(0).isEmpty()) {
            return false;
        }
        return level.getBlockState(counter.above()).isAir();
    }

    private static ItemStack findOrderInInventory(IItemHandler inv, String orderId) {
        if (inv == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty() || !OrderClipBlock.isOrderItem(stack)) {
                continue;
            }
            CompoundTag t = stack.getTag();
            String id = t != null ? t.getString("OrderId") : "";
            if (orderId == null || orderId.isEmpty() || orderId.equals(id)) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack extractOrderFromInventory(IItemHandler inv, String orderId) {
        if (inv == null) {
            return ItemStack.EMPTY;
        }
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty() || !OrderClipBlock.isOrderItem(stack)) {
                continue;
            }
            CompoundTag t = stack.getTag();
            String id = t != null ? t.getString("OrderId") : "";
            if (orderId == null || orderId.isEmpty() || orderId.equals(id)) {
                return inv.extractItem(i, 1, false);
            }
        }
        return ItemStack.EMPTY;
    }

    /** 找一台机器可用的空闲厨师（绑定时仅绑定者、员工上限、MACHINE 单需背包有空槽），取离机器最近者。 */
    private static EntityMaid findFreeCook(ServerLevel level, BlockPos machine,
                                          List<EntityMaid> allMaids, boolean needFreeSlot) {
        EntityMaid best = null;
        double bestDist = Double.MAX_VALUE;
        int boundCount = MaidUtils.getWorkerCountForMachine(machine);
        for (EntityMaid maid : allMaids) {
            if (!MaidUtils.isCookMaid(maid)) {
                continue;
            }
            CompoundTag d = maid.getPersistentData();
            if (d.contains(F_MACHINE)) {
                continue;
            }
            if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) {
                continue;
            }
            if (MaidUtils.isOccupied(maid)) {
                if (!MaidUtils.hasTaskTracker(maid.getUUID())) {
                    MaidUtils.setOccupied(maid, false);
                } else {
                    continue;
                }
            }
            if (boundCount > 0 && !MaidUtils.isMaidBoundToMachine(maid.getUUID(), machine)) {
                continue;
            }
            if (!MaidUtils.canAcceptWorker(level, machine)) {
                continue;
            }
            if (needFreeSlot && !hasFreeSlot(maid)) {
                continue;
            }
            double dist = maid.distanceToSqr(machine.getX() + 0.5, machine.getY(), machine.getZ() + 0.5);
            if (dist < bestDist) {
                bestDist = dist;
                best = maid;
            }
        }
        return best;
    }

    private static boolean hasFreeSlot(EntityMaid maid) {
        IItemHandler inv = MaidUtils.getInventory(maid);
        if (inv == null) {
            return false;
        }
        for (int i = 0; i < inv.getSlots(); i++) {
            if (inv.getStackInSlot(i).isEmpty()) {
                return true;
            }
        }
        return false;
    }

    /** 原地等待并重试；返回 true 表示已超时结束。 */
    private static boolean waitOrGiveUp(ServerLevel level, EntityMaid maid, CompoundTag data, String what) {
        long now = level.getGameTime();
        if (!data.contains(F_WAIT_SINCE)) {
            data.putLong(F_WAIT_SINCE, now);
            maid.getNavigation().stop();
            return false;
        }
        if (now - data.getLong(F_WAIT_SINCE) > WAIT_TIMEOUT_TICKS) {
            giveUp(level, maid, data, "等待" + what + "超时");
            return true;
        }
        maid.getNavigation().stop();
        return false;
    }

    /** 放弃任务：解锁、清状态；打单机来源且订单已取出（阶段1）时让订单在女仆当前位置掉落，不退回打单机。 */
    private static void giveUp(ServerLevel level, EntityMaid maid, CompoundTag data, String reason) {
        boolean fromClip = data.getByte(F_SOURCE) == SRC_CLIP;
        int stage = data.getInt(F_STAGE);
        String orderId = data.getString(F_ORDER_ID);
        if (!fromClip && stage >= STAGE_TO_COUNTER) {
            IItemHandler inv = MaidUtils.getInventory(maid);
            ItemStack order = extractOrderFromInventory(inv, orderId);
            if (!order.isEmpty()) {
                Block.popResource(level, maid.blockPosition(), order);
                MaidRestaurantBusiness.LOGGER.warn("取单入台: 厨师 {} 任务失败，订单 {} 掉落在当前位置（{}）",
                        maid.getName().getString(), orderId, reason);
            }
        } else {
            MaidRestaurantBusiness.LOGGER.warn("取单入台: 厨师 {} 放弃取单任务（{}）", maid.getName().getString(), reason);
        }
        finish(level, maid, false);
    }

    private static void finish(ServerLevel level, EntityMaid maid, boolean success) {
        CompoundTag data = maid.getPersistentData();
        if (data.contains(F_LOCK)) {
            lockedKeys.remove(data.getString(F_LOCK));
        }
        if (success) {
            TaskManager.getInstance().completeTask(maid.getUUID());
        } else {
            TaskManager.getInstance().failTask(maid.getUUID(), "fetch_order failed");
        }
        data.remove(F_MACHINE);
        data.remove(F_COUNTER);
        data.remove(F_SOURCE);
        data.remove(F_STAGE);
        data.remove(F_SLOT);
        data.remove(F_CLIP);
        data.remove(F_ORDER_ID);
        data.remove(F_DELIVERY);
        data.remove(F_START);
        data.remove(F_WAIT_SINCE);
        data.remove(F_LOCK);
        TaskSafetyUtils.resetMaidState(maid);
    }
}
