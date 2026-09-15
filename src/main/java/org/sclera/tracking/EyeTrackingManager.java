package org.sclera.tracking;

import net.minecraft.client.MinecraftClient;
import org.sclera.filter.OneEuroFilter;
import org.sclera.ui.GazeCursorHandler;
import org.vmstudio.visor.api.client.input.InputHelper;
import org.vmstudio.visor.api.client.input.MouseButtonType;
import org.vmstudio.visor.api.client.player.pose.VRPlayerPoseClient;
import org.vmstudio.visor.api.common.HandType;

/**
 * Core manager handling eye tracking state, OSC socket lifecycle, calibration offsets,
 * and routing between gaze, VR controllers, and blinking actions.
 */
public class EyeTrackingManager {
    private static EyeTrackingManager INSTANCE;

    private final OneEuroFilter pitchFilter = new OneEuroFilter(1.2, 0.005, 1.0);
    private final OneEuroFilter yawFilter = new OneEuroFilter(1.2, 0.005, 1.0);

    private final GazeData gazeData = new GazeData();
    private OscGazeReceiver oscReceiver1;
    private OscGazeReceiver oscReceiver2;

    public enum TrackingMode {
        EYE_TRACKING("§a👁 Eyes"),
        HEAD_TRACKING("§e🥽 Head"),
        OFF("§c⏻ Off");

        private final String label;

        TrackingMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public TrackingMode next() {
            TrackingMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum WorldInteractionMode {
        HYBRID("§bHybrid"),
        GAZE_ONLY("§bEyes Only"),
        HANDS_ONLY("§bHands Only"),
        OFF("§7Off"),
        CUSTOM("§7Custom");

        private final String label;

        WorldInteractionMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public String getLabel(TrackingMode mode) {
            if (mode == TrackingMode.HEAD_TRACKING) {
                switch (this) {
                    case HYBRID: return "§bHybrid";
                    case GAZE_ONLY: return "§bHead Only";
                    case HANDS_ONLY: return "§bHands Only";
                    case OFF: return "§7Off";
                    case CUSTOM: return "§7Custom";
                }
            }
            return this.label;
        }

        public WorldInteractionMode next() {
            switch (this) {
                case HYBRID: return GAZE_ONLY;
                case GAZE_ONLY: return HANDS_ONLY;
                case HANDS_ONLY: return OFF;
                case OFF:
                case CUSTOM:
                default: return HYBRID;
            }
        }
    }

    public enum CrosshairMode {
        ENABLED("§bDot"),
        DEBUG("§cDebug"),
        DISABLED("§7Off");

        private final String label;

        CrosshairMode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public CrosshairMode next() {
            CrosshairMode[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum BlinkWorldAction {
        PLACE("§bPlace"),
        ATTACK("§bBreak"),
        OFF("§7Off");

        private final String label;

        BlinkWorldAction(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public BlinkWorldAction next() {
            BlinkWorldAction[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private TrackingMode trackingMode = TrackingMode.EYE_TRACKING;
    private boolean enabled = true;
    private boolean handsEnabled = true;
    private CrosshairMode crosshairMode = CrosshairMode.ENABLED;
    private boolean subtlePointerEnabled = true;
    private boolean debugPointerEnabled = false;
    private boolean bowGazeAimEnabled = true;
    private WorldInteractionMode worldInteractionMode = WorldInteractionMode.HYBRID;

    // Granular domain toggles
    private boolean gazeInMenus = true;
    private boolean gazeInInventory = true;
    private boolean gazeInWorld = true;

    private boolean handsInMenus = true;
    private boolean handsInInventory = true;
    private boolean handsInWorld = true;

    private boolean blinkInMenus = false;
    private boolean blinkInInventory = false;
    private BlinkWorldAction blinkWorldAction = BlinkWorldAction.OFF;

    private float sensitivity = 1.0f;
    private float offsetPitch = 0.0f;
    private float offsetYaw = 0.0f;

    private long packetsReceived = 0;
    private boolean simulationMode = false;
    private boolean firstPacketLogged = false;

    // Partial values for multi-packet assembly
    private float leftYaw = 0f, rightYaw = 0f;
    private float leftPitch = 0f, rightPitch = 0f;
    private float eyelid = 1.0f;

    public EyeTrackingManager() {
        INSTANCE = this;
        startSimulationThread();
    }

    public static EyeTrackingManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EyeTrackingManager();
        }
        return INSTANCE;
    }

    public void init(int primaryPort, int fallbackPort) {
        if (oscReceiver1 == null) {
            oscReceiver1 = new OscGazeReceiver(primaryPort, this);
            oscReceiver1.start();
        }
        if (fallbackPort != primaryPort && oscReceiver2 == null) {
            oscReceiver2 = new OscGazeReceiver(fallbackPort, this);
            oscReceiver2.start();
        }
    }

    public synchronized void onRawGazeReceived(float pitch, float yaw, boolean blink) {
        if (!enabled) return;

        packetsReceived++;

        double now = System.nanoTime() / 1_000_000_000.0;
        float fPitch = (float) pitchFilter.filter(pitch, now);
        float fYaw = (float) yawFilter.filter(yaw, now);

        gazeData.update(pitch, yaw, fPitch, fYaw, blink);
    }

    public synchronized void onPitchReceived(float pitch) {
        if (!enabled) return;
        packetsReceived++;
        this.leftPitch = pitch;
        this.rightPitch = pitch;

        double now = System.nanoTime() / 1_000_000_000.0;
        float fPitch = (float) pitchFilter.filter(pitch, now);
        gazeData.updatePitch(pitch, fPitch, eyelid < 0.2f);
    }

    public synchronized void onYawReceived(float yaw) {
        if (!enabled) return;
        packetsReceived++;
        this.leftYaw = yaw;
        this.rightYaw = yaw;

        double now = System.nanoTime() / 1_000_000_000.0;
        float fYaw = (float) yawFilter.filter(yaw, now);
        gazeData.updateYaw(yaw, fYaw, eyelid < 0.2f);
    }

    public synchronized void onLeftPitchReceived(float p) {
        if (!enabled) return;
        this.leftPitch = p;
        float avgPitch = (leftPitch + rightPitch) * 0.5f;
        double now = System.nanoTime() / 1_000_000_000.0;
        float fPitch = (float) pitchFilter.filter(avgPitch, now);
        gazeData.updatePitch(avgPitch, fPitch, eyelid < 0.2f);
    }

    public synchronized void onRightPitchReceived(float p) {
        if (!enabled) return;
        this.rightPitch = p;
        float avgPitch = (leftPitch + rightPitch) * 0.5f;
        double now = System.nanoTime() / 1_000_000_000.0;
        float fPitch = (float) pitchFilter.filter(avgPitch, now);
        gazeData.updatePitch(avgPitch, fPitch, eyelid < 0.2f);
    }

    public synchronized void onLeftYawReceived(float y) {
        if (!enabled) return;
        this.leftYaw = y;
        float avgYaw = (leftYaw + rightYaw) * 0.5f;
        double now = System.nanoTime() / 1_000_000_000.0;
        float fYaw = (float) yawFilter.filter(avgYaw, now);
        gazeData.updateYaw(avgYaw, fYaw, eyelid < 0.2f);
    }

    public synchronized void onRightYawReceived(float y) {
        if (!enabled) return;
        this.rightYaw = y;
        float avgYaw = (leftYaw + rightYaw) * 0.5f;
        double now = System.nanoTime() / 1_000_000_000.0;
        float fYaw = (float) yawFilter.filter(avgYaw, now);
        gazeData.updateYaw(avgYaw, fYaw, eyelid < 0.2f);
    }

    public synchronized void onEyeLidReceived(float openness) {
        this.eyelid = openness;
        gazeData.blink = (openness < 0.2f);
    }

    public synchronized boolean isTrackingActive() {
        if (trackingMode == TrackingMode.OFF) return false;
        if (trackingMode == TrackingMode.HEAD_TRACKING) return true;
        return simulationMode || gazeData.isFresh(1500);
    }

    public synchronized void recenter() {
        this.offsetPitch = -gazeData.filteredPitch;
        this.offsetYaw = -gazeData.filteredYaw;
    }

    public synchronized void recenter(float targetYawDeg, float targetPitchDeg) {
        this.offsetYaw = targetYawDeg - gazeData.filteredYaw;
        this.offsetPitch = -targetPitchDeg - gazeData.filteredPitch;
    }

    private void startSimulationThread() {
        Thread t = new Thread(() -> {
            double time = 0.0;
            while (true) {
                try {
                    Thread.sleep(16);
                    if (simulationMode && enabled) {
                        time += 0.03;
                        float simYaw = (float) (Math.sin(time) * 12.0);
                        float simPitch = (float) (Math.cos(time * 0.7) * 8.0);
                        onRawGazeReceived(simPitch, simYaw, false);
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }
        }, "Sclera-Simulation-Generator");
        t.setDaemon(true);
        t.start();
    }

    public synchronized GazeData getGazeData() { return gazeData; }

    public TrackingMode getTrackingMode() { return trackingMode; }
    public void setTrackingMode(TrackingMode mode) {
        this.trackingMode = mode != null ? mode : TrackingMode.EYE_TRACKING;
        if (this.trackingMode == TrackingMode.OFF) {
            this.handsInMenus = true;
        }
    }
    public void cycleTrackingMode() {
        this.trackingMode = this.trackingMode.next();
        if (this.trackingMode == TrackingMode.OFF) {
            this.handsInMenus = true;
        }
    }

    public boolean isEnabled() { return trackingMode != TrackingMode.OFF; }
    public void setEnabled(boolean enabled) {
        setTrackingMode(enabled ? TrackingMode.EYE_TRACKING : TrackingMode.OFF);
    }

    public boolean isGazeInMenus() { return gazeInMenus; }
    public void setGazeInMenus(boolean enabled) {
        this.gazeInMenus = enabled;
        // Safety constraint: At least one must be active in Menus!
        if (!this.gazeInMenus && !this.handsInMenus) {
            this.handsInMenus = true;
        }
    }

    public boolean isHandsInMenus() { return handsInMenus; }
    public void setHandsInMenus(boolean enabled) {
        this.handsInMenus = enabled;
        // Safety constraint: At least one must be active in Menus!
        if (!this.handsInMenus && !this.gazeInMenus) {
            this.gazeInMenus = true;
        }
    }

    public boolean isGazeInInventory() { return gazeInInventory; }
    public void setGazeInInventory(boolean enabled) { this.gazeInInventory = enabled; }

    public boolean isHandsInInventory() { return handsInInventory; }
    public void setHandsInInventory(boolean enabled) { this.handsInInventory = enabled; }

    public boolean isGazeInWorld() { return gazeInWorld; }
    public void setGazeInWorld(boolean enabled) { this.gazeInWorld = enabled; }

    public boolean isHandsInWorld() { return handsInWorld; }
    public void setHandsInWorld(boolean enabled) { this.handsInWorld = enabled; }

    public boolean isHandsEnabled() { return handsInMenus || handsInInventory || handsInWorld; }
    public void setHandsEnabled(boolean handsEnabled) {
        this.handsInMenus = handsEnabled;
        this.handsInInventory = handsEnabled;
        this.handsInWorld = handsEnabled;
        if (!this.handsInMenus && !this.gazeInMenus) {
            this.gazeInMenus = true;
        }
    }

    public CrosshairMode getCrosshairMode() { return crosshairMode; }
    public void setCrosshairMode(CrosshairMode mode) {
        this.crosshairMode = mode != null ? mode : CrosshairMode.ENABLED;
        this.subtlePointerEnabled = (this.crosshairMode == CrosshairMode.ENABLED);
        this.debugPointerEnabled = (this.crosshairMode == CrosshairMode.DEBUG);
    }
    public void cycleCrosshairMode() {
        setCrosshairMode(this.crosshairMode.next());
    }

    public boolean isSubtlePointerEnabled() { return crosshairMode == CrosshairMode.ENABLED; }
    public void setSubtlePointerEnabled(boolean enabled) {
        if (enabled) {
            setCrosshairMode(CrosshairMode.ENABLED);
        } else if (crosshairMode == CrosshairMode.ENABLED) {
            setCrosshairMode(CrosshairMode.DISABLED);
        }
    }

    public boolean isDebugPointerEnabled() { return crosshairMode == CrosshairMode.DEBUG; }
    public void setDebugPointerEnabled(boolean enabled) {
        if (enabled) {
            setCrosshairMode(CrosshairMode.DEBUG);
        } else if (crosshairMode == CrosshairMode.DEBUG) {
            setCrosshairMode(CrosshairMode.DISABLED);
        }
    }

    public boolean isBlinkInMenus() { return blinkInMenus; }
    public void setBlinkInMenus(boolean enabled) { this.blinkInMenus = enabled; }

    public boolean isBlinkInInventory() { return blinkInInventory; }
    public void setBlinkInInventory(boolean enabled) { this.blinkInInventory = enabled; }

    public BlinkWorldAction getBlinkWorldAction() { return blinkWorldAction; }
    public void setBlinkWorldAction(BlinkWorldAction action) {
        this.blinkWorldAction = action != null ? action : BlinkWorldAction.OFF;
    }
    public void cycleBlinkWorldAction() {
        setBlinkWorldAction(this.blinkWorldAction.next());
    }

    public boolean isBowGazeAimEnabled() { return bowGazeAimEnabled; }
    public void setBowGazeAimEnabled(boolean bowGazeAimEnabled) { this.bowGazeAimEnabled = bowGazeAimEnabled; }

    public WorldInteractionMode getWorldInteractionMode() {
        if (gazeInWorld && handsInWorld) return WorldInteractionMode.HYBRID;
        if (gazeInWorld && !handsInWorld) return WorldInteractionMode.GAZE_ONLY;
        if (!gazeInWorld && handsInWorld) return WorldInteractionMode.HANDS_ONLY;
        if (!gazeInWorld && !handsInWorld) return WorldInteractionMode.OFF;
        return WorldInteractionMode.CUSTOM;
    }
    public void setWorldInteractionMode(WorldInteractionMode mode) {
        if (mode == null) mode = WorldInteractionMode.HYBRID;
        switch (mode) {
            case HYBRID:
                this.gazeInWorld = true;
                this.handsInWorld = true;
                break;
            case GAZE_ONLY:
                this.gazeInWorld = true;
                this.handsInWorld = false;
                break;
            case HANDS_ONLY:
                this.gazeInWorld = false;
                this.handsInWorld = true;
                break;
            case OFF:
                this.gazeInWorld = false;
                this.handsInWorld = false;
                break;
            case CUSTOM:
            default:
                this.gazeInWorld = true;
                this.handsInWorld = true;
                break;
        }
    }
    public void cycleWorldInteractionMode() {
        setWorldInteractionMode(getWorldInteractionMode().next());
    }

    public boolean isInGameTargetingEnabled() {
        return gazeInWorld;
    }
    public void setInGameTargetingEnabled(boolean inGameTargetingEnabled) {
        this.gazeInWorld = inGameTargetingEnabled;
    }

    public boolean shouldUseGazeInWorld(VRPlayerPoseClient poseClient, HandType activeHand) {
        if (!isTrackingActive() || !gazeInWorld) return false;
        if (!handsInWorld) return true;

        MinecraftClient mc = MinecraftClient.getInstance();
        boolean isActionActive = false;
        if (mc != null) {
            boolean attackPressed = mc.options != null && mc.options.attackKey.isPressed();
            boolean usePressed = mc.options != null && mc.options.useKey.isPressed();
            boolean mouseLeft = InputHelper.isMousePressed(MouseButtonType.LEFT);
            boolean mouseRight = InputHelper.isMousePressed(MouseButtonType.RIGHT);
            boolean breaking = mc.interactionManager != null && mc.interactionManager.isBreakingBlock();
            isActionActive = attackPressed || usePressed || mouseLeft || mouseRight || breaking;
        }

        // If an interaction/mining is currently active
        if (isActionActive) {
            // If it was initiated with gaze, keep gaze locked until action stops
            if (GazeCursorHandler.getInstance().isGazeInteracting()) {
                return true;
            }
        } else {
            GazeCursorHandler.getInstance().setGazeInteracting(false);
        }

        boolean handMoving = GazeCursorHandler.getInstance().isHandMoving(poseClient, activeHand);
        if (handMoving) {
            GazeCursorHandler.getInstance().setGazeInteracting(false);
            return false;
        } else {
            if (isActionActive) {
                GazeCursorHandler.getInstance().setGazeInteracting(true);
            }
            return true;
        }
    }

    public float getSensitivity() { return sensitivity; }
    public void setSensitivity(float sensitivity) { this.sensitivity = 1.0f; }

    public float getOffsetPitch() { return offsetPitch; }
    public void setOffsetPitch(float offsetPitch) { this.offsetPitch = offsetPitch; }

    public float getOffsetYaw() { return offsetYaw; }
    public void setOffsetYaw(float offsetYaw) { this.offsetYaw = offsetYaw; }

    public long getPacketsReceived() { return packetsReceived; }

    public boolean isSimulationMode() { return simulationMode; }
    public void setSimulationMode(boolean simulationMode) { this.simulationMode = simulationMode; }

    public void resetCalibration() {
        this.offsetPitch = 0.0f;
        this.offsetYaw = 0.0f;
    }

    public void resetDefaults() {
        this.trackingMode = TrackingMode.EYE_TRACKING;
        this.enabled = true;
        this.crosshairMode = CrosshairMode.ENABLED;
        this.subtlePointerEnabled = true;
        this.debugPointerEnabled = false;
        this.bowGazeAimEnabled = true;
        this.worldInteractionMode = WorldInteractionMode.HYBRID;
        this.gazeInMenus = true;
        this.gazeInInventory = true;
        this.gazeInWorld = true;
        this.handsInMenus = true;
        this.handsInInventory = true;
        this.handsInWorld = true;
        this.blinkInMenus = false;
        this.blinkInInventory = false;
        this.blinkWorldAction = BlinkWorldAction.OFF;
    }
}