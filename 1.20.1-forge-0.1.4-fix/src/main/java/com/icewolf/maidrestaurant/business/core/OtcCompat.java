package com.icewolf.maidrestaurant.business.core;

import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;

/**
 * Order to Cook 兼容工具类
 * 同时支持 Forge 版本（RegistryObject<Item>）和 Fabric 版本（直接 Item）
 * 通过反射获取 ModItems 字段，避免 NoSuchFieldError
 */
public class OtcCompat {
    private static final Logger LOGGER = LogManager.getLogger("OtcCompat");
    private static Class<?> modItemsClass = null;
    private static boolean initialized = false;

    static {
        try {
            modItemsClass = Class.forName("cn.breezeth.ordertocook.registry.ModItems");
            initialized = true;
        } catch (ClassNotFoundException e) {
            LOGGER.error("OtcCompat: 未找到 ModItems 类，ordertocook 可能未安装");
        }
    }

    /**
     * 通过反射获取 ModItems 字段的值，自动处理 RegistryObject 和直接 Item
     */
    private static Item getModItem(String fieldName, String fallbackId) {
        if (!initialized || modItemsClass == null) {
            return getItemById(fallbackId);
        }
        try {
            Field field = modItemsClass.getField(fieldName);
            Object value = field.get(null);
            if (value == null) {
                return getItemById(fallbackId);
            }
            // Forge 版本：RegistryObject<Item>
            if (value instanceof net.minecraftforge.registries.RegistryObject) {
                Object item = ((net.minecraftforge.registries.RegistryObject<?>) value).get();
                if (item instanceof Item) {
                    return (Item) item;
                }
            }
            // Fabric 版本：直接 Item
            if (value instanceof Item) {
                return (Item) value;
            }
            // 其他情况：尝试调用 get() 方法
            try {
                Object item = value.getClass().getMethod("get").invoke(value);
                if (item instanceof Item) {
                    return (Item) item;
                }
            } catch (Exception ignored) {}
            LOGGER.warn("OtcCompat: 字段 {} 类型未知: {}, 使用 fallback: {}", fieldName, value.getClass().getName(), fallbackId);
            return getItemById(fallbackId);
        } catch (NoSuchFieldException e) {
            LOGGER.warn("OtcCompat: 未找到字段 {}，使用 fallback: {}", fieldName, fallbackId);
            return getItemById(fallbackId);
        } catch (IllegalAccessException e) {
            LOGGER.error("OtcCompat: 访问字段 {} 失败", fieldName, e);
            return getItemById(fallbackId);
        }
    }

    private static Item getItemById(String id) {
        return ForgeRegistries.ITEMS.getValue(new ResourceLocation(id));
    }

    // 常用物品的便捷获取方法
    public static Item ORDER() { return getModItem("ORDER", "ordertocook:order"); }
    public static Item FOOD_PLATE() { return getModItem("FOOD_PLATE", "ordertocook:food_plate"); }
    public static Item TAKEOUT_BAG() { return getModItem("TAKEOUT_BAG", "ordertocook:takeout_bag"); }
    public static Item CLEAN_PLATE() { return getModItem("CLEAN_PLATE", "ordertocook:clean_plate"); }
    public static Item DIRTY_PLATE() { return getModItem("DIRTY_PLATE", "ordertocook:dirty_plate"); }
    public static Item COUNTERTOP() { return getModItem("COUNTERTOP", "ordertocook:countertop"); }
    public static Item ORDER_MACHINE() { return getModItem("ORDER_MACHINE", "ordertocook:order_machine"); }
    public static Item CHAIR() { return getModItem("CHAIR", "ordertocook:chair"); }
    public static Item SHELF() { return getModItem("SHELF", "ordertocook:shelf"); }
    public static Item DISHWASHER() { return getModItem("DISHWASHER", "ordertocook:dishwasher"); }
    public static Item WASHINGTABLE() { return getModItem("WASHINGTABLE", "ordertocook:washingtable"); }
    public static Item PLATE_SHELF() { return getModItem("PLATE_SHELF", "ordertocook:plate_shelf"); }
}
