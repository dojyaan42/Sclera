package org.sclera.tracking;

/**
 * Container holding raw and filtered eye tracking telemetry and blink state.
 */
public class GazeData {
    public float rawPitch;
    public float rawYaw;
    public float filteredPitch;
    public float filteredYaw;
    public boolean blink;
    public long timestampMs;
    private long timestampNanos;

    public GazeData(GazeData source) {
        rawPitch = source.rawPitch;
        rawYaw = source.rawYaw;
        filteredPitch = source.filteredPitch;
        filteredYaw = source.filteredYaw;
        blink = source.blink;
        timestampMs = source.timestampMs;
        timestampNanos = source.timestampNanos;
    }

    public GazeData() {
        this.rawPitch = 0f;
        this.rawYaw = 0f;
        this.filteredPitch = 0f;
        this.filteredYaw = 0f;
        this.blink = false;
        this.timestampMs = 0;
    }

    public boolean isFresh(long maxAgeMs) {
        return timestampMs != 0 && (System.nanoTime() - timestampNanos) < maxAgeMs * 1_000_000L;
    }

    public void update(float pitch, float yaw, float fPitch, float fYaw, boolean blink) {
        this.rawPitch = pitch;
        this.rawYaw = yaw;
        this.filteredPitch = fPitch;
        this.filteredYaw = fYaw;
        this.blink = blink;
        this.timestampMs = System.currentTimeMillis();
        this.timestampNanos = System.nanoTime();
    }

    public void updatePitch(float pitch, float fPitch, boolean blink) {
        this.rawPitch = pitch;
        this.filteredPitch = fPitch;
        this.blink = blink;
        this.timestampMs = System.currentTimeMillis();
        this.timestampNanos = System.nanoTime();
    }

    public void updateYaw(float yaw, float fYaw, boolean blink) {
        this.rawYaw = yaw;
        this.filteredYaw = fYaw;
        this.blink = blink;
        this.timestampMs = System.currentTimeMillis();
        this.timestampNanos = System.nanoTime();
    }

    public void invalidate() {
        rawPitch = rawYaw = filteredPitch = filteredYaw = 0f;
        blink = false;
        timestampMs = 0;
        timestampNanos = 0;
    }
}
