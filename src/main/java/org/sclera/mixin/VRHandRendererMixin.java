package org.sclera.mixin;

import net.minecraft.client.util.math.MatrixStack;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.api.client.render.VRRenderPass;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.core.client.gui.VRCursorHandlerImpl;
import org.vmstudio.visor.core.client.render.decoration.hand.VRHandRenderer;

/**
 * Suppresses controller pointer beam when gaze is actively controlling the interface or world.
 */
@Mixin(value = VRHandRenderer.class, remap = false)
public class VRHandRendererMixin {

    @Inject(method = "renderCursorLine", at = @At("HEAD"), cancellable = true)
    private void sclera$cancelHandCursorLine(HandType handType, VRRenderPass pass, MatrixStack matrices, VRCursorHandlerImpl cursorHandler, CallbackInfo ci) {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isTrackingActive() || !GazeCursorHandler.isVRActive()) {
            return;
        }
        net.minecraft.client.gui.screen.Screen screen = GazeCursorHandler.getInstance().getActiveScreen();
        org.vmstudio.visor.api.client.gui.overlays.VROverlay focused = null;
        if (cursorHandler != null) {
            focused = cursorHandler.getFocusedOverlay(handType, true);
            if (focused == null) {
                focused = cursorHandler.getFocusedOverlay();
            }
        }

        boolean inOverlayOrScreen = (screen != null || focused != null);

        if (inOverlayOrScreen) {
            boolean isInv = GazeCursorHandler.isInventoryOrContainerActive(screen)
                    || (focused != null && ("inventory".equals(focused.getId()) || "container".equals(focused.getId())));
            boolean handsActive = isInv ? manager.isHandsInInventory() : manager.isHandsInMenus();
            boolean gazeActive = isInv ? manager.isGazeInInventory() : manager.isGazeInMenus();

            if (!handsActive) {
                ci.cancel();
                return;
            }
            if (gazeActive && !GazeCursorHandler.getInstance().isHandControllingMenu(handType)) {
                ci.cancel();
                return;
            }
        } else {
            // In 3D world
            if (!manager.isHandsInWorld()) {
                ci.cancel();
                return;
            }
        }
    }
}
