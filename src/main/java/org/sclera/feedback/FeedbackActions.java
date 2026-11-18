package org.sclera.feedback;

import net.minecraft.server.network.ServerPlayerEntity;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.HandType;

public final class FeedbackActions {
    private FeedbackActions() {}

    public static FeedbackTarget hand(ServerPlayerEntity player, HandType hand) {
        var server = VisorAPI.server();
        var vrPlayer = server == null ? null : server.getVRPlayer(player);
        return vrPlayer == null ? FeedbackTarget.UNKNOWN : FeedbackTarget.fromHand(hand, vrPlayer.isLeftHanded());
    }

    public static FeedbackTarget activeHand(ServerPlayerEntity player) {
        FeedbackTarget scoped = FeedbackActionContext.current(player.getUuid());
        if (scoped != FeedbackTarget.UNKNOWN) return scoped;
        var server = VisorAPI.server();
        var vrPlayer = server == null ? null : server.getVRPlayer(player);
        return vrPlayer == null ? FeedbackTarget.UNKNOWN
                : FeedbackTarget.fromHand(vrPlayer.getActiveHand(), vrPlayer.isLeftHanded());
    }
}
