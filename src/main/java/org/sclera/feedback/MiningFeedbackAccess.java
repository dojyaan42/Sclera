package org.sclera.feedback;

/** Carries an explicit physical-swing hand into Visor's delayed mining completion. */
public interface MiningFeedbackAccess {
    void sclera$recordMiningHand(long position, FeedbackTarget target);
}
