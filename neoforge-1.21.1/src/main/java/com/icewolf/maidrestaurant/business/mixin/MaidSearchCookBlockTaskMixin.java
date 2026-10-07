package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.icewolf.maidrestaurant.business.util.CookingTargetRedirector;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.maid.task.cook.MaidSearchCookBlockTask;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * 修复两女仆撞同一炒锅（type0：已骑乘、锅在身边 2 格内快速开做阶段）。
 * 重定向现场 searchWorkBlock：营业中请求直接用自己 targets 的锅。
 */
@Mixin(MaidSearchCookBlockTask.class)
public class MaidSearchCookBlockTaskMixin {

    @Redirect(
            method = "checkExtraStartConditions",
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
