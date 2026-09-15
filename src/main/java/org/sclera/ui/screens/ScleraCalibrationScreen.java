package org.sclera.ui.screens;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.tracking.GazeData;
import org.sclera.ui.GazeCursorHandler;

/**
 * Center calibration screen providing a target bullseye to calibrate gaze offsets.
 */
public class ScleraCalibrationScreen extends Screen {
    private final Screen parent;
    private boolean calibrated = false;
    private long calibratedTime = 0;

    public ScleraCalibrationScreen(Screen parent) {
        super(Text.literal("Sclera - Center Calibration"));
        this.parent = parent;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        performCalibration();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 256) { // Escape
            if (this.client != null) {
                this.client.setScreen(this.parent);
            }
            return true;
        }
        performCalibration();
        return true;
    }

    private void performCalibration() {
        if (calibrated) return;
        calibrated = true;
        calibratedTime = System.currentTimeMillis();

        GazeCursorHandler.getInstance().calibrateToOverlayCenter();

        if (this.client != null) {
            this.client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.2f)
            );
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Dark background for visual concentration
        context.fill(0, 0, this.width, this.height, 0xEE0A0A0A);

        int cx = this.width / 2;
        int cy = this.height / 2;

        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        GazeData gaze = manager.getGazeData();

        if (calibrated) {
            // Successfully calibrated state
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§a§l✔ CENTER CALIBRATED SUCCESSFULLY!"), cx, cy - 45, 0x55FF55);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(String.format("§7Offset applied: Yaw %.2f° | Pitch %.2f°",
                    manager.getOffsetYaw(), manager.getOffsetPitch())), cx, cy - 25, 0xAAAAAA);

            // Bright green / white bullseye target
            drawTarget(context, cx, cy, 0xFF55FF55, 0xFFFFFFFF);

            if (System.currentTimeMillis() - calibratedTime > 800) {
                if (this.client != null) {
                    this.client.setScreen(this.parent);
                }
            }
        } else {
            // Title and instructions
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§a§lEYE TRACKING CALIBRATION"), cx, cy - 75, 0x55FF55);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§fLook directly at the green center dot"), cx, cy - 55, 0xFFFFFF);
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§ePull your VR controller trigger to calibrate"), cx, cy + 45, 0xFFFF55);

            // Live telemetry
            context.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal(String.format("§8Current Gaze -> Yaw: %.2f° | Pitch: %.2f°",
                    gaze.filteredYaw, gaze.filteredPitch)), cx, cy + 68, 0x888888);

            // Emerald green target with cross
            drawTarget(context, cx, cy, 0xFF00DD44, 0xFF00FF66);
        }

        // Render pointer to visualize alignment with center
        if (manager.isSubtlePointerEnabled() && !manager.isDebugPointerEnabled()) {
            GazeCursorHandler.getInstance().renderSubtlePointer(context, this.width, this.height);
        }
        if (manager.isDebugPointerEnabled()) {
            GazeCursorHandler.getInstance().renderDebugPointer(context, this.width, this.height);
        }
    }

    private void drawTarget(DrawContext context, int cx, int cy, int outerColor, int innerColor) {
        // Outer crosshair
        context.fill(cx - 24, cy - 1, cx + 25, cy + 2, outerColor);
        context.fill(cx - 1, cy - 24, cx + 2, cy + 25, outerColor);

        // Outer ring
        context.fill(cx - 14, cy - 14, cx + 15, cy - 12, outerColor);
        context.fill(cx - 14, cy + 13, cx + 15, cy + 15, outerColor);
        context.fill(cx - 14, cy - 14, cx - 12, cy + 15, outerColor);
        context.fill(cx + 13, cy - 14, cx + 15, cy + 15, outerColor);

        // Inner circle
        context.fill(cx - 7, cy - 7, cx + 8, cy + 8, innerColor);
        // Center dot
        context.fill(cx - 2, cy - 2, cx + 3, cy + 3, 0xFFFFFFFF);
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public void close() {
        if (this.client != null) {
            this.client.setScreen(this.parent);
        }
    }
}
