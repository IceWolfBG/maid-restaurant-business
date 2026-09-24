/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.mojang.brigadier.CommandDispatcher
 *  com.mojang.brigadier.arguments.ArgumentType
 *  com.mojang.brigadier.arguments.BoolArgumentType
 *  com.mojang.brigadier.arguments.IntegerArgumentType
 *  com.mojang.brigadier.builder.LiteralArgumentBuilder
 *  com.mojang.brigadier.context.CommandContext
 *  net.minecraft.commands.CommandSourceStack
 *  net.minecraft.commands.Commands
 *  net.minecraft.network.chat.Component
 *  net.minecraftforge.event.RegisterCommandsEvent
 *  net.minecraftforge.eventbus.api.SubscribeEvent
 *  net.minecraftforge.fml.common.Mod$EventBusSubscriber
 */
package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.config.AutomationConfig;
import com.icewolf.maidrestaurant.business.config.GameplayConfig;
import com.icewolf.maidrestaurant.business.config.PerformanceConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.ArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid="maid_restaurant_business")
public class CommandHandler {
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(
            Commands.literal("mrb")
                .requires(src -> src.hasPermission(2))
                .executes(CommandHandler::showStatus)
                .then(Commands.literal("set")
                    .then(Commands.literal("autoAccept").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> setBool(ctx, "autoAccept"))))
                    .then(Commands.literal("acceptDelivery").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> setBool(ctx, "acceptDelivery"))))
                    .then(Commands.literal("autoPack").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> setBool(ctx, "autoPack"))))
                    .then(Commands.literal("waiterDeliver").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> setBool(ctx, "waiterDeliver"))))
                    .then(Commands.literal("autoWash").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> setBool(ctx, "autoWash"))))
                    .then(Commands.literal("priorityMode")
                        .then(Commands.literal("PRESTIGE").executes(ctx -> setPriority(ctx, GameplayConfig.PriorityMode.PRESTIGE)))
                        .then(Commands.literal("FIFO").executes(ctx -> setPriority(ctx, GameplayConfig.PriorityMode.FIFO))))
                    .then(Commands.literal("maxPendingOrders").then(Commands.argument("value", IntegerArgumentType.integer(1, 10)).executes(ctx -> setInt(ctx, "maxPendingOrders"))))
                    .then(Commands.literal("searchRange").then(Commands.argument("value", IntegerArgumentType.integer(4, 48)).executes(ctx -> setInt(ctx, "searchRange"))))
                    .then(Commands.literal("acceptDelay").then(Commands.argument("value", IntegerArgumentType.integer(0, 1200)).executes(ctx -> setInt(ctx, "acceptDelay"))))
                    .then(Commands.literal("minPlatesToWash").then(Commands.argument("value", IntegerArgumentType.integer(1, 10)).executes(ctx -> setInt(ctx, "minPlatesToWash"))))
                    .then(Commands.literal("levelBasedProgression").then(Commands.argument("value", BoolArgumentType.bool()).executes(ctx -> setBool(ctx, "levelBasedProgression"))))
                )
                .then(Commands.literal("reload").executes(CommandHandler::reloadConfig))
        );
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = (CommandSourceStack)ctx.getSource();
        src.sendSuccess(() -> Component.literal((String)"\u00a76=== \u5973\u4ec6\u9910\u5385\uff1a\u7ecf\u8425 \u914d\u7f6e ==="), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u81ea\u52a8\u63a5\u5355: \u00a7f" + AutomationConfig.autoAccept)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u63a5\u5916\u5356\u5355: \u00a7f" + AutomationConfig.acceptDelivery)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u81ea\u52a8\u88c5\u76d8: \u00a7f" + AutomationConfig.autoPack)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u4f8d\u8005\u9001\u9910: \u00a7f" + AutomationConfig.waiterDeliver)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u81ea\u52a8\u6d17\u7897: \u00a7f" + AutomationConfig.autoWash)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u4f18\u5148\u7ea7: \u00a7f" + GameplayConfig.priorityMode)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u6700\u5927\u8ba2\u5355\u6570: \u00a7f" + GameplayConfig.maxPendingOrders)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u641c\u7d22\u8303\u56f4: \u00a7f" + PerformanceConfig.searchRange)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u81ea\u52a8\u63a5\u5355\u5ef6\u8fdf: \u00a7f" + GameplayConfig.acceptDelay + "tick (" + (GameplayConfig.acceptDelay / 20) + "秒)")), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u6d17\u7897\u9608\u503c: \u00a7f" + GameplayConfig.minPlatesToWash + "个脏盘子")), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u7b49\u7ea7\u89e3\u9501: \u00a7f" + GameplayConfig.levelBasedProgression + " \u00a77(false=\u5168\u90e8\u529f\u80fd\u76f4\u63a5\u5f00\u542f)")), false);
        src.sendSuccess(() -> Component.literal((String)"\u00a77\u4fee\u6539: /mrb set <\u9009\u9879> <\u503c>"), false);
        return 1;
    }

    private static int setBool(CommandContext<CommandSourceStack> ctx, String key) {
        boolean value = BoolArgumentType.getBool(ctx, (String)"value");
        switch (key) {
            case "autoAccept": {
                AutomationConfig.autoAccept = value;
                AutomationConfig.AUTO_ACCEPT.set(value);
                break;
            }
            case "acceptDelivery": {
                AutomationConfig.acceptDelivery = value;
                AutomationConfig.ACCEPT_DELIVERY.set(value);
                break;
            }
            case "autoPack": {
                AutomationConfig.autoPack = value;
                AutomationConfig.AUTO_PACK.set(value);
                break;
            }
            case "waiterDeliver": {
                AutomationConfig.waiterDeliver = value;
                AutomationConfig.WAITER_DELIVER.set(value);
                break;
            }
            case "autoWash": {
                AutomationConfig.autoWash = value;
                AutomationConfig.AUTO_WASH.set(value);
                break;
            }
            case "levelBasedProgression": {
                GameplayConfig.levelBasedProgression = value;
                GameplayConfig.LEVEL_BASED_PROGRESSION.set(value);
            }
        }
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal((String)("\u00a7a\u5df2\u8bbe\u7f6e " + key + " = " + value)), false);
        return 1;
    }

    private static int setPriority(CommandContext<CommandSourceStack> ctx, GameplayConfig.PriorityMode mode) {
        GameplayConfig.priorityMode = mode;
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal((String)("\u00a7a\u5df2\u8bbe\u7f6e\u4f18\u5148\u7ea7 = " + mode)), false);
        return 1;
    }

    private static int setInt(CommandContext<CommandSourceStack> ctx, String key) {
        int value = IntegerArgumentType.getInteger(ctx, (String)"value");
        switch (key) {
            case "maxPendingOrders": {
                GameplayConfig.maxPendingOrders = value;
                GameplayConfig.MAX_PENDING_ORDERS.set(value);
                break;
            }
            case "searchRange": {
                PerformanceConfig.searchRange = value;
                PerformanceConfig.SEARCH_RANGE.set(value);
                break;
            }
            case "acceptDelay": {
                GameplayConfig.acceptDelay = value;
                GameplayConfig.ACCEPT_DELAY.set(value);
                break;
            }
            case "minPlatesToWash": {
                GameplayConfig.minPlatesToWash = value;
                GameplayConfig.MIN_PLATES_TO_WASH.set(value);
                break;
            }
        }
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal((String)("\u00a7a\u5df2\u8bbe\u7f6e " + key + " = " + value)), false);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        AutomationConfig.autoAccept = (Boolean)AutomationConfig.AUTO_ACCEPT.get();
        AutomationConfig.acceptDelivery = (Boolean)AutomationConfig.ACCEPT_DELIVERY.get();
        AutomationConfig.autoPack = (Boolean)AutomationConfig.AUTO_PACK.get();
        AutomationConfig.waiterDeliver = (Boolean)AutomationConfig.WAITER_DELIVER.get();
        AutomationConfig.autoWash = (Boolean)AutomationConfig.AUTO_WASH.get();
        GameplayConfig.priorityMode = (GameplayConfig.PriorityMode)(GameplayConfig.PRIORITY_MODE.get());
        GameplayConfig.maxPendingOrders = (Integer)GameplayConfig.MAX_PENDING_ORDERS.get();
        PerformanceConfig.searchRange = (Integer)PerformanceConfig.SEARCH_RANGE.get();
        GameplayConfig.acceptDelay = (Integer)GameplayConfig.ACCEPT_DELAY.get();
        GameplayConfig.minPlatesToWash = (Integer)GameplayConfig.MIN_PLATES_TO_WASH.get();
        GameplayConfig.levelBasedProgression = (Boolean)GameplayConfig.LEVEL_BASED_PROGRESSION.get();
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal((String)"\u00a7a\u914d\u7f6e\u5df2\u4ece\u6587\u4ef6\u91cd\u65b0\u52a0\u8f7d"), false);
        return 1;
    }
}
