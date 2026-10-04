package com.icewolf.maidrestaurant.business.core;

import cn.breezeth.ordertocook.util.CoinUtils;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * 玩家退款待领台账：退款时买家不在线，则按买家 UUID 累计，买家下次登录时自动发放。
 * 绑定在主世界存储，随世界持久化。
 */
public class PlayerRefunds extends SavedData {
    private static final String NAME = "maid_restaurant_business_player_refunds";

    private final Map<UUID, Integer> pending = new HashMap<>();

    public static PlayerRefunds get(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        return overworld.getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(PlayerRefunds::new, PlayerRefunds::load, null), NAME);
    }

    /** 退款：买家在线直接发放并提示；不在线则累计待领。 */
    public void refund(MinecraftServer server, UUID buyer, String buyerName, int amount) {
        if (amount <= 0 || buyer == null) return;
        ServerPlayer online = server.getPlayerList().getPlayer(buyer);
        if (online != null) {
            CoinUtils.giveCoins(online, amount);
            online.displayClientMessage(
                    Component.translatable("message.business.order.refunded", amount)
                            .withStyle(ChatFormatting.GOLD),
                    false);
        } else {
            pending.merge(buyer, amount, Integer::sum);
            setDirty();
        }
    }

    /** 玩家登录：发放其待领退款。 */
    public void grantOnLogin(ServerPlayer player) {
        Integer v = pending.remove(player.getUUID());
        if (v != null && v > 0) {
            CoinUtils.giveCoins(player, v);
            player.displayClientMessage(
                    Component.translatable("message.business.order.refunded", v)
                            .withStyle(ChatFormatting.GOLD),
                    false);
            setDirty();
        }
    }

    // ===== 持久化 =====

    private static PlayerRefunds load(CompoundTag tag, HolderLookup.Provider provider) {
        PlayerRefunds data = new PlayerRefunds();
        CompoundTag p = tag.getCompound("pending");
        for (String k : p.getAllKeys()) {
            try {
                data.pending.put(UUID.fromString(k), p.getInt(k));
            } catch (IllegalArgumentException ignored) {}
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        CompoundTag p = new CompoundTag();
        for (Map.Entry<UUID, Integer> e : pending.entrySet()) {
            p.putInt(e.getKey().toString(), e.getValue());
        }
        tag.put("pending", p);
        return tag;
    }
}
