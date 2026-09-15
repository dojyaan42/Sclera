package org.sclera.mixin;

import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vmstudio.visor.api.client.gui.overlays.VROverlay;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.common.player.VRPose;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.player.VRLocalPlayerImpl;
import org.vmstudio.visor.core.client.player.pose.LocalPlayerPose;

/**
 * Makes the wrist inventory overlay consider gaze aim for visibility.
 * Without this, Visor only checks hand and HMD aim, so eye tracking users
 * can never make the inventory visible by looking at it.
 */
@Mixin(value = org.vmstudio.essentials.core.client.gui.overlays.VROverlayInventory.class, remap = false)
public class VROverlayInventoryMixin {

    @Inject(method = "updateVisibility", at = @At("RETURN"), cancellable = true)
    private void sclera$addGazeVisibility(CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValue()) return;

        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (manager == null || !manager.isTrackingActive() || !manager.isGazeInInventory()) return;
        if (!GazeCursorHandler.isVRActive()) return;

        VROverlay self = (VROverlay) (Object) this;
        if (self.getPose() == null) return;

        try {
            VRLocalPlayerImpl player = ClientContext.localPlayer;
            if (player == null) return;
            LocalPlayerPose renderPose = player.getPoseData(PlayerPoseType.RENDER);
            if (renderPose == null) return;
            VRPose gazePose = GazeCursorHandler.getInstance().getGazePose(renderPose);
            if (gazePose == null) return;

            if (ClientContext.cursorHandler != null
                    && ClientContext.cursorHandler.isFacingOverlay(gazePose, self, true)) {
                org.joml.Vector3fc pos = self.getPose().getPosition();
                org.joml.Matrix4fc rot = self.getPose().getRotation();
                float scale = self.getPose().getScale();
                float aspect = self.getAspectRatio();

                org.joml.Vector3f res = ClientContext.cursorHandler.findCursorPosition3D(gazePose, pos, rot, scale, aspect);
                if (res != null && res.z >= 0 && res.z <= 2.0f) {
                    if (res.x >= -0.15f && res.x <= 1.15f && res.y >= -0.15f && res.y <= 1.15f) {
                        cir.setReturnValue(true);
                    }
                }
            }
        } catch (Throwable ignored) {}
    }
}
