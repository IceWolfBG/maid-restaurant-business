package com.icewolf.maidrestaurant.business.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 自动化功能开关配置（maid_restaurant_business/automation.toml）。
 * 只放“是否启用某类自动化”的布尔开关，方便玩家一眼对照排班表/打单机开关。
 */
@Mod.EventBusSubscriber(modid = "maid_restaurant_business", bus = Mod.EventBusSubscriber.Bus.MOD)
public class AutomationConfig {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.BooleanValue AUTO_ACCEPT;
    public static final ForgeConfigSpec.BooleanValue ACCEPT_DELIVERY;
    public static final ForgeConfigSpec.BooleanValue AUTO_PACK;
    public static final ForgeConfigSpec.BooleanValue WAITER_DELIVER;
    public static final ForgeConfigSpec.BooleanValue AUTO_WASH;
    public static final ForgeConfigSpec.BooleanValue AUTO_PRE_COOKING;

    public static boolean autoAccept;
    public static boolean acceptDelivery;
    public static boolean autoPack;
    public static boolean waiterDeliver;
    public static boolean autoWash;
    public static boolean autoPreCooking;

    static {
        BUILDER.push("automation");
        AUTO_ACCEPT = BUILDER
                .comment("自动接单开关（打单机旁有订单菜单展示框时生效；含到店顾客接待与打单机订单自动入台）")
                .define("autoAccept", true);
        ACCEPT_DELIVERY = BUILDER
                .comment("是否接外卖（配送）订单")
                .define("acceptDelivery", false);
        AUTO_PACK = BUILDER
                .comment("自动装盘/打包开关")
                .define("autoPack", true);
        WAITER_DELIVER = BUILDER
                .comment("侍者女仆自动送餐给顾客")
                .define("waiterDeliver", true);
        AUTO_WASH = BUILDER
                .comment("侍者女仆自动收脏盘子并洗碗")
                .define("autoWash", true);
        AUTO_PRE_COOKING = BUILDER
                .comment(
                        "自动预烹饪：厨师提前烹饪挂单夹里订单所需的食物（订单尚未放入操作台时也会先做，成品优先存入冰箱）。",
                        "需打单机达到自动预烹饪对应的等级才会生效。")
                .define("autoPreCooking", true);
        BUILDER.pop();
        SPEC = BUILDER.build();

        autoAccept = true;
        acceptDelivery = false;
        autoPack = true;
        waiterDeliver = true;
        autoWash = true;
        autoPreCooking = true;
    }

    @SubscribeEvent
    static void onLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            autoAccept = AUTO_ACCEPT.get();
            acceptDelivery = ACCEPT_DELIVERY.get();
            autoPack = AUTO_PACK.get();
            waiterDeliver = WAITER_DELIVER.get();
            autoWash = AUTO_WASH.get();
            autoPreCooking = AUTO_PRE_COOKING.get();
        }
    }
}
