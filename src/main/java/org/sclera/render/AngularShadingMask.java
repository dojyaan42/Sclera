package org.sclera.render;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import java.nio.ByteBuffer;
import java.util.Arrays;

/** Cached eye rays and conservative tile cones. Rebuilt only for projection/viewport changes. */
public final class AngularShadingMask {
    private final Matrix4f projection = new Matrix4f();
    private int width, height, tileWidth, tileHeight;
    private float[] cones = new float[0]; // xyz, cos(tile radius), sin(tile radius)
    private byte[] rates = new byte[0];
    private final int[] counts = new int[4];
    private boolean valid;

    public boolean prepare(Matrix4f matrix, int w, int h, int tw, int th) {
        if (w == width && h == height && tw == tileWidth && th == tileHeight && projection.equals(matrix)) return false;
        width = w; height = h; tileWidth = tw; tileHeight = th;
        projection.set(matrix);
        int gw = (w + tw - 1) / tw, gh = (h + th - 1) / th;
        cones = new float[gw * gh * 5];
        rates = new byte[gw * gh];
        Matrix4f inverse = new Matrix4f(matrix).invert();
        valid = inverse.isFinite() && matrix.m23() < 0f && matrix.m33() == 0f;
        if (!valid) return true;
        Vector3f center = new Vector3f(), corner = new Vector3f();
        for (int y = 0; y < gh; y++) for (int x = 0; x < gw; x++) {
            float x0 = x * tw, x1 = Math.min(w, (x + 1) * tw);
            float y0 = y * th, y1 = Math.min(h, (y + 1) * th);
            ray(inverse, (x0 + x1) / 2, (y0 + y1) / 2, center);
            float minDot = 1f;
            for (int c = 0; c < 4; c++) {
                ray(inverse, (c & 1) == 0 ? x0 : x1, (c & 2) == 0 ? y0 : y1, corner);
                minDot = Math.min(minDot, center.dot(corner));
            }
            int i = (y * gw + x) * 5;
            cones[i] = center.x; cones[i + 1] = center.y; cones[i + 2] = center.z;
            cones[i + 3] = Math.max(-1f, minDot);
            cones[i + 4] = (float) Math.sqrt(Math.max(0f, 1f - minDot * minDot));
        }
        return true;
    }

    private void ray(Matrix4f inverse, float x, float y, Vector3f out) {
        inverse.transformProject(out.set(2f * x / width - 1f, 2f * y / height - 1f, 0f));
        out.normalize();
    }

    public boolean isValid() { return valid; }
    public int count(int rate) { return counts[rate]; }

    public void generate(ByteBuffer output, Vector3f gaze, float focalDeg, float transitionDeg, boolean menu) {
        Arrays.fill(counts, 0);
        double f = Math.toRadians(focalDeg), t = Math.toRadians(Math.max(focalDeg, transitionDeg));
        float cf = (float) Math.cos(f), sf = (float) Math.sin(f);
        float ct = (float) Math.cos(t), st = (float) Math.sin(t);
        for (int i = 0; i < rates.length; i++) {
            int j = i * 5;
            byte rate = 0;
            if (menu) rate = 3;
            else if (valid && gaze != null && gaze.isFinite() && Float.isFinite(focalDeg) && Float.isFinite(transitionDeg)) {
                float dot = cones[j] * gaze.x + cones[j + 1] * gaze.y + cones[j + 2] * gaze.z;
                // Inflate the cone by the tile's angular radius so boundary tiles stay sharp.
                rate = dot >= cf * cones[j + 3] - sf * cones[j + 4] ? (byte) 0
                        : dot >= ct * cones[j + 3] - st * cones[j + 4] ? (byte) 1 : (byte) 2;
            }
            rates[i] = rate;
            counts[rate]++;
        }
        output.clear();
        output.put(rates).flip();
    }
}
