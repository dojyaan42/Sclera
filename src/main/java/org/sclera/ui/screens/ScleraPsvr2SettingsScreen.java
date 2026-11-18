package org.sclera.ui.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.sclera.config.ScleraClientConfig;
import org.sclera.haptics.AdaptiveBowTriggers;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.widgets.SettingButtonWidget;

public final class ScleraPsvr2SettingsScreen extends Screen {
    private final Screen parent;
    private int buttonWidth;

    public ScleraPsvr2SettingsScreen(Screen parent) {
        super(Text.literal("Sclera - PS VR2 Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        AdaptiveBowTriggers toolkit = AdaptiveBowTriggers.getInstance();
        ScleraClientConfig config = ScleraClientConfig.getInstance();
        int cx = width / 2;

        // Measure each row's longest state, keeping one stable shared width as settings change.
        buttonWidth = Math.max(settingWidth("Block Break Feedback", "Unavailable", "On", "Off"), Math.max(
                settingWidth("Eye Tracking Source", "PSVR2 (Inactive)", "VRCFT"),
                Math.max(settingWidth("Bow Trigger", "Requires PSVR2", "Unavailable", "On", "Off"),
                        Math.max(settingWidth("Damage Feedback", "Unavailable", "On", "Off"),
                                settingWidth("Hit Feedback", "Unavailable", "On", "Off")))));
        int buttonX = cx - buttonWidth / 2;

        toolkit.checkAvailability(availability -> {});

        addDrawableChild(new SettingButtonWidget(
                buttonX, 62, buttonWidth, 20,
                "Eye Tracking Source",
                () -> manager.getEyeTrackingSource() == EyeTrackingManager.EyeTrackingSource.PSVR2
                        ? (toolkit.isAvailable() ? "§bPSVR2" : "§bPSVR2 §7(Inactive)") : "§dVRCFT",
                button -> manager.setEyeTrackingSource(manager.getEyeTrackingSource().next()),
                Tooltip.of(Text.literal("Switch between direct PSVR2 native tracking and VRCFT (UDP)."))
        ));

        addDrawableChild(new SettingButtonWidget(
                buttonX, 90, buttonWidth, 20,
                "Bow Trigger",
                () -> manager.getEyeTrackingSource() != EyeTrackingManager.EyeTrackingSource.PSVR2
                        ? "§7Requires PSVR2"
                        : (!toolkit.isAvailable() ? "§7Unavailable"
                        : (manager.isAdaptiveBowTriggerEnabled() ? "§aOn" : "§7Off")),
                () -> manager.getEyeTrackingSource() == EyeTrackingManager.EyeTrackingSource.PSVR2
                        && toolkit.isAvailable(),
                button -> {
                    if (manager.getEyeTrackingSource() != EyeTrackingManager.EyeTrackingSource.PSVR2
                            || !toolkit.isAvailable()) return;
                    boolean enabled = !manager.isAdaptiveBowTriggerEnabled();
                    manager.setAdaptiveBowTriggerEnabled(enabled);
                },
                Tooltip.of(Text.literal(manager.getEyeTrackingSource() == EyeTrackingManager.EyeTrackingSource.PSVR2
                        ? "Adds progressive resistance to the trigger of the hand drawing the bow."
                        : "Select PSVR2 as the eye tracking source to use the bow trigger."))
        ));

        addDrawableChild(new SettingButtonWidget(
                buttonX, 118, buttonWidth, 20,
                "Damage Feedback",
                () -> !toolkit.isAvailable() ? "§7Unavailable"
                        : (config.isDamageVibrationEnabled() ? "§aOn" : "§7Off"),
                toolkit::isAvailable,
                button -> {
                    if (toolkit.isAvailable()) config.setDamageVibrationEnabled(!config.isDamageVibrationEnabled());
                },
                Tooltip.of(Text.literal("Vibrate when you take damage. Works independently of eye tracking."))
        ));

        addDrawableChild(new SettingButtonWidget(
                buttonX, 146, buttonWidth, 20,
                "Hit Feedback",
                () -> !toolkit.isAvailable() ? "§7Unavailable"
                        : (config.isHitFeedbackEnabled() ? "§aOn" : "§7Off"),
                toolkit::isAvailable,
                button -> {
                    if (toolkit.isAvailable()) config.setHitFeedbackEnabled(!config.isHitFeedbackEnabled());
                },
                Tooltip.of(Text.literal("Vibrate the attacking controller on a confirmed hit. Bows use both controllers. Requires Sclera on the server."))
        ));

        addDrawableChild(new SettingButtonWidget(
                buttonX, 174, buttonWidth, 20,
                "Block Break Feedback",
                () -> !toolkit.isAvailable() ? "§7Unavailable"
                        : (config.isBlockBreakFeedbackEnabled() ? "§aOn" : "§7Off"),
                toolkit::isAvailable,
                button -> {
                    if (toolkit.isAvailable()) config.setBlockBreakFeedbackEnabled(!config.isBlockBreakFeedbackEnabled());
                },
                Tooltip.of(Text.literal("Vibrate the controller that breaks a block. Requires Sclera on the server."))
        ));

        addDrawableChild(ButtonWidget.builder(Text.literal("Back"), button -> {
            if (client != null) client.setScreen(parent);
        }).dimensions(cx - 60, 208, 120, 20).build());
    }

    private int settingWidth(String title, String... states) {
        int valueWidth = 0;
        for (String state : states) valueWidth = Math.max(valueWidth, textRenderer.getWidth(state));
        return textRenderer.getWidth(title) + valueWidth + 24; // 8px padding and gap
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        int cx = width / 2;
        int cardX = cx - buttonWidth / 2 - 6;
        int cardY = 40;
        int cardW = buttonWidth + 12;
        int cardH = 160;
        context.fill(cardX, cardY, cardX + cardW, cardY + cardH, 0xCC181822);
        context.fill(cardX, cardY, cardX + cardW, cardY + 1, 0xFF3E3E4C);
        context.fill(cardX, cardY + cardH - 1, cardX + cardW, cardY + cardH, 0xFF3E3E4C);
        context.fill(cardX, cardY + 1, cardX + 1, cardY + cardH - 1, 0xFF3E3E4C);
        context.fill(cardX + cardW - 1, cardY + 1, cardX + cardW, cardY + cardH - 1, 0xFF3E3E4C);
        context.fill(cardX + 4, cardY + 16, cardX + cardW - 4, cardY + 17, 0xFF3E3E4C);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§e§lSclera §7• §fPS VR2 Settings"), cx, 16, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§7Eye tracking, adaptive triggers, and vibration"), cx, 28, 0xAAAAAA);
        context.drawCenteredTextWithShadow(textRenderer, Text.literal("§b§nPSVR2 Controls"), cx, cardY + 4, 0x80D8FF);
        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (client != null) client.setScreen(parent);
    }
}
