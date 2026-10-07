package com.icewolf.maidrestaurant.business.util;

import org.slf4j.Logger;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * 统一日志节流工具：按 key 限流，避免「幽灵忙碌 / 卡住自愈 / 状态校正」类日志
 * 在异常状态被反复制造时刷屏。同一 key 在 intervalTicks 内最多输出一次，默认 200tick（10秒）。
 *
 * 注意：本工具只节流「日志」，不节流实际的清理/重置动作——动作每次都必须执行。
 */
public final class LogThrottle {
    private LogThrottle() {}

    /** 默认节流间隔：200tick = 10秒 */
    public static final long DEFAULT_INTERVAL = 200L;
    private static final int PRUNE_THRESHOLD = 256;
    private static final long PRUNE_AGE = 6000L; // 超过5分钟未再触发的条目清理

    private static final Map<String, Long> LAST = new HashMap<>();

    /** 是否允许该 key 在 gameTime 输出（默认 10 秒一次）；允许时记录本次时间 */
    public static boolean allow(String key, long gameTime) {
        return allow(key, gameTime, DEFAULT_INTERVAL);
    }

    /** 是否允许该 key 在 gameTime 输出（自定义间隔） */
    public static boolean allow(String key, long gameTime, long intervalTicks) {
        Long last = LAST.get(key);
        if (last != null && gameTime - last < intervalTicks) {
            return false;
        }
        LAST.put(key, gameTime);
        if (LAST.size() > PRUNE_THRESHOLD) {
            prune(gameTime);
        }
        return true;
    }

    /** 节流 WARN（默认 10 秒一条），返回是否实际输出 */
    public static boolean warn(Logger logger, String key, long gameTime, String pattern, Object... args) {
        if (allow(key, gameTime)) {
            logger.warn(pattern, args);
            return true;
        }
        return false;
    }

    /** 节流 WARN（自定义间隔），返回是否实际输出 */
    public static boolean warn(Logger logger, String key, long gameTime, long intervalTicks,
                               String pattern, Object... args) {
        if (allow(key, gameTime, intervalTicks)) {
            logger.warn(pattern, args);
            return true;
        }
        return false;
    }

    private static void prune(long gameTime) {
        Iterator<Map.Entry<String, Long>> it = LAST.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Long> e = it.next();
            if (gameTime - e.getValue() > PRUNE_AGE) {
                it.remove();
            }
        }
    }
}
