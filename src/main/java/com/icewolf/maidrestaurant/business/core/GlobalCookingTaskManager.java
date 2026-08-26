package com.icewolf.maidrestaurant.business.core;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局烹饪任务管理器
 * 负责收集所有操作台的所有订单的所有食物需求，合并去重，按优先级排序
 */
public class GlobalCookingTaskManager {
    private static GlobalCookingTaskManager instance;

    // 全局任务信息
    public static class GlobalTask {
        public final String itemId;           // 食物ID
        public int totalNeeded;                // 总需求数量
        public int currentlyCooking;           // 正在烹饪的数量
        public int alreadyInCounters;          // 已经在操作台的数量
        public final Map<Long, CounterOrderInfo> sources = new HashMap<>();  // 来源操作台
        public int priority;                   // 优先级（数字越大优先级越高）
        public long lastUpdateTime;            // 最后更新时间

        public GlobalTask(String itemId) {
            this.itemId = itemId;
            this.totalNeeded = 0;
            this.currentlyCooking = 0;
            this.alreadyInCounters = 0;
            this.priority = 0;
            this.lastUpdateTime = 0;
        }

        // 还需要烹饪的数量
        public int getRemainingToCook() {
            return Math.max(0, totalNeeded - currentlyCooking - alreadyInCounters);
        }
    }

    // 操作台订单信息
    public static class CounterOrderInfo {
        public final BlockPos counterPos;
        public final String orderId;
        public int needed;
        public int alreadyInCounter;
        public long orderCreateTime;

        public CounterOrderInfo(BlockPos counterPos, String orderId, int needed, long orderCreateTime) {
            this.counterPos = counterPos;
            this.orderId = orderId;
            this.needed = needed;
            this.alreadyInCounter = 0;
            this.orderCreateTime = orderCreateTime;
        }
    }

    // 全局任务表（itemId -> 任务信息）
    private final Map<String, GlobalTask> tasks = new ConcurrentHashMap<>();

    // 最后更新时间
    private long lastUpdateTick = 0;

    private GlobalCookingTaskManager() {}

    public static synchronized GlobalCookingTaskManager getInstance() {
        if (instance == null) {
            instance = new GlobalCookingTaskManager();
        }
        return instance;
    }

    /**
     * 更新全局任务列表（每10tick调用一次）
     * 由BusinessManager调用，扫描所有操作台的所有订单
     */
    public void beginUpdate(long currentTick) {
        if (currentTick - lastUpdateTick < 10) {
            return;
        }
        lastUpdateTick = currentTick;
        // 重置所有任务的统计数据（等待重新收集）
        for (GlobalTask task : tasks.values()) {
            task.totalNeeded = 0;
            task.alreadyInCounters = 0;
            task.sources.clear();
        }
    }

    /**
     * 添加一个食物需求（由CookingBridge在扫描操作台时调用）
     */
    public void addFoodRequirement(String itemId, int needed, int alreadyInCounter,
                                     BlockPos counterPos, String orderId, long orderCreateTime) {
        GlobalTask task = tasks.computeIfAbsent(itemId, k -> new GlobalTask(itemId));
        task.totalNeeded += needed;
        task.alreadyInCounters += alreadyInCounter;
        task.lastUpdateTime = lastUpdateTick;

        long counterKey = counterPos.asLong();
        CounterOrderInfo info = task.sources.get(counterKey);
        if (info == null) {
            info = new CounterOrderInfo(counterPos, orderId, needed, orderCreateTime);
            task.sources.put(counterKey, info);
        } else {
            info.needed += needed;
            info.alreadyInCounter += alreadyInCounter;
        }

        // 计算优先级：订单越早创建（等待时间越长），优先级越高
        long waitTime = lastUpdateTick - orderCreateTime;
        task.priority = Math.max(task.priority, (int)(waitTime / 200));  // 每10秒增加1优先级
    }

    /**
     * 结束更新（清理过期任务，计算优先级）
     */
    public void endUpdate() {
        // 移除没有来源的任务（过期的）
        tasks.entrySet().removeIf(entry -> entry.getValue().sources.isEmpty());

        // 按优先级排序（通过TreeMap或在获取时排序）
    }

    /**
     * 标记某个食物正在烹饪
     */
    public void markCooking(String itemId, int count) {
        GlobalTask task = tasks.get(itemId);
        if (task != null) {
            task.currentlyCooking += count;
        }
    }

    /**
     * 标记某个食物烹饪完成
     */
    public void markCookingCompleted(String itemId, int count) {
        GlobalTask task = tasks.get(itemId);
        if (task != null) {
            task.currentlyCooking = Math.max(0, task.currentlyCooking - count);
        }
    }

    /**
     * 获取所有需要烹饪的任务（按优先级排序）
     */
    public List<GlobalTask> getTasksToCook() {
        List<GlobalTask> result = new ArrayList<>();
        for (GlobalTask task : tasks.values()) {
            if (task.getRemainingToCook() > 0) {
                result.add(task);
            }
        }
        // 按优先级降序排序，优先级相同按需求数量降序
        result.sort((a, b) -> {
            if (b.priority != a.priority) return Integer.compare(b.priority, a.priority);
            return Integer.compare(b.getRemainingToCook(), a.getRemainingToCook());
        });
        return result;
    }

    /**
     * 为指定厨师获取最合适的任务
     * 优先级：背包已有食材 > 附近有可用厨具 > 优先级高 > 需求多 > 距离近
     */
    public GlobalTask getBestTaskForMaid(BlockPos maidPos, Set<String> itemsInMaidInventory) {
        List<GlobalTask> tasksToCook = getTasksToCook();
        if (tasksToCook.isEmpty()) return null;

        // 评分排序
        tasksToCook.sort((a, b) -> {
            int scoreA = calculateTaskScore(a, maidPos, itemsInMaidInventory);
            int scoreB = calculateTaskScore(b, maidPos, itemsInMaidInventory);
            return Integer.compare(scoreB, scoreA);
        });

        return tasksToCook.get(0);
    }

    /**
     * 计算任务评分（分数越高越优先）
     */
    private int calculateTaskScore(GlobalTask task, BlockPos maidPos, Set<String> itemsInMaidInventory) {
        int score = 0;

        // 优先级1：厨师背包里已有食材（+1000分）
        if (itemsInMaidInventory != null && itemsInMaidInventory.contains(task.itemId)) {
            score += 1000;
        }

        // 优先级2：优先级高的任务（+priority * 10分）
        score += task.priority * 10;

        // 优先级3：需求数量多的任务（+remaining * 2分）
        score += task.getRemainingToCook() * 2;

        // 优先级4：距离近的操作台（距离越近分数越高）
        if (maidPos != null && !task.sources.isEmpty()) {
            double minDist = Double.MAX_VALUE;
            for (CounterOrderInfo info : task.sources.values()) {
                double dist = info.counterPos.distSqr(maidPos);
                if (dist < minDist) minDist = dist;
            }
            // 距离每远1格扣1分
            score -= (int)(minDist / 10);
        }

        return score;
    }

    /**
     * 获取统计信息（用于调试）
     */
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalTasks", tasks.size());
        int tasksToCook = 0;
        int totalRemaining = 0;
        for (GlobalTask task : tasks.values()) {
            if (task.getRemainingToCook() > 0) {
                tasksToCook++;
                totalRemaining += task.getRemainingToCook();
            }
        }
        stats.put("tasksToCook", tasksToCook);
        stats.put("totalRemaining", totalRemaining);
        return stats;
    }

    /**
     * 清空所有状态（世界卸载时调用）
     */
    public void clear() {
        tasks.clear();
        lastUpdateTick = 0;
    }
}
