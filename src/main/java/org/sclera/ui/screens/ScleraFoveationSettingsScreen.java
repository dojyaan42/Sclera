package org.sclera.ui.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.sclera.render.FoveationConfig;
import org.sclera.render.FoveationDiagnostics;
import org.sclera.ui.widgets.SettingButtonWidget;

/**
 * Configuration and real-time diagnostic screen for Sclera Foveated Rendering.
 */
public class ScleraFoveationSettingsScreen extends Screen {
    private final Screen parent;

    public ScleraFoveationSettingsScreen(Screen parent) {
        super(Text.literal("Sclera - Foveated Rendering"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        org.sclera.render.FoveationProbe.probe();
        FoveationConfig config = FoveationConfig.getInstance();
        int cx = this.width / 2;
        int buttonW = getButtonWidth();
        int cardW = buttonW + 12;
        int cardX = cx - (cardW / 2);
        int startY = 58;
        int rowH = 26;

        // Toggle: Enable Foveated Rendering
        this.addDrawableChild(new SettingButtonWidget(
                cardX + 6, startY, buttonW, 20,
                "Foveated Rendering",
                () -> config.isEnabled() ? "§aEnabled" : "§7Disabled",
                btn -> config.setEnabled(!config.isEnabled()),
                Tooltip.of(Text.literal("Toggles real-time eye-tracked foveated rendering."))
        ));

        // The main preset keeps the eye area and peripheral quality in sync.
        this.addDrawableChild(new SettingButtonWidget(
                cardX + 6, startY + rowH, buttonW, 20,
                "Preset",
                () -> getCombinedPresetLabel(config),
                btn -> setNextPreset(config),
                Tooltip.of(Text.literal("Choose a balanced combination of foveal area size and peripheral texture quality."))
        ));

        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Advanced Options..."),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(new ScleraFoveationAdvancedSettingsScreen(this));
                    }
                }
        )
        .dimensions(cardX + 6, startY + (rowH * 2), buttonW, 20)
        .tooltip(Tooltip.of(Text.literal("Adjust the foveal area, peripheral quality, blur, and debug view.")))
        .build());

        // Back button
        this.addDrawableChild(ButtonWidget.builder(
                Text.literal("Back"),
                btn -> {
                    if (this.client != null) {
                        this.client.setScreen(this.parent);
                    }
                }
        )
        .dimensions(cx - 60, 144, 120, 20)
        .build());
    }

    private static String getCombinedPresetLabel(FoveationConfig config) {
        return switch (config.getPreset()) {
            case QUALITY -> config.getPeripheralRate() == FoveationConfig.PeripheralRate.RATE_1X2
                    ? "§aQuality" : "§7Custom";
            case BALANCED -> config.getPeripheralRate() == FoveationConfig.PeripheralRate.RATE_2X2
                    ? "§eBalanced" : "§7Custom";
            case PERFORMANCE -> config.getPeripheralRate() == FoveationConfig.PeripheralRate.RATE_4X4
                    ? "§cPerformance" : "§7Custom";
        };
    }

    private static void setNextPreset(FoveationConfig config) {
        FoveationConfig.Preset next = config.getPreset().next();
        config.setPreset(next);
        config.setPeripheralRate(switch (next) {
            case QUALITY -> FoveationConfig.PeripheralRate.RATE_1X2;
            case BALANCED -> FoveationConfig.PeripheralRate.RATE_2X2;
            case PERFORMANCE -> FoveationConfig.PeripheralRate.RATE_4X4;
        });
    }

    private int getButtonWidth() {
        int longestSetting = Math.max(
                textRenderer.getWidth("Foveated Rendering") + textRenderer.getWidth("Disabled"),
                textRenderer.getWidth("Preset") + textRenderer.getWidth("Performance"));
        return Math.max(longestSetting + 24, textRenderer.getWidth("Advanced Options...") + 24);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        this.renderBackground(context);
        FoveationDiagnostics diag = FoveationDiagnostics.getInstance();
        int cx = this.width / 2;

        int cardW = getButtonWidth() + 12;
        int cardX = cx - cardW / 2;
        int cardY = 36;
        int cardH = 100;
        context.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xCC181822);
        context.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFF3E3E4C);
        context.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0xFF3E3E4C);
        context.fill(cardX, cardY + 1, cardX + 1, cardY + cardH - 1, 0xFF3E3E4C);
        context.fill(cardX + cardW - 1, cardY + 1, cardX + cardW, cardY + cardH - 1, 0xFF3E3E4C);
        context.fill(cardX + 4, cardY + 16, cardX + cardW - 4, cardY + 17, 0xFF3E3E4C);
        context.drawCenteredTextWithShadow(this.textRenderer, "§b§lFoveated Rendering", cx, 12, 0xFFFFFF);
        context.drawCenteredTextWithShadow(this.textRenderer, "§7Keep the center clear while reducing detail around it", cx, 24, 0xAAAAAA);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§b§nRendering Options"), cx, cardY + 4, 0x80D8FF);

        int boxY = this.height - 40;
        int boxW = 320;
        int boxX = cx - (boxW / 2);

        context.fill(boxX, boxY, boxX + boxW, boxY + 32, 0xCC181822);
        context.fill(boxX, boxY, boxX + boxW, boxY + 1, 0xFF3E3E4C);

        String reasonInfo = "§7Status: §f" + truncate(diag.getStatusReason(), 42);
        context.drawText(this.textRenderer, Text.literal(reasonInfo), boxX + 10, boxY + 4, 0xCCCCCC, false);

        String stats = String.format("§7Eye passes: §f%d §7| VRS: §a%d §7| Saccades: §e%d",
                diag.getFramesRendered(), diag.getFoveatedFrames(), diag.getSaccadeEyePasses());
        context.drawText(this.textRenderer, Text.literal(stats), boxX + 10, boxY + 18, 0xAAAAAA, false);
        super.render(context, mouseX, mouseY, delta);
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "Unknown";
        return s.length() > maxLen ? s.substring(0, maxLen) + "..." : s;
    }
}
