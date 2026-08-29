/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 */
package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import com.icewolf.maidrestaurant.business.core.ActiveOrder;
import com.icewolf.maidrestaurant.business.core.CookingBridge;
import com.icewolf.maidrestaurant.business.core.DeliveryBridge;
import com.icewolf.maidrestaurant.business.core.DishwashingBridge;
import com.icewolf.maidrestaurant.business.core.OrderBridge;
import com.icewolf.maidrestaurant.business.core.PackagingBridge;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;

public class BusinessManager {
    private final MinecraftServer server;
    private long tickCounter = 0L;
    private static final long DELAY_ORDER = 200L;
    private static final long DELAY_COOKING = 10L;
    private static final long DELAY_PACKAGING = 10L;
    private static final long DELAY_DELIVERY = 10L;
    private static final long DELAY_DISHWASH = 10L;
    private static final long ACTIVE_ORDER_TIMEOUT = 1200L;
    private long lastOrderTick = -200L;
    private long lastCookingTick = -10L;
    private long lastPackagingTick = -10L;
    private long lastDeliveryTick = -10L;
    private long lastDishwashTick = -10L;
    private long lastStaleCleanupTick = -1200L;
    private final Map<BlockPos, Long> orderCooldowns = new HashMap<BlockPos, Long>();
    private final Map<BlockPos, BlockPos> counterToMachine = new HashMap<BlockPos, BlockPos>();
    private final Map<BlockPos, ActiveOrder> activeOrders = new HashMap<BlockPos, ActiveOrder>();
    private final Set<BlockPos> activatedMachines = new HashSet<BlockPos>();

    public BusinessManager(MinecraftServer server) {
        this.server = server;
        // 设置TaskManager的BusinessManager引用，用于自动接单等功能
        TaskManager.getInstance().setBusinessManager(this);
    }

    public void tick(MinecraftServer server) {
        ++this.tickCounter;
        for (ServerLevel level : server.getAllLevels()) {
            try {
                this.cleanupExpiredOrders(level);
                // 定期清理残留烹饪任务（每1200tick=60秒）
                if (this.tickCounter - this.lastStaleCleanupTick >= 1200L) {
                    CookingBridge.cleanupStaleTasks(level, this.activeOrders.keySet());
                    this.lastStaleCleanupTick = this.tickCounter;
                }
                // TaskManager超时检测和异常处理（每10tick=0.5秒检测一次）
                TaskManager.getInstance().tick(this.tickCounter, level);
                // 女仆卡住自愈检测（每100tick=5秒检测一次）
                if (this.tickCounter % 100L == 0L) {
                    // 主动从女仆饰品栏维护绑定关系（不依赖饰品的onTick）
                    MaidUtils.updateBindingsFromBaubles(level);
                    
                    // 检测正在执行任务但卡住的女仆（超时300tick=15秒）
                    int resetCount = MaidUtils.checkAndResetStuckMaids(level, this.tickCounter, 300, 1.0);
                    // 检测被标记为不忙碌但AI状态卡住的女仆（任务清理不彻底导致的）
                    int idleStuckCount = MaidUtils.checkAndResetIdleStuckMaids(level);
                    if (resetCount > 0 || idleStuckCount > 0) {
                    }
                    // 绑定关系统计调试（每100tick=5秒输出一次）
                    for (BlockPos machinePos : this.getActivatedMachines()) {
                        int boundCount = MaidUtils.getWorkerCountForMachine(machinePos);
                        int maxWorkers = ProgressionManager.getMaxWorkers(level, machinePos);
                    }
                }
                // 自动接单已集成到TaskManager中，每10tick检查一次，这里不需要单独调用了
                // TaskManager.tick会调用OrderBridge.tickOrders，少一次监测，提高性能
                if (this.tickCounter - this.lastCookingTick >= 10L) {
                    // 更新全局厨具状态（在烹饪任务分配前更新）
                    if (!this.getActivatedMachines().isEmpty()) {
                        BlockPos center = this.getActivatedMachines().iterator().next();
                        // 初始化持久化保存（只初始化一次）
                        CookingDeviceManager.getInstance().initSavedData(level);
                        CookingDeviceManager.getInstance().update(level, center, 32, this.tickCounter);
                        // 每200tick输出一次厨具状态统计
                        if (this.tickCounter % 200L == 0L) {
                            Map<String, Integer> stats = CookingDeviceManager.getInstance().getStats();
                        }
                    }
                    CookingBridge.tickCooking(level, this);
                    this.lastCookingTick = this.tickCounter;
                }
                if (BusinessConfig.autoPack && this.tickCounter - this.lastPackagingTick >= 10L) {
                    PackagingBridge.tickPackaging(level, this);
                    this.lastPackagingTick = this.tickCounter;
                }
                if (BusinessConfig.waiterDeliver && this.tickCounter - this.lastDeliveryTick >= 10L) {
                    DeliveryBridge.tickDelivery(level, this);
                    this.lastDeliveryTick = this.tickCounter;
                }
                if (!BusinessConfig.autoWash || this.tickCounter - this.lastDishwashTick < 10L) continue;
                DishwashingBridge.tickDishwashing(level, this);
                this.lastDishwashTick = this.tickCounter;
            }
            catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("Business tick error in dimension {}", level.dimension().location(), t);
            }
        }
    }

    public Map<BlockPos, Long> getOrderCooldowns() {
        return this.orderCooldowns;
    }

    public Map<BlockPos, BlockPos> getCounterToMachine() {
        return this.counterToMachine;
    }

    public Map<BlockPos, ActiveOrder> getActiveOrders() {
        return this.activeOrders;
    }

    public Set<BlockPos> getActivatedMachines() {
        return this.activatedMachines;
    }

    public MinecraftServer getServer() {
        return this.server;
    }
    
    public long getTickCounter() {
        return this.tickCounter;
    }

    private void cleanupExpiredOrders(ServerLevel level) {
        if (this.activeOrders.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<BlockPos, ActiveOrder>> it = this.activeOrders.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, ActiveOrder> entry = it.next();
            ActiveOrder order = entry.getValue();
            if (this.tickCounter - order.createdTick > ACTIVE_ORDER_TIMEOUT) {
                try {
                    CookingBridge.cancelCookRequestsForCounter(level, entry.getKey());
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("Failed to cancel cooking tasks for expired order", t);
                }
                it.remove();
            }
        }
    }
}
