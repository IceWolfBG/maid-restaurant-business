/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  cn.breezeth.ordertocook.api.CountertopAutomationApi
 *  cn.breezeth.ordertocook.api.CountertopAutomationApi$Action
 *  cn.breezeth.ordertocook.api.CountertopAutomationApi$Result
 *  cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity
 *  cn.breezeth.ordertocook.registry.ModItems
 *  com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.neoforged.neoforge.items.IItemHandler
 */
package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.core.BusinessManager;
import com.icewolf.maidrestaurant.business.core.MaidUtils;
import com.icewolf.maidrestaurant.business.core.OrderBridge;
import com.icewolf.maidrestaurant.business.core.PackagingCompat;
import com.icewolf.maidrestaurant.business.core.ProgressionManager;
import java.lang.ref.WeakReference;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.IItemHandler;

public class PackagingBridge {
    private static final int STATE_GO_TO_COUNTER = 0;
    private static final int STATE_PACK = 1;
    private static final Map<BlockPos, PackTask> packTasks = new HashMap<BlockPos, PackTask>();

    // counterToMachine映射缓存更新间隔（tick）
    private static final long COUNTER_MAP_UPDATE_INTERVAL = 100L; // 5秒更新一次
    private static long lastCounterMapUpdate = 0;

    public static void tickPackaging(ServerLevel level, BusinessManager manager) {
        PackagingBridge.tickPackTasks(level, manager);

        // 定期更新counterToMachine映射（以激活的打单机为中心，24格范围）
        long currentTick = level.getGameTime();
        if (currentTick - lastCounterMapUpdate >= COUNTER_MAP_UPDATE_INTERVAL) {
            lastCounterMapUpdate = currentTick;
            updateCounterToMachineMap(level, manager);
        }

        for (Map.Entry<BlockPos, BlockPos> entry : manager.getCounterToMachine().entrySet()) {
            BlockPos counterPos = entry.getKey();
            BlockPos machinePos = entry.getValue();
            boolean deliveryUnlocked = ProgressionManager.isDeliveryUnlocked(level, machinePos);
            boolean activated = OrderBridge.isActivated(level, machinePos);
            if (!deliveryUnlocked || !activated) continue;
            try {
                PackagingBridge.checkAndStart(level, counterPos, manager);
            }
            catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("Error checking pack at {}", counterPos, t);
            }
        }
    }

    /**
     * 以激活的打单机为中心，24格范围扫描操作台，更新counterToMachine映射
     * 替代原来的全局扫描，提升性能
     */
    private static void updateCounterToMachineMap(ServerLevel level, BusinessManager manager) {
        try {
            Set<BlockPos> validCounters = new HashSet<>();
            // 获取所有激活的打单机
            Set<BlockPos> activatedMachines = ActivationCache.getActivatedMachines(level);
            for (BlockPos machinePos : activatedMachines) {
                if (machinePos == null) continue;
                // 以打单机为中心，扫描24格范围内的操作台
                int range = 24;
                for (BlockPos checkPos : BlockPos.betweenClosed(
                        machinePos.offset(-range, -4, -range),
                        machinePos.offset(range, 4, range))) {
                    BlockEntity be = level.getBlockEntity(checkPos);
                    if (!(be instanceof TakeoutBoxBlockEntity)) continue;
                    BlockPos counterPos = checkPos.immutable();
                    validCounters.add(counterPos);
                    // 更新映射（操作台 -> 打单机）
                    manager.getCounterToMachine().put(counterPos, machinePos);
                }
            }
            // 清理不在范围内的操作台
            Iterator<BlockPos> it = manager.getCounterToMachine().keySet().iterator();
            while (it.hasNext()) {
                BlockPos counterPos = it.next();
                if (!validCounters.contains(counterPos)) {
                    it.remove();
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("更新counterToMachine映射失败", t);
        }
    }

    private static void tickPackTasks(ServerLevel level, BusinessManager manager) {
        Iterator<Map.Entry<BlockPos, PackTask>> it = packTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, PackTask> entry = it.next();
            BlockPos counterPos = entry.getKey();
            PackTask task = entry.getValue();
            EntityMaid maid = (EntityMaid)task.maidRef.get();
            if (maid == null || !(level.getBlockEntity(counterPos) instanceof TakeoutBoxBlockEntity)) {
                // TaskManager：任务失败
                if (maid != null) {
                    TaskManager.getInstance().failTask(maid.getUUID(), "packaging target missing");
                }
                task.cleanup();
                it.remove();
                continue;
            }
            // TaskManager心跳更新
            TaskManager.getInstance().heartbeat(maid.getUUID(), manager.getTickCounter());
            long now = level.getGameTime();
            if (task.startTime == 0L) {
                task.startTime = now;
            }
            switch (task.state) {
                case 0: {
                    boolean timeout;
                    boolean near = MaidUtils.isNear(maid, counterPos, 3.0);
                    boolean bl = timeout = now - task.startTime > 60L;
                    if (near || timeout) {
                        if (timeout) {
                        }
                        task.state = 1;
                        break;
                    }
                    if (now - task.lastChange > 20L) {
                        task.lastChange = now;
                    }
                    MaidUtils.moveToSide(maid, counterPos, 0.4);
                    break;
                }
                case 1: {
                    // TaskManager：标记开始交互
                    TaskManager.getInstance().startInteraction(maid.getUUID());
                    boolean packSuccess = PackagingBridge.executePack(level, counterPos, manager);
                    if (packSuccess) {
                        // TaskManager：完成任务
                        TaskManager.getInstance().completeTask(maid.getUUID());
                    } else {
                        MaidRestaurantBusiness.LOGGER.warn("打包失败，标记任务失败: counter={}", counterPos);
                        // TaskManager：失败任务
                        TaskManager.getInstance().failTask(maid.getUUID(), "packaging_failed");
                    }
                    task.cleanup();
                    it.remove();
                }
            }
        }
    }

    private static void checkAndStart(ServerLevel level, BlockPos counterPos, BusinessManager manager) {
        if (packTasks.containsKey(counterPos)) {
            return;
        }
        // 排班表配置检查：如果附近有排班表且关闭了自动打包，则不执行
        BlockPos machinePos = manager.getCounterToMachine().get(counterPos);
        if (machinePos != null && !MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_PACKAGING)) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 排班表关闭了自动打包, counter={}, machine={}", counterPos, machinePos);
            return;
        }
        BlockEntity be = level.getBlockEntity(counterPos);
        if (!(be instanceof TakeoutBoxBlockEntity)) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 操作台不是TakeoutBoxBlockEntity, counter={}, be={}", counterPos, be == null ? "null" : be.getClass().getName());
            return;
        }
        IItemHandler inv = OrderBridge.getItemHandler(be);
        if (inv == null) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 无法获取物品处理器, counter={}", counterPos);
            return;
        }
        ItemStack orderStack = inv.getStackInSlot(0);
        if (orderStack.isEmpty() || !orderStack.is((Item)OtcCompat.ORDER())) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 订单不存在或不是订单物品, counter={}, isEmpty={}, isOrder={}", counterPos, orderStack.isEmpty(), orderStack.is((Item)OtcCompat.ORDER()));
            return;
        }
        CompoundTag nbt = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(orderStack);
        if (nbt == null) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 订单NBT为空, counter={}", counterPos);
            return;
        }
        boolean isDelivery = nbt.getBoolean("Delivery");
        // 使用兼容层执行打包/装盘（同时支持 Forge 和 Fabric 版本）
        boolean success = PackagingCompat.execute(level, counterPos, isDelivery, null, true);
        if (!success) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] PackagingCompat模拟检查失败, counter={}, isDelivery={}", counterPos, isDelivery);
            return;
        }
        if (!level.getBlockState(counterPos.above()).isAir()) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 操作台上方不是空气, counter={}", counterPos);
            return;
        }
        // 打单机员工人数限制检查
        if (machinePos == null) {
            machinePos = manager.getCounterToMachine().get(counterPos);
        }
        if (machinePos != null && !MaidUtils.canAcceptWorker(level, machinePos)) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 员工人数限制, counter={}, machine={}", counterPos, machinePos);
            return;
        }
        // 智能查找女仆：有绑定女仆时只找绑定的，否则回退到自动分配
        EntityMaid maid = MaidUtils.findWaiterMaidSmart(level, counterPos, 24, machinePos);
        if (maid == null) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 找不到侍者女仆, counter={}, machine={}", counterPos, machinePos);
            return;
        }
        // 任务冲突检查：如果女仆已有任务在执行，不分配打包任务
        if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 女仆已有任务, counter={}, maid={}", counterPos, maid.getName().getString());
            return;
        }
        if (MaidUtils.isOccupied(maid)) {
            MaidRestaurantBusiness.LOGGER.info("[打包调试] 女仆忙碌, counter={}, maid={}", counterPos, maid.getName().getString());
            return;
        }
        MaidRestaurantBusiness.LOGGER.info("[打包调试] 分配打包任务, counter={}, maid={}", counterPos, maid.getName().getString());
        packTasks.put(counterPos, new PackTask(maid, machinePos, manager.getTickCounter()));

        // TaskManager集成：创建打包任务并分配给女仆
        String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_PACKAGING, counterPos, machinePos);
        if (taskId != null) {
            TaskManager.getInstance().assignTask(maid.getUUID(), TaskManager.TYPE_PACKAGING, level);
        }
    }

    private static boolean executePack(ServerLevel level, BlockPos counterPos, BusinessManager manager) {
        BlockEntity be = level.getBlockEntity(counterPos);
        if (!(be instanceof TakeoutBoxBlockEntity)) {
            MaidRestaurantBusiness.LOGGER.warn("打包失败: 操作台不是TakeoutBoxBlockEntity, pos={}", counterPos);
            return false;
        }
        IItemHandler inv = OrderBridge.getItemHandler(be);
        if (inv == null) {
            MaidRestaurantBusiness.LOGGER.warn("打包失败: 无法获取物品处理器, pos={}", counterPos);
            return false;
        }
        ItemStack orderStack = inv.getStackInSlot(0);
        if (orderStack.isEmpty() || !orderStack.is((Item)OtcCompat.ORDER())) {
            MaidRestaurantBusiness.LOGGER.warn("打包失败: 订单不存在或不是订单物品, pos={}", counterPos);
            return false;
        }
        CompoundTag nbt = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(orderStack);
        if (nbt == null) {
            MaidRestaurantBusiness.LOGGER.warn("打包失败: 订单NBT为空, pos={}", counterPos);
            return false;
        }
        // 确保订单NBT中有machineId，用于餐厅统计（解决"不知名餐厅"问题）
        ensureMachineIdInOrder(level, counterPos, nbt, manager);
        boolean isDelivery = nbt.getBoolean("Delivery");
        if (!level.getBlockState(counterPos.above()).isAir()) {
            MaidRestaurantBusiness.LOGGER.warn("打包失败: 操作台上方不是空气, pos={}", counterPos);
            return false;
        }
        // 使用兼容层执行打包/装盘（同时支持 Forge 和 Fabric 版本）
        boolean actual = PackagingCompat.execute(level, counterPos, isDelivery, null, false);
        if (!actual) {
            MaidRestaurantBusiness.LOGGER.warn("打包失败: PackagingCompat.execute返回false, pos={}", counterPos);
            return false;
        }
        return true;
    }

    /**
     * 确保订单NBT中有order_machine.id字段
     * 从对应的订单机获取machineId并写入订单NBT
     */
    private static void ensureMachineIdInOrder(ServerLevel level, BlockPos counterPos, CompoundTag nbt, BusinessManager manager) {
        try {
            // 如果已经有machineId，就不用再设置了
            if (nbt.contains("order_machine.id") && nbt.getInt("order_machine.id") > 0) {
                return;
            }
            // 从manager获取对应的订单机位置
            BlockPos machinePos = manager.getCounterToMachine().get(counterPos);
            if (machinePos == null) {
                MaidRestaurantBusiness.LOGGER.warn("打包: 操作台 {} 没有对应的订单机，无法设置machineId", counterPos);
                return;
            }
            BlockEntity machineBe = level.getBlockEntity(machinePos);
            if (machineBe == null) {
                return;
            }
            // 通过反射调用getMachineId()或ensureMachineId()
            int machineId = getMachineIdFromOrderMachine(machineBe, level);
            if (machineId > 0) {
                nbt.putInt("order_machine.id", machineId);
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("设置订单machineId失败", t);
        }
    }

    /**
     * 通过反射从订单机获取machineId
     */
    private static int getMachineIdFromOrderMachine(BlockEntity machineBe, ServerLevel level) {
        try {
            // 先尝试getMachineId()
            try {
                java.lang.reflect.Method getMachineId = machineBe.getClass().getMethod("getMachineId");
                Object result = getMachineId.invoke(machineBe);
                if (result instanceof Integer && (Integer)result > 0) {
                    return (Integer)result;
                }
            } catch (NoSuchMethodException ignored) {}

            // 再尝试ensureMachineId()（需要ServerWorld参数，Fabric版本）
            try {
                java.lang.reflect.Method ensureMachineId = machineBe.getClass().getMethod("ensureMachineId", level.getClass());
                Object result = ensureMachineId.invoke(machineBe, level);
                if (result instanceof Integer && (Integer)result > 0) {
                    return (Integer)result;
                }
            } catch (NoSuchMethodException ignored) {}

            // 尝试直接读取machineId字段
            try {
                java.lang.reflect.Field field = machineBe.getClass().getDeclaredField("machineId");
                field.setAccessible(true);
                Object result = field.get(machineBe);
                if (result instanceof Integer && (Integer)result > 0) {
                    return (Integer)result;
                }
            } catch (NoSuchFieldException ignored) {}
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("从订单机获取machineId失败", t);
        }
        return 0;
    }

    private static class PackTask {
        int state = 0;
        long lastChange = 0L;
        long startTime = 0L;
        final WeakReference<EntityMaid> maidRef;

        PackTask(EntityMaid maid, BlockPos machinePos, long currentTick) {
            this.maidRef = new WeakReference<EntityMaid>(maid);
            MaidUtils.setOccupied(maid, true);
            MaidUtils.startTask(maid, machinePos, "packaging", currentTick);
            // 显示侍者开始打包气泡
            try {
                com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.waiterPacking(maid);
            } catch (Exception e) {}
        }

        void cleanup() {
            EntityMaid maid = (EntityMaid)this.maidRef.get();
            if (maid != null) {
                try {
                    // 调用TaskSafetyUtils彻底重置女仆状态
                    TaskSafetyUtils.resetMaidState(maid);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("打包任务清理时TaskSafetyUtils.resetMaidState失败", t);
                    // 回退：手动清理
                    try {
                        maid.getNavigation().stop();
                        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH);
                    } catch (Throwable t2) {}
                    MaidUtils.setOccupied(maid, false);
                }
            }
        }
    }
}
