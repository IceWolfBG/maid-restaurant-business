package com.icewolf.maidrestaurant.business.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * 任务安全与超时保护配置
 * 单独的配置文件（maid_restaurant_business-safety.toml）
 * 类型为COMMON，服务端可读取，用于TaskManager的超时检测和异常处理
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "maid_restaurant_business", bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class TaskSafetyConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // ========== 任务超时时间（tick，20tick=1秒） ==========
    public static final ModConfigSpec.IntValue TASK_TIMEOUT_PACKAGING;
    public static final ModConfigSpec.IntValue TASK_TIMEOUT_DELIVERY;
    public static final ModConfigSpec.IntValue TASK_TIMEOUT_COOKING;
    public static final ModConfigSpec.IntValue TASK_TIMEOUT_PREP;
    public static final ModConfigSpec.IntValue TASK_TIMEOUT_DISHWASHING;
    public static final ModConfigSpec.IntValue TASK_TIMEOUT_COLLECT_PLATE;

    // ========== PENDING任务超时 ==========
    public static final ModConfigSpec.IntValue PENDING_TASK_TIMEOUT;

    // ========== 厨具占用超时 ==========
    public static final ModConfigSpec.IntValue DEVICE_OCCUPY_TIMEOUT;

    // ========== 重试与检测 ==========
    public static final ModConfigSpec.IntValue MAX_RETRIES;
    public static final ModConfigSpec.IntValue CHECK_INTERVAL;

    // ========== 激进卡住检测 ==========
    public static final ModConfigSpec.DoubleValue STUCK_DETECTION_MULTIPLIER;

    // ========== 运行时缓存值 ==========
    public static int taskTimeoutPackaging;
    public static int taskTimeoutDelivery;
    public static int taskTimeoutCooking;
    public static int taskTimeoutPrep;
    public static int taskTimeoutDishwashing;
    public static int taskTimeoutCollectPlate;
    public static int pendingTaskTimeout;
    public static int deviceOccupyTimeout;
    public static int maxRetries;
    public static int checkInterval;
    public static double stuckDetectionMultiplier;

    static {
        BUILDER.push("task_timeouts");
        BUILDER.comment("任务超时时间设置（单位：tick，20tick = 1秒）");

        TASK_TIMEOUT_PACKAGING = BUILDER
                .comment("打包任务超时时间（默认600tick = 30秒）")
                .defineInRange("taskTimeoutPackaging", 600, 100, 12000);

        TASK_TIMEOUT_DELIVERY = BUILDER
                .comment("配送任务超时时间（默认1200tick = 60秒）")
                .defineInRange("taskTimeoutDelivery", 1200, 100, 12000);

        TASK_TIMEOUT_COOKING = BUILDER
                .comment("烹饪任务超时时间（默认1800tick = 90秒，每道菜独立计时）")
                .defineInRange("taskTimeoutCooking", 1800, 100, 12000);

        TASK_TIMEOUT_PREP = BUILDER
                .comment("备菜任务超时时间（默认1200tick = 60秒）")
                .defineInRange("taskTimeoutPrep", 1200, 100, 12000);

        TASK_TIMEOUT_DISHWASHING = BUILDER
                .comment("洗碗任务超时时间（默认1200tick = 60秒）")
                .defineInRange("taskTimeoutDishwashing", 1200, 100, 12000);

        TASK_TIMEOUT_COLLECT_PLATE = BUILDER
                .comment("收盘子任务超时时间（默认300tick = 15秒）")
                .defineInRange("taskTimeoutCollectPlate", 300, 100, 12000);

        BUILDER.pop();

        BUILDER.push("pending_and_device");
        BUILDER.comment("PENDING任务与厨具占用超时设置");

        PENDING_TASK_TIMEOUT = BUILDER
                .comment("PENDING任务无女仆领取超时时间（默认600tick = 30秒，超时后自动移除任务）")
                .defineInRange("pendingTaskTimeout", 600, 100, 12000);

        DEVICE_OCCUPY_TIMEOUT = BUILDER
                .comment("厨具占用超时时间（默认200tick = 10秒，超时后自动释放厨具占用）")
                .defineInRange("deviceOccupyTimeout", 200, 50, 6000);

        BUILDER.pop();

        BUILDER.push("retry_and_detection");
        BUILDER.comment("重试次数与检测间隔设置");

        MAX_RETRIES = BUILDER
                .comment("任务失败最大重试次数（默认1次，超时后重试1次，仍失败则移除任务）")
                .defineInRange("maxRetries", 1, 0, 5);

        CHECK_INTERVAL = BUILDER
                .comment("任务状态检测间隔（默认10tick = 0.5秒，越小检测越频繁但性能消耗越大）")
                .defineInRange("checkInterval", 10, 5, 100);

        STUCK_DETECTION_MULTIPLIER = BUILDER
                .comment("激进卡住检测倍数（默认3.0倍，任务创建后超过 超时时间×倍数 则强制释放，即使心跳正常）")
                .defineInRange("stuckDetectionMultiplier", 3.0, 1.5, 10.0);

        BUILDER.pop();

        SPEC = BUILDER.build();

        // 初始化默认值
        taskTimeoutPackaging = 600;
        taskTimeoutDelivery = 1200;
        taskTimeoutCooking = 1800;
        taskTimeoutPrep = 1200;
        taskTimeoutDishwashing = 1200;
        taskTimeoutCollectPlate = 300;
        pendingTaskTimeout = 600;
        deviceOccupyTimeout = 200;
        maxRetries = 1;
        checkInterval = 10;
        stuckDetectionMultiplier = 3.0;
    }

    /**
     * 注册配置到NeoForge配置系统
     */
    public static void register(net.neoforged.bus.api.IEventBus modEventBus) {
        modEventBus.register(TaskSafetyConfig.class);
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            loadValues();
        }
    }

    @SubscribeEvent
    static void onReload(ModConfigEvent.Reloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            loadValues();
        }
    }

    /**
     * 从配置文件加载值到运行时缓存
     */
    private static void loadValues() {
        taskTimeoutPackaging = TASK_TIMEOUT_PACKAGING.get();
        taskTimeoutDelivery = TASK_TIMEOUT_DELIVERY.get();
        taskTimeoutCooking = TASK_TIMEOUT_COOKING.get();
        taskTimeoutPrep = TASK_TIMEOUT_PREP.get();
        taskTimeoutDishwashing = TASK_TIMEOUT_DISHWASHING.get();
        taskTimeoutCollectPlate = TASK_TIMEOUT_COLLECT_PLATE.get();
        pendingTaskTimeout = PENDING_TASK_TIMEOUT.get();
        deviceOccupyTimeout = DEVICE_OCCUPY_TIMEOUT.get();
        maxRetries = MAX_RETRIES.get();
        checkInterval = CHECK_INTERVAL.get();
        stuckDetectionMultiplier = STUCK_DETECTION_MULTIPLIER.get();
    }

    /**
     * 根据任务类型获取超时时间
     */
    public static long getTaskTimeout(String taskType) {
        return switch (taskType) {
            case "packaging" -> taskTimeoutPackaging;
            case "delivery" -> taskTimeoutDelivery;
            case "cooking" -> taskTimeoutCooking;
            case "prep" -> taskTimeoutPrep;
            case "dishwashing" -> taskTimeoutDishwashing;
            case "collect_plate" -> taskTimeoutCollectPlate;
            default -> 1200L;
        };
    }
}
