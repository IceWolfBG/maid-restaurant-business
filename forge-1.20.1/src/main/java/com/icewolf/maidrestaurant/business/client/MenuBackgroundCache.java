package com.icewolf.maidrestaurant.business.client;

import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.core.MenuBgConstants;
import com.icewolf.maidrestaurant.business.network.MenuBgRequestPacket;
import com.icewolf.maidrestaurant.business.network.ModMessages;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import org.lwjgl.system.MemoryUtil;

/**
 * 客户端饭店菜单背景纹理缓存：
 * - 记录服务端图库文件名列表（来自 MenuBgListPacket）；
 * - 按文件名向服务端请求字节（MenuBgRequestPacket），分片收齐后构建 DynamicTexture；
 * - 提供 blit 到面板的方法（拉伸铺满，失败/缺失回退纯色由调用方处理）。
 */
public final class MenuBackgroundCache {
    private static final Map<String, ResourceLocation> LOC = new HashMap<>();
    private static final Map<String, int[]> DIM = new HashMap<>();
    private static final Set<String> PENDING = new HashSet<>();
    private static final Map<String, ByteArrayOutputStream> ASSEMBLE = new HashMap<>();
    private static List<String> serverNames = new ArrayList<>();

    private MenuBackgroundCache() {}

    public static void setServerNames(List<String> names) {
        serverNames = new ArrayList<>(names);
    }

    public static List<String> getServerNames() {
        return serverNames;
    }

    /** 若该背景图尚未缓存且未在请求中，则向服务端发起请求（内置图走本地加载）。 */
    public static void request(String name) {
        if (name == null || name.isEmpty()) return;
        if (LOC.containsKey(name) || PENDING.contains(name) || FAILED.contains(name)) return;
        // 模组 jar 内置背景：任何客户端本地都有资源，无需联网请求
        if (MenuBgConstants.isBuiltin(name)) {
            loadBuiltin(name);
            return;
        }
        PENDING.add(name);
        ModMessages.INSTANCE.sendToServer(new MenuBgRequestPacket(name));
    }

    /** 从模组 jar 资源加载内置背景图并注册为动态纹理。 */
    private static void loadBuiltin(String name) {
        if (LOC.containsKey(name) || PENDING.contains(name)) return;
        PENDING.add(name);
        try {
            ResourceLocation res = new ResourceLocation(MaidRestaurantBusiness.MOD_ID,
                    MenuBgConstants.BUILTIN_RES_PATH);
            Optional<Resource> opt = Minecraft.getInstance().getResourceManager().getResource(res);
            if (opt.isEmpty()) {
                PENDING.remove(name);
                FAILED.add(name);
                return;
            }
            try (InputStream is = opt.get().open()) {
                byte[] bytes = is.readAllBytes();
                NativeImage img = readImage(bytes);
                float luma = averageLuma(img);
                DynamicTexture tex = new DynamicTexture(img);
                ResourceLocation loc = new ResourceLocation(MaidRestaurantBusiness.MOD_ID, "menu_bg/builtin");
                Minecraft.getInstance().getTextureManager().register(loc, tex);
                LOC.put(name, loc);
                DIM.put(name, new int[]{img.getWidth(), img.getHeight()});
                LUMA.put(name, luma);
                PENDING.remove(name);
            }
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.warn("MenuBackgroundCache: failed to load builtin bg", e);
            PENDING.remove(name);
            FAILED.add(name);
        }
    }

    public static boolean hasTexture(String name) {
        return LOC.containsKey(name);
    }

    /** 平均亮度达到该值即视为浅色背景（浅底上 UI 文字需改用深色）。 */
    private static final float LIGHT_THRESHOLD = 128f;
    private static final Map<String, Float> LUMA = new HashMap<>();
    /** 解码失败的背景：不再反复重试，避免每帧重复解码。 */
    private static final Set<String> FAILED = new HashSet<>();

    /**
     * 解码 PNG 字节。必须使用 direct 缓冲区：NativeImage.read 传入堆内 ByteBuffer
     * 会在 stbi 原生层直接 ACCESS_VIOLATION 崩掉游戏（与 MC 内部 read(InputStream) 的做法一致）。
     */
    public static NativeImage readImage(byte[] bytes) throws IOException {
        ByteBuffer direct = MemoryUtil.memAlloc(bytes.length);
        try {
            direct.put(bytes);
            direct.rewind();
            return NativeImage.read(direct);
        } finally {
            MemoryUtil.memFree(direct);
        }
    }

    /** 该背景是否偏亮；亮底上需改用深色文字才可读（尚未加载完成时按深色处理）。 */
    public static boolean isLightBackground(String name) {
        Float v = LUMA.get(name);
        return v != null && v >= LIGHT_THRESHOLD;
    }

    /** 采样计算图像平均亮度（0-255）。 */
    private static float averageLuma(NativeImage img) {
        int w = img.getWidth();
        int h = img.getHeight();
        int stepX = Math.max(1, w / 64);
        int stepY = Math.max(1, h / 64);
        long sum = 0;
        int n = 0;
        for (int y = 0; y < h; y += stepY) {
            for (int x = 0; x < w; x += stepX) {
                int argb = img.getPixelRGBA(x, y);
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                sum += (long) (0.299f * r + 0.587f * g + 0.114f * b);
                n++;
            }
        }
        return n == 0 ? 0f : (float) sum / n;
    }

    /** 服务端下发的一张分片；收齐最后一片时构建纹理并注册。 */
    public static void receiveChunk(String name, int index, boolean isLast, byte[] data) {
        if (!MenuBgConstants.isSafeName(name)) {
            PENDING.remove(name);
            ASSEMBLE.remove(name);
            return;
        }
        ByteArrayOutputStream bos = ASSEMBLE.computeIfAbsent(name, k -> new ByteArrayOutputStream());
        try {
            bos.write(data);
        } catch (IOException ignored) {
            // 极端内存情况，放弃本次加载
            PENDING.remove(name);
            ASSEMBLE.remove(name);
            return;
        }
        if (!isLast) return;

        byte[] all = bos.toByteArray();
        ASSEMBLE.remove(name);
        PENDING.remove(name);
        try {
            NativeImage img = readImage(all);
            float luma = averageLuma(img);
            DynamicTexture tex = new DynamicTexture(img);
            String safe = name.replaceAll("[^a-zA-Z0-9_\\-]", "_");
            ResourceLocation loc = new ResourceLocation(MaidRestaurantBusiness.MOD_ID,
                    "menu_bg/" + safe + "_" + Integer.toHexString(all.hashCode()));
            Minecraft.getInstance().getTextureManager().register(loc, tex);
            LOC.put(name, loc);
            DIM.put(name, new int[]{img.getWidth(), img.getHeight()});
            LUMA.put(name, luma);
        } catch (Exception e) {
            MaidRestaurantBusiness.LOGGER.warn("MenuBackgroundCache: failed to load bg {}", name, e);
            FAILED.add(name);
        }
    }

    /** 把已缓存的纹理拉伸铺满 (x,y,w,h) 矩形。返回是否成功绘制。 */
    public static boolean blit(net.minecraft.client.gui.GuiGraphics g, String name,
                               int x, int y, int w, int h) {
        ResourceLocation loc = LOC.get(name);
        int[] dim = DIM.get(name);
        if (loc == null || dim == null) {
            request(name);
            return false;
        }
        // 10 参 blit：将整张图(imgW x imgH)拉伸绘制到面板矩形(w x h)
        g.blit(loc, x, y, w, h, 0, 0, dim[0], dim[1], dim[0], dim[1]);
        return true;
    }

    public static void clearPending(String name) {
        PENDING.remove(name);
    }
}
