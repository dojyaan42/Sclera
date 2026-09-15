package org.sclera.ui.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.tracking.GazeData;
import org.sclera.ui.widgets.SettingButtonWidget;

/**
 * Streamlined main configuration screen for Sclera VR Eye Tracking.
 * Provides high-level controls and presets, while linking to granular Advanced Settings.
 */
public class ScleraSettingsScreen extends Screen {
    private final Screen parent;

    private ButtonWidget trackingModeBtn;
    private ButtonWidget calibrateBtn;

    public ScleraSettingsScreen(Screen parent) {
        super(Text.literal("Sclera - Eye Tracking Settings"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        int cx = this.width / 2;

        int cardW = 146;
        int gap = 8;
        int leftCardX = cx - cardW - (gap / 2);
        int rightCardX = cx + (gap / 2);
        int cardY = 52;
        int cardH = 115;
        int btnW = cardW - 12; // 134px
        int leftBtnX = leftCardX + 6;
        int rightBtnX = rightCardX + 6;
        int rowH = 23;

        // Top Bar: Aim Source (Left) & Center Calibration (Right)
        this.trackingModeBtn = this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Aim Source: " + manager.getTrackingMode().getLabel()),
            btn -> {
                manager.cycleTrackingMode();
                updateLabels();
            }
        )
        .dimensions(leftCardX, 28, 172, 20)
        .tooltip(Tooltip.of(Text.literal("Select input source: Eyes (Eye Tracker), Head (Headset Center), or Off.")))
        .build());

        this.calibrateBtn = this.addDrawableChild(ButtonWidget.builder(
            Text.literal("🎯 Calibrate Center"),
            btn -> {
                if (this.client != null) {
                    this.client.setScreen(new ScleraCalibrationScreen(this));
                }
            }
        )
        .dimensions(rightCardX + (cardW - 114), 28, 114, 20)
        .tooltip(Tooltip.of(Text.literal("Aligns gaze center with the middle of your VR view.")))
        .build());

        // Card 1: Gameplay & Visuals (4 items)
        this.addDrawableChild(new SettingButtonWidget(
            leftBtnX, cardY + 20, btnW, 20,
            "Mode",
            () -> manager.getWorldInteractionMode().getLabel(manager.getTrackingMode()),
            btn -> manager.cycleWorldInteractionMode(),
            Tooltip.of(Text.literal("Gameplay interaction mode: Hybrid, Eyes Only, or Hands Only."))
        ));

        this.addDrawableChild(new SettingButtonWidget(
            leftBtnX, cardY + 20 + rowH, btnW, 20,
            "Bow Aim",
            () -> manager.isBowGazeAimEnabled() ? "§bGaze" : "§7Hand",
            btn -> manager.setBowGazeAimEnabled(!manager.isBowGazeAimEnabled()),
            Tooltip.of(Text.literal("Arrows fly directly to where you look instead of hand tilt."))
        ));

        this.addDrawableChild(new SettingButtonWidget(
            leftBtnX, cardY + 20 + (rowH * 2), btnW, 20,
            "Reticle",
            () -> manager.getCrosshairMode().getLabel(),
            btn -> manager.cycleCrosshairMode(),
            Tooltip.of(Text.literal("Visual reticle showing gaze focus: Subtle Dot, Debug, or Off."))
        ));

        this.addDrawableChild(new SettingButtonWidget(
            leftBtnX, cardY + 20 + (rowH * 3), btnW, 20,
            "Blink Action",
            () -> manager.getBlinkWorldAction().getLabel(),
            btn -> manager.cycleBlinkWorldAction(),
            Tooltip.of(Text.literal("Hands-free action on blink: Place block, Break/Attack, or Off."))
        ));

        // Card 2: Interface & Presets (4 items)
        this.addDrawableChild(new SettingButtonWidget(
            rightBtnX, cardY + 20, btnW, 20,
            "Mode",
            this::getUIModeBadge,
            btn -> cycleUIMode(manager),
            Tooltip.of(Text.literal("Interface interaction mode: Hybrid (Eyes + Hands), Eyes Only, or Hands Only."))
        ));

        this.addDrawableChild(new SettingButtonWidget(
            rightBtnX, cardY + 20 + rowH, btnW, 20,
            "Blink Click",
            () -> (manager.isBlinkInMenus() || manager.isBlinkInInventory()) ? "§dOn" : "§7Off",
            btn -> {
                boolean nextState = !(manager.isBlinkInMenus() || manager.isBlinkInInventory());
                manager.setBlinkInMenus(nextState);
                manager.setBlinkInInventory(nextState);
            },
            Tooltip.of(Text.literal("A quick blink performs a left click without pressing controller triggers."))
        ));

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Advanced Settings..."),
            btn -> {
                if (this.client != null) {
                    this.client.setScreen(new ScleraAdvancedSettingsScreen(this));
                }
            }
        )
        .dimensions(rightBtnX, cardY + 20 + (rowH * 2), btnW, 20)
        .tooltip(Tooltip.of(Text.literal("Fine-tune individual domains: menus vs inventory, hand overrides, and actions.")))
        .build());

        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Reset Defaults"),
            btn -> {
                manager.resetDefaults();
                updateLabels();
                if (this.client != null) {
                    this.client.getSoundManager().play(
                        PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f)
                    );
                }
            }
        )
        .dimensions(rightBtnX, cardY + 20 + (rowH * 3), btnW, 20)
        .tooltip(Tooltip.of(Text.literal("Restore all settings to recommended defaults.")))
        .build());

        // Footer: Centered Back Button
        this.addDrawableChild(ButtonWidget.builder(
            Text.literal("Back"),
            btn -> {
                if (this.client != null) {
                    this.client.setScreen(this.parent);
                }
            }
        )
        .dimensions(cx - 60, cardY + cardH + 8, 120, 20)
        .tooltip(Tooltip.of(Text.literal("Save settings and return to previous menu.")))
        .build());

        updateLabels();
    }

    private String getUIModeBadge() {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        boolean gm = manager.isGazeInMenus();
        boolean gi = manager.isGazeInInventory();
        boolean hm = manager.isHandsInMenus();
        boolean hi = manager.isHandsInInventory();
        boolean bm = manager.isBlinkInMenus();
        boolean bi = manager.isBlinkInInventory();

        // If blink in 2D menus or inventory is enabled, it modifies the preset to Custom
        if (bm || bi) {
            return "§7Custom";
        }

        if (gm && gi && hm && hi) {
            return "§dHybrid";
        } else if (gm && gi && !hm && !hi) {
            return "§dEyes Only";
        } else if (!gm && !gi && hm && hi) {
            return "§dHands Only";
        } else if (!gm && !gi && !hm && !hi) {
            return "§7Off";
        } else {
            return "§7Custom";
        }
    }

    private void cycleUIMode(EyeTrackingManager manager) {
        String current = getUIModeBadge();
        if (current.contains("Hybrid")) {
            // Switch to Eyes Only
            manager.setGazeInMenus(true);
            manager.setGazeInInventory(true);
            manager.setHandsInMenus(false);
            manager.setHandsInInventory(false);
            manager.setBlinkInMenus(false);
            manager.setBlinkInInventory(false);
        } else if (current.contains("Eyes Only")) {
            // Switch to Hands Only
            manager.setHandsInMenus(true);
            manager.setHandsInInventory(true);
            manager.setGazeInMenus(false);
            manager.setGazeInInventory(false);
            manager.setBlinkInMenus(false);
            manager.setBlinkInInventory(false);
        } else {
            // Switch back to Hybrid (exact default)
            manager.setGazeInMenus(true);
            manager.setGazeInInventory(true);
            manager.setHandsInMenus(true);
            manager.setHandsInInventory(true);
            manager.setBlinkInMenus(false);
            manager.setBlinkInInventory(false);
        }
    }

    private void updateLabels() {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (trackingModeBtn != null) {
            trackingModeBtn.setMessage(Text.literal("Aim Source: " + manager.getTrackingMode().getLabel()));
        }
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
        int cardW = 146;
        int gap = 8;
        int leftCardX = cx - cardW - (gap / 2);
        int rightCardX = cx + (gap / 2);
        int cardY = 52;
        int cardH = 115;

        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        GazeData gaze = manager.getGazeData();
        boolean active = manager.isTrackingActive();

        // Title
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e§lSclera §7• §fVR Eye Tracking Settings"), cx, 6, 0xFFFFFF);

        // Hardware Status & Telemetry Badge
        if (manager.getTrackingMode() == EyeTrackingManager.TrackingMode.OFF) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c○ Tracking Disabled §7(100% Controller Hand Aim)"), cx, 17, 0xAAAAAA);
        } else if (manager.getTrackingMode() == EyeTrackingManager.TrackingMode.HEAD_TRACKING) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e● Head Tracking Active §7(VR Headset Center)"), cx, 17, 0xAAAAAA);
        } else if (manager.isSimulationMode()) {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§e● Simulation Active §7(Generating Test Saccades)"), cx, 17, 0xAAAAAA);
        } else if (active) {
            String telemetry = String.format("§a● Connected §7(UDP 9000)  |  §7Yaw: §f%.2f° §7Pitch: §f%.2f° §7Blink: %s",
                gaze.filteredYaw, gaze.filteredPitch, gaze.blink ? "§dOn" : "§7Off");
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal(telemetry), cx, 17, 0xAAAAAA);
        } else {
            context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§c○ Waiting for Signal §7(UDP 9000 / 9002)  |  §7Yaw: 0.00° Pitch: 0.00°"), cx, 17, 0xAAAAAA);
        }

        // Card 1: Gameplay & Visuals (4 items) - Cyan header
        drawCard(context, leftCardX, cardY, cardW, cardH, 0xDD181822, 0xFF3E3E4C);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§b§nGameplay & Visuals"), leftCardX + cardW / 2, cardY + 5, 0x80D8FF);

        // Card 2: Interface & Presets (4 items) - Purple header
        drawCard(context, rightCardX, cardY, cardW, cardH, 0xDD181822, 0xFF3E3E4C);
        context.drawCenteredTextWithShadow(this.textRenderer, Text.literal("§d§nInterface & Presets"), rightCardX + cardW / 2, cardY + 5, 0xD580FF);

        super.render(context, mouseX, mouseY, delta);
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}