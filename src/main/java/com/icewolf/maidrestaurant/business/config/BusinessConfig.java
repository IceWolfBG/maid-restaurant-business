/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraftforge.common.ForgeConfigSpec
 *  net.minecraftforge.common.ForgeConfigSpec$BooleanValue
 *  net.minecraftforge.common.ForgeConfigSpec$Builder
 *  net.minecraftforge.common.ForgeConfigSpec$EnumValue
 *  net.minecraftforge.common.ForgeConfigSpec$IntValue
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber$Bus
 *  net.minecraftforge.fml.event.config.ModConfigEvent
 */
package com.icewolf.maidrestaurant.business.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid="maid_restaurant_business", bus=Mod.EventBusSubscriber.Bus.MOD)
public class BusinessConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.BooleanValue AUTO_ACCEPT;
    public static final ForgeConfigSpec.BooleanValue ACCEPT_DELIVERY;
    public static final ForgeConfigSpec.BooleanValue AUTO_PACK;
    public static final ForgeConfigSpec.BooleanValue WAITER_DELIVER;
    public static final ForgeConfigSpec.BooleanValue AUTO_WASH;
    public static final ForgeConfigSpec.EnumValue<PriorityMode> PRIORITY_MODE;
    public static final ForgeConfigSpec.IntValue MAX_PENDING_ORDERS;
    public static final ForgeConfigSpec.IntValue SEARCH_RANGE;
    public static final ForgeConfigSpec.IntValue ACCEPT_DELAY;
    public static final ForgeConfigSpec.BooleanValue LEVEL_BASED_PROGRESSION;
    public static final ForgeConfigSpec.IntValue MIN_PLATES_TO_WASH;
    public static final ForgeConfigSpec.DoubleValue FAVORABILITY_BONUS;
    public static boolean autoAccept;
    public static boolean acceptDelivery;
    public static boolean autoPack;
    public static boolean waiterDeliver;
    public static boolean autoWash;
    public static PriorityMode priorityMode;
    public static int maxPendingOrders;
    public static boolean levelBasedProgression;
    public static int searchRange;
    public static int acceptDelay;
    public static int minPlatesToWash;
    public static double favorabilityBonus;

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            autoAccept = (Boolean)AUTO_ACCEPT.get();
            acceptDelivery = (Boolean)ACCEPT_DELIVERY.get();
            autoPack = (Boolean)AUTO_PACK.get();
            waiterDeliver = (Boolean)WAITER_DELIVER.get();
            autoWash = (Boolean)AUTO_WASH.get();
            priorityMode = (PriorityMode)(PRIORITY_MODE.get());
            maxPendingOrders = (Integer)MAX_PENDING_ORDERS.get();
            searchRange = (Integer)SEARCH_RANGE.get();
            acceptDelay = (Integer)ACCEPT_DELAY.get();
            levelBasedProgression = (Boolean)LEVEL_BASED_PROGRESSION.get();
            minPlatesToWash = (Integer)MIN_PLATES_TO_WASH.get();
            favorabilityBonus = (Double)FAVORABILITY_BONUS.get();
        }
    }

    static {
        BUILDER.push("maid_restaurant_business");
        AUTO_ACCEPT = BUILDER.comment("\u81ea\u52a8\u63a5\u5355\u5f00\u5173\uff08\u6253\u5355\u673a\u65c1\u6709\u8ba2\u5355\u83dc\u5355\u5c55\u793a\u6846\u65f6\u751f\u6548\uff09").define("autoAccept", false);
        ACCEPT_DELIVERY = BUILDER.comment("\u662f\u5426\u63a5\u5916\u5356\u8ba2\u5355").define("acceptDelivery", false);
        AUTO_PACK = BUILDER.comment("\u81ea\u52a8\u88c5\u76d8/\u6253\u5305\u5f00\u5173").define("autoPack", true);
        WAITER_DELIVER = BUILDER.comment("\u4f8d\u8005\u5973\u4ec6\u81ea\u52a8\u9001\u9910\u7ed9\u987e\u5ba2").define("waiterDeliver", true);
        AUTO_WASH = BUILDER.comment("\u4f8d\u8005\u5973\u4ec6\u81ea\u52a8\u6536\u810f\u76d8\u5b50\u5e76\u6d17\u7897").define("autoWash", true);
        PRIORITY_MODE = BUILDER.comment("\u8ba2\u5355\u4f18\u5148\u7ea7\uff1aPRESTIGE=\u62a5\u916c\u4f18\u5148, FIFO=\u5148\u5230\u5148\u5f97").defineEnum("priorityMode", (Enum)PriorityMode.PRESTIGE);
        MAX_PENDING_ORDERS = BUILDER.comment("\u6700\u5927\u540c\u65f6\u5904\u7406\u8ba2\u5355\u6570").defineInRange("maxPendingOrders", 3, 1, 10);
        SEARCH_RANGE = BUILDER.comment("\u5973\u4ec6\u641c\u7d22\u8303\u56f4\uff08\u65b9\u5757\uff09").defineInRange("searchRange", 16, 4, 48);
        ACCEPT_DELAY = BUILDER.comment("\u81ea\u52a8\u63a5\u5355\u5ef6\u8fdf\uff08tick\uff0c20tick=1\u79d2\uff0c\u9ed8\u8ba4200=10\u79d2\uff09").defineInRange("acceptDelay", 200, 0, 1200);
        LEVEL_BASED_PROGRESSION = BUILDER.comment("\u662f\u5426\u6309\u6253\u5355\u673a\u7b49\u7ea7\u89e3\u9501\u81ea\u52a8\u5316\u529f\u80fd\uff08false=\u5168\u90e8\u529f\u80fd\u76f4\u63a5\u5f00\u542f\uff09").define("levelBasedProgression", true);
        MIN_PLATES_TO_WASH = BUILDER.comment("\u5973\u4ec6\u6536\u96c6\u5230\u591a\u5c11\u4e2a\u810f\u76d8\u5b50\u540e\u624d\u53bb\u6d17\u7897\uff081-10\uff0c\u9ed8\u8ba43\uff09").defineInRange("minPlatesToWash", 3, 1, 10);
        FAVORABILITY_BONUS = BUILDER.comment("女仆好感度每级的收益加成比例（0-1.0，默认0.1=10%，0级无加成，1级+10%，2级+20%，3级+30%）").defineInRange("favorabilityBonus", 0.1, 0.0, 1.0);
        BUILDER.pop();
        SPEC = BUILDER.build();
        autoAccept = false;
        acceptDelivery = false;
        autoPack = true;
        waiterDeliver = true;
        autoWash = true;
        priorityMode = PriorityMode.PRESTIGE;
        maxPendingOrders = 3;
        levelBasedProgression = true;
        searchRange = 16;
        acceptDelay = 200;
        minPlatesToWash = 3;
        favorabilityBonus = 0.1;
    }

    public static enum PriorityMode {
        PRESTIGE,
        FIFO;

    }
}
