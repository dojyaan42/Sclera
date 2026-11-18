package org.sclera.haptics;

import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Structure;
import org.sclera.tracking.EyeTrackingManager;

import java.io.File;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class AdaptiveBowTriggers {
    private static final AdaptiveBowTriggers INSTANCE = new AdaptiveBowTriggers();
    private static final int MODE_OFF = 0;
    private static final int MODE_FEEDBACK = 1;
    private static final long NOTCH_REFRESH_NANOS = 250_000_000L;
    private static final int MAX_OFF_RETRIES = 2;
    private static final long OFF_RETRY_DELAY_MILLIS = 50L;
    private static final byte CONTROLLER_LEFT = 0;
    private static final byte CONTROLLER_RIGHT = 1;

    private final ScheduledExecutorService worker = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "Sclera-PSVR2-Triggers");
        thread.setDaemon(true);
        return thread;
    });
    private final ConcurrentLinkedQueue<Request> pending = new ConcurrentLinkedQueue<>();
    private final AtomicBoolean drainQueued = new AtomicBoolean();
    private volatile ToolkitApi api;
    private volatile boolean available;
    private volatile boolean eyeTrackingAvailable;
    private volatile boolean initialized;
    private volatile boolean closed;
    private volatile boolean gazePollingEnabled;
    private ScheduledFuture<?> gazePoll;
    private byte activeController = -1;
    private int activeStage = -1;
    private long lastNotchSentNanos;

    private AdaptiveBowTriggers() {}

    public static AdaptiveBowTriggers getInstance() {
        return INSTANCE;
    }

    public void initialize() {
        if (!closed) worker.execute(this::ensureInitialized);
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean isEyeTrackingAvailable() {
        return eyeTrackingAvailable || available;
    }

    /** Runs the Toolkit probe away from the render thread and returns both feature states. */
    public void checkAvailability(Consumer<Availability> callback) {
        if (closed) {
            callback.accept(new Availability(false, false));
            return;
        }
        worker.execute(() -> {
            ensureInitialized();
            boolean driverActive = false;
            ToolkitApi loaded = api;
            if (loaded != null) {
                try {
                    driverActive = loaded.psvr2_toolkit_get_driver_active() != 0;
                } catch (Throwable ignored) {
                    driverActive = false;
                }
            }
            available = driverActive;
            eyeTrackingAvailable = driverActive;
            callback.accept(new Availability(driverActive, driverActive));
        });
    }

    public void setEyeTrackingEnabled(boolean enabled) {
        gazePollingEnabled = enabled;
        if (closed) return;
        worker.execute(() -> {
            if (enabled) {
                ensureInitialized();
                if (gazePoll == null && gazePollingEnabled && api != null && available) {
                    gazePoll = worker.scheduleAtFixedRate(this::pollGaze, 0, 11, TimeUnit.MILLISECONDS);
                }
            } else if (gazePoll != null) {
                gazePoll.cancel(false);
                gazePoll = null;
            }
        });
    }

    public static boolean shouldApplyNotchResistance(boolean enabled, boolean active, boolean notched,
            boolean awaitingNewNotch) {
        return enabled && active && notched && !awaitingNewNotch;
    }

    public synchronized void engageNotch(byte controller, int stage) {
        if (api == null || (controller != CONTROLLER_LEFT && controller != CONTROLLER_RIGHT)) {
            release();
            return;
        }

        stage = Math.max(0, Math.min(2, stage));
        long now = System.nanoTime();
        if (activeController != controller) {
                if (activeController >= 0) enqueue(Request.off(activeController, 0));
            activeController = controller;
            activeStage = stage;
            enqueue(Request.notch(controller, stage, false));
            lastNotchSentNanos = now;
        } else if (activeStage != stage) {
            activeStage = stage;
            enqueue(Request.notch(controller, stage, false));
            lastNotchSentNanos = now;
        } else if (now - lastNotchSentNanos >= NOTCH_REFRESH_NANOS) {
            enqueue(Request.notch(controller, stage, true));
            lastNotchSentNanos = now;
        }
    }

    public synchronized void release() {
        if (activeController >= 0) enqueue(Request.off(activeController, 0));
        activeController = -1;
        activeStage = -1;
        lastNotchSentNanos = 0L;
    }

    public void shutdown() {
        synchronized (this) {
            if (closed) return;
            release();
            closed = true;
        }
        worker.execute(() -> {
            if (gazePoll != null) gazePoll.cancel(false);
            ToolkitApi loaded = api;
            if (loaded == null) return;
            try {
                send(loaded, Request.off(CONTROLLER_LEFT, 0));
                send(loaded, Request.off(CONTROLLER_RIGHT, 0));
                loaded.psvr2_toolkit_deinit();
            } catch (Throwable ignored) {
                // Toolkit may already have stopped while Minecraft is closing.
            }
            available = false;
            eyeTrackingAvailable = false;
            api = null;
        });
        worker.shutdown();
        try {
            worker.awaitTermination(250, TimeUnit.MILLISECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void ensureInitialized() {
        if (initialized || closed) return;
        initialized = true;
        try {
            api = loadToolkit();
            available = api != null && api.psvr2_toolkit_init() == 0;
        } catch (Throwable ignored) {
            available = false;
            api = null;
        }
    }

    private void pollGaze() {
        ToolkitApi loaded = api;
        if (!gazePollingEnabled || loaded == null || closed) return;
        try {
            GazeStatus status = new GazeStatus();
            if (loaded.psvr2_toolkit_gaze_status(status, 0) != 0
                    && (status.hasValidGaze() || status.hasValidBlink())) {
                eyeTrackingAvailable = true;
                status.publishTo(EyeTrackingManager.getInstance());
            }
        } catch (Throwable ignored) {
            eyeTrackingAvailable = false;
        }
    }

    private void enqueue(Request request) {
        if (closed) return;
        pending.offer(request);
        if (drainQueued.compareAndSet(false, true)) {
            worker.execute(this::drainRequests);
        }
    }

    private void drainRequests() {
        try {
            Request request;
            while ((request = pending.poll()) != null) {
                ToolkitApi loaded = api;
                if (loaded != null) send(loaded, request);
            }
        } finally {
            drainQueued.set(false);
            if (!pending.isEmpty() && !closed && drainQueued.compareAndSet(false, true)) {
                worker.execute(this::drainRequests);
            }
        }
    }

    private void send(ToolkitApi loaded, Request request) {
        try {
            boolean driverActive = loaded.psvr2_toolkit_get_driver_active() != 0;
            if (!driverActive) {
                available = false;
                retryOffIfNeeded(request);
                return;
            }
            TriggerCommand command = request.off
                    ? TriggerCommand.releaseForAttempt(request.retryAttempt)
                    : TriggerCommand.notch(request.stage);
            int result = loaded.psvr2_toolkit_set_trigger_effect(request.controller, command);
            available = result == 0;
            if (request.off) retryOffIfNeeded(request);
        } catch (Throwable ignored) {
            available = false;
            if (request.off) {
                retryOffIfNeeded(request);
            }
        }
    }

    private void retryOffIfNeeded(Request request) {
        if (!request.off || request.retryAttempt >= MAX_OFF_RETRIES || closed) return;
        worker.schedule(() -> {
            synchronized (AdaptiveBowTriggers.this) {
                if (activeController == request.controller) return;
            }
            enqueue(Request.off(request.controller, request.retryAttempt + 1));
        }, OFF_RETRY_DELAY_MILLIS, TimeUnit.MILLISECONDS);
    }

    private static ToolkitApi loadToolkit() {
        String configured = System.getProperty("sclera.psvr2toolkit.path");
        if (configured != null && !configured.isBlank()) {
            File configuredPath = Path.of(configured).toFile();
            File library = configuredPath.isDirectory()
                    ? new File(configuredPath, "psvr2_toolkit_capi.dll")
                    : configuredPath;
            if (library.isFile()) return Native.load(library.getAbsolutePath(), ToolkitApi.class);
        }

        String programFiles = System.getenv().getOrDefault("ProgramFiles(x86)", "C:/Program Files (x86)");
        File standardLibrary = Path.of(programFiles, "Steam", "steamapps", "common", "PlayStation VR2 App",
                "SteamVR_Plug-In", "bin", "win64", "psvr2_toolkit_capi.dll").toFile();
        if (standardLibrary != null && standardLibrary.isFile()) {
            return Native.load(standardLibrary.getAbsolutePath(), ToolkitApi.class);
        }
        try {
            return Native.load("psvr2_toolkit_capi", ToolkitApi.class);
        } catch (UnsatisfiedLinkError missing) {
            return null;
        }
    }

    private static final class Request {
        final byte controller;
        final boolean off;
        final int stage;
        final int retryAttempt;
        final boolean refresh;

        private Request(byte controller, boolean off, int stage, int retryAttempt, boolean refresh) {
            this.controller = controller;
            this.off = off;
            this.stage = stage;
            this.retryAttempt = retryAttempt;
            this.refresh = refresh;
        }

        static Request off(byte controller, int retryAttempt) {
            return new Request(controller, true, -1, retryAttempt, false);
        }
        static Request notch(byte controller, int stage, boolean refresh) {
            return new Request(controller, false, stage, 0, refresh);
        }
    }

    public interface ToolkitApi extends Library {
        int psvr2_toolkit_init();
        void psvr2_toolkit_deinit();
        byte psvr2_toolkit_get_driver_active();
        byte psvr2_toolkit_gaze_status(GazeStatus status, int timeoutMs);
        int psvr2_toolkit_set_trigger_effect(byte controllerType, TriggerCommand command);
    }

    public record Availability(boolean bowTriggers, boolean eyeTracking) {}

    @Structure.FieldOrder({"magic", "version", "size", "exposureLeft", "exposureRight", "ledStatus",
            "exposureCounterLeft", "exposureCounterRight", "ledCounter", "dspReturnCode", "lensConfig",
            "userCalibrationId", "foveatedGazeOrigin", "enabledEye", "motorSequence", "motorStrength",
            "wearable", "foveated"})
    public static class GazeStatus extends Structure {
        public byte[] magic = new byte[2];
        public short version;
        public int size;
        public float exposureLeft, exposureRight;
        public int ledStatus;
        public int exposureCounterLeft, exposureCounterRight, ledCounter, dspReturnCode;
        public LensConfig lensConfig = new LensConfig();
        public int userCalibrationId;
        public Vector3 foveatedGazeOrigin = new Vector3();
        public byte enabledEye, motorSequence, motorStrength;
        public WearableData wearable = new WearableData();
        public FoveatedData foveated = new FoveatedData();

        boolean hasValidGaze() {
            return (wearable.left.gazeDirectionValid != 0 && wearable.left.gazeDirection.isFiniteNonZero())
                    || (wearable.right.gazeDirectionValid != 0 && wearable.right.gazeDirection.isFiniteNonZero())
                    || (wearable.combinedDirectionValid != 0 && wearable.combinedDirection.isFiniteNonZero());
        }

        boolean hasValidBlink() {
            return wearable.left.blinkValid != 0 || wearable.right.blinkValid != 0;
        }

        void publishTo(EyeTrackingManager manager) {
            EyeSample left = EyeSample.from(wearable.left.gazeDirection, wearable.left.gazeDirectionValid != 0,
                    wearable.left.blinkValid != 0, wearable.left.blink != 0);
            EyeSample right = EyeSample.from(wearable.right.gazeDirection, wearable.right.gazeDirectionValid != 0,
                    wearable.right.blinkValid != 0, wearable.right.blink != 0);
            EyeSample combined = EyeSample.from(wearable.combinedDirection,
                    wearable.combinedDirectionValid != 0, false, false);
            manager.onPsvr2GazeReceived(left, right, combined);
        }
    }

    @Structure.FieldOrder({"left", "right"})
    public static class LensConfig extends Structure {
        public Vector3 left = new Vector3();
        public Vector3 right = new Vector3();
    }

    @Structure.FieldOrder({"x", "y", "z"})
    public static class Vector3 extends Structure {
        public float x, y, z;

        boolean isFiniteNonZero() {
            return Float.isFinite(x) && Float.isFinite(y) && Float.isFinite(z)
                    && (x * x + y * y + z * z) > 1.0e-8f;
        }
    }

    @Structure.FieldOrder({"timestamp", "frameCounter", "left", "right", "combinedOriginValid",
            "combinedOrigin", "combinedDirectionValid", "combinedDirection", "convergenceValid"})
    public static class WearableData extends Structure {
        public long timestamp;
        public int frameCounter;
        public WearableEye left = new WearableEye();
        public WearableEye right = new WearableEye();
        public int combinedOriginValid;
        public Vector3 combinedOrigin = new Vector3();
        public int combinedDirectionValid;
        public Vector3 combinedDirection = new Vector3();
        public int convergenceValid;
    }

    @Structure.FieldOrder({"originValid", "origin", "gazeDirectionValid", "gazeDirection", "pupilDiameterValid",
            "pupilDiameter", "pupilPositionValid", "pupilPosition", "positionGuideValid", "positionGuide",
            "blinkValid", "blink"})
    public static class WearableEye extends Structure {
        public int originValid;
        public Vector3 origin = new Vector3();
        public int gazeDirectionValid;
        public Vector3 gazeDirection = new Vector3();
        public int pupilDiameterValid;
        public float pupilDiameter;
        public int pupilPositionValid;
        public Vector2 pupilPosition = new Vector2();
        public int positionGuideValid;
        public Vector2 positionGuide = new Vector2();
        public int blinkValid, blink;
    }

    @Structure.FieldOrder({"x", "y"})
    public static class Vector2 extends Structure {
        public float x, y;
    }

    @Structure.FieldOrder({"timestamp", "frameCounter", "trackingState", "left", "right", "combined", "convergenceDistance"})
    public static class FoveatedData extends Structure {
        public long timestamp;
        public int frameCounter, trackingState;
        public Vector3 left = new Vector3();
        public Vector3 right = new Vector3();
        public Vector3 combined = new Vector3();
        public float convergenceDistance;
    }

    public static final class EyeSample {
        public final float pitch, yaw;
        public final boolean valid, blinkValid, blink;

        private EyeSample(float pitch, float yaw, boolean valid, boolean blinkValid, boolean blink) {
            this.pitch = pitch;
            this.yaw = yaw;
            this.valid = valid;
            this.blinkValid = blinkValid;
            this.blink = blink;
        }

        static EyeSample from(Vector3 direction, boolean valid, boolean blink) {
            return from(direction, valid, true, blink);
        }

        static EyeSample from(Vector3 direction, boolean valid, boolean blinkValid, boolean blink) {
            if (!valid || !direction.isFiniteNonZero()) {
                return new EyeSample(0, 0, false, blinkValid, blink);
            }
            double horizontal = Math.sqrt(direction.x * direction.x + direction.z * direction.z);
            float pitch = (float) Math.toDegrees(Math.atan2(-direction.y, horizontal));
            // PSVR2Toolkit applies (-X, +Y, -Z) before publishing gaze to OpenVR.
            float yaw = (float) Math.toDegrees(Math.atan2(-direction.x, direction.z));
            return new EyeSample(pitch, yaw, Float.isFinite(pitch) && Float.isFinite(yaw), blinkValid, blink);
        }

    }

    @Structure.FieldOrder({"mode", "padding", "data"})
    public static class TriggerCommand extends Structure {
        public int mode;
        public byte[] padding = new byte[4];
        public byte[] data = new byte[48];

        static TriggerCommand off() {
            TriggerCommand command = new TriggerCommand();
            command.mode = MODE_OFF;
            command.write();
            return command;
        }

        static TriggerCommand releaseForAttempt(int attempt) {
            if ((attempt & 1) == 0) return off();

            TriggerCommand command = new TriggerCommand();
            command.mode = MODE_FEEDBACK;
            command.write();
            return command;
        }

        static TriggerCommand notch(int stage) {
            TriggerCommand command = new TriggerCommand();
            command.mode = MODE_FEEDBACK;
            command.data[0] = 4;
            command.data[1] = (byte) switch (stage) {
                case 0 -> 7;
                case 1 -> 4;
                default -> 1;
            };
            command.write();
            return command;
        }

    }
}
