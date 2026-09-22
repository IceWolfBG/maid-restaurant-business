package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import cn.breezeth.ordertocook.core.OrderGenerator;
import cn.breezeth.ordertocook.core.OrderNpcManager;
import cn.breezeth.ordertocook.core.ModConstants;
import cn.breezeth.ordertocook.core.WalkInNpcManager;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.block.OrderClipBlock;
import com.icewolf.maidrestaurant.business.block.entity.OrderClipBlockEntity;
import com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;

/**
 * 到店顾客（walk-in）自动接待桥。
 *
 * <p>侍者女仆在满足条件时，主动走到“随机刷在店里、需要交互才下单”的到店顾客身边，
 * 复刻下单了(OTC)玩家右键接待的完整流程（生成订单、给顾客打 otc_order 标记、切换队伍），
 * 随后走到归属打单机的操作台：<b>有空操作台就直接把订单放上操作台槽 0（占台等菜、走烹饪主链），
 * 没有空台才把订单夹进该机器的某个空挂单夹暂存</b>（之后由取单入台桥在有空台时自动转台）。</p>
 *
 * <p><b>订单携带方式：</b>接待生成订单后直接放进女仆随身背包（不落到地上、不藏进不可见数据），
 * 这样即使女仆中途卡住 / 寻路失败，玩家也能从女仆背包里取出订单手动完成后续流程；
 * 只有真正放上操作台 / 夹进挂单夹时才从背包取出。</p>
 *
 * <p>门控：全局 BusinessConfig.autoAccept（在 BusinessManager 调度处判断）、排班表“自动接单”、
 * 进度解锁、绑定 / 员工上限、女仆背包至少有一个空槽；<b>有空操作台或空挂单夹其一</b>才接待，两者都没有则不接待。</p>
 *
 * <p>健壮性：走到台边会二次判定，目标台被占就换另一台空台（够近直接放、较远改走过去），再没有才降级夹单；
 * 操作台消失会重新寻找；暂时台夹都满会在原地等待重试（{@link #WAIT_TIMEOUT_TICKS}）；
 * 总时长超过 {@link #TOTAL_TIMEOUT_TICKS} 保底放弃，此时订单仍留在女仆背包，绝不吞单。
 * 全部服务端权威，按激活打单机 + 维度隔离，局部扫描。</p>
 */
public class WalkInGreetBridge {

    // 女仆 PersistentData 键
    private static final String TAG_NPC = "BusinessGreetNpc";
    private static final String TAG_MACHINE = "BusinessGreetMachine";
    private static final String TAG_CLIP = "BusinessGreetClip";
    private static final String TAG_COUNTER = "BusinessGreetCounter";
    private static final String TAG_STAGE = "BusinessGreetStage";
    private static final String TAG_ORDER_ID = "BusinessGreetOrderId";
    private static final String TAG_WAIT_SINCE = "BusinessGreetWaitSince";
    private static final String TAG_START = "BusinessGreetStart";

    private static final int STAGE_GO_TO_NPC = 0;
    private static final int STAGE_GO_TO_COUNTER = 1;

    private static final float MOVEMENT_SPEED = 0.4f;
    private static final double CLOSE_ENOUGH_DIST = 2.5;

    // 与挂单夹归属扫描一致的范围：水平 24、垂直 8
    private static final int RANGE_H = 24;
    private static final int RANGE_V = 8;

    // 到达操作台后暂时没有空夹 / 操作台时的等待上限（10s）
    private static final long WAIT_TIMEOUT_TICKS = 200L;
    // 整个接待任务的总时长保底（30s），超时放弃，订单留在女仆背包
    private static final long TOTAL_TIMEOUT_TICKS = 600L;

    // 落单结果
    private static final int PLACE_ON_COUNTER = 0; // 已直接放上操作台槽0
    private static final int PLACE_CLIPPED = 1;    // 没空台，已夹进空挂单夹
    private static final int PLACE_NO_ORDER = 2;   // 背包里已没有订单（玩家接手）
    private static final int PLACE_WAIT = 3;       // 台夹都满，订单已回背包，原地等待
    private static final int PLACE_REPATH = 4;     // 找到另一台较远空台，已改走过去（订单回背包）

    // 我方在顾客实体上打的“已被某位侍者认领”占位标记，防止同一顾客被重复接待
    private static final String CLAIMED_TAG = "business_greet_claimed";
    // OTC 字面量（TAG_NPC 在 OTC 中是 private，只能用字面量）
    private static final String OTC_NPC_TAG = "otc_npc";
    private static final String WALKIN_INTERACTED_TAG = "otc_walkin_interacted";
    private static final String OTC_LEVEL_PREFIX = "otc_level:";

    public static void tickGreet(ServerLevel level, BusinessManager manager) {
        try {
            List<EntityMaid> allMaids = TaskManager.getInstance().getCachedMaids(level);

            // 1. 先推进正在接待途中的侍者
            for (EntityMaid maid : allMaids) {
                if (!MaidUtils.isWaiterMaid(maid)) {
                    continue;
                }
                CompoundTag data = maid.getPersistentData();
                if (!data.contains(TAG_NPC)) {
                    continue;
                }
                if (!MaidUtils.isOccupied(maid)) {
                    MaidUtils.setOccupied(maid, true);
                }
                processGreetingMaid(level, maid, manager);
            }

            // 2. 再为空闲侍者分配接待任务
            for (EntityMaid maid : allMaids) {
                if (!MaidUtils.isWaiterMaid(maid)) {
                    continue;
                }
                CompoundTag data = maid.getPersistentData();
                if (data.contains(TAG_NPC)) {
                    continue;
                }
                if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) {
                    continue;
                }
                assignGreetTask(level, maid, manager);
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("到店接待 tick 出错", t);
        }
    }

    /** 为一台机器记录一个可接待候选。 */
    private static final class GreetCandidate {
        LivingEntity npc;
        BlockPos machine;
        BlockPos counter;
        BlockPos clip;

        GreetCandidate(LivingEntity npc, BlockPos machine, BlockPos counter, BlockPos clip) {
            this.npc = npc;
            this.machine = machine;
            this.counter = counter;
            this.clip = clip;
        }
    }

    private static void assignGreetTask(ServerLevel level, EntityMaid maid, BusinessManager manager) {
        // 幽灵忙碌检测：被标记忙碌但没有任何实际任务时立即清理，否则跳过本轮
        if (MaidUtils.isOccupied(maid)) {
            if (!MaidUtils.hasTaskTracker(maid.getUUID())) {
                MaidRestaurantBusiness.LOGGER.warn("到店接待: 女仆 {} 被标记忙碌但无实际任务，清理忙碌标记", maid.getName().getString());
                MaidUtils.setOccupied(maid, false);
            } else {
                return;
            }
        }

        // 背包必须至少有一个空槽（订单要放进女仆背包，方便卡住时玩家手动取出）
        if (!hasFreeInventorySlot(maid)) {
            return;
        }

        GreetCandidate best = null;
        double bestDist = Double.MAX_VALUE;

        BlockPos maidPos = maid.blockPosition();
        for (BlockPos machine : ActivationCache.getActivatedMachines(level)) {
            // 按打单机隔离：女仆必须在该打单机工作范围内才分配，店外（如出生点）的侍者跳过
            if (Math.abs(maidPos.getX() - machine.getX()) > RANGE_H) continue;
            if (Math.abs(maidPos.getZ() - machine.getZ()) > RANGE_H) continue;
            if (Math.abs(maidPos.getY() - machine.getY()) > RANGE_V) continue;
            // 排班表“自动接单”开关
            if (!MaidUtils.isScheduleBoardEnabled(level, machine, MaidUtils.SCHED_AUTO_ACCEPT)) {
                continue;
            }
            // 进度解锁
            if (!ProgressionManager.isAutoOrderUnlocked(level, machine)) {
                continue;
            }
            // 绑定检查：该机器若有绑定女仆，只有绑定者能接
            int boundCount = MaidUtils.getWorkerCountForMachine(machine);
            if (boundCount > 0 && !MaidUtils.isMaidBoundToMachine(maid.getUUID(), machine)) {
                continue;
            }
            // 员工上限
            if (!MaidUtils.canAcceptWorker(level, machine)) {
                continue;
            }
            // 该机器归属的操作台（按机器缓存、局部圆扫）
            List<BlockPos> countersAll = OrderBridge.scanCountersAround(level, machine);
            if (countersAll.isEmpty()) {
                continue; // 连操作台都没有，订单无处可落
            }
            // 空台优先：有空台就直接把订单放上台（占台等菜）；没有空台才退而求其次夹进空挂单夹暂存
            BlockPos freeCounter = OrderBridge.findNearestFreeCounter(level, machine, countersAll, manager);
            List<BlockPos> emptyClips = TaskManager.getInstance().getCachedEmptyClips(level, machine);
            if (freeCounter == null && emptyClips.isEmpty()) {
                continue; // 既没有空操作台、也没有空挂单夹，暂不接待
            }
            // 走到的目标台：有空台就走那台（直接放）；否则走到最近的操作台（到台边再夹单）
            BlockPos counter = freeCounter != null ? freeCounter.immutable() : nearestCounter(countersAll, machine);
            if (counter == null) {
                continue;
            }
            // 锁定一个空挂单夹作为兜底（可能没有，此时不写 TAG_CLIP）
            BlockPos clip = emptyClips.isEmpty() ? null : nearestTo(emptyClips, counter).immutable();
            // 该机器待接待的 walk-in 顾客
            List<LivingEntity> npcs = findWalkInNpcs(level, machine);
            if (npcs.isEmpty()) {
                continue;
            }
            for (LivingEntity npc : npcs) {
                double d = maid.distanceToSqr(npc);
                if (d < bestDist) {
                    bestDist = d;
                    best = new GreetCandidate(npc, machine.immutable(), counter, clip);
                }
            }
        }

        if (best == null) {
            return;
        }

        // 认领占位，防止其他侍者 / 下一轮重复接待
        best.npc.addTag(CLAIMED_TAG);

        CompoundTag data = maid.getPersistentData();
        data.putUUID(TAG_NPC, best.npc.getUUID());
        data.putLong(TAG_MACHINE, best.machine.asLong());
        if (best.clip != null) {
            data.putLong(TAG_CLIP, best.clip.asLong());
        } else {
            data.remove(TAG_CLIP);
        }
        data.putLong(TAG_COUNTER, best.counter.asLong());
        data.putInt(TAG_STAGE, STAGE_GO_TO_NPC);
        data.remove(TAG_ORDER_ID);
        data.remove(TAG_WAIT_SINCE);
        data.putLong(TAG_START, level.getGameTime());

        MaidUtils.setOccupied(maid, true);
        MaidUtils.startTask(maid, best.machine, "greet", manager.getTickCounter());

        String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_GREET, best.npc.blockPosition(), best.machine);
        if (taskId != null) {
            TaskManager.getInstance().assignTask(maid.getUUID(), TaskManager.TYPE_GREET, level);
        }

        BlockPos greetPos = greetStandPos(level, best.npc.blockPosition());
        maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(greetPos, MOVEMENT_SPEED, 1));

        // 新接待开始：清除同类去重后打个招呼（受全局 10 秒冷却约束）
        MaidChatBubbleHelper.onStateChanged(maid);
        MaidChatBubbleHelper.waiterGreetCustomer(maid);
    }

    private static void processGreetingMaid(ServerLevel level, EntityMaid maid, BusinessManager manager) {
        CompoundTag data = maid.getPersistentData();
        TaskManager.getInstance().heartbeat(maid.getUUID(), manager.getTickCounter());

        long now = level.getGameTime();
        // 总时长保底超时：放弃任务，订单留在女仆背包
        if (data.contains(TAG_START) && now - data.getLong(TAG_START) > TOTAL_TIMEOUT_TICKS) {
            MaidRestaurantBusiness.LOGGER.warn("到店接待: 女仆 {} 接待总时长超限，放弃任务（订单保留在背包）", maid.getName().getString());
            finishGreet(level, maid, false);
            return;
        }

        Entity ne = level.getEntity(data.getUUID(TAG_NPC));
        BlockPos machine = BlockPos.of(data.getLong(TAG_MACHINE));
        BlockPos counterPos = BlockPos.of(data.getLong(TAG_COUNTER));

        int stage = data.getInt(TAG_STAGE);
        if (stage == STAGE_GO_TO_NPC) {
            if (!(ne instanceof LivingEntity npc) || !npc.isAlive()) {
                MaidRestaurantBusiness.LOGGER.warn("到店接待: 女仆 {} 的顾客已消失，结束接待任务", maid.getName().getString());
                finishGreet(level, maid, false);
                return;
            }
            // 顾客已被玩家手动接待（walk-in 标记被移除、转为正式 otc_npc），放弃本任务
            if (!npc.getTags().contains(OrderNpcManager.TAG_WALKIN)
                    || npc.getTags().contains(WALKIN_INTERACTED_TAG)
                    || npc.getTags().contains(OTC_NPC_TAG)) {
                npc.removeTag(CLAIMED_TAG);
                finishGreet(level, maid, false);
                return;
            }
            BlockPos standPos = greetStandPos(level, npc.blockPosition());
            double dist = maid.distanceToSqr(standPos.getX() + 0.5, standPos.getY(), standPos.getZ() + 0.5);
            if (dist <= CLOSE_ENOUGH_DIST * CLOSE_ENOUGH_DIST * 2.25) {
                boolean greeted = doGreet(level, maid, npc, machine, data);
                if (!greeted) {
                    npc.removeTag(CLAIMED_TAG);
                    finishGreet(level, maid, false);
                    return;
                }
                npc.removeTag(CLAIMED_TAG);
                data.putInt(TAG_STAGE, STAGE_GO_TO_COUNTER);
                data.remove(TAG_WAIT_SINCE);
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(counterPos, MOVEMENT_SPEED, 1));
            } else {
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(standPos, MOVEMENT_SPEED, 1));
            }
            return;
        }

        // STAGE_GO_TO_COUNTER：此阶段不再依赖顾客实体（订单已在女仆背包）
        // 操作台消失则重新寻找，找不到进入等待
        if (!(level.getBlockEntity(counterPos) instanceof TakeoutBoxBlockEntity)) {
            BlockPos alt = nearestCounter(OrderBridge.scanCountersAround(level, machine), machine);
            if (alt != null) {
                counterPos = alt.immutable();
                data.putLong(TAG_COUNTER, counterPos.asLong());
                data.remove(TAG_WAIT_SINCE);
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(counterPos, MOVEMENT_SPEED, 1));
                return;
            }
            if (waitOrGiveUp(level, maid, data, "操作台")) {
                return;
            }
            return;
        }

        double dist = maid.distanceToSqr(counterPos.getX() + 0.5, counterPos.getY(), counterPos.getZ() + 0.5);
        if (dist > CLOSE_ENOUGH_DIST * CLOSE_ENOUGH_DIST) {
            maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(counterPos, MOVEMENT_SPEED, 1));
            return;
        }

        int placeResult = placeOrder(level, maid, machine, counterPos, data, manager);
        if (placeResult == PLACE_ON_COUNTER || placeResult == PLACE_CLIPPED) {
            // 订单已放上操作台 / 夹进挂单夹，立即令取单入台的候选缓存失效
            OrderFetchBridge.invalidate(level, machine);
            finishGreet(level, maid, true);
        } else if (placeResult == PLACE_NO_ORDER) {
            // 订单已不在背包（玩家取走接手），正常结束，不报错
            finishGreet(level, maid, true);
        } else if (placeResult == PLACE_REPATH) {
            // placeOrder 已把目标改到另一台空台并让女仆走过去，本轮不再处理
            return;
        } else {
            // 暂时既没有空操作台、也没有空挂单夹，原地等待重试；超时则放弃（订单留在背包）
            waitOrGiveUp(level, maid, data, "空操作台或空挂单夹");
        }
    }

    /** 等待重试；返回 true 表示已结束任务（超时放弃）。 */
    private static boolean waitOrGiveUp(ServerLevel level, EntityMaid maid, CompoundTag data, String what) {
        long now = level.getGameTime();
        if (!data.contains(TAG_WAIT_SINCE)) {
            data.putLong(TAG_WAIT_SINCE, now);
            return false;
        }
        if (now - data.getLong(TAG_WAIT_SINCE) > WAIT_TIMEOUT_TICKS) {
            MaidRestaurantBusiness.LOGGER.warn("到店接待: 女仆 {} 等待{}超时，放弃任务（订单保留在背包）", maid.getName().getString(), what);
            finishGreet(level, maid, false);
            return true;
        }
        // 原地待命，下一 tick（10tick 调度）重新检测
        maid.getNavigation().stop();
        return false;
    }

    /**
     * 复刻 OTC OrderToCookMod#handleWalkInNpc 的接待流程，区别只是订单不发给玩家，
     * 而是直接放进女仆随身背包，稍后夹进挂单夹。
     *
     * @return 是否成功接待（顾客仍是未交互的 walk-in、订单生成并放入背包成功）
     */
    private static boolean doGreet(ServerLevel level, EntityMaid maid, LivingEntity npc,
                                   BlockPos lockedMachine, CompoundTag data) {
        // 到达后再次严格校验，防止并发 / 玩家抢先
        if (!npc.getTags().contains(OrderNpcManager.TAG_WALKIN)
                || npc.getTags().contains(WALKIN_INTERACTED_TAG)
                || npc.getTags().contains(OTC_NPC_TAG)) {
            return false;
        }

        long gameTime = level.getGameTime();

        // 解析生成时刻 tick（缺失则用系统毫秒反算）
        long spawnTick = -1L;
        for (String tag : npc.getTags()) {
            if (tag.startsWith(OrderNpcManager.TAG_WALKIN_SPAWN_TIME)) {
                try {
                    spawnTick = Long.parseLong(tag.substring(OrderNpcManager.TAG_WALKIN_SPAWN_TIME.length()));
                } catch (NumberFormatException ignored) {
                }
                break;
            }
        }
        if (spawnTick == -1L) {
            long spawnSys = -1L;
            for (String tag : npc.getTags()) {
                if (tag.startsWith(OrderNpcManager.TAG_WALKIN_SPAWN_SYSTEM_TIME)) {
                    try {
                        spawnSys = Long.parseLong(tag.substring(OrderNpcManager.TAG_WALKIN_SPAWN_SYSTEM_TIME.length()));
                    } catch (NumberFormatException ignored) {
                    }
                    break;
                }
            }
            if (spawnSys != -1L) {
                long elapsedMs = Math.max(0L, System.currentTimeMillis() - spawnSys);
                long elapsedTicks = (elapsedMs + 49L) / 50L;
                spawnTick = Math.max(0L, gameTime - elapsedTicks);
            }
        }
        if (spawnTick == -1L) {
            spawnTick = gameTime;
        }

        // 订单等级
        int orderLevel = 1;
        for (String tag : npc.getTags()) {
            if (tag.startsWith(OTC_LEVEL_PREFIX)) {
                try {
                    orderLevel = Integer.parseInt(tag.substring(OTC_LEVEL_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                }
                break;
            }
        }
        if (orderLevel <= 0) {
            orderLevel = 1;
        }

        // 归属打单机：以顾客携带的 machine_pos tag 为准（分配时已校验与锁定机器一致）
        BlockPos machinePos = parseMachinePos(npc);
        if (machinePos == null) {
            machinePos = lockedMachine;
        }

        String customerName = npc.getCustomName() != null
                ? npc.getCustomName().getString()
                : Component.translatable("keyword.ordertocook.customer").getString();

        List<net.minecraft.world.item.Item> menuFoods =
                OrderMachineBlockEntity.getBoundBoardMenuFoods(level, machinePos);
        ItemStack order = OrderGenerator.generateWalkInOrder(
                level, npc.blockPosition(), orderLevel, spawnTick, customerName, menuFoods);
        if (order == null || order.isEmpty()) {
            MaidRestaurantBusiness.LOGGER.warn("到店接待: 为顾客 {} 生成订单失败", npc.getUUID());
            return false;
        }

        CompoundTag nbt = order.getTag();
        if (nbt == null) {
            nbt = new CompoundTag();
            order.setTag(nbt);
        }
        nbt.putLong(ModConstants.NBT_MACHINE_POS, machinePos.asLong());
        nbt.putString(ModConstants.NBT_MACHINE_DIM, level.dimension().location().toString());
        BlockEntity mbe = level.getBlockEntity(machinePos);
        if (mbe instanceof OrderMachineBlockEntity omb) {
            int machineId = omb.ensureMachineId(level);
            if (machineId > 0) {
                nbt.putInt(ModConstants.NBT_MACHINE_ID, machineId);
            }
        }
        String orderId = nbt.getString(ModConstants.NBT_ORDER_ID);
        long expiryTick = nbt.contains(ModConstants.NBT_EXPIRY_TICK) ? nbt.getLong(ModConstants.NBT_EXPIRY_TICK) : -1L;
        long expirySys = nbt.contains(ModConstants.NBT_EXPIRY_TIME) ? nbt.getLong(ModConstants.NBT_EXPIRY_TIME) : -1L;

        // 给顾客打正式订单标记（player 传 null 已验证安全，仅 dev 调试显示用到 player）
        OrderNpcManager.tagNpc(null, orderId, expiryTick, expirySys, npc);
        // 切换队伍描边颜色，与玩家右键接待后的表现一致
        WalkInNpcManager.changeToNormalTeam(level, npc);

        level.playSound(null, npc.blockPosition(), SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 1.0f, 1.0f);

        // 订单放进女仆随身背包（卡住时玩家可从背包取出手动接手）
        IItemHandler inv = MaidUtils.getInventory(maid);
        if (inv == null) {
            MaidRestaurantBusiness.LOGGER.warn("到店接待: 女仆 {} 背包不可用，订单在顾客位置掉落", maid.getName().getString());
            Block.popResource(level, npc.blockPosition(), order);
            return false;
        }
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(inv, order.copy(), false);
        if (!remainder.isEmpty()) {
            // 理论上分配前已确认有空槽，兜底防止吞单
            MaidRestaurantBusiness.LOGGER.warn("到店接待: 女仆 {} 背包放不下订单，多余订单在顾客位置掉落", maid.getName().getString());
            Block.popResource(level, npc.blockPosition(), remainder);
            return false;
        }
        data.putString(TAG_ORDER_ID, orderId);

        return true;
    }

    /**
     * 走到操作台后落单：优先把订单直接放上空闲操作台槽 0（占台等菜、由烹饪主链做菜）；
     * 当前台被占就找另一台空台（够近直接放、较远则改走过去下轮再放）；实在没有空台才夹进空挂单夹暂存；
     * 台夹都满则订单放回背包、原地等待。订单一旦从背包取出，任何失败路径都保证回背包或掉落，绝不吞单。
     */
    private static int placeOrder(ServerLevel level, EntityMaid maid, BlockPos machine,
                                  BlockPos counterPos, CompoundTag data, BusinessManager manager) {
        IItemHandler inv = MaidUtils.getInventory(maid);
        if (inv == null) {
            return PLACE_WAIT;
        }
        String wantOrderId = data.contains(TAG_ORDER_ID) ? data.getString(TAG_ORDER_ID) : null;

        // 1) 从女仆背包取出对应订单
        ItemStack order = ItemStack.EMPTY;
        for (int i = 0; i < inv.getSlots(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty() || !OrderClipBlock.isOrderItem(stack)) {
                continue;
            }
            if (wantOrderId != null) {
                CompoundTag t = stack.getTag();
                String id = t != null ? t.getString(ModConstants.NBT_ORDER_ID) : "";
                if (!wantOrderId.equals(id)) {
                    continue;
                }
            }
            order = inv.extractItem(i, 1, false);
            if (!order.isEmpty()) {
                break;
            }
        }
        if (order.isEmpty()) {
            return PLACE_NO_ORDER;
        }

        // 2) 当前走到的操作台仍空闲：直接放上台
        if (OrderBridge.isCounterFree(level, counterPos, manager)
                && OrderBridge.putOrderIntoSlot0(level, counterPos, order)) {
            afterPutOnCounter(level, maid, counterPos);
            return PLACE_ON_COUNTER;
        }

        // 3) 当前台被占，找该机器另一台空台
        BlockPos free = OrderBridge.findNearestFreeCounter(
                level, machine, OrderBridge.scanCountersAround(level, machine), manager);
        if (free != null) {
            double d = maid.distanceToSqr(free.getX() + 0.5, free.getY(), free.getZ() + 0.5);
            if (d <= CLOSE_ENOUGH_DIST * CLOSE_ENOUGH_DIST) {
                if (OrderBridge.putOrderIntoSlot0(level, free, order)) {
                    data.putLong(TAG_COUNTER, free.asLong());
                    afterPutOnCounter(level, maid, free);
                    return PLACE_ON_COUNTER;
                }
            } else {
                // 空台较远：订单先回背包，走过去下一轮再放
                returnOrderToBag(level, inv, order, counterPos);
                data.putLong(TAG_COUNTER, free.asLong());
                data.remove(TAG_WAIT_SINCE);
                maid.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(free, MOVEMENT_SPEED, 1));
                return PLACE_REPATH;
            }
        }

        // 4) 没有空台：降级夹进空挂单夹
        if (clipGivenStack(level, machine, data, order)) {
            maid.swing(InteractionHand.OFF_HAND);
            return PLACE_CLIPPED;
        }

        // 5) 台夹都满：订单放回背包，等待下一轮重试
        returnOrderToBag(level, inv, order, counterPos);
        return PLACE_WAIT;
    }

    /** 直接放台成功后的反馈：放单音效 + 摆副手。walk-in 顾客在接待阶段已生成，这里不再生成顾客。 */
    private static void afterPutOnCounter(ServerLevel level, EntityMaid maid, BlockPos counter) {
        level.playSound(null, counter, SoundEvents.BOOK_PUT, SoundSource.BLOCKS, 0.8f, 1.0f);
        maid.swing(InteractionHand.OFF_HAND);
    }

    /** 把已取出的订单夹进锁定空挂单夹（优先）或该机器任意空挂单夹；成功播放夹单音效。 */
    private static boolean clipGivenStack(ServerLevel level, BlockPos machine, CompoundTag data, ItemStack order) {
        OrderClipBlockEntity target = null;
        if (data.contains(TAG_CLIP)) {
            BlockPos lockedClip = BlockPos.of(data.getLong(TAG_CLIP));
            if (level.getBlockEntity(lockedClip) instanceof OrderClipBlockEntity locked && locked.isEmpty()) {
                target = locked;
            }
        }
        if (target == null) {
            for (BlockPos cp : TaskManager.getInstance().getCachedEmptyClips(level, machine)) {
                if (level.getBlockEntity(cp) instanceof OrderClipBlockEntity c && c.isEmpty()) {
                    target = c;
                    break;
                }
            }
        }
        if (target != null && target.storeOne(order)) {
            level.playSound(null, target.getBlockPos(), SoundEvents.ITEM_FRAME_ADD_ITEM, SoundSource.BLOCKS, 0.8f, 1.0f);
            return true;
        }
        return false;
    }

    /** 订单放回女仆背包；背包放不下则掉在指定位置，绝不吞单。 */
    private static void returnOrderToBag(ServerLevel level, IItemHandler inv, ItemStack order, BlockPos dropPos) {
        ItemStack back = ItemHandlerHelper.insertItemStacked(inv, order, false);
        if (!back.isEmpty()) {
            Block.popResource(level, dropPos, back);
        }
    }

    private static void finishGreet(ServerLevel level, EntityMaid maid, boolean success) {
        CompoundTag data = maid.getPersistentData();
        // 订单始终留在女仆背包（成功时已夹入挂单夹、从背包取出；失败时保留，玩家可取），这里不处理物品
        if (success) {
            TaskManager.getInstance().completeTask(maid.getUUID());
        } else {
            TaskManager.getInstance().failTask(maid.getUUID(), "greet failed");
        }
        data.remove(TAG_NPC);
        data.remove(TAG_MACHINE);
        data.remove(TAG_CLIP);
        data.remove(TAG_COUNTER);
        data.remove(TAG_STAGE);
        data.remove(TAG_ORDER_ID);
        data.remove(TAG_WAIT_SINCE);
        data.remove(TAG_START);
        TaskSafetyUtils.resetMaidState(maid);
    }

    /** 女仆随身背包是否至少有一个空槽。 */
    private static boolean hasFreeInventorySlot(EntityMaid maid) {
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



    /** 扫描归属某台激活打单机、尚未被接待 / 认领的 walk-in 顾客（局部范围）。 */
    private static List<LivingEntity> findWalkInNpcs(ServerLevel level, BlockPos machine) {
        List<LivingEntity> result = new ArrayList<>();
        long machineKey = machine.asLong();
        AABB aabb = new AABB(
                machine.getX() - RANGE_H, machine.getY() - RANGE_V, machine.getZ() - RANGE_H,
                machine.getX() + RANGE_H, machine.getY() + RANGE_V, machine.getZ() + RANGE_H);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, aabb)) {
            var tags = e.getTags();
            if (!tags.contains(OrderNpcManager.TAG_WALKIN)) {
                continue;
            }
            if (tags.contains(WALKIN_INTERACTED_TAG) || tags.contains(OTC_NPC_TAG) || tags.contains(CLAIMED_TAG)) {
                continue;
            }
            Long mp = parseMachinePosNullable(e);
            if (mp == null || mp != machineKey) {
                continue;
            }
            result.add(e);
        }
        return result;
    }

    private static BlockPos parseMachinePos(LivingEntity npc) {
        Long packed = parseMachinePosNullable(npc);
        return packed == null ? null : BlockPos.of(packed);
    }

    private static Long parseMachinePosNullable(LivingEntity npc) {
        for (String tag : npc.getTags()) {
            if (tag.startsWith(OrderNpcManager.TAG_WALKIN_MACHINE_POS_PREFIX)) {
                try {
                    return Long.parseLong(tag.substring(OrderNpcManager.TAG_WALKIN_MACHINE_POS_PREFIX.length()));
                } catch (NumberFormatException ignored) {
                    return null;
                }
            }
        }
        return null;
    }

    /** 从操作台位置列表里取离 center 最近的一台（列表为空返回 null）。 */
    private static BlockPos nearestCounter(List<BlockPos> list, BlockPos center) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (BlockPos p : list) {
            double d = center.distSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best == null ? null : best.immutable();
    }

    private static BlockPos nearestTo(List<BlockPos> list, BlockPos center) {
        BlockPos best = list.get(0);
        double bestDist = center.distSqr(best);
        for (BlockPos p : list) {
            double d = center.distSqr(p);
            if (d < bestDist) {
                bestDist = d;
                best = p;
            }
        }
        return best;
    }

    /** 侍者站立点：顾客坐在椅子上时优先取相邻空气格，否则用顾客所在格。 */
    private static BlockPos greetStandPos(ServerLevel level, BlockPos npcPos) {
        int[][] around = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        for (int[] d : around) {
            BlockPos p = npcPos.offset(d[0], 0, d[1]);
            if (level.getBlockState(p).isAir() && level.getBlockState(p.above()).isAir()) {
                return p;
            }
        }
        return npcPos;
    }
}
