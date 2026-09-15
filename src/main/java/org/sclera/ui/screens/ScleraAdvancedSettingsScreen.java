package org.sclera.ui.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.widgets.SettingButtonWidget;

/**
 * Advanced configuration screen for granular per-domain controls (Menus vs. Inventory,
 * and physical hand overrides).
 */
public class ScleraAdvancedSettingsScreen extends Screen {
    private final Screen parent;

    public ScleraAdvancedSettingsScreen(Screen parent) {
        super(Text.literal("Sclera - Advanced Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        int cx = this.width / 2;

        int cardW = 368;
        int cardX = cx - (cardW / 2); // cx - 184
        int card1Y = 36;
        int card1H = 66;

        int card2Y = card1Y + card1H + 8; // 110
        int card2H = 44;

        // --- Card 1: 2D Menus & Inventories (3 wide x 2 high) ---
        int innerW = cardW - 12; // 356px
        int colGap1 = 4;
        int btnW1 = (innerW - (colGap1 * 2)) / 3; // 116px
        int col1_0 = cardX + 6;
        int col1_1 = col1_0 + btnW1 + colGap1;
        int col1_2 = col1_1 + btnW1 + colGap1;
        int row1_0 = card1Y + 18;
        int row1_1 = row1_0 + 23;

        // Row 1 (Menus)
        this.addDrawableChild(new SettingButtonWidget(
            col1_0, row1_0, btnW1, 20,
            "Gaze in Menus",
            () -> manager.isGazeInMenus() ? "§dOn" : "§7Off",
            btn -> manager.setGazeInMenus(!manager.isGazeInMenus()),
            Tooltip.of(Text.literal("Look directly at buttons in pause, mod, and options menus."))
        ));

        this.addDrawableChild(new SettingButtonWidget(
            col1_1, row1_0, btnW1, 20,
            "Hands in Menus",
            () -> manager.isHandsInMenus() ? "§dOn" : "§7Off",
            btn -> manager.setHandsInMenus(!manager.isHandsInMenus()),
            Tooltip.of(Text.literal("Aim at menu buttons using your hands."))
        ));

        this.addDrawableChild(new SettingButtonWidget(
            col1_2, row1_0, btnW1, 20,
            "Blink in Menus",
            () -> manager.isBlinkInMenus() ? "§dOn" : "§7Off",
            btn -> manager.setBlinkInMenus(!manager.isBlinkInMenus()),
            Tooltip.of(Text.literal("Quick blink triggers a left click in pause and options menus."))
        ));

        // Row 2 (Inventory)
        this.addDrawableChild(new SettingButtonWidget(
            col1_0, row1_1, btnW1, 20,
            "Gaze in Inv.",
            () -> manager.isGazeInInventory() ? "§dOn" : "§7Off",
            btn -> manager.setGazeInInventory(!manager.isGazeInInventory()),
            Tooltip.of(Text.literal("Magnetically snaps gaze to item slots in chests and crafting."))
        ));

        this.addDrawableChild(new SettingButtonWidget(
            col1_1, row1_1, btnW1, 20,
            "Hands in Inv.",
            () -> manager.isHandsInInventory() ? "§dOn" : "§7Off",
            btn -> manager.setHandsInInventory(!manager.isHandsInInventory()),
            Tooltip.of(Text.literal("Aim at inventory slots using your hands."))
        ));

        this.addDrawableChild(new SettingButtonWidget(
            col1_2, row1_1, btnW1, 20,
            "Blink in Inv.",
            () -> manager.isBlinkInInventory() ? "§dOn" : "§7Off",
            btn -> manager.setBlinkInInventory(!manager.isBlinkInInventory()),
            Tooltip.of(Text.literal("Quick blink picks up and places items in containers."))
        ));

        // --- Card 2: 3D World & Hand Controls (2 wide x 1 high) ---
        int colGap2 = 6;
        int btnW2 = (innerW - colGap2) / 2; // 175px
        int col2_0 = cardX + 6;
        int col2_1 = col2_0 + btnW2 + colGap2;
        int row2_0 = card2Y + 18;

        this.addDrawableChild(new SettingButtonWidget(
            col2_0, row2_0, btnW2, 20,
            "Gaze in Blocks",
            () -> manager.isGazeInWorld() ? "§bOn" : "§7Off",
            btn -> manager.setGazeInWorld(!manager.isGazeInWorld()),
            Tooltip.of(Text.literal("Highlight and mine blocks where your gaze is focused."))
        ));

        this.addDrawableChild(new SettingButtonWidget(
            col2_1, row2_0, btnW2, 20,
            "Hands in World",
            () -> manager.isHandsInWorld() ? "§bOn" : "§7Off",
            btn -> manager.setHandsInWorld(!manager.isHandsInWorld()),
            Tooltip.of(Text.literal("Use VR controller hands to aim and swing in the 3D world."))
        ));

        // Footer: Back Button
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            btn -> {
                if (this.client != null) {
                    this.client.setScreen(this.parent);
                }
            }
        )
        .dimensions(cx - 60, card2Y + card2H + 12, 120, 20)
        .tooltip(Tooltip.of(Text.literal("Return to Main Settings.")))
        .build());
    }

    private void drawCard(DrawContext context, int x, int y, int w, int h, int bgColor, int borderColor) {
        context.fill(x, y, x + w, y + h, bgColor);
        context.fill(x, y, x + w, y + 1, borderColor);
        context.fill(x, y + h - 1, x + w, y + h, borderColor);
        context.fill(x, y + 1, x + 1, y + h, borderColor);
        context.fill(x + w - 1, y, x + w, y + h, borderColor);
        context.fill(x + 4, y + 16, x + w - 4, y + 17, borderColor);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);

        int cx = this.width / 2;
        int cardW = 368;
        int cardX = cx - (cardW / 2);
        int card1Y = 36;
        int card1H = 66;
        int card2Y = card1Y + card1H + 8;
        int card2H = 44;

        // Title and clear subtitle
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e§lSclera §7• §fAdvanced Settings"), cx, 8, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§7Independent controls for menus, inventory, and hands"), cx, 20, 0xAAAAAA);

        // Card 1: 2D Menus & Inventories (3 wide x 2 high)
        drawCard(context, cardX, card1Y, cardW, card1H, 0xDD181822, 0xFF3E3E4C);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§d§n2D Menus & Inventories"), cx, card1Y + 5, 0xD580FF);

        // Card 2: 3D World & Hand Controls (2 wide x 1 high)
        drawCard(context, cardX, card2Y, cardW, card2H, 0xDD181822, 0xFF3E3E4C);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§b§n3D World & Hand Controls"), cx, card2Y + 5, 0x80D8FF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
