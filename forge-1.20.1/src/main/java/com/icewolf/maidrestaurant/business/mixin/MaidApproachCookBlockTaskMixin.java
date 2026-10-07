package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.util.CookingTargetRedirector;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidApproachCookBlockTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复两女仆撞同一炒锅（type1：非骑乘、走到厨具阶段）。
 * 本体 search() 无视 request.targets、现场 searchWorkBlock；走路窗口锅都未实际占用，
 * 两个女仆会选最近的同一口锅，后到者走到一半被挡、回退阶段1。
 * 重定向该次选锅：营业中请求直接用自己 targets 的锅，天然按锅错开。
 */
@Mixin(MaidApproachCookBlockTask.class)
public class MaidApproachCookBlockTaskMixin {

    @Redirect(
            method = "search",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/mastermarisa/maid_restaurant/api/ICookTask;searchWorkBlock(Lnet/minecraft/server/level/ServerLevel;Lcom/github/tartaricacid/touhoulittlemaid/entity/passive/EntityMaid;II)Lnet/minecraft/core/BlockPos;"
            ),
            remap = false
    )
    private BlockPos business$useAssignedTarget(ICookTask task, ServerLevel level, EntityMaid maid,
                                                int horizontalSearchRange, int verticalSearchRange) {
        BlockPos assigned = CookingTargetRedirector.resolveAssignedTarget(task, level, maid);
        return assigned != null ? assigned
                : task.searchWorkBlock(level, maid, horizontalSearchRange, verticalSearchRange);
    }
}
