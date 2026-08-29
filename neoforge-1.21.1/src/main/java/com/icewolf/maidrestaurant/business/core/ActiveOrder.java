/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.minecraft.core.BlockPos
 *  net.minecraft.nbt.CompoundTag
 *  net.minecraft.resources.ResourceLocation
 */
package com.icewolf.maidrestaurant.business.core;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;

public class ActiveOrder {
    public final BlockPos machinePos;
    public final BlockPos counterPos;
    public final ResourceLocation recipeId;
    public final CompoundTag orderNbt;
    public final Map<String, Integer> foodList;
    public final int prestige;
    public final boolean delivery;
    public long createdTick;

    public ActiveOrder(BlockPos machinePos, BlockPos counterPos, ResourceLocation recipeId, CompoundTag orderNbt, Map<String, Integer> foodList, int prestige, boolean delivery, long createdTick) {
        this.machinePos = machinePos;
        this.counterPos = counterPos;
        this.recipeId = recipeId;
        this.orderNbt = orderNbt;
        this.foodList = foodList;
        this.prestige = prestige;
        this.delivery = delivery;
        this.createdTick = createdTick;
    }
}
