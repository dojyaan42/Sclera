package org.sclera.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.vmstudio.visor.api.client.player.pose.VRPlayerPoseClient;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRPose;

/**
 * When gaze or head bow aiming is active, redirects
 * VisorEssentials' BowAimCrosshairRenderer from the hand pose to the gaze pose.
 *
 * This removes the normal hand-directed bow crosshair and instead places the
 * crosshair at the player's eye focal target while drawing the bow.
 */
@Mixin(value = org.vmstudio.essentials.core.client.render.BowAimCrosshairRenderer.class, remap = false)
public class BowAimCrosshairMixin {

    // Only widen the renderer's checks; the actual bow task and projectile launch are unchanged.
    // Omit the isActive descriptor because its Minecraft parameter is renamed by each loader.
    @ModifyExpressionValue(
        method = {"onRenderPipelineStage", "getBrightness"},
        at = {
            @At(value = "INVOKE", target = "Lorg/vmstudio/essentials/core/client/tasks/BowItemTask;isActive"),
            @At(value = "INVOKE", target = "Lorg/vmstudio/essentials/core/client/tasks/BowItemTask;isNotched()Z"),
            @At(value = "INVOKE", target = "Lorg/vmstudio/essentials/core/client/tasks/BowItemTask;isDrawingBow()Z")
        }
    )
    private boolean sclera$includeAimedTrident(boolean original) {
        return original || GazeCursorHandler.getInstance().isTridentGazeAimActive();
    }

    @Redirect(
        method = "onRenderPipelineStage",
        at = @At(
            value = "INVOKE",
            target = "Lorg/vmstudio/visor/api/client/player/pose/VRPlayerPoseClient;getHand(Lorg/vmstudio/visor/api/common/HandType;)Lorg/vmstudio/visor/api/common/player/VRPose;"
        )
    )
    private VRPose sclera$redirectBowCrosshairToGaze(VRPlayerPoseClient poseClient, HandType handType) {
        if (poseClient == null) return null;

        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (manager != null && manager.isTrackingActive() && manager.isBowGazeAimEnabled()
                && manager.isGazeInWorld()
                && manager.getTrackingMode() != EyeTrackingManager.TrackingMode.OFF) {
            VRPose gazePose = GazeCursorHandler.getInstance().getGazePose(poseClient);
            if (gazePose != null) {
                return gazePose;
            }
        }

        return poseClient.getHand(handType);
    }
}
