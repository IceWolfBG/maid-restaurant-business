package com.icewolf.maidrestaurant.business.core;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 店铺待领收益：店主离线（或店铺尚无店主）期间，玩家下单归店的金币按「维度#打单机坐标」累计。
 * 店主下次右键对应打单机时自动发放并清除。数据随世界持久化保存。
 */
public class ShopEarnings extends SavedData {
    private static final String NAME = "maid_restaurant_business_shop_earnings";

    private final Map<String, Integer> pending = new HashMap<>();

    public static ShopEarnings get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(ShopEarnings::new, ShopEarnings::load, null), NAME);
    }

    public static String key(String dim, long machinePos) {
        return dim + "#" + machinePos;
    }

    public void add(String key, int amount) {
        if (amount <= 0) return;
        pending.merge(key, amount, Integer::sum);
        setDirty();
    }

    /** 取出并清除指定店铺的待领金额；没有则返回 0。 */
    public int take(String key) {
        Integer v = pending.remove(key);
        if (v != null) setDirty();
        return v == null ? 0 : v;
    }

    public int peek(String key) {
        return pending.getOrDefault(key, 0);
    }

    private static ShopEarnings load(CompoundTag tag, HolderLookup.Provider provider) {
        ShopEarnings data = new ShopEarnings();
        CompoundTag p = tag.getCompound("pending");
        for (String k : p.getAllKeys()) {
            data.pending.put(k, p.getInt(k));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag p = new CompoundTag();
        for (Map.Entry<String, Integer> e : pending.entrySet()) {
            p.putInt(e.getKey(), e.getValue());
        }
        tag.put("pending", p);
        return tag;
    }
}
