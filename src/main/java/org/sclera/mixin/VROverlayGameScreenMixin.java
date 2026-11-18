package org.sclera.mixin;

import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.core.client.gui.overlays.builtin.VROverlayGameScreen;

/**
 * Captures normalized 2D cursor coordinates from Visor's in-game screen overlay.
 */
@Mixin(value = VROverlayGameScreen.class, remap = false)
public class VROverlayGameScreenMixin {

    @Inject(method = "updateCursorData", at = @At("HEAD"), cancellable = true)
    private void sclera$onUpdateCursorData(boolean isHovered, float normX, float normY, CallbackInfo ci) {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (manager != null && manager.isTrackingActive()) {
            boolean inventory = GazeCursorHandler.isInventoryOrContainerActive(
                    net.minecraft.client.MinecraftClient.getInstance().currentScreen);
            boolean gazeEnabled = inventory ? manager.isGazeInInventory() : manager.isGazeInMenus();
            // Hold gaze during a blink, but still allow hands and focus changes to update.
            if (isHovered && gazeEnabled && !GazeCursorHandler.getInstance().isAnyHandControllingMenu()
                    && manager.getTrackingMode() != EyeTrackingManager.TrackingMode.HEAD_TRACKING && manager.getGazeData().blink) {
                if (!GazeCursorHandler.getInstance().isBlinkSyntheticUpdate()) {
                    ci.cancel();
                    return;
                }
            }
            if (isHovered) {
                GazeCursorHandler.getInstance().recordCursorPos(normX, normY);
            }
        }
    }
}
