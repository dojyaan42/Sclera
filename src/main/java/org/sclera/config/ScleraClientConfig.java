package org.sclera.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;
import org.sclera.tracking.EyeTrackingManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Client preferences stored in config/sclera-client.json. */
public final class ScleraClientConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static ScleraClientConfig instance;

    private transient Path file;
    private boolean trackingInChat = true;
    private boolean damageVibrationEnabled = false;
    private boolean hitFeedbackEnabled = false;
    private boolean blockBreakFeedbackEnabled = false;
    private TrackingPreferences tracking = new TrackingPreferences();

    /** Saved choices only. Hardware availability and live gaze state are never serialized. */
    public static final class TrackingPreferences {
        public EyeTrackingManager.TrackingMode mode = EyeTrackingManager.TrackingMode.EYE_TRACKING;
        public EyeTrackingManager.CrosshairMode crosshair = EyeTrackingManager.CrosshairMode.ENABLED;
        public boolean bowGazeAim = true;
        public boolean bowTrigger = false;
        public boolean gazeInMenus = true, gazeInInventory = true, gazeInWorld = true;
        public boolean handsInMenus = true, handsInInventory = true, handsInWorld = true;
        public boolean blinkInMenus = false, blinkInInventory = false;
        public EyeTrackingManager.BlinkWorldAction blinkAction = EyeTrackingManager.BlinkWorldAction.OFF;
        public float offsetPitch = EyeTrackingManager.DEFAULT_UNCALIBRATED_PITCH_OFFSET;
        public float offsetYaw = 0;
    }

    private ScleraClientConfig() {}

    public static synchronized ScleraClientConfig getInstance() {
        if (instance == null) {
            Path configDir = FabricLoader.getInstance().getConfigDir();
            instance = load(configDir.resolve("sclera-client.json"));
        }
        return instance;
    }

    static ScleraClientConfig load(Path file) {
        ScleraClientConfig config;
        try (var reader = Files.newBufferedReader(file)) {
            config = GSON.fromJson(reader, ScleraClientConfig.class);
        } catch (Exception ignored) {
            config = new ScleraClientConfig();
        }
        if (config == null) config = new ScleraClientConfig();
        if (config.tracking == null) config.tracking = new TrackingPreferences();
        config.file = file;
        return config;
    }

    public boolean isTrackingInChat() {
        return trackingInChat;
    }

    public void setTrackingInChat(boolean trackingInChat) {
        this.trackingInChat = trackingInChat;
        save();
    }

    public boolean isDamageVibrationEnabled() {
        return damageVibrationEnabled;
    }

    public void setDamageVibrationEnabled(boolean enabled) {
        damageVibrationEnabled = enabled;
        save();
    }

    public boolean isHitFeedbackEnabled() {
        return hitFeedbackEnabled;
    }

    public void setHitFeedbackEnabled(boolean enabled) {
        hitFeedbackEnabled = enabled;
        save();
    }

    public boolean isBlockBreakFeedbackEnabled() { return blockBreakFeedbackEnabled; }

    public void setBlockBreakFeedbackEnabled(boolean enabled) {
        blockBreakFeedbackEnabled = enabled;
        save();
    }

    public TrackingPreferences getTrackingPreferences() {
        return tracking;
    }

    public void setTrackingPreferences(TrackingPreferences tracking) {
        this.tracking = tracking;
        save();
    }

    /** The tracking manager resets its own preferences and preserves calibration. */
    public void resetDefaults() {
        trackingInChat = true;
        damageVibrationEnabled = false;
        hitFeedbackEnabled = false;
        blockBreakFeedbackEnabled = false;
        save();
    }

    private void save() {
        try {
            Files.createDirectories(file.getParent());
            try (var writer = Files.newBufferedWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException ignored) {
        }
    }
}
