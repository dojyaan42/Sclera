package org.sclera.render;

import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;

import java.nio.ByteBuffer;

/**
 * Mathematical transformations for foveated rendering:
 * 1. Pitch/Yaw angles to camera-space 3D ray.
 * 2. Camera-space ray to Screen UV via eye-specific projection matrix.
 * 3. Generation of Shading Rate Image (SRI) tile indices based on angular eccentricity.
 */
public class FoveationMath {

    public static final byte SHADING_RATE_1X1_INDEX = 0;
    public static final byte SHADING_RATE_2X2_INDEX = 1;
    public static final byte SHADING_RATE_4X4_INDEX = 2;

    /**
     * Converts pitch and yaw in degrees to a unit direction vector in eye camera space.
     * In VRCFT/OpenXR tracking: positive pitch is looking DOWN, positive yaw is looking RIGHT.
     * In OpenGL camera space: -Z is forward, +X is right, +Y is up, -Y is down.
     * Pitch is negated (-pitchDeg) so looking down points in -Y.
     */
    public static Vector3f gazeAnglesToVector(float pitchDeg, float yawDeg, Vector3f dest) {
        if (dest == null) dest = new Vector3f();

        float pitchRad = (float) Math.toRadians(-pitchDeg);
        float yawRad = (float) Math.toRadians(yawDeg);

        float cosPitch = (float) Math.cos(pitchRad);
        float sinPitch = (float) Math.sin(pitchRad);
        float cosYaw = (float) Math.cos(yawRad);
        float sinYaw = (float) Math.sin(yawRad);

        // Yaw rotates around Y, pitch rotates around X
        dest.x = sinYaw * cosPitch;
        dest.y = sinPitch;
        dest.z = -cosYaw * cosPitch;

        return dest.normalize();
    }

    /**
     * Projects a 3D ray in camera space onto screen UV coordinates [0, 1] using the eye's projection matrix.
     * Returns true if the ray is in front of the camera, false if behind.
     */
    public static boolean projectGazeToUV(Vector3f gazeDir, Matrix4f projMatrix, Vector2f destUV) {
        if (destUV == null) destUV = new Vector2f();

        if (gazeDir == null || projMatrix == null) {
            destUV.set(0.5f, 0.5f);
            return false;
        }

        // Place a point at depth 1.0 along the ray
        float x = projMatrix.m00() * gazeDir.x + projMatrix.m10() * gazeDir.y + projMatrix.m20() * gazeDir.z + projMatrix.m30();
        float y = projMatrix.m01() * gazeDir.x + projMatrix.m11() * gazeDir.y + projMatrix.m21() * gazeDir.z + projMatrix.m31();
        float w = projMatrix.m03() * gazeDir.x + projMatrix.m13() * gazeDir.y + projMatrix.m23() * gazeDir.z + projMatrix.m33();
        if (!Float.isFinite(x) || !Float.isFinite(y) || !Float.isFinite(w) || w <= 0.0001f) {
            destUV.set(0.5f, 0.5f);
            return false;
        }

        float ndcX = x / w;
        float ndcY = y / w;

        float u = (ndcX + 1.0f) * 0.5f;
        float v = (ndcY + 1.0f) * 0.5f;

        // Clamp to valid [0, 1] screen UV range
        destUV.x = Math.max(0.0f, Math.min(1.0f, u));
        destUV.y = Math.max(0.0f, Math.min(1.0f, v));
        return true;
    }

    public static final byte RATE_INDEX_FOCAL = 0;       // Palette Index 0: 1x1 (focal cone)
    public static final byte RATE_INDEX_TRANSITION = 1;  // Palette Index 1: Intermediate transition
    public static final byte RATE_INDEX_PERIPHERY = 2;   // Palette Index 2: Outer periphery
    public static final byte RATE_INDEX_MENU = 3;        // Palette Index 3: Coarse rate for pause/settings menus

    private static final ThreadLocal<byte[]> TILE_BUFFER = ThreadLocal.withInitial(() -> new byte[256 * 256]);

    /**
     * Uses one shading rate for the world behind a menu.
     */
    public static void generateUniformPeripheralBuffer(ByteBuffer buffer, int gridW, int gridH) {
        int totalTiles = gridW * gridH;
        byte[] rawArray = TILE_BUFFER.get();
        if (rawArray.length < totalTiles) {
            rawArray = new byte[totalTiles];
            TILE_BUFFER.set(rawArray);
        }

        java.util.Arrays.fill(rawArray, 0, totalTiles, RATE_INDEX_MENU);
        buffer.clear();
        buffer.put(rawArray, 0, totalTiles);
        buffer.flip();
    }

    /**
     * Fills a byte buffer with shading rate palette indices (0 = 1x1, 1 = transition, 2 = periphery)
     * corresponding to the 2D grid of tiles in the Shading Rate Image.
     */
    public static void generateShadingRateBuffer(
            ByteBuffer buffer,
            int gridW,
            int gridH,
            Vector2f gazeUV,
            float focalRadiusDeg,
            float transitionRadiusDeg,
            boolean isSaccade,
            float approxFovDeg) {
        generateShadingRateBuffer(buffer, gridW, gridH, gazeUV, focalRadiusDeg, transitionRadiusDeg, isSaccade, approxFovDeg, false, false);
    }

    public static void generateShadingRateBuffer(
            ByteBuffer buffer,
            int gridW,
            int gridH,
            Vector2f gazeUV,
            float focalRadiusDeg,
            float transitionRadiusDeg,
            boolean isSaccade,
            float approxFovDeg,
            boolean useRate2x2Periphery) {
        generateShadingRateBuffer(buffer, gridW, gridH, gazeUV, focalRadiusDeg, transitionRadiusDeg, isSaccade, approxFovDeg, useRate2x2Periphery, false);
    }

    public static void generateShadingRateBuffer(
            ByteBuffer buffer,
            int gridW,
            int gridH,
            Vector2f gazeUV,
            float focalRadiusDeg,
            float transitionRadiusDeg,
            boolean isSaccade,
            float approxFovDeg,
            boolean useRate2x2Periphery,
            boolean isTestCutoutMode) {

        int totalTiles = gridW * gridH;
        byte[] rawArray = TILE_BUFFER.get();
        if (rawArray.length < totalTiles) {
            rawArray = new byte[totalTiles];
            TILE_BUFFER.set(rawArray);
        }

        // If no valid gaze UV, fallback to full 1x1
        if (gazeUV == null) {
            java.util.Arrays.fill(rawArray, 0, totalTiles, SHADING_RATE_1X1_INDEX);
            buffer.clear();
            buffer.put(rawArray, 0, totalTiles);
            buffer.flip();
            return;
        }

        // Stable foveal and transition radii: zero radius popping or pulsating during saccades
        float focalRadiusUV = focalRadiusDeg / approxFovDeg;
        float transitionRadiusUV = transitionRadiusDeg / approxFovDeg;
        float focalRadiusSq = focalRadiusUV * focalRadiusUV;
        float transitionRadiusSq = transitionRadiusUV * transitionRadiusUV;

        float invGridW = 1.0f / (float) gridW;
        float invGridH = 1.0f / (float) gridH;

        int idx = 0;
        for (int y = 0; y < gridH; y++) {
            float tileCenterY = (y + 0.5f) * invGridH;
            float dy = tileCenterY - gazeUV.y;
            float dySq = dy * dy;

            for (int x = 0; x < gridW; x++) {
                float tileCenterX = (x + 0.5f) * invGridW;
                float dx = tileCenterX - gazeUV.x;
                float distSq = dx * dx + dySq;

                if (distSq <= focalRadiusSq) {
                    rawArray[idx++] = SHADING_RATE_1X1_INDEX;
                } else if (distSq <= transitionRadiusSq) {
                    rawArray[idx++] = SHADING_RATE_2X2_INDEX;
                } else {
                    rawArray[idx++] = SHADING_RATE_4X4_INDEX;
                }
            }
        }

        buffer.clear();
        buffer.put(rawArray, 0, totalTiles);
        buffer.flip();
    }
}
