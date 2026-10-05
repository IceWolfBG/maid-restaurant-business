package com.icewolf.maidrestaurant.business.client.screen;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * 游戏内文件选择界面：直接在 MC 里浏览本地磁盘、挑一张 PNG 作为菜单背景图。
 * 故意不依赖 AWT/Swing（JFileChooser），因为部分启动器带 -Djava.awt.headless=true，
 * 一旦 GraphicsEnvironment 在启动阶段被缓存为 headless，Swing 弹窗必崩且无法挽回。
 */
public class MenuBgFilePickerScreen extends Screen {
    private final Screen returnTo;
    private final Consumer<File> onPick;
    private File dir;
    private final List<Entry> entries = new ArrayList<>();
    private final List<Hit> hits = new ArrayList<>();
    private int scroll = 0;
    private int maxScroll = 0;
    private int px, py, pw, ph;

    private record Entry(boolean dir, String name, File file) {}
    private record Hit(String type, File file, int x, int y, int w, int h) {
        boolean contains(double mx, double my) {
            return mx >= x && mx <= x + w && my >= y && my <= y + h;
        }
    }

    public MenuBgFilePickerScreen(Screen returnTo, Consumer<File> onPick) {
        super(Component.translatable("screen.business.restaurant_order.bg_picker_title"));
        this.returnTo = returnTo;
        this.onPick = onPick;
        File home = new File(System.getProperty("user.home", "."));
        File pics = new File(home, "Pictures");
        this.dir = pics.isDirectory() ? pics : home;
    }

    @Override
    protected void init() {
        this.pw = 320;
        this.ph = 240;
        this.px = (this.width - pw) / 2;
        this.py = (this.height - ph) / 2;
        refresh();
    }

    private void refresh() {
        entries.clear();
        File parent = dir.getParentFile();
        if (parent != null) entries.add(new Entry(true, ".. (上级目录)", parent));
        File[] files = dir.listFiles();
        if (files != null) {
            List<File> dirs = new ArrayList<>();
            List<File> pngs = new ArrayList<>();
            for (File f : files) {
                if (f.isDirectory()) dirs.add(f);
                else if (f.isFile() && f.getName().toLowerCase().endsWith(".png")) pngs.add(f);
            }
            dirs.sort(Comparator.comparing(File::getName));
            pngs.sort(Comparator.comparing(File::getName));
            for (File d : dirs) entries.add(new Entry(true, d.getName(), d));
            for (File p : pngs) entries.add(new Entry(false, p.getName(), p));
        }
        int listH = ph - 70;
        maxScroll = Math.max(0, entries.size() * 20 - listH);
        scroll = 0;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        this.hits.clear();
        graphics.fill(this.px, this.py, this.px + this.pw, this.py + this.ph, 0xFF241B13);
        graphics.renderOutline(this.px, this.py, this.pw, this.ph, 0xFFB58A4A);
        graphics.drawCenteredString(this.font, this.title, this.px + this.pw / 2, this.py + 8, 0xFFE8D9B0);

        String path = this.dir.getAbsolutePath();
        if (this.font.width(path) > this.pw - 20) {
            path = this.font.plainSubstrByWidth(path, this.pw - 20) + "…";
        }
        graphics.drawString(this.font, path, this.px + 10, this.py + 26, 0xFFB9A882);

        int listX = this.px + 10;
        int listY = this.py + 40;
        int listW = this.pw - 20;
        int listH = this.ph - 70;
        int rowH = 20;
        graphics.enableScissor(listX, listY, listX + listW, listY + listH);
        for (int i = 0; i < this.entries.size(); i++) {
            int yy = listY + i * rowH - this.scroll;
            if (yy + rowH < listY || yy > listY + listH) continue;
            Entry e = this.entries.get(i);
            int color = e.dir ? 0xFF9FC0E8 : 0xFFEDE2C8;
            String label = (e.dir ? "[目录] " : "[图片] ") + e.name;
            graphics.drawString(this.font, label, listX + 4, yy + 5, color);
            this.hits.add(new Hit("", e.file, listX, yy, listW, rowH));
        }
        graphics.disableScissor();

        // 取消按钮
        int by = this.py + this.ph - 26;
        int cx = this.px + this.pw - 90;
        graphics.fill(cx, by, cx + 80, by + 20, 0xFF3A2F22);
        graphics.renderOutline(cx, by, 80, 20, 0xFFB58A4A);
        graphics.drawCenteredString(this.font,
                Component.translatable("screen.business.restaurant_order.settings_cancel"),
                cx + 40, by + 6, 0xFFE8D9B0);
        this.hits.add(new Hit("cancel", null, cx, by, 80, 20));

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        for (Hit h : this.hits) {
            if (h.contains(mouseX, mouseY)) {
                if (h.type.equals("cancel")) {
                    this.minecraft.setScreen(this.returnTo);
                    return true;
                }
                File f = h.file;
                if (f.isDirectory()) {
                    this.dir = f;
                    this.refresh();
                } else {
                    this.minecraft.setScreen(this.returnTo);
                    this.onPick.accept(f);
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double delta) {
        this.scroll = Math.max(0, Math.min(this.maxScroll, this.scroll - (int) (delta * 20)));
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // ESC
            this.minecraft.setScreen(this.returnTo);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
