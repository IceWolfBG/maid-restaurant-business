package com.icewolf.maidrestaurant.business.core;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 玩家订单资金托管台账（仅玩家之间通过「饭店菜单」下的订单）。
 *
 * 下单扣款后先冻结于此、不立即给店；买家拆包确认收货时才全额结算给店铺。
 * 未打包超时全额退款；已打包超时或送达失败，退一半给买家、另一半归店，包裹随之失效。
 *
 * 绑定在主世界存储、按 orderId 索引，全局唯一且结算幂等：
 * 即使订单/包裹卡在女仆背包、方块、掉落物中，或被销毁、复制，到期退款与收款都只发生一次。
 */
public class PlayerOrderEscrow extends SavedData {
    private static final String NAME = "maid_restaurant_business_order_escrow";

    public static final int STATE_ORDER = 0;   // 订单期，尚未打包
    public static final int STATE_PACKED = 1;  // 已打包，待送达 / 拆包
    public static final int STATE_SETTLED = 2; // 已结算（保留态，扫描时清除）

    private final Map<String, Entry> entries = new HashMap<>();

    public static PlayerOrderEscrow get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage()
                .computeIfAbsent(PlayerOrderEscrow::load, PlayerOrderEscrow::new, NAME);
    }

    /** 下单：登记一笔冻结资金。 */
    public void createOrder(String orderId, int amount, int tipAmount, UUID buyerUuid, String buyerName,
                            long machinePos, String machineDim, long expiryTick) {
        if (orderId == null || orderId.isEmpty() || amount <= 0) return;
        entries.put(orderId, new Entry(amount, tipAmount, buyerUuid, buyerName, machinePos, machineDim,
                STATE_ORDER, expiryTick));
        setDirty();
    }

    /** 打包完成：订单期 -> 已打包。 */
    public void markPacked(String orderId) {
        Entry e = entries.get(orderId);
        if (e != null && e.state == STATE_ORDER) {
            e.state = STATE_PACKED;
            setDirty();
        }
    }

    /** 是否允许拆包：记录存在、已打包、未结算、未过期。 */
    public boolean canUnpack(String orderId, long nowGameTime) {
        Entry e = entries.get(orderId);
        return e != null && e.state == STATE_PACKED && nowGameTime < e.expiryTick;
    }

    /** 拆包确认收货：移除并返回条目（用于全额给店）；已结算 / 不存在返回 null。 */
    public Entry settle(String orderId) {
        Entry e = entries.remove(orderId);
        if (e != null) setDirty();
        return e;
    }

    /** 送达失败（非超时，如送餐点被占 / 容器满 / 找不到送餐点）：未打包全退，已打包半退。 */
    public void reportDeliveryFailure(MinecraftServer server, String orderId) {
        Entry e = entries.get(orderId);
        if (e == null || e.state == STATE_SETTLED) return;
        if (e.state == STATE_ORDER) {
            refundFull(server, e);
        } else {
            refundHalf(server, e);
        }
        entries.remove(orderId);
        setDirty();
    }

    /** 周期扫描（主世界）：处理超时记录。 */
    public void tick(MinecraftServer server, ServerLevel overworld) {
        long now = overworld.getGameTime();
        Iterator<Map.Entry<String, Entry>> it = entries.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<String, Entry> kv = it.next();
            Entry e = kv.getValue();
            if (e.state == STATE_SETTLED) {
                it.remove();
                continue;
            }
            if (now < e.expiryTick) continue;
            if (e.state == STATE_ORDER) {
                refundFull(server, e);
            } else {
                refundHalf(server, e);
            }
            it.remove();
        }
    }

    private void refundFull(MinecraftServer server, Entry e) {
        PlayerRefunds.get(server).refund(server, e.buyerUuid, e.buyerName, e.amount);
    }

    private void refundHalf(MinecraftServer server, Entry e) {
        int half = e.amount / 2;            // 向下取整退买家
        int shopPart = e.amount - half;     // 向上取整归店
        if (half > 0) {
            PlayerRefunds.get(server).refund(server, e.buyerUuid, e.buyerName, half);
        }
        if (shopPart > 0) {
            ServerLevel shopLevel = levelByDim(server, e.machineDim);
            if (shopLevel == null) shopLevel = server.overworld();
            PlayerOrderManager.creditShop(shopLevel, BlockPos.of(e.machinePos), shopPart, 0);
        }
    }

    private static ServerLevel levelByDim(MinecraftServer server, String dim) {
        if (dim == null) return null;
        for (ServerLevel lvl : server.getAllLevels()) {
            if (lvl.dimension().location().toString().equals(dim)) return lvl;
        }
        return null;
    }

    // ===== 持久化 =====

    private static PlayerOrderEscrow load(CompoundTag tag) {
        PlayerOrderEscrow data = new PlayerOrderEscrow();
        CompoundTag all = tag.getCompound("entries");
        for (String orderId : all.getAllKeys()) {
            CompoundTag e = all.getCompound(orderId);
            data.entries.put(orderId, new Entry(
                    e.getInt("amount"),
                    e.getInt("tip"),
                    e.hasUUID("buyer") ? e.getUUID("buyer") : new UUID(0L, 0L),
                    e.getString("buyerName"),
                    e.getLong("machinePos"),
                    e.getString("machineDim"),
                    e.getInt("state"),
                    e.getLong("expiry")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag all = new CompoundTag();
        for (Map.Entry<String, Entry> kv : entries.entrySet()) {
            Entry e = kv.getValue();
            CompoundTag ec = new CompoundTag();
            ec.putInt("amount", e.amount);
            ec.putInt("tip", e.tipAmount);
            ec.putUUID("buyer", e.buyerUuid);
            ec.putString("buyerName", e.buyerName);
            ec.putLong("machinePos", e.machinePos);
            ec.putString("machineDim", e.machineDim);
            ec.putInt("state", e.state);
            ec.putLong("expiry", e.expiryTick);
            all.put(kv.getKey(), ec);
        }
        tag.put("entries", all);
        return tag;
    }

    /** 一条托管记录。 */
    public static final class Entry {
        public final int amount;
        public final int tipAmount;
        public final UUID buyerUuid;
        public final String buyerName;
        public final long machinePos;
        public final String machineDim;
        public int state;
        public final long expiryTick;

        Entry(int amount, int tipAmount, UUID buyerUuid, String buyerName, long machinePos, String machineDim,
              int state, long expiryTick) {
            this.amount = amount;
            this.tipAmount = tipAmount;
            this.buyerUuid = buyerUuid;
            this.buyerName = buyerName;
            this.machinePos = machinePos;
            this.machineDim = machineDim;
            this.state = state;
            this.expiryTick = expiryTick;
        }
    }
}
