package org.sclera.ui;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.tracking.GazeData;
import org.vmstudio.visor.api.client.gui.overlays.VROverlay;
import org.vmstudio.visor.api.client.gui.overlays.framework.VROverlayScreen;
import org.vmstudio.visor.api.client.gui.overlays.framework.screen.VROverlayScreenInScreen;
import org.vmstudio.visor.api.client.input.InputHelper;
import org.vmstudio.visor.api.client.input.MouseButtonType;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.client.player.pose.VRPlayerPoseClient;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRPose;
import org.vmstudio.visor.api.common.utils.VRMathUtils;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;
import org.vmstudio.visor.core.client.player.VRLocalPlayerImpl;
import org.vmstudio.visor.core.client.player.pose.LocalPlayerPose;
import org.vmstudio.visor.extensions.client.WindowExtension;

/**
 * Coordinates 3D gaze raycasting, cursor projection onto VR overlays, and blink actions.
 */
public class GazeCursorHandler {
    private static final GazeCursorHandler INSTANCE = new GazeCursorHandler();
    private VRPose gazePose;

    public static boolean isVRActive() {
        try {
            return VisorState.get() != null && VisorState.get().isActive();
        } catch (Throwable t) {
            return false;
        }
    }

    private volatile Vec3d latestEyePos;
    private volatile Vec3d latestGazeDir;
    private volatile Vec3d latestHandPos;
    private volatile long latestGazeTime = 0;

    private float lastNormX = 0.5f;
    private float lastNormY = 0.5f;
    private boolean hasHit = false;
    private long lastHitTime = 0;

    private final Vector3f lastMainHandPos = new Vector3f();
    private long lastMainHandPosTime = 0;
    private long lastMainHandMoveTime = 0;
    private long lastMainHandAimTime = 0;

    private final Vector3f lastOffHandPos = new Vector3f();
    private long lastOffHandPosTime = 0;
    private long lastOffHandMoveTime = 0;
    private long lastOffHandAimTime = 0;

    private boolean gazeInteracting = false;
    private boolean mainHandActiveInMenu = false;
    private boolean offhandActiveInMenu = false;

    private GazeCursorHandler() {}

    public static GazeCursorHandler getInstance() {
        return INSTANCE;
    }

    public void recordCursorPos(float normX, float normY) {
        this.lastNormX = normX;
        this.lastNormY = normY;
        this.hasHit = true;
        this.lastHitTime = System.currentTimeMillis();
    }

    public float getLastNormX() { return lastNormX; }
    public float getLastNormY() { return lastNormY; }
    public boolean hasHit() { return hasHit; }

    public void calibrateToOverlayCenter() {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        try {
            VRLocalPlayerImpl player = ClientContext.localPlayer;
            if (player != null) {
                LocalPlayerPose renderPose = player.getPoseData(PlayerPoseType.RENDER);
                if (renderPose != null && renderPose.getHmd() != null) {
                    VRPose hmd = renderPose.getHmd();
                    VROverlay targetOverlay = null;
                    if (ClientContext.overlayManager != null && ClientContext.overlayManager.getOverlaysRegistry() != null) {
                        for (VROverlay overlay : ClientContext.overlayManager.getOverlaysRegistry().getSortedComponents()) {
                            if (overlay.isVisible() && overlay.supportsCursor()) {
                                targetOverlay = overlay;
                                break;
                            }
                        }
                    }

                    if (targetOverlay != null && targetOverlay.getPose() != null) {
                        Vector3fc overlayCenter = targetOverlay.getPose().getPosition();
                        Vector3fc hmdPos = hmd.getPosition();
                        Vector3f worldDir = new Vector3f(overlayCenter).sub(hmdPos).normalize();

                        Matrix4f worldHmdRot = new Matrix4f()
                            .rotationY(hmd.getUsedRotationY())
                            .mul(hmd.getRawRotation());

                        Vector3f localDir = new Matrix4f(worldHmdRot).invert().transformDirection(worldDir).normalize();

                        float targetYawDeg = (float) Math.toDegrees(Math.atan2(localDir.x, -localDir.z));
                        float targetPitchDeg = (float) Math.toDegrees(Math.asin(Math.max(-1f, Math.min(1f, localDir.y))));

                        manager.recenter(targetYawDeg, targetPitchDeg);
                        this.lastNormX = 0.5f;
                        this.lastNormY = 0.5f;
                        this.hasHit = true;
                        this.lastHitTime = System.currentTimeMillis();
                        return;
                    }
                }
            }
        } catch (Throwable ignored) {}

        // Fallback
        manager.recenter();
        this.lastNormX = 0.5f;
        this.lastNormY = 0.5f;
        this.hasHit = true;
        this.lastHitTime = System.currentTimeMillis();
    }

    public boolean isGazePose(VRPose pose) {
        return this.gazePose != null && this.gazePose == pose;
    }

    public boolean isGazeInteracting() {
        return gazeInteracting;
    }

    public void setGazeInteracting(boolean gazeInteracting) {
        this.gazeInteracting = gazeInteracting;
    }

    public static boolean isContainerActive() {
        MinecraftClient mc = MinecraftClient.getInstance();
        return mc != null && mc.player != null && mc.player.currentScreenHandler != null
                && mc.player.currentScreenHandler != mc.player.playerScreenHandler;
    }

    public static boolean isWristInventoryActive() {
        try {
            if (ClientContext.overlayManager != null) {
                VROverlay invOverlay = ClientContext.overlayManager.getOverlay("inventory");
                return invOverlay != null && invOverlay.isVisible();
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static boolean isInventoryOrContainerActive(Screen screen) {
        if (isContainerActive() || isWristInventoryActive()) {
            return true;
        }
        return screen instanceof net.minecraft.client.gui.screen.ingame.HandledScreen;
    }

    public VROverlayScreen getActiveOverlayScreen() {
        try {
            VROverlayScreen rendering = VROverlayScreen.getRenderingOverlay();
            if (rendering != null && rendering.isVisible()) {
                return rendering;
            }
        } catch (Throwable ignored) {}

        // Prioritize the overlay currently focused by cursor or gaze
        try {
            if (ClientContext.cursorHandler != null) {
                VROverlay focused = ClientContext.cursorHandler.getFocusedOverlay();
                if (focused instanceof VROverlayScreen os && os.isVisible()) {
                    return os;
                }
            }
        } catch (Throwable ignored) {}

        // Fallback 1: Container overlay when container screen handler is active
        if (isContainerActive()) {
            try {
                if (ClientContext.overlayManager != null) {
                    VROverlay containerOverlay = ClientContext.overlayManager.getOverlay("container");
                    if (containerOverlay instanceof VROverlayScreen os && (os.isVisible() || os.isEnabled())) {
                        return os;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Fallback 2: Wrist inventory overlay
        if (isWristInventoryActive()) {
            try {
                if (ClientContext.overlayManager != null) {
                    VROverlay invOverlay = ClientContext.overlayManager.getOverlay("inventory");
                    if (invOverlay instanceof VROverlayScreen os && os.isVisible()) {
                        return os;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    public Screen getActiveScreen() {
        // Priority 1: Screen belonging to the currently focused overlay
        try {
            if (ClientContext.cursorHandler != null) {
                VROverlay focused = ClientContext.cursorHandler.getFocusedOverlay();
                if (focused instanceof VROverlayScreenInScreen<?> sis && sis.getScreen() != null) {
                    return sis.getScreen();
                }
                if (focused instanceof Screen s) {
                    return s;
                }
            }
        } catch (Throwable ignored) {}

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.currentScreen != null) {
            return mc.currentScreen;
        }

        // Priority 2: Container check (chests, barrels, crafting tables, furnaces, etc.)
        if (isContainerActive()) {
            try {
                if (ClientContext.overlayManager != null) {
                    VROverlay containerOverlay = ClientContext.overlayManager.getOverlay("container");
                    if (containerOverlay instanceof VROverlayScreenInScreen<?> sis && sis.getScreen() != null) {
                        return sis.getScreen();
                    }
                    if (containerOverlay instanceof VROverlayScreen os) {
                        return os;
                    }
                }
            } catch (Throwable ignored) {}
        }

        // Priority 3: Wrist Inventory check
        if (isWristInventoryActive()) {
            try {
                if (ClientContext.overlayManager != null) {
                    VROverlay invOverlay = ClientContext.overlayManager.getOverlay("inventory");
                    if (invOverlay instanceof VROverlayScreenInScreen<?> sis && sis.getScreen() != null) {
                        return sis.getScreen();
                    }
                    if (invOverlay instanceof VROverlayScreen os) {
                        return os;
                    }
                }
            } catch (Throwable ignored) {}
        }

        return null;
    }

    public int[] getOverlayPointerPos(VROverlay overlay, int width, int height) {
        if (overlay == null || overlay.getPose() == null) return null;
        try {
            VRLocalPlayerImpl player = ClientContext.localPlayer;
            if (player == null) return null;
            LocalPlayerPose renderPose = player.getPoseData(PlayerPoseType.RENDER);
            if (renderPose == null) return null;
            VRPose gaze = getGazePose(renderPose);
            if (gaze == null) return null;

            Vector3fc pos = overlay.getPose().getPosition();
            Matrix4fc rot = overlay.getPose().getRotation();
            float scale = overlay.getPose().getScale();
            float aspect = overlay.getAspectRatio();

            if (ClientContext.cursorHandler != null) {
                Vector3f res = ClientContext.cursorHandler.findCursorPosition3D(gaze, pos, rot, scale, aspect);
                if (res != null && res.z >= 0) {
                    if (res.x >= -0.08f && res.x <= 1.08f && res.y >= -0.08f && res.y <= 1.08f) {
                        float clampedX = Math.max(0f, Math.min(1f, res.x));
                        float clampedY = Math.max(0f, Math.min(1f, res.y));
                        int px = Math.round(clampedX * width);
                        int py = Math.round(clampedY * height);
                        return new int[]{px, py};
                    }
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    public boolean isHandControllingMenu(HandType handType) {
        if (!isVRActive()) return true;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isTrackingActive()) {
            return true;
        }

        Screen screen = getActiveScreen();
        boolean isInv = isInventoryOrContainerActive(screen);
        boolean gazeActive = isInv ? manager.isGazeInInventory() : manager.isGazeInMenus();
        boolean handsActive = isInv ? manager.isHandsInInventory() : manager.isHandsInMenus();

        if (!handsActive) return false;
        if (!gazeActive) return true;

        if (handType == HandType.OFFHAND) {
            return offhandActiveInMenu;
        }
        return mainHandActiveInMenu;
    }

    public boolean isAnyHandControllingMenu() {
        if (!isVRActive()) return true;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isTrackingActive()) {
            return true;
        }

        Screen screen = getActiveScreen();
        boolean isInv = isInventoryOrContainerActive(screen);
        boolean gazeActive = isInv ? manager.isGazeInInventory() : manager.isGazeInMenus();
        boolean handsActive = isInv ? manager.isHandsInInventory() : manager.isHandsInMenus();

        if (!handsActive) return false;
        if (!gazeActive) return true;

        return mainHandActiveInMenu || offhandActiveInMenu;
    }

    public void setHandControllingMenu(HandType handType, boolean active) {
        if (handType == HandType.OFFHAND) {
            this.offhandActiveInMenu = active;
        } else {
            this.mainHandActiveInMenu = active;
        }
    }

    public boolean isHandAimingAtOverlay(VRPose handPose) {
        if (handPose == null) return false;
        try {
            if (ClientContext.overlayManager != null) {
                // 1. Direct check for active container overlay
                if (isContainerActive()) {
                    VROverlay container = ClientContext.overlayManager.getOverlay("container");
                    if (container != null && container.getPose() != null) {
                        Vector3fc pos = container.getPose().getPosition();
                        Matrix4fc rot = container.getPose().getRotation();
                        float scale = container.getPose().getScale();
                        float aspect = container.getAspectRatio();
                        if (ClientContext.cursorHandler != null) {
                            Vector3f res = ClientContext.cursorHandler.findCursorPosition3D(handPose, pos, rot, scale, aspect);
                            if (res != null && res.z >= 0 && res.x >= -0.05f && res.x <= 1.05f && res.y >= -0.05f && res.y <= 1.05f) {
                                return true;
                            }
                        }
                    }
                }

                // 2. Direct check for active wrist inventory overlay
                if (isWristInventoryActive()) {
                    VROverlay inv = ClientContext.overlayManager.getOverlay("inventory");
                    if (inv != null && inv.getPose() != null) {
                        Vector3fc pos = inv.getPose().getPosition();
                        Matrix4fc rot = inv.getPose().getRotation();
                        float scale = inv.getPose().getScale();
                        float aspect = inv.getAspectRatio();
                        if (ClientContext.cursorHandler != null) {
                            Vector3f res = ClientContext.cursorHandler.findCursorPosition3D(handPose, pos, rot, scale, aspect);
                            if (res != null && res.z >= 0 && res.x >= -0.05f && res.x <= 1.05f && res.y >= -0.05f && res.y <= 1.05f) {
                                return true;
                            }
                        }
                    }
                }

                // 3. Fallback check across all registered overlays
                if (ClientContext.overlayManager.getOverlaysRegistry() != null) {
                    for (VROverlay overlay : ClientContext.overlayManager.getOverlaysRegistry().getSortedComponents()) {
                        if (overlay.isVisible() && overlay.supportsCursor() && overlay.getPose() != null) {
                            Vector3fc pos = overlay.getPose().getPosition();
                            Matrix4fc rot = overlay.getPose().getRotation();
                            float scale = overlay.getPose().getScale();
                            float aspect = overlay.getAspectRatio();
                            if (ClientContext.cursorHandler != null) {
                                Vector3f res = ClientContext.cursorHandler.findCursorPosition3D(handPose, pos, rot, scale, aspect);
                                if (res != null && res.z >= 0 && res.x >= -0.05f && res.x <= 1.05f && res.y >= -0.05f && res.y <= 1.05f) {
                                    return true;
                                }
                            }
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public boolean shouldUseHandInMenu(VRPlayerPoseClient poseClient, HandType handType) {
        if (!isVRActive()) return true;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isTrackingActive()) {
            setHandControllingMenu(handType, true);
            return true;
        }

        Screen screen = getActiveScreen();
        boolean isInv = isInventoryOrContainerActive(screen);
        boolean gazeActive = isInv ? manager.isGazeInInventory() : manager.isGazeInMenus();
        boolean handsActive = isInv ? manager.isHandsInInventory() : manager.isHandsInMenus();

        if (!handsActive) {
            setHandControllingMenu(handType, false);
            return false;
        }
        if (!gazeActive) {
            setHandControllingMenu(handType, true);
            return true;
        }

        // HYBRID Mode: Both are active
        VRPose hand = poseClient != null ? poseClient.getHand(handType) : null;
        if (hand == null) {
            setHandControllingMenu(handType, false);
            return false;
        }

        boolean aiming = isHandAimingAtOverlay(hand);
        if (!aiming) {
            setHandControllingMenu(handType, false);
            return false;
        }

        boolean isOffhand = (handType == HandType.OFFHAND);
        long now = System.currentTimeMillis();
        boolean moving = isHandMoving(poseClient, handType);
        if (moving) {
            if (isOffhand) {
                lastOffHandAimTime = now;
            } else {
                lastMainHandAimTime = now;
            }
        }
        long lastAim = isOffhand ? lastOffHandAimTime : lastMainHandAimTime;
        boolean active = (now - lastAim) < 400;
        setHandControllingMenu(handType, active);
        return active;
    }

    public boolean isHandMoving(VRPlayerPoseClient poseClient, HandType handType) {
        VRPose hand = poseClient.getHand(handType);
        if (hand == null) return false;
        Vector3fc currentPos = hand.getPosition();
        if (currentPos == null) return false;

        boolean isOffhand = (handType == HandType.OFFHAND);
        Vector3f lastPos = isOffhand ? lastOffHandPos : lastMainHandPos;
        long lastPosTime = isOffhand ? lastOffHandPosTime : lastMainHandPosTime;
        long lastMoveTime = isOffhand ? lastOffHandMoveTime : lastMainHandMoveTime;

        long now = System.currentTimeMillis();
        if (lastPosTime == 0) {
            lastPos.set(currentPos);
            if (isOffhand) lastOffHandPosTime = now;
            else lastMainHandPosTime = now;
            return false;
        }

        long dtMs = now - lastPosTime;
        if (dtMs >= 10) {
            float dt = dtMs / 1000.0f;
            float dist = currentPos.distance(lastPos);
            float speed = dist / dt; // Speed in meters per second

            // Intentional hand movement in VR is > 0.28 m/s (28 cm/s).
            // Tremor / trigger pressing without swinging is typically < 0.15 m/s.
            if (speed > 0.28f) {
                lastMoveTime = now;
                if (isOffhand) lastOffHandMoveTime = now;
                else lastMainHandMoveTime = now;
            }

            lastPos.set(currentPos);
            if (isOffhand) lastOffHandPosTime = now;
            else lastMainHandPosTime = now;
        }

        return (now - lastMoveTime) < 350;
    }

    public VRPose getGazePose(VRPlayerPoseClient playerPose) {
        if (gazePose == null) {
            gazePose = VRPose.create();
        }

        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isTrackingActive()) {
            return null;
        }

        GazeData gaze = manager.getGazeData();
        VRPose hmd = playerPose.getHmd();
        if (hmd == null) return null;

        Matrix4f rawGazeRot;
        if (manager.getTrackingMode() == EyeTrackingManager.TrackingMode.HEAD_TRACKING) {
            rawGazeRot = new Matrix4f(hmd.getRawRotation());
        } else {
            float effectiveYaw = (gaze.filteredYaw + manager.getOffsetYaw()) * manager.getSensitivity();
            float effectivePitch = -(gaze.filteredPitch + manager.getOffsetPitch()) * manager.getSensitivity();

            // Local rotation of gaze relative to HMD raw local rotation
            rawGazeRot = new Matrix4f(hmd.getRawRotation())
                .rotateY((float) Math.toRadians(-effectiveYaw))
                .rotateX((float) Math.toRadians(effectivePitch));
        }

        Vector3f rawGazeDir = VRMathUtils.extractForwardDir(rawGazeRot, true);

        // Update gazePose with exact Visor pose pipeline
        gazePose.update(
            hmd.getRawPosition(),
            rawGazeRot,
            rawGazeDir,
            hmd.getUsedOrigin(),
            hmd.getUsedRotationY(),
            hmd.getUsedWorldScale()
        );

        Vector3fc pos = gazePose.getPosition();
        Vector3fc dir = gazePose.getDirection();
        if (pos != null && dir != null) {
            this.latestEyePos = new Vec3d(pos.x(), pos.y(), pos.z());
            this.latestGazeDir = new Vec3d(dir.x(), dir.y(), dir.z()).normalize();
            this.latestGazeTime = System.currentTimeMillis();
        }

        try {
            VRLocalPlayerImpl lp = ClientContext.localPlayer;
            HandType ah = lp != null ? lp.getActiveHand() : HandType.MAIN;
            VRPose hand = playerPose.getHand(ah != null ? ah : HandType.MAIN);
            if (hand != null && hand.getPosition() != null) {
                Vector3fc hp = hand.getPosition();
                this.latestHandPos = new Vec3d(hp.x(), hp.y(), hp.z());
            }
        } catch (Throwable ignored) {}

        return gazePose;
    }

    public Vec3d getLatestEyePos() { return latestEyePos; }
    public Vec3d getLatestGazeDir() { return latestGazeDir; }
    public Vec3d getLatestHandPos() { return latestHandPos; }
    public boolean isGazeFresh(long maxAgeMs) {
        return latestGazeTime > 0 && (System.currentTimeMillis() - latestGazeTime) <= maxAgeMs;
    }

    public Vec3d computeGazeAimPoint(World world, Entity shooter, double maxDistance) {
        Vec3d eyePos = null;
        Vec3d gazeDir = null;

        try {
            VRLocalPlayerImpl player = ClientContext.localPlayer;
            if (player != null) {
                LocalPlayerPose renderPose = player.getPoseData(PlayerPoseType.RENDER);
                if (renderPose != null) {
                    VRPose pose = getGazePose(renderPose);
                    if (pose != null) {
                        Vector3fc p = pose.getPosition();
                        Vector3fc d = pose.getDirection();
                        if (p != null && d != null) {
                            eyePos = new Vec3d(p.x(), p.y(), p.z());
                            gazeDir = new Vec3d(d.x(), d.y(), d.z()).normalize();
                        }
                    }
                }
            }
        } catch (Throwable ignored) {}

        if (eyePos == null || gazeDir == null) {
            eyePos = this.latestEyePos;
            gazeDir = this.latestGazeDir;
        }

        if (eyePos == null || gazeDir == null) {
            return null;
        }

        Vec3d maxPos = eyePos.add(gazeDir.multiply(maxDistance));
        if (world == null || shooter == null) {
            return maxPos;
        }

        double closestDistSq = maxDistance * maxDistance;
        Vec3d targetPoint = maxPos;

        // 1. Raycast against blocks
        try {
            RaycastContext raycastContext = new RaycastContext(
                eyePos,
                maxPos,
                RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE,
                shooter
            );
            BlockHitResult blockHit = world.raycast(raycastContext);
            if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
                targetPoint = blockHit.getPos();
                closestDistSq = eyePos.squaredDistanceTo(targetPoint);
            }
        } catch (Throwable ignored) {}

        // 2. Raycast against entities
        try {
            Box searchBox = shooter.getBoundingBox().stretch(gazeDir.multiply(maxDistance)).expand(2.0);
            EntityHitResult entityHit = ProjectileUtil.raycast(
                shooter,
                eyePos,
                maxPos,
                searchBox,
                e -> !e.isSpectator() && e.canHit(),
                closestDistSq
            );
            if (entityHit != null && entityHit.getEntity() != null) {
                targetPoint = entityHit.getPos();
            }
        } catch (Throwable ignored) {}

        return targetPoint;
    }

    public int[] getScreenPointerPos(int screenWidth, int screenHeight) {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        GazeData gaze = manager.getGazeData();
        int px, py;

        long now = System.currentTimeMillis();
        boolean recentHit = hasHit && (now - lastHitTime < 400);

        if (recentHit && lastNormX >= -0.2f && lastNormX <= 1.2f && lastNormY >= -0.2f && lastNormY <= 1.2f) {
            px = Math.round(lastNormX * screenWidth);
            py = Math.round(lastNormY * screenHeight);
        } else if (manager.getTrackingMode() == EyeTrackingManager.TrackingMode.HEAD_TRACKING) {
            px = screenWidth / 2;
            py = screenHeight / 2;
        } else {
            // Screen center projection from degrees
            float effectiveYaw = (gaze.filteredYaw + manager.getOffsetYaw()) * manager.getSensitivity();
            float effectivePitch = -(gaze.filteredPitch + manager.getOffsetPitch()) * manager.getSensitivity();
            px = Math.round(screenWidth * (0.5f + (effectiveYaw / 25.0f)));
            py = Math.round(screenHeight * (0.5f - (effectivePitch / 20.0f)));
        }

        px = Math.max(2, Math.min(screenWidth - 2, px));
        py = Math.max(2, Math.min(screenHeight - 2, py));
        return new int[]{px, py};
    }

    public void syncGazeToMouse(int screenWidth, int screenHeight) {
        if (!isVRActive()) return;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isTrackingActive()) return;

        int[] p = getScreenPointerPos(screenWidth, screenHeight);
        int px = p[0];
        int py = p[1];

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.getWindow() != null) {
            Object windowObj = mc.getWindow();
            int mcScreenWidth = (windowObj instanceof WindowExtension ext) ? ext.visor$mcScreenWidth() : mc.getWindow().getWidth();
            int mcScreenHeight = (windowObj instanceof WindowExtension ext) ? ext.visor$mcScreenHeight() : mc.getWindow().getHeight();
            double wx = (double) px * mcScreenWidth / (double) screenWidth;
            double wy = (double) py * mcScreenHeight / (double) screenHeight;
            InputHelper.setMousePos(wx, wy);
        }
    }

    public void renderSubtlePointerAt(DrawContext context, int px, int py) {
        if (!isVRActive()) return;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isSubtlePointerEnabled()) return;
        if (!manager.isTrackingActive()) return;
        if (manager.isDebugPointerEnabled()) return;

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 500.0f);
        RenderSystem.disableDepthTest();

        // Clean subtle reticle: 4x4 soft shadow with crisp white 2x2 core
        context.fill(px - 2, py - 2, px + 2, py + 2, 0x80000000);
        context.fill(px - 1, py - 1, px + 1, py + 1, 0xFFFFFFFF);

        RenderSystem.enableDepthTest();
        context.getMatrices().pop();
    }

    public void renderSubtlePointer(DrawContext context, int screenWidth, int screenHeight) {
        int[] p = getScreenPointerPos(screenWidth, screenHeight);
        renderSubtlePointerAt(context, p[0], p[1]);
    }

    public void renderDebugPointerAt(DrawContext context, int px, int py) {
        if (!isVRActive()) return;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isDebugPointerEnabled()) return;

        GazeData gaze = manager.getGazeData();

        context.getMatrices().push();
        context.getMatrices().translate(0.0f, 0.0f, 500.0f);
        RenderSystem.disableDepthTest();

        // Draw bright red reticle with white center:
        // Outer red crosshair (13px wide, 13px high)
        context.fill(px - 6, py - 1, px + 7, py + 2, 0xFFFF0000);
        context.fill(px - 1, py - 6, px + 2, py + 7, 0xFFFF0000);
        // White border ring
        context.fill(px - 3, py - 3, px + 4, py + 4, 0xFFFFFFFF);
        // Center red pupil
        context.fill(px - 1, py - 1, px + 2, py + 2, 0xFFFF0000);

        // Telemetry HUD in top corner
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null && mc.textRenderer != null) {
            String badge = String.format("§c[Gaze Debug] (%d, %d) | Yaw: %.1f° | Pitch: %.1f° | Controllers: %s",
                px, py, gaze.filteredYaw, gaze.filteredPitch, manager.isHandsEnabled() ? "ON" : "OFF");
            context.drawTextWithShadow(mc.textRenderer, badge, 8, 8, 0xFFFF4444);
        }

        RenderSystem.enableDepthTest();
        context.getMatrices().pop();
    }

    public void renderDebugPointer(DrawContext context, int screenWidth, int screenHeight) {
        int[] p = getScreenPointerPos(screenWidth, screenHeight);
        renderDebugPointerAt(context, p[0], p[1]);
    }

    private boolean wasBlinkPressedScreen = false;
    private long blinkPressScreenTime = 0;
    private long lastBlinkClickScreenTime = 0;

    private boolean wasBlinkPressedWorld = false;
    private long blinkPressWorldTime = 0;
    private long lastBlinkClickWorldTime = 0;
    private MouseButtonType activeWorldBlinkBtn = null;

    private boolean isAutoChargingCrossbow = false;
    private long autoChargeStartTime = 0;

    private ItemStack getHeldCrossbow(MinecraftClient mc) {
        if (mc == null || mc.player == null) return null;
        ItemStack main = mc.player.getMainHandStack();
        if (main != null && main.getItem() instanceof CrossbowItem) {
            return main;
        }
        ItemStack off = mc.player.getOffHandStack();
        if (off != null && off.getItem() instanceof CrossbowItem) {
            return off;
        }
        return null;
    }

    public void updateBlinkScreen(Screen screen) {
        if (!isVRActive() || screen == null) return;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isTrackingActive()) return;

        boolean isInv = isInventoryOrContainerActive(screen);
        boolean blinkEnabled = isInv ? manager.isBlinkInInventory() : manager.isBlinkInMenus();
        if (!blinkEnabled) {
            if (wasBlinkPressedScreen) {
                triggerScreenClickRelease();
                wasBlinkPressedScreen = false;
            }
            return;
        }

        boolean isBlinking = manager.getGazeData().blink;
        long now = System.currentTimeMillis();

        if (isBlinking && !wasBlinkPressedScreen) {
            if (now - lastBlinkClickScreenTime > 350) {
                wasBlinkPressedScreen = true;
                blinkPressScreenTime = now;
                triggerScreenClickPress(screen);
            }
        } else if (!isBlinking && wasBlinkPressedScreen) {
            wasBlinkPressedScreen = false;
            lastBlinkClickScreenTime = now;
            triggerScreenClickRelease();
        } else if (wasBlinkPressedScreen && (now - blinkPressScreenTime > 800)) {
            wasBlinkPressedScreen = false;
            lastBlinkClickScreenTime = now;
            triggerScreenClickRelease();
        }
    }

    private void triggerScreenClickPress(Screen screen) {
        VROverlayScreen activeOverlay = getActiveOverlayScreen();
        if (activeOverlay != null) {
            int px = activeOverlay.getMouseX();
            int py = activeOverlay.getMouseY();
            if (px < 0 || py < 0) {
                int[] p = getOverlayPointerPos(activeOverlay, activeOverlay.getWidth(), activeOverlay.getHeight());
                if (p != null) {
                    px = p[0];
                    py = p[1];
                }
            }
            if (px >= 0 && py >= 0) {
                activeOverlay.mouseClicked((double) px, (double) py, 0);
            }
        }
        InputHelper.pressMouse(MouseButtonType.LEFT);
    }

    private void triggerScreenClickRelease() {
        VROverlayScreen activeOverlay = getActiveOverlayScreen();
        if (activeOverlay != null) {
            int px = activeOverlay.getMouseX();
            int py = activeOverlay.getMouseY();
            if (px < 0 || py < 0) {
                int[] p = getOverlayPointerPos(activeOverlay, activeOverlay.getWidth(), activeOverlay.getHeight());
                if (p != null) {
                    px = p[0];
                    py = p[1];
                }
            }
            if (px >= 0 && py >= 0) {
                activeOverlay.mouseReleased((double) px, (double) py, 0);
            }
        }
        InputHelper.releaseMouse(MouseButtonType.LEFT);
    }

    public void updateBlinkWorld() {
        if (!isVRActive()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.currentScreen != null || isContainerActive() || isWristInventoryActive()) {
            if (isAutoChargingCrossbow) {
                InputHelper.releaseMouse(MouseButtonType.RIGHT);
                isAutoChargingCrossbow = false;
            }
            if (wasBlinkPressedWorld && activeWorldBlinkBtn != null) {
                InputHelper.releaseMouse(activeWorldBlinkBtn);
                wasBlinkPressedWorld = false;
                activeWorldBlinkBtn = null;
            }
            return;
        }

        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (!manager.isTrackingActive()) {
            if (isAutoChargingCrossbow) {
                InputHelper.releaseMouse(MouseButtonType.RIGHT);
                isAutoChargingCrossbow = false;
            }
            return;
        }

        ItemStack crossbow = getHeldCrossbow(mc);

        // Auto-charging latch handler: keeps holding right click until CrossbowItem is charged
        if (isAutoChargingCrossbow) {
            long elapsed = System.currentTimeMillis() - autoChargeStartTime;
            if (mc.currentScreen != null || crossbow == null || elapsed > 2500) {
                InputHelper.releaseMouse(MouseButtonType.RIGHT);
                isAutoChargingCrossbow = false;
            } else if (CrossbowItem.isCharged(crossbow)) {
                InputHelper.releaseMouse(MouseButtonType.RIGHT);
                isAutoChargingCrossbow = false;
            }
        }

        EyeTrackingManager.BlinkWorldAction action = manager.getBlinkWorldAction();
        if (action == EyeTrackingManager.BlinkWorldAction.OFF) {
            if (wasBlinkPressedWorld && activeWorldBlinkBtn != null) {
                InputHelper.releaseMouse(activeWorldBlinkBtn);
                wasBlinkPressedWorld = false;
                activeWorldBlinkBtn = null;
            }
            if (isAutoChargingCrossbow) {
                InputHelper.releaseMouse(MouseButtonType.RIGHT);
                isAutoChargingCrossbow = false;
            }
            return;
        }

        boolean hasCrossbow = (crossbow != null && action == EyeTrackingManager.BlinkWorldAction.PLACE);
        boolean isCrossbowCharged = hasCrossbow && CrossbowItem.isCharged(crossbow);

        MouseButtonType btn = (action == EyeTrackingManager.BlinkWorldAction.PLACE)
            ? MouseButtonType.RIGHT : MouseButtonType.LEFT;

        boolean isBlinking = manager.getGazeData().blink;
        long now = System.currentTimeMillis();

        if (isBlinking && !wasBlinkPressedWorld) {
            if (now - lastBlinkClickWorldTime > 400) {
                wasBlinkPressedWorld = true;
                blinkPressWorldTime = now;
                activeWorldBlinkBtn = btn;

                if (hasCrossbow) {
                    if (!isCrossbowCharged) {
                        // Empty crossbow: toggle auto-charging with eyes open!
                        if (isAutoChargingCrossbow) {
                            InputHelper.releaseMouse(MouseButtonType.RIGHT);
                            isAutoChargingCrossbow = false;
                        } else {
                            isAutoChargingCrossbow = true;
                            autoChargeStartTime = now;
                            InputHelper.pressMouse(MouseButtonType.RIGHT);
                        }
                    } else {
                        // Charged crossbow: SHOOT!
                        InputHelper.pressMouse(MouseButtonType.RIGHT);
                    }
                } else {
                    InputHelper.pressMouse(btn);
                }
            }
        } else if (!isBlinking && wasBlinkPressedWorld) {
            wasBlinkPressedWorld = false;
            lastBlinkClickWorldTime = now;

            if (hasCrossbow && !isCrossbowCharged && isAutoChargingCrossbow) {
                // Keep charging with eyes open! Do not release right click here.
            } else {
                if (activeWorldBlinkBtn != null) {
                    InputHelper.releaseMouse(activeWorldBlinkBtn);
                    activeWorldBlinkBtn = null;
                }
            }
        } else if (wasBlinkPressedWorld && (now - blinkPressWorldTime > 1200)) {
            wasBlinkPressedWorld = false;
            lastBlinkClickWorldTime = now;
            if (hasCrossbow && !isCrossbowCharged && isAutoChargingCrossbow) {
                // Keep auto-charging in background
            } else {
                if (activeWorldBlinkBtn != null) {
                    InputHelper.releaseMouse(activeWorldBlinkBtn);
                    activeWorldBlinkBtn = null;
                }
            }
        }
    }
}