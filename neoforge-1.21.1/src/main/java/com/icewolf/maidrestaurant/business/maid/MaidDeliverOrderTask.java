/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  cn.breezeth.ordertocook.block.entity.FoodPlateBlockEntity
 *  cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity
 *  cn.breezeth.ordertocook.entity.CustomerEntity
 *  cn.breezeth.ordertocook.item.TakeoutBagItem
 *  cn.breezeth.ordertocook.registry.ModItems
 *  com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask
 *  com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid
 *  com.google.common.collect.ImmutableMap
 *  com.mastermarisa.maid_restaurant.api.IStep
 *  com.mastermarisa.maid_restaurant.maid.task.base.StepResult
 *  com.mastermarisa.maid_restaurant.init.ModEntities
 *  com.mastermarisa.maid_restaurant.maid.TaskWaiter
 *  com.mastermarisa.maid_restaurant.maid.task.base.MaidTickRateTask
 *  com.mastermarisa.maid_restaurant.utils.BehaviorUtils
 *  com.mojang.authlib.GameProfile
 *  net.minecraft.core.BlockPos
 *  net.minecraft.core.Vec3i
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.server.level.ServerLevel
 *  net.minecraft.server.level.ServerPlayer
 *  net.minecraft.world.InteractionResult
 *  net.minecraft.world.entity.Entity
 *  net.minecraft.world.entity.LivingEntity
 *  net.minecraft.world.entity.ai.behavior.BlockPosTracker
 *  net.minecraft.world.entity.ai.behavior.PositionTracker
 *  net.minecraft.world.entity.ai.memory.MemoryModuleType
 *  net.minecraft.world.entity.ai.memory.MemoryStatus
 *  net.minecraft.world.entity.player.Player
 *  net.minecraft.world.item.Item
 *  net.minecraft.world.item.ItemStack
 *  net.minecraft.world.level.block.entity.BlockEntity
 *  net.minecraft.world.level.block.state.BlockState
 *  net.minecraft.world.level.chunk.LevelChunk
 *  net.minecraft.world.phys.AABB
 *  net.neoforged.neoforge.common.util.FakePlayer
 *  net.neoforged.neoforge.common.util.FakePlayerFactory
 *  net.neoforged.neoforge.items.IItemHandler
 *  net.neoforged.neoforge.items.ItemHandlerHelper
 *  net.neoforged.neoforge.items.wrapper.CombinedInvWrapper
 *  net.neoforged.neoforge.registries.NeoNeoNeoForgeRegistries
 */
package com.icewolf.maidrestaurant.business.maid;

import cn.breezeth.ordertocook.block.entity.FoodPlateBlockEntity;
import cn.breezeth.ordertocook.block.entity.TakeoutBoxBlockEntity;
import cn.breezeth.ordertocook.item.TakeoutBagItem;
import com.github.tartaricacid.touhoulittlemaid.api.task.IMaidTask;
import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.ImmutableMap;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.core.BusinessManager;
import com.icewolf.maidrestaurant.business.core.CustomerCompat;
import com.icewolf.maidrestaurant.business.core.OrderBridge;
import com.icewolf.maidrestaurant.business.core.OtcCompat;
import com.icewolf.maidrestaurant.business.core.ProgressionManager;
import com.mastermarisa.maid_restaurant.api.IStep;
import com.mastermarisa.maid_restaurant.maid.task.base.StepResult;
import com.mastermarisa.maid_restaurant.init.ModEntities;
import com.mastermarisa.maid_restaurant.maid.TaskWaiter;
import com.mastermarisa.maid_restaurant.maid.task.base.MaidTickRateTask;
import com.mastermarisa.maid_restaurant.utils.BehaviorUtils;
import com.mojang.authlib.GameProfile;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.behavior.BlockPosTracker;
import net.minecraft.world.entity.ai.behavior.PositionTracker;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.common.util.FakePlayerFactory;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.wrapper.CombinedInvWrapper;

public class MaidDeliverOrderTask
extends MaidTickRateTask
implements IStep {
    private static final String TAG_COUNTER_POS = "BusinessDeliverCounter";
    private static final String TAG_CUSTOMER_ID = "BusinessDeliverCustomerId";
    private static final String TAG_STAGE = "BusinessDeliverStage";
    private static final String TAG_PICKUP_RETRY = "BusinessDeliverPickupRetry";
    private static final int STAGE_GO_TO_COUNTER = 0;
    private static final int STAGE_GO_TO_CUSTOMER = 1;
    private static final int MAX_PICKUP_RETRY = 3; // 连续3次拿不到餐盘就取消任务（餐盘可能被玩家拿走）
    private final float movementSpeed;
    private final double closeEnoughDist;
    // 多侍者任务锁：记录正在被送餐的操作台，防止多个侍者同时接取同一个操作台的送餐任务
    private static final java.util.Set<Long> activeDeliveries = java.util.Collections.newSetFromMap(new java.util.concurrent.ConcurrentHashMap<>());

    public MaidDeliverOrderTask(float movementSpeed, double closeEnoughDist) {
        super((Map)ImmutableMap.of((Object)((MemoryModuleType)ModEntities.TARGET_POS.get()), MemoryStatus.VALUE_ABSENT), 5, 120);
        this.movementSpeed = movementSpeed;
        this.closeEnoughDist = closeEnoughDist;
    }

    protected boolean checkExtraStartConditions(ServerLevel level, EntityMaid maid) {
        BusinessManager manager = MaidRestaurantBusiness.getManager();
        if (manager == null) {
            return false;
        }
        IMaidTask task = maid.getTask();
        if (!(task instanceof TaskWaiter)) {
            return false;
        }
        BlockPos counterPos = this.findCounterWithPlate(level, maid);
        if (counterPos == null) {
            return false;
        }
        // 多侍者任务锁：检查该操作台是否已被其他侍者接取送餐任务
        long counterLong = counterPos.asLong();
        if (activeDeliveries.contains(counterLong)) {
            return false;
        }
        boolean hasActivatedMachine = false;
        for (BlockPos mp : manager.getActivatedMachines()) {
            if (!(mp.distSqr((Vec3i)counterPos) <= 64.0)) continue;
            hasActivatedMachine = true;
            break;
        }
        if (!hasActivatedMachine) {
            return false;
        }
        // 多侍者任务锁：标记该操作台正在被送餐，防止其他侍者同时接取
        activeDeliveries.add(counterLong);
        EntityMaid maidLE = maid;
        CompoundTag data = maidLE.getPersistentData();
        data.putLong(TAG_COUNTER_POS, counterPos.asLong());
        data.remove(TAG_CUSTOMER_ID);
        data.putInt(TAG_STAGE, 0);
        data.putInt(TAG_PICKUP_RETRY, 0);
        BehaviorUtils.setTargetPos((LivingEntity)maidLE, (PositionTracker)new BlockPosTracker(counterPos), (int)5);
        BehaviorUtils.setWalkAndLookTargetMemories((LivingEntity)maidLE, (BlockPos)counterPos, (BlockPos)counterPos, (float)this.movementSpeed, (int)0);
        return true;
    }

    protected void tick(ServerLevel level, EntityMaid maid, long gameTime) {
        if (!this.shouldTick(level, maid, gameTime)) {
            return;
        }
        EntityMaid maidLE = maid;
        CompoundTag data = maidLE.getPersistentData();
        int stage = data.getInt(TAG_STAGE);
        BlockPos counterPos = BlockPos.of((long)data.getLong(TAG_COUNTER_POS));
        if (stage == 0) {
            if (maidLE.distanceToSqr((double)counterPos.getX() + 0.5, (double)counterPos.getY(), (double)counterPos.getZ() + 0.5) <= this.closeEnoughDist * this.closeEnoughDist) {
                // 直接拿起餐盘，不先检查顾客（防止因为顾客缺失导致永远无法继续）
                ItemStack plate = this.pickUpPlate(level, counterPos, maid);
                if (!plate.isEmpty()) {
                    // 拿起餐盘后，从餐盘NBT中获取orderId，然后查找顾客
                    String orderId = "";
                    CompoundTag plateTag = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(plate);
                    if (plateTag != null && plateTag.contains("OrderId")) {
                        orderId = plateTag.getString("OrderId");
                    }
                    LivingEntity customer = null;
                    if (!orderId.isEmpty()) {
                        customer = this.findCustomerByOrderId(level, counterPos, orderId);
                    }
                    if (customer == null) {
                        // 如果找不到顾客，任务失败，但餐盘已经在背包中了（不会丢失）
                        MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: no customer found for orderId={}, task failed but plate kept in inventory", orderId);
                        this.accept(level, maid, StepResult.FAIL);
                        return;
                    }
                    data.putString(TAG_CUSTOMER_ID, CustomerCompat.getCustomerId(customer));
                    data.putInt(TAG_STAGE, 1);
                    BlockPos targetPos = MaidDeliverOrderTask.findSafeDeliveryPos(level, MaidDeliverOrderTask.customerPos(customer));
                    BehaviorUtils.setTargetPos((LivingEntity)maidLE, (PositionTracker)new BlockPosTracker(targetPos), (int)5);
                    BehaviorUtils.setWalkAndLookTargetMemories((LivingEntity)maidLE, (BlockPos)targetPos, (BlockPos)targetPos, (float)this.movementSpeed, (int)0);
                } else {
                    // 拿不到餐盘，增加重试次数
                    int retry = data.getInt(TAG_PICKUP_RETRY) + 1;
                    data.putInt(TAG_PICKUP_RETRY, retry);
                    MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: failed to pick up plate at counter {} (retry {}/{})", counterPos, retry, MAX_PICKUP_RETRY);
                    if (retry >= MAX_PICKUP_RETRY) {
                        // 连续多次拿不到餐盘，说明餐盘可能被玩家拿走了，取消任务
                        MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: plate pickup failed {} times, cancelling task for counter {}", MAX_PICKUP_RETRY, counterPos);
                        this.accept(level, maid, StepResult.FAIL);
                        return;
                    }
                    BehaviorUtils.setWalkAndLookTargetMemories((LivingEntity)maidLE, (BlockPos)counterPos, (BlockPos)counterPos, (float)this.movementSpeed, (int)0);
                }
            } else {
                BehaviorUtils.setWalkAndLookTargetMemories((LivingEntity)maidLE, (BlockPos)counterPos, (BlockPos)counterPos, (float)this.movementSpeed, (int)0);
            }
        } else if (stage == 1) {
            LivingEntity customer = this.findCustomerById(level, counterPos, data.getString(TAG_CUSTOMER_ID));
            if (customer == null) {
                this.accept(level, maid, StepResult.FAIL);
                return;
            }
            BlockPos customerPos = MaidDeliverOrderTask.customerPos(customer);
            BlockPos targetPos = MaidDeliverOrderTask.findSafeDeliveryPos(level, customerPos);
            double dist = maidLE.distanceToSqr((double)targetPos.getX() + 0.5, (double)targetPos.getY(), (double)targetPos.getZ() + 0.5);
            if (dist <= this.closeEnoughDist * this.closeEnoughDist * 2.25) {
                this.deliverToCustomer(level, maid, customer, counterPos);
            } else {
                BehaviorUtils.setWalkAndLookTargetMemories((LivingEntity)maidLE, (BlockPos)targetPos, (BlockPos)targetPos, (float)this.movementSpeed, (int)0);
            }
        }
    }

    protected boolean canStillUseCheck(ServerLevel level, EntityMaid maid, long gameTimeIn) {
        CompoundTag data = maid.getPersistentData();
        return data.contains(TAG_COUNTER_POS) && (data.getInt(TAG_STAGE) == 0 || data.getInt(TAG_STAGE) == 1);
    }

    protected void stop(ServerLevel level, EntityMaid maid, long gameTime) {
        EntityMaid maidLE = maid;
        CompoundTag data = maidLE.getPersistentData();
        // 多侍者任务锁：释放该操作台的送餐锁，让其他侍者可以接取
        long counterLong = data.getLong(TAG_COUNTER_POS);
        if (counterLong != 0L) {
            activeDeliveries.remove(counterLong);
        }
        int stage = data.getInt(TAG_STAGE);
        if (stage == 1) {
            BlockPos counterPos = BlockPos.of((long)data.getLong(TAG_COUNTER_POS));
            LivingEntity customer = this.findCustomerById(level, counterPos, data.getString(TAG_CUSTOMER_ID));
            if (customer != null) {
                BlockPos cp = MaidDeliverOrderTask.customerPos(customer);
                double dist = maidLE.distanceToSqr((double)cp.getX() + 0.5, (double)cp.getY(), (double)cp.getZ() + 0.5);
                if (dist <= this.closeEnoughDist * this.closeEnoughDist * 2.25) {
                    this.accept(level, maid, StepResult.SUCCESS);
                } else {
                    this.accept(level, maid, StepResult.FAIL);
                }
            } else {
                this.accept(level, maid, StepResult.FAIL);
            }
        } else {
            this.accept(level, maid, StepResult.FAIL);
        }
        BehaviorUtils.eraseTargetPos((LivingEntity)maidLE);
        maidLE.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        data.remove(TAG_COUNTER_POS);
        data.remove(TAG_CUSTOMER_ID);
        data.remove(TAG_STAGE);
        data.remove(TAG_PICKUP_RETRY);
    }

    public void accept(ServerLevel level, EntityMaid maid, StepResult result) {
        EntityMaid maidLE = maid;
        if (result != StepResult.SUCCESS) {
            // 任务失败时，不从女仆背包中移除餐盘（餐盘可能还需要用来送餐，或者玩家可以手动处理）
            CompoundTag data = maidLE.getPersistentData();
            long counterLong = data.getLong(TAG_COUNTER_POS);
            if (counterLong != 0L) {
            }
            return;
        }
        CompoundTag data = maidLE.getPersistentData();
        long counterLong = data.getLong(TAG_COUNTER_POS);
        if (counterLong != 0L) {
            BlockPos counterPos = BlockPos.of((long)counterLong);
            BusinessManager manager = MaidRestaurantBusiness.getManager();
            if (manager != null) {
                manager.getActiveOrders().remove(counterPos);
                manager.getCounterToMachine().remove(counterPos);
            }
        }
    }

    private BlockPos findCounterWithPlate(ServerLevel level, EntityMaid maid) {
        BusinessManager manager = MaidRestaurantBusiness.getManager();
        if (manager == null) {
            return null;
        }
        EntityMaid maidLE = maid;
        BlockPos maidPos = maidLE.blockPosition();
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        double searchRange = 256.0;
        int scannedBE = 0;
        int foundCounter = 0;
        int foundPlate = 0;
        int chunkX = maidPos.getX() >> 4;
        int chunkZ = maidPos.getZ() >> 4;
        for (int cx = chunkX - 1; cx <= chunkX + 1; ++cx) {
            for (int cz = chunkZ - 1; cz <= chunkZ + 1; ++cz) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) continue;
                for (BlockPos pos : chunk.getBlockEntitiesPos()) {
                    BlockEntity be = chunk.getBlockEntity(pos);
                    if (be == null) continue;
                    ++scannedBE;
                    if (!(be instanceof TakeoutBoxBlockEntity)) continue;
                    ++foundCounter;
                    BlockPos counterPos = pos.immutable();
                    double dist = counterPos.distSqr((Vec3i)maidPos);
                    if (dist > searchRange) continue;
                    boolean plateFound = false;
                    // 调试：检查操作台正上方的方块状态
                    BlockPos abovePos = counterPos.above();
                    net.minecraft.world.level.block.state.BlockState aboveState = level.getBlockState(abovePos);
                    BlockEntity aboveBe = level.getBlockEntity(abovePos);
                    for (int dy = 0; dy <= 2 && !plateFound; ++dy) {
                        for (int dx = -2; dx <= 2 && !plateFound; ++dx) {
                            for (int dz = -2; dz <= 2 && !plateFound; ++dz) {
                                FoodPlateBlockEntity plateEntity;
                                BlockPos platePos = counterPos.offset(dx, dy, dz);
                                BlockEntity plateBe = level.getBlockEntity(platePos);
                                if (plateBe != null && !(plateBe instanceof FoodPlateBlockEntity)) {
                                    // 调试：列出操作台周围的非餐盘方块实体
                                }
                                if (!(plateBe instanceof FoodPlateBlockEntity) || (plateEntity = (FoodPlateBlockEntity)plateBe).getPlateStack().isEmpty()) continue;
                                ++foundPlate;
                                BlockPos machinePos = manager.getCounterToMachine().get(counterPos);
                                if (machinePos != null && !ProgressionManager.isDeliveryUnlocked(level, machinePos)) {
                                    continue;
                                }
                                if (!(dist < nearestDist)) continue;
                                nearestDist = dist;
                                nearest = counterPos;
                                plateFound = true;
                            }
                        }
                    }
                }
            }
        }
        if (nearest == null) {
        }
        return nearest;
    }

    private ItemStack peekPlate(ServerLevel level, BlockPos counterPos) {
        for (int dy = 0; dy <= 2; ++dy) {
            for (int dx = -2; dx <= 2; ++dx) {
                for (int dz = -2; dz <= 2; ++dz) {
                    BlockPos abovePos = counterPos.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(abovePos);
                    if (be instanceof FoodPlateBlockEntity plateBe) {
                        ItemStack plateStack = plateBe.getPlateStack();
                        if (!plateStack.isEmpty()) {
                            return plateStack.copy();
                        }
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private ItemStack pickUpPlate(ServerLevel level, BlockPos counterPos, EntityMaid maid) {
        CombinedInvWrapper inv = maid.getAvailableInv(false);
        if (inv == null) {
            MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: maid inventory is null");
            return ItemStack.EMPTY;
        }
        for (int dy = 0; dy <= 2; ++dy) {
            for (int dx = -2; dx <= 2; ++dx) {
                for (int dz = -2; dz <= 2; ++dz) {
                    FoodPlateBlockEntity plateBe;
                    ItemStack plateStack;
                    BlockPos abovePos = counterPos.offset(dx, dy, dz);
                    BlockEntity be = level.getBlockEntity(abovePos);
                    if (!(be instanceof FoodPlateBlockEntity) || (plateStack = (plateBe = (FoodPlateBlockEntity)be).getPlateStack().copy()).isEmpty()) continue;
                    
                    // 调试：记录餐盘的NBT信息
                    CompoundTag plateTag = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(plateStack);
                    
                    // 先模拟插入，检查是否有空间
                    ItemStack remainder = ItemHandlerHelper.insertItemStacked((IItemHandler)inv, (ItemStack)plateStack.copy(), (boolean)true);
                    if (!remainder.isEmpty()) {
                        MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: maid inventory full, cannot pick up plate, remainder={}", remainder.getCount());
                        return ItemStack.EMPTY;
                    }
                    
                    // 先移除餐盘方块和BlockEntity，再实际插入（防止重复）
                    plateBe.setPlateStack(ItemStack.EMPTY);
                    level.removeBlock(abovePos, false);
                    
                    // 实际插入女仆背包
                    ItemStack actualRemainder = ItemHandlerHelper.insertItemStacked((IItemHandler)inv, (ItemStack)plateStack, (boolean)false);
                    if (!actualRemainder.isEmpty()) {
                        // 实际插入失败，把餐盘丢到地上（防止丢失）
                        MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: failed to insert plate into inventory, dropping as item, remainder={}", actualRemainder.getCount());
                        net.minecraft.world.entity.item.ItemEntity itemEntity = new net.minecraft.world.entity.item.ItemEntity(level, abovePos.getX() + 0.5, abovePos.getY() + 0.5, abovePos.getZ() + 0.5, actualRemainder);
                        level.addFreshEntity(itemEntity);
                        return ItemStack.EMPTY;
                    }
                    
                    return plateStack;
                }
            }
        }
        MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: no plate found near counter {}", counterPos);
        return ItemStack.EMPTY;
    }

    private void deliverToCustomer(ServerLevel level, EntityMaid maid, LivingEntity customer, BlockPos counterPos) {
        Player deliverPlayer;
        EntityMaid maidLE = maid;
        CombinedInvWrapper inv = maid.getAvailableInv(false);
        if (inv == null) {
            this.accept(level, maid, StepResult.FAIL);
            return;
        }
        int plateSlot = -1;
        ItemStack plateStack = ItemStack.EMPTY;
        for (int i = 0; i < inv.getSlots(); ++i) {
            ItemStack s = inv.getStackInSlot(i);
            if (s.isEmpty() || !s.is((Item)OtcCompat.FOOD_PLATE())) continue;
            plateSlot = i;
            plateStack = s;
            break;
        }
        if (plateStack.isEmpty()) {
            MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: no plate in maid inventory when delivering");
            this.accept(level, maid, StepResult.FAIL);
            return;
        }
        String plateOrderId = "";
        CompoundTag plateTag = com.icewolf.maidrestaurant.business.util.ItemStackUtils.getTag(plateStack);
        if (plateTag != null && plateTag.contains("OrderId")) {
            plateOrderId = plateTag.getString("OrderId");
        }
        if (!plateOrderId.isEmpty()) {
            boolean customerMatches = CustomerCompat.hasOrderTag(customer, plateOrderId);
            if (!customerMatches) {
                MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: customer mismatch, plate orderId={}, searching correct customer", plateOrderId);
                LivingEntity correctCustomer = this.findCustomerByOrderId(level, counterPos, plateOrderId);
                if (correctCustomer != null) {
                    customer = correctCustomer;
                } else {
                    MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: no matching customer for orderId={}", plateOrderId);
                    this.accept(level, maid, StepResult.FAIL);
                    return;
                }
            }
        }
        Player ownerPlayer = null;
        try {
            // 优先用getOwnerUUID()获取主人UUID（OwnableEntity接口的标准方法）
            UUID ownerUuid = maidLE.getOwnerUUID();
            if (ownerUuid != null && level.getServer() != null) {
                ownerPlayer = level.getServer().getPlayerList().getPlayer(ownerUuid);
            }
            // 如果getOwnerUUID()获取不到（比如玩家离线），再尝试反射getOwner()
            if (ownerPlayer == null) {
                try {
                    Method getOwner = maidLE.getClass().getMethod("getOwner");
                    Object owner = getOwner.invoke(maidLE);
                    if (owner instanceof ServerPlayer) {
                        ownerPlayer = (ServerPlayer)owner;
                    }
                } catch (Exception e2) {
                    MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: reflection getOwner() failed: {}", e2.getMessage());
                }
            }
        }
        catch (Exception getOwner) {
            MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: getOwnerUUID failed: {}", getOwner.getMessage());
        }
        if (ownerPlayer != null) {
            deliverPlayer = ownerPlayer;
        } else {
            deliverPlayer = FakePlayerFactory.get((ServerLevel)level, (GameProfile)new GameProfile(UUID.randomUUID(), "MaidWaiter"));
            deliverPlayer.moveTo(maidLE.getX(), maidLE.getY(), maidLE.getZ(), maidLE.getYRot(), maidLE.getXRot());
        }
        InteractionResult result = TakeoutBagItem.trySubmitDineInFromEntityUse((ServerLevel)level, (Player)deliverPlayer, (ItemStack)plateStack.copy(), (Entity)customer);
        if (result.consumesAction()) {
            inv.extractItem(plateSlot, 1, false);
            // 好感度收益加成（调用公共方法，确保两个送餐系统都能触发）
            com.icewolf.maidrestaurant.business.core.DeliveryBridge.applyFavorabilityBonus(level, maidLE, plateStack, deliverPlayer);
            this.accept(level, maid, StepResult.SUCCESS);
        } else {
            MaidRestaurantBusiness.LOGGER.warn("DeliverOrderTask: delivery failed, result={}", result);
            this.accept(level, maid, StepResult.FAIL);
        }
    }

    private static BlockPos customerPos(LivingEntity customer) {
        return customer.blockPosition();
    }

    private static BlockPos findSafeDeliveryPos(ServerLevel level, BlockPos customerPos) {
        if (MaidDeliverOrderTask.isChairBlock(level, customerPos)) {
            for (int dx = -1; dx <= 1; ++dx) {
                for (int dz = -1; dz <= 1; ++dz) {
                    BlockPos checkPos;
                    if (dx == 0 && dz == 0 || MaidDeliverOrderTask.isChairBlock(level, checkPos = customerPos.offset(dx, 0, dz)) || !level.getBlockState(checkPos).isAir()) continue;
                    return checkPos;
                }
            }
        }
        return customerPos;
    }

    private static boolean isChairBlock(ServerLevel level, BlockPos pos) {
        try {
            BlockState state = level.getBlockState(pos);
            String blockName = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
            return blockName.equals("ordertocook:chair") || blockName.contains("chair");
        }
        catch (Exception e) {
            return false;
        }
    }

    private LivingEntity findCustomer(ServerLevel level, BlockPos counterPos) {
        return CustomerCompat.findCustomerById(level, counterPos, "", 16.0);
    }

    private LivingEntity findCustomerByOrderId(ServerLevel level, BlockPos counterPos, String orderId) {
        return CustomerCompat.findCustomerByOrderId(level, counterPos, orderId, 32.0);
    }

    private LivingEntity findCustomerById(ServerLevel level, BlockPos counterPos, String customerId) {
        return CustomerCompat.findCustomerById(level, counterPos, customerId, 32.0);
    }

}
