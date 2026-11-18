package org.sclera.render;

import net.minecraft.client.MinecraftClient;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.*;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.tracking.RenderGazeLatch;
import org.sclera.tracking.RenderGazeSample;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;

/** Stable filtered gaze and UV masks, with VRS scoped to the complete eye game render. */
public final class NvFoveationRenderer {
    private static final NvFoveationRenderer INSTANCE = new NvFoveationRenderer();
    private final int[] textures = new int[2];
    private final int[] widths = new int[2], heights = new int[2];
    private final Vector2f[] uv = {new Vector2f(.5f), new Vector2f(.5f)};
    private final Vector2f[] lastGaze = {new Vector2f(Float.NaN), new Vector2f(Float.NaN)};
    private final float[] lastFocal = {Float.NaN, Float.NaN}, lastTransition = {Float.NaN, Float.NaN};
    private final boolean[] lastMenu = new boolean[2], eyeActive = new boolean[2];
    private final float[] lastFov = {Float.NaN, Float.NaN};
    private final Vector3f gaze = new Vector3f();
    private final RenderGazeLatch latch = new RenderGazeLatch();
    private final IntBuffer viewport = BufferUtils.createIntBuffer(4);
    private ByteBuffer pixels;
    private boolean menu, ownsState, previousEnabled;
    private int previousImage;
    private FoveationConfig.PeripheralRate lastRate;
    private boolean lastBlackout;
    private int ownedImage;
    private int suspendDepth;

    public static NvFoveationRenderer getInstance() { return INSTANCE; }
    public Vector2f getEyeGazeUV(boolean left) { return uv[left ? 0 : 1]; }
    public boolean isEyeVrsActive(boolean left) { return eyeActive[left ? 0 : 1]; }
    public RenderGazeLatch gazeLatch() { return latch; }
    public boolean isMenu() { return menu; }

    public void beginScene() {
        endEye();
        eyeActive[0] = eyeActive[1] = false;
        MinecraftClient mc = MinecraftClient.getInstance();
        // A wrist overlay does not turn the surrounding world into a menu background.
        menu = mc != null && mc.currentScreen != null;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        if (manager.getTrackingMode() != EyeTrackingManager.TrackingMode.EYE_TRACKING) latch.clear();
        latch.latch(manager.getCalibratedRenderSample(), System.nanoTime());
    }

    public boolean beginEye(boolean left, Matrix4f projection) {
        endEye();
        int eye = left ? 0 : 1;
        eyeActive[eye] = false;
        FoveationConfig config = FoveationConfig.getInstance();
        FoveationDiagnostics diag = FoveationDiagnostics.getInstance();
        if (!config.isEnabled()) {
            diag.setCurrentMode(FoveationDiagnostics.Mode.DISABLED);
            return false;
        }
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null || !FoveationProbe.probe()
                || FoveationProbe.isRenderingShadowPass()) return false;
        viewport.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, viewport);
        int w = viewport.get(2), h = viewport.get(3);
        if (viewport.get(0) != 0 || viewport.get(1) != 0 || w <= 0 || h <= 0
                || GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING) == 0) {
            diag.setStatusReason("Full shading: unsupported world viewport");
            diag.setCurrentMode(FoveationDiagnostics.Mode.FALLBACK_100);
            return false;
        }
        int tw = diag.getTexelWidth(), th = diag.getTexelHeight();
        int gw = (w + tw - 1) / tw, gh = (h + th - 1) / th;
        boolean changed = false;
        boolean head = EyeTrackingManager.getInstance().getTrackingMode() == EyeTrackingManager.TrackingMode.HEAD_TRACKING;
        RenderGazeSample sample = latch.sample(left);
        if (menu || head) gaze.set(0, 0, -1);
        else {
            if (latch.state(left) == RenderGazeLatch.State.LOST) {
                diag.recordFrame(false, false);
                diag.setCurrentMode(FoveationDiagnostics.Mode.FALLBACK_100);
                diag.setStatusReason("Tracking unavailable (full shading)");
                return false;
            }
            FoveationMath.gazeAnglesToVector(Math.max(-80f, Math.min(80f, sample.getLeftPitch())),
                    Math.max(-80f, Math.min(80f, sample.getLeftYaw())), gaze);
        }
        if (!FoveationMath.projectGazeToUV(gaze, projection, uv[eye])) return false;
        if (textures[eye] == 0 || widths[eye] != gw || heights[eye] != gh) {
            if (textures[eye] != 0) GL11.glDeleteTextures(textures[eye]);
            textures[eye] = createTexture(gw, gh);
            widths[eye] = gw; heights[eye] = gh;
            changed = true;
        }
        if (pixels == null || pixels.capacity() < gw * gh) pixels = BufferUtils.createByteBuffer(gw * gh);
        if (lastRate != config.getPeripheralRate() || lastBlackout != config.isTestCutoutMode()) setPalette(config);
        float focal = config.getFocalRadiusDeg() + (head ? 3f : 0f);
        float transition = config.getTransitionRadiusDeg() + (head ? 4f : 0f);
        // Restore the 1.3.3 mask geometry and movement threshold.
        Vector2f previous = lastGaze[eye];
        float fov = config.getApproxFovDeg();
        boolean moved = !previous.isFinite() || Math.abs(previous.x - uv[eye].x) > .006f
                || Math.abs(previous.y - uv[eye].y) > .006f;
        if (changed || moved || focal != lastFocal[eye] || transition != lastTransition[eye]
                || fov != lastFov[eye] || menu != lastMenu[eye]) {
            if (menu) FoveationMath.generateUniformPeripheralBuffer(pixels, gw, gh);
            else FoveationMath.generateShadingRateBuffer(pixels, gw, gh, uv[eye], focal, transition, false, fov);
            upload(textures[eye], gw, gh);
            previous.set(uv[eye]); lastFocal[eye] = focal; lastTransition[eye] = transition;
            lastFov[eye] = fov; lastMenu[eye] = menu;
        }
        previousImage = GL11.glGetInteger(NVShadingRateImage.GL_SHADING_RATE_IMAGE_BINDING_NV);
        previousEnabled = GL11.glIsEnabled(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV);
        ownsState = true;
        ownedImage = textures[eye];
        suspendDepth = 0;
        NVShadingRateImage.glBindShadingRateImageNV(ownedImage);
        GL11.glEnable(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV);
        eyeActive[eye] = true;
        diag.recordFrame(true, !menu && !head && sample.isSaccade());
        diag.setCurrentMode(FoveationDiagnostics.Mode.VRS_ACTIVE);
        diag.setStatusReason(menu ? "VRS menu background" : "VRS filtered shared gaze (UV mask)");
        return true;
    }

    private void setPalette(FoveationConfig config) {
        lastRate = config.getPeripheralRate();
        lastBlackout = config.isTestCutoutMode();
        int fine = NVShadingRateImage.GL_SHADING_RATE_1_INVOCATION_PER_PIXEL_NV;
        int half = NVShadingRateImage.GL_SHADING_RATE_1_INVOCATION_PER_1X2_PIXELS_NV;
        int quarter = NVShadingRateImage.GL_SHADING_RATE_1_INVOCATION_PER_2X2_PIXELS_NV;
        int coarse = switch (lastRate) {
            case RATE_1X2 -> half;
            case RATE_2X2 -> quarter;
            case RATE_4X4 -> NVShadingRateImage.GL_SHADING_RATE_1_INVOCATION_PER_4X4_PIXELS_NV;
        };
        int transition = lastRate == FoveationConfig.PeripheralRate.RATE_4X4 ? quarter : half;
        int none = NVShadingRateImage.GL_SHADING_RATE_NO_INVOCATIONS_NV;
        int[] palette = {fine, lastBlackout ? none : transition, lastBlackout ? none : coarse, coarse};
        for (int vp = 0; vp < 4; vp++) NVShadingRateImage.glShadingRateImagePaletteNV(vp, 0, palette);
    }

    private int createTexture(int w, int h) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int texture = GL11.glGenTextures();
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_NEAREST);
            GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_NEAREST);
            GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL30.GL_R8UI, w, h);
            return texture;
        } finally { GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous); }
    }

    private void upload(int texture, int w, int h) {
        int previous = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int pbo = GL11.glGetInteger(GL21.GL_PIXEL_UNPACK_BUFFER_BINDING);
        int row = GL11.glGetInteger(GL11.GL_UNPACK_ROW_LENGTH);
        int skipX = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_PIXELS);
        int skipY = GL11.glGetInteger(GL11.GL_UNPACK_SKIP_ROWS);
        int alignment = GL11.glGetInteger(GL11.GL_UNPACK_ALIGNMENT);
        try {
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            if (pbo != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, 0);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, 1);
            GL11.glTexSubImage2D(GL11.GL_TEXTURE_2D, 0, 0, 0, w, h, GL30.GL_RED_INTEGER, GL11.GL_UNSIGNED_BYTE, pixels);
            NVShadingRateImage.glShadingRateImageBarrierNV(true);
        } finally {
            GL11.glPixelStorei(GL11.GL_UNPACK_ROW_LENGTH, row);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_PIXELS, skipX);
            GL11.glPixelStorei(GL11.GL_UNPACK_SKIP_ROWS, skipY);
            GL11.glPixelStorei(GL11.GL_UNPACK_ALIGNMENT, alignment);
            if (pbo != 0) GL15.glBindBuffer(GL21.GL_PIXEL_UNPACK_BUFFER, pbo);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, previous);
        }
    }

    public void endEye() {
        if (ownsState && suspendDepth == 0) {
            NVShadingRateImage.glBindShadingRateImageNV(previousImage);
            if (previousEnabled) GL11.glEnable(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV);
            else GL11.glDisable(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV);
        }
        ownsState = false;
        ownedImage = 0;
        suspendDepth = 0;
    }

    public void suspendForIncompatiblePass() {
        if (!ownsState) return;
        if (suspendDepth++ == 0) {
            NVShadingRateImage.glBindShadingRateImageNV(previousImage);
            if (previousEnabled) GL11.glEnable(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV);
            else GL11.glDisable(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV);
        }
    }

    public void resumeAfterIncompatiblePass() {
        if (!ownsState || suspendDepth == 0) return;
        if (--suspendDepth == 0) {
            NVShadingRateImage.glBindShadingRateImageNV(ownedImage);
            GL11.glEnable(NVShadingRateImage.GL_SHADING_RATE_IMAGE_NV);
        }
    }

    public void destroy() {
        endEye();
        for (int i = 0; i < 2; i++) {
            if (textures[i] != 0) GL11.glDeleteTextures(textures[i]);
            textures[i] = 0; eyeActive[i] = false; lastGaze[i].set(Float.NaN);
        }
        latch.clear(); pixels = null; lastRate = null;
    }
}
