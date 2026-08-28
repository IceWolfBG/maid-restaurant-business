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

import com.icewolf.maidrestaurant.business.config.BusinessConfig;
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
                        .then(Commands.literal("PRESTIGE").executes(ctx -> setPriority(ctx, BusinessConfig.PriorityMode.PRESTIGE)))
                        .then(Commands.literal("FIFO").executes(ctx -> setPriority(ctx, BusinessConfig.PriorityMode.FIFO))))
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
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u81ea\u52a8\u63a5\u5355: \u00a7f" + BusinessConfig.autoAccept)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u63a5\u5916\u5356\u5355: \u00a7f" + BusinessConfig.acceptDelivery)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u81ea\u52a8\u88c5\u76d8: \u00a7f" + BusinessConfig.autoPack)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u4f8d\u8005\u9001\u9910: \u00a7f" + BusinessConfig.waiterDeliver)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u81ea\u52a8\u6d17\u7897: \u00a7f" + BusinessConfig.autoWash)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u4f18\u5148\u7ea7: \u00a7f" + BusinessConfig.priorityMode)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u6700\u5927\u8ba2\u5355\u6570: \u00a7f" + BusinessConfig.maxPendingOrders)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u641c\u7d22\u8303\u56f4: \u00a7f" + BusinessConfig.searchRange)), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u81ea\u52a8\u63a5\u5355\u5ef6\u8fdf: \u00a7f" + BusinessConfig.acceptDelay + "tick (" + (BusinessConfig.acceptDelay / 20) + "秒)")), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u6d17\u7897\u9608\u503c: \u00a7f" + BusinessConfig.minPlatesToWash + "个脏盘子")), false);
        src.sendSuccess(() -> Component.literal((String)("\u00a7e\u7b49\u7ea7\u89e3\u9501: \u00a7f" + BusinessConfig.levelBasedProgression + " \u00a77(false=\u5168\u90e8\u529f\u80fd\u76f4\u63a5\u5f00\u542f)")), false);
        src.sendSuccess(() -> Component.literal((String)"\u00a77\u4fee\u6539: /mrb set <\u9009\u9879> <\u503c>"), false);
        return 1;
    }

    private static int setBool(CommandContext<CommandSourceStack> ctx, String key) {
        boolean value = BoolArgumentType.getBool(ctx, (String)"value");
        switch (key) {
            case "autoAccept": {
                BusinessConfig.autoAccept = value;
                BusinessConfig.AUTO_ACCEPT.set(value);
                break;
            }
            case "acceptDelivery": {
                BusinessConfig.acceptDelivery = value;
                BusinessConfig.ACCEPT_DELIVERY.set(value);
                break;
            }
            case "autoPack": {
                BusinessConfig.autoPack = value;
                BusinessConfig.AUTO_PACK.set(value);
                break;
            }
            case "waiterDeliver": {
                BusinessConfig.waiterDeliver = value;
                BusinessConfig.WAITER_DELIVER.set(value);
                break;
            }
            case "autoWash": {
                BusinessConfig.autoWash = value;
                BusinessConfig.AUTO_WASH.set(value);
                break;
            }
            case "levelBasedProgression": {
                BusinessConfig.levelBasedProgression = value;
                BusinessConfig.LEVEL_BASED_PROGRESSION.set(value);
            }
        }
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal((String)("\u00a7a\u5df2\u8bbe\u7f6e " + key + " = " + value)), false);
        return 1;
    }

    private static int setPriority(CommandContext<CommandSourceStack> ctx, BusinessConfig.PriorityMode mode) {
        BusinessConfig.priorityMode = mode;
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal((String)("\u00a7a\u5df2\u8bbe\u7f6e\u4f18\u5148\u7ea7 = " + mode)), false);
        return 1;
    }

    private static int setInt(CommandContext<CommandSourceStack> ctx, String key) {
        int value = IntegerArgumentType.getInteger(ctx, (String)"value");
        switch (key) {
            case "maxPendingOrders": {
                BusinessConfig.maxPendingOrders = value;
                BusinessConfig.MAX_PENDING_ORDERS.set(value);
                break;
            }
            case "searchRange": {
                BusinessConfig.searchRange = value;
                BusinessConfig.SEARCH_RANGE.set(value);
                break;
            }
            case "acceptDelay": {
                BusinessConfig.acceptDelay = value;
                BusinessConfig.ACCEPT_DELAY.set(value);
                break;
            }
            case "minPlatesToWash": {
                BusinessConfig.minPlatesToWash = value;
                BusinessConfig.MIN_PLATES_TO_WASH.set(value);
                break;
            }
        }
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal((String)("\u00a7a\u5df2\u8bbe\u7f6e " + key + " = " + value)), false);
        return 1;
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        BusinessConfig.autoAccept = (Boolean)BusinessConfig.AUTO_ACCEPT.get();
        BusinessConfig.acceptDelivery = (Boolean)BusinessConfig.ACCEPT_DELIVERY.get();
        BusinessConfig.autoPack = (Boolean)BusinessConfig.AUTO_PACK.get();
        BusinessConfig.waiterDeliver = (Boolean)BusinessConfig.WAITER_DELIVER.get();
        BusinessConfig.autoWash = (Boolean)BusinessConfig.AUTO_WASH.get();
        BusinessConfig.priorityMode = (BusinessConfig.PriorityMode)(BusinessConfig.PRIORITY_MODE.get());
        BusinessConfig.maxPendingOrders = (Integer)BusinessConfig.MAX_PENDING_ORDERS.get();
        BusinessConfig.searchRange = (Integer)BusinessConfig.SEARCH_RANGE.get();
        BusinessConfig.acceptDelay = (Integer)BusinessConfig.ACCEPT_DELAY.get();
        BusinessConfig.minPlatesToWash = (Integer)BusinessConfig.MIN_PLATES_TO_WASH.get();
        BusinessConfig.levelBasedProgression = (Boolean)BusinessConfig.LEVEL_BASED_PROGRESSION.get();
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal((String)"\u00a7a\u914d\u7f6e\u5df2\u4ece\u6587\u4ef6\u91cd\u65b0\u52a0\u8f7d"), false);
        return 1;
    }
}
