package org.sclera.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.joml.Vector2f;
import org.joml.Vector3f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.tracking.RenderGazeSample;

/**
 * Renders thick, highly visible visual rings directly into the OpenXR eye render target.
 * Displays:
 * 1. White reticle at the center of gaze.
 * 2. Cyan/Green ribbon for the 1x1 focal region boundary.
 * 3. Yellow/Orange ribbon for the 2x2 transition boundary.
 * 4. Red ribbon for the 4x4 peripheral boundary.
 */
public class FoveationDebugRenderer {

    private static final Vector3f tempGazeVec = new Vector3f();
    private static final Vector2f tempGazeUV = new Vector2f();

    public static void render(boolean isLeftEye, Matrix4f projMatrix, int viewportW, int viewportH) {
        FoveationConfig config = FoveationConfig.getInstance();
        if (!config.isDebugOverlay()) return;

        // Save current FBO binding and viewport to prevent state pollution in Visor/Iris
        int prevFbo = GL11.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] prevViewport = new int[4];
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, prevViewport);

        // Ensure we are targeting the active eye framebuffer
        org.vmstudio.visor.core.client.render.VRRendererBase vrRenderer = org.vmstudio.visor.core.client.ClientContext.renderer;
        if (vrRenderer != null) {
            if (isLeftEye && vrRenderer.getTextureLeftEye() != null) {
                vrRenderer.getTextureLeftEye().getRenderTarget().beginWrite(true);
            } else if (!isLeftEye && vrRenderer.getTextureRightEye() != null) {
                vrRenderer.getTextureRightEye().getRenderTarget().beginWrite(true);
            }
        }

        RenderGazeSample sample = EyeTrackingManager.getInstance().getLatestRenderGazeSample();
        boolean hasSample = (sample != null && sample.isFresh(150_000_000L));

        float cx = viewportW * 0.5f;
        float cy = viewportH * 0.5f;
        boolean tracked = false;

        if (hasSample && projMatrix != null) {
            float pitch = sample.getPitchForEye(isLeftEye);
            float yaw = sample.getYawForEye(isLeftEye);
            FoveationMath.gazeAnglesToVector(pitch, yaw, tempGazeVec);

            if (FoveationMath.projectGazeToUV(tempGazeVec, projMatrix, tempGazeUV)) {
                cx = tempGazeUV.x * viewportW;
                cy = tempGazeUV.y * viewportH;
                tracked = true;
            }
        }

        float approxFov = config.getApproxFovDeg();
        float focalDeg = config.getFocalRadiusDeg();
        float transDeg = config.getTransitionRadiusDeg();

        float rFocal = (focalDeg / approxFov) * viewportW;
        float rTrans = (transDeg / approxFov) * viewportW;
        float rOuter = rTrans * 1.35f;

        // Save original RenderSystem matrices and sorting
        Matrix4f origProj = RenderSystem.getProjectionMatrix();
        VertexSorter origSorter = RenderSystem.getVertexSorting();

        // CRITICAL: Push and reset ModelViewStack to identity so 3D camera transforms don't clip the 2D overlay
        RenderSystem.getModelViewStack().push();
        RenderSystem.getModelViewStack().peek().getPositionMatrix().identity();
        RenderSystem.applyModelViewMatrix();

        // Setup 2D orthographic projection matching the eye render target
        Matrix4f ortho = new Matrix4f().ortho(0, viewportW, 0, viewportH, -1000, 1000);
        RenderSystem.setProjectionMatrix(ortho, VertexSorter.BY_Z);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0f, 1.0f, 1.0f, 1.0f);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();

        // Distinct colors: Tracked (Cyan/Gold/Red/White) vs Fallback (Purple/Orange/Coral/Yellow)
        float r1 = tracked ? 0.0f : 0.8f;
        float g1 = tracked ? 1.0f : 0.2f;
        float b1 = tracked ? 0.7f : 1.0f;

        float r2 = tracked ? 1.0f : 1.0f;
        float g2 = tracked ? 0.85f : 0.5f;
        float b2 = tracked ? 0.0f : 0.1f;

        float r3 = tracked ? 1.0f : 0.9f;
        float g3 = tracked ? 0.2f : 0.1f;
        float b3 = tracked ? 0.2f : 0.1f;

        // 1. Center Focal Dot (small discrete 4px dot, avoids conflicting with Sclera 3D crosshair)
        drawThickCircle(tessellator, buffer, cx, cy, 3.0f, 3.0f, 1.0f, 1.0f, 1.0f, 0.95f);

        // 2. Focal boundary ring (Cyan/Green: 1x1 rate) - 14px thick ribbon
        drawThickCircle(tessellator, buffer, cx, cy, rFocal, 14.0f, r1, g1, b1, 0.90f);

        // 3. Transition boundary ring (Yellow/Gold: 2x2 rate) - 16px thick ribbon
        drawThickCircle(tessellator, buffer, cx, cy, rTrans, 16.0f, r2, g2, b2, 0.90f);

        // 4. Outer peripheral boundary ring (Coral/Red: 4x4 rate) - 18px thick ribbon
        drawThickCircle(tessellator, buffer, cx, cy, rOuter, 18.0f, r3, g3, b3, 0.80f);

        // Restore OpenGL state and matrices cleanly
        RenderSystem.enableDepthTest();
        RenderSystem.enableCull();

        RenderSystem.setProjectionMatrix(origProj, origSorter);
        RenderSystem.getModelViewStack().pop();
        RenderSystem.applyModelViewMatrix();

        // Restore previous viewport and framebuffer binding
        RenderSystem.viewport(prevViewport[0], prevViewport[1], prevViewport[2], prevViewport[3]);
        org.lwjgl.opengl.GL30.glBindFramebuffer(org.lwjgl.opengl.GL30.GL_FRAMEBUFFER, prevFbo);
    }

    private static void drawThickCircle(
            Tessellator tessellator, BufferBuilder buffer,
            float cx, float cy, float radius, float thickness,
            float r, float g, float b, float a) {

        int segments = 64;
        float rIn = Math.max(1.0f, radius - (thickness * 0.5f));
        float rOut = radius + (thickness * 0.5f);

        buffer.begin(VertexFormat.DrawMode.TRIANGLE_STRIP, VertexFormats.POSITION_COLOR);
        for (int i = 0; i <= segments; i++) {
            double angle = 2.0 * Math.PI * i / segments;
            float cos = (float) Math.cos(angle);
            float sin = (float) Math.sin(angle);

            buffer.vertex(cx + cos * rIn, cy + sin * rIn, 0.0f).color(r, g, b, a).next();
            buffer.vertex(cx + cos * rOut, cy + sin * rOut, 0.0f).color(r, g, b, a).next();
        }
        tessellator.draw();
    }
}
