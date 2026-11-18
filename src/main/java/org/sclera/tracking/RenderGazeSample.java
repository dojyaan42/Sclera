package org.sclera.tracking;

/**
 * Immutable snapshot of binocular eye tracking telemetry used exclusively by the rendering thread.
 * Decoupled from cursor filters; timestamps retain acquisition age.
 */
public final class RenderGazeSample {
    public static final float SACCADE_THRESHOLD_DEG_PER_SEC = 150.0f;
    private static final RenderGazeSample INVALID_INSTANCE = new RenderGazeSample(
            0L, 0f, 0f, 0f, 0f, false, false, 1.0f, 1.0f, 0f, false
    );

    private final long timestampNanos;
    private final long leftTimestampNanos, rightTimestampNanos;
    private final float leftPitch;
    private final float leftYaw;
    private final float rightPitch;
    private final float rightYaw;
    private final boolean leftValid;
    private final boolean rightValid;
    private final float leftEyeLid;  // 1.0 = fully open, 0.0 = closed
    private final float rightEyeLid; // 1.0 = fully open, 0.0 = closed
    private final float angularVelocityDegPerSec;
    private final boolean saccade;

    public RenderGazeSample(
            long timestampNanos,
            float leftPitch,
            float leftYaw,
            float rightPitch,
            float rightYaw,
            boolean leftValid,
            boolean rightValid,
            float leftEyeLid,
            float rightEyeLid,
            float angularVelocityDegPerSec,
            boolean saccade) {
        this(timestampNanos, leftPitch, leftYaw, rightPitch, rightYaw, leftValid, rightValid,
                leftEyeLid, rightEyeLid, angularVelocityDegPerSec, saccade, timestampNanos, timestampNanos);
    }

    public RenderGazeSample(long timestampNanos, float leftPitch, float leftYaw, float rightPitch, float rightYaw,
            boolean leftValid, boolean rightValid, float leftEyeLid, float rightEyeLid,
            float angularVelocityDegPerSec, boolean saccade, long leftTimestampNanos, long rightTimestampNanos) {
        this.leftTimestampNanos = leftTimestampNanos;
        this.rightTimestampNanos = rightTimestampNanos;
        this.timestampNanos = timestampNanos;
        this.leftPitch = leftPitch;
        this.leftYaw = leftYaw;
        this.rightPitch = rightPitch;
        this.rightYaw = rightYaw;
        this.leftValid = leftValid;
        this.rightValid = rightValid;
        this.leftEyeLid = leftEyeLid;
        this.rightEyeLid = rightEyeLid;
        this.angularVelocityDegPerSec = angularVelocityDegPerSec;
        this.saccade = saccade;
    }

    public static RenderGazeSample invalid() {
        return INVALID_INSTANCE;
    }

    public boolean isFresh(long maxAgeNanos) {
        if (!leftValid && !rightValid) return false;
        return (System.nanoTime() - timestampNanos) <= maxAgeNanos;
    }

    public RenderGazeSample calibrated(float pitchOffset, float yawOffset, float sensitivity) {
        return new RenderGazeSample(timestampNanos,
                (leftPitch + pitchOffset) * sensitivity, (leftYaw + yawOffset) * sensitivity,
                (rightPitch + pitchOffset) * sensitivity, (rightYaw + yawOffset) * sensitivity,
                leftValid, rightValid, leftEyeLid, rightEyeLid, angularVelocityDegPerSec, saccade,
                leftTimestampNanos, rightTimestampNanos);
    }

    public long timestampForEye(boolean left) { return left ? leftTimestampNanos : rightTimestampNanos; }

    /** Shared cursor direction; never refresh acquisition time just because a frame is rendered. */
    public RenderGazeSample withSharedFilteredDirection(float pitch, float yaw) {
        boolean left = leftValid && isEyeOpen(true);
        boolean right = rightValid && isEyeOpen(false);
        boolean valid = (left || right) && Float.isFinite(pitch) && Float.isFinite(yaw);
        long acquired = left && right ? Math.min(leftTimestampNanos, rightTimestampNanos)
                : left ? leftTimestampNanos : rightTimestampNanos;
        return new RenderGazeSample(acquired, pitch, yaw, pitch, yaw, valid, valid,
                valid ? 1f : 0f, valid ? 1f : 0f, angularVelocityDegPerSec, saccade);
    }

    public RenderGazeSample withEyeTimes(long left, long right) {
        return new RenderGazeSample(timestampNanos, leftPitch, leftYaw, rightPitch, rightYaw,
                leftValid, rightValid, leftEyeLid, rightEyeLid, angularVelocityDegPerSec, saccade, left, right);
    }

    public long getTimestampNanos() {
        return timestampNanos;
    }

    public float getLeftPitch() {
        return leftPitch;
    }

    public float getLeftYaw() {
        return leftYaw;
    }

    public float getRightPitch() {
        return rightPitch;
    }

    public float getRightYaw() {
        return rightYaw;
    }

    public boolean isLeftValid() {
        return leftValid;
    }

    public boolean isRightValid() {
        return rightValid;
    }

    public float getLeftEyeLid() {
        return leftEyeLid;
    }

    public float getRightEyeLid() {
        return rightEyeLid;
    }

    public float getAngularVelocityDegPerSec() {
        return angularVelocityDegPerSec;
    }

    public boolean isSaccade() {
        return saccade;
    }

    public boolean isEyeOpen(boolean leftEye) {
        float lid = leftEye ? leftEyeLid : rightEyeLid;
        return lid > 0.15f;
    }

    /**
     * Pitch for a given eye, falling back to the opposite eye if one is invalid.
     */
    public float getPitchForEye(boolean leftEye) {
        if (leftEye) {
            return leftValid ? leftPitch : rightPitch;
        } else {
            return rightValid ? rightPitch : leftPitch;
        }
    }

    /**
     * Yaw for a given eye, falling back to the opposite eye if one is invalid.
     */
    public float getYawForEye(boolean leftEye) {
        if (leftEye) {
            return leftValid ? leftYaw : rightYaw;
        } else {
            return rightValid ? rightYaw : leftYaw;
        }
    }
}
