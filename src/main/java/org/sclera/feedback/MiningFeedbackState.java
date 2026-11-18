package org.sclera.feedback;

import java.util.LinkedHashMap;

/** Per-player pending block contributions, bounded even when physical swings never finish mining. */
public final class MiningFeedbackState {
    private final LinkedHashMap<Long, FeedbackTarget> hands = new LinkedHashMap<>();

    public void remember(long position, FeedbackTarget target) {
        if (target == null || target == FeedbackTarget.UNKNOWN) return;
        hands.remove(position);
        hands.put(position, target);
        if (hands.size() > 64) hands.remove(hands.keySet().iterator().next());
    }

    public FeedbackTarget take(long position) {
        FeedbackTarget target = hands.remove(position);
        return target == null ? FeedbackTarget.UNKNOWN : target;
    }

    public void forget(long position) { hands.remove(position); }
    public void clear() { hands.clear(); }
}
