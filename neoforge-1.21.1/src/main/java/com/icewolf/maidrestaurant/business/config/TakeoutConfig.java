package com.icewolf.maidrestaurant.business.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.config.ModConfigEvent;

/**
 * 酒狐速递站（外卖配送）独立配置文件
 * 不跟现有配置混在一起
 */
@net.neoforged.fml.common.EventBusSubscriber(modid = "maid_restaurant_business", bus = net.neoforged.fml.common.EventBusSubscriber.Bus.MOD)
public class TakeoutConfig {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    public static final ModConfigSpec SPEC;

    // 配送速度相关
    public static final ModConfigSpec.IntValue BASE_DELIVERY_SPEED;
    public static final ModConfigSpec.IntValue SPEED_PER_LEVEL;
    public static final ModConfigSpec.IntValue MIN_DELIVERY_SECONDS;
    public static final ModConfigSpec.IntValue MAX_DELIVERY_SECONDS;

    // 手续费相关
    public static final ModConfigSpec.DoubleValue BASE_FEE;
    public static final ModConfigSpec.DoubleValue FEE_PER_LEVEL;
    public static final ModConfigSpec.DoubleValue MIN_FEE;

    // 运行时缓存值
    public static int baseDeliverySpeed;
    public static int speedPerLevel;
    public static int minDeliverySeconds;
    public static int maxDeliverySeconds;
    public static double baseFee;
    public static double feePerLevel;
    public static double minFee;

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            baseDeliverySpeed = BASE_DELIVERY_SPEED.get();
            speedPerLevel = SPEED_PER_LEVEL.get();
            minDeliverySeconds = MIN_DELIVERY_SECONDS.get();
            maxDeliverySeconds = MAX_DELIVERY_SECONDS.get();
            baseFee = BASE_FEE.get();
            feePerLevel = FEE_PER_LEVEL.get();
            minFee = MIN_FEE.get();
        }
    }

    static {
        BUILDER.push("jiuhu_station");

        BUILDER.push("delivery_speed");
        BASE_DELIVERY_SPEED = BUILDER
            .comment("基础配送速度（格/秒，默认2）")
            .defineInRange("baseDeliverySpeed", 2, 1, 20);
        SPEED_PER_LEVEL = BUILDER
            .comment("打单机每升一级增加的配送速度（格/秒，默认2）")
            .defineInRange("speedPerLevel", 2, 0, 10);
        MIN_DELIVERY_SECONDS = BUILDER
            .comment("最小配送时间（秒，默认10）")
            .defineInRange("minDeliverySeconds", 10, 1, 600);
        MAX_DELIVERY_SECONDS = BUILDER
            .comment("最大配送时间（秒，默认900=15分钟）")
            .defineInRange("maxDeliverySeconds", 900, 10, 3600);
        BUILDER.pop();

        BUILDER.push("fee");
        BASE_FEE = BUILDER
            .comment("基础手续费比例（0-1.0，默认0.4=40%）")
            .defineInRange("baseFee", 0.4, 0.0, 1.0);
        FEE_PER_LEVEL = BUILDER
            .comment("打单机每升一级减免的手续费比例（0-1.0，默认0.05=5%）")
            .defineInRange("feePerLevel", 0.05, 0.0, 0.5);
        MIN_FEE = BUILDER
            .comment("手续费下限比例（0-1.0，默认0.2=20%，再低就没人自己配送了）")
            .defineInRange("minFee", 0.2, 0.0, 1.0);
        BUILDER.pop();

        BUILDER.pop();
        SPEC = BUILDER.build();

        // 默认值初始化
        baseDeliverySpeed = 2;
        speedPerLevel = 2;
        minDeliverySeconds = 10;
        maxDeliverySeconds = 900;
        baseFee = 0.4;
        feePerLevel = 0.05;
        minFee = 0.2;
    }
}
