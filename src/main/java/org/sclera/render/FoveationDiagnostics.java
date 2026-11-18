package org.sclera.render;

/**
 * Diagnostic container holding live hardware capabilities, probe results, and runtime counters.
 */
public class FoveationDiagnostics {
    private static final FoveationDiagnostics INSTANCE = new FoveationDiagnostics();

    public enum Mode {
        UNINITIALIZED("§7Uninitialized"),
        VRS_ACTIVE("§aVRS (Hardware)"),
        MULTIRES_ACTIVE("§eMulti-Res (FBO)"),
        FALLBACK_100("§6Fallback 100%"),
        DISABLED("§cDisabled"),
        IN_MENU("§7Paused in Menu"),
        SHADERS_PAUSED("§eShaders Active (VRS Paused)"),
        UNSUPPORTED("§4Unsupported");

        private final String label;

        Mode(String label) {
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }

    private volatile boolean probeExecuted = false;
    private volatile boolean vrsSupported = false;
    private volatile String glVersion = "Unknown";
    private volatile String glVendor = "Unknown";
    private volatile String glRenderer = "Unknown";
    private volatile int texelWidth = 16;
    private volatile int texelHeight = 16;
    private volatile int paletteSize = 16;
    private volatile String statusReason = "Pending initial frame probe";
    private volatile Mode currentMode = Mode.UNINITIALIZED;

    private long framesRendered = 0;
    private long foveatedFrames = 0;
    private long saccadeEyePasses = 0;

    public static FoveationDiagnostics getInstance() {
        return INSTANCE;
    }

    public boolean isProbeExecuted() {
        return probeExecuted;
    }

    public void setProbeResults(boolean vrsSupported, String version, String vendor, String renderer,
                                int texelW, int texelH, int palSize, String status) {
        this.probeExecuted = true;
        this.vrsSupported = vrsSupported;
        this.glVersion = version;
        this.glVendor = vendor;
        this.glRenderer = renderer;
        this.texelWidth = texelW > 0 ? texelW : 16;
        this.texelHeight = texelH > 0 ? texelH : 16;
        this.paletteSize = palSize > 0 ? palSize : 16;
        this.statusReason = status;
        this.currentMode = vrsSupported ? Mode.FALLBACK_100 : Mode.UNSUPPORTED;
    }

    public boolean isVrsSupported() {
        return vrsSupported;
    }

    public String getGlVersion() {
        return glVersion;
    }

    public String getGlVendor() {
        return glVendor;
    }

    public String getGlRenderer() {
        return glRenderer;
    }

    public int getTexelWidth() {
        return texelWidth;
    }

    public int getTexelHeight() {
        return texelHeight;
    }

    public int getPaletteSize() {
        return paletteSize;
    }

    public String getStatusReason() {
        return statusReason;
    }

    public void setStatusReason(String statusReason) {
        this.statusReason = statusReason;
    }

    public Mode getCurrentMode() {
        return currentMode;
    }

    public void setCurrentMode(Mode mode) {
        this.currentMode = mode;
    }

    public synchronized void recordFrame(boolean foveated, boolean saccade) {
        framesRendered++;
        if (foveated) foveatedFrames++;
        if (saccade) saccadeEyePasses++;
    }

    public long getFramesRendered() {
        return framesRendered;
    }

    public long getFoveatedFrames() {
        return foveatedFrames;
    }

    public long getSaccadeEyePasses() {
        return saccadeEyePasses;
    }

}
