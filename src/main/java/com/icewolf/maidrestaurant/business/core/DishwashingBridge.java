/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  cn.breezeth.ordertocook.block.entity.DishwasherBlockEntity
 *  com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.core.BlockPos
 *  net.minecraft.resources.ResourceLocation
 *  net.minecraft.server.MinecraftServer
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.item.ItemEntity
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.Level
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.block.state.properties.IntegerProperty
 *  net.minecraft.world.level.block.state.properties.Property
 *  net.minecraft.world.phys.AABB
 *  net.minecraftforge.common.util.FakePlayer
 *  net.minecraftforge.common.util.FakePlayerFactory
 *  net.minecraftforge.items.IItemHandler
 *  net.minecraftforge.items.ItemHandlerHelper
 *  net.minecraftforge.registries.ForgeRegistries
 *  net.minecraftforge.server.ServerLifecycleHooks
 */
package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.block.entity.DishwasherBlockEntity;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.config.BusinessConfig;
import com.icewolf.maidrestaurant.business.core.BusinessManager;
import com.icewolf.maidrestaurant.business.core.MaidUtils;
import com.icewolf.maidrestaurant.business.core.ProgressionManager;
import com.mojang.authlib.GameProfile;
import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.common.util.FakePlayerFactory;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.server.ServerLifecycleHooks;

public class DishwashingBridge {
    private static final int STATE_GO_TO_PLATE = 0;
    private static final int STATE_COLLECT = 1;
    private static final int STATE_GO_TO_DISHWASHER = 2;
    private static final int STATE_INSERT = 3;
    private static final int STATE_WAIT_WASH = 4;
    private static final int STATE_TAKE_CLEAN = 5;
    private static final int STATE_GO_TO_RACK = 6;
    private static final int STATE_PUT_TO_RACK = 7;
    private static final int MAX_PLATES_PER_WASH = 16;
    private static final Map<BlockPos, DishTask> dishTasks = new HashMap<BlockPos, DishTask>();
    private static Method isDirtyStageMethod = null;
    private static boolean reflectionInit = false;

    private static int getMinPlatesToWash(ServerLevel level, BlockPos machinePos) {
        if (machinePos != null) {
            int scheduleVal = MaidUtils.getScheduleBoardMinPlates(level, machinePos);
            if (scheduleVal > 0) {
                return scheduleVal;
            }
        }
        return BusinessConfig.minPlatesToWash;
    }

    private static void initReflection() {
        if (reflectionInit) {
            return;
        }
        try {
            Class<?> foodPlateClass = Class.forName("cn.breezeth.ordertocook.block.FoodPlateBlock");
            for (Method m : foodPlateClass.getDeclaredMethods()) {
                if (!m.getName().equals("isDirtyStage") || m.getParameterCount() != 1) continue;
                m.setAccessible(true);
                isDirtyStageMethod = m;
                break;
            }
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.warn("Dishwashing: isDirtyStage not found: {}", t.toString());
        }
        reflectionInit = true;
    }

    private static boolean isDirtyStage(BlockState state) {
        DishwashingBridge.initReflection();
        if (isDirtyStageMethod == null) {
            return false;
        }
        try {
            Object prop = state.getProperties().stream().filter(p -> p.getName().equals("stage")).findFirst().orElse(null);
            if (prop instanceof IntegerProperty) {
                IntegerProperty intProp = (IntegerProperty)prop;
                int stage = (Integer)state.getValue(intProp);
                return (Boolean)isDirtyStageMethod.invoke(null, stage);
            }
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("Dishwashing: isDirtyStage check failed", t);
        }
        return false;
    }

    public static void tickDishwashing(ServerLevel level, BusinessManager manager) {
        DishwashingBridge.tickDishTasks(level);
        boolean unlocked = false;
        for (BlockPos machinePos : manager.getActivatedMachines()) {
            if (!ProgressionManager.isDishwashingUnlocked(level, machinePos)) continue;
            // 排班表配置检查：如果附近有排班表且关闭了自动洗碗，则跳过该打单机
            if (!MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_WASH)) continue;
            if (!MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_COLLECT)) continue;
            unlocked = true;
            break;
        }
        if (!unlocked) {
            return;
        }
        try {
            DishwashingBridge.scanAndStartTasks(level, manager);
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("Error in dishwashing scan", t);
        }
    }

    private static void tickDishTasks(ServerLevel level) {
        Iterator<Map.Entry<BlockPos, DishTask>> it = dishTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, DishTask> entry = it.next();
            DishTask task = entry.getValue();
            EntityMaid maid = (EntityMaid)task.maidRef.get();
            if (maid == null) {
                // TaskManager：任务失败（女仆消失）
                TaskManager.getInstance().failTask(null, "dishwashing maid missing");
                task.cleanup();
                it.remove();
                continue;
            }
            // TaskManager心跳更新
            TaskManager.getInstance().heartbeat(maid.getUUID(), level.getGameTime());
            long now = level.getGameTime();
            
            // 总超时保护：如果任务持续超过1200tick（60秒），强制结束，避免女仆永远卡住
            long taskDuration = now - task.startTime;
            if (taskDuration > 1200L) {
                MaidRestaurantBusiness.LOGGER.warn("洗碗: 任务总超时（{}tick，阈值1200tick），强制结束 女仆={} 当前状态={} 洗碗机={} 当前盘子={}",
                    taskDuration, maid.getName().getString(), task.state, task.dishwasherPos, task.currentPlatePos);
                TaskManager.getInstance().failTask(maid.getUUID(), "dishwashing task total timeout");
                task.cleanup();
                it.remove();
                continue;
            }
            
            // 每100tick输出一次任务状态debug日志，方便排查卡住问题
            if (now % 100L == 0L) {
                MaidRestaurantBusiness.LOGGER.info("洗碗: 任务状态 女仆={} 状态={} 持续={}tick 洗碗机={} 当前盘子={} 女仆位置=({},{},{})",
                    maid.getName().getString(), task.state, taskDuration, task.dishwasherPos, task.currentPlatePos,
                    MaidUtils.getX((Entity)maid), MaidUtils.getY((Entity)maid), MaidUtils.getZ((Entity)maid));
            }
            
            switch (task.state) {
                case 0: {
                    int dirtyCount;
                    if (task.currentPlatePos == null) {
                        task.currentPlatePos = DishwashingBridge.findNearestDirtyPlate(level, maid);
                        task.lastChange = now;
                        if (task.currentPlatePos == null) {
                            dirtyCount = DishwashingBridge.countDirtyPlatesInMaid(maid);
                            if (dirtyCount >= DishwashingBridge.getMinPlatesToWash(level, task.machinePos)) {
                                task.state = 2;
                                break;
                            }
                            MaidRestaurantBusiness.LOGGER.info("洗碗: 没有更多脏盘子且未达阈值，任务结束 女仆={} 身上脏盘子={}", maid.getName().getString(), dirtyCount);
                            TaskManager.getInstance().completeTask(maid.getUUID());
                            task.cleanup();
                            it.remove();
                            break;
                        }
                        MaidRestaurantBusiness.LOGGER.info("洗碗: 找到脏盘子 女仆={} 目标={}", maid.getName().getString(), task.currentPlatePos);
                    }
                    // 移动超时保护：如果100tick（5秒）还没到，重新找脏盘子，避免卡在无法到达的位置
                    if (now - task.lastChange > 100L && !MaidUtils.isNear(maid, task.currentPlatePos, 3.0)) {
                        MaidRestaurantBusiness.LOGGER.info("洗碗: 移动超时，重新找脏盘子 女仆={} 当前目标={}", maid.getName().getString(), task.currentPlatePos);
                        task.currentPlatePos = null;
                        break;
                    }
                    if (MaidUtils.isNear(maid, task.currentPlatePos, 3.0)) {
                        task.state = 1;
                        break;
                    }
                    MaidUtils.moveToSide(maid, task.currentPlatePos, 0.3);
                    break;
                }
                case 1: {
                    int dirtyCount;
                    if (DishwashingBridge.collectDirtyPlate(level, maid, task.currentPlatePos)) {
                        MaidRestaurantBusiness.LOGGER.info("\u6d17\u7897\uff1a\u6536\u8d70\u810f\u76d8\u5b50 {}", task.currentPlatePos);
                        task.currentPlatePos = null;
                        dirtyCount = DishwashingBridge.countDirtyPlatesInMaid(maid);
                        if (dirtyCount >= DishwashingBridge.getMinPlatesToWash(level, task.machinePos)) {
                            task.state = 2;
                            MaidRestaurantBusiness.LOGGER.info("\u6d17\u7897\uff1a\u6536\u96c6{}\u4e2a\u810f\u76d8\u5b50\uff0c\u524d\u5f80\u6d17\u7897\u673a", dirtyCount);
                            break;
                        }
                        task.state = 0;
                        break;
                    }
                    task.currentPlatePos = null;
                    task.state = 0;
                    break;
                }
                case 2: {
                    if (task.dishwasherPos == null) {
                        MaidRestaurantBusiness.LOGGER.info("\u6d17\u7897\uff1a\u65e0\u6d17\u7897\u673a\uff0c\u4efb\u52a1\u7ed3\u675f\uff08\u810f\u76d8\u5b50\u4fdd\u7559\u5728\u80cc\u5305\uff09");
                        task.cleanup();
                        it.remove();
                        break;
                    }
                    if (now - task.lastChange > 200L) {
                        BlockEntity be = level.getBlockEntity(task.dishwasherPos);
                        if (!(be instanceof DishwasherBlockEntity)) {
                            MaidRestaurantBusiness.LOGGER.warn("\u6d17\u7897\uff1a\u6d17\u7897\u673a\u4e0d\u5b58\u5728\uff0c\u4efb\u52a1\u7ed3\u675f");
                            task.cleanup();
                            it.remove();
                            break;
                        }
                        task.lastChange = now;
                    }
                    if (MaidUtils.isNear(maid, task.dishwasherPos, 3.0)) {
                        task.state = 3;
                        task.lastChange = now;
                        break;
                    }
                    // 移动超时保护：如果在state 2停留超过200tick还没到，结束任务
                    if (now - task.lastChange > 200L && task.state == 2) {
                        MaidRestaurantBusiness.LOGGER.warn("洗碗: 去洗碗机移动超时，结束任务 女仆={} 洗碗机={}", maid.getName().getString(), task.dishwasherPos);
                        TaskManager.getInstance().failTask(maid.getUUID(), "move to dishwasher timeout");
                        task.cleanup();
                        it.remove();
                        break;
                    }
                    MaidUtils.moveToSide(maid, task.dishwasherPos, 0.3);
                    break;
                }
                case 3: {
                    if (DishwashingBridge.insertDirtyPlatesToDishwasher(level, maid, task.dishwasherPos)) {
                        task.state = 4;
                        task.lastChange = now;
                        MaidRestaurantBusiness.LOGGER.info("\u6d17\u7897\uff1a\u653e\u5165\u810f\u76d8\u5b50\uff0c\u5f00\u59cb\u6d17\u6da4");
                        break;
                    }
                    MaidRestaurantBusiness.LOGGER.warn("\u6d17\u7897\uff1a\u653e\u5165\u810f\u76d8\u5b50\u5931\u8d25");
                    task.cleanup();
                    it.remove();
                    break;
                }
                case 4: {
                    if (DishwashingBridge.canTakeCleanPlates(level, task.dishwasherPos)) {
                        task.state = 5;
                        break;
                    }
                    if (now - task.lastChange <= 200L) break;
                    MaidRestaurantBusiness.LOGGER.warn("\u6d17\u7897\uff1a\u7b49\u5f85\u8d85\u65f6");
                    task.cleanup();
                    it.remove();
                    break;
                }
                case 5: {
                    DishwashingBridge.takeCleanPlatesFromDishwasher(level, maid, task.dishwasherPos);
                    MaidRestaurantBusiness.LOGGER.info("\u6d17\u7897\uff1a\u62ff\u53d6\u5e72\u51c0\u76d8\u5b50\u5b8c\u6210\uff0c\u524d\u5f80\u76d8\u5b50\u67b6");
                    task.state = 6;
                    task.lastChange = now;
                    break;
                }
                case 6: {
                    if (task.lastChange == 0) task.lastChange = now;
                    BlockPos rackPos = DishwashingBridge.findNearestPlateRack(level, maid);
                    if (rackPos == null) {
                        MaidRestaurantBusiness.LOGGER.warn("洗碗：未找到盘子架，任务结束");
                        TaskManager.getInstance().completeTask(maid.getUUID());
                        task.cleanup();
                        it.remove();
                        break;
                    }
                    if (MaidUtils.isNear(maid, rackPos, 3.0)) {
                        task.state = 7;
                        task.lastChange = now;
                        break;
                    }
                    // 移动超时保护：如果在state 6停留超过200tick还没到，结束任务
                    if (now - task.lastChange > 200L) {
                        MaidRestaurantBusiness.LOGGER.warn("洗碗: 去盘子架移动超时，结束任务 女仆={} 盘子架={}", maid.getName().getString(), rackPos);
                        TaskManager.getInstance().failTask(maid.getUUID(), "move to plate rack timeout");
                        task.cleanup();
                        it.remove();
                        break;
                    }
                    MaidUtils.moveToSide(maid, rackPos, 0.3);
                    break;
                }
                case 7: {
                    BlockPos rackPos = DishwashingBridge.findNearestPlateRack(level, maid);
                    if (rackPos != null) {
                        DishwashingBridge.putCleanPlatesToRack(level, maid, rackPos);
                        MaidRestaurantBusiness.LOGGER.info("洗碗：放入干净盘子到盘子架完成");
                    }
                    // TaskManager：任务完成
                    TaskManager.getInstance().completeTask(maid.getUUID());
                    task.cleanup();
                    it.remove();
                }
            }
        }
    }

    private static BlockPos findNearestDirtyPlate(ServerLevel level, EntityMaid maid) {
        double mx = MaidUtils.getX((Entity)maid);
        double my = MaidUtils.getY((Entity)maid);
        double mz = MaidUtils.getZ((Entity)maid);
        BlockPos center = BlockPos.containing((double)mx, (double)my, (double)mz);
        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed((BlockPos)center.offset(-12, -4, -12), (BlockPos)center.offset(12, 4, 12))) {
            double dz;
            double dy;
            double dx;
            double d;
            BlockState state = level.getBlockState(pos);
            if (!state.getBlock().getClass().getName().contains("FoodPlateBlock") || !DishwashingBridge.isDirtyStage(state) || !((d = (dx = (double)pos.getX() + 0.5 - mx) * dx + (dy = (double)pos.getY() + 0.5 - my) * dy + (dz = (double)pos.getZ() + 0.5 - mz) * dz) < minDist)) continue;
            minDist = d;
            nearest = pos.immutable();
        }
        return nearest;
    }

    private static BlockPos findNearestPlateRack(ServerLevel level, EntityMaid maid) {
        double mx = MaidUtils.getX((Entity)maid);
        double my = MaidUtils.getY((Entity)maid);
        double mz = MaidUtils.getZ((Entity)maid);
        BlockPos center = BlockPos.containing((double)mx, (double)my, (double)mz);
        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed((BlockPos)center.offset(-12, -4, -12), (BlockPos)center.offset(12, 4, 12))) {
            double dz;
            double dy;
            double dx;
            double d;
            BlockState state = level.getBlockState(pos);
            if (!state.getBlock().getClass().getName().contains("PlateShelf")) continue;
            try {
                IntegerProperty intProp;
                int plates;
                Property platesProp = state.getProperties().stream().filter(p -> p.getName().equals("plates")).findFirst().orElse(null);
                if (platesProp instanceof IntegerProperty && (plates = ((Integer)state.getValue((Property)(intProp = (IntegerProperty)platesProp))).intValue()) >= 18) {
                    continue;
                }
            }
            catch (Throwable platesProp) {
                // empty catch block
            }
            if (!((d = (dx = (double)pos.getX() + 0.5 - mx) * dx + (dy = (double)pos.getY() + 0.5 - my) * dy + (dz = (double)pos.getZ() + 0.5 - mz) * dz) < minDist)) continue;
            minDist = d;
            nearest = pos.immutable();
        }
        return nearest;
    }

    private static void putCleanPlatesToRack(ServerLevel level, EntityMaid maid, BlockPos rackPos) {
        IItemHandler maidInv = MaidUtils.getInventory(maid);
        if (maidInv == null) {
            return;
        }
        BlockState state = level.getBlockState(rackPos);
        Property platesProp = state.getProperties().stream().filter(p -> p.getName().equals("plates")).findFirst().orElse(null);
        if (!(platesProp instanceof IntegerProperty)) {
            return;
        }
        IntegerProperty intProp = (IntegerProperty)platesProp;
        int plates = (Integer)state.getValue(intProp);
        int putCount = 0;
        for (int slot = 0; slot < maidInv.getSlots() && plates < 18; ++slot) {
            int toPut;
            String itemId;
            ResourceLocation rl;
            ItemStack stack = maidInv.getStackInSlot(slot);
            if (stack.isEmpty() || (rl = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null || !(itemId = rl.toString()).equals("ordertocook:clean_plate") && !itemId.contains("clean_plate") || (toPut = Math.min(stack.getCount(), 18 - plates)) <= 0) continue;
            plates += toPut;
            maidInv.extractItem(slot, toPut, false);
            putCount += toPut;
        }
        if (putCount > 0) {
            level.setBlock(rackPos, (BlockState)state.setValue(intProp, Integer.valueOf(plates)), 3);
            MaidRestaurantBusiness.LOGGER.info("\u6d17\u7897\uff1a\u653e\u5165{}\u4e2a\u5e72\u51c0\u76d8\u5b50\u5230\u76d8\u5b50\u67b6\uff0c\u5f53\u524d\u76d8\u5b50\u6570={}", putCount, plates);
        }
    }

    private static int countDirtyPlatesInMaid(EntityMaid maid) {
        IItemHandler inv = MaidUtils.getInventory(maid);
        if (inv == null) {
            return 0;
        }
        int count = 0;
        for (int i = 0; i < inv.getSlots(); ++i) {
            ResourceLocation rl;
            ItemStack stack = inv.getStackInSlot(i);
            if (stack.isEmpty() || (rl = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null || !rl.toString().equals("ordertocook:dirty_plate")) continue;
            count += stack.getCount();
        }
        return count;
    }

    private static boolean collectDirtyPlate(ServerLevel level, EntityMaid maid, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        if (!state.getBlock().getClass().getName().contains("FoodPlateBlock")) {
            return false;
        }
        IItemHandler maidInv = MaidUtils.getInventory(maid);
        if (maidInv == null) {
            return false;
        }
        if (!level.getBlockState(pos.above()).isAir()) {
            return false;
        }
        try {
            // 使用FakePlayer右键交互餐盘方块，触发FoodPlateBlock.use()收取盘子
            // 这样不会有破坏粒子，也不会生成掉落物
            FakePlayer fakePlayer = FakePlayerFactory.get((ServerLevel)level, (GameProfile)new GameProfile(UUID.randomUUID(), "MaidDishwasher"));
            if (fakePlayer != null) {
                fakePlayer.setPos((double)pos.getX() + 0.5, (double)pos.getY(), (double)pos.getZ() + 0.5);
                // 清空FakePlayer背包，避免之前的物品干扰
                fakePlayer.getInventory().clearContent();
                // 构造BlockHitResult，从女仆方向指向餐盘
                Direction face = Direction.UP;
                BlockHitResult hit = new BlockHitResult(
                    new Vec3((double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5),
                    face, pos, false
                );
                // 调用use交互，FoodPlateBlock.use()会把物品给FakePlayer并移除方块
                InteractionResult result = state.use(level, (Player)fakePlayer, InteractionHand.MAIN_HAND, hit);
                if (result == InteractionResult.SUCCESS || result == InteractionResult.CONSUME) {
                    // 将FakePlayer背包中的物品转移到女仆背包
                    for (int i = 0; i < fakePlayer.getInventory().getContainerSize(); ++i) {
                        ItemStack stack = fakePlayer.getInventory().getItem(i);
                        if (!stack.isEmpty()) {
                            ItemStack leftover = ItemHandlerHelper.insertItemStacked((IItemHandler)maidInv, (ItemStack)stack.copy(), (boolean)false);
                            if (!leftover.isEmpty()) {
                                // 女仆背包满了，掉落到地上
                                level.addFreshEntity(new ItemEntity(level, (double)pos.getX() + 0.5, (double)pos.getY() + 0.5, (double)pos.getZ() + 0.5, leftover));
                            }
                            fakePlayer.getInventory().setItem(i, ItemStack.EMPTY);
                        }
                    }
                    MaidRestaurantBusiness.LOGGER.info("\u6d17\u7897\uff1a\u4f7f\u7528use\u4ea4\u4e92\u6536\u8d70\u810f\u76d8\u5b50 {}", pos);
                    return true;
                }
            }
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("Error collecting dirty plate via use", t);
        }
        // 备用方案：直接移除方块（不产生掉落物），然后给女仆一个脏盘子
        level.removeBlock(pos, false);
        ItemStack dirtyPlate = new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation("ordertocook:dirty_plate")));
        if (!dirtyPlate.isEmpty()) {
            ItemHandlerHelper.insertItemStacked((IItemHandler)maidInv, (ItemStack)dirtyPlate, (boolean)false);
        }
        return true;
    }

    private static boolean insertDirtyPlatesToDishwasher(ServerLevel level, EntityMaid maid, BlockPos dwPos) {
        BlockEntity be = level.getBlockEntity(dwPos);
        if (!(be instanceof DishwasherBlockEntity)) {
            return false;
        }
        DishwasherBlockEntity dishwasher = (DishwasherBlockEntity)be;
        if (!dishwasher.canInsertDirtyPlates()) {
            return false;
        }
        IItemHandler maidInv = MaidUtils.getInventory(maid);
        if (maidInv == null) {
            return false;
        }
        int dirtyCount = DishwashingBridge.countDirtyPlatesInMaid(maid);
        if (dirtyCount <= 0) {
            return false;
        }
        int toWash = Math.min(dirtyCount, 16);
        try {
            FakePlayer fakePlayer = FakePlayerFactory.get((ServerLevel)level, (GameProfile)new GameProfile(UUID.randomUUID(), "MaidDishwasher"));
            if (fakePlayer == null) {
                return false;
            }
            fakePlayer.setPos((double)dwPos.getX() + 0.5, (double)dwPos.getY(), (double)dwPos.getZ() + 0.5);
            int remaining = toWash;
            for (int i = 0; i < maidInv.getSlots() && remaining > 0; ++i) {
                int take;
                ItemStack extracted;
                ResourceLocation rl;
                ItemStack stack = maidInv.getStackInSlot(i);
                if (stack.isEmpty() || (rl = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null || !rl.toString().equals("ordertocook:dirty_plate") || (extracted = maidInv.extractItem(i, take = Math.min(remaining, stack.getCount()), false)).isEmpty()) continue;
                fakePlayer.getInventory().add(extracted);
                remaining -= take;
            }
            int actualWashed = toWash - remaining;
            if (actualWashed <= 0) {
                return false;
            }
            dishwasher.insertDirtyPlates((Player)fakePlayer);
            MaidRestaurantBusiness.LOGGER.info("\u6d17\u7897\uff1a\u653e\u5165{}\u4e2a\u810f\u76d8\u5b50", actualWashed);
            return true;
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("Error inserting dirty plates", t);
            return false;
        }
    }

    private static boolean canTakeCleanPlates(ServerLevel level, BlockPos dwPos) {
        BlockEntity be = level.getBlockEntity(dwPos);
        if (!(be instanceof DishwasherBlockEntity)) {
            return false;
        }
        DishwasherBlockEntity dishwasher = (DishwasherBlockEntity)be;
        return dishwasher.canTakeCleanPlates();
    }

    private static void takeCleanPlatesFromDishwasher(ServerLevel level, EntityMaid maid, BlockPos dwPos) {
        BlockEntity be = level.getBlockEntity(dwPos);
        if (!(be instanceof DishwasherBlockEntity)) {
            return;
        }
        DishwasherBlockEntity dishwasher = (DishwasherBlockEntity)be;
        if (!dishwasher.canTakeCleanPlates()) {
            return;
        }
        IItemHandler maidInv = MaidUtils.getInventory(maid);
        if (maidInv == null) {
            return;
        }
        try {
            FakePlayer fakePlayer = FakePlayerFactory.get((ServerLevel)level, (GameProfile)new GameProfile(UUID.randomUUID(), "MaidDishwasher"));
            if (fakePlayer == null) {
                return;
            }
            fakePlayer.setPos((double)dwPos.getX() + 0.5, (double)dwPos.getY(), (double)dwPos.getZ() + 0.5);
            dishwasher.takeCleanPlates((Player)fakePlayer);
            for (int i = 0; i < fakePlayer.getInventory().getContainerSize(); ++i) {
                ResourceLocation rl;
                ItemStack stack = fakePlayer.getInventory().getItem(i);
                if (stack.isEmpty() || (rl = ForgeRegistries.ITEMS.getKey(stack.getItem())) == null || !rl.toString().equals("ordertocook:clean_plate")) continue;
                ItemStack remainder = ItemHandlerHelper.insertItemStacked((IItemHandler)maidInv, (ItemStack)stack.copy(), (boolean)false);
                stack.setCount(remainder.getCount());
            }
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("Error taking clean plates", t);
        }
    }

    private static void scanAndStartTasks(ServerLevel level, BusinessManager manager) {
        ArrayList<BlockPos> dirtyPlates = new ArrayList<BlockPos>();
        ArrayList<BlockPos> dishwashers = new ArrayList<BlockPos>();
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            double px = MaidUtils.getX((Entity)player);
            double py = MaidUtils.getY((Entity)player);
            double pz = MaidUtils.getZ((Entity)player);
            BlockPos center = BlockPos.containing((double)px, (double)py, (double)pz);
            for (BlockPos pos : BlockPos.betweenClosed((BlockPos)center.offset(-16, -4, -16), (BlockPos)center.offset(16, 4, 16))) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock().getClass().getName().contains("FoodPlateBlock") && DishwashingBridge.isDirtyStage(state)) {
                    dirtyPlates.add(pos.immutable());
                }
                if (!(level.getBlockEntity(pos) instanceof DishwasherBlockEntity)) continue;
                dishwashers.add(pos.immutable());
            }
        }
        if (dirtyPlates.isEmpty()) {
            return;
        }
        if (!dishwashers.isEmpty()) {
            for (BlockPos dwPos : dishwashers) {
                EntityMaid maid;
                // 找到最近的已激活打单机作为绑定参考
                BlockPos nearestMachine = findNearestActivatedMachine(level, dwPos, manager);
                if (dishTasks.containsKey(dwPos) || (maid = MaidUtils.findWaiterMaidSmart(level, dwPos, 16, nearestMachine)) == null) continue;
                // 任务冲突检查：如果女仆已有任务在执行，不分配洗碗任务
                if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) {
                    MaidRestaurantBusiness.LOGGER.info("洗碗: 女仆 {} 已有任务在执行，跳过分配", maid.getName().getString());
                    continue;
                }
                if (MaidUtils.isOccupied(maid)) {
                    MaidRestaurantBusiness.LOGGER.info("洗碗: 女仆 {} 被标记为忙碌，跳过分配", maid.getName().getString());
                    continue;
                }
                dishTasks.put(dwPos, new DishTask(dwPos, maid, nearestMachine));
                MaidRestaurantBusiness.LOGGER.info("洗碗：发起任务 洗碗机={}", dwPos);

                // TaskManager集成：创建洗碗任务
                String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_DISHWASHING, dwPos, nearestMachine);
                if (taskId != null) {
                    TaskManager.getInstance().assignTask(maid.getUUID(), TaskManager.TYPE_DISHWASHING, level);
                    MaidRestaurantBusiness.LOGGER.info("洗碗: TaskManager创建任务 {} 分配给女仆 {}", taskId, maid.getName().getString());
                }
            }
        } else {
            for (BlockPos platePos : dirtyPlates) {
                EntityMaid maid;
                BlockPos taskKey = platePos;
                BlockPos nearestMachine = findNearestActivatedMachine(level, platePos, manager);
                if (dishTasks.containsKey(taskKey) || (maid = MaidUtils.findWaiterMaidSmart(level, platePos, 16, nearestMachine)) == null) continue;
                // 任务冲突检查：如果女仆已有任务在执行，不分配收盘子任务
                if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) {
                    MaidRestaurantBusiness.LOGGER.info("收盘子: 女仆 {} 已有任务在执行，跳过分配", maid.getName().getString());
                    continue;
                }
                if (MaidUtils.isOccupied(maid)) {
                    MaidRestaurantBusiness.LOGGER.info("收盘子: 女仆 {} 被标记为忙碌，跳过分配", maid.getName().getString());
                    continue;
                }
                dishTasks.put(taskKey, new DishTask(null, maid, nearestMachine));
                MaidRestaurantBusiness.LOGGER.info("洗碗：无洗碗机，仅收脏盘子 位置={}", platePos);

                // TaskManager集成：创建收盘子任务
                String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_DISHWASHING, platePos, nearestMachine);
                if (taskId != null) {
                    TaskManager.getInstance().assignTask(maid.getUUID(), TaskManager.TYPE_DISHWASHING, level);
                    MaidRestaurantBusiness.LOGGER.info("洗碗: TaskManager创建收盘子任务 {} 分配给女仆 {}", taskId, maid.getName().getString());
                }
                break;
            }
        }
    }
    
    /**
     * 找到指定位置附近最近的已激活打单机
     */
    private static BlockPos findNearestActivatedMachine(ServerLevel level, BlockPos center, BusinessManager manager) {
        BlockPos nearest = null;
        double minDist = Double.MAX_VALUE;
        for (BlockPos machinePos : manager.getActivatedMachines()) {
            double dist = machinePos.distSqr(center);
            if (dist < minDist) {
                minDist = dist;
                nearest = machinePos;
            }
        }
        return nearest;
    }

    private static class DishTask {
        final BlockPos dishwasherPos;
        final BlockPos machinePos;
        int state;
        long lastChange;
        final long startTime; // 任务开始时间，用于总超时检测
        final WeakReference<EntityMaid> maidRef;
        BlockPos currentPlatePos;

        DishTask(BlockPos dishwasherPos, EntityMaid maid, BlockPos machinePos) {
            this.dishwasherPos = dishwasherPos;
            this.machinePos = machinePos;
            this.state = 0;
            this.lastChange = 0L;
            this.startTime = maid.level().getGameTime();
            this.maidRef = new WeakReference<EntityMaid>(maid);
            this.currentPlatePos = null;
            MaidUtils.setOccupied(maid, true);
            MaidRestaurantBusiness.LOGGER.info("洗碗: 任务创建 女仆={} 洗碗机={} 开始时间={}", maid.getName().getString(), dishwasherPos, this.startTime);
        }

        void cleanup() {
            EntityMaid maid = (EntityMaid)this.maidRef.get();
            if (maid != null) {
                try {
                    // 停止寻路，清除大脑记忆，防止女仆AI状态卡住
                    maid.getNavigation().stop();
                    maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.WALK_TARGET);
                    maid.getBrain().eraseMemory(net.minecraft.world.entity.ai.memory.MemoryModuleType.PATH);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("洗碗任务清理时重置女仆AI状态失败", t);
                }
                MaidUtils.setOccupied(maid, false);
            }
        }
    }
}
