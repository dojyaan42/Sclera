package org.sclera.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.api.client.gui.overlays.VROverlay;
import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayScreen;
import org.vmstudio.visor.api.client.gui.overlays.framework.screen.VROverlayScreenInScreen;
import org.vmstudio.visor.core.client.ClientContext;

/**
 * Synchronizes gaze cursor position with Minecraft screen coordinates and renders pointers.
 */
@Mixin(Screen.class)
public class ScreenDebugMixin {

    @Shadow public int width;
    @Shadow public int height;

    @Unique
    private boolean sclera$renderedWithTooltip = false;

    @Inject(method = "renderWithTooltip", at = @At("HEAD"))
    private void sclera$onRenderWithTooltipHead(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        sclera$renderedWithTooltip = true;
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void sclera$syncMouseBeforeRender(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!GazeCursorHandler.isVRActive()) return;

        VROverlay overlay = sclera$findOverlayForScreen();
        if (overlay == null) return;

        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (manager == null || !manager.isTrackingActive()) return;

        boolean isInv = GazeCursorHandler.isInventoryOrContainerActive((Screen) (Object) this)
                || "inventory".equals(overlay.getId()) || "container".equals(overlay.getId());
        boolean gazeActive = isInv ? manager.isGazeInInventory() : manager.isGazeInMenus();
        if (!gazeActive) return;

        VROverlay focused = null;
        if (ClientContext.cursorHandler != null) {
            focused = ClientContext.cursorHandler.getFocusedOverlay();
        }

        if (focused == overlay) {
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.currentScreen == (Object) this) {
                GazeCursorHandler.getInstance().syncGazeToMouse(this.width, this.height);
            }
            GazeCursorHandler.getInstance().updateBlinkScreen((Screen) (Object) this);
        }
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void sclera$renderDebugPointerFallback(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        // If renderWithTooltip is active, defer rendering to renderWithTooltip:TAIL so the pointer renders
        // strictly on top of all item slots, 3D block models, cursor drag stacks, and tooltips
        if (sclera$renderedWithTooltip) return;
        sclera$doRenderPointer(context);
    }

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void sclera$renderDebugPointerTop(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        sclera$renderedWithTooltip = false;
        sclera$doRenderPointer(context);
    }

    @Unique
    private VROverlay sclera$findOverlayForScreen() {
        // 1. If Visor is currently rendering an overlay screen, that is definitively the overlay for this screen
        try {
            VROverlayScreen rendering = VROverlayScreen.getRenderingOverlay();
            if (rendering != null) {
                if (rendering == (Object) this) {
                    return rendering;
                }
                if (rendering instanceof VROverlayScreenInScreen<?> sis && sis.getScreen() == (Object) this) {
                    return rendering;
                }
            }
        } catch (Throwable ignored) {}

        // 2. Check registered overlays in ClientContext.overlayManager
        if (ClientContext.overlayManager != null) {
            try {
                VROverlay container = ClientContext.overlayManager.getOverlay("container");
                if (container instanceof VROverlayScreenInScreen<?> sis && sis.getScreen() == (Object) this) {
                    return container;
                }
            } catch (Throwable ignored) {}

            try {
                VROverlay inv = ClientContext.overlayManager.getOverlay("inventory");
                if (inv instanceof VROverlayScreenInScreen<?> sis && sis.getScreen() == (Object) this) {
                    return inv;
                }
            } catch (Throwable ignored) {}

            try {
                VROverlay kb = ClientContext.overlayManager.getOverlay("keyboard");
                if (kb instanceof VROverlayScreenInScreen<?> sis && sis.getScreen() == (Object) this) {
                    return kb;
                }
            } catch (Throwable ignored) {}

            try {
                if (ClientContext.overlayManager.getOverlaysRegistry() != null) {
                    for (VROverlay overlay : ClientContext.overlayManager.getOverlaysRegistry().getSortedComponents()) {
                        if (overlay == (Object) this) {
                            return overlay;
                        }
                        if (overlay instanceof VROverlayScreenInScreen<?> sis && sis.getScreen() == (Object) this) {
                            return overlay;
                        }
                    }
                }
            } catch (Throwable ignored) {}

            // 3. If screen is Minecraft's currentScreen, it belongs to the game_screen overlay
            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc != null && mc.currentScreen == (Object) this) {
                try {
                    VROverlay gameScreen = ClientContext.overlayManager.getOverlay("game_screen");
                    if (gameScreen != null) {
                        return gameScreen;
                    }
                } catch (Throwable ignored) {}
            }
        }

        return null;
    }

    @Unique
    private void sclera$doRenderPointer(DrawContext context) {
        if (!GazeCursorHandler.isVRActive()) return;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (manager == null || !manager.isTrackingActive()) return;

        VROverlay overlay = sclera$findOverlayForScreen();
        if (overlay == null) return;

        boolean isInv = GazeCursorHandler.isInventoryOrContainerActive((Screen) (Object) this)
                || "inventory".equals(overlay.getId()) || "container".equals(overlay.getId());
        boolean gazeActive = isInv ? manager.isGazeInInventory() : manager.isGazeInMenus();
        if (!gazeActive) return;

        if (GazeCursorHandler.getInstance().isAnyHandControllingMenu()) return;

        // Strictly enforce a single active reticle across all screens and overlays.
        // The reticle must only render if this specific overlay is the one currently focused by gaze.
        VROverlay focused = null;
        if (ClientContext.cursorHandler != null) {
            focused = ClientContext.cursorHandler.getFocusedOverlay();
        }

        boolean isTargeted = (focused != null && focused == overlay);
        if (!isTargeted && focused == null && overlay.getActiveCursorData() != null && overlay.getActiveCursorData().isInGui()) {
            isTargeted = true;
        }

        if (!isTargeted) {
            return;
        }

        int px = overlay.getMouseX();
        int py = overlay.getMouseY();

        if (px < 0 || py < 0 || px > this.width || py > this.height) {
            if (overlay.getActiveCursorData() != null && overlay.getActiveCursorData().isInGui()) {
                px = overlay.getActiveCursorData().getCursorX();
                py = overlay.getActiveCursorData().getCursorY();
            }
        }

        if (px < 0 || py < 0 || px > this.width || py > this.height) {
            int[] p = GazeCursorHandler.getInstance().getOverlayPointerPos(overlay, this.width, this.height);
            if (p != null) {
                px = p[0];
                py = p[1];
            }
        }

        if (px >= 0 && py >= 0 && px <= this.width && py <= this.height) {
            GazeCursorHandler.getInstance().renderSubtlePointerAt(context, px, py);
            GazeCursorHandler.getInstance().renderDebugPointerAt(context, px, py);
        }
    }
}
