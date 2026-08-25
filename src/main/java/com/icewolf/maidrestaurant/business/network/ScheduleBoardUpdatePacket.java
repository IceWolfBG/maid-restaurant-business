package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.block.entity.ScheduleBoardBlockEntity;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.network.NetworkEvent;

public class ScheduleBoardUpdatePacket {
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

    private final BlockPos pos;
    private final int type;
    private final boolean boolValue;
    private final int intValue;

    public ScheduleBoardUpdatePacket(BlockPos pos, int type, boolean value) {
        this.pos = pos;
        this.type = type;
        this.boolValue = value;
        this.intValue = 0;
    }

    public ScheduleBoardUpdatePacket(BlockPos pos, int type, int value) {
        this.pos = pos;
        this.type = type;
        this.boolValue = false;
        this.intValue = value;
    }

    public static void encode(ScheduleBoardUpdatePacket msg, FriendlyByteBuf buf) {
        buf.writeBlockPos(msg.pos);
        buf.writeInt(msg.type);
        buf.writeBoolean(msg.boolValue);
        buf.writeInt(msg.intValue);
    }

    public static ScheduleBoardUpdatePacket decode(FriendlyByteBuf buf) {
        BlockPos pos = buf.readBlockPos();
        int type = buf.readInt();
        boolean boolValue = buf.readBoolean();
        int intValue = buf.readInt();
        if (type == TYPE_MIN_PLATES || type == TYPE_WORK_SCHEDULE) {
            return new ScheduleBoardUpdatePacket(pos, type, intValue);
        }
        return new ScheduleBoardUpdatePacket(pos, type, boolValue);
    }

    public static void handle(ScheduleBoardUpdatePacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            BlockEntity be = player.level().getBlockEntity(msg.pos);
            if (!(be instanceof ScheduleBoardBlockEntity)) return;
            ScheduleBoardBlockEntity board = (ScheduleBoardBlockEntity)be;
            switch (msg.type) {
                case TYPE_AUTO_ENABLED: board.setAutoEnabled(msg.boolValue); break;
                case TYPE_AUTO_DELIVERY: board.setAutoDelivery(msg.boolValue); break;
                case TYPE_AUTO_PACKAGING: board.setAutoPackaging(msg.boolValue); break;
                case TYPE_AUTO_COOKING: board.setAutoCooking(msg.boolValue); break;
                case TYPE_AUTO_PREP: board.setAutoPrep(msg.boolValue); break;
                case TYPE_AUTO_COLLECT: board.setAutoCollect(msg.boolValue); break;
                case TYPE_AUTO_WASH: board.setAutoWash(msg.boolValue); break;
                case TYPE_MIN_PLATES: board.setMinPlatesToWash(msg.intValue); break;
                case TYPE_WORK_SCHEDULE: board.setWorkSchedule(msg.intValue); break;
                case TYPE_BELL_ENABLED: board.setBellEnabled(msg.boolValue); break;
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
