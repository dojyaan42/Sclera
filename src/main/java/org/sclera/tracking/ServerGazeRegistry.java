package org.sclera.tracking;

import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/** Recent eye directions supplied by players, independent of their replicated HMD pose. */
public final class ServerGazeRegistry {
    public static final long MAX_AGE_NANOS = 500_000_000L;
    private static final ConcurrentMap<UUID, Sample> SAMPLES = new ConcurrentHashMap<>();

    private ServerGazeRegistry() {}

    public static boolean update(UUID playerId, Vector3fc direction, long receivedAtNanos) {
        if (playerId == null || !EndermanGaze.isValidDirection(direction)) return false;
        SAMPLES.put(playerId, new Sample(new Vector3f(direction).normalize(), receivedAtNanos));
        return true;
    }

    public static Sample getFresh(UUID playerId, long nowNanos) {
        Sample sample = SAMPLES.get(playerId);
        if (sample == null) return null;
        if (nowNanos - sample.receivedAtNanos() > MAX_AGE_NANOS) {
            SAMPLES.remove(playerId, sample);
            return null;
        }
        return sample;
    }

    public static void remove(UUID playerId) {
        if (playerId != null) SAMPLES.remove(playerId);
    }

    public record Sample(Vector3f direction, long receivedAtNanos) {}
}
