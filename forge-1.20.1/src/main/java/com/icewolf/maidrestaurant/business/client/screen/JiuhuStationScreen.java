package com.icewolf.maidrestaurant.business.client.screen;

import com.icewolf.maidrestaurant.business.config.TakeoutConfig;
import com.icewolf.maidrestaurant.business.menu.JiuhuStationMenu;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public class JiuhuStationScreen extends AbstractContainerScreen<JiuhuStationMenu> {
    private static final ResourceLocation BACKGROUND =
            new ResourceLocation("maid_restaurant_business", "textures/gui/jiuhu_station_gui.png");
    private static final int SLOT_X0 = 20;   // bag rests one px right/down inside the fixed cubby
    private static final int SLOT_Y = 19;
    private static final int SLOT_PITCH = 30;
    private static final int SLOT_SIZE = 18;
    private static final int BAR_X_OFF = 0;   // 16px groove starts at slot left
    private static final int BAR_W = 16;
    private static final int BAR_Y = 40;
    private static final int BAR_H = 3;
    private static final int TXT_Y = 47;

    public JiuhuStationScreen(JiuhuStationMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        this.imageWidth = 176;
        this.imageHeight = 100;
        this.inventoryLabelY = -1000;
    }

    @Override
    protected void init() {
        super.init();
        this.titleLabelX = (this.imageWidth - this.font.width(this.title)) / 2;
        this.titleLabelY = 4;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(g);
        super.render(g, mouseX, mouseY, partialTick);

        for (int i = 0; i < 5; i++) {
            ItemStack stack = this.menu.getSlot(i).getItem();
            if (stack.isEmpty()) continue;
            int x = this.leftPos + SLOT_X0 + i * SLOT_PITCH;

            int rem = this.menu.getRemainingSeconds(i);
            int tot = this.menu.getTotalSeconds(i);
            int profit = this.menu.getActualProfit(i);

            float prog = 0f;
            if (tot > 0) {
                prog = 1f - (float) rem / (float) tot;
                if (prog < 0f) prog = 0f;
                if (prog > 1f) prog = 1f;
            } else if (rem <= 0) {
                prog = 1f;
            }
            int fw = (int) (prog * BAR_W);
            if (fw > 0) {
                int bx = x + BAR_X_OFF;
                g.fill(bx, this.topPos + BAR_Y, bx + fw, this.topPos + BAR_Y + BAR_H, 0xFFDC9C3C);
                g.fill(bx, this.topPos + BAR_Y, bx + fw, this.topPos + BAR_Y + 1, 0xFFF0C868);
            }

            // Potential earnings (the progress bar already conveys timing; no countdown text)
            String txt = "+" + profit;
            int tx = x + (16 - this.font.width(txt)) / 2;
            g.drawString(this.font, txt, tx, this.topPos + TXT_Y, 0x9A6818, false);
        }

        // Bottom band: actual delivery speed / fee after the station's own upgrade level
        int lvl = this.menu.getUpgradeLevel();
        int speed = TakeoutConfig.baseDeliverySpeed + lvl * TakeoutConfig.speedPerLevel;
        double actualFee = Math.max(TakeoutConfig.minFee, TakeoutConfig.baseFee - lvl * TakeoutConfig.feePerLevel);
        g.drawString(this.font, speed + "格/秒",
                this.leftPos + 25, this.topPos + 72, 0xF0E0B8, false);
        String fee = Math.round(actualFee * 100.0) + "%";
        g.drawString(this.font, fee, this.leftPos + 139, this.topPos + 72, 0xF0E0B8, false);

        // No "配送中 x/5" line: the number of bags on the shelf already shows what is delivering.

        this.renderTooltip(g, mouseX, mouseY);
    }

    @Override
    protected void renderBg(GuiGraphics g, float partialTick, int mouseX, int mouseY) {
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShaderTexture(0, BACKGROUND);
        // Explicit texture dimensions: the 7-arg blit assumes a 256x256 sheet, but our background
        // is exactly imageWidth x imageHeight. Without this the square slots get stretched into tall
        // vertical recesses (only the top-left fraction is sampled and scaled to the panel).
        g.blit(BACKGROUND, this.leftPos, this.topPos, 0.0f, 0.0f,
                this.imageWidth, this.imageHeight, this.imageWidth, this.imageHeight);
    }

    @Override
    protected void renderLabels(GuiGraphics g, int mouseX, int mouseY) {
        // No on-screen title ("酒狐速递站") and no player inventory label; the block and the
        // visible bags already identify the station.
    }
}
