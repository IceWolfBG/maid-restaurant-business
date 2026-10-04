package com.icewolf.maidrestaurant.business.network;

import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

/** 客户端点单界面 → 服务端：携带菜单快照 NBT、所选菜品数量与时限/小费档位，服务端校验、扣款并生成订单。 */
public class PlayerOrderSubmitPacket implements CustomPacketPayload {
    public static final Type<PlayerOrderSubmitPacket> TYPE =
            new Type<>(ResourceLocation.tryParse("maid_restaurant_business:player_order_submit"));

    public static final StreamCodec<RegistryFriendlyByteBuf, PlayerOrderSubmitPacket> STREAM_CODEC =
            StreamCodec.of(
                    (buf, msg) -> {
                        buf.writeNbt(msg.menuTag);
                        buf.writeNbt(msg.selection);
                        buf.writeVarInt(msg.timeTier);
                        buf.writeVarInt(msg.tipTier);
                    },
                    buf -> new PlayerOrderSubmitPacket(buf.readNbt(), buf.readNbt(),
                            buf.readVarInt(), buf.readVarInt())
            );

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

    public void handle(ServerPlayer sender) {
        if (sender == null || this.menuTag == null || this.selection == null) return;
        ItemStack menu = new ItemStack(ModItems.RESTAURANT_MENU.get());
        com.icewolf.maidrestaurant.business.util.ItemStackUtils.setTag(menu, this.menuTag);
        LinkedHashMap<String, Integer> sel = new LinkedHashMap<>();
        for (String key : this.selection.getAllKeys()) {
            sel.put(key, this.selection.getInt(key));
        }
        PlayerOrderManager.submitOrder(sender.serverLevel(), sender, menu, sel, this.timeTier, this.tipTier);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
