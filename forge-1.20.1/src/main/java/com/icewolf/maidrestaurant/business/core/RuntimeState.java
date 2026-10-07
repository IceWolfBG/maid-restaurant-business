package com.icewolf.maidrestaurant.business.core;

/**
 * 服务器启动时的运行时态重置。
 *
 * 单机集成环境下，退出存档只会停止（卸载）集成服务端，JVM 并不退出。各类单例与 static
 * 缓存若不清理，会残留上一存档的任务、占用与时间戳；而新服务端的 tick 计数从 0 重新开始，
 * 此时 {@code currentTick - lastCheckTick} 为负，会导致中心化任务管理器在 tick 计数爬回旧值前
 * 整体短路，表现为“退出存档再重进，所有自动化停摆”。故每次服务器启动统一把易失运行时态归零，
 * 女仆与方块会在新服务端运行后被重新扫描、任务重新发布。
 *
 * 边界：只清理易失运行时态；持久化数据（SavedData，如店铺收益、玩家订单托管）、
 * 反射与类级缓存（可跨服务端复用、与具体世界无关）不在清理范围内。
 */
public final class RuntimeState {

    private RuntimeState() {
    }

    public static void onServerStarting() {
        // 1) 核心单例整体丢弃：懒汉加载会在新服务端首次访问时以全新状态重建（时间戳/任务/缓存归零）
        TaskManager.resetInstance();
        CookingDeviceStatsManager.resetInstance();
        // 2) 各 Bridge / 工具的流程任务与强运行时状态
        CookingBridge.clearRuntimeState();
        DishwashingBridge.clearRuntimeState();
        PackagingBridge.clearRuntimeState();
        RestockBridge.clearRuntimeState();
        OrderFetchBridge.clearRuntimeState();
        OrderBridge.clearRuntimeState();
        MaidUtils.clearRuntimeState();
        TaskSafetyUtils.clearRuntimeState();
        // 3) 区块级方块实体索引按 ServerLevel 分桶，旧世界整体清空，新区块加载时重建
        WorldScanner.clearAll();
    }
}
