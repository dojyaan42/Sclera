package org.sclera.network;

import net.minecraft.network.PacketByteBuf;
import org.vmstudio.visor.api.common.network.VisorPayloadToClient;
import org.sclera.feedback.FeedbackTarget;

/** Sent only to the attacker; no client-side target entity is needed. */
public record HitFeedbackPayload(Event event, FeedbackTarget target) implements VisorPayloadToClient {
    public enum Event { HIT, BLOCK_BREAK }

    public HitFeedbackPayload {
        if (event == null || target == null || target == FeedbackTarget.UNKNOWN) {
            throw new IllegalArgumentException("Feedback requires an event and a known controller origin");
        }
    }

    public static HitFeedbackPayload read(byte id, PacketByteBuf buffer) {
        if (id != 1 || buffer.readableBytes() != 2) return null;
        int event = buffer.readUnsignedByte();
        FeedbackTarget target = FeedbackTarget.fromCode(buffer.readUnsignedByte());
        if (event >= Event.values().length || target == FeedbackTarget.UNKNOWN) return null;
        return new HitFeedbackPayload(Event.values()[event], target);
    }

    @Override public byte payloadId() { return 1; }
    @Override public void onWrite(PacketByteBuf buffer) {
        buffer.writeByte(event.ordinal());
        buffer.writeByte(target.code());
    }
}
