package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidNodeEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.PathType;
import net.minecraft.world.level.pathfinder.PathfindingContext;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 注入车万女仆的MaidNodeEvaluator，添加我们自己的黑名单方块
 * 直接在代码层面检查，不依赖tag文件的加载顺序
 * 避免女仆站在otc的操作台、洗碗台、椅子、盘子架、架子上
 *
 * 注意：Minecraft 1.21.1中方法签名已变更：
 * - getMaidBlockPathTypeRaw(PathfindingContext, int, int, int) 返回 PathType
 * - BlockPathTypes 改名为 PathType
 */
@Mixin(MaidNodeEvaluator.class)
public class MaidNodeEvaluatorMixin {

    // 我们的黑名单方块集合（otc的相关方块）
    private static final Set<String> CUSTOM_BLACKLIST = Set.of(
            "ordertocook:countertop",
            "ordertocook:washingtable",
            "ordertocook:chair",
            "ordertocook:plate_shelf",
            "ordertocook:shelf"
    );

    /**
     * 在getMaidBlockPathTypeRaw()方法开始时注入
     * 检查方块是否在我们的自定义黑名单中
     * 如果在，返回DAMAGE_OTHER，与车万女仆原生黑名单的处理一致
     *
     * Minecraft 1.21.1方法签名：
     * private PathType getMaidBlockPathTypeRaw(PathfindingContext context, int x, int y, int z)
     */
    @Inject(method = "getMaidBlockPathTypeRaw", at = @At("HEAD"), cancellable = true, remap = false)
    private void business$checkCustomBlacklist(PathfindingContext context, int x, int y, int z, CallbackInfoReturnable<PathType> cir) {
        try {
            // 从PathfindingContext获取方块状态
            BlockPos pos = new BlockPos(x, y, z);
            BlockState blockState = context.getBlockState(pos);

            if (blockState == null) {
                return;
            }

            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());

            if (blockId != null && CUSTOM_BLACKLIST.contains(blockId.toString())) {
                // 在黑名单中，返回DAMAGE_OTHER
                // 与车万女仆原生黑名单(blockState.is(TagBlock.MAID_AVOID_BLOCK))的处理一致
                cir.setReturnValue(PathType.DAMAGE_OTHER);
            }
        } catch (Exception e) {
            // 检查过程中出现异常，让原方法处理（避免我们的代码导致新问题）
        }
    }
}
