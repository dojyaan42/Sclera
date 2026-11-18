package org.sclera.feedback;

import org.vmstudio.visor.api.common.HandType;

/** Physical controllers, independent of the player's current handedness setting. */
public enum FeedbackTarget {
    UNKNOWN(0), LEFT(1), RIGHT(2), BOTH(3);

    private final int code;
    FeedbackTarget(int code) { this.code = code; }
    public int code() { return code; }

    public static FeedbackTarget fromCode(int code) {
        return switch (code) { case 1 -> LEFT; case 2 -> RIGHT; case 3 -> BOTH; default -> UNKNOWN; };
    }

    public static FeedbackTarget fromHand(HandType hand, boolean leftHanded) {
        if (hand == null) return UNKNOWN;
        return (hand == HandType.MAIN) == leftHanded ? LEFT : RIGHT;
    }
}
