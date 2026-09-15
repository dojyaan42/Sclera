package org.sclera.mixin;

import net.minecraft.client.MinecraftClient;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import net.minecraft.client.gui.screen.Screen;
import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayScreen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.api.client.gui.overlays.VROverlay;
import org.vmstudio.visor.api.client.input.InputHelper;
import org.vmstudio.visor.api.client.input.MouseButtonType;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.gui.overlays.builtin.VROverlayGameScreen;
import org.vmstudio.visor.core.client.input.mouse.MouseClickHandler;

/**
 * Intercepts Visor mouse clicks to ensure gaze-targeted screen interactions trigger correctly,
 * while allowing Visor overlays (such as the virtual keyboard and wrist inventory) to receive clicks natively.
 */
@Mixin(value = MouseClickHandler.class, remap = false)
public abstract class MouseClickHandlerMixin {

    @Shadow @Final private MouseButtonType buttonType;

    @Inject(method = "onPress", at = @At("HEAD"), cancellable = true)
    private void sclera$onPress(HandType handType, CallbackInfo ci) {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        Screen activeScreen = GazeCursorHandler.getInstance().getActiveScreen();
        if (manager != null && manager.isTrackingActive() && activeScreen != null) {
            // If this hand is actively controlling the menu, let Visor handle the click natively!
            if (GazeCursorHandler.getInstance().isHandControllingMenu(handType)) {
                return;
            }

            // Check if an overlay like the Visor virtual keyboard or wrist inventory is focused
            VROverlay focused = null;
            if (ClientContext.cursorHandler != null) {
                focused = ClientContext.cursorHandler.getFocusedOverlay(handType, true);
                if (focused == null) {
                    focused = ClientContext.cursorHandler.getFocusedOverlay();
                }
            }
            if (focused != null && !(focused instanceof VROverlayGameScreen)) {
                // Secondary overlay focused! Let Visor handle the click natively
                return;
            }

            VROverlayScreen activeOverlay = GazeCursorHandler.getInstance().getActiveOverlayScreen();
            if (activeOverlay != null) {
                int px = activeOverlay.getMouseX();
                int py = activeOverlay.getMouseY();
                if (px < 0 || py < 0) {
                    int[] p = GazeCursorHandler.getInstance().getOverlayPointerPos(activeOverlay, activeOverlay.getWidth(), activeOverlay.getHeight());
                    if (p != null) {
                        px = p[0];
                        py = p[1];
                    }
                }
                if (px >= 0 && py >= 0) {
                    activeOverlay.mouseClicked((double) px, (double) py, this.buttonType != null ? this.buttonType.getId() : 0);
                    ci.cancel();
                    return;
                }
            }

            InputHelper.pressMouse(this.buttonType != null ? this.buttonType : MouseButtonType.LEFT);
            ci.cancel();
        }
    }

    @Inject(method = "onRelease", at = @At("HEAD"), cancellable = true)
    private void sclera$onRelease(HandType handType, CallbackInfo ci) {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        Screen activeScreen = GazeCursorHandler.getInstance().getActiveScreen();
        if (manager != null && manager.isTrackingActive() && activeScreen != null) {
            if (GazeCursorHandler.getInstance().isHandControllingMenu(handType)) {
                return;
            }

            VROverlay focused = null;
            if (ClientContext.cursorHandler != null) {
                focused = ClientContext.cursorHandler.getFocusedOverlay(handType, true);
                if (focused == null) {
                    focused = ClientContext.cursorHandler.getFocusedOverlay();
                }
            }
            if (focused != null && !(focused instanceof VROverlayGameScreen)) {
                return;
            }

            VROverlayScreen activeOverlay = GazeCursorHandler.getInstance().getActiveOverlayScreen();
            if (activeOverlay != null) {
                int px = activeOverlay.getMouseX();
                int py = activeOverlay.getMouseY();
                if (px < 0 || py < 0) {
                    int[] p = GazeCursorHandler.getInstance().getOverlayPointerPos(activeOverlay, activeOverlay.getWidth(), activeOverlay.getHeight());
                    if (p != null) {
                        px = p[0];
                        py = p[1];
                    }
                }
                if (px >= 0 && py >= 0) {
                    activeOverlay.mouseReleased((double) px, (double) py, this.buttonType != null ? this.buttonType.getId() : 0);
                    ci.cancel();
                    return;
                }
            }

            InputHelper.releaseMouse(this.buttonType != null ? this.buttonType : MouseButtonType.LEFT);
            ci.cancel();
        }
    }
}
