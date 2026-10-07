package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.ai.navigation.MaidNodeEvaluator;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 注入车万女仆的MaidNodeEvaluator，添加我们自己的黑名单方块。
 * Forge 1.20.1 与 NeoForge 1.21.1 统一为 Mixin 代码注入，不再依赖数据包 tag 文件。
 * 避免女仆站在 OTC 的操作台、洗碗台、椅子、盘子架、架子上。
 *
 * 注意：Forge 1.20.1 中方法签名为
 * private BlockPathTypes getMaidBlockPathTypeRaw(BlockGetter level, BlockPos pos)
 * （1.21.1 才改为 PathfindingContext + x/y/z，BlockPathTypes 改名 PathType）。
 */
@Mixin(MaidNodeEvaluator.class)
public class MaidNodeEvaluatorMixin {

    // 我们的黑名单方块集合（OTC 的相关方块）
    private static final Set<String> CUSTOM_BLACKLIST = Set.of(
            "ordertocook:countertop",
            "ordertocook:washingtable",
            "ordertocook:chair",
            "ordertocook:plate_shelf",
            "ordertocook:shelf"
    );

    /**
     * 在getMaidBlockPathTypeRaw()方法开始时注入。
     * 方块在自定义黑名单中则返回DAMAGE_OTHER，与车万女仆原生黑名单处理一致。
     */
    @Inject(method = "getMaidBlockPathTypeRaw", at = @At("HEAD"), cancellable = true, remap = false)
    private void business$checkCustomBlacklist(BlockGetter level, BlockPos pos, CallbackInfoReturnable<BlockPathTypes> cir) {
        try {
            BlockState blockState = level.getBlockState(pos);
            if (blockState == null) {
                return;
            }
            ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(blockState.getBlock());
            if (blockId != null && CUSTOM_BLACKLIST.contains(blockId.toString())) {
                cir.setReturnValue(BlockPathTypes.DAMAGE_OTHER);
            }
        } catch (Exception e) {
            // 检查异常时让原方法处理，避免引入新问题
        }
    }
}
