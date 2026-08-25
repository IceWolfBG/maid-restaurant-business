package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.core.BlockPos;

import java.lang.reflect.Method;
import java.util.List;

/**
 * 顾客兼容工具类
 * 支持 CustomerEntity（普通顾客）和车万女仆实体（当启用了车万女仆顾客兼容时）
 * 通过命令标签（otc_npc, otc_order:xxx）来识别顾客
 */
public class CustomerCompat {
    
    private static final String TAG_NPC = "otc_npc";
    private static final String TAG_ORDER_PREFIX = "otc_order:";
    private static final String TAG_TOUHOU_COMPLETION_ORDER_PREFIX = "otc_touhou_completion_order:";
    
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
        // 检查乘客
        if (entity.hasPassenger(entity)) {
            for (Entity passenger : entity.getPassengers()) {
                if (hasCommandTag(passenger, orderTag) || hasCommandTag(passenger, touhouOrderTag)) return true;
            }
        }
        return false;
    }
    
    /**
     * 通过反射检查实体是否有指定的命令标签
     * 兼容 Forge（getTags）和 Fabric（getCommandTags）
     */
    public static boolean hasCommandTag(Entity entity, String tag) {
        if (entity == null) return false;
        // 先尝试 getTags()（Forge版本）
        try {
            if (entity.getTags().contains(tag)) return true;
        } catch (Exception e) {
            // 忽略
        }
        // 再尝试 getCommandTags()（Fabric版本，通过反射）
        try {
            Method method = entity.getClass().getMethod("getCommandTags");
            Object result = method.invoke(entity);
            if (result instanceof java.util.Collection) {
                if (((java.util.Collection<?>)result).contains(tag)) return true;
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
            Method method = customer.getClass().getMethod("getCustomerId");
            Object result = method.invoke(customer);
            if (result instanceof String) {
                return (String)result;
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
            Method method = customer.getClass().getMethod("isChairCustomer");
            Object result = method.invoke(customer);
            if (result instanceof Boolean) {
                return (Boolean)result;
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
            Method method = customer.getClass().getMethod("isEatingActionActive");
            Object result = method.invoke(customer);
            if (result instanceof Boolean) {
                return (Boolean)result;
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
        
        // 调试：列出所有可能的顾客
        List<LivingEntity> allLiving = level.getEntitiesOfClass(LivingEntity.class, area, c -> c.isAlive());
        MaidRestaurantBusiness.LOGGER.info("CustomerCompat: 搜索顾客 orderId={}, 范围内活体数={}", orderId, allLiving.size());
        for (LivingEntity le : allLiving) {
            boolean hasOtcNpc = hasCommandTag(le, "otc_npc");
            boolean hasOrder = hasOrderTag(le, orderId);
            boolean isChair = isChairCustomer(le);
            boolean hasTouhouCompletion = hasCommandTag(le, "otc_touhou_completion");
            MaidRestaurantBusiness.LOGGER.info("  活体: {}={}, pos={}, isChair={}, otc_npc={}, orderTag={}, touhouCompletion={}, tags={}, commandTags={}", 
                le.getType().toShortString(), le.getName().getString(), le.blockPosition(), 
                isChair, hasOtcNpc, hasOrder, hasTouhouCompletion,
                le.getTags(), getCommandTagsSafe(le));
        }
        
        List<LivingEntity> customers = level.getEntitiesOfClass(LivingEntity.class, area, 
            c -> c.isAlive() && isChairCustomer(c) && hasOrderTag(c, orderId));
        if (customers.isEmpty()) {
            MaidRestaurantBusiness.LOGGER.info("CustomerCompat: 没有找到匹配的顾客 orderId={}, 尝试最近的顾客", orderId);
            List<LivingEntity> allCustomers = level.getEntitiesOfClass(LivingEntity.class, area, 
                c -> c.isAlive() && isChairCustomer(c) && hasCommandTag(c, "otc_npc") && !isEatingActionActive(c));
            if (!allCustomers.isEmpty()) {
                MaidRestaurantBusiness.LOGGER.info("CustomerCompat: 使用最近的顾客 {} 作为后备", allCustomers.get(0).getName().getString());
                return allCustomers.get(0);
            }
            return null;
        }
        MaidRestaurantBusiness.LOGGER.info("CustomerCompat: 找到匹配的顾客 {} orderId={}", customers.get(0).getName().getString(), orderId);
        return customers.get(0);
    }
    
    // 安全获取命令标签（兼容 Forge 和 Fabric）
    private static java.util.Collection<String> getCommandTagsSafe(Entity entity) {
        try {
            Method method = entity.getClass().getMethod("getCommandTags");
            Object result = method.invoke(entity);
            if (result instanceof java.util.Collection) {
                return (java.util.Collection<String>)result;
            }
        } catch (Exception e) {
            // 方法不存在
        }
        return java.util.Collections.emptyList();
    }
    
    /**
     * 查找指定顾客ID的顾客
     */
    public static LivingEntity findCustomerById(Level level, BlockPos center, String customerId, double range) {
        if (level == null || center == null) return null;
        AABB area = new AABB(center).inflate(range);
        List<LivingEntity> customers = level.getEntitiesOfClass(LivingEntity.class, area, 
            c -> c.isAlive() && isChairCustomer(c));
        MaidRestaurantBusiness.LOGGER.info("CustomerCompat.findCustomerById: customerId={}, 找到坐在椅子上的活体数={}", customerId, customers.size());
        for (LivingEntity c : customers) {
            MaidRestaurantBusiness.LOGGER.info("  顾客候选: {}={}, uuid={}, customerId={}, tags={}", 
                c.getType().toShortString(), c.getName().getString(), c.getStringUUID(), getCustomerId(c), c.getTags());
        }
        if (customerId != null && !customerId.isEmpty()) {
            for (LivingEntity c : customers) {
                if (customerId.equals(getCustomerId(c))) {
                    MaidRestaurantBusiness.LOGGER.info("CustomerCompat.findCustomerById: 找到匹配的顾客 {}", c.getName().getString());
                    return c;
                }
            }
        }
        if (customers.isEmpty()) {
            MaidRestaurantBusiness.LOGGER.info("CustomerCompat.findCustomerById: 没有找到任何顾客");
            return null;
        }
        MaidRestaurantBusiness.LOGGER.info("CustomerCompat.findCustomerById: 使用第一个顾客 {} 作为后备", customers.get(0).getName().getString());
        return customers.get(0);
    }
}
