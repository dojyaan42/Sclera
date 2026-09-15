package org.sclera.mixin;

import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.client.render.decoration.VRDecorator;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.player.VRLocalPlayerImpl;
import org.vmstudio.visor.core.client.player.pose.LocalPlayerPose;
import org.vmstudio.visor.core.client.render.decoration.effects.hand.HandEffectCrosshair;

/**
 * Ensures only a single crosshair is rendered in the 3D world when gaze tracking is active,
 * suppressing duplicate crosshairs from secondary hands and honoring the crosshair mode setting.
 */
@Mixin(value = HandEffectCrosshair.class, remap = false)
public class HandEffectCrosshairMixin {

    @Inject(method = "isVisible", at = @At("HEAD"), cancellable = true)
    private void sclera$onIsVisible(VRDecorator decorator, HandType handType, boolean active, CallbackInfoReturnable<Boolean> cir) {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (manager == null || !manager.isTrackingActive() || !GazeCursorHandler.isVRActive()) {
            return;
        }

        // If crosshair is disabled by user in settings, suppress the 3D crosshair
        if (manager.getCrosshairMode() == EyeTrackingManager.CrosshairMode.DISABLED) {
            cir.setReturnValue(false);
            return;
        }

        VRLocalPlayerImpl localPlayer = ClientContext.localPlayer;
        if (localPlayer == null) return;
        LocalPlayerPose renderPose = localPlayer.getPoseData(PlayerPoseType.RENDER);
        if (renderPose == null) return;

        HandType activeHand = localPlayer.getActiveHand();
        if (activeHand == null) activeHand = HandType.MAIN;

        // When gaze is controlling world targeting, only the active hand represents the gaze point.
        // Suppress the secondary hand's crosshair to eliminate duplicate (+ vs x) crosshairs!
        if (manager.shouldUseGazeInWorld(renderPose, activeHand)) {
            if (handType != activeHand) {
                cir.setReturnValue(false);
            }
        }
    }
}
