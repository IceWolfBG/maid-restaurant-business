package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 顾客兼容工具类
 * 支持 CustomerEntity（普通顾客）和车万女仆实体（当启用了车万女仆顾客兼容时）
 * 通过命令标签（otc_npc, otc_order:xxx）来识别顾客
 */
public class CustomerCompat {

    private static final String TAG_NPC = "otc_npc";
    private static final String TAG_ORDER_PREFIX = "otc_order:";
    private static final String TAG_TOUHOU_COMPLETION_ORDER_PREFIX = "otc_touhou_completion_order:";

    // 反射 Method 缓存：送餐阶段每 tick 对范围内大量生物调用，避免每次 getMethod + 抛异常的开销
    private static final Map<String, Method> METHOD_CACHE = new ConcurrentHashMap<>();
    private static final Set<String> METHOD_MISSING = ConcurrentHashMap.newKeySet();
    // "找不到顾客"日志节流：同一订单只在第一次未找到时 WARN，避免送餐阶段每 tick 刷屏
    private static final Set<String> WARNED_MISSING_BY_ORDER = ConcurrentHashMap.newKeySet();

    /** 按"具体类#方法名"缓存无参反射方法；确认不存在的方法也记录，避免反复抛 NoSuchMethodException。 */
    private static Method resolveMethod(Class<?> clazz, String name) {
        if (clazz == null || name == null) return null;
        String key = clazz.getName() + "#" + name;
        Method cached = METHOD_CACHE.get(key);
        if (cached != null) return cached;
        if (METHOD_MISSING.contains(key)) return null;
        try {
            Method m = clazz.getMethod(name);
            METHOD_CACHE.put(key, m);
            return m;
        } catch (NoSuchMethodException e) {
            METHOD_MISSING.add(key);
            return null;
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * 检查实体是否是otc的顾客（通过命令标签判断）
     */
    public static boolean isOtcCustomer(Entity entity) {
        if (entity == null) return false;
        if (hasCommandTag(entity, TAG_NPC)) return true;
        // 检查坐骑
        Entity vehicle = entity.getVehicle();
        if (vehicle != null && hasCommandTag(vehicle, TAG_NPC)) return true;
        return false;
    }

    /**
     * 检查实体是否有指定的订单标签
     * 支持普通顾客（otc_order:xxx）和车万女仆顾客（otc_touhou_completion_order:xxx）
     */
    public static boolean hasOrderTag(Entity entity, String orderId) {
        if (entity == null) return false;
        String orderTag = TAG_ORDER_PREFIX + orderId;
        String touhouOrderTag = TAG_TOUHOU_COMPLETION_ORDER_PREFIX + orderId;
        if (hasCommandTag(entity, orderTag) || hasCommandTag(entity, touhouOrderTag)) return true;
        // 检查坐骑
        Entity vehicle = entity.getVehicle();
        if (vehicle != null && (hasCommandTag(vehicle, orderTag) || hasCommandTag(vehicle, touhouOrderTag))) return true;
        // 检查乘客（直接遍历乘客；旧代码 entity.hasPassenger(entity) 恒为 false，导致该分支永不执行）
        for (Entity passenger : entity.getPassengers()) {
            if (hasCommandTag(passenger, orderTag) || hasCommandTag(passenger, touhouOrderTag)) return true;
        }
        return false;
    }

    /**
     * 通过反射检查实体是否有指定的命令标签
     * 兼容 Forge（getTags）和 Fabric（getCommandTags）
     */
    public static boolean hasCommandTag(Entity entity, String tag) {
        if (entity == null) return false;
        // 先尝试 getTags()（Forge/NeoForge版本）
        try {
            if (entity.getTags().contains(tag)) return true;
        } catch (Exception e) {
            // 忽略
        }
        // 再尝试 getCommandTags()（Fabric版本，通过反射，结果已缓存）
        try {
            Method method = resolveMethod(entity.getClass(), "getCommandTags");
            if (method != null) {
                Object result = method.invoke(entity);
                if (result instanceof java.util.Collection) {
                    if (((java.util.Collection<?>) result).contains(tag)) return true;
                }
            }
        } catch (Exception e) {
            // 方法不存在，忽略
        }
        return false;
    }

    /**
     * 获取顾客ID
     * 对于 CustomerEntity，使用 getCustomerId()
     * 对于车万女仆，尝试从NBT或其他方式获取
     */
    public static String getCustomerId(LivingEntity customer) {
        if (customer == null) return "";
        // 尝试调用 getCustomerId() 方法
        try {
            Method method = resolveMethod(customer.getClass(), "getCustomerId");
            if (method != null) {
                Object result = method.invoke(customer);
                if (result instanceof String) {
                    return (String) result;
                }
            }
        } catch (Exception e) {
            // 方法不存在，忽略
        }
        // 回退：使用实体的UUID字符串
        return customer.getStringUUID();
    }

    /**
     * 判断顾客是否坐在椅子上
     * 对于 CustomerEntity，使用 isChairCustomer()
     * 对于其他实体，检查坐骑是否是 SeatEntity 或椅子
     */
    public static boolean isChairCustomer(LivingEntity customer) {
        if (customer == null) return false;
        // 尝试调用 isChairCustomer() 方法
        try {
            Method method = resolveMethod(customer.getClass(), "isChairCustomer");
            if (method != null) {
                Object result = method.invoke(customer);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
        } catch (Exception e) {
            // 方法不存在，忽略
        }
        // 回退：检查坐骑
        Entity vehicle = customer.getVehicle();
        return vehicle != null;
    }

    /**
     * 判断顾客是否正在吃东西
     * 对于 CustomerEntity，使用 isEatingActionActive()
     * 对于其他实体，检查是否正在使用物品
     */
    public static boolean isEatingActionActive(LivingEntity customer) {
        if (customer == null) return false;
        // 尝试调用 isEatingActionActive() 方法
        try {
            Method method = resolveMethod(customer.getClass(), "isEatingActionActive");
            if (method != null) {
                Object result = method.invoke(customer);
                if (result instanceof Boolean) {
                    return (Boolean) result;
                }
            }
        } catch (Exception e) {
            // 方法不存在，忽略
        }
        // 回退：检查是否正在使用物品
        return customer.isUsingItem();
    }

    /**
     * 查找指定订单ID的顾客
     * 使用 LivingEntity.class，兼容 CustomerEntity 和车万女仆
     */
    public static LivingEntity findCustomerByOrderId(Level level, BlockPos center, String orderId, double range) {
        if (level == null || center == null || orderId == null || orderId.isEmpty()) return null;
        AABB area = new AABB(center).inflate(range);

        List<LivingEntity> customers = level.getEntitiesOfClass(LivingEntity.class, area,
            c -> c.isAlive() && isChairCustomer(c) && hasOrderTag(c, orderId));
        if (customers.isEmpty()) {
            // 修复：没有找到匹配订单的顾客时，返回null而不是第一个顾客
            // 避免把订单A的餐错误地配送给顾客B
            // 日志节流：同一订单只在第一次未找到时 WARN（顾客离开后送餐会持续找不到，属正常分支，不刷屏）
            if (WARNED_MISSING_BY_ORDER.add(orderId)) {
                MaidRestaurantBusiness.LOGGER.warn("findCustomerByOrderId: 未找到匹配订单 {} 的顾客", orderId);
            }
            return null;
        }
        // 找到了，清除该订单的未找到标记
        WARNED_MISSING_BY_ORDER.remove(orderId);
        return customers.get(0);
    }

    /**
     * 查找指定顾客ID的顾客
     */
    public static LivingEntity findCustomerById(Level level, BlockPos center, String customerId, double range) {
        if (level == null || center == null) return null;
        AABB area = new AABB(center).inflate(range);
        List<LivingEntity> customers = level.getEntitiesOfClass(LivingEntity.class, area,
            c -> c.isAlive() && isChairCustomer(c));
        if (customerId != null && !customerId.isEmpty()) {
            for (LivingEntity c : customers) {
                if (customerId.equals(getCustomerId(c))) {
                    return c;
                }
            }
            // 修复：没有找到匹配ID的顾客时，返回null而不是第一个顾客
            if (WARNED_MISSING_BY_ORDER.add(customerId)) {
                MaidRestaurantBusiness.LOGGER.warn("findCustomerById: 未找到匹配ID {} 的顾客", customerId);
            }
            return null;
        }
        if (customers.isEmpty()) {
            return null;
        }
        return customers.get(0);
    }
}
