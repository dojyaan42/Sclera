package org.sclera.haptics;

import org.vmstudio.visor.api.client.input.VRInputManager;
import org.vmstudio.visor.api.common.HandType;

/** Controller feedback for server-confirmed damage to the local PS VR2 player. */
public final class DamageVibration {
    private DamageVibration() {}

    public static void onDamage(int damagedEntityId, int localPlayerId, boolean enabled,
                                boolean vrActive, boolean psvr2Available, VRInputManager input) {
        if (damagedEntityId != localPlayerId || !enabled || !vrActive || !psvr2Available || input == null) return;

        // Visor's duration is in nanoseconds; both hands also covers left-handed play.
        input.triggerHapticPulse(HandType.MAIN, 160.0f, 0.65f, 150_000_000L);
        input.triggerHapticPulse(HandType.OFFHAND, 160.0f, 0.65f, 150_000_000L);
    }
}
