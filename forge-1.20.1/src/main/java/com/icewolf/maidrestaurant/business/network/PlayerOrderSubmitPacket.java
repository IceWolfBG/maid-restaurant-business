package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Supplier;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.network.NetworkEvent;

/** 客户端点单界面 → 服务端：携带菜单快照 NBT、所选菜品数量与时限/小费档位，服务端校验、扣款并生成订单。 */
public class PlayerOrderSubmitPacket {
    private final CompoundTag menuTag;
    private final CompoundTag selection;
    private final int timeTier;
    private final int tipTier;

    public PlayerOrderSubmitPacket(CompoundTag menuTag, Map<String, Integer> selection, int timeTier, int tipTier) {
        this.menuTag = menuTag;
        this.timeTier = PlayerOrderManager.clampTimeTier(timeTier);
        this.tipTier = PlayerOrderManager.clampTipTier(tipTier);
        this.selection = new CompoundTag();
        for (Map.Entry<String, Integer> e : selection.entrySet()) {
            this.selection.putInt(e.getKey(), e.getValue());
        }
    }

    private PlayerOrderSubmitPacket(CompoundTag menuTag, CompoundTag selection, int timeTier, int tipTier) {
        this.menuTag = menuTag;
        this.selection = selection;
        this.timeTier = timeTier;
        this.tipTier = tipTier;
    }

    public static void encode(PlayerOrderSubmitPacket msg, FriendlyByteBuf buf) {
        buf.writeNbt(msg.menuTag);
        buf.writeNbt(msg.selection);
        buf.writeVarInt(msg.timeTier);
        buf.writeVarInt(msg.tipTier);
    }

    public static PlayerOrderSubmitPacket decode(FriendlyByteBuf buf) {
        CompoundTag menuTag = buf.readNbt();
        CompoundTag selection = buf.readNbt();
        int timeTier = buf.readVarInt();
        int tipTier = buf.readVarInt();
        return new PlayerOrderSubmitPacket(menuTag, selection, timeTier, tipTier);
    }

    public static void handle(PlayerOrderSubmitPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender == null || msg.menuTag == null || msg.selection == null) return;
            ItemStack menu = new ItemStack(ModItems.RESTAURANT_MENU.get());
            menu.setTag(msg.menuTag);
            LinkedHashMap<String, Integer> sel = new LinkedHashMap<>();
            for (String key : msg.selection.getAllKeys()) {
                sel.put(key, msg.selection.getInt(key));
            }
            ServerLevel level = sender.serverLevel();
            PlayerOrderManager.submitOrder(level, sender, menu, sel, msg.timeTier, msg.tipTier);
        });
        ctx.get().setPacketHandled(true);
    }
}
