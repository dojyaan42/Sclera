package org.sclera.tracking;

/** Render-thread latch: both eyes use the same acquisition snapshot for a scene. */
public final class RenderGazeLatch {
    public enum State { FRESH, HELD, LOST }
    private final RenderGazeSample[] accepted = {RenderGazeSample.invalid(), RenderGazeSample.invalid()};
    private final State[] states = {State.LOST, State.LOST};
    private long frameTime;

    public void latch(RenderGazeSample sample, long now) {
        frameTime = now;
        for (int eye = 0; eye < 2; eye++) {
            boolean left = eye == 0;
            long age = now - sample.timestampForEye(left);
            boolean valid = left ? sample.isLeftValid() : sample.isRightValid();
            if (valid && sample.isEyeOpen(left) && age >= 0 && age <= 150_000_000L) {
                accepted[eye] = sample;
                states[eye] = State.FRESH;
            } else {
                long heldAge = now - accepted[eye].timestampForEye(left);
                boolean hadSample = left ? accepted[eye].isLeftValid() : accepted[eye].isRightValid();
                states[eye] = hadSample && heldAge >= 0 && heldAge <= 350_000_000L ? State.HELD : State.LOST;
            }
        }
    }

    public State state(boolean left) { return states[left ? 0 : 1]; }
    public RenderGazeSample sample(boolean left) { return accepted[left ? 0 : 1]; }
    public long age(boolean left) { return Math.max(0L, frameTime - sample(left).timestampForEye(left)); }
    public void clear() {
        java.util.Arrays.fill(accepted, RenderGazeSample.invalid());
        java.util.Arrays.fill(states, State.LOST);
    }
}
