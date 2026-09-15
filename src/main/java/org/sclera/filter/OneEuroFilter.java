package org.sclera.filter;

/**
 * 1€ (One Euro) Filter for real-time jitter reduction with adaptive cutoff frequency.
 * Designed specifically for human eye-tracking & gaze input.
 * References: Casiez, Roussel, Vogel (CHI 2012)
 */
public class OneEuroFilter {
    private double minCutoff; // Minimum cutoff frequency in Hz (smoothness at rest)
    private double beta;      // Speed coefficient (responsiveness during saccades)
    private double dCutoff;   // Derivative cutoff frequency in Hz

    private double xPrev;
    private double dxPrev;
    private double tPrev;
    private boolean initialized = false;

    public OneEuroFilter(double minCutoff, double beta, double dCutoff) {
        this.minCutoff = minCutoff;
        this.beta = beta;
        this.dCutoff = dCutoff;
    }

    public OneEuroFilter() {
        // minCutoff = 1.2 Hz (smooth at rest, jitter-free)
        // beta = 0.08 (adaptive speed: snaps in < 5ms during saccades for 60-120fps feel)
        this(1.2, 0.08, 1.0);
    }

    public synchronized double filter(double x, double timestampSeconds) {
        if (!initialized) {
            initialized = true;
            xPrev = x;
            dxPrev = 0.0;
            tPrev = timestampSeconds;
            return x;
        }

        double dt = timestampSeconds - tPrev;
        if (dt <= 0.0) {
            dt = 1e-3; // Prevent division by zero
        }
        tPrev = timestampSeconds;

        // Estimate derivative of signal
        double dx = (x - xPrev) / dt;
        double aD = alpha(dt, dCutoff);
        double dxHat = aD * dx + (1.0 - aD) * dxPrev;
        dxPrev = dxHat;

        // Adaptive cutoff frequency based on velocity
        double cutoff = minCutoff + beta * Math.abs(dxHat);

        // Filter original signal
        double a = alpha(dt, cutoff);
        double xHat = a * x + (1.0 - a) * xPrev;
        xPrev = xHat;

        return xHat;
    }

    public synchronized void reset() {
        initialized = false;
    }

    private static double alpha(double dt, double cutoff) {
        double tau = 1.0 / (2.0 * Math.PI * cutoff);
        return 1.0 / (1.0 + tau / dt);
    }

    public synchronized void setParameters(double minCutoff, double beta) {
        this.minCutoff = minCutoff;
        this.beta = beta;
    }
}