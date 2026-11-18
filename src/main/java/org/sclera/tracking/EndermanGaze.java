package org.sclera.tracking;

import org.joml.Vector3fc;

public final class EndermanGaze {
    private EndermanGaze() {}

    public static boolean isValidDirection(Vector3fc direction) {
        if (direction == null || !direction.isFinite()) return false;
        float lengthSquared = direction.lengthSquared();
        return lengthSquared >= .96f && lengthSquared <= 1.04f;
    }

    public static boolean isLookingAt(Vector3fc eyePosition, Vector3fc direction, Vector3fc targetPosition) {
        if (!isValidDirection(direction) || eyePosition == null || targetPosition == null) return false;
        float dx = targetPosition.x() - eyePosition.x();
        float dy = targetPosition.y() - eyePosition.y();
        float dz = targetPosition.z() - eyePosition.z();
        float distanceSquared = dx * dx + dy * dy + dz * dz;
        if (distanceSquared == 0 || distanceSquared >= 128f * 128f) return false;
        float distance = (float) Math.sqrt(distanceSquared);
        float dot = (direction.x() * dx + direction.y() * dy + direction.z() * dz) / distance;
        return dot > 1.0f - 0.1f / distance;
    }
}
