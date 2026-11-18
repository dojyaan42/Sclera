package org.sclera.haptics;

import net.minecraft.client.MinecraftClient;
import org.sclera.config.ScleraClientConfig;
import org.sclera.ui.GazeCursorHandler;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.input.VRInputManager;
import org.vmstudio.visor.api.common.HandType;
import me.phoenixra.atumvr.api.enums.ControllerType;
import org.sclera.feedback.FeedbackTarget;
import org.sclera.network.HitFeedbackPayload;

/** Client response to a server-confirmed successful attack. */
public final class HitFeedback {
    private HitFeedback() {}

    public static void onConfirmedAction(HitFeedbackPayload payload) {
        MinecraftClient client = MinecraftClient.getInstance();
        client.execute(() -> {
            if (client.player == null || client.world == null || VisorAPI.client() == null) return;
            ScleraClientConfig config = ScleraClientConfig.getInstance();
            boolean enabled = payload.event() == HitFeedbackPayload.Event.HIT
                    ? config.isHitFeedbackEnabled() : config.isBlockBreakFeedbackEnabled();
            play(enabled, GazeCursorHandler.isVRActive(), AdaptiveBowTriggers.getInstance().isAvailable(),
                    VisorAPI.client().getInputManager(), payload.target());
        });
    }

    public static void play(boolean enabled, boolean vrActive, boolean psvr2Available,
                            VRInputManager input, FeedbackTarget target) {
        if (!enabled || !vrActive || !psvr2Available || input == null || target == null || target == FeedbackTarget.UNKNOWN) return;
        if (target == FeedbackTarget.BOTH) {
            input.triggerHapticPulse(HandType.MAIN, 160.0f, 0.45f, 90_000_000L);
            input.triggerHapticPulse(HandType.OFFHAND, 160.0f, 0.45f, 90_000_000L);
        } else {
            ControllerType controller = target == FeedbackTarget.LEFT ? ControllerType.LEFT : ControllerType.RIGHT;
            input.triggerHapticPulse(HandType.fromController(controller, input.isLeftHanded()), 160.0f, 0.45f, 90_000_000L);
        }
    }
}
