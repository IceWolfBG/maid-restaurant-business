package com.icewolf.maidrestaurant.business.block.entity;

import com.icewolf.maidrestaurant.business.menu.ScheduleBoardMenu;
import com.icewolf.maidrestaurant.business.registry.ModBlockEntities;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.state.BlockState;

public class ScheduleBoardBlockEntity extends BlockEntity {
    public static final String TAG_MACHINE_X = "BoundMachineX";
    public static final String TAG_MACHINE_Y = "BoundMachineY";
    public static final String TAG_MACHINE_Z = "BoundMachineZ";
    public static final String TAG_HAS_MACHINE = "HasBoundMachine";
    
    // 自动化配置
    public static final String TAG_AUTO_ENABLED = "AutoEnabled";
    public static final String TAG_AUTO_DELIVERY = "AutoDelivery";
    public static final String TAG_AUTO_PACKAGING = "AutoPackaging";
    public static final String TAG_AUTO_COOKING = "AutoCooking";
    public static final String TAG_AUTO_PREP = "AutoPrep";
    public static final String TAG_AUTO_COLLECT = "AutoCollect";
    public static final String TAG_AUTO_WASH = "AutoWash";
    public static final String TAG_MIN_PLATES_TO_WASH = "MinPlatesToWash";
    public static final String TAG_WORK_SCHEDULE = "WorkSchedule"; // 0=白天, 1=黑天, 2=全天, 3=歇业
    public static final String TAG_BELL_ENABLED = "BellEnabled";
    
    // 营业时间常量
    public static final int SCHEDULE_DAY = 0;
    public static final int SCHEDULE_NIGHT = 1;
    public static final int SCHEDULE_ALL = 2;
    public static final int SCHEDULE_CLOSED = 3;
    
    // 时间节点（Minecraft dayTime）
    public static final int DAY_START = 1000;    // 日出
    public static final int DAY_END = 12000;     // 日落（白天休息时间）
    public static final int NIGHT_END = 23000;   // 日出前（黑天休息时间）
    
    private boolean hasBoundMachine = false;
    @Nullable
    private BlockPos boundMachinePos = null;
    
    // 自动化开关配置（默认全部开启）
    private boolean autoEnabled = true;
    private boolean autoDelivery = true;
    private boolean autoPackaging = true;
    private boolean autoCooking = true;
    private boolean autoPrep = true;
    private boolean autoCollect = true;
    private boolean autoWash = true;
    private int minPlatesToWash = 3;
    
    // 营业时间配置（默认全天）
    private int workSchedule = SCHEDULE_ALL;
    private boolean bellEnabled = true;
    
    // tick相关
    private long lastCheckTick = 0;
    private long lastBindAttemptTick = 0; // 上一次尝试绑定打单机的时间，避免频繁遍历
    private int lastDayPhase = -1; // 上一次的时间段：0=白天, 1=黑夜, -1=未初始化
    private boolean lastMachineActive = true; // 上一次设置的打单机状态，避免重复设置
    
    // 反射缓存
    private static java.lang.reflect.Field activeField = null;
    private static boolean reflectionInitialized = false;

    public ScheduleBoardBlockEntity(BlockPos pos, BlockState state) {
        super((BlockEntityType)ModBlockEntities.SCHEDULE_BOARD.get(), pos, state);
    }

    public boolean hasBoundMachine() {
        return this.hasBoundMachine;
    }

    @Nullable
    public BlockPos getBoundMachinePos() {
        return this.boundMachinePos;
    }

    public void bindMachine(BlockPos machinePos) {
        this.boundMachinePos = machinePos;
        this.hasBoundMachine = true;
        this.setChanged();
    }

    public boolean tryBindNearestMachine(Level level) {
        if (this.hasBoundMachine) return true;
        BlockPos nearest = null;
        double nearestDist = Double.MAX_VALUE;
        for (BlockPos pos : BlockPos.betweenClosed(this.worldPosition.offset(-16, -8, -16), this.worldPosition.offset(16, 8, 16))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be != null && be.getClass().getName().contains("OrderMachineBlockEntity")) {
                // 检查该打单机是否已经被其他排班表绑定
                if (isMachineBoundByOtherBoard(level, pos.immutable())) {
                    continue;
                }
                double dist = pos.distSqr(this.worldPosition);
                if (dist < nearestDist) {
                    nearestDist = dist;
                    nearest = pos.immutable();
                }
            }
        }
        if (nearest != null) {
            this.bindMachine(nearest);
            return true;
        }
        return false;
    }
    
    // 检查打单机是否已经被其他排班表绑定
    private boolean isMachineBoundByOtherBoard(Level level, BlockPos machinePos) {
        for (BlockPos pos : BlockPos.betweenClosed(machinePos.offset(-16, -8, -16), machinePos.offset(16, 8, 16))) {
            BlockEntity be = level.getBlockEntity(pos);
            if (be instanceof ScheduleBoardBlockEntity otherBoard && otherBoard != this) {
                if (otherBoard.hasBoundMachine() && otherBoard.getBoundMachinePos() != null 
                    && otherBoard.getBoundMachinePos().equals(machinePos)) {
                    return true;
                }
            }
        }
        return false;
    }

    // Getters and setters for config
    public boolean isAutoEnabled() { return autoEnabled; }
    public void setAutoEnabled(boolean v) { this.autoEnabled = v; syncToClient(); }
    
    public boolean isAutoDelivery() { return autoDelivery; }
    public void setAutoDelivery(boolean v) { this.autoDelivery = v; syncToClient(); }
    
    public boolean isAutoPackaging() { return autoPackaging; }
    public void setAutoPackaging(boolean v) { this.autoPackaging = v; syncToClient(); }
    
    public boolean isAutoCooking() { return autoCooking; }
    public void setAutoCooking(boolean v) { this.autoCooking = v; syncToClient(); }
    
    public boolean isAutoPrep() { return autoPrep; }
    public void setAutoPrep(boolean v) { this.autoPrep = v; syncToClient(); }
    
    public boolean isAutoCollect() { return autoCollect; }
    public void setAutoCollect(boolean v) { this.autoCollect = v; syncToClient(); }
    
    public boolean isAutoWash() { return autoWash; }
    public void setAutoWash(boolean v) { this.autoWash = v; syncToClient(); }
    
    public int getMinPlatesToWash() { return minPlatesToWash; }
    public void setMinPlatesToWash(int v) { this.minPlatesToWash = Math.max(1, Math.min(10, v)); syncToClient(); }
    
    public int getWorkSchedule() { return workSchedule; }
    public void setWorkSchedule(int v) { 
        this.workSchedule = Math.max(0, Math.min(3, v)); 
        syncToClient();
        // 切换模式时立即检查并调整营业状态
        if (this.level != null && !this.level.isClientSide) {
            checkAndUpdateMachineState(true);
        }
    }
    public void cycleWorkSchedule() { 
        this.workSchedule = (this.workSchedule + 1) % 4; 
        syncToClient();
        if (this.level != null && !this.level.isClientSide) {
            checkAndUpdateMachineState(true);
        }
    }
    
    public boolean isBellEnabled() { return bellEnabled; }
    public void setBellEnabled(boolean v) { this.bellEnabled = v; syncToClient(); }
    
    // 获取营业时间显示名称
    public String getScheduleName() {
        return switch (this.workSchedule) {
            case SCHEDULE_DAY -> "白天";
            case SCHEDULE_NIGHT -> "黑天";
            case SCHEDULE_ALL -> "全天";
            case SCHEDULE_CLOSED -> "歇业";
            default -> "全天";
        };
    }
    
    // 检查当前是否在工作时间内
    public boolean isInWorkTime(int dayTime) {
        int time = dayTime % 24000;
        boolean isDay = time >= DAY_START && time < DAY_END;
        return switch (this.workSchedule) {
            case SCHEDULE_DAY -> isDay;
            case SCHEDULE_NIGHT -> !isDay;
            case SCHEDULE_ALL -> true;
            case SCHEDULE_CLOSED -> false;
            default -> true;
        };
    }
    
    // 获取当前时间段：0=白天, 1=黑夜
    private int getDayPhase(int dayTime) {
        int time = dayTime % 24000;
        return (time >= DAY_START && time < DAY_END) ? 0 : 1;
    }
    
    // 安全设置打单机状态，额外包裹异常保护，避免任何异常扩散
    private void safeSetMachineActive(boolean active) {
        try {
            if (active == lastMachineActive) return; // 避免重复设置
            setMachineActive(active);
            lastMachineActive = active;
        } catch (Exception e) {
            // 静默失败，确保不会影响其他功能
        }
    }
    
    // 检查并更新打单机营业状态（全新时间段比较方案）
    // 全天模式：切换时设置为营业，平时不干预
    // 歇业模式：切换时设置为停业，平时不干预
    // 白天/黑天模式：只在时间段切换时（日出/日落）设置一次
    private void checkAndUpdateMachineState(boolean force) {
        if (this.level == null || this.level.isClientSide) return;
        if (!this.hasBoundMachine || this.boundMachinePos == null) return;
        
        // 全天模式：切换时设置为营业，平时不干预
        if (this.workSchedule == SCHEDULE_ALL) {
            if (force) {
                safeSetMachineActive(true);
            }
            lastDayPhase = -1; // 重置时间段缓存
            return;
        }
        
        // 歇业模式：切换时设置为停业，平时不干预
        if (this.workSchedule == SCHEDULE_CLOSED) {
            if (force) {
                safeSetMachineActive(false);
            }
            lastDayPhase = -1;
            return;
        }
        
        // 白天/黑天模式：使用时间段比较检测时间节点
        int dayTime = (int)(this.level.getDayTime() % 24000);
        int currentPhase = getDayPhase(dayTime);
        
        if (force) {
            // 切换模式时立即设置一次
            boolean inWorkTime = isInWorkTime(dayTime);
            safeSetMachineActive(inWorkTime);
        } else if (lastDayPhase >= 0 && lastDayPhase != currentPhase) {
            // 时间段发生变化（跨过了日出或日落），才设置打单机状态
            boolean inWorkTime = isInWorkTime(dayTime);
            safeSetMachineActive(inWorkTime);
            // 如果刚到休息时间（不在工作时间），响铃
            if (!inWorkTime) {
                playBellSound();
            }
        }
        
        lastDayPhase = currentPhase;
    }
    
    // 初始化反射
    private void initReflection() {
        if (reflectionInitialized) return;
        try {
            Class<?> machineClass = Class.forName("cn.breezeth.ordertocook.block.entity.OrderMachineBlockEntity");
            activeField = machineClass.getDeclaredField("active");
            activeField.setAccessible(true);
            reflectionInitialized = true;
        } catch (Exception e) {
            reflectionInitialized = true; // 标记为已尝试，避免重复尝试
        }
    }
    
    // 设置打单机营业状态
    private void setMachineActive(boolean active) {
        if (!this.hasBoundMachine || this.boundMachinePos == null) return;
        if (this.level == null || this.level.isClientSide) return;
        
        BlockEntity be = this.level.getBlockEntity(this.boundMachinePos);
        if (be == null) return;
        
        initReflection();
        if (activeField == null) return;
        
        try {
            boolean current = activeField.getBoolean(be);
            if (current != active) {
                activeField.setBoolean(be, active);
                be.setChanged();
                // 同步到客户端
                if (be instanceof net.minecraft.world.level.block.entity.BlockEntity) {
                    this.level.sendBlockUpdated(this.boundMachinePos, be.getBlockState(), be.getBlockState(), 3);
                }
            }
        } catch (Exception e) {
            // 静默失败
        }
    }
    
    // 播放下班铃声（玩家升级声，清脆有上升感）
    private void playBellSound() {
        if (!this.bellEnabled) return;
        if (this.level == null || this.level.isClientSide) return;
        this.level.playSound(null, this.worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 0.6f, 1.0f);
    }
    
    // tick方法
    public static <T extends BlockEntity> void tick(Level level, BlockPos pos, BlockState state, T blockEntity) {
        if (!(blockEntity instanceof ScheduleBoardBlockEntity board)) return;
        if (level.isClientSide) return;
        
        try {
            long gameTime = level.getGameTime();
            // 每100tick（5秒）检查一次，日出/日落检测不需要秒级精度
            if (gameTime - board.lastCheckTick < 100) return;
            board.lastCheckTick = gameTime;
            
            // 全天模式或未绑定打单机时，完全不干预，直接返回
            if (board.workSchedule == SCHEDULE_ALL || !board.hasBoundMachine) {
                return;
            }
            
            // 检查并更新营业状态
            board.checkAndUpdateMachineState(false);
        } catch (Exception e) {
            // 最外层保护：任何异常都不扩散，避免影响女仆AI或其他模组
        }
    }

    // 同步配置到客户端
    private void syncToClient() {
        this.setChanged();
        if (this.level != null && !this.level.isClientSide) {
            this.level.sendBlockUpdated(this.worldPosition, this.getBlockState(), this.getBlockState(), 3);
        }
    }

    public MenuProvider getMenuProvider() {
        return new MenuProvider() {
            public Component getDisplayName() {
                return Component.literal("排班表");
            }

            @Nullable
            public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
                return new ScheduleBoardMenu(id, inventory, ScheduleBoardBlockEntity.this);
            }
        };
    }

    public void load(CompoundTag tag) {
        super.load(tag);
        this.hasBoundMachine = tag.getBoolean(TAG_HAS_MACHINE);
        if (this.hasBoundMachine) {
            this.boundMachinePos = new BlockPos(tag.getInt(TAG_MACHINE_X), tag.getInt(TAG_MACHINE_Y), tag.getInt(TAG_MACHINE_Z));
        }
        this.autoEnabled = tag.contains(TAG_AUTO_ENABLED) ? tag.getBoolean(TAG_AUTO_ENABLED) : true;
        this.autoDelivery = tag.contains(TAG_AUTO_DELIVERY) ? tag.getBoolean(TAG_AUTO_DELIVERY) : true;
        this.autoPackaging = tag.contains(TAG_AUTO_PACKAGING) ? tag.getBoolean(TAG_AUTO_PACKAGING) : true;
        this.autoCooking = tag.contains(TAG_AUTO_COOKING) ? tag.getBoolean(TAG_AUTO_COOKING) : true;
        this.autoPrep = tag.contains(TAG_AUTO_PREP) ? tag.getBoolean(TAG_AUTO_PREP) : true;
        this.autoCollect = tag.contains(TAG_AUTO_COLLECT) ? tag.getBoolean(TAG_AUTO_COLLECT) : true;
        this.autoWash = tag.contains(TAG_AUTO_WASH) ? tag.getBoolean(TAG_AUTO_WASH) : true;
        this.minPlatesToWash = tag.contains(TAG_MIN_PLATES_TO_WASH) ? tag.getInt(TAG_MIN_PLATES_TO_WASH) : 3;
        if (this.minPlatesToWash < 1) this.minPlatesToWash = 3;
        this.workSchedule = tag.contains(TAG_WORK_SCHEDULE) ? tag.getInt(TAG_WORK_SCHEDULE) : SCHEDULE_ALL;
        this.bellEnabled = tag.contains(TAG_BELL_ENABLED) ? tag.getBoolean(TAG_BELL_ENABLED) : true;
    }

    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putBoolean(TAG_HAS_MACHINE, this.hasBoundMachine);
        if (this.hasBoundMachine && this.boundMachinePos != null) {
            tag.putInt(TAG_MACHINE_X, this.boundMachinePos.getX());
            tag.putInt(TAG_MACHINE_Y, this.boundMachinePos.getY());
            tag.putInt(TAG_MACHINE_Z, this.boundMachinePos.getZ());
        }
        tag.putBoolean(TAG_AUTO_ENABLED, this.autoEnabled);
        tag.putBoolean(TAG_AUTO_DELIVERY, this.autoDelivery);
        tag.putBoolean(TAG_AUTO_PACKAGING, this.autoPackaging);
        tag.putBoolean(TAG_AUTO_COOKING, this.autoCooking);
        tag.putBoolean(TAG_AUTO_PREP, this.autoPrep);
        tag.putBoolean(TAG_AUTO_COLLECT, this.autoCollect);
        tag.putBoolean(TAG_AUTO_WASH, this.autoWash);
        tag.putInt(TAG_MIN_PLATES_TO_WASH, this.minPlatesToWash);
        tag.putInt(TAG_WORK_SCHEDULE, this.workSchedule);
        tag.putBoolean(TAG_BELL_ENABLED, this.bellEnabled);
    }

    // ========== 客户端同步方法 ==========
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        this.saveAdditional(tag);
        return tag;
    }
}
