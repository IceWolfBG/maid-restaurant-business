/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity
 *  net.minecraft.core.BlockPos
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.world.level.block.entity.BlockEntity
 */
package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.GameplayConfig;
import java.lang.reflect.Field;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;

public final class ProgressionManager {
    public static final int LEVEL_DELIVERY = 0;
    public static final int LEVEL_COOK_AND_PREP = 1;
    public static final int LEVEL_DISHWASHING = 2;
    /** 自动接单（含到店顾客接待、打单机订单自动入台）：3级解锁。 */
    public static final int LEVEL_AUTO_ORDER = 3;
    /** 自动预烹饪（挂单夹订单在入台前提前备菜，成品优先入冰箱）：4级解锁。 */
    public static final int LEVEL_AUTO_PRE_COOK = 4;

    private ProgressionManager() {
    }

    public static int getRestaurantLevel(ServerLevel level, BlockPos machinePos) {
        BlockEntity be = level.getBlockEntity(machinePos);
        if (be instanceof OrderMachineBlockEntity) {
            OrderMachineBlockEntity machine = (OrderMachineBlockEntity)be;
            // 尝试多个可能的字段名（Forge版本用restaurantLevel，Fabric版本用level）
            String[] fieldNames = {"restaurantLevel", "level"};
            for (String fieldName : fieldNames) {
                try {
                    Field f = machine.getClass().getDeclaredField(fieldName);
                    f.setAccessible(true);
                    Object result = f.get(machine);
                    if (result instanceof Integer) {
                        return (Integer)result;
                    }
                }
                catch (Throwable t) {
                    // 尝试下一个字段名
                }
            }
            MaidRestaurantBusiness.LOGGER.warn("Progression: failed to get restaurant level for machine at {}, all field names failed, defaulting to 0", machinePos);
        }
        return 0;
    }

    public static boolean isUnlocked(ServerLevel level, BlockPos machinePos, int requiredLevel) {
        if (!GameplayConfig.levelBasedProgression) {
            return true;
        }
        return ProgressionManager.getRestaurantLevel(level, machinePos) >= requiredLevel;
    }

    public static boolean isDeliveryUnlocked(ServerLevel level, BlockPos machinePos) {
        return ProgressionManager.isUnlocked(level, machinePos, 0);
    }

    public static boolean isCookAndPrepUnlocked(ServerLevel level, BlockPos machinePos) {
        return ProgressionManager.isUnlocked(level, machinePos, 1);
    }

    public static boolean isDishwashingUnlocked(ServerLevel level, BlockPos machinePos) {
        return ProgressionManager.isUnlocked(level, machinePos, 2);
    }

    public static boolean isAutoOrderUnlocked(ServerLevel level, BlockPos machinePos) {
        return ProgressionManager.isUnlocked(level, machinePos, LEVEL_AUTO_ORDER);
    }

    public static boolean isAutoPreCookingUnlocked(ServerLevel level, BlockPos machinePos) {
        return ProgressionManager.isUnlocked(level, machinePos, LEVEL_AUTO_PRE_COOK);
    }

    public static int getMaxWorkers(ServerLevel level, BlockPos machinePos) {
        int restaurantLevel = ProgressionManager.getRestaurantLevel(level, machinePos);
        if (restaurantLevel >= 8) {
            return 10;
        }
        return Math.min(restaurantLevel + 1, 10);
    }
}
