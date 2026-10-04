package com.icewolf.maidrestaurant.business.client.screen;

import cn.breezeth.ordertocook.core.ModConstants;
import com.icewolf.maidrestaurant.business.core.PlayerOrderManager;
import com.icewolf.maidrestaurant.business.network.PlayerOrderSubmitPacket;
import com.icewolf.maidrestaurant.business.network.MenuRenamePacket;
import com.icewolf.maidrestaurant.business.network.ModMessages;
import com.icewolf.maidrestaurant.business.core.OtcCompat;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/** 饭店菜单点单界面：勾选菜品与数量（一次最多 6 种、每种 6 个），选择配送时限与小费档位，提交后发网络包。 */
public class RestaurantOrderScreen extends Screen {
    private static final int PANEL_W = 220;
    private static final int PANEL_H = 248;
    private static final int ROW_H = 22;
    private static final int LIST_H = 124;
    private static final int SET_W = 180;
    private static final int SET_H = 96;

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

    public RestaurantOrderScreen(ItemStack menu) {
        super(Component.translatable("screen.business.restaurant_order.title"));
        this.menuTag = menu.hasTag() ? menu.getTag().copy() : new CompoundTag();
        CompoundTag foods = this.menuTag.getCompound(PlayerOrderManager.M_FOODS);
        this.ids.addAll(foods.getAllKeys());
        for (String id : this.ids) {
            this.counts.put(id, 0);
        }
        String customTitle = this.menuTag.getString(PlayerOrderManager.M_MENU_TITLE);
        this.displayTitle = customTitle.isBlank()
                ? Component.translatable("screen.business.restaurant_order.title")
                : Component.literal(customTitle);
    }

    @Override
    protected void init() {
        this.px = (this.width - PANEL_W) / 2;
        this.py = (this.height - PANEL_H) / 2;
        this.maxScroll = Math.max(0, this.ids.size() * ROW_H - LIST_H);

        int spx = this.px + (PANEL_W - SET_W) / 2;
        int spy = this.py + (PANEL_H - SET_H) / 2;
        this.titleBox = new EditBox(this.font, spx + 12, spy + 32, SET_W - 24, 18,
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
        this.renderBackground(graphics);
        this.hits.clear();

        // 设置模式：只渲染全屏遮罩 + 设置子面板，不再渲染点单主界面，避免两层 GUI 重叠
        if (this.settingsMode) {
            renderSettingsOnly(graphics, mouseX, mouseY, partialTick);
            super.render(graphics, mouseX, mouseY, partialTick);
            return;
        }

        graphics.fill(this.px, this.py, this.px + PANEL_W, this.py + PANEL_H, 0xFF1A1410);
        graphics.renderOutline(this.px, this.py, PANEL_W, PANEL_H, 0xFFB58A4A);
        graphics.drawCenteredString(this.font, this.displayTitle, this.px + PANEL_W / 2, this.py + 10, 0xFFE8D9B0);

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
            graphics.drawString(this.font, item.getDescription(), lx + 26, rowY + 7, 0xFFEDE2C8);

            int minusX = lx + lw - 66;
            int plusX = lx + lw - 26;
            int btnY = rowY + 3;
            graphics.fill(minusX, btnY, minusX + 16, btnY + 16, 0xFF3A2F22);
            graphics.fill(plusX, btnY, plusX + 16, btnY + 16, 0xFF3A2F22);
            graphics.drawCenteredString(this.font, Component.literal("-"), minusX + 8, btnY + 4, 0xFFE8D9B0);
            graphics.drawCenteredString(this.font, Component.literal("+"), plusX + 8, btnY + 4, 0xFFE8D9B0);
            graphics.drawCenteredString(this.font,
                    Component.literal(String.valueOf(this.counts.getOrDefault(id, 0))),
                    lx + lw - 41, btnY + 5, 0xFFFFFF);
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
                this.px + 12, timeLabelY + 4, 0xFFB9A882);
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
                this.px + 12, tipLabelY + 4, 0xFFB9A882);
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
                this.px + 12, this.py + 206, 0xFFB9A882);

        Component totalLine = Component.translatable("screen.business.restaurant_order.total", computeTotal());
        graphics.drawString(this.font, totalLine,
                this.px + PANEL_W - 12 - this.font.width(totalLine), this.py + 206, 0xFFE8C87A);

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

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    /** 设置模式专用：只渲染全屏遮罩 + 设置子面板（自定义点单名称），不渲染点单主界面。 */
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

        int btnY = spy + SET_H - 28;
        int okX = spx + 12;
        int cancelX = spx + SET_W - 12 - 70;
        graphics.fill(okX, btnY, okX + 70, btnY + 20, 0xFF6E4B2A);
        graphics.renderOutline(okX, btnY, 70, 20, 0xFFD9B36C);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.settings_ok"),
                okX + 35, btnY + 6, 0xFFF4E6C2);
        graphics.fill(cancelX, btnY, cancelX + 70, btnY + 20, 0xFF3A2F22);
        graphics.renderOutline(cancelX, btnY, 70, 20, 0xFFB58A4A);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.settings_cancel"),
                cancelX + 35, btnY + 6, 0xFFE8D9B0);
        this.settingsHits.add(new Hit("rename_ok", "", okX, btnY, 70, 20));
        this.settingsHits.add(new Hit("rename_cancel", "", cancelX, btnY, 70, 20));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.settingsMode) {
            this.titleBox.mouseClicked(mouseX, mouseY, button);
            for (Hit hit : this.settingsHits) {
                if (hit.contains(mouseX, mouseY)) {
                    switch (hit.type) {
                        case "rename_ok" -> applyRename();
                        case "rename_cancel" -> closeSettings();
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
        ModMessages.INSTANCE.sendToServer(
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
            if (keyCode == 257 || keyCode == 335) { applyRename(); return true; }
            return this.titleBox.keyPressed(keyCode, scanCode, modifiers);
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    private void openSettings() {
        this.settingsMode = true;
        this.titleBox.setValue(this.menuTag.getString(PlayerOrderManager.M_MENU_TITLE));
        this.titleBox.setFocused(true);
    }

    private void closeSettings() {
        this.settingsMode = false;
        this.titleBox.setFocused(false);
    }

    private void applyRename() {
        String value = this.titleBox.getValue().trim();
        if (value.isEmpty()) {
            this.menuTag.remove(PlayerOrderManager.M_MENU_TITLE);
            this.displayTitle = Component.translatable("screen.business.restaurant_order.title");
        } else {
            this.menuTag.putString(PlayerOrderManager.M_MENU_TITLE, value);
            this.displayTitle = Component.literal(value);
        }
        ModMessages.INSTANCE.sendToServer(new MenuRenamePacket(value));
        closeSettings();
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
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (this.settingsMode) return true;
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
