package com.icewolf.maidrestaurant.business.util;

import com.github.tartaricacid.touhoulittlemaid.entity.passive.EntityMaid;
import com.mastermarisa.maid_restaurant.api.ICookTask;
import com.mastermarisa.maid_restaurant.request.CookRequest;
import com.mastermarisa.maid_restaurant.utils.BlockUsageManager;
import com.mastermarisa.maid_restaurant.utils.EncodeUtils;
import com.mastermarisa.maid_restaurant.utils.RequestManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/**
 * 烹饪目标重定向：营业中发布的烹饪请求，让女仆直接使用发布时已按打单机错开分配的
 * request.targets 锅，而不是本体现场重新 searchWorkBlock。
 *
 * 本体 type0/type1 找锅时无视 request.targets，而走路窗口锅都未实际 addUser 占用，
 * 两个女仆会各自选“最近的同一口锅”而撞车、后到者回退。
 *
 * 仅对营业中请求（extraData 含 BusinessCounter）生效；targets 失效则返回 null，
 * 由 Mixin 回退本体现场搜索，再失败走自愈 / ASSIGNED 硬超时重分配。
 */
public final class CookingTargetRedirector {

    public static final String BUSINESS_COUNTER_KEY = "BusinessCounter";

    private CookingTargetRedirector() {}

    /**
     * 取营业中请求已分配的锅；非营业中请求或分配锅已失效时返回 null。
     */
    public static BlockPos resolveAssignedTarget(ICookTask task, ServerLevel level, EntityMaid maid) {
        try {
            CookRequest request = (CookRequest) RequestManager.peek(maid, CookRequest.TYPE);
            if (request == null || request.extraData == null
                    || !request.extraData.contains(BUSINESS_COUNTER_KEY)
                    || request.targets == null || request.targets.length == 0) {
                return null;
            }
            BlockPos assigned = EncodeUtils.decode(request.targets[0]);
            if (assigned == null) {
                return null;
            }
            // 锅仍存在且满足该厨具的工作条件（BE 正确 / 有热源等）
            if (!safeIsValidWorkBlock(task, level, maid, assigned)) {
                return null;
            }
            // 未被别人实际占用：无人可用；只有女仆自己（预占）时也仍对她可用
            int count = BlockUsageManager.getUserCount(assigned);
            if (count > 0 && !(BlockUsageManager.isUsing(assigned, maid.getUUID()) && count == 1)) {
                return null;
            }
            return assigned.immutable();
        } catch (Throwable t) {
            return null;
        }
    }

    private static boolean safeIsValidWorkBlock(ICookTask task, ServerLevel level, EntityMaid maid, BlockPos pos) {
        try {
            return task.isValidWorkBlock(level, maid, pos);
        } catch (Throwable t) {
            return false;
        }
    }
}
