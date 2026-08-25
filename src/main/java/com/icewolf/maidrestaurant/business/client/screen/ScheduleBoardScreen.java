package com.icewolf.maidrestaurant.business.client.screen;

import com.icewolf.maidrestaurant.business.menu.ScheduleBoardMenu;
import com.icewolf.maidrestaurant.business.network.ModMessages;
import com.icewolf.maidrestaurant.business.network.ScheduleBoardUpdatePacket;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;

public class ScheduleBoardScreen extends AbstractContainerScreen<ScheduleBoardMenu> {
    private static final int GUI_WIDTH = 220;
    private static final int GUI_HEIGHT = 210;
    private static final ResourceLocation BG_TEXTURE = new ResourceLocation("maid_restaurant_business", "textures/gui/schedule_board_bg.png");

    private Button autoEnabledBtn;
    private Button autoDeliveryBtn;
    private Button autoPackagingBtn;
    private Button autoCookingBtn;
    private Button autoPrepBtn;
    private Button autoCollectBtn;
    private Button autoWashBtn;
    private Button minPlatesMinusBtn;
    private Button minPlatesPlusBtn;
    private Button workScheduleBtn;
    private Button bellEnabledBtn;

    public ScheduleBoardScreen(ScheduleBoardMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        this.imageWidth = GUI_WIDTH;
        this.imageHeight = GUI_HEIGHT;
    }

    protected void init() {
        super.init();
        int x = this.leftPos;
        int y = this.topPos;
        int centerX = x + (GUI_WIDTH - 140) / 2; // 居中按钮的x坐标

        // 营业时间按钮（点击循环切换白天/黑天/全天/歇业）
        workScheduleBtn = addRenderableWidget(Button.builder(Component.literal("营业时间: " + menu.getScheduleName()), btn -> {
            int newVal = (menu.getWorkSchedule() + 1) % 4;
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_WORK_SCHEDULE, newVal));
            btn.setMessage(Component.literal("营业时间: " + getScheduleName(newVal)));
        }).bounds(centerX, y + 26, 140, 20).build());
        
        // 总自动化开关
        autoEnabledBtn = addRenderableWidget(Button.builder(Component.literal(getToggleText(menu.isAutoEnabled())), btn -> {
            boolean newVal = !menu.isAutoEnabled();
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_AUTO_ENABLED, newVal));
            btn.setMessage(Component.literal(getToggleText(newVal)));
        }).bounds(centerX, y + 52, 140, 20).build());

        // 自动配送
        autoDeliveryBtn = addRenderableWidget(Button.builder(Component.literal("配送: " + getOnOff(menu.isAutoDelivery())), btn -> {
            boolean newVal = !menu.isAutoDelivery();
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_AUTO_DELIVERY, newVal));
            btn.setMessage(Component.literal("配送: " + getOnOff(newVal)));
        }).bounds(x + 15, y + 80, 85, 20).build());

        // 自动装盘/打包
        autoPackagingBtn = addRenderableWidget(Button.builder(Component.literal("装盘: " + getOnOff(menu.isAutoPackaging())), btn -> {
            boolean newVal = !menu.isAutoPackaging();
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_AUTO_PACKAGING, newVal));
            btn.setMessage(Component.literal("装盘: " + getOnOff(newVal)));
        }).bounds(x + 120, y + 80, 85, 20).build());

        // 自动烹饪
        autoCookingBtn = addRenderableWidget(Button.builder(Component.literal("烹饪: " + getOnOff(menu.isAutoCooking())), btn -> {
            boolean newVal = !menu.isAutoCooking();
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_AUTO_COOKING, newVal));
            btn.setMessage(Component.literal("烹饪: " + getOnOff(newVal)));
        }).bounds(x + 15, y + 108, 85, 20).build());

        // 自动备菜
        autoPrepBtn = addRenderableWidget(Button.builder(Component.literal("备菜: " + getOnOff(menu.isAutoPrep())), btn -> {
            boolean newVal = !menu.isAutoPrep();
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_AUTO_PREP, newVal));
            btn.setMessage(Component.literal("备菜: " + getOnOff(newVal)));
        }).bounds(x + 120, y + 108, 85, 20).build());

        // 自动收盘子
        autoCollectBtn = addRenderableWidget(Button.builder(Component.literal("收盘: " + getOnOff(menu.isAutoCollect())), btn -> {
            boolean newVal = !menu.isAutoCollect();
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_AUTO_COLLECT, newVal));
            btn.setMessage(Component.literal("收盘: " + getOnOff(newVal)));
        }).bounds(x + 15, y + 136, 85, 20).build());

        // 自动洗碗
        autoWashBtn = addRenderableWidget(Button.builder(Component.literal("洗碗: " + getOnOff(menu.isAutoWash())), btn -> {
            boolean newVal = !menu.isAutoWash();
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_AUTO_WASH, newVal));
            btn.setMessage(Component.literal("洗碗: " + getOnOff(newVal)));
        }).bounds(x + 120, y + 136, 85, 20).build());

        // 洗碗阈值区域
        // 铃铛声开关
        bellEnabledBtn = addRenderableWidget(Button.builder(Component.literal("铃声: " + getOnOff(menu.isBellEnabled())), btn -> {
            boolean newVal = !menu.isBellEnabled();
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_BELL_ENABLED, newVal));
            btn.setMessage(Component.literal("铃声: " + getOnOff(newVal)));
        }).bounds(x + 15, y + 172, 55, 20).build());
        
        // 洗碗阈值减号
        minPlatesMinusBtn = addRenderableWidget(Button.builder(Component.literal("§c-"), btn -> {
            int newVal = Math.max(1, menu.getMinPlatesToWash() - 1);
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_MIN_PLATES, newVal));
        }).bounds(x + 85, y + 172, 20, 20).build());

        // 洗碗阈值加号
        minPlatesPlusBtn = addRenderableWidget(Button.builder(Component.literal("§a+"), btn -> {
            int newVal = Math.min(10, menu.getMinPlatesToWash() + 1);
            ModMessages.INSTANCE.sendToServer(new ScheduleBoardUpdatePacket(menu.getBlockPos(), ScheduleBoardUpdatePacket.TYPE_MIN_PLATES, newVal));
        }).bounds(x + GUI_WIDTH - 105, y + 172, 20, 20).build());
    }

    private String getToggleText(boolean enabled) {
        return enabled ? "总开关: 开" : "总开关: 关";
    }

    private String getOnOff(boolean on) {
        return on ? "§a开" : "§c关";
    }
    
    private String getScheduleName(int schedule) {
        return switch (schedule) {
            case 0 -> "白天";
            case 1 -> "黑天";
            case 2 -> "全天";
            case 3 -> "歇业";
            default -> "全天";
        };
    }

    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);
    }

    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);

        int x = this.leftPos;
        int y = this.topPos;

        // 绘制GUI背景图（深色橡木边框+米白羊皮纸+顶部金色装饰）
        // 使用缩放版本的blit，将整个256x256纹理缩放到GUI_WIDTH x GUI_HEIGHT
        RenderSystem.setShaderTexture(0, BG_TEXTURE);
        graphics.blit(BG_TEXTURE, x, y, GUI_WIDTH, GUI_HEIGHT, 0, 0, 256, 256, 256, 256);

        // 标题文字
        graphics.drawCenteredString(this.font, Component.literal("§6§l排班表"), x + GUI_WIDTH / 2, y + 11, 0xFF4A2C0A);

        // 洗碗阈值区域背景
        graphics.fill(x + 15, y + 162, x + GUI_WIDTH - 15, y + 198, 0x80E8D4B8);
        // 洗碗阈值标签
        graphics.drawCenteredString(this.font, Component.literal("§6洗碗阈值"), x + GUI_WIDTH / 2, y + 165, 0xFF4A2C0A);
        // 洗碗阈值数值（显示在加减按钮中间）
        graphics.drawCenteredString(this.font, Component.literal("§l§e" + menu.getMinPlatesToWash() + " §r§7盘"), x + GUI_WIDTH / 2 + 5, y + 177, 0xFF4A2C0A);
    }

    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 不渲染默认标签
    }
}
