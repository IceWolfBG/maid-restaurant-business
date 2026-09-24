package com.icewolf.maidrestaurant.business.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 扫描范围与性能调试配置（maid_restaurant_business/performance.toml）。
 * 普通玩家一般无需改动；debugPerformance 仅在排查性能时临时开启。
 */
@Mod.EventBusSubscriber(modid = "maid_restaurant_business", bus = Mod.EventBusSubscriber.Bus.MOD)
public class PerformanceConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.IntValue SEARCH_RANGE;
    public static final ForgeConfigSpec.IntValue DISH_SCAN_RANGE;
    public static final ForgeConfigSpec.BooleanValue DEBUG_PERFORMANCE;

    public static int searchRange;
    public static int dishScanRange;
    public static boolean debugPerformance;

    static {
        BUILDER.push("performance");
        SEARCH_RANGE = BUILDER
                .comment("女仆搜索范围（方块）")
                .defineInRange("searchRange", 16, 4, 48);
        DISH_SCAN_RANGE = BUILDER
                .comment("收盘子和洗碗的扫描范围（以打单机为中心，方块，默认24，最大48）")
                .defineInRange("dishScanRange", 24, 4, 48);
        DEBUG_PERFORMANCE = BUILDER
                .comment(
                        "性能调试日志开关（默认关闭）。",
                        "开启后定期在日志输出扫描/同步/缓存命中统计，排查性能时使用，正常游玩请保持false。")
                .define("debugPerformance", false);
        BUILDER.pop();
        SPEC = BUILDER.build();

        searchRange = 16;
        dishScanRange = 24;
        debugPerformance = false;
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            searchRange = SEARCH_RANGE.get();
            dishScanRange = DISH_SCAN_RANGE.get();
            debugPerformance = DEBUG_PERFORMANCE.get();
        }
    }
}
