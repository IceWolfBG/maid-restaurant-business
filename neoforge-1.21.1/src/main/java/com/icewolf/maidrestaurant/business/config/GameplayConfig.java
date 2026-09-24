package com.icewolf.maidrestaurant.business.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * 玩法与成长配置（maid_restaurant_business/gameplay.toml）。
 * 含等级解锁、订单调度、收益加成、洗碗阈值、气泡冷却等影响经营节奏的设定。
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "maid_restaurant_business", bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class GameplayConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    public static final ModConfigSpec.BooleanValue LEVEL_BASED_PROGRESSION;
    public static final ModConfigSpec.EnumValue<PriorityMode> PRIORITY_MODE;
    public static final ModConfigSpec.IntValue MAX_PENDING_ORDERS;
    public static final ModConfigSpec.IntValue ACCEPT_DELAY;
    public static final ModConfigSpec.IntValue MIN_PLATES_TO_WASH;
    public static final ModConfigSpec.DoubleValue FAVORABILITY_BONUS;
    public static final ModConfigSpec.IntValue BUBBLE_COOLDOWN;

    public static boolean levelBasedProgression;
    public static PriorityMode priorityMode;
    public static int maxPendingOrders;
    public static int acceptDelay;
    public static int minPlatesToWash;
    public static double favorabilityBonus;
    public static int bubbleCooldown;

    static {
        BUILDER.push("gameplay");
        LEVEL_BASED_PROGRESSION = BUILDER.comment("是否按打单机等级解锁自动化功能（false=全部功能直接开启）").define("levelBasedProgression", true);
        PRIORITY_MODE = BUILDER.comment("订单优先级：PRESTIGE=报酬优先, FIFO=先到先得").defineEnum("priorityMode", (Enum) PriorityMode.PRESTIGE);
        MAX_PENDING_ORDERS = BUILDER.comment("最大同时处理订单数").defineInRange("maxPendingOrders", 3, 1, 10);
        ACCEPT_DELAY = BUILDER.comment("自动接单延迟（tick，20tick=1秒，默认200=10秒）").defineInRange("acceptDelay", 200, 0, 1200);
        MIN_PLATES_TO_WASH = BUILDER.comment(
                "自动洗碗的脏盘子数量上限阈值（1-10，默认10）。",
                "实际洗碗阈值以排班表设定为准（排班表默认3）；本配置作为上限，",
                "排班表阈值不超过该值时按排班表生效，因此默认给到10，避免玩家调高排班表后仍被旧默认值3卡住。").defineInRange("minPlatesToWash", 10, 1, 10);
        FAVORABILITY_BONUS = BUILDER.comment("女仆好感度每级的收益加成比例（0-1.0，默认0.1=10%，0级无加成，1级+10%，2级+20%，3级+30%）").defineInRange("favorabilityBonus", 0.1, 0.0, 1.0);
        BUBBLE_COOLDOWN = BUILDER.comment("女仆对话气泡冷却时间（tick，20tick=1秒，默认200=10秒，0=无冷却）").defineInRange("bubbleCooldown", 200, 0, 1200);
        BUILDER.pop();
        SPEC = BUILDER.build();

        levelBasedProgression = true;
        priorityMode = PriorityMode.PRESTIGE;
        maxPendingOrders = 3;
        acceptDelay = 200;
        minPlatesToWash = 10;
        favorabilityBonus = 0.1;
        bubbleCooldown = 200;
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent.Loading event) {
        if (event.getConfig().getModId().equals("maid_restaurant_business")) {
            try {
                loadConfigValues();
            } catch (Exception ignored) {
                // 配置尚未完全加载，等待 Reloading 事件
            }
        }
    }

    @SubscribeEvent
    static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getModId().equals("maid_restaurant_business")) {
            loadConfigValues();
        }
    }

    private static void loadConfigValues() {
        levelBasedProgression = LEVEL_BASED_PROGRESSION.get();
        priorityMode = PRIORITY_MODE.get();
        maxPendingOrders = MAX_PENDING_ORDERS.get();
        acceptDelay = ACCEPT_DELAY.get();
        minPlatesToWash = MIN_PLATES_TO_WASH.get();
        favorabilityBonus = FAVORABILITY_BONUS.get();
        bubbleCooldown = BUBBLE_COOLDOWN.get();
    }

    /** 获取气泡冷却时间。 */
    public static long getBubbleCooldown() {
        return bubbleCooldown;
    }

    public enum PriorityMode {
        PRESTIGE,
        FIFO
    }
}
