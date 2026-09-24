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

        // ③ 枚举厨师同款容器里的皮革（±24/y±8），记录有皮革的容器并按到目标货架距离排序
        List<BlockPos> leatherContainers = new ArrayList<>();
        int containerLeather = 0;
        for (BlockPos pos : BlockPos.betweenClosed(machinePos.offset(-24, -8, -24),
                                                   machinePos.offset(24, 8, 24))) {
            IItemHandler inv = MaidStorages.tryGetHandler((Level) level, pos);
            if (inv == null) continue;
            int c = MaidUtils.countItem(inv, LEATHER_ID);
            if (c > 0) {
                leatherContainers.add(pos.immutable());
                containerLeather += c;
            }
        }
        final BlockPos sortCenter = targetShelf;
        leatherContainers.sort((a, b) -> Double.compare(a.distSqr(sortCenter), b.distSqr(sortCenter)));

        // ④ 枚举空闲侍者候选（中心化缓存 + 职业/绑定/占用过滤，与洗碗一致），按到目标货架距离排序
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
        candidates.sort((a, b) -> Double.compare(a.blockPosition().distSqr(sortCenter), b.blockPosition().distSqr(sortCenter)));

        // ⑤ 逐个体判定：公共容器皮革 + 该侍者自己背包皮革，选第一个拿得到皮革的侍者
        EntityMaid maid = null;
        int maidLeather = 0;
        for (EntityMaid candidate : candidates) {
            IItemHandler candidateInv = MaidUtils.getInventory(candidate);
            int own = candidateInv == null ? 0 : MaidUtils.countItem(candidateInv, LEATHER_ID);
            if (containerLeather + own > 0) {
                maid = candidate;
                maidLeather = own;
                break;
            }
        }
        if (maid == null) {
            // 有候选侍者但公共容器和她们背包都没有皮革：对最近的候选提示一次（错误类气泡自带同类去重）
            if (!candidates.isEmpty()) {
                MaidChatBubbleHelper.waiterRestockNoLeather(candidates.get(0));
            }
            return;
        }

        // ⑥ 建任务（状态机会先用她背包皮革，不足再去容器取）
        tasks.put(targetShelf, new RestockTask(targetShelf, machinePos, maid, need, leatherContainers));
        String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_RESTOCK, targetShelf, machinePos);
        if (taskId != null) {
            TaskManager.getInstance().assignTask(maid.getUUID(), TaskManager.TYPE_RESTOCK, level);
        }
        try {
            MaidChatBubbleHelper.waiterRestocking(maid);
        } catch (Throwable t) {}
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
            TaskManager.getInstance().heartbeat(maid.getUUID(), level.getGameTime());
            long now = level.getGameTime();

            // 总超时保底（400tick=20秒）
            if (now - task.startTime > 400L) {
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
                    if (held >= task.need || task.containerQueue.isEmpty()) {
                        task.state = STATE_GO_TO_SHELF;
                        task.lastChange = now;
                        break;
                    }
                    BlockPos container = task.containerQueue.get(0);
                    IItemHandler inv = MaidStorages.tryGetHandler((Level) level, container);
                    if (inv == null || MaidUtils.countItem(inv, LEATHER_ID) <= 0) {
                        task.containerQueue.remove(0);
                        break;
                    }
                    if (MaidUtils.isNear(maid, container, 3.0)) {
                        MaidUtils.takeItem(inv, maidInv, LEATHER_ID, task.need - held);
                        task.containerQueue.remove(0);
                        task.lastChange = now;
                        break;
                    }
                    if (now - task.lastChange > 100L) {
                        // 走到这个容器超时，换下一个容器
                        task.containerQueue.remove(0);
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
                    if (now - task.lastChange > 200L) {
                        MaidRestaurantBusiness.LOGGER.warn("[补货] 去货架移动超时，结束 女仆={} 货架={}", maid.getName().getString(), task.shelfPos);
                        TaskManager.getInstance().failTask(maid.getUUID(), "move to shelf timeout");
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
        final long startTime;

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
            MaidUtils.setOccupied(maid, true);
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
