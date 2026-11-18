package org.sclera.network;

import org.joml.Vector3fc;
import org.sclera.tracking.ServerGazeRegistry;
import org.vmstudio.visor.api.common.addon.VisorAddon;
import org.vmstudio.visor.api.common.network.VisorChannel;
import org.vmstudio.visor.api.common.network.VisorNetwork;
import org.vmstudio.visor.api.compatibility.mcversion.McVersionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

public final class ScleraGazeChannel {
    private static final Logger LOGGER = LoggerFactory.getLogger("Sclera/GazeNetwork");
    private static final AtomicBoolean LOGGED_CLIENT_SEND = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_SERVER_RECEIVE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_MISSING_CHANNEL = new AtomicBoolean();
    private static final net.minecraft.util.Identifier ID = McVersionUtils.newResourceLoc("sclera:gaze");
    private static VisorChannel channel;
    private static long lastSentAt;

    private ScleraGazeChannel() {}

    public static void register(VisorAddon owner) {
        channel = VisorChannel.builder(owner, ID, 1)
                .toServer(GazeDirectionPayload::read, (payload, sender, responseSender) -> {
                    if (payload.isActive() && payload.isValid()) {
                        if (ServerGazeRegistry.update(sender.getUuid(), payload.direction(), System.nanoTime())
                                && LOGGED_SERVER_RECEIVE.compareAndSet(false, true)) {
                            LOGGER.info("Received the first valid eye-gaze packet from client");
                        }
                    } else {
                        ServerGazeRegistry.remove(sender.getUuid());
                    }
                })
                .build();
        VisorNetwork.registerChannel(channel);
        LOGGER.info("Registered the eye-gaze channel with Visor");
    }

    public static void send(Vector3fc direction, long nowNanos) {
        if (channel == null) {
            if (LOGGED_MISSING_CHANNEL.compareAndSet(false, true)) {
                LOGGER.warn("Eye-gaze packet was requested before the Visor channel was registered");
            }
            return;
        }
        if (nowNanos - lastSentAt < 50_000_000L) return;
        lastSentAt = nowNanos;
        if (org.sclera.tracking.EndermanGaze.isValidDirection(direction)) {
            if (LOGGED_CLIENT_SEND.compareAndSet(false, true)) {
                LOGGER.info("Sending the first valid eye-gaze packet to the server");
            }
            channel.sendToServer(new GazeDirectionPayload(true, direction.x(), direction.y(), direction.z()));
        } else {
            channel.sendToServer(new GazeDirectionPayload(false, 0, 0, 0));
        }
    }
}
