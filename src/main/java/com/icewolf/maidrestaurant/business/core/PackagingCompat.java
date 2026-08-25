package com.icewolf.maidrestaurant.business.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;

/**
 * 打包/装盘兼容工具类
 * 同时支持 Forge 版本（CountertopAutomationApi）和 Fabric 版本（直接调用 TakeoutBoxBlockEntity 方法）
 */
public class PackagingCompat {
    private static final Logger LOGGER = LogManager.getLogger("PackagingCompat");
    private static Boolean hasAutomationApi = null;
    private static Method executeMethod = null;
    private static Class<?> actionClass = null;
    private static Object actionPack = null;
    private static Object actionPlate = null;

    private static Method tryPackOrderMethod = null;
    private static Method tryPlateOrderMethod = null;

    static {
        try {
            Class<?> apiClass = Class.forName("cn.breezeth.ordertocook.api.CountertopAutomationApi");
            actionClass = Class.forName("cn.breezeth.ordertocook.api.CountertopAutomationApi$Action");
            Object[] actions = actionClass.getEnumConstants();
            for (Object a : actions) {
                String name = ((Enum<?>) a).name();
                if (name.equals("PACK")) actionPack = a;
                if (name.equals("PLATE")) actionPlate = a;
            }
            executeMethod = apiClass.getMethod("execute", Level.class, BlockPos.class, actionClass, Player.class, boolean.class);
            hasAutomationApi = true;
            LOGGER.info("PackagingCompat: 检测到 CountertopAutomationApi，使用 Forge 版本打包方式");
        } catch (ClassNotFoundException | NoSuchMethodException e) {
            hasAutomationApi = false;
            LOGGER.info("PackagingCompat: 未检测到 CountertopAutomationApi，使用 Fabric 版本打包方式（直接调用 TakeoutBoxBlockEntity）");
            try {
                tryPackOrderMethod = TakeoutBoxBlockEntity.class.getMethod("tryPackOrder", Player.class);
                tryPlateOrderMethod = TakeoutBoxBlockEntity.class.getMethod("tryPlateOrder", Player.class);
                LOGGER.info("PackagingCompat: 成功获取 tryPackOrder/tryPlateOrder 方法");
            } catch (NoSuchMethodException ex) {
                LOGGER.error("PackagingCompat: 无法获取 tryPackOrder/tryPlateOrder 方法", ex);
            }
        }
    }

    /**
     * 执行打包/装盘
     * @param level 世界
     * @param counterPos 操作台位置
     * @param isDelivery 是否是配送订单（true=打包外卖袋，false=装盘）
     * @param player 玩家（用于调用方法，可以是 fake player 或女仆主人）
     * @param simulate 是否模拟（true=只检查不执行，false=实际执行）
     * @return 是否成功
     */
    public static boolean execute(ServerLevel level, BlockPos counterPos, boolean isDelivery, Player player, boolean simulate) {
        BlockEntity be = level.getBlockEntity(counterPos);
        if (!(be instanceof TakeoutBoxBlockEntity)) {
            return false;
        }
        TakeoutBoxBlockEntity counter = (TakeoutBoxBlockEntity) be;

        if (Boolean.TRUE.equals(hasAutomationApi) && executeMethod != null) {
            // Forge 版本：使用 CountertopAutomationApi
            try {
                Object action = isDelivery ? actionPack : actionPlate;
                Object result = executeMethod.invoke(null, level, counterPos, action, player, simulate);
                if (result != null) {
                    Method successMethod = result.getClass().getMethod("success");
                    return (Boolean) successMethod.invoke(result);
                }
            } catch (Exception e) {
                LOGGER.error("PackagingCompat: CountertopAutomationApi.execute 调用失败", e);
            }
            return false;
        } else {
            // Fabric 版本：直接调用 TakeoutBoxBlockEntity 方法
            try {
                Method method = isDelivery ? tryPackOrderMethod : tryPlateOrderMethod;
                if (method != null) {
                    if (simulate) {
                        // 模拟模式：检查上方是否有空间，以及订单是否满足
                        // Fabric 版本没有 simulate 参数，直接执行
                        // 这里我们只做基本检查
                        return level.getBlockState(counterPos.above()).isAir();
                    } else {
                        method.invoke(counter, player);
                        return true;
                    }
                }
            } catch (Exception e) {
                LOGGER.error("PackagingCompat: TakeoutBoxBlockEntity 方法调用失败", e);
            }
            return false;
        }
    }

    public static boolean hasAutomationApi() {
        return Boolean.TRUE.equals(hasAutomationApi);
    }
}
