package org.sclera.mixin;

import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayScreen;

/**
 * Captures 2D cursor interaction state from Visor's overlay screens.
 */
@Mixin(value = VROverlayScreen.class, remap = false)
public class VROverlayScreenMixin {

    @Inject(method = "updateCursorData", at = @At("HEAD"), cancellable = true)
    private void sclera$onUpdateCursorData(boolean isHovered, float normX, float normY, CallbackInfo ci) {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (manager != null && manager.isTrackingActive()) {
            VROverlayScreen overlay = (VROverlayScreen) (Object) this;
            boolean inventory = "inventory".equals(overlay.getId()) || "container".equals(overlay.getId());
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
                GazeCursorHandler.getInstance().recordCursorPos((VROverlayScreen) (Object) this, normX, normY);
            }
        }
    }
}
