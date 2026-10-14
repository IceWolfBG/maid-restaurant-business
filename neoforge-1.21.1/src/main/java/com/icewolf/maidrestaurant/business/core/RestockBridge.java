package com.icewolf.maidrestaurant.business.core;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.AutomationConfig;
import com.icewolf.maidrestaurant.business.config.GameplayConfig;
import com.icewolf.maidrestaurant.business.config.PerformanceConfig;
import com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper;
import com.mastermarisa.maid_restaurant.utils.MaidStorages;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.neoforged.neoforge.items.IItemHandler;

/**
 * 侍者自动给 OTC 包装货架（ShelfBlock，属性 papers）补充皮革。NeoForge 1.21.1 版。
 *
 * 机制背景（新版 OrderToCook）：
 *  - 外卖打包耗材是皮革，放在独立的 ShelfBlock（货架）上，方块状态 papers 0~10，
 *    玩家手持皮革右键 +1，操作台打包时从周围 5 格货架抽取。
 *  - 干净盘子走 PlateShelfBlock（plates），已有"收脏盘→洗碗→放回盘子架"闭环；
 *    货架皮革原本只能玩家手动补，本桥补齐这个闭环。
 *
 * 设计（事件驱动、按激活打单机隔离、最大化复用洗碗流程）：
 *  - 仅在酒狐速递站完成一笔外卖结算后打一次"待检查"标记（RestockBridge.requestCheck），
 *    不做持续全局扫描。
 *  - 触发条件：打单机范围内货架总缺额 = 10*货架数 - Σpapers，大于排班表"洗碗阈值"才补。
 *  - 一次只补最近的一个货架、补满到 10（容器里皮革不够就补全部能拿到的）。
 *  - 皮革从厨师取食材的同一套容器（MaidStorages，±24/y±8）取，执行/寻路/动画/占用
 *    全部复用洗碗任务，受排班表"自动洗碗"开关控制，不新增配置项与 GUI。
 */
public class RestockBridge {
    private static final int STATE_GO_TO_CONTAINER = 0;
    private static final int STATE_GO_TO_SHELF = 1;
    private static final int STATE_PUT = 2;

    private static final int MAX_SHELF_PAPERS = 10;
    private static final ResourceLocation LEATHER_ID = ResourceLocation.fromNamespaceAndPath("minecraft", "leather");
    // 按音效 id 自行构造来跨 mod 播放 OTC 已注册音效，不直接引用 OTC 的 DeferredHolder，
    // 避免运行环境 OTC 版本与编译期不一致、字段类型不同导致 NoSuchFieldError
    private static final SoundEvent SOUND_BAG_PLACE = SoundEvent.createVariableRangeEvent(ResourceLocation.fromNamespaceAndPath("ordertocook", "bag_place"));

    // 待检查的激活打单机位置（按维度隔离，速递站结算后打标记，带去抖）
    private static final Map<ResourceLocation, Set<BlockPos>> pendingByDim = new HashMap<>();
    // 进行中的补货任务，key=货架位置
    private static final Map<BlockPos, RestockTask> tasks = new HashMap<>();

    /** 服务器启动时清空补货待处理集合与流程任务（见 {@link RuntimeState}）。 */
    public static void clearRuntimeState() {
        pendingByDim.clear();
        tasks.clear();
    }

    /** 酒狐速递站一笔外卖结算完成后调用：给关联打单机打一次补货检查标记。 */
    public static void requestCheck(ServerLevel level, BlockPos machinePos) {
        if (level == null || machinePos == null) return;
        pendingByDim.computeIfAbsent(level.dimension().location(), k -> new HashSet<>()).add(machinePos.immutable());
    }

    /** 与自动洗碗同一个 10tick 节流、同一个 autoWash 开关，由 BusinessManager 调用。 */
    public static void tickRestock(ServerLevel level, BusinessManager manager) {
        try {
            tickTasks(level);
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("[补货] 任务推进异常", t);
        }
        Set<BlockPos> pending = pendingByDim.remove(level.dimension().location());
        if (pending == null || pending.isEmpty()) return;
        List<BlockPos> machines = new ArrayList<>(pending);
        for (BlockPos machinePos : machines) {
            try {
                scanAndStart(level, machinePos, manager);
            } catch (Throwable t) {
                MaidRestaurantBusiness.LOGGER.error("[补货] 扫描启动异常 machine={}", machinePos, t);
            }
        }
    }

    // ==================== 扫描与建任务 ====================

    private static void scanAndStart(ServerLevel level, BlockPos machinePos, BusinessManager manager) {
        if (machinePos == null) return;
        // 打单机必须仍处于激活状态
        if (!manager.getActivatedMachines().contains(machinePos)) return;
        // 绑定排班表"自动洗碗"开关（没有排班表默认开）
        if (!MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_WASH)) return;

        int range = PerformanceConfig.dishScanRange;

        // ① 枚举打单机范围内的包装货架（OTC ShelfBlock，papers 属性；排除盘子架 PlateShelfBlock）
        List<BlockPos> shelves = new ArrayList<>();
        int totalPapers = 0;
        BlockPos targetShelf = null;
        int targetPapers = 0;
        double minShelfDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(machinePos.offset(-range, -4, -range),
                                                   machinePos.offset(range, 4, range))) {
            BlockState state = level.getBlockState(pos);
            IntegerProperty prop = papersProperty(state);
            if (prop == null) continue;
            int papers = (Integer) state.getValue(prop);
            shelves.add(pos.immutable());
            totalPapers += papers;
            if (papers < MAX_SHELF_PAPERS) {
                double d = pos.distSqr(machinePos);
                if (d < minShelfDist) {
                    minShelfDist = d;
                    targetShelf = pos.immutable();
                    targetPapers = papers;
                }
            }
        }
        if (targetShelf == null) return; // 没有货架，或货架全满

        // ② 总池缺额 = 10*货架数 - Σpapers；缺额 > 洗碗阈值才补
        int threshold = MaidUtils.getScheduleBoardMinPlates(level, machinePos);
        if (threshold <= 0) threshold = GameplayConfig.minPlatesToWash;
        int totalCapacity = MAX_SHELF_PAPERS * shelves.size();
        int deficit = totalCapacity - totalPapers;
        if (deficit <= threshold) {
            return;
        }

        // 该货架已有进行中的补货任务，不重复派
        if (tasks.containsKey(targetShelf)) return;

        int need = MAX_SHELF_PAPERS - targetPapers; // 只补最近这一个货架到满

        // ③ 枚举空闲侍者候选（中心化缓存 + 职业/绑定/占用过滤，与洗碗一致），按到目标货架距离排序
        final BlockPos sortCenter = targetShelf;
        List<EntityMaid> candidates = new ArrayList<>();
        for (EntityMaid candidate : TaskManager.getInstance().getCachedMaidsForMachine(level, machinePos)) {
            if (candidate == null || !candidate.isAlive()) continue;
            if (!MaidUtils.TASK_WAITER.equals(MaidUtils.getTaskUid(candidate))) continue;
            if (MaidUtils.getWorkerCountForMachine(machinePos) > 0
                    && !MaidUtils.isMaidBoundToMachine(candidate.getUUID(), machinePos)) continue;
            if (TaskManager.getInstance().hasMaidTask(candidate.getUUID())) continue;
            if (MaidUtils.isOccupied(candidate)) continue;
            candidates.add(candidate);
        }
        candidates.sort((a, b) -> Double.compare(a.blockPosition().distSqr(sortCenter),
                                                  b.blockPosition().distSqr(sortCenter)));

        // ④ 逐侍者判定：只统计"她自己真实可达范围内的皮革容器 + 她自己背包皮革"——
        //    可达范围与执行期取物同源（中心=居家工作中心/非居家脚下，水平=getRestrictRadius，垂直=2），
        //    与烹饪 CookingBridge 方案 A 口径一致。她够不到的容器不会派给她，避免"机器24格放行、
        //    执行期走不到、原地不动"；选第一个拿得到皮革的侍者。
        EntityMaid maid = null;
        List<BlockPos> maidLeatherContainers = null;
        for (EntityMaid candidate : candidates) {
            IItemHandler candidateInv = MaidUtils.getInventory(candidate);
            int own = candidateInv == null ? 0 : MaidUtils.countItem(candidateInv, LEATHER_ID);
            List<BlockPos> reachable = findReachableLeatherContainers(level, candidate, sortCenter);
            if (own > 0 || !reachable.isEmpty()) {
                maid = candidate;
                maidLeatherContainers = reachable;
                break;
            }
        }
        if (maid == null) {
            // 所有候选侍者背包和"她们各自可达范围"都没有皮革（含只在机器24内、但谁都够不到的情况）：
            // 对最近的候选提示一次（错误类气泡自带同类去重）。
            if (!candidates.isEmpty()) {
                MaidChatBubbleHelper.waiterRestockNoLeather(candidates.get(0));
            }
            return;
        }

        // ⑤ 建任务（状态机会先用她背包皮革，不足再去她可达范围内的容器取）
        String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_RESTOCK, targetShelf, machinePos);
        if (taskId != null) {
            tasks.put(targetShelf, new RestockTask(targetShelf, machinePos, maid, need, maidLeatherContainers));
            TaskManager.getInstance().assignTask(maid.getUUID(), TaskManager.TYPE_RESTOCK, level);
        }
        try {
            MaidChatBubbleHelper.waiterRestocking(maid);
        } catch (Throwable t) {}
    }

    /**
     * 扫描某侍者"可达范围"内的皮革容器——逐格复刻本体 MaidGetFromStorageTask 取物搜索：
     * 中心=居家工作中心/非居家脚下（BehaviorUtils.getSearchPos），水平=getRestrictRadius，垂直=2；
     * 容器识别用 MaidStorages.tryGetHandler（与执行期同一个，含厨房站 RefinedStorageItemHandler）。
     * 返回该侍者真正走得到、且里面有皮革的容器，按到目标货架距离由近到远排序。
     */
    private static List<BlockPos> findReachableLeatherContainers(ServerLevel level, EntityMaid maid,
                                                                 BlockPos targetShelf) {
        List<BlockPos> result = new ArrayList<>();
        try {
            BlockPos center = maid.isHomeModeEnable() ? maid.getRestrictCenter() : maid.blockPosition().below();
            int h = (int) maid.getRestrictRadius();
            if (h < 0) h = 0;
            for (BlockPos pos : BlockPos.betweenClosed(center.offset(-h, -2, -h), center.offset(h, 2, h))) {
                IItemHandler inv = MaidStorages.tryGetHandler((Level) level, pos);
                if (inv == null) continue;
                if (MaidUtils.countItem(inv, LEATHER_ID) > 0) {
                    result.add(pos.immutable());
                }
            }
        } catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("[补货] 可达皮革容器扫描出错", t);
        }
        result.sort((a, b) -> Double.compare(a.distSqr(targetShelf), b.distSqr(targetShelf)));
        return result;
    }

    // ==================== 任务状态机 ====================

    private static void tickTasks(ServerLevel level) {
        Iterator<Map.Entry<BlockPos, RestockTask>> it = tasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, RestockTask> entry = it.next();
            RestockTask task = entry.getValue();
            if (!task.dim.equals(level.dimension().location())) continue; // 只推进本维度的任务
            EntityMaid maid = task.maidRef.get();
            if (maid == null) {
                TaskManager.getInstance().failTask(null, "restock maid missing");
                task.cleanup();
                it.remove();
                continue;
            }
            TaskManager.getInstance().heartbeat(maid.getUUID(), TaskManager.getInstance().getCurrentTick());
            long now = level.getGameTime();

            // 总超时保底（500tick=25秒：去容器200 + 去货架200 + 余量）
            if (now - task.startTime > 500L) {
                MaidRestaurantBusiness.LOGGER.warn("[补货] 任务总超时，强制结束 女仆={} 货架={}", maid.getName().getString(), task.shelfPos);
                TaskManager.getInstance().failTask(maid.getUUID(), "restock timeout");
                task.cleanup();
                it.remove();
                continue;
            }

            switch (task.state) {
                case STATE_GO_TO_CONTAINER: {
                    IItemHandler maidInv = MaidUtils.getInventory(maid);
                    int held = maidInv == null ? 0 : MaidUtils.countItem(maidInv, LEATHER_ID);
                    if (held >= task.need) {
                        task.state = STATE_GO_TO_SHELF;
                        task.lastChange = now;
                        break;
                    }
                    if (task.containerQueue.isEmpty()) {
                        // 所有容器都试完：完全没拿到皮革（held=0）则直接结束，空手去货架放不了东西、没有意义；
                        // 拿到部分（held>0）仍去货架把手里的放掉。
                        if (held <= 0) {
                            TaskManager.getInstance().failTask(maid.getUUID(), "restock no leather");
                            task.cleanup();
                            it.remove();
                            break;
                        }
                        task.state = STATE_GO_TO_SHELF;
                        task.lastChange = now;
                        break;
                    }
                    BlockPos container = task.containerQueue.get(0);
                    IItemHandler inv = MaidStorages.tryGetHandler((Level) level, container);
                    if (inv == null || MaidUtils.countItem(inv, LEATHER_ID) <= 0) {
                        task.containerQueue.remove(0);
                        task.swungAtContainer = false; // 换下一个容器，重置摆臂标记
                        break;
                    }
                    if (MaidUtils.isNear(maid, container, 3.0)) {
                        // 对齐女仆餐厅本体取食材（MaidGetFromStorageTask.accept）：到达容器后“一次性拿够”
                        // 所需皮革，takeItem 内部按槽位取至上限。原本每 10tick 只拿 1 个，补满 10 个会被
                        // 10tick 调度放大成 10×10tick≈5 秒，女仆会在容器前呆站约 5 秒。
                        int remaining = task.need - held;
                        int taken = MaidUtils.takeItem(inv, maidInv, LEATHER_ID, remaining);
                        if (taken > 0 && !task.swungAtContainer) {
                            task.swungAtContainer = true; // 与本体一致：本容器取物只摆一次手
                            try { maid.swing(InteractionHand.OFF_HAND); } catch (Throwable t) {}
                        }
                        task.lastChange = now;
                        // 当前容器拿空 / 背包拿不动：弹出队首，下一拍去队列里下一个容器
                        if (taken <= 0 || MaidUtils.countItem(inv, LEATHER_ID) <= 0) {
                            task.containerQueue.remove(0);
                            task.swungAtContainer = false;
                        }
                        // 已拿够或已无容器可去：当场判定，省掉最多一次 10tick 轮询等待
                        int nowHeld = MaidUtils.countItem(maidInv, LEATHER_ID);
                        boolean containersDone = task.containerQueue.isEmpty();
                        if (nowHeld <= 0 && containersDone) {
                            // 容器全试完且没拿到任何皮革：直接结束，空手去货架放不了东西、没有意义
                            TaskManager.getInstance().failTask(maid.getUUID(), "restock no leather");
                            task.cleanup();
                            it.remove();
                            break;
                        }
                        if (nowHeld >= task.need || containersDone) {
                            task.state = STATE_GO_TO_SHELF;
                            task.lastChange = now;
                            if (papersProperty(level.getBlockState(task.shelfPos)) != null
                                    && !MaidUtils.isNear(maid, task.shelfPos, 3.0)) {
                                MaidUtils.moveToSide(maid, task.shelfPos, 0.3);
                            }
                        }
                        break;
                    }
                    if (!task.isMoving(maid, now)) {
                        // 连续 100tick（5秒）没移动才判超时（真正卡住 / 走不到），换下一个容器；
                        // 移动期间不断刷新计时，长距离走路不会被误判超时。
                        task.containerQueue.remove(0);
                        task.swungAtContainer = false;
                        task.lastChange = now;
                        break;
                    }
                    MaidUtils.moveToSide(maid, container, 0.3);
                    break;
                }
                case STATE_GO_TO_SHELF: {
                    BlockState state = level.getBlockState(task.shelfPos);
                    if (papersProperty(state) == null) {
                        // 货架被破坏：皮革直接保留在侍者背包里（下次补货还会用到），不吞物品也不掉落
                        TaskManager.getInstance().completeTask(maid.getUUID());
                        task.cleanup();
                        it.remove();
                        break;
                    }
                    if (MaidUtils.isNear(maid, task.shelfPos, 3.0)) {
                        task.state = STATE_PUT;
                        task.lastChange = now;
                        break;
                    }
                    if (!task.isMoving(maid, now)) {
                        // 连续 100tick（5秒）没移动才判超时（真正卡住 / 走不到），结束任务
                        MaidRestaurantBusiness.LOGGER.warn("[补货] 去货架移动停滞超时（连续5秒不移动），结束 女仆={} 货架={}", maid.getName().getString(), task.shelfPos);
                        TaskManager.getInstance().failTask(maid.getUUID(), "move to shelf stuck");
                        task.cleanup();
                        it.remove();
                        break;
                    }
                    MaidUtils.moveToSide(maid, task.shelfPos, 0.3);
                    break;
                }
                case STATE_PUT: {
                    int put = 0;
                    try {
                        put = putLeatherToShelf(level, maid, task.shelfPos);
                    } catch (Throwable t) {
                        MaidRestaurantBusiness.LOGGER.error("[补货] 放入货架异常 女仆={} 货架={}", maid.getName().getString(), task.shelfPos, t);
                    }
                    TaskManager.getInstance().completeTask(maid.getUUID());
                    task.cleanup();
                    it.remove();
                    break;
                }
            }
        }
    }

    // ==================== 工具方法 ====================

    /** 若是 OTC 包装货架（ShelfBlock，含 papers 整数属性）返回该属性，否则 null（同时排除盘子架）。 */
    private static IntegerProperty papersProperty(BlockState state) {
        if (state == null || state.getBlock() == null) return null;
        String simple = state.getBlock().getClass().getSimpleName();
        if (!"ShelfBlock".equals(simple)) return null; // PlateShelfBlock 不叫 ShelfBlock
        Property<?> p = state.getProperties().stream().filter(x -> x.getName().equals("papers")).findFirst().orElse(null);
        return (p instanceof IntegerProperty ip) ? ip : null;
    }

    /** 把女仆背包里的皮革放进货架（写 papers 属性），照搬放回盘子架的逻辑，返回放入数量。 */
    private static int putLeatherToShelf(ServerLevel level, EntityMaid maid, BlockPos shelfPos) {
        IItemHandler maidInv = MaidUtils.getInventory(maid);
        if (maidInv == null) return 0;
        BlockState state = level.getBlockState(shelfPos);
        IntegerProperty prop = papersProperty(state);
        if (prop == null) return 0;
        int papers = (Integer) state.getValue(prop);
        int put = 0;
        for (int slot = 0; slot < maidInv.getSlots() && papers < MAX_SHELF_PAPERS; slot++) {
            ItemStack stack = maidInv.getStackInSlot(slot);
            ResourceLocation rl;
            if (stack.isEmpty() || (rl = BuiltInRegistries.ITEM.getKey(stack.getItem())) == null
                    || !rl.equals(LEATHER_ID)) continue;
            int toPut = Math.min(stack.getCount(), MAX_SHELF_PAPERS - papers);
            if (toPut <= 0) continue;
            papers += toPut;
            maidInv.extractItem(slot, toPut, false);
            put += toPut;
        }
        if (put > 0) {
            level.setBlock(shelfPos, state.setValue(prop, Integer.valueOf(papers)), 3);
            // 音效与手臂摇摆各自独立 try-catch，绝不能因播放失败中断补货或让任务残留（皮革已入货架）
            try {
                // OTC 货架放入皮革专用音效 bag_place，与玩家手动补货听感一致
                level.playSound(null, shelfPos, SOUND_BAG_PLACE, SoundSource.BLOCKS, 1.0f, 1.0f);
            } catch (Throwable t) {}
            try {
                maid.swing(InteractionHand.OFF_HAND);
            } catch (Throwable t) {}
        }
        return put;
    }

    // ==================== 任务数据 ====================

    private static class RestockTask {
        final BlockPos shelfPos;
        final BlockPos machinePos;
        final ResourceLocation dim;
        final WeakReference<EntityMaid> maidRef;
        final int need;
        final List<BlockPos> containerQueue;
        int state;
        long lastChange;
        boolean swungAtContainer; // 当前容器取物是否已摆过手臂（每个容器只摆一次，避免每tick取物多重摆臂）
        final long startTime;
        // 停滞检测：女仆“上次位置”和“上次移动时刻”。连续 STUCK_TIMEOUT tick 位置基本不变才判超时；
        // 移动期间（位置持续变化）不断刷新，长距离走路不会被误判超时。
        double lastX, lastY, lastZ;
        long lastMoveTick;
        private static final long STUCK_TIMEOUT = 100L; // 连续 5 秒不移动才超时
        private static final double MOVE_EPSILON = 0.03D; // 位移超过此值视为在移动

        RestockTask(BlockPos shelfPos, BlockPos machinePos, EntityMaid maid, int need, List<BlockPos> containers) {
            this.shelfPos = shelfPos;
            this.machinePos = machinePos;
            this.dim = maid.level().dimension().location();
            this.maidRef = new WeakReference<>(maid);
            this.need = need;
            this.containerQueue = new ArrayList<>(containers);
            this.state = STATE_GO_TO_CONTAINER;
            this.lastChange = maid.level().getGameTime();
            this.startTime = maid.level().getGameTime();
            this.lastX = maid.getX();
            this.lastY = maid.getY();
            this.lastZ = maid.getZ();
            this.lastMoveTick = maid.level().getGameTime();
            MaidUtils.setOccupied(maid, true);
        }

        /**
         * 停滞检测：女仆本拍相对上次记录是否在移动。在移动则刷新计时，返回 true；
         * 连续 STUCK_TIMEOUT tick 未移动返回 false（真正卡住，调用方应放弃当前容器/目标）。
         */
        boolean isMoving(EntityMaid maid, long now) {
            double dx = maid.getX() - lastX;
            double dy = maid.getY() - lastY;
            double dz = maid.getZ() - lastZ;
            if (dx * dx + dy * dy + dz * dz > MOVE_EPSILON * MOVE_EPSILON) {
                lastX = maid.getX();
                lastY = maid.getY();
                lastZ = maid.getZ();
                lastMoveTick = now;
                return true;
            }
            return now - lastMoveTick <= STUCK_TIMEOUT;
        }

        void cleanup() {
            EntityMaid maid = this.maidRef.get();
            if (maid != null) {
                try {
                    TaskSafetyUtils.resetMaidState(maid);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("[补货] 清理时resetMaidState失败", t);
                    try {
                        maid.getNavigation().stop();
                        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                        maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH);
                    } catch (Throwable t2) {}
                    MaidUtils.setOccupied(maid, false);
                }
            }
        }
    }
}
