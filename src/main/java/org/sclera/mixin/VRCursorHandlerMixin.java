package org.sclera.mixin;


import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.vmstudio.visor.api.client.player.pose.VRPlayerPoseClient;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRPose;
import org.vmstudio.visor.core.client.gui.VRCursorHandlerImpl;

/**
 * Redirects Visor's GUI overlay cursor ray to the gaze direction when eye tracking is active.
 * getCursorResult is used exclusively for overlay interactions, never for 3D world targeting.
 */
@Mixin(value = VRCursorHandlerImpl.class, remap = false)
public class VRCursorHandlerMixin {

    @Redirect(
        method = "getCursorResult",
        at = @At(
            value = "INVOKE",
            target = "Lorg/vmstudio/visor/api/client/player/pose/VRPlayerPoseClient;getHand(Lorg/vmstudio/visor/api/common/HandType;)Lorg/vmstudio/visor/api/common/player/VRPose;"
        )
    )
    private VRPose sclera$redirectCursorPose(VRPlayerPoseClient poseClient, HandType handType) {
        if (poseClient == null) return null;
        VRPose handPose = poseClient.getHand(handType);

        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (manager == null || !manager.isTrackingActive() || !GazeCursorHandler.isVRActive()) {
            return handPose;
        }

        VRPose gazePose = GazeCursorHandler.getInstance().getGazePose(poseClient);
        if (gazePose == null) {
            return handPose;
        }

        boolean inContainer = GazeCursorHandler.isContainerActive();
        boolean inWristInv = GazeCursorHandler.isWristInventoryActive();
        net.minecraft.client.gui.screen.Screen activeScreen = GazeCursorHandler.getInstance().getActiveScreen();

        // In menus, container screens, or wrist inventory:
        if (activeScreen != null || inContainer || inWristInv) {
            boolean isInv = inContainer || inWristInv || (activeScreen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen);
            boolean gazeActive = manager.isTrackingActive() && (isInv ? manager.isGazeInInventory() : manager.isGazeInMenus());
            boolean handsActive = isInv ? manager.isHandsInInventory() : manager.isHandsInMenus();

            if (handsActive && !gazeActive) {
                return handPose;
            }
            if (gazeActive && !handsActive) {
                return gazePose;
            }
            if (handsActive && gazeActive) {
                if (GazeCursorHandler.getInstance().shouldUseHandInMenu(poseClient, handType)) {
                    return handPose;
                }
                return gazePose;
            }
            return handPose;
        }

        // Fallback for other overlays (settings, chat, hotbar, etc.)
        // getCursorResult is strictly for GUI overlays, never for 3D world targeting
        boolean gazeForGUI = manager.isGazeInMenus();
        boolean handsForGUI = manager.isHandsInMenus();

        if (handsForGUI && !gazeForGUI) {
            return handPose;
        }
        if (gazeForGUI && !handsForGUI) {
            return gazePose;
        }
        if (gazeForGUI && handsForGUI) {
            if (GazeCursorHandler.getInstance().shouldUseHandInMenu(poseClient, handType)) {
                return handPose;
            }
            return gazePose;
        }
        return handPose;
    }
}