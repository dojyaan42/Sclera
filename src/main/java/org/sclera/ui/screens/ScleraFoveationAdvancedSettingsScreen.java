package org.sclera.ui.screens;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.sclera.render.FoveationConfig;
import org.sclera.ui.widgets.SettingButtonWidget;

/** Detailed controls for the eye area and peripheral rendering quality. */
public class ScleraFoveationAdvancedSettingsScreen extends Screen {
    private final Screen parent;

    public ScleraFoveationAdvancedSettingsScreen(Screen parent) {
        super(Text.literal("Sclera - Advanced Foveated Rendering"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        FoveationConfig config = FoveationConfig.getInstance();
        int cx = this.width / 2;
        int buttonW = getButtonWidth();
        int cardW = buttonW + 12;
        int cardX = cx - cardW / 2;
        int x = cardX + 6;
        int y = 60;
        int row = 26;

        addDrawableChild(new SettingButtonWidget(
                x, y, buttonW, 20,
                "Area Size",
                () -> switch (config.getPreset()) {
                    case QUALITY -> "§aBig";
                    case BALANCED -> "§eMedium";
                    case PERFORMANCE -> "§cSmall";
                },
                btn -> config.setPreset(config.getPreset().next()),
                Tooltip.of(Text.literal("Set the sharp area around your gaze."))
        ));

        addDrawableChild(new SettingButtonWidget(
                x, y + row, buttonW, 20,
                "Texture Quality",
                () -> switch (config.getPeripheralRate()) {
                    case RATE_1X2 -> "§aHigh";
                    case RATE_2X2 -> "§eMedium";
                    case RATE_4X4 -> "§cLow";
                },
                btn -> config.setPeripheralRate(config.getPeripheralRate().next()),
                Tooltip.of(Text.literal("Choose detail level outside the sharp area. Lower quality can improve performance."))
        ));

        addDrawableChild(new SettingButtonWidget(
                x, y + row * 2, buttonW, 20,
                "Peripheral Blur",
                () -> config.isPeripheralSmoothingEnabled() ? "§aOn" : "§7Off",
                btn -> config.setPeripheralSmoothing(!config.isPeripheralSmoothingEnabled()),
                Tooltip.of(Text.literal("Softens the outer view to reduce visible pixel shimmer."))
        ));

        addDrawableChild(new SettingButtonWidget(
                x, y + row * 3, buttonW, 20,
                "Debug",
                () -> config.isTestCutoutMode() ? "§aOn" : "§7Off",
                btn -> config.setTestCutoutMode(!config.isTestCutoutMode()),
                Tooltip.of(Text.literal("Hides the outer view to show the area around your gaze."))
        ));

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), btn -> {
            if (this.client != null) this.client.setScreen(this.parent);
        }).dimensions(cx - 60, 172, 120, 20).build());
    }

    private int getButtonWidth() {
        int textureQuality = textRenderer.getWidth("Texture Quality") + textRenderer.getWidth("Medium");
        int peripheralBlur = textRenderer.getWidth("Peripheral Blur") + textRenderer.getWidth("Off");
        return Math.max(textureQuality, peripheralBlur) + 24;
    }

    @Override
    public void render(net.minecraft.client.gui.DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int cx = width / 2;
        int cardW = getButtonWidth() + 12;
        int cardX = cx - cardW / 2;
        int cardY = 40;
        int cardH = 124;
        context.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xCC181822);
        context.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFF3E3E4C);
        context.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0xFF3E3E4C);
        context.fill(cardX, cardY + 1, cardX + 1, cardY + cardH - 1, 0xFF3E3E4C);
        context.fill(cardX + cardW - 1, cardY + 1, cardX + cardW, cardY + cardH - 1, 0xFF3E3E4C);
        context.fill(cardX + 4, cardY + 16, cardX + cardW - 4, cardY + 17, 0xFF3E3E4C);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§b§lAdvanced Foveated Rendering"), cx, 18, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§7Tune sharpness and detail around your gaze"), cx, 30, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§b§nImage Quality"), cx, cardY + 4, 0x80D8FF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) this.client.setScreen(this.parent);
    }
}
