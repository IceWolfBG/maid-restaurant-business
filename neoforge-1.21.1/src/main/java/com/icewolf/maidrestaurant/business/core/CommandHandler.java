package com.icewolf.maidrestaurant.business.core;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;

@EventBusSubscriber(modid = MaidRestaurantBusiness.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class CommandHandler {
    /**
     * 安全保存配置值
     * 先修改运行时静态字段（立即生效），再尝试保存到配置文件
     * 如果配置对象未分配，保存失败但不影响运行时功能
     */
    private static void safeSetBoolean(net.neoforged.neoforge.common.ModConfigSpec.BooleanValue configValue, boolean value) {
        try {
            configValue.set(value);
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.warn("配置保存失败（运行时值已生效）: " + e.getMessage());
        }
    }

    private static void safeSetInt(net.neoforged.neoforge.common.ModConfigSpec.IntValue configValue, int value) {
        try {
            configValue.set(value);
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.warn("配置保存失败（运行时值已生效）: " + e.getMessage());
        }
    }

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
        MaidRestaurantBusiness.LOGGER.info("CommandHandler: /mrb 命令注册成功");
    }

    private static int showStatus(CommandContext<CommandSourceStack> ctx) {
        try {
            CommandSourceStack src = ctx.getSource();
            src.sendSuccess(() -> Component.literal("§6=== 女仆餐厅：经营 配置 ==="), false);
            src.sendSuccess(() -> Component.literal("§e自动接单: §f" + BusinessConfig.autoAccept), false);
            src.sendSuccess(() -> Component.literal("§e接外卖单: §f" + BusinessConfig.acceptDelivery), false);
            src.sendSuccess(() -> Component.literal("§e自动装盘: §f" + BusinessConfig.autoPack), false);
            src.sendSuccess(() -> Component.literal("§e侍者送餐: §f" + BusinessConfig.waiterDeliver), false);
            src.sendSuccess(() -> Component.literal("§e自动洗碗: §f" + BusinessConfig.autoWash), false);
            src.sendSuccess(() -> Component.literal("§e优先级: §f" + BusinessConfig.priorityMode), false);
            src.sendSuccess(() -> Component.literal("§e最大订单数: §f" + BusinessConfig.maxPendingOrders), false);
            src.sendSuccess(() -> Component.literal("§e搜索范围: §f" + BusinessConfig.searchRange), false);
            src.sendSuccess(() -> Component.literal("§e自动接单延迟: §f" + BusinessConfig.acceptDelay + "tick (" + (BusinessConfig.acceptDelay / 20) + "秒)"), false);
            src.sendSuccess(() -> Component.literal("§e洗碗阈值: §f" + BusinessConfig.minPlatesToWash + "个脏盘子"), false);
            src.sendSuccess(() -> Component.literal("§e等级解锁: §f" + BusinessConfig.levelBasedProgression + " §7(false=全部功能直接开启)"), false);
            src.sendSuccess(() -> Component.literal("§7修改: /mrb set <选项> <值>"), false);
            return 1;
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("CommandHandler.showStatus 执行失败", e);
            ctx.getSource().sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int setBool(CommandContext<CommandSourceStack> ctx, String key) {
        try {
            boolean value = BoolArgumentType.getBool(ctx, "value");
            switch (key) {
                case "autoAccept":
                    BusinessConfig.autoAccept = value;
                    safeSetBoolean(BusinessConfig.AUTO_ACCEPT, value);
                    break;
                case "acceptDelivery":
                    BusinessConfig.acceptDelivery = value;
                    safeSetBoolean(BusinessConfig.ACCEPT_DELIVERY, value);
                    break;
                case "autoPack":
                    BusinessConfig.autoPack = value;
                    safeSetBoolean(BusinessConfig.AUTO_PACK, value);
                    break;
                case "waiterDeliver":
                    BusinessConfig.waiterDeliver = value;
                    safeSetBoolean(BusinessConfig.WAITER_DELIVER, value);
                    break;
                case "autoWash":
                    BusinessConfig.autoWash = value;
                    safeSetBoolean(BusinessConfig.AUTO_WASH, value);
                    break;
                case "levelBasedProgression":
                    BusinessConfig.levelBasedProgression = value;
                    safeSetBoolean(BusinessConfig.LEVEL_BASED_PROGRESSION, value);
                    break;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§a已设置 " + key + " = " + value), false);
            return 1;
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("CommandHandler.setBool 执行失败, key=" + key, e);
            ctx.getSource().sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int setPriority(CommandContext<CommandSourceStack> ctx, BusinessConfig.PriorityMode mode) {
        try {
            BusinessConfig.priorityMode = mode;
            ctx.getSource().sendSuccess(() -> Component.literal("§a已设置优先级 = " + mode), false);
            return 1;
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("CommandHandler.setPriority 执行失败", e);
            ctx.getSource().sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int setInt(CommandContext<CommandSourceStack> ctx, String key) {
        try {
            int value = IntegerArgumentType.getInteger(ctx, "value");
            switch (key) {
                case "maxPendingOrders":
                    BusinessConfig.maxPendingOrders = value;
                    safeSetInt(BusinessConfig.MAX_PENDING_ORDERS, value);
                    break;
                case "searchRange":
                    BusinessConfig.searchRange = value;
                    safeSetInt(BusinessConfig.SEARCH_RANGE, value);
                    break;
                case "acceptDelay":
                    BusinessConfig.acceptDelay = value;
                    safeSetInt(BusinessConfig.ACCEPT_DELAY, value);
                    break;
                case "minPlatesToWash":
                    BusinessConfig.minPlatesToWash = value;
                    safeSetInt(BusinessConfig.MIN_PLATES_TO_WASH, value);
                    break;
            }
            ctx.getSource().sendSuccess(() -> Component.literal("§a已设置 " + key + " = " + value), false);
            return 1;
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("CommandHandler.setInt 执行失败, key=" + key, e);
            ctx.getSource().sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
            return 0;
        }
    }

    private static int reloadConfig(CommandContext<CommandSourceStack> ctx) {
        try {
            BusinessConfig.autoAccept = BusinessConfig.AUTO_ACCEPT.get();
            BusinessConfig.acceptDelivery = BusinessConfig.ACCEPT_DELIVERY.get();
            BusinessConfig.autoPack = BusinessConfig.AUTO_PACK.get();
            BusinessConfig.waiterDeliver = BusinessConfig.WAITER_DELIVER.get();
            BusinessConfig.autoWash = BusinessConfig.AUTO_WASH.get();
            BusinessConfig.priorityMode = BusinessConfig.PRIORITY_MODE.get();
            BusinessConfig.maxPendingOrders = BusinessConfig.MAX_PENDING_ORDERS.get();
            BusinessConfig.searchRange = BusinessConfig.SEARCH_RANGE.get();
            BusinessConfig.acceptDelay = BusinessConfig.ACCEPT_DELAY.get();
            BusinessConfig.minPlatesToWash = BusinessConfig.MIN_PLATES_TO_WASH.get();
            BusinessConfig.levelBasedProgression = BusinessConfig.LEVEL_BASED_PROGRESSION.get();
            ctx.getSource().sendSuccess(() -> Component.literal("§a配置已从文件重新加载"), false);
            return 1;
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.error("CommandHandler.reloadConfig 执行失败", e);
            ctx.getSource().sendFailure(Component.literal("命令执行失败: " + e.getMessage()));
            return 0;
        }
    }
}
