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
            LOGGER.info("PackagingCompat: 找到 CountertopAutomationApi 类");
            actionClass = Class.forName("cn.breezeth.ordertocook.api.CountertopAutomationApi$Action");
            Object[] actions = actionClass.getEnumConstants();
            for (Object a : actions) {
                String name = ((Enum<?>) a).name();
                if (name.equals("PACK")) actionPack = a;
                if (name.equals("PLATE")) actionPlate = a;
            }
            LOGGER.info("PackagingCompat: actionPack={}, actionPlate={}", actionPack, actionPlate);
            executeMethod = apiClass.getMethod("execute", Level.class, BlockPos.class, actionClass, Player.class, boolean.class);
            hasAutomationApi = true;
            LOGGER.info("PackagingCompat: CountertopAutomationApi 初始化成功，使用API模式");
        } catch (Throwable e) {
            hasAutomationApi = false;
            LOGGER.warn("PackagingCompat: CountertopAutomationApi 初始化失败，使用直接调用模式", e);
            try {
                tryPackOrderMethod = TakeoutBoxBlockEntity.class.getMethod("tryPackOrder", Player.class);
                tryPlateOrderMethod = TakeoutBoxBlockEntity.class.getMethod("tryPlateOrder", Player.class);
                LOGGER.info("PackagingCompat: tryPackOrder/tryPlateOrder 方法获取成功");
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
            LOGGER.warn("PackagingCompat: 操作台不是TakeoutBoxBlockEntity, pos={}", counterPos);
            return false;
        }
        TakeoutBoxBlockEntity counter = (TakeoutBoxBlockEntity) be;

        if (Boolean.TRUE.equals(hasAutomationApi) && executeMethod != null) {
            // Forge/NeoForge 版本：使用 CountertopAutomationApi
            try {
                Object action = isDelivery ? actionPack : actionPlate;
                LOGGER.info("PackagingCompat: 调用CountertopAutomationApi.execute, pos={}, isDelivery={}, simulate={}, action={}", counterPos, isDelivery, simulate, action);
                Object result = executeMethod.invoke(null, level, counterPos, action, player, simulate);
                if (result != null) {
                    Method successMethod = result.getClass().getMethod("success");
                    boolean success = (Boolean) successMethod.invoke(result);
                    LOGGER.info("PackagingCompat: CountertopAutomationApi.execute返回, success={}, result={}", success, result);
                    return success;
                } else {
                    LOGGER.warn("PackagingCompat: CountertopAutomationApi.execute返回null");
                }
            } catch (Exception e) {
                LOGGER.error("PackagingCompat: CountertopAutomationApi.execute 调用失败", e);
            }
            return false;
        } else {
            // Fabric/回退 版本：直接调用 TakeoutBoxBlockEntity 方法
            LOGGER.info("PackagingCompat: 使用直接调用模式, hasAutomationApi={}, executeMethod={}", hasAutomationApi, executeMethod);
            try {
                Method method = isDelivery ? tryPackOrderMethod : tryPlateOrderMethod;
                if (method != null) {
                    if (simulate) {
                        // 模拟模式：检查上方是否有空间
                        boolean airAbove = level.getBlockState(counterPos.above()).isAir();
                        LOGGER.info("PackagingCompat: 模拟模式, 上方是否空气={}", airAbove);
                        return airAbove;
                    } else {
                        LOGGER.info("PackagingCompat: 实际执行, 调用{}", method.getName());
                        method.invoke(counter, player);
                        return true;
                    }
                } else {
                    LOGGER.warn("PackagingCompat: method为null");
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
