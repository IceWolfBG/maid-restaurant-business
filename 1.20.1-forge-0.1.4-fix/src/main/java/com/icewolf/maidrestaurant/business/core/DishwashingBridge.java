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
    private static final Map<BlockPos, CollectPlateTask> collectTasks = new HashMap<BlockPos, CollectPlateTask>();
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
        // 处理进行中的任务（收盘子和洗碗）
        DishwashingBridge.tickCollectTasks(level);
        DishwashingBridge.tickDishTasks(level);
        
        boolean collectUnlocked = false;
        boolean washUnlocked = false;
        for (BlockPos machinePos : manager.getActivatedMachines()) {
            if (!ProgressionManager.isDishwashingUnlocked(level, machinePos)) continue;
            // 排班表配置检查
            if (MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_COLLECT)) {
                collectUnlocked = true;
            }
            if (MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_WASH)) {
                washUnlocked = true;
            }
            if (collectUnlocked && washUnlocked) break;
        }
        
        try {
            if (collectUnlocked) {
                DishwashingBridge.scanAndStartCollectTasks(level, manager);
            }
            if (washUnlocked) {
                DishwashingBridge.scanMaidBackpacksAndStartWashing(level, manager);
            }
        }
        catch (Throwable t) {
            MaidRestaurantBusiness.LOGGER.error("Error in dishwashing scan", t);
        }
    }

    private static void tickCollectTasks(ServerLevel level) {
        Iterator<Map.Entry<BlockPos, CollectPlateTask>> it = collectTasks.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, CollectPlateTask> entry = it.next();
            CollectPlateTask task = entry.getValue();
            EntityMaid maid = (EntityMaid)task.maidRef.get();
            if (maid == null) {
                TaskManager.getInstance().failTask(null, "collect plate maid missing");
                task.cleanup();
                it.remove();
                continue;
            }
            TaskManager.getInstance().heartbeat(maid.getUUID(), level.getGameTime());
            long now = level.getGameTime();
            
            // 总超时保护：300tick（15秒）
            long taskDuration = now - task.startTime;
            if (taskDuration > 300L) {
                MaidRestaurantBusiness.LOGGER.warn("收盘子: 任务总超时（{}tick），强制结束 女仆={}", taskDuration, maid.getName().getString());
                TaskManager.getInstance().failTask(maid.getUUID(), "collect plate timeout");
                task.cleanup();
                it.remove();
                continue;
            }
            
            switch (task.state) {
                case 0: { // 前往脏盘子
                    if (task.currentPlatePos == null) {
                        task.currentPlatePos = DishwashingBridge.findNearestDirtyPlate(level, maid);
                        task.lastChange = now;
                        if (task.currentPlatePos == null) {
                            TaskManager.getInstance().completeTask(maid.getUUID());
                            task.cleanup();
                            it.remove();
                            break;
                        }
                    }
                    // 移动超时保护
                    if (now - task.lastChange > 100L && !MaidUtils.isNear(maid, task.currentPlatePos, 3.0)) {
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
                case 1: { // 收取脏盘子
                    if (DishwashingBridge.collectDirtyPlate(level, maid, task.currentPlatePos)) {
                        TaskManager.getInstance().completeTask(maid.getUUID());
                        task.cleanup();
                        it.remove();
                    } else {
                        task.currentPlatePos = null;
                        task.state = 0;
                    }
                    break;
                }
            }
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
            
            // 目标消失检测：洗碗机是否还存在（每10tick检测一次）
            if (task.dishwasherPos != null && now % 10L == 0L) {
                BlockEntity be = level.getBlockEntity(task.dishwasherPos);
                if (!(be instanceof DishwasherBlockEntity)) {
                    MaidRestaurantBusiness.LOGGER.warn("洗碗: 洗碗机 {} 已消失，立即结束任务 女仆={} 当前状态={}", 
                        task.dishwasherPos, maid.getName().getString(), task.state);
                    TaskManager.getInstance().failTask(maid.getUUID(), "dishwasher disappeared");
                    task.cleanup();
                    it.remove();
                    continue;
                }
            }
            
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
            }
            
            switch (task.state) {
                case 0: { // 检查女仆背包脏盘子，达到阈值就去洗碗机
                    int dirtyCount = DishwashingBridge.countDirtyPlatesInMaid(maid);
                    if (dirtyCount <= 0) {
                        TaskManager.getInstance().completeTask(maid.getUUID());
                        task.cleanup();
                        it.remove();
                        break;
                    }
                    if (task.dishwasherPos == null) {
                        MaidRestaurantBusiness.LOGGER.warn("洗碗: 无洗碗机位置，任务结束 女仆={}", maid.getName().getString());
                        TaskManager.getInstance().failTask(maid.getUUID(), "no dishwasher position");
                        task.cleanup();
                        it.remove();
                        break;
                    }
                    task.state = 2;
                    task.lastChange = now;
                    break;
                }
                case 2: {
                    if (task.dishwasherPos == null) {
                        TaskManager.getInstance().failTask(maid.getUUID(), "dishwasher pos null in state 2");
                        task.cleanup();
                        it.remove();
                        break;
                    }
                    if (now - task.lastChange > 200L) {
                        BlockEntity be = level.getBlockEntity(task.dishwasherPos);
                        if (!(be instanceof DishwasherBlockEntity)) {
                            MaidRestaurantBusiness.LOGGER.warn("洗碗：洗碗机不存在，任务结束");
                            TaskManager.getInstance().failTask(maid.getUUID(), "dishwasher not exist in state 2");
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
                        break;
                    }
                    MaidRestaurantBusiness.LOGGER.warn("洗碗：放入脏盘子失败，结束任务 女仆={}", maid.getName().getString());
                    TaskManager.getInstance().failTask(maid.getUUID(), "insert dirty plates failed");
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
                    MaidRestaurantBusiness.LOGGER.warn("洗碗：等待超时，结束任务 女仆={}", maid.getName().getString());
                    TaskManager.getInstance().failTask(maid.getUUID(), "wait for clean plates timeout");
                    task.cleanup();
                    it.remove();
                    break;
                }
                case 5: {
                    DishwashingBridge.takeCleanPlatesFromDishwasher(level, maid, task.dishwasherPos);
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

    /**
     * 扫描脏盘子并创建收盘子任务（独立任务，只负责收盘子）
     * 以打单机为中心扫描，不依赖玩家位置
     */
    private static void scanAndStartCollectTasks(ServerLevel level, BusinessManager manager) {
        int scanRange = BusinessConfig.dishScanRange;
        ArrayList<BlockPos> dirtyPlates = new ArrayList<BlockPos>();
        
        // 以所有已激活打单机为中心扫描
        for (BlockPos machinePos : manager.getActivatedMachines()) {
            if (!ProgressionManager.isDishwashingUnlocked(level, machinePos)) continue;
            if (!MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_COLLECT)) continue;
            
            for (BlockPos pos : BlockPos.betweenClosed(
                    machinePos.offset(-scanRange, -4, -scanRange), 
                    machinePos.offset(scanRange, 4, scanRange))) {
                BlockState state = level.getBlockState(pos);
                if (state.getBlock().getClass().getName().contains("FoodPlateBlock") && DishwashingBridge.isDirtyStage(state)) {
                    if (!dirtyPlates.contains(pos.immutable())) {
                        dirtyPlates.add(pos.immutable());
                    }
                }
            }
        }
        
        if (dirtyPlates.isEmpty()) {
            return;
        }
        
        // 为每个脏盘子创建收盘子任务
        for (BlockPos platePos : dirtyPlates) {
            if (collectTasks.containsKey(platePos)) continue;
            
            BlockPos nearestMachine = findNearestActivatedMachine(level, platePos, manager);
            EntityMaid maid = MaidUtils.findWaiterMaidSmart(level, platePos, scanRange, nearestMachine);
            if (maid == null) continue;
            
            // 任务冲突检查
            if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) {
                continue;
            }
            if (MaidUtils.isOccupied(maid)) {
                continue;
            }
            
            collectTasks.put(platePos, new CollectPlateTask(platePos, maid, nearestMachine));
            
            // TaskManager集成
            String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_COLLECT_PLATE, platePos, nearestMachine);
            if (taskId != null) {
                TaskManager.getInstance().assignTask(maid.getUUID(), TaskManager.TYPE_COLLECT_PLATE, level);
            }
            break; // 每次只分配一个收盘子任务，避免多个女仆抢
        }
    }
    
    /**
     * 扫描女仆背包，超过阈值且有洗碗机时创建洗碗任务（独立任务，基于背包检测）
     * 以打单机为中心扫描，不依赖玩家位置
     */
    private static void scanMaidBackpacksAndStartWashing(ServerLevel level, BusinessManager manager) {
        int scanRange = BusinessConfig.dishScanRange;
        
        // 遍历所有已激活打单机
        for (BlockPos machinePos : manager.getActivatedMachines()) {
            if (!ProgressionManager.isDishwashingUnlocked(level, machinePos)) continue;
            if (!MaidUtils.isScheduleBoardEnabled(level, machinePos, MaidUtils.SCHED_AUTO_WASH)) continue;
            
            // 以打单机为中心扫描洗碗机
            ArrayList<BlockPos> dishwashers = new ArrayList<BlockPos>();
            for (BlockPos pos : BlockPos.betweenClosed(
                    machinePos.offset(-scanRange, -4, -scanRange), 
                    machinePos.offset(scanRange, 4, scanRange))) {
                if (level.getBlockEntity(pos) instanceof DishwasherBlockEntity) {
                    dishwashers.add(pos.immutable());
                }
            }
            if (dishwashers.isEmpty()) {
                continue; // 这台打单机附近没有洗碗机，跳过
            }
            
            // 查找附近的侍者女仆（scanRange格范围内）
            List<EntityMaid> nearbyMaids = new ArrayList<>();
            for (ServerLevel lvl : level.getServer().getAllLevels()) {
                nearbyMaids.addAll(lvl.getEntitiesOfClass(EntityMaid.class, 
                    new AABB(machinePos).inflate(scanRange)));
            }
            
            for (EntityMaid maid : nearbyMaids) {
                if (maid == null || !maid.isAlive()) continue;
                
                // 只处理侍者职业的女仆
                if (!MaidUtils.TASK_WAITER.equals(MaidUtils.getTaskUid(maid))) continue;
                
                // 检查女仆是否绑定到该打单机（如果有绑定的话）
                if (MaidUtils.getWorkerCountForMachine(machinePos) > 0) {
                    if (!MaidUtils.isMaidBoundToMachine(maid.getUUID(), machinePos)) continue;
                }
                
                // 任务冲突检查
                if (TaskManager.getInstance().hasMaidTask(maid.getUUID())) continue;
                if (MaidUtils.isOccupied(maid)) continue;
                
                // 检查背包脏盘子数量
                int dirtyCount = DishwashingBridge.countDirtyPlatesInMaid(maid);
                int minPlates = DishwashingBridge.getMinPlatesToWash(level, machinePos);
                
                if (dirtyCount >= minPlates) {
                    // 找到最近的洗碗机
                    BlockPos nearestDw = null;
                    double minDist = Double.MAX_VALUE;
                    for (BlockPos dwPos : dishwashers) {
                        double dist = dwPos.distSqr(maid.blockPosition());
                        if (dist < minDist) {
                            minDist = dist;
                            nearestDw = dwPos;
                        }
                    }
                    
                    if (nearestDw != null && !dishTasks.containsKey(nearestDw)) {
                        dishTasks.put(nearestDw, new DishTask(nearestDw, maid, machinePos));
                        
                        // TaskManager集成
                        String taskId = TaskManager.getInstance().createTask(TaskManager.TYPE_DISHWASHING, nearestDw, machinePos);
                        if (taskId != null) {
                            TaskManager.getInstance().assignTask(maid.getUUID(), TaskManager.TYPE_DISHWASHING, level);
                        }
                    }
                }
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

    /**
     * 收盘子任务（独立任务，只负责收一个脏盘子）
     */
    private static class CollectPlateTask {
        final BlockPos machinePos;
        int state; // 0=前往盘子, 1=收取
        long lastChange;
        final long startTime;
        final WeakReference<EntityMaid> maidRef;
        BlockPos currentPlatePos;

        CollectPlateTask(BlockPos platePos, EntityMaid maid, BlockPos machinePos) {
            this.machinePos = machinePos;
            this.state = 0;
            this.lastChange = 0L;
            this.startTime = maid.level().getGameTime();
            this.maidRef = new WeakReference<EntityMaid>(maid);
            this.currentPlatePos = platePos;
            MaidUtils.setOccupied(maid, true);
            // 显示侍者收盘子气泡
            try {
                com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.waiterCollectPlate(maid);
            } catch (Exception e) {}
        }

        void cleanup() {
            EntityMaid maid = (EntityMaid)this.maidRef.get();
            if (maid != null) {
                try {
                    // 调用TaskSafetyUtils彻底重置女仆状态
                    TaskSafetyUtils.resetMaidState(maid);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("收盘子任务清理时TaskSafetyUtils.resetMaidState失败", t);
                    // 回退：手动清理
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
            // 显示侍者洗碗气泡
            try {
                com.icewolf.maidrestaurant.business.util.MaidChatBubbleHelper.waiterWashing(maid);
            } catch (Exception e) {}
        }

        void cleanup() {
            EntityMaid maid = (EntityMaid)this.maidRef.get();
            if (maid != null) {
                try {
                    // 调用TaskSafetyUtils彻底重置女仆状态
                    TaskSafetyUtils.resetMaidState(maid);
                } catch (Throwable t) {
                    MaidRestaurantBusiness.LOGGER.warn("洗碗任务清理时TaskSafetyUtils.resetMaidState失败", t);
                    // 回退：手动清理
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
