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

    public GazeData() {
        this.rawPitch = 0f;
        this.rawYaw = 0f;
        this.filteredPitch = 0f;
        this.filteredYaw = 0f;
        this.blink = false;
        this.timestampMs = 0;
    }

    public boolean isFresh(long maxAgeMs) {
        return (System.currentTimeMillis() - timestampMs) < maxAgeMs;
    }

    public void update(float pitch, float yaw, float fPitch, float fYaw, boolean blink) {
        this.rawPitch = pitch;
        this.rawYaw = yaw;
        this.filteredPitch = fPitch;
        this.filteredYaw = fYaw;
        this.blink = blink;
        this.timestampMs = System.currentTimeMillis();
    }

    public void updatePitch(float pitch, float fPitch, boolean blink) {
        this.rawPitch = pitch;
        this.filteredPitch = fPitch;
        this.blink = blink;
        this.timestampMs = System.currentTimeMillis();
    }

    public void updateYaw(float yaw, float fYaw, boolean blink) {
        this.rawYaw = yaw;
        this.filteredYaw = fYaw;
        this.blink = blink;
        this.timestampMs = System.currentTimeMillis();
    }
}