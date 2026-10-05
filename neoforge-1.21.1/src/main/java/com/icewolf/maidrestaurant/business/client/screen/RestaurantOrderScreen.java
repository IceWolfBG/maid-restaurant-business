package com.icewolf.maidrestaurant.business.client.screen;

import cn.breezeth.ordertocook.core.ModConstants;
import com.icewolf.maidrestaurant.business.MaidRestaurantBusiness;
import com.icewolf.maidrestaurant.business.client.MenuBackgroundCache;
import com.icewolf.maidrestaurant.business.client.screen.MenuBgFilePickerScreen;
import com.icewolf.maidrestaurant.business.core.MenuBgConstants;
import com.icewolf.maidrestaurant.business.core.OtcCompat;
import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import com.icewolf.maidrestaurant.business.network.MenuBgListRequestPacket;
import com.icewolf.maidrestaurant.business.network.MenuBgSetPacket;
import com.icewolf.maidrestaurant.business.network.MenuBgUploadPacket;
import com.icewolf.maidrestaurant.business.network.MenuRenamePacket;
import com.icewolf.maidrestaurant.business.network.PlayerOrderSubmitPacket;
import com.icewolf.maidrestaurant.business.util.ItemStackUtils;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.network.PacketDistributor;

/** 饭店菜单点单界面：勾选菜品与数量（一次最多 6 种、每种 6 个），选择配送时限与小费档位，提交后发网络包。 */
public class RestaurantOrderScreen extends Screen {
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 248;
    private static final int ROW_H = 22;
    private static final int LIST_H = 124;
    private static final int SET_W = 200;
    private static final int SET_H = 264;

    private final CompoundTag menuTag;
    private final List<String> ids = new ArrayList<>();
    private final Map<String, Integer> counts = new TreeMap<>();

    private int timeTier = 0;
    private int tipTier = 0;

    private int px;
    private int py;
    private int scroll = 0;
    private int maxScroll = 0;

    private final List<Hit> hits = new ArrayList<>();
    private final List<Hit> settingsHits = new ArrayList<>();
    private boolean settingsMode = false;
    private EditBox titleBox;
    private Component displayTitle;

    // 背景图设置相关
    private String selectedBg = "";
    private int bgScroll = 0;
    private static final int BG_ROW_H = 16;
    private static final int BG_LIST_VH = 64;

    public RestaurantOrderScreen(ItemStack menu) {
        super(Component.translatable("screen.business.restaurant_order.title"));
        CompoundTag mt = ItemStackUtils.getTag(menu);
        this.menuTag = mt != null ? mt.copy() : new CompoundTag();
        CompoundTag foods = this.menuTag.getCompound(PlayerOrderManager.M_FOODS);
        this.ids.addAll(foods.getAllKeys());
        for (String id : this.ids) {
            this.counts.put(id, 0);
        }
        String customTitle = this.menuTag.getString(PlayerOrderManager.M_MENU_TITLE);
        this.displayTitle = customTitle.isBlank()
                ? Component.translatable("screen.business.restaurant_order.title")
                : Component.literal(customTitle);
        this.selectedBg = this.menuTag.getString(PlayerOrderManager.M_MENU_BG);
    }

    @Override
    protected void init() {
        this.px = (this.width - PANEL_W) / 2;
        this.py = (this.height - PANEL_H) / 2;
        this.maxScroll = Math.max(0, this.ids.size() * ROW_H - LIST_H);

        int spx = this.px + (PANEL_W - SET_W) / 2;
        int spy = this.py + (PANEL_H - SET_H) / 2;
        this.titleBox = new EditBox(this.font, spx + 12, spy + 26, SET_W - 24, 18,
                Component.translatable("screen.business.restaurant_order.settings_title"));
        this.titleBox.setMaxLength(32);
        this.titleBox.setHint(Component.translatable("screen.business.restaurant_order.title"));
        this.titleBox.setValue(this.menuTag.getString(PlayerOrderManager.M_MENU_TITLE));
    }

    private int listX() { return this.px + 10; }
    private int listY() { return this.py + 28; }
    private int listW() { return PANEL_W - 20; }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.hits.clear();

        // 设置模式：只渲染全屏遮罩 + 设置子面板，不再渲染点单主界面，避免两层 GUI 重叠
        if (this.settingsMode) {
            renderSettingsOnly(graphics, mouseX, mouseY, partialTick);
            // 1.21.1 的 Screen.render 开头会自动再调一次 renderBackground（含全屏模糊后处理），
            // 面板已画完再调会把整帧（含面板）糊掉并叠加菜单底色，形成"虚影"——故不得调用 super.render
            return;
        }

        // 背景：优先用服务器托管的背景图（拉伸铺满），缺失/加载中回退纯色
        String bgName = this.menuTag.getString(PlayerOrderManager.M_MENU_BG);
        boolean bgDrawn = !bgName.isEmpty()
                && MenuBackgroundCache.blit(graphics, bgName, this.px, this.py, PANEL_W, PANEL_H);
        if (!bgDrawn) {
            graphics.fill(this.px, this.py, this.px + PANEL_W, this.py + PANEL_H, 0xFF1A1410);
        }
        // 浅色背景（如内置米色内芯）上改用深色墨字，保证文字可读
        boolean lightBg = bgDrawn && MenuBackgroundCache.isLightBackground(bgName);
        int cTitle = lightBg ? 0xFF4A3018 : 0xFFE8D9B0;
        int cText = lightBg ? 0xFF3A2A18 : 0xFFEDE2C8;
        int cLabel = lightBg ? 0xFF6B4A22 : 0xFFB9A882;
        int cCount = lightBg ? 0xFF2A1E10 : 0xFFFFFF;
        int cTotal = lightBg ? 0xFF8A2E1E : 0xFFE8C87A;
        graphics.renderOutline(this.px, this.py, PANEL_W, PANEL_H, 0xFFB58A4A);
        // 浅色底上关掉文字阴影：MC 自带 1px 深色投影在米色页面上会形成可见偏移副本（重影）
        boolean shadow = !lightBg;
        int titleX = this.px + PANEL_W / 2 - this.font.width(this.displayTitle) / 2;
        graphics.drawString(this.font, this.displayTitle, titleX, this.py + 10, cTitle, shadow);

        // 右上角设置按钮
        int setBtnW = 30;
        int setBtnH = 14;
        int setBtnX = this.px + PANEL_W - 12 - setBtnW;
        int setBtnY = this.py + 8;
        graphics.fill(setBtnX, setBtnY, setBtnX + setBtnW, setBtnY + setBtnH, 0xFF3A2F22);
        graphics.renderOutline(setBtnX, setBtnY, setBtnW, setBtnH, 0xFFB58A4A);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.settings_btn"),
                setBtnX + setBtnW / 2, setBtnY + 3, 0xFFE8D9B0);
        this.hits.add(new Hit("settings", "", setBtnX, setBtnY, setBtnW, setBtnH));

        int lx = listX();
        int ly = listY();
        int lw = listW();

        graphics.enableScissor(lx, ly, lx + lw, ly + LIST_H);
        for (int i = 0; i < this.ids.size(); i++) {
            int rowY = ly + i * ROW_H - this.scroll;
            if (rowY + ROW_H < ly || rowY > ly + LIST_H) continue;
            String id = this.ids.get(i);
            ResourceLocation rl = ResourceLocation.tryParse(id);
            Item item = rl != null ? BuiltInRegistries.ITEM.get(rl) : OtcCompat.ORDER();
            graphics.renderItem(new ItemStack(item), lx + 4, rowY + 3);
            graphics.drawString(this.font, item.getDescription(), lx + 26, rowY + 7, cText, shadow);

            int minusX = lx + lw - 66;
            int plusX = lx + lw - 26;
            int btnY = rowY + 3;
            graphics.fill(minusX, btnY, minusX + 16, btnY + 16, 0xFF3A2F22);
            graphics.fill(plusX, btnY, plusX + 16, btnY + 16, 0xFF3A2F22);
            graphics.drawCenteredString(this.font, Component.literal("-"), minusX + 8, btnY + 4, 0xFFE8D9B0);
            graphics.drawCenteredString(this.font, Component.literal("+"), plusX + 8, btnY + 4, 0xFFE8D9B0);
            Component countC = Component.literal(String.valueOf(this.counts.getOrDefault(id, 0)));
            graphics.drawString(this.font, countC,
                    lx + lw - 41 - this.font.width(countC) / 2, btnY + 5, cCount, shadow);
            this.hits.add(new Hit("minus", id, minusX, btnY, 16, 16));
            this.hits.add(new Hit("plus", id, plusX, btnY, 16, 16));
        }
        graphics.disableScissor();

        // 滚动条
        if (this.maxScroll > 0) {
            int trackX = lx + lw + 2;
            int barH = Math.max(20, LIST_H * LIST_H / (this.ids.size() * ROW_H));
            int barY = ly + (LIST_H - barH) * this.scroll / this.maxScroll;
            graphics.fill(trackX, ly, trackX + 4, ly + LIST_H, 0xFF2A2118);
            graphics.fill(trackX, barY, trackX + 4, barY + barH, 0xFFB58A4A);
        }

        // ===== 配送时限档位 =====
        int timeLabelY = this.py + 160;
        graphics.drawString(this.font,
                Component.translatable("screen.business.restaurant_order.time_label"),
                this.px + 12, timeLabelY + 4, cLabel, shadow);
        for (int t = 0; t < PlayerOrderManager.TIME_TIER_COUNT; t++) {
            int bx = this.px + 64 + t * 50;
            boolean sel = this.timeTier == t;
            graphics.fill(bx, timeLabelY, bx + 46, timeLabelY + 16, sel ? 0xFF6E4B2A : 0xFF3A2F22);
            if (sel) graphics.renderOutline(bx, timeLabelY, 46, 16, 0xFFD9B36C);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.business.restaurant_order.time_tier." + t),
                    bx + 23, timeLabelY + 4, sel ? 0xFFF4E6C2 : 0xFFB9A882);
            this.hits.add(new Hit("time", String.valueOf(t), bx, timeLabelY, 46, 16));
        }

        // ===== 小费档位 =====
        int tipLabelY = this.py + 182;
        graphics.drawString(this.font,
                Component.translatable("screen.business.restaurant_order.tip_label"),
                this.px + 12, tipLabelY + 4, cLabel, shadow);
        for (int t = 0; t < PlayerOrderManager.TIP_TIER_COUNT; t++) {
            int bx = this.px + 64 + t * 37;
            boolean sel = this.tipTier == t;
            graphics.fill(bx, tipLabelY, bx + 34, tipLabelY + 16, sel ? 0xFF6E4B2A : 0xFF3A2F22);
            if (sel) graphics.renderOutline(bx, tipLabelY, 34, 16, 0xFFD9B36C);
            graphics.drawCenteredString(this.font,
                    Component.translatable("screen.business.restaurant_order.tip_tier." + t),
                    bx + 17, tipLabelY + 4, sel ? 0xFFF4E6C2 : 0xFFB9A882);
            this.hits.add(new Hit("tip", String.valueOf(t), bx, tipLabelY, 34, 16));
        }

        int selectedKinds = 0;
        int totalItems = 0;
        for (int v : this.counts.values()) {
            if (v > 0) selectedKinds++;
            totalItems += v;
        }
        graphics.drawString(this.font,
                Component.translatable("screen.business.restaurant_order.summary", selectedKinds, totalItems),
                this.px + 12, this.py + 206, cLabel, shadow);

        Component totalLine = Component.translatable("screen.business.restaurant_order.total", computeTotal());
        graphics.drawString(this.font, totalLine,
                this.px + PANEL_W - 12 - this.font.width(totalLine), this.py + 206, cTotal, shadow);

        int submitX = this.px + PANEL_W - 90;
        int submitY = this.py + PANEL_H - 28;
        boolean canSubmit = selectedKinds > 0 && selectedKinds <= PlayerOrderManager.MAX_KINDS;
        graphics.fill(submitX, submitY, submitX + 78, submitY + 20, canSubmit ? 0xFF6E4B2A : 0xFF3A332A);
        graphics.renderOutline(submitX, submitY, 78, 20, canSubmit ? 0xFFD9B36C : 0xFF5A5045);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.submit"),
                submitX + 39, submitY + 6, canSubmit ? 0xFFF4E6C2 : 0xFF8A8070);
        this.hits.add(new Hit("submit", "", submitX, submitY, 78, 20));

        // 档位按钮悬停说明
        for (Hit h : this.hits) {
            if ((h.type.equals("time") || h.type.equals("tip")) && h.contains(mouseX, mouseY)) {
                int idx = Integer.parseInt(h.id);
                String key = h.type.equals("time")
                        ? "screen.business.restaurant_order.time_tip." + idx
                        : "screen.business.restaurant_order.tip_tip." + idx;
                graphics.renderTooltip(this.font, Component.translatable(key), mouseX, mouseY);
                break;
            }
        }

        // 本界面无任何注册 widget（按钮均自绘 + hits 判定），且 1.21.1 的 super.render 会重放
        // renderBackground（全屏模糊 + 菜单底色）叠在面板之上，绝不调用
    }

    /** 设置模式专用：只渲染全屏遮罩 + 设置子面板（自定义点单名称 + 背景图），不渲染点单主界面。 */
    private void renderSettingsOnly(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.settingsHits.clear();
        int spx = this.px + (PANEL_W - SET_W) / 2;
        int spy = this.py + (PANEL_H - SET_H) / 2;
        graphics.fill(0, 0, this.width, this.height, 0xC0000000);
        graphics.fill(spx, spy, spx + SET_W, spy + SET_H, 0xFF241B13);
        graphics.renderOutline(spx, spy, SET_W, SET_H, 0xFFB58A4A);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.settings_title"),
                spx + SET_W / 2, spy + 12, 0xFFE8D9B0);
        this.titleBox.render(graphics, mouseX, mouseY, partialTick);

        // ===== 背景图区域 =====
        int bgLabelY = spy + 54;
        graphics.drawString(this.font,
                Component.translatable("screen.business.restaurant_order.settings_bg"),
                spx + 12, bgLabelY, 0xFFE8D9B0);

        // 预览框
        int prevX = spx + 12;
        int prevY = spy + 68;
        int prevS = 44;
        graphics.fill(prevX, prevY, prevX + prevS, prevY + prevS, 0xFF120C08);
        graphics.renderOutline(prevX, prevY, prevS, prevS, 0xFFB58A4A);
        if (!this.selectedBg.isEmpty()) {
            MenuBackgroundCache.blit(graphics, this.selectedBg, prevX, prevY, prevS, prevS);
            graphics.renderOutline(prevX, prevY, prevS, prevS, 0xFFB58A4A);
        }
        // 选中文件名（预览右侧，超长截断）
        String shownName = this.selectedBg.isEmpty()
                ? Component.translatable("screen.business.restaurant_order.bg_none").getString()
                : this.selectedBg;
        if (this.font.width(shownName) > SET_W - 70) {
            shownName = this.font.plainSubstrByWidth(shownName, SET_W - 70) + "…";
        }
        graphics.drawString(this.font, shownName, spx + 62, spy + 72, 0xFFEDE2C8);
        graphics.drawString(this.font,
                Component.translatable("screen.business.restaurant_order.bg_preview"),
                spx + 62, spy + 88, 0xFFB9A882);

        // 列表标题
        int listLabelY = spy + 118;
        graphics.drawString(this.font,
                Component.translatable("screen.business.restaurant_order.bg_select"),
                spx + 12, listLabelY, 0xFFB9A882);

        // 服务器图库文件名列表（可滚动）
        List<String> names = MenuBackgroundCache.getServerNames();
        int listX = spx + 10;
        int listY = spy + 132;
        int listW = SET_W - 20;
        int bgMaxScroll = Math.max(0, names.size() * BG_ROW_H - BG_LIST_VH);
        this.bgScroll = Math.max(0, Math.min(bgMaxScroll, this.bgScroll));
        graphics.enableScissor(listX, listY, listX + listW, listY + BG_LIST_VH);
        for (int i = 0; i < names.size(); i++) {
            int rowY = listY + i * BG_ROW_H - this.bgScroll;
            if (rowY + BG_ROW_H < listY || rowY > listY + BG_LIST_VH) continue;
            String name = names.get(i);
            boolean sel = name.equals(this.selectedBg);
            if (sel) graphics.fill(listX, rowY, listX + listW, rowY + BG_ROW_H - 1, 0xFF6E4B2A);
            // 内置图显示本地化名称，玩家/服务器图库中的图显示原始文件名
            String dispName = MenuBgConstants.isBuiltin(name)
                    ? Component.translatable("screen.business.restaurant_order.bg_builtin").getString()
                    : name;
            graphics.drawString(this.font,
                    this.font.plainSubstrByWidth(dispName, listW - 6),
                    listX + 4, rowY + 4, sel ? 0xFFF4E6C2 : 0xFFEDE2C8);
            this.settingsHits.add(new Hit("bg", name, listX, rowY, listW, BG_ROW_H));
        }
        graphics.disableScissor();
        if (bgMaxScroll > 0) {
            int trackX = listX + listW + 2;
            int barH = Math.max(16, BG_LIST_VH * BG_LIST_VH / Math.max(1, names.size() * BG_ROW_H));
            int barY = listY + (BG_LIST_VH - barH) * this.bgScroll / bgMaxScroll;
            graphics.fill(trackX, listY, trackX + 4, listY + BG_LIST_VH, 0xFF2A2118);
            graphics.fill(trackX, barY, trackX + 4, barY + barH, 0xFFB58A4A);
        }

        // 需求提醒（常驻显示，避免玩家白做不符合要求的图）
        int reqY = spy + 200;
        graphics.drawString(this.font,
                Component.translatable("screen.business.restaurant_order.bg_req"),
                spx + 12, reqY, 0xFF9C8A66);

        // 上传 / 清除 按钮
        int upY = spy + 214;
        int upOkX = spx + 12;
        int upClrX = spx + SET_W - 12 - 80;
        graphics.fill(upOkX, upY, upOkX + 80, upY + 20, 0xFF6E4B2A);
        graphics.renderOutline(upOkX, upY, 80, 20, 0xFFD9B36C);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.bg_upload"),
                upOkX + 40, upY + 6, 0xFFF4E6C2);
        graphics.fill(upClrX, upY, upClrX + 80, upY + 20, 0xFF3A2F22);
        graphics.renderOutline(upClrX, upY, 80, 20, 0xFFB58A4A);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.bg_clear"),
                upClrX + 40, upY + 6, 0xFFE8D9B0);
        this.settingsHits.add(new Hit("bg_upload", "", upOkX, upY, 80, 20));
        this.settingsHits.add(new Hit("bg_clear", "", upClrX, upY, 80, 20));

        // 确定 / 取消
        int btnY = spy + 238;
        int okX = spx + 12;
        int cancelX = spx + SET_W - 12 - 80;
        graphics.fill(okX, btnY, okX + 80, btnY + 20, 0xFF6E4B2A);
        graphics.renderOutline(okX, btnY, 80, 20, 0xFFD9B36C);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.settings_ok"),
                okX + 40, btnY + 6, 0xFFF4E6C2);
        graphics.fill(cancelX, btnY, cancelX + 80, btnY + 20, 0xFF3A2F22);
        graphics.renderOutline(cancelX, btnY, 80, 20, 0xFFB58A4A);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.settings_cancel"),
                cancelX + 40, btnY + 6, 0xFFE8D9B0);
        this.settingsHits.add(new Hit("rename_ok", "", okX, btnY, 80, 20));
        this.settingsHits.add(new Hit("rename_cancel", "", cancelX, btnY, 80, 20));

        // 上传按钮悬停提示完整需求（多行，避免单行超宽出屏）
        for (Hit h : this.settingsHits) {
            if (h.type.equals("bg_upload") && h.contains(mouseX, mouseY)) {
                graphics.renderComponentTooltip(this.font, List.of(
                        Component.translatable("screen.business.restaurant_order.bg_req_tip_1"),
                        Component.translatable("screen.business.restaurant_order.bg_req_tip_2"),
                        Component.translatable("screen.business.restaurant_order.bg_req_tip_3")
                ), mouseX, mouseY);
                break;
            }
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.settingsMode) {
            this.titleBox.mouseClicked(mouseX, mouseY, button);
            for (Hit hit : this.settingsHits) {
                if (hit.contains(mouseX, mouseY)) {
                    switch (hit.type) {
                        case "rename_ok" -> applySettings();
                        case "rename_cancel" -> closeSettings();
                        case "bg" -> this.selectedBg = hit.id;
                        case "bg_upload" -> openUploadDialog();
                        case "bg_clear" -> this.selectedBg = "";
                    }
                    return true;
                }
            }
            return true;
        }
        for (Hit hit : this.hits) {
            if (hit.contains(mouseX, mouseY)) {
                switch (hit.type) {
                    case "minus" -> this.counts.merge(hit.id, -1, Integer::sum);
                    case "plus" -> tryIncrease(hit.id);
                    case "time" -> this.timeTier = Integer.parseInt(hit.id);
                    case "tip" -> this.tipTier = Integer.parseInt(hit.id);
                    case "submit" -> trySubmit();
                    case "settings" -> openSettings();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private void tryIncrease(String id) {
        int current = this.counts.getOrDefault(id, 0);
        if (current >= PlayerOrderManager.MAX_PER_KIND) return;
        if (current == 0) {
            long kinds = this.counts.values().stream().filter(v -> v > 0).count();
            if (kinds >= PlayerOrderManager.MAX_KINDS) {
                if (this.minecraft != null && this.minecraft.player != null) {
                    this.minecraft.player.displayClientMessage(
                            Component.translatable("message.business.order.too_many_kinds"), true);
                }
                return;
            }
        }
        this.counts.put(id, current + 1);
    }

    private void trySubmit() {
        LinkedHashMap<String, Integer> selection = new LinkedHashMap<>();
        for (Map.Entry<String, Integer> e : this.counts.entrySet()) {
            if (e.getValue() > 0) selection.put(e.getKey(), e.getValue());
        }
        if (selection.isEmpty()) return;
        PacketDistributor.sendToServer(
                new PlayerOrderSubmitPacket(this.menuTag, selection, this.timeTier, this.tipTier));
        if (this.minecraft != null) this.minecraft.setScreen(null);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.settingsMode) return this.titleBox.charTyped(codePoint, modifiers);
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.settingsMode) {
            if (keyCode == 256) { closeSettings(); return true; }
            if (keyCode == 257 || keyCode == 335) { applySettings(); return true; }
            return this.titleBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openSettings() {
        this.settingsMode = true;
        this.selectedBg = this.menuTag.getString(PlayerOrderManager.M_MENU_BG);
        this.titleBox.setValue(this.menuTag.getString(PlayerOrderManager.M_MENU_TITLE));
        this.titleBox.setFocused(true);
        // 拉取服务器当前图库列表
        PacketDistributor.sendToServer(new MenuBgListRequestPacket());
    }

    private void closeSettings() {
        this.settingsMode = false;
        this.titleBox.setFocused(false);
    }

    private void applySettings() {
        String value = this.titleBox.getValue().trim();
        if (value.isEmpty()) {
            this.menuTag.remove(PlayerOrderManager.M_MENU_TITLE);
            this.displayTitle = Component.translatable("screen.business.restaurant_order.title");
        } else {
            this.menuTag.putString(PlayerOrderManager.M_MENU_TITLE, value);
            this.displayTitle = Component.literal(value);
        }
        PacketDistributor.sendToServer(new MenuRenamePacket(value));
        // 同步背景图选择
        PacketDistributor.sendToServer(new MenuBgSetPacket(this.selectedBg));
        this.menuTag.putString(PlayerOrderManager.M_MENU_BG, this.selectedBg);
        closeSettings();
    }

    /** 打开游戏内文件选择界面（不依赖 AWT/Swing，避免被启动器的 headless 参数阻断）。 */
    private void openUploadDialog() {
        if (this.minecraft == null || this.minecraft.player == null) return;
        final LocalPlayer player = this.minecraft.player;
        MenuBgFilePickerScreen picker = new MenuBgFilePickerScreen(this,
                (File f) -> validateAndUpload(player, f));
        this.minecraft.setScreen(picker);
    }

    private void validateAndUpload(LocalPlayer player, File f) {
        if (f == null) return;
        String lower = f.getName().toLowerCase();
        if (!lower.endsWith(".png")) {
            showMsg(player, "message.business.menu.bg_notpng");
            return;
        }
        long len = f.length();
        if (len > MenuBgConstants.MAX_FILE_BYTES) {
            showMsg(player, "message.business.menu.bg_toobig");
            return;
        }
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(f.toPath());
        } catch (IOException e) {
            showMsg(player, "message.business.menu.bg_readfail");
            return;
        }
        // 本地解码取尺寸，提前拦掉超尺寸图，避免玩家白做
        int w, h;
        try (NativeImage img = MenuBackgroundCache.readImage(bytes)) {
            w = img.getWidth();
            h = img.getHeight();
        } catch (Exception e) {
            showMsg(player, "message.business.menu.bg_notpng");
            return;
        }
        if (w > MenuBgConstants.MAX_EDGE || h > MenuBgConstants.MAX_EDGE) {
            showMsg(player, "message.business.menu.bg_toobig_dim", String.valueOf(w), String.valueOf(h));
            return;
        }
        String name = sanitizeName(f.getName());
        // 分片上传
        int total = bytes.length;
        int chunks = (total + MenuBgConstants.CHUNK_SIZE - 1) / MenuBgConstants.CHUNK_SIZE;
        for (int i = 0; i < chunks; i++) {
            int off = i * MenuBgConstants.CHUNK_SIZE;
            int cl = Math.min(MenuBgConstants.CHUNK_SIZE, total - off);
            byte[] slice = new byte[cl];
            System.arraycopy(bytes, off, slice, 0, cl);
            PacketDistributor.sendToServer(new MenuBgUploadPacket(name, i, i == chunks - 1, slice));
        }
        // 选中刚上传的图并刷新列表
        String finalName = name;
        this.minecraft.execute(() -> {
            this.selectedBg = finalName;
            PacketDistributor.sendToServer(new MenuBgListRequestPacket());
            showMsg(player, "message.business.menu.bg_uploaded", finalName);
        });
    }

    private static String sanitizeName(String raw) {
        String n = raw.replaceAll("[^A-Za-z0-9_\\-\\.]", "_");
        if (!n.toLowerCase().endsWith(".png")) n += ".png";
        return n;
    }

    private static void showMsg(LocalPlayer player, String key, String... args) {
        if (player != null) {
            player.displayClientMessage(Component.translatable(key, (Object[]) args), true);
        }
    }

    /**
     * 按当前勾选与档位实时计算总金额，口径与服务端 {@code PlayerOrderManager.computeTotalPrice} 一致。
     */
    private int computeTotal() {
        CompoundTag foodList = new CompoundTag();
        boolean any = false;
        for (Map.Entry<String, Integer> e : this.counts.entrySet()) {
            if (e.getValue() > 0) {
                foodList.putInt(e.getKey(), e.getValue());
                any = true;
            }
        }
        if (!any) return 0;
        int level = this.menuTag.getInt(PlayerOrderManager.M_MENU_LEVEL);
        return PlayerOrderManager.computeTotalPrice(foodList, level, this.timeTier, this.tipTier);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        if (this.settingsMode) {
            this.bgScroll = Math.max(0, this.bgScroll - (int) (delta * BG_ROW_H));
            return true;
        }
        this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int) (delta * ROW_H)));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private record Hit(String type, String id, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }
}
