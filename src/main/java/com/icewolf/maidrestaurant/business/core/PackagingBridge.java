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
 *  net.minecraftforge.items.IItemHandler
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
import java.util.Iterator;
import java.util.List;
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
import net.minecraftforge.items.IItemHandler;

public class PackagingBridge {
    private static final int STATE_GO_TO_COUNTER = 0;
    private static final int STATE_PACK = 1;
    private static final Map<BlockPos, PackTask> packTasks = new HashMap<BlockPos, PackTask>();

    public static void tickPackaging(ServerLevel level, BusinessManager manager) {
        PackagingBridge.tickPackTasks(level, manager);
        int counterCount = manager.getCounterToMachine().size();
        // 每次都输出调试日志，方便排查问题
        MaidRestaurantBusiness.LOGGER.info("打包: tickPackaging 被调用，counterToMachine操作台数量={}", counterCount);
        
        // 如果counterToMachine为空，直接扫描附近的操作台
        if (counterCount == 0) {
            MaidRestaurantBusiness.LOGGER.info("打包: counterToMachine为空，直接扫描操作台");
            List<BlockPos> counters = null;
            try {
                counters = WorldScanner.scan(level, TakeoutBoxBlockEntity.class);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("打包: 扫描操作台失败", t);
            }
            if (counters != null) {
                for (BlockPos counterPos : counters) {
                    // 查找附近的打单机
                    BlockPos machinePos = null;
                    try {
                        List<BlockPos> machines = WorldScanner.scan(level, OrderMachineBlockEntity.class);
                        for (BlockPos mp : machines) {
                            if (counterPos.distSqr(mp) <= 64.0) {
                                machinePos = mp;
                                break;
                            }
                        }
                    } catch (Throwable t) {}
                    if (machinePos == null) continue;
                    // 临时添加到counterToMachine
                    manager.getCounterToMachine().put(counterPos, machinePos);
                    MaidRestaurantBusiness.LOGGER.info("打包: 临时添加操作台 {} -> 打单机 {}", counterPos, machinePos);
                }
            }
        }
        
        for (Map.Entry<BlockPos, BlockPos> entry : manager.getCounterToMachine().entrySet()) {
            BlockPos counterPos = entry.getKey();
            BlockPos machinePos = entry.getValue();
            boolean deliveryUnlocked = ProgressionManager.isDeliveryUnlocked(level, machinePos);
            boolean activated = OrderBridge.isActivated(level, machinePos);
            MaidRestaurantBusiness.LOGGER.info("打包: 检查操作台={}, 打单机={}, 送餐解锁={}, 已激活={}", counterPos, machinePos, deliveryUnlocked, activated);
            if (!deliveryUnlocked || !activated) continue;
            try {
                PackagingBridge.checkAndStart(level, counterPos, manager);
            }
            catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("Error checking pack at {}", counterPos, t);
            }
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
                task.cleanup();
                it.remove();
                continue;
            }
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
                            MaidRestaurantBusiness.LOGGER.info("\u6253\u5305\uff1a\u5973\u4ec6\u8d85\u65f6\uff0c\u76f4\u63a5\u6253\u5305 counter={} maidPos=({},{},{})", counterPos, MaidUtils.getX((Entity)maid), MaidUtils.getY((Entity)maid), MaidUtils.getZ((Entity)maid));
                        }
                        task.state = 1;
                        break;
                    }
                    if (now - task.lastChange > 20L) {
                        MaidRestaurantBusiness.LOGGER.info("\u6253\u5305\uff1a\u5973\u4ec6\u524d\u5f80\u6253\u5305\u53f0 counter={} maidPos=({},{},{}) near={}", counterPos, MaidUtils.getX((Entity)maid), MaidUtils.getY((Entity)maid), MaidUtils.getZ((Entity)maid), near);
                        task.lastChange = now;
                    }
                    MaidUtils.moveToSide(maid, counterPos, 0.4);
                    break;
                }
                case 1: {
                    MaidRestaurantBusiness.LOGGER.info("打包：执行打包 counter={}", counterPos);
                    PackagingBridge.executePack(level, counterPos, manager);
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
            return;
        }
        BlockEntity be = level.getBlockEntity(counterPos);
        if (!(be instanceof TakeoutBoxBlockEntity)) {
            return;
        }
        IItemHandler inv = OrderBridge.getItemHandler(be);
        if (inv == null) {
            MaidRestaurantBusiness.LOGGER.info("打包: 操作台 {} 没有物品处理器", counterPos);
            return;
        }
        ItemStack orderStack = inv.getStackInSlot(0);
        MaidRestaurantBusiness.LOGGER.info("打包: 操作台 {} 槽位0物品={}, 数量={}, 是空={}", counterPos, orderStack.getItem(), orderStack.getCount(), orderStack.isEmpty());
        if (orderStack.isEmpty() || !orderStack.is((Item)OtcCompat.ORDER())) {
            return;
        }
        CompoundTag nbt = orderStack.getTag();
        if (nbt == null) {
            MaidRestaurantBusiness.LOGGER.info("打包: 操作台 {} 订单没有NBT", counterPos);
            return;
        }
        boolean isDelivery = nbt.getBoolean("Delivery");
        MaidRestaurantBusiness.LOGGER.info("打包: 检测到订单 at counter={}, isDelivery={}, OrderId={}", counterPos, isDelivery, nbt.getString("OrderId"));
        // 使用兼容层执行打包/装盘（同时支持 Forge 和 Fabric 版本）
        boolean success = PackagingCompat.execute(level, counterPos, isDelivery, null, true);
        MaidRestaurantBusiness.LOGGER.info("打包: 模拟检查结果={} at counter={}", success, counterPos);
        if (!success) {
            return;
        }
        if (!level.getBlockState(counterPos.above()).isAir()) {
            MaidRestaurantBusiness.LOGGER.info("打包: 操作台上方不是空气，跳过 at counter={}", counterPos);
            return;
        }
        // 打单机员工人数限制检查
        if (machinePos == null) {
            machinePos = manager.getCounterToMachine().get(counterPos);
        }
        if (machinePos != null && !MaidUtils.canAcceptWorker(level, machinePos)) {
            MaidRestaurantBusiness.LOGGER.info("打包: 打单机 {} 已达员工上限，跳过分配", machinePos);
            return;
        }
        // 智能查找女仆：有绑定女仆时只找绑定的，否则回退到自动分配
        EntityMaid maid = MaidUtils.findWaiterMaidSmart(level, counterPos, 16, machinePos);
        if (maid == null) {
            MaidRestaurantBusiness.LOGGER.info("打包: 没有找到可用的侍者女仆 at counter={} (有绑定女仆时仅绑定女仆可工作)", counterPos);
            return;
        }
        packTasks.put(counterPos, new PackTask(maid, machinePos, manager.getTickCounter()));
        MaidRestaurantBusiness.LOGGER.info("打包：女仆前往打包台 {}", counterPos);
    }

    private static void executePack(ServerLevel level, BlockPos counterPos, BusinessManager manager) {
        BlockEntity be = level.getBlockEntity(counterPos);
        if (!(be instanceof TakeoutBoxBlockEntity)) {
            return;
        }
        IItemHandler inv = OrderBridge.getItemHandler(be);
        if (inv == null) {
            return;
        }
        ItemStack orderStack = inv.getStackInSlot(0);
        if (orderStack.isEmpty() || !orderStack.is((Item)OtcCompat.ORDER())) {
            return;
        }
        CompoundTag nbt = orderStack.getTag();
        if (nbt == null) {
            return;
        }
        // 确保订单NBT中有machineId，用于餐厅统计（解决"不知名餐厅"问题）
        ensureMachineIdInOrder(level, counterPos, nbt, manager);
        boolean isDelivery = nbt.getBoolean("Delivery");
        if (!level.getBlockState(counterPos.above()).isAir()) {
            return;
        }
        // 使用兼容层执行打包/装盘（同时支持 Forge 和 Fabric 版本）
        boolean actual = PackagingCompat.execute(level, counterPos, isDelivery, null, false);
        if (actual) {
            MaidRestaurantBusiness.LOGGER.info("自动装盘成功 at {}", counterPos);
        }
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
                MaidRestaurantBusiness.LOGGER.info("打包: 为订单设置machineId={} at counter={}", machineId, counterPos);
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
        }

        void cleanup() {
            EntityMaid maid = (EntityMaid)this.maidRef.get();
            if (maid != null) {
                try {
                    // 停止寻路，清除大脑记忆，防止女仆AI状态卡住
                    maid.getNavigation().stop();
                    maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                    maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("打包任务清理时重置女仆AI状态失败", t);
                }
                MaidUtils.setOccupied(maid, false);
            }
        }
    }
}
