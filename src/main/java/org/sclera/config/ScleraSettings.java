package org.sclera.config;

import org.sclera.render.FoveationConfig;
import org.sclera.tracking.EyeTrackingManager;

/** One reset action for all client settings, without replacing live configuration objects. */
public final class ScleraSettings {
    private ScleraSettings() {}

    public static void initialize() {
        EyeTrackingManager.getInstance().attachPreferences(ScleraClientConfig.getInstance());
        FoveationConfig.getInstance();
    }

    public static void resetDefaults() {
        initialize();
        resetDefaults(EyeTrackingManager.getInstance(), ScleraClientConfig.getInstance(), FoveationConfig.getInstance());
    }

    public static void resetDefaults(EyeTrackingManager tracking, ScleraClientConfig client, FoveationConfig foveation) {
        tracking.resetDefaults();
        client.resetDefaults();
        foveation.resetDefaults();
    }
}
