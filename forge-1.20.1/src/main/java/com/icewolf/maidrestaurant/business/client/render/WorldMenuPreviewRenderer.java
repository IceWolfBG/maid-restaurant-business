package com.icewolf.maidrestaurant.business.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/**
 * 在世界坐标把一个 ItemStack 渲染成悬浮贴图（始终面向玩家、轻微上下浮动）。
 * 用于饭店菜单在选上菜点时于目标方块上方显示菜单预览。
 *
 * 本工具为营业中自行实现的轻量渲染辅助，仅使用 Minecraft 通用渲染 API，
 * 不依赖任何外部 mod 的渲染实现。
 */
public final class WorldMenuPreviewRenderer {
    private WorldMenuPreviewRenderer() {}

    /**
     * @param worldPos 贴图中心所在的世界坐标（已为方块中心上浮 1 格）
     */
    public static void renderItemFloating(PoseStack poseStack, MultiBufferSource buffer, Vec3 worldPos,
                                          Minecraft minecraft, ItemStack stack) {
        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 camPos = camera.getPosition();

        poseStack.pushPose();
        // 平移到世界坐标：poseStack 处于相机空间，需减去相机位置
        poseStack.translate(worldPos.x - camPos.x, worldPos.y - camPos.y, worldPos.z - camPos.z);

        // 让贴图始终正对玩家
        double dx = camPos.x - worldPos.x;
        double dz = camPos.z - worldPos.z;
        float yaw = (float) Math.atan2(dz, dx);
        poseStack.mulPose(Axis.YP.rotation(-yaw + (float) Math.PI / 2f));

        // 轻微上下浮动，营造悬浮感
        float t = minecraft.level.getGameTime() / 20.0f;
        poseStack.translate(0, Math.sin(t) * 0.08, 0);

        poseStack.scale(0.6f, 0.6f, 0.6f);

        ItemRenderer itemRenderer = minecraft.getItemRenderer();
        itemRenderer.renderStatic(
                stack,
                ItemDisplayContext.FIXED,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                buffer,
                minecraft.level,
                0
        );

        poseStack.popPose();
    }
}
