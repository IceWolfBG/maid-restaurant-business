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
    private final Map<BlockPos, Long> orderCooldowns = new HashMap<BlockPos, Long>();
    private final Map<BlockPos, BlockPos> counterToMachine = new HashMap<BlockPos, BlockPos>();
    private final Map<BlockPos, ActiveOrder> activeOrders = new HashMap<BlockPos, ActiveOrder>();
    private final Set<BlockPos> activatedMachines = new HashSet<BlockPos>();

    public BusinessManager(MinecraftServer server) {
        this.server = server;
    }

    public void tick(MinecraftServer server) {
        ++this.tickCounter;
        for (ServerLevel level : server.getAllLevels()) {
            try {
                this.cleanupExpiredOrders(level);
                // 女仆卡住自愈检测（每100tick=5秒检测一次）
                if (this.tickCounter % 100L == 0L) {
                    // 主动从女仆饰品栏维护绑定关系（不依赖饰品的onTick）
                    MaidUtils.updateBindingsFromBaubles(level);
                    
                    // 检测正在执行任务但卡住的女仆（超时300tick=15秒）
                    int resetCount = MaidUtils.checkAndResetStuckMaids(level, this.tickCounter, 300, 1.0);
                    // 检测被标记为不忙碌但AI状态卡住的女仆（任务清理不彻底导致的）
                    int idleStuckCount = MaidUtils.checkAndResetIdleStuckMaids(level);
                    if (resetCount > 0 || idleStuckCount > 0) {
                        MaidRestaurantBusiness.LOGGER.info("女仆卡住自愈: 本轮重置了 {} 个任务中卡住的女仆, {} 个空闲但AI卡住的女仆", resetCount, idleStuckCount);
                    }
                    // 绑定关系统计调试（每100tick=5秒输出一次）
                    for (BlockPos machinePos : this.getActivatedMachines()) {
                        int boundCount = MaidUtils.getWorkerCountForMachine(machinePos);
                        int maxWorkers = ProgressionManager.getMaxWorkers(level, machinePos);
                        MaidRestaurantBusiness.LOGGER.info("绑定调试: 打单机 {} 绑定女仆数={}/{}, 有绑定时仅绑定女仆可工作", machinePos, boundCount, maxWorkers);
                    }
                }
                if (this.tickCounter - this.lastOrderTick >= 200L) {
                    OrderBridge.tickOrders(level, this);
                    this.lastOrderTick = this.tickCounter;
                }
                if (this.tickCounter - this.lastCookingTick >= 10L) {
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
                MaidRestaurantBusiness.LOGGER.info("ActiveOrder expired at counter {}, removing (age={} ticks) and cancelling cooking tasks", entry.getKey(), this.tickCounter - order.createdTick);
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
