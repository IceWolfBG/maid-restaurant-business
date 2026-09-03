package com.icewolf.maidrestaurant.business.client.screen;

import com.icewolf.maidrestaurant.business.block.entity.JiuhuStationBlockEntity;
import com.icewolf.maidrestaurant.business.menu.JiuhuStationMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class JiuhuStationScreen extends AbstractContainerScreen<JiuhuStationMenu> {
    private static final ResourceLocation BACKGROUND = ResourceLocation.fromNamespaceAndPath("maid_restaurant_business", "textures/gui/jiuhu_station_gui.png");

    // 背景图坐标（与背景图完全对应）
    private static final int TITLE_Y = 10; // 标题文字Y坐标（标题区域y=8，高度12，文字居中）
    private static final int SLOT_START_X = 31;
    private static final int SLOT_START_Y = 37;
    private static final int SLOT_SIZE = 20;
    private static final int SLOT_GAP = 6;
    private static final int PROGRESS_BAR_Y = 58; // 进度条比实际格子偏左上2格
    private static final int PROGRESS_BAR_HEIGHT = 5;
    private static final int INFO_AREA_Y = 74;

    public JiuhuStationScreen(JiuhuStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 110;
        this.inventoryLabelY = -1000; // 玩家背包标题移到GUI外
    }

    @Override
    protected void init() {
        super.init();
        // 标题居中
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = TITLE_Y;
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        this.renderTooltip(graphics, mouseX, mouseY);

        // 渲染配送进度条和文字信息
        for (int i = 0; i < JiuhuStationBlockEntity.SLOT_COUNT; i++) {
            ItemStack stack = this.menu.getSlot(i).getItem();
            if (!stack.isEmpty()) {
                int remaining = this.menu.getRemainingSeconds(i);
                int total = this.menu.getTotalSeconds(i);
                int profit = this.menu.getActualProfit(i);

                int slotX = this.leftPos + SLOT_START_X + i * (SLOT_SIZE + SLOT_GAP);
                int progressY = this.topPos + PROGRESS_BAR_Y;

                // 计算进度百分比
                float progress = 0f;
                if (total > 0) {
                    progress = 1.0f - ((float) remaining / (float) total);
                    if (remaining <= 0) progress = 1.0f;
                }

                // 绘制进度条填充
                if (progress > 0) {
                    int fillWidth = (int) (progress * (SLOT_SIZE - 2));
                    if (fillWidth > 0) {
                        int fillX = slotX - 1;
                        int fillY = progressY + 1;
                        int fillH = PROGRESS_BAR_HEIGHT - 2;
                        graphics.fill(fillX, fillY, fillX + fillWidth, fillY + fillH, 0xFFDC9C3C);
                        graphics.fill(fillX, fillY, fillX + fillWidth, fillY + 1, 0xFFE8B060);
                    }
                }

                // 进度条下方显示收益
                String profitText = "+" + profit;
                int profitX = slotX + (SLOT_SIZE - this.font.width(profitText)) / 2;
                int profitY = progressY + PROGRESS_BAR_HEIGHT + 1;
                graphics.drawString(this.font, profitText, profitX, profitY, 0xFFFF00, true);
            }
        }

        // 信息区域文字
        int infoY = this.topPos + INFO_AREA_Y;
        String speedText = "速度: 2格/秒";
        graphics.drawString(this.font, speedText, this.leftPos + 32, infoY + 5, 0x4A321E, false);
        String feeText = "手续费: 40%";
        graphics.drawString(this.font, feeText, this.leftPos + this.imageWidth / 2 + 24, infoY + 5, 0x4A321E, false);
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, BACKGROUND);
        int x = this.leftPos;
        int y = this.topPos;
        graphics.blit(BACKGROUND, x, y, 0, 0, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        // 标题
        graphics.drawString(this.font, this.title, this.titleLabelX, this.titleLabelY, 0x4A321E, false);
    }
}
