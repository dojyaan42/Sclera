package org.sclera.tracking;

import net.minecraft.client.MinecraftClient;
import org.sclera.filter.OneEuroFilter;
import org.sclera.ui.GazeCursorHandler;
import org.sclera.haptics.AdaptiveBowTriggers;
import org.sclera.config.ScleraClientConfig;
import java.util.function.Consumer;
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
    private final Consumer<Boolean> nativeEyeTracking;
    private final Runnable releaseBowTrigger;
    private ScleraClientConfig preferences;

    private final OneEuroFilter pitchFilter = new OneEuroFilter(1.2, 0.005, 1.0);
    private final OneEuroFilter yawFilter = new OneEuroFilter(1.2, 0.005, 1.0);

    private final GazeData gazeData = new GazeData();
    private OscGazeReceiver oscReceiver1;
    private OscGazeReceiver oscReceiver2;

    public enum TrackingMode {
        EYE_TRACKING("§aEyes"),
        HEAD_TRACKING("§eHead"),
        OFF("§cOff");

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

    public enum EyeTrackingSource {
        VRCFT("VRCFT"), PSVR2("PSVR2");

        private final String label;
        EyeTrackingSource(String label) { this.label = label; }
        public String getLabel() { return label; }
        public EyeTrackingSource next() { return this == VRCFT ? PSVR2 : VRCFT; }
    }

    public enum WorldInteractionMode {
        HYBRID("§bHybrid"),
        GAZE_ONLY("§bEyes Only"),
        HANDS_ONLY("§bHands Only"),
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
                    case CUSTOM: return "§7Custom";
                }
            }
            return this.label;
        }

        public WorldInteractionMode next() {
            switch (this) {
                case HYBRID: return GAZE_ONLY;
                case GAZE_ONLY: return HANDS_ONLY;
                case HANDS_ONLY: return HYBRID;
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
    private boolean adaptiveBowTriggerEnabled;
    private volatile EyeTrackingSource eyeTrackingSource = EyeTrackingSource.VRCFT;
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
    public static final float DEFAULT_UNCALIBRATED_PITCH_OFFSET = 1.0f;
    public static final float HEAD_TRACKING_PITCH_OFFSET_DEG = 2.5f;

    private float offsetPitch = DEFAULT_UNCALIBRATED_PITCH_OFFSET;
    private float offsetYaw = 0.0f;

    private long packetsReceived = 0;
    private volatile boolean simulationMode = false;
    private Thread simulationThread;
    private final long[] componentTimes = new long[4]; // left pitch/yaw, right pitch/yaw
    private int pendingComponents;

    // Partial values for multi-packet assembly
    private float leftYaw = 0f, rightYaw = 0f;
    private float leftPitch = 0f, rightPitch = 0f;
    private float eyelid = 1.0f;
    private float leftEyeLid = 1f, rightEyeLid = 1f;

    // Foveated Rendering Atomic Pipeline & Port Arbitration
    private final java.util.concurrent.atomic.AtomicReference<RenderGazeSample> latestRenderSample =
            new java.util.concurrent.atomic.AtomicReference<>(RenderGazeSample.invalid());
    private long lastSampleTimestampNanos = 0;
    private float lastSamplePitch = 0f;
    private float lastSampleYaw = 0f;
    private int activePort = 0;
    private long lastActivePortPacketNanos = 0;

    public EyeTrackingManager() {
        this(active -> AdaptiveBowTriggers.getInstance().setEyeTrackingEnabled(active),
                () -> AdaptiveBowTriggers.getInstance().release());
    }

    /** Allows configuration checks to run without initializing native hardware. */
    public EyeTrackingManager(Consumer<Boolean> nativeEyeTracking, Runnable releaseBowTrigger) {
        this.nativeEyeTracking = nativeEyeTracking;
        this.releaseBowTrigger = releaseBowTrigger;
        INSTANCE = this;
    }

    /** Restore saved choices before startup hardware detection, bypassing runtime input guards. */
    public synchronized void attachPreferences(ScleraClientConfig config) {
        if (preferences != null) return;
        ScleraClientConfig.TrackingPreferences saved = config.getTrackingPreferences();
        trackingMode = saved.mode != null ? saved.mode : TrackingMode.EYE_TRACKING;
        crosshairMode = saved.crosshair != null ? saved.crosshair : CrosshairMode.ENABLED;
        subtlePointerEnabled = crosshairMode == CrosshairMode.ENABLED;
        debugPointerEnabled = crosshairMode == CrosshairMode.DEBUG;
        bowGazeAimEnabled = saved.bowGazeAim;
        adaptiveBowTriggerEnabled = saved.bowTrigger;
        gazeInMenus = saved.gazeInMenus;
        gazeInInventory = saved.gazeInInventory;
        gazeInWorld = saved.gazeInWorld;
        handsInMenus = saved.handsInMenus;
        handsInInventory = saved.handsInInventory;
        handsInWorld = saved.handsInWorld;
        blinkInMenus = saved.blinkInMenus;
        blinkInInventory = saved.blinkInInventory;
        blinkWorldAction = saved.blinkAction != null ? saved.blinkAction : BlinkWorldAction.OFF;
        offsetPitch = Float.isFinite(saved.offsetPitch) ? saved.offsetPitch : DEFAULT_UNCALIBRATED_PITCH_OFFSET;
        offsetYaw = Float.isFinite(saved.offsetYaw) ? saved.offsetYaw : 0;
        preferences = config;
    }

    private synchronized void savePreferences() {
        if (preferences == null) return;
        ScleraClientConfig.TrackingPreferences saved = new ScleraClientConfig.TrackingPreferences();
        saved.mode = trackingMode;
        saved.crosshair = crosshairMode;
        saved.bowGazeAim = bowGazeAimEnabled;
        saved.bowTrigger = adaptiveBowTriggerEnabled;
        saved.gazeInMenus = gazeInMenus;
        saved.gazeInInventory = gazeInInventory;
        saved.gazeInWorld = gazeInWorld;
        saved.handsInMenus = handsInMenus;
        saved.handsInInventory = handsInInventory;
        saved.handsInWorld = handsInWorld;
        saved.blinkInMenus = blinkInMenus;
        saved.blinkInInventory = blinkInInventory;
        saved.blinkAction = blinkWorldAction;
        saved.offsetPitch = offsetPitch;
        saved.offsetYaw = offsetYaw;
        preferences.setTrackingPreferences(saved);
    }

    public static EyeTrackingManager getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new EyeTrackingManager();
        }
        return INSTANCE;
    }

    public synchronized boolean acceptPacketFromPort(int port) {
        if (eyeTrackingSource != EyeTrackingSource.VRCFT) return false;
        long now = System.nanoTime();
        if (activePort == 0 || activePort == port || (now - lastActivePortPacketNanos) > 500_000_000L) {
            if (activePort != port) {
                pendingComponents = 0;
                java.util.Arrays.fill(componentTimes, 0L);
                latestRenderSample.set(RenderGazeSample.invalid());
                eyelid = 1f;
                leftEyeLid = rightEyeLid = 1f;
            }
            activePort = port;
            lastActivePortPacketNanos = now;
            return true;
        }
        return false;
    }

    public synchronized void onBinocularGazeReceived(float leftP, float leftY, boolean leftValid, float rightP, float rightY, boolean rightValid) {
        if (!enabled) return;

        long now = System.nanoTime();
        float curPitch = leftValid ? leftP : rightP;
        float curYaw = leftValid ? leftY : rightY;

        float angularVelocity = 0f;
        boolean saccade = false;
        if (lastSampleTimestampNanos != 0 && now > lastSampleTimestampNanos) {
            float dt = (now - lastSampleTimestampNanos) / 1_000_000_000.0f;
            if (dt > 0.005f && dt < 0.2f) {
                float dPitch = curPitch - lastSamplePitch;
                float dYaw = curYaw - lastSampleYaw;
                float dAngle = (float) Math.hypot(dPitch, dYaw);
                angularVelocity = dAngle / dt;
                saccade = (dAngle >= 3.5f) && (angularVelocity >= RenderGazeSample.SACCADE_THRESHOLD_DEG_PER_SEC);
            }
        }
        lastSampleTimestampNanos = now;
        lastSamplePitch = curPitch;
        lastSampleYaw = curYaw;
        RenderGazeSample sample = new RenderGazeSample(
                now,
                leftP, leftY,
                rightP, rightY,
                leftValid, rightValid,
                leftEyeLid, rightEyeLid,
                angularVelocity,
                saccade
        );
        latestRenderSample.set(sample);
    }

    public RenderGazeSample getLatestRenderGazeSample() {
        RenderGazeSample sample = latestRenderSample.get();
        if (sample != null && sample.isFresh(150_000_000L)) { // 150ms in nanos
            return sample;
        }
        return RenderGazeSample.invalid();
    }

    public synchronized RenderGazeSample getCalibratedRenderSample() {
        if (!enabled || trackingMode != TrackingMode.EYE_TRACKING) return RenderGazeSample.invalid();
        // Use the cursor's filtered, calibrated direction for both eyes, latched once per scene.
        return latestRenderSample.get().withSharedFilteredDirection(
                (gazeData.filteredPitch + offsetPitch) * sensitivity,
                (gazeData.filteredYaw + offsetYaw) * sensitivity);
    }

    private void publishComponents(int changed) {
        long now = System.nanoTime();
        for (int i = 0; i < 4; i++) if ((changed & (1 << i)) != 0) componentTimes[i] = now;
        pendingComponents |= changed;
        boolean leftReady = (pendingComponents & 3) == 3;
        boolean rightReady = (pendingComponents & 12) == 12;
        if (!leftReady && !rightReady) return;
        boolean leftFresh = componentTimes[0] != 0 && componentTimes[1] != 0
                && now - componentTimes[0] <= 150_000_000L && now - componentTimes[1] <= 150_000_000L;
        boolean rightFresh = componentTimes[2] != 0 && componentTimes[3] != 0
                && now - componentTimes[2] <= 150_000_000L && now - componentTimes[3] <= 150_000_000L;
        if (leftReady) pendingComponents &= ~3;
        if (rightReady) pendingComponents &= ~12;
        onBinocularGazeReceived(leftPitch, leftYaw, leftFresh, rightPitch, rightYaw, rightFresh);
        latestRenderSample.set(latestRenderSample.get().withEyeTimes(
                Math.min(componentTimes[0], componentTimes[1]), Math.min(componentTimes[2], componentTimes[3])));
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

    public synchronized void onCursorGazeReceived(float pitch, float yaw, boolean blink) {
        if (!enabled) return;

        packetsReceived++;

        double now = System.nanoTime() / 1_000_000_000.0;
        float fPitch = (float) pitchFilter.filter(pitch, now);
        float fYaw = (float) yawFilter.filter(yaw, now);

        gazeData.update(pitch, yaw, fPitch, fYaw, blink);
    }

    public synchronized void onRawGazeReceived(float pitch, float yaw, boolean blink) {
        if (!enabled || eyeTrackingSource != EyeTrackingSource.VRCFT) return;
        eyelid = blink ? 0f : 1f;
        leftEyeLid = rightEyeLid = eyelid;
        onCursorGazeReceived(pitch, yaw, blink);

        this.leftPitch = pitch;
        this.rightPitch = pitch;
        this.leftYaw = yaw;
        this.rightYaw = yaw;
        onBinocularGazeReceived(pitch, yaw, true, pitch, yaw, true);
    }

    public synchronized void onPsvr2GazeReceived(AdaptiveBowTriggers.EyeSample left,
            AdaptiveBowTriggers.EyeSample right, AdaptiveBowTriggers.EyeSample combined) {
        if (!enabled || eyeTrackingSource != EyeTrackingSource.PSVR2) return;
        boolean leftValid = left.valid;
        boolean rightValid = right.valid;

        boolean leftBlinkValid = left.blinkValid;
        boolean rightBlinkValid = right.blinkValid;
        boolean blinkValid = leftBlinkValid || rightBlinkValid;
        boolean blink = blinkValid && (!leftBlinkValid || left.blink) && (!rightBlinkValid || right.blink);
        leftEyeLid = leftBlinkValid && left.blink ? 0f : 1f;
        rightEyeLid = rightBlinkValid && right.blink ? 0f : 1f;
        eyelid = blink ? 0f : 1f;
        gazeData.blink = blink;

        if (!combined.valid && !leftValid && !rightValid) return;

        float pitch = combined.valid ? combined.pitch : leftValid ? left.pitch : right.pitch;
        float yaw = combined.valid ? combined.yaw : leftValid ? left.yaw : right.yaw;
        double now = System.nanoTime() / 1_000_000_000.0;
        float filteredPitch = (float) pitchFilter.filter(pitch, now);
        float filteredYaw = (float) yawFilter.filter(yaw, now);
        gazeData.update(pitch, yaw, filteredPitch, filteredYaw, blink);
        packetsReceived++;
        onBinocularGazeReceived(left.pitch, left.yaw, leftValid, right.pitch, right.yaw, rightValid);
    }

    public EyeTrackingSource getEyeTrackingSource() { return eyeTrackingSource; }

    public synchronized void setEyeTrackingSource(EyeTrackingSource source) {
        EyeTrackingSource next = source == null ? EyeTrackingSource.VRCFT : source;
        if (eyeTrackingSource == next) return;
        eyeTrackingSource = next;
        if (next == EyeTrackingSource.PSVR2) {
            pitchFilter.setParameters(0.75, 0.005);
            yawFilter.setParameters(0.75, 0.005);
        } else {
            pitchFilter.setParameters(1.2, 0.005);
            yawFilter.setParameters(1.2, 0.005);
        }
        latestRenderSample.set(RenderGazeSample.invalid());
        lastSampleTimestampNanos = 0;
        pitchFilter.reset();
        yawFilter.reset();
        gazeData.invalidate();
        pendingComponents = 0;
        java.util.Arrays.fill(componentTimes, 0L);
        nativeEyeTracking.accept(next == EyeTrackingSource.PSVR2);
        if (next != EyeTrackingSource.PSVR2) {
            releaseBowTrigger.run();
        }
    }

    public synchronized void onPitchReceived(float pitch) {
        if (!enabled) return;
        packetsReceived++;
        this.leftPitch = pitch;
        this.rightPitch = pitch;

        double now = System.nanoTime() / 1_000_000_000.0;
        float fPitch = (float) pitchFilter.filter(pitch, now);
        gazeData.updatePitch(pitch, fPitch, eyelid < 0.2f);

        publishComponents(5);
    }

    public synchronized void onYawReceived(float yaw) {
        if (!enabled) return;
        packetsReceived++;
        this.leftYaw = yaw;
        this.rightYaw = yaw;

        double now = System.nanoTime() / 1_000_000_000.0;
        float fYaw = (float) yawFilter.filter(yaw, now);
        gazeData.updateYaw(yaw, fYaw, eyelid < 0.2f);

        publishComponents(10);
    }

    public synchronized void onLeftPitchReceived(float p) {
        if (!enabled) return;
        this.leftPitch = p;
        float avgPitch = (leftPitch + rightPitch) * 0.5f;
        double now = System.nanoTime() / 1_000_000_000.0;
        float fPitch = (float) pitchFilter.filter(avgPitch, now);
        gazeData.updatePitch(avgPitch, fPitch, eyelid < 0.2f);

        publishComponents(1);
    }

    public synchronized void onRightPitchReceived(float p) {
        if (!enabled) return;
        this.rightPitch = p;
        float avgPitch = (leftPitch + rightPitch) * 0.5f;
        double now = System.nanoTime() / 1_000_000_000.0;
        float fPitch = (float) pitchFilter.filter(avgPitch, now);
        gazeData.updatePitch(avgPitch, fPitch, eyelid < 0.2f);

        publishComponents(4);
    }

    public synchronized void onLeftYawReceived(float y) {
        if (!enabled) return;
        this.leftYaw = y;
        float avgYaw = (leftYaw + rightYaw) * 0.5f;
        double now = System.nanoTime() / 1_000_000_000.0;
        float fYaw = (float) yawFilter.filter(avgYaw, now);
        gazeData.updateYaw(avgYaw, fYaw, eyelid < 0.2f);

        publishComponents(2);
    }

    public synchronized void onRightYawReceived(float y) {
        if (!enabled) return;
        this.rightYaw = y;
        float avgYaw = (leftYaw + rightYaw) * 0.5f;
        double now = System.nanoTime() / 1_000_000_000.0;
        float fYaw = (float) yawFilter.filter(avgYaw, now);
        gazeData.updateYaw(avgYaw, fYaw, eyelid < 0.2f);

        publishComponents(8);
    }

    public synchronized void onEyeLidReceived(float openness) {
        leftEyeLid = rightEyeLid = openness;
        updateEyeLids();
    }

    public synchronized void onEyeLidReceived(boolean left, float openness) {
        if (left) leftEyeLid = openness; else rightEyeLid = openness;
        updateEyeLids();
    }

    private void updateEyeLids() {
        float openness = (leftEyeLid + rightEyeLid) * .5f;
        this.eyelid = openness;
        gazeData.blink = (openness < 0.2f);
        RenderGazeSample cur = latestRenderSample.get();
        if (cur != null) {
            RenderGazeSample updated = new RenderGazeSample(
                    cur.getTimestampNanos(),
                    cur.getLeftPitch(), cur.getLeftYaw(),
                    cur.getRightPitch(), cur.getRightYaw(),
                    cur.isLeftValid(), cur.isRightValid(),
                    leftEyeLid, rightEyeLid,
                    cur.getAngularVelocityDegPerSec(),
                    cur.isSaccade(), cur.timestampForEye(true), cur.timestampForEye(false)
            );
            latestRenderSample.set(updated);
        }
    }

    public synchronized boolean isTrackingActive() {
        if (trackingMode == TrackingMode.OFF) return false;
        if (trackingMode == TrackingMode.HEAD_TRACKING) return true;
        return simulationMode || gazeData.isFresh(1500);
    }

    public synchronized void recenter() {
        this.offsetPitch = -gazeData.filteredPitch;
        this.offsetYaw = -gazeData.filteredYaw;
        savePreferences();
    }

    public synchronized void recenter(float targetYawDeg, float targetPitchDeg) {
        this.offsetYaw = targetYawDeg - gazeData.filteredYaw;
        this.offsetPitch = -targetPitchDeg - gazeData.filteredPitch;
        savePreferences();
    }

    /**
     * Unified calibrated gaze direction in degrees [pitch, yaw].
     * Shared across all Sclera systems: menus, crosshair, and Foveated Rendering.
     * When HEAD_TRACKING is active, returns (0, 0) (headset center).
     */
    public synchronized boolean getCalibratedGaze(float[] outPitchYaw) {
        if (!enabled || trackingMode == TrackingMode.OFF) {
            return false;
        }
        if (trackingMode == TrackingMode.HEAD_TRACKING) {
            outPitchYaw[0] = -HEAD_TRACKING_PITCH_OFFSET_DEG;
            outPitchYaw[1] = 0.0f;
            return true;
        }
        if (!isTrackingActive()) {
            return false;
        }
        outPitchYaw[0] = (gazeData.filteredPitch + offsetPitch) * sensitivity;
        outPitchYaw[1] = (gazeData.filteredYaw + offsetYaw) * sensitivity;
        return true;
    }

    private void startSimulationThread() {
        Thread t = new Thread(() -> {
            double time = 0.0;
            while (simulationMode) {
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
        simulationThread = t;
        t.start();
    }

    public synchronized GazeData getGazeData() { return new GazeData(gazeData); }

    public TrackingMode getTrackingMode() { return trackingMode; }
    public synchronized void setTrackingMode(TrackingMode mode) {
        this.trackingMode = mode != null ? mode : TrackingMode.EYE_TRACKING;
        savePreferences();
    }
    public synchronized void cycleTrackingMode() {
        setTrackingMode(this.trackingMode.next());
    }

    public synchronized boolean mustUseHandsOnly() {
        return trackingMode == TrackingMode.OFF
                || (trackingMode == TrackingMode.EYE_TRACKING && !isTrackingActive());
    }

    public boolean isEnabled() { return trackingMode != TrackingMode.OFF; }
    public void setEnabled(boolean enabled) {
        setTrackingMode(enabled ? TrackingMode.EYE_TRACKING : TrackingMode.OFF);
    }

    public boolean isGazeInMenus() { return gazeInMenus; }
    public void setGazeInMenus(boolean enabled) {
        if (mustUseHandsOnly()) return;
        this.gazeInMenus = enabled;
        if (!this.gazeInMenus && !this.handsInMenus) {
            this.handsInMenus = true;
        }
        savePreferences();
    }

    public boolean isHandsInMenus() { return handsInMenus; }
    public void setHandsInMenus(boolean enabled) {
        if (mustUseHandsOnly()) return;
        this.handsInMenus = enabled;
        if (!this.handsInMenus && !this.gazeInMenus) {
            this.gazeInMenus = true;
        }
        savePreferences();
    }

    public boolean isGazeInInventory() { return gazeInInventory; }
    public void setGazeInInventory(boolean enabled) {
        if (mustUseHandsOnly()) return;
        this.gazeInInventory = enabled;
        if (!this.gazeInInventory && !this.handsInInventory) this.handsInInventory = true;
        savePreferences();
    }

    public boolean isHandsInInventory() { return handsInInventory; }
    public void setHandsInInventory(boolean enabled) {
        if (mustUseHandsOnly()) return;
        this.handsInInventory = enabled;
        if (!this.handsInInventory && !this.gazeInInventory) this.gazeInInventory = true;
        savePreferences();
    }

    public boolean isGazeInWorld() { return gazeInWorld; }
    public void setGazeInWorld(boolean enabled) {
        if (mustUseHandsOnly()) return;
        this.gazeInWorld = enabled;
        if (!this.gazeInWorld && !this.handsInWorld) this.handsInWorld = true;
        savePreferences();
    }

    public boolean isHandsInWorld() { return handsInWorld; }
    public void setHandsInWorld(boolean enabled) {
        if (mustUseHandsOnly()) return;
        this.handsInWorld = enabled;
        if (!this.handsInWorld && !this.gazeInWorld) this.gazeInWorld = true;
        savePreferences();
    }

    public boolean isHandsEnabled() { return handsInMenus || handsInInventory || handsInWorld; }
    public void setHandsEnabled(boolean handsEnabled) {
        this.handsInMenus = handsEnabled;
        this.handsInInventory = handsEnabled;
        this.handsInWorld = handsEnabled;
        if (!this.handsInMenus && !this.gazeInMenus) {
            this.gazeInMenus = true;
        }
        savePreferences();
    }

    public CrosshairMode getCrosshairMode() { return crosshairMode; }
    public void setCrosshairMode(CrosshairMode mode) {
        this.crosshairMode = mode != null ? mode : CrosshairMode.ENABLED;
        this.subtlePointerEnabled = (this.crosshairMode == CrosshairMode.ENABLED);
        this.debugPointerEnabled = (this.crosshairMode == CrosshairMode.DEBUG);
        savePreferences();
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
    public void setBlinkInMenus(boolean enabled) { this.blinkInMenus = enabled; savePreferences(); }

    public boolean isBlinkInInventory() { return blinkInInventory; }
    public void setBlinkInInventory(boolean enabled) { this.blinkInInventory = enabled; savePreferences(); }

    public BlinkWorldAction getBlinkWorldAction() { return blinkWorldAction; }
    public void setBlinkWorldAction(BlinkWorldAction action) {
        this.blinkWorldAction = action != null ? action : BlinkWorldAction.OFF;
        savePreferences();
    }
    public void cycleBlinkWorldAction() {
        setBlinkWorldAction(this.blinkWorldAction.next());
    }

    public boolean isBowGazeAimEnabled() { return bowGazeAimEnabled; }
    public void setBowGazeAimEnabled(boolean bowGazeAimEnabled) { this.bowGazeAimEnabled = bowGazeAimEnabled; savePreferences(); }

    public WorldInteractionMode getWorldInteractionMode() {
        if (mustUseHandsOnly()) return WorldInteractionMode.HANDS_ONLY;
        if (gazeInWorld && handsInWorld) return WorldInteractionMode.HYBRID;
        if (gazeInWorld && !handsInWorld) return WorldInteractionMode.GAZE_ONLY;
        if (!gazeInWorld && handsInWorld) return WorldInteractionMode.HANDS_ONLY;
        return WorldInteractionMode.CUSTOM;
    }
    public void setWorldInteractionMode(WorldInteractionMode mode) {
        if (mustUseHandsOnly()) return;
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
            case CUSTOM:
            default:
                this.gazeInWorld = true;
                this.handsInWorld = true;
                break;
        }
        savePreferences();
    }
    public void cycleWorldInteractionMode() {
        if (mustUseHandsOnly()) return;
        setWorldInteractionMode(getWorldInteractionMode().next());
    }

    public boolean isInGameTargetingEnabled() {
        return gazeInWorld;
    }
    public void setInGameTargetingEnabled(boolean inGameTargetingEnabled) {
        this.gazeInWorld = inGameTargetingEnabled;
        savePreferences();
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
    public synchronized void setOffsetPitch(float offsetPitch) { this.offsetPitch = offsetPitch; savePreferences(); }

    public float getOffsetYaw() { return offsetYaw; }
    public synchronized void setOffsetYaw(float offsetYaw) { this.offsetYaw = offsetYaw; savePreferences(); }

    public long getPacketsReceived() { return packetsReceived; }

    public boolean isSimulationMode() { return simulationMode; }
    public synchronized void setSimulationMode(boolean simulationMode) {
        if (this.simulationMode == simulationMode) return;
        this.simulationMode = simulationMode;
        if (simulationMode) startSimulationThread();
        else if (simulationThread != null) simulationThread.interrupt();
    }

    public synchronized void resetCalibration() {
        this.offsetPitch = DEFAULT_UNCALIBRATED_PITCH_OFFSET;
        this.offsetYaw = 0.0f;
        savePreferences();
    }

    public void resetDefaults() {
        this.trackingMode = TrackingMode.EYE_TRACKING;
        this.enabled = true;
        this.crosshairMode = CrosshairMode.ENABLED;
        this.subtlePointerEnabled = true;
        this.debugPointerEnabled = false;
        this.bowGazeAimEnabled = true;
        this.adaptiveBowTriggerEnabled = false;
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
        releaseBowTrigger.run();
        savePreferences();
    }

    public boolean isAdaptiveBowTriggerEnabled() {
        return eyeTrackingSource == EyeTrackingSource.PSVR2 && adaptiveBowTriggerEnabled;
    }

    public void setAdaptiveBowTriggerEnabled(boolean enabled) {
        this.adaptiveBowTriggerEnabled = enabled;
        if (!enabled) releaseBowTrigger.run();
        savePreferences();
    }
}
