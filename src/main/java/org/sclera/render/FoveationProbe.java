package org.sclera.render;

import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GLCapabilities;
import org.lwjgl.opengl.NVShadingRateImage;

import java.nio.IntBuffer;
import org.lwjgl.BufferUtils;

/**
 * Executes a real-time probe of the active OpenGL context on the rendering thread.
 * Determines if GL_NV_shading_rate_image is genuinely usable.
 */
public class FoveationProbe {
    private static final class IrisAccess {
        static Object instance;
        static java.lang.reflect.Method shadow;
        static java.lang.reflect.Method active;
        static {
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                instance = api.getMethod("getInstance").invoke(null);
                shadow = api.getMethod("isRenderingShadowPass");
                active = api.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError ignored) {}
        }
    }

    public static boolean probe() {
        FoveationDiagnostics diag = FoveationDiagnostics.getInstance();
        if (diag.isProbeExecuted()) {
            return diag.isVrsSupported();
        }

        try {
            String version = GL11.glGetString(GL11.GL_VERSION);
            String vendor = GL11.glGetString(GL11.GL_VENDOR);
            String renderer = GL11.glGetString(GL11.GL_RENDERER);

            GLCapabilities caps = GL.getCapabilities();
            boolean vrsSupported = caps.GL_NV_shading_rate_image;

            int texelW = 16;
            int texelH = 16;
            int paletteSize = 16;
            String status;

            if (vrsSupported) {
                try {
                    IntBuffer ib = BufferUtils.createIntBuffer(1);
                    GL11.glGetIntegerv(NVShadingRateImage.GL_SHADING_RATE_IMAGE_TEXEL_WIDTH_NV, ib);
                    texelW = ib.get(0);
                    ib.rewind();
                    GL11.glGetIntegerv(NVShadingRateImage.GL_SHADING_RATE_IMAGE_TEXEL_HEIGHT_NV, ib);
                    texelH = ib.get(0);
                    ib.rewind();
                    GL11.glGetIntegerv(NVShadingRateImage.GL_SHADING_RATE_IMAGE_PALETTE_SIZE_NV, ib);
                    paletteSize = ib.get(0);

                    status = "VRS Supported (Hardware Accelerated)";
                } catch (Throwable t) {
                    status = "VRS extension present but failed querying limits: " + t.getMessage();
                }
            } else {
                status = "GL_NV_shading_rate_image extension NOT reported by OpenGL context";
            }

            diag.setProbeResults(vrsSupported, version, vendor, renderer, texelW, texelH, paletteSize, status);
            return vrsSupported;
        } catch (Throwable t) {
            diag.setProbeResults(false, "Error", "Error", "Error", 16, 16, 16, "Probe exception: " + t.getMessage());
            return false;
        }
    }

    /**
     * Checks if custom Iris shader pack pipelines are currently active in Visor or Iris.
     */
     public static boolean isShaderActive() {
        try {
            if (org.vmstudio.visor.compatibility.ShaderCompatHelper.isShaderActive()) {
                return true;
            }
        } catch (Throwable ignored) {}
        try {
            if (IrisAccess.instance != null && IrisAccess.active != null) {
                return (boolean) IrisAccess.active.invoke(IrisAccess.instance);
            }
        } catch (Throwable ignored) {}
        return false;
    }

    /**
     * Checks if Iris is currently drawing to a shadow map framebuffer.
     */
    public static boolean isRenderingShadowPass() {
        try {
            if (IrisAccess.instance != null && IrisAccess.shadow != null) {
                return (boolean) IrisAccess.shadow.invoke(IrisAccess.instance);
            }
        } catch (Throwable ignored) {}
        return false;
    }
}
