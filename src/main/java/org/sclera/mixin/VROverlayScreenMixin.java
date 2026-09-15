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

    @Inject(method = "updateCursorData", at = @At("HEAD"))
    private void sclera$onUpdateCursorData(boolean isHovered, float normX, float normY, CallbackInfo ci) {
        if (isHovered && EyeTrackingManager.getInstance().isTrackingActive()) {
            GazeCursorHandler.getInstance().recordCursorPos(normX, normY);
        }
    }
}