package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;

/**
 * 排班表更新网络包
 */
public class ScheduleBoardUpdatePacket implements CustomPacketPayload {
    public static final Type<ScheduleBoardUpdatePacket> TYPE = new Type<>(ResourceLocation.tryParse("maid_restaurant_business:schedule_board_update"));

    public static final StreamCodec<RegistryFriendlyByteBuf, ScheduleBoardUpdatePacket> STREAM_CODEC = StreamCodec.of(
        (buf, msg) -> {
            // 防御性检查：pos为null时使用原点，避免NullPointerException导致玩家断开连接
            BlockPos posToWrite = msg.pos != null ? msg.pos : BlockPos.ZERO;
            buf.writeBlockPos(posToWrite);
            buf.writeInt(msg.type);
            buf.writeBoolean(msg.boolValue);
            buf.writeInt(msg.intValue);
        },
        buf -> new ScheduleBoardUpdatePacket(
            buf.readBlockPos(),
            buf.readInt(),
            buf.readBoolean(),
            buf.readInt()
        )
    );

    public static final int TYPE_AUTO_ENABLED = 0;
    public static final int TYPE_AUTO_DELIVERY = 1;
    public static final int TYPE_AUTO_PACKAGING = 2;
    public static final int TYPE_AUTO_COOKING = 3;
    public static final int TYPE_AUTO_PREP = 4;
    public static final int TYPE_AUTO_COLLECT = 5;
    public static final int TYPE_AUTO_WASH = 6;
    public static final int TYPE_MIN_PLATES = 7;
    public static final int TYPE_WORK_SCHEDULE = 8;
    public static final int TYPE_BELL_ENABLED = 9;
    public static final int TYPE_AUTO_ACCEPT = 10;

    private final BlockPos pos;
    private final int type;
    private final boolean boolValue;
    private final int intValue;

    public ScheduleBoardUpdatePacket(BlockPos pos, int type, boolean boolValue, int intValue) {
        this.pos = pos;
        this.type = type;
        this.boolValue = boolValue;
        this.intValue = intValue;
    }

    public ScheduleBoardUpdatePacket(BlockPos pos, int type, boolean value) {
        this(pos, type, value, 0);
    }

    public ScheduleBoardUpdatePacket(BlockPos pos, int type, int value) {
        this(pos, type, false, value);
    }

    public BlockPos getPos() { return pos; }
    public int getType() { return type; }
    public boolean getBoolValue() { return boolValue; }
    public int getIntValue() { return intValue; }

    public void handle(ServerPlayer player) {
        if (player == null || this.pos == null) return;
        BlockEntity be = player.level().getBlockEntity(this.pos);
        if (!(be instanceof ScheduleBoardBlockEntity)) return;
        ScheduleBoardBlockEntity board = (ScheduleBoardBlockEntity)be;
        switch (this.type) {
            case TYPE_AUTO_ENABLED: board.setAutoEnabled(this.boolValue); break;
            case TYPE_AUTO_DELIVERY: board.setAutoDelivery(this.boolValue); break;
            case TYPE_AUTO_PACKAGING: board.setAutoPackaging(this.boolValue); break;
            case TYPE_AUTO_COOKING: board.setAutoCooking(this.boolValue); break;
            case TYPE_AUTO_PREP: board.setAutoPrep(this.boolValue); break;
            case TYPE_AUTO_COLLECT: board.setAutoCollect(this.boolValue); break;
            case TYPE_AUTO_WASH: board.setAutoWash(this.boolValue); break;
            case TYPE_MIN_PLATES: board.setMinPlatesToWash(this.intValue); break;
            case TYPE_WORK_SCHEDULE: board.setWorkSchedule(this.intValue); break;
            case TYPE_BELL_ENABLED: board.setBellEnabled(this.boolValue); break;
            case TYPE_AUTO_ACCEPT: board.setAutoAccept(this.boolValue); break;
        }
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
