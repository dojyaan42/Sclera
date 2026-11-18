package org.sclera.network;

import net.minecraft.server.network.ServerPlayerEntity;
import org.sclera.haptics.HitFeedback;
import org.sclera.feedback.FeedbackTarget;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.visor.api.common.network.VisorChannel;
import org.vmstudio.visor.api.common.network.VisorNetwork;
import org.vmstudio.visor.api.compatibility.mcversion.McVersionUtils;

public final class HitFeedbackChannel {
    private static VisorChannel channel;
    private HitFeedbackChannel() {}

    public static void register(VisorAddon owner) {
        channel = VisorChannel.builder(owner, McVersionUtils.newResourceLoc("sclera:hit_feedback"), 2)
                .toClient(HitFeedbackPayload::read, HitFeedback::onConfirmedAction)
                .build();
        VisorNetwork.registerChannel(channel);
    }

    public static void send(ServerPlayerEntity attacker, HitFeedbackPayload.Event event, FeedbackTarget target) {
        if (channel != null && target != null && target != FeedbackTarget.UNKNOWN
                && VisorAPI.server().getVRPlayer(attacker) != null) {
            channel.sendToClient(attacker, new HitFeedbackPayload(event, target));
        }
    }
}
