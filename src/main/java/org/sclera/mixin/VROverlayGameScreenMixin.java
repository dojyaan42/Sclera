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

    @Inject(method = "updateCursorData", at = @At("HEAD"))
    private void sclera$onUpdateCursorData(boolean isHovered, float normX, float normY, CallbackInfo ci) {
        if (isHovered && EyeTrackingManager.getInstance().isTrackingActive()) {
            GazeCursorHandler.getInstance().recordCursorPos(normX, normY);
        }
    }
}