package com.icewolf.maidrestaurant.business.client.render;

import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 饭店菜单"选上菜点"可视化：手持已绑定打单机的饭店菜单时，在送餐点（或准星指向的候选方块）
 * 上方渲染一个悬浮的饭店菜单贴图，替代原先仅靠底部提示的方式，让玩家直观看到上菜点位置。
 *
 * 仅在客户端生效，监听世界渲染的透明方块之后阶段；不修改任何服务端逻辑。
 */
@Mod.EventBusSubscriber(modid = "maid_restaurant_business", value = Dist.CLIENT)
public final class ServingPointGhostRenderer {
    private ServingPointGhostRenderer() {}

    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) return;

        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null) return;

        // 主手或副手持饭店菜单都生效
        ItemStack menu = player.getMainHandItem();
        if (!menu.is(ModItems.RESTAURANT_MENU.get())) {
            menu = player.getOffhandItem();
            if (!menu.is(ModItems.RESTAURANT_MENU.get())) return;
        }

        CompoundTag tag = menu.getTag();
        if (tag == null || !tag.contains(PlayerOrderManager.M_MACHINE)) return;

        Level level = mc.level;
        if (!tag.getString(PlayerOrderManager.M_DIM).equals(level.dimension().location().toString())) return;

        PoseStack poseStack = event.getPoseStack();
        MultiBufferSource.BufferSource buffer = mc.renderBuffers().bufferSource();

        // 已选定上菜点：在其上方常驻显示；否则：对当前准星指向的方块实时预览
        Vec3 target = null;
        if (tag.contains(PlayerOrderManager.M_DELIVERY_POS)) {
            CompoundTag dp = tag.getCompound(PlayerOrderManager.M_DELIVERY_POS);
            BlockPos p = new BlockPos(dp.getInt("x"), dp.getInt("y"), dp.getInt("z"));
            target = Vec3.atCenterOf(p.above());
        } else {
            HitResult hit = mc.hitResult;
            if (hit != null && hit.getType() == HitResult.Type.BLOCK) {
                BlockHitResult bhr = (BlockHitResult) hit;
                BlockPos point = bhr.getBlockPos().relative(bhr.getDirection());
                BlockPos machine = BlockPos.of(tag.getLong(PlayerOrderManager.M_MACHINE));
                long r2 = (long) PlayerOrderManager.BIND_RADIUS * PlayerOrderManager.BIND_RADIUS;
                if (machine.distSqr(point) <= r2) {
                    target = Vec3.atCenterOf(point.above());
                }
            }
        }

        if (target != null) {
            WorldMenuPreviewRenderer.renderItemFloating(
                    poseStack, buffer, target, mc, new ItemStack(ModItems.RESTAURANT_MENU.get()));
            buffer.endBatch();
        }
    }
}
