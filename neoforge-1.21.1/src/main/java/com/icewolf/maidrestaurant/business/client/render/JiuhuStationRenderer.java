package com.icewolf.maidrestaurant.business.client.render;

import com.icewolf.maidrestaurant.business.block.JiuhuStationBlock;
import com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity;
import com.icewolf.maidrestaurant.business.registry.ModItems;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Quaternionf;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

import java.util.HashMap;
import java.util.Map;

public class JiuhuStationRenderer implements BlockEntityRenderer<JiuhuStationBlockEntity> {
    private static final class State {
        Object beRef;          // identity of the owning BlockEntity
        boolean init;
        final boolean[] occ = new boolean[5];
        boolean delivering;
        double startSec;
        int pending;
    }

    private final Map<BlockPos, State> states = new HashMap<>();
    private ItemStack[] proxies;

    public JiuhuStationRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    private ItemStack proxy(int cmd) {
        if (proxies == null) proxies = new ItemStack[18];
        if (proxies[cmd] == null) {
            ItemStack s = new ItemStack(ModItems.RENDER_PROXY.get());
            s.set(net.minecraft.core.component.DataComponents.CUSTOM_MODEL_DATA,
                    new net.minecraft.world.item.component.CustomModelData(cmd));
            proxies[cmd] = s;
        }
        return proxies[cmd];
    }

    private void renderProxy(int cmd, PoseStack pose, MultiBufferSource buf,
                             int light, int overlay, Level level) {
        ItemRenderer ir = Minecraft.getInstance().getItemRenderer();
        // Proxy models are authored in absolute block-element coordinates; renderStatic(NONE) ends
        // with translate(-0.5) meant to centre a normal item, which shunts absolute geometry toward
        // the origin corner. Counter-translate +0.5 to cancel so bags/gap render where authored.
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        ir.renderStatic(proxy(cmd), ItemDisplayContext.NONE, light, overlay, pose, buf, level, 0);
        pose.popPose();
    }

    private static float[] sample(float[] data, float t, float dflt) {
        float[] out = {dflt, dflt, dflt};
        if (data == null) return out;
        int n = data.length / 4;
        if (t <= data[0]) {
            out[0] = data[1]; out[1] = data[2]; out[2] = data[3];
            return out;
        }
        int last = (n - 1) * 4;
        if (t >= data[last]) {
            out[0] = data[last + 1]; out[1] = data[last + 2]; out[2] = data[last + 3];
            return out;
        }
        for (int k = 0; k < n - 1; k++) {
            int a = k * 4, c = (k + 1) * 4;
            if (t >= data[a] && t <= data[c]) {
                float f = (t - data[a]) / (data[c] - data[a]);
                for (int q = 0; q < 3; q++)
                    out[q] = data[a + 1 + q] + (data[c + 1 + q] - data[a + 1 + q]) * f;
                return out;
            }
        }
        return out;
    }

    private void applyBone(int boneId, float t, PoseStack pose) {
        float[] o = JiuhuAnimData.ORIGIN[boneId];
        float[] r = sample(JiuhuAnimData.ROT[boneId], t, 0f);
        float[] p = sample(JiuhuAnimData.POS[boneId], t, 0f);
        float[] s = sample(JiuhuAnimData.SCL[boneId], t, 1f);
        pose.translate((o[0] + p[0]) / 16f, (o[1] + p[1]) / 16f, (o[2] + p[2]) / 16f);
        if (r[2] != 0f) pose.mulPose(new Quaternionf().rotateZ((float) Math.toRadians(r[2])));
        if (r[1] != 0f) pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(r[1])));
        if (r[0] != 0f) pose.mulPose(new Quaternionf().rotateX((float) Math.toRadians(r[0])));
        pose.scale(s[0], s[1], s[2]);
        pose.translate(-o[0] / 16f, -o[1] / 16f, -o[2] / 16f);
    }

    private void nested(int boneId, int cmd, float t, PoseStack pose,
                        MultiBufferSource buf, int light, int overlay, Level level) {
        pose.pushPose();
        applyBone(boneId, t, pose);
        renderProxy(cmd, pose, buf, light, overlay, level);
        pose.popPose();
    }

    private static void trigger(State st, double nowSec) {
        if (st.delivering) st.pending = Math.min(st.pending + 1, 4);
        else {
            st.delivering = true;
            st.startSec = nowSec;
        }
    }

    @Override
    public void render(JiuhuStationBlockEntity be, float partial, PoseStack pose,
                       MultiBufferSource buf, int light, int overlay) {
        Level level = be.getLevel();
        if (level == null) return;
        double nowSec = (level.getGameTime() + partial) / 20.0;

        // Bind animation state to the BlockEntity *instance*; a fresh block after breaking (even at
        // the same BlockPos) must not inherit the old "delivering" state and replay its animation.
        State st = states.get(be.getBlockPos());
        if (st == null || st.beRef != be) {
            st = new State();
            st.beRef = be;
            states.put(be.getBlockPos(), st);
        }
        boolean[] cur = new boolean[5];
        for (int i = 0; i < 5; i++) cur[i] = !be.getItem(i).isEmpty();

        if (!st.init) {
            System.arraycopy(cur, 0, st.occ, 0, 5);
            st.init = true;
        } else {
            for (int i = 0; i < 5; i++) {
                if (st.occ[i] && !cur[i]) trigger(st, nowSec);
                st.occ[i] = cur[i];
            }
        }

        // Match the blockstate's per-FACING y-rotation of the static model, otherwise the
        // unrotated bags/gap drift outside the rotated body. PoseStack equivalent is rotateY(-y).
        Direction facing = be.getBlockState().getValue(JiuhuStationBlock.FACING);
        int yDeg = switch (facing) {
            case EAST -> 90; case SOUTH -> 180; case WEST -> 270; default -> 0;
        };
        pose.pushPose();
        pose.translate(0.5, 0.0, 0.5);
        pose.mulPose(new Quaternionf().rotateY((float) Math.toRadians(-yDeg)));
        pose.translate(-0.5, 0.0, -0.5);

        // bag1 is authored at the viewer's RIGHT of the front (small world x); reverse slot->bag
        // so GUI slot 0 (screen left) renders at the front's left cubby = bag5. Facing-independent.
        for (int i = 0; i < 5; i++) {
            if (cur[i]) renderProxy(JiuhuAnimData.CMD_BAG1 + (4 - i), pose, buf, light, overlay, level);
        }

        float t = st.delivering ? (float) (nowSec - st.startSec) : 0f;
        if (st.delivering) {
            pose.pushPose();
            applyBone(JiuhuAnimData.B_GAP, t, pose);
            nested(JiuhuAnimData.B_VOID, JiuhuAnimData.CMD_VOID, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_LIDT, JiuhuAnimData.CMD_LIDT, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_LIDB, JiuhuAnimData.CMD_LIDB, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_S0, JiuhuAnimData.CMD_S0, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_S1, JiuhuAnimData.CMD_S1, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_S2, JiuhuAnimData.CMD_S2, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_SHARDS, JiuhuAnimData.CMD_SHARDS, t, pose, buf, light, overlay, level);
            pose.popPose();
            nested(JiuhuAnimData.B_DROPBAG, JiuhuAnimData.CMD_DROPBAG, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_FALL0, JiuhuAnimData.CMD_FALL0, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_FALL1, JiuhuAnimData.CMD_FALL1, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_FALL2, JiuhuAnimData.CMD_FALL2, t, pose, buf, light, overlay, level);
            nested(JiuhuAnimData.B_FALL3, JiuhuAnimData.CMD_FALL3, t, pose, buf, light, overlay, level);

            if (t >= JiuhuAnimData.DURATION) {
                if (st.pending > 0) {
                    st.pending--;
                    st.startSec = nowSec;
                } else {
                    st.delivering = false;
                }
            }
        } else {
            double sec = level.getGameTime() / 20.0;
            float breath = 0.025f * (1f - (float) Math.cos(2.0 * Math.PI * sec / 3.0));
            pose.pushPose();
            pose.translate(8f / 16f, (21.2f + breath) / 16f, 8f / 16f);
            pose.scale(0.01f, 0.01f, 0.01f);
            pose.translate(-8f / 16f, -21.2f / 16f, -8f / 16f);
            renderProxy(JiuhuAnimData.CMD_VOID, pose, buf, light, overlay, level);
            renderProxy(JiuhuAnimData.CMD_LIDT, pose, buf, light, overlay, level);
            renderProxy(JiuhuAnimData.CMD_LIDB, pose, buf, light, overlay, level);
            renderProxy(JiuhuAnimData.CMD_S0, pose, buf, light, overlay, level);
            renderProxy(JiuhuAnimData.CMD_S1, pose, buf, light, overlay, level);
            renderProxy(JiuhuAnimData.CMD_S2, pose, buf, light, overlay, level);
            renderProxy(JiuhuAnimData.CMD_SHARDS, pose, buf, light, overlay, level);
            pose.popPose();
        }

        pose.popPose(); // end FACING y-rotation wrapper
    }
}
