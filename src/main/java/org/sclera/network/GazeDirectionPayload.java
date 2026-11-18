package org.sclera.network;

import net.minecraft.network.PacketByteBuf;
import org.joml.Vector3f;
import org.sclera.tracking.EndermanGaze;
import org.vmstudio.visor.api.common.network.VisorPayloadToServer;

public final class GazeDirectionPayload implements VisorPayloadToServer {
    public static final byte ID = 0;
    private final boolean active;
    private final Vector3f direction;

    public GazeDirectionPayload(boolean active, float x, float y, float z) {
        this.active = active;
        this.direction = new Vector3f(x, y, z);
    }

    public Vector3f direction() {
        return new Vector3f(direction);
    }

    public static GazeDirectionPayload read(byte payloadId, PacketByteBuf buffer) {
        if (payloadId != ID || buffer.readableBytes() != 1 + Float.BYTES * 3) {
            return new GazeDirectionPayload(false, 0, 0, 0);
        }
        boolean active = buffer.readBoolean();
        return new GazeDirectionPayload(active, buffer.readFloat(), buffer.readFloat(), buffer.readFloat());
    }

    @Override
    public void onWrite(PacketByteBuf buffer) {
        buffer.writeBoolean(active);
        buffer.writeFloat(direction.x);
        buffer.writeFloat(direction.y);
        buffer.writeFloat(direction.z);
    }

    @Override
    public byte payloadId() {
        return ID;
    }

    public boolean isValid() {
        return active && EndermanGaze.isValidDirection(direction);
    }

    public boolean isActive() {
        return active;
    }
}
