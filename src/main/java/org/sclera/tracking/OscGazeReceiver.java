package org.sclera.tracking;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.charset.StandardCharsets;

/**
 * Ultra-low-latency UDP socket receiver that parses Open Sound Control (OSC) packets
 * (both standalone messages and OSC #bundles) and raw JSON telemetry sent by VRCFaceTracking,
 * PSVR2Toolkit, or custom eye-tracking daemons.
 */
public class OscGazeReceiver implements Runnable {
    private final int port;
    private final EyeTrackingManager manager;
    private volatile boolean running = false;
    private DatagramSocket socket;

    private long packetsReceived = 0;
    private long packetsDropped = 0;
    private long lastPacketTimeMs = 0;

    public OscGazeReceiver(int port, EyeTrackingManager manager) {
        this.port = port;
        this.manager = manager;
    }

    public void start() {
        if (running) return;
        running = true;
        Thread thread = new Thread(this, "Sclera-Gaze-UDP-" + port);
        thread.setDaemon(true);
        thread.start();
    }

    public void stop() {
        running = false;
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
    }

    public int getPort() {
        return port;
    }

    public long getPacketsReceived() {
        return packetsReceived;
    }

    public long getPacketsDropped() {
        return packetsDropped;
    }

    public long getLastPacketTimeMs() {
        return lastPacketTimeMs;
    }

    @Override
    public void run() {
        byte[] buffer = new byte[4096];
        try {
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(65536);

            while (running && !socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                lastPacketTimeMs = System.currentTimeMillis();
                parsePacket(packet.getData(), packet.getLength());
            }
        } catch (Exception e) {
            // Ignored when stopped normally
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    public void parsePacket(byte[] data, int length) {
        if (data == null || length < 4 || length > data.length) {
            packetsDropped++;
            return;
        }

        // 1. Plain JSON
        if (data[0] == '{') {
            parseJson(new String(data, 0, length, StandardCharsets.UTF_8));
            return;
        }

        // 2. OSC #bundle
        if (length >= 16 && data[0] == '#' && data[1] == 'b' && data[2] == 'u' && data[3] == 'n' &&
            data[4] == 'd' && data[5] == 'l' && data[6] == 'e' && data[7] == 0) {
            int offset = 16; // Skip "#bundle\0" (8 bytes) + timetag (8 bytes)
            while (offset + 4 <= length) {
                int elemSize = ((data[offset] & 0xFF) << 24)
                             | ((data[offset + 1] & 0xFF) << 16)
                             | ((data[offset + 2] & 0xFF) << 8)
                             | (data[offset + 3] & 0xFF);
                offset += 4;
                if (elemSize <= 0 || elemSize > length - offset) {
                    packetsDropped++;
                    break;
                }
                parseMessage(data, offset, elemSize);
                offset += elemSize;
            }
            return;
        }

        // 3. Standalone OSC Message
        parseMessage(data, 0, length);
    }

    private void parseMessage(byte[] data, int startOffset, int length) {
        try {
            int offset = startOffset;
            int maxEnd = startOffset + length;

            String address = readNullTerminatedString(data, offset, maxEnd);
            if (address == null) {
                packetsDropped++;
                return;
            }
            offset = align4(offset + address.length() + 1);

            if (offset >= maxEnd) {
                packetsDropped++;
                return;
            }

            String typeTags = readNullTerminatedString(data, offset, maxEnd);
            if (typeTags == null || !typeTags.startsWith(",")) {
                packetsDropped++;
                return;
            }
            offset = align4(offset + typeTags.length() + 1);

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, offset, maxEnd - offset));

            if (address.equals("/tracking/eye/LeftRightPitchYaw") && typeTags.equals(",ffff")) {
                float leftPitch = dis.readFloat();
                float leftYaw = dis.readFloat();
                float rightPitch = dis.readFloat();
                float rightYaw = dis.readFloat();

                boolean leftValid = isValidAngle(leftPitch) && isValidAngle(leftYaw);
                boolean rightValid = isValidAngle(rightPitch) && isValidAngle(rightYaw);

                if (leftValid || rightValid) {
                    if (!acceptValidPacket()) return;
                    // Update atomic binocular sample for foveated rendering
                    manager.onBinocularGazeReceived(leftPitch, leftYaw, leftValid, rightPitch, rightYaw, rightValid);

                    // Update smoothed average for gameplay cursor interaction
                    float avgPitch = (leftValid && rightValid) ? (leftPitch + rightPitch) * 0.5f : (leftValid ? leftPitch : rightPitch);
                    float avgYaw = (leftValid && rightValid) ? (leftYaw + rightYaw) * 0.5f : (leftValid ? leftYaw : rightYaw);
                    manager.onCursorGazeReceived(avgPitch, avgYaw, false);
                } else {
                    packetsDropped++;
                }
            } else if (address.equals("/tracking/eye/EyesClosedAmount") && typeTags.equals(",f")) {
                float closed = dis.readFloat();
                if (!Float.isNaN(closed) && !Float.isInfinite(closed)) {
                    if (!acceptValidPacket()) return;
                    manager.onEyeLidReceived(Math.max(0.0f, Math.min(1.0f, 1.0f - closed)));
                } else {
                    packetsDropped++;
                }
            } else if (typeTags.equals(",f")) {
                float value = dis.readFloat();
                if (!Float.isNaN(value) && !Float.isInfinite(value)) {
                    handleSingleParameter(address, value);
                } else {
                    packetsDropped++;
                }
            }
        } catch (Exception e) {
            packetsDropped++;
        }
    }

    private static boolean isValidAngle(float angle) {
        return !Float.isNaN(angle) && !Float.isInfinite(angle) && Math.abs(angle) <= 90.0f;
    }

    private void handleSingleParameter(String address, float value) {
        boolean lid = address.equals("/avatar/parameters/EyeLidLeft")
                || address.equals("/avatar/parameters/EyeLidRight") || address.equals("/avatar/parameters/EyeLids");
        boolean angle = switch (address) {
            case "/avatar/parameters/EyesPitch", "/avatar/parameters/v2/EyesPitch",
                    "/avatar/parameters/EyesYaw", "/avatar/parameters/v2/EyesYaw",
                    "/avatar/parameters/EyeLeftPitch", "/avatar/parameters/EyeLeftYaw",
                    "/avatar/parameters/EyeRightPitch", "/avatar/parameters/EyeRightYaw" -> true;
            default -> false;
        };
        if ((!lid && !angle) || (angle && !isValidAngle(value))) { packetsDropped++; return; }
        if (!acceptValidPacket()) return;
        if (lid) value = Math.max(0f, Math.min(1f, value));
        switch (address) {
            case "/avatar/parameters/EyesPitch":
            case "/avatar/parameters/v2/EyesPitch":
                manager.onPitchReceived(value);
                break;
            case "/avatar/parameters/EyesYaw":
            case "/avatar/parameters/v2/EyesYaw":
                manager.onYawReceived(value);
                break;
            case "/avatar/parameters/EyeLeftPitch":
                manager.onLeftPitchReceived(value);
                break;
            case "/avatar/parameters/EyeLeftYaw":
                manager.onLeftYawReceived(value);
                break;
            case "/avatar/parameters/EyeRightPitch":
                manager.onRightPitchReceived(value);
                break;
            case "/avatar/parameters/EyeRightYaw":
                manager.onRightYawReceived(value);
                break;
            case "/avatar/parameters/EyeLidLeft":
                manager.onEyeLidReceived(true, value);
                break;
            case "/avatar/parameters/EyeLidRight":
                manager.onEyeLidReceived(false, value);
                break;
            case "/avatar/parameters/EyeLids":
                manager.onEyeLidReceived(value);
                break;
        }
    }

    private void parseJson(String json) {
        try {
            com.google.gson.JsonObject object = com.google.gson.JsonParser.parseString(json).getAsJsonObject();
            float yaw = object.get("yaw").getAsFloat();
            float pitch = object.get("pitch").getAsFloat();
            boolean blink = object.has("blink") && object.get("blink").getAsBoolean();
            if (isValidAngle(yaw) && isValidAngle(pitch)) {
                if (!acceptValidPacket()) return;
                manager.onRawGazeReceived(pitch, yaw, blink);
            } else {
                packetsDropped++;
            }
        } catch (Exception ignored) {
            packetsDropped++;
        }
    }

    private static String readNullTerminatedString(byte[] data, int offset, int maxEnd) {
        int end = offset;
        while (end < maxEnd && data[end] != 0) {
            end++;
        }
        if (end >= maxEnd) return null;
        return new String(data, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static int align4(int pos) {
        return (pos + 3) & ~3;
    }

    private boolean acceptValidPacket() {
        if (!manager.acceptPacketFromPort(port)) { packetsDropped++; return false; }
        packetsReceived++;
        return true;
    }
}
