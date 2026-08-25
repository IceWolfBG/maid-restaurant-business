/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid
 *  com.google.common.collect.Lists
 *  com.mastermarisa.maid_restaurant.maid.task.TaskWaiter
 *  com.mojang.datafixers.util.Pair
 *  net.minecraft.world.entity.ai.behavior.BehaviorControl
 *  org.spongepowered.asm.mixin.Mixin
 *  org.spongepowered.asm.mixin.injection.At
 *  org.spongepowered.asm.mixin.injection.Inject
 *  org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable
 */
package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.google.common.collect.Lists;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.maid.MaidDeliverOrderTask;
import com.mastermarisa.maid_restaurant.maid.task.TaskWaiter;
import com.mojang.datafixers.util.Pair;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.ai.behavior.BehaviorControl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value={TaskWaiter.class})
public class TaskWaiterMixin {
    static {
        MaidRestaurantBusiness.LOGGER.info("TaskWaiterMixin: class loaded");
    }

    @Inject(method={"createBrainTasks(Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;)Ljava/util/List;"}, at={@At(value="RETURN")}, cancellable=true, remap=false)
    private void addDeliverOrderTask(EntityMaid maid, CallbackInfoReturnable<List<Pair<Integer, BehaviorControl<? super EntityMaid>>>> cir) {
        MaidRestaurantBusiness.LOGGER.info("TaskWaiterMixin: adding MaidDeliverOrderTask to brain tasks for maid {}", maid.getName().getString());
        ArrayList tasks = Lists.newArrayList((Iterable)((Iterable)cir.getReturnValue()));
        MaidDeliverOrderTask deliverTask = new MaidDeliverOrderTask(0.4f, 2.0);
        tasks.add(Pair.of((Object)3, (Object)(deliverTask)));
        cir.setReturnValue(tasks);
    }
}
