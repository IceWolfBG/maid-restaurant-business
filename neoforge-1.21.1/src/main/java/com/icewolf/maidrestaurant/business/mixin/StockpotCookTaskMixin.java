package com.icewolf.maidrestaurant.business.mixin;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.github.ysbbbbbb.kaleidoscopecookery.api.blockentity.IStockpot;
import com.mastermarisa.maid_restaurant.cooktask.StockpotCookTask;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

@Mixin(StockpotCookTask.class)
public class StockpotCookTaskMixin {
    /**
     * 修复空锅误报汤底：
     * 汤锅初始状态 / 重置后 status=PUT_SOUP_BASE，且 soupBaseId 默认就是 WATER。
     * 原 getCurrentInput 仅凭 getSoupBase()!=null，就把“默认选中的汤底类型”
     * 误判成“已经加入的汤底”，于是女仆一滴 water 都没取，cookState 也判 COOK，
     * 直接走到锅前烹饪。
     * 实际上 PUT_SOUP_BASE 状态下锅内没有任何东西（食材只能在 PUT_INGREDIENT 后放入），
     * 因此该状态直接返回空列表，让 cookState 保持 STORAGE、女仆先去取水。
     * 注意：两桶水的“背包内无限水”是 filterByCountStockpot / tickState0 的独立机制，
     * 此处不改动。
     */
    @Inject(method = "getCurrentInput", at = @At("HEAD"), cancellable = true, remap = false)
    private void business$fixEmptyPotSoupBase(Level level, BlockPos pos, EntityMaid maid,
                                               CallbackInfoReturnable<List<ItemStack>> cir) {
        if (level.getBlockEntity(pos) instanceof IStockpot stockpot
                && stockpot.getStatus() == IStockpot.PUT_SOUP_BASE) {
            cir.setReturnValue(new ArrayList<>());
        }
    }
}
