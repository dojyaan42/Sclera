package org.sclera.feedback;

import java.util.UUID;
import java.util.function.Supplier;

/** Scoped server action. Nested attacks cannot overwrite each other's origin. */
public final class FeedbackActionContext {
    private record Action(UUID actor, FeedbackTarget target) {}
    private static final ThreadLocal<Action> CURRENT = new ThreadLocal<>();
    private FeedbackActionContext() {}

    public static FeedbackTarget current(UUID actor) {
        Action action = CURRENT.get();
        return action != null && action.actor.equals(actor) ? action.target : FeedbackTarget.UNKNOWN;
    }

    public static <T> T call(UUID actor, FeedbackTarget target, Supplier<T> operation) {
        Action previous = CURRENT.get();
        CURRENT.set(new Action(actor, target));
        try {
            return operation.get();
        } finally {
            if (previous == null) CURRENT.remove();
            else CURRENT.set(previous);
        }
    }

    public static void run(UUID actor, FeedbackTarget target, Runnable operation) {
        call(actor, target, () -> { operation.run(); return null; });
    }
}
