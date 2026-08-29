package com.icewolf.maidrestaurant.business.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;

@net.neoforged.fml.common.EventBusSubscriber(modid="maid_restaurant_business", bus=net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class BusinessConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue AUTO_ACCEPT;
    public static final ModConfigSpec.BooleanValue ACCEPT_DELIVERY;
    public static final ModConfigSpec.BooleanValue AUTO_PACK;
    public static final ModConfigSpec.BooleanValue WAITER_DELIVER;
    public static final ModConfigSpec.BooleanValue AUTO_WASH;
    public static final ModConfigSpec.EnumValue<PriorityMode> PRIORITY_MODE;
    public static final ModConfigSpec.IntValue MAX_PENDING_ORDERS;
    public static final ModConfigSpec.IntValue SEARCH_RANGE;
    public static final ModConfigSpec.IntValue ACCEPT_DELAY;
    public static final ModConfigSpec.BooleanValue LEVEL_BASED_PROGRESSION;
    public static final ModConfigSpec.IntValue MIN_PLATES_TO_WASH;
    public static final ModConfigSpec.IntValue DISH_SCAN_RANGE;
    public static final ModConfigSpec.DoubleValue FAVORABILITY_BONUS;
    public static final ModConfigSpec.IntValue BUBBLE_COOLDOWN;
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
    public static int dishScanRange;
    public static double favorabilityBonus;
    public static int bubbleCooldown;

    /**
     * 注册配置文件到NeoForge配置系统
     */
    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(BusinessConfig.class);
    }

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
            dishScanRange = (Integer)DISH_SCAN_RANGE.get();
            favorabilityBonus = (Double)FAVORABILITY_BONUS.get();
            bubbleCooldown = (Integer)BUBBLE_COOLDOWN.get();
        }
    }

    static {
        BUILDER.push("maid_restaurant_business");
        AUTO_ACCEPT = BUILDER.comment("自动接单开关（打单机旁有订单菜单展示框时生效）").define("autoAccept", false);
        ACCEPT_DELIVERY = BUILDER.comment("是否接外卖订单").define("acceptDelivery", false);
        AUTO_PACK = BUILDER.comment("自动装盘/打包开关").define("autoPack", true);
        WAITER_DELIVER = BUILDER.comment("侍者女仆自动送餐给顾客").define("waiterDeliver", true);
        AUTO_WASH = BUILDER.comment("侍者女仆自动收脏盘子并洗碗").define("autoWash", true);
        PRIORITY_MODE = BUILDER.comment("订单优先级：PRESTIGE=报酬优先, FIFO=先到先得").defineEnum("priorityMode", (Enum)PriorityMode.PRESTIGE);
        MAX_PENDING_ORDERS = BUILDER.comment("最大同时处理订单数").defineInRange("maxPendingOrders", 3, 1, 10);
        SEARCH_RANGE = BUILDER.comment("女仆搜索范围（方块）").defineInRange("searchRange", 16, 4, 48);
        ACCEPT_DELAY = BUILDER.comment("自动接单延迟（tick，20tick=1秒，默认200=10秒）").defineInRange("acceptDelay", 200, 0, 1200);
        LEVEL_BASED_PROGRESSION = BUILDER.comment("是否按打单机等级解锁自动化功能（false=全部功能直接开启）").define("levelBasedProgression", true);
        MIN_PLATES_TO_WASH = BUILDER.comment("女仆收集到多少个脏盘子后才去洗碗（1-10，默认3）").defineInRange("minPlatesToWash", 3, 1, 10);
        DISH_SCAN_RANGE = BUILDER.comment("收盘子和洗碗的扫描范围（以打单机为中心，方块，默认24，最大48）").defineInRange("dishScanRange", 24, 4, 48);
        FAVORABILITY_BONUS = BUILDER.comment("女仆好感度每级的收益加成比例（0-1.0，默认0.1=10%，0级无加成，1级+10%，2级+20%，3级+30%）").defineInRange("favorabilityBonus", 0.1, 0.0, 1.0);
        BUBBLE_COOLDOWN = BUILDER.comment("女仆对话气泡冷却时间（tick，20tick=1秒，默认200=10秒，0=无冷却）").defineInRange("bubbleCooldown", 200, 0, 1200);
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
        dishScanRange = 24;
        favorabilityBonus = 0.1;
        bubbleCooldown = 200;
    }

    /**
     * 获取气泡冷却时间
     */
    public static long getBubbleCooldown() {
        return bubbleCooldown;
    }

    public static enum PriorityMode {
        PRESTIGE,
        FIFO;

    }
}
