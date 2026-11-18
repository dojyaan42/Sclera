package org.sclera.render;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Configuration settings for foveated rendering, persisted to config/sclera-foveation.json.
 */
public class FoveationConfig {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static FoveationConfig INSTANCE;
    private transient Path file;

    public enum Preset {
        @com.google.gson.annotations.SerializedName(value = "QUALITY", alternate = {"Quality"})
        QUALITY("§aQuality (15°)", 15.0f, 25.0f),

        @com.google.gson.annotations.SerializedName(value = "BALANCED", alternate = {"Balanced"})
        BALANCED("§eBalanced (12°)", 12.0f, 20.0f),

        @com.google.gson.annotations.SerializedName(value = "PERFORMANCE", alternate = {"AGGRESSIVE", "Performance", "Aggressive"})
        PERFORMANCE("§cPerformance (8°)", 8.0f, 15.0f);

        private final String label;
        private final float defaultFocal;
        private final float defaultTransition;

        Preset(String label, float defaultFocal, float defaultTransition) {
            this.label = label;
            this.defaultFocal = defaultFocal;
            this.defaultTransition = defaultTransition;
        }

        public String getLabel() {
            return label;
        }

        public float getDefaultFocal() {
            return defaultFocal;
        }

        public float getDefaultTransition() {
            return defaultTransition;
        }

        public Preset next() {
            Preset[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    public enum PeripheralRate {
        @com.google.gson.annotations.SerializedName(value = "RATE_1X2", alternate = {"1X2", "RATE_1_2"})
        RATE_1X2("§aQuality (1×2)"),

        @com.google.gson.annotations.SerializedName(value = "RATE_2X2", alternate = {"2X2", "RATE_2_2"})
        RATE_2X2("§eBalanced (2×2)"),

        @com.google.gson.annotations.SerializedName(value = "RATE_4X4", alternate = {"4X4", "RATE_4_4"})
        RATE_4X4("§cPerformance (4×4)");

        private final String label;

        PeripheralRate(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }

        public PeripheralRate next() {
            PeripheralRate[] values = values();
            return values[(this.ordinal() + 1) % values.length];
        }
    }

    private boolean enabled = false; // Disabled by default for safety until user enables it
    private Preset preset = Preset.QUALITY;
    private float focalRadiusDeg = Preset.QUALITY.getDefaultFocal();
    private float transitionRadiusDeg = Preset.QUALITY.getDefaultTransition();
    private PeripheralRate peripheralRate = PeripheralRate.RATE_1X2;
    private boolean peripheralSmoothing = false;
    private float smoothingStrength = 1.0f;
    private boolean debugOverlay = false;
    private boolean testCutoutMode = false;
    private float approxFovDeg = 110.0f;

    public static synchronized FoveationConfig getInstance() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static FoveationConfig load() {
        return load(FabricLoader.getInstance().getConfigDir().resolve("sclera-foveation.json"));
    }

    public static FoveationConfig load(Path file) {
        FoveationConfig config = null;
        try (var reader = Files.newBufferedReader(file)) {
            config = GSON.fromJson(reader, FoveationConfig.class);
        } catch (Exception ignored) {}
        boolean newConfig = config == null;
        if (newConfig) config = new FoveationConfig();
        config.file = file;
        if (config.preset == null) config.preset = Preset.QUALITY;
        if (config.peripheralRate == null) config.peripheralRate = PeripheralRate.RATE_1X2;
        if (newConfig) config.save();
        return config;
    }

    public synchronized void save() {
        if (file == null) return;
        try {
            Files.createDirectories(file.toAbsolutePath().getParent());
            try (var writer = Files.newBufferedWriter(file)) {
                GSON.toJson(this, writer);
            }
        } catch (Exception ignored) {}
    }

    public void resetDefaults() {
        enabled = false;
        preset = Preset.QUALITY;
        focalRadiusDeg = preset.getDefaultFocal();
        transitionRadiusDeg = preset.getDefaultTransition();
        peripheralRate = PeripheralRate.RATE_1X2;
        peripheralSmoothing = false;
        smoothingStrength = 1.0f;
        debugOverlay = false;
        testCutoutMode = false;
        approxFovDeg = 110.0f;
        save();
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
        save();
    }

    public Preset getPreset() {
        return preset;
    }

    public void setPreset(Preset preset) {
        if (preset == null) preset = Preset.QUALITY;
        this.preset = preset;
        this.focalRadiusDeg = preset.getDefaultFocal();
        this.transitionRadiusDeg = preset.getDefaultTransition();
        save();
    }

    public float getFocalRadiusDeg() {
        return focalRadiusDeg;
    }

    public float getTransitionRadiusDeg() {
        return transitionRadiusDeg;
    }

    public boolean isDebugOverlay() {
        return debugOverlay;
    }

    public void setDebugOverlay(boolean debugOverlay) {
        this.debugOverlay = debugOverlay;
        save();
    }

    public boolean isTestCutoutMode() {
        return testCutoutMode;
    }

    public void setTestCutoutMode(boolean testCutoutMode) {
        this.testCutoutMode = testCutoutMode;
        save();
    }

    public float getApproxFovDeg() {
        return approxFovDeg;
    }

    public void setApproxFovDeg(float approxFovDeg) {
        this.approxFovDeg = approxFovDeg;
        save();
    }

    public PeripheralRate getPeripheralRate() {
        return peripheralRate != null ? peripheralRate : PeripheralRate.RATE_1X2;
    }

    public void setPeripheralRate(PeripheralRate peripheralRate) {
        this.peripheralRate = (peripheralRate != null) ? peripheralRate : PeripheralRate.RATE_1X2;
        save();
    }

    public boolean isPeripheralSmoothingEnabled() {
        return peripheralSmoothing;
    }

    public void setPeripheralSmoothing(boolean peripheralSmoothing) {
        this.peripheralSmoothing = peripheralSmoothing;
        save();
    }

    public float getSmoothingStrength() {
        return smoothingStrength;
    }

    public void setSmoothingStrength(float strength) {
        this.smoothingStrength = Math.max(0.0f, Math.min(1.0f, strength));
        save();
    }
}
