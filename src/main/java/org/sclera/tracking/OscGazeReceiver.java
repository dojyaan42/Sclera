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

    @Override
    public void run() {
        byte[] buffer = new byte[4096];
        try {
            socket = new DatagramSocket(port);
            socket.setReceiveBufferSize(65536);

            while (running && !socket.isClosed()) {
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                parsePacket(packet.getData(), packet.getLength());
            }
        } catch (Exception e) {
            // Ignored when stopped
        } finally {
            if (socket != null && !socket.isClosed()) {
                socket.close();
            }
        }
    }

    private void parsePacket(byte[] data, int length) {
        if (length < 4) return;

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
                if (elemSize <= 0 || offset + elemSize > length) break;
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
            if (address == null) return;
            offset = align4(offset + address.length() + 1);

            if (offset >= maxEnd) return;

            String typeTags = readNullTerminatedString(data, offset, maxEnd);
            if (typeTags == null || !typeTags.startsWith(",")) return;
            offset = align4(offset + typeTags.length() + 1);

            DataInputStream dis = new DataInputStream(new ByteArrayInputStream(data, offset, maxEnd - offset));

            if (address.equals("/tracking/eye/LeftRightPitchYaw") && typeTags.equals(",ffff")) {
                float leftPitch = dis.readFloat();
                float leftYaw = dis.readFloat();
                float rightPitch = dis.readFloat();
                float rightYaw = dis.readFloat();
                float avgPitch = (leftPitch + rightPitch) * 0.5f;
                float avgYaw = (leftYaw + rightYaw) * 0.5f;
                manager.onRawGazeReceived(avgPitch, avgYaw, false);
            } else if (address.equals("/tracking/eye/EyesClosedAmount") && typeTags.equals(",f")) {
                float closed = dis.readFloat();
                manager.onEyeLidReceived(1.0f - closed);
            } else if (typeTags.equals(",f")) {
                float value = dis.readFloat();
                handleSingleParameter(address, value);
            }
        } catch (Exception ignored) {
            // Ignore malformed element
        }
    }

    private void handleSingleParameter(String address, float value) {
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
            case "/avatar/parameters/EyeLidRight":
            case "/avatar/parameters/EyeLids":
                manager.onEyeLidReceived(value);
                break;
        }
    }

    private void parseJson(String json) {
        try {
            float yaw = 0f, pitch = 0f;
            boolean blink = false;
            boolean hasYaw = false, hasPitch = false;

            for (String part : json.replace("{", "").replace("}", "").replace("\"", "").split(",")) {
                String[] kv = part.split(":");
                if (kv.length == 2) {
                    String key = kv[0].trim().toLowerCase();
                    String val = kv[1].trim();
                    if (key.equals("yaw")) {
                        yaw = Float.parseFloat(val);
                        hasYaw = true;
                    } else if (key.equals("pitch")) {
                        pitch = Float.parseFloat(val);
                        hasPitch = true;
                    } else if (key.equals("blink")) {
                        blink = Boolean.parseBoolean(val);
                    }
                }
            }
            if (hasYaw && hasPitch) {
                manager.onRawGazeReceived(pitch, yaw, blink);
            }
        } catch (Exception ignored) {}
    }

    private static String readNullTerminatedString(byte[] data, int offset, int maxEnd) {
        int end = offset;
        while (end < maxEnd && data[end] != 0) {
            end++;
        }
        if (end > maxEnd) return null;
        return new String(data, offset, end - offset, StandardCharsets.UTF_8);
    }

    private static int align4(int pos) {
        return (pos + 3) & ~3;
    }
}