package org.sclera.ui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.slot.Slot;
import org.joml.Vector2f;
import org.sclera.mixin.HandledScreenAccessor;

/**
 * Snaps the gaze cursor toward nearby inventory slots to assist slot selection.
 */
public class SlotMagnetism {
    private static final float SNAP_RADIUS = 16.0f; // Pixels in GUI space
    private static final float DAMPING_FACTOR = 0.25f;

    public static Vector2f applyMagnetism(float rawGuiX, float rawGuiY) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return new Vector2f(rawGuiX, rawGuiY);

        Screen screen = GazeCursorHandler.getInstance().getActiveScreen();
        if (!(screen instanceof HandledScreen<?> handledScreen)) {
            return new Vector2f(rawGuiX, rawGuiY);
        }

        HandledScreenAccessor accessor = (HandledScreenAccessor) handledScreen;
        int guiLeft = accessor.getX();
        int guiTop = accessor.getY();

        float bestDistanceSq = SNAP_RADIUS * SNAP_RADIUS;
        float targetX = rawGuiX;
        float targetY = rawGuiY;

        for (Slot slot : handledScreen.getScreenHandler().slots) {
            float slotCenterX = guiLeft + slot.x + 8.0f;
            float slotCenterY = guiTop + slot.y + 8.0f;

            float dx = rawGuiX - slotCenterX;
            float dy = rawGuiY - slotCenterY;
            float distSq = dx * dx + dy * dy;

            if (distSq < bestDistanceSq) {
                bestDistanceSq = distSq;
                float dist = (float) Math.sqrt(distSq);
                float factor = (dist < 6.0f) ? 0.0f : (dist / SNAP_RADIUS) * DAMPING_FACTOR;

                targetX = slotCenterX + dx * factor;
                targetY = slotCenterY + dy * factor;
            }
        }

        return new Vector2f(targetX, targetY);
    }
}