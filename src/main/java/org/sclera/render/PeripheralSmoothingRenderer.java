package org.sclera.render;

import net.minecraft.client.gl.Framebuffer;
import org.joml.Vector2f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL20;
import org.lwjgl.opengl.GL30;

import java.nio.FloatBuffer;

/**
 * Light blur outside the sharp gaze area. Uses a separate copy to avoid reading and writing the same image.
 */
public class PeripheralSmoothingRenderer {
    private static final PeripheralSmoothingRenderer INSTANCE = new PeripheralSmoothingRenderer();

    private int shaderProgram = 0;
    private int uSamplerLoc = -1;
    private int uGazeUVLoc = -1;
    private int uFocalRadiusUVLoc = -1;
    private int uTransitionRadiusUVLoc = -1;
    private int uTexelSizeLoc = -1;
    private int uStrengthLoc = -1;

    private int quadVao = 0;
    private int quadVbo = 0;

    private int pingPongFbo = 0;
    private int pingPongTexture = 0;
    private int currentW = 0;
    private int currentH = 0;
    private boolean failed;
    private final java.nio.IntBuffer savedViewport = BufferUtils.createIntBuffer(4);
    private final java.nio.ByteBuffer savedColorMask = BufferUtils.createByteBuffer(4);

    public static PeripheralSmoothingRenderer getInstance() {
        return INSTANCE;
    }

    private static final String VERTEX_SHADER =
            "#version 150\n" +
            "in vec2 aPos;\n" +
            "out vec2 vUV;\n" +
            "void main() {\n" +
            "    vUV = aPos * 0.5 + 0.5;\n" +
            "    gl_Position = vec4(aPos, 0.0, 1.0);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER =
            "#version 150\n" +
            "uniform sampler2D uSampler;\n" +
            "uniform vec2 uGazeUV;\n" +
            "uniform float uFocalRadiusUV;\n" +
            "uniform float uTransitionRadiusUV;\n" +
            "uniform vec2 uTexelSize;\n" +
            "uniform float uStrength;\n" +
            "in vec2 vUV;\n" +
            "out vec4 FragColor;\n" +
            "void main() {\n" +
            "    vec4 centerColor = texture(uSampler, vUV);\n" +
            "    float dist = distance(vUV, uGazeUV);\n" +
            "    if (dist <= uFocalRadiusUV || uStrength <= 0.001) {\n" +
            "        FragColor = centerColor;\n" +
            "        return;\n" +
            "    }\n" +
            "    float factor = smoothstep(uFocalRadiusUV, uTransitionRadiusUV, dist) * uStrength;\n" +
            "    vec2 step = uTexelSize * 2.5;\n" +
            "    vec4 sum = centerColor * 2.0;\n" +
            "    sum += texture(uSampler, vUV + vec2(-step.x,  0.0));\n" +
            "    sum += texture(uSampler, vUV + vec2( step.x,  0.0));\n" +
            "    sum += texture(uSampler, vUV + vec2( 0.0,    -step.y));\n" +
            "    sum += texture(uSampler, vUV + vec2( 0.0,     step.y));\n" +
            "    vec4 smoothed = sum * 0.1666667;\n" +
            "    FragColor = mix(centerColor, smoothed, factor);\n" +
            "}\n";

    private void initShaders() {
        if (shaderProgram != 0) return;

        int vs = compileShader(GL20.GL_VERTEX_SHADER, VERTEX_SHADER);
        int fs;
        try {
            fs = compileShader(GL20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER);
        } catch (RuntimeException error) {
            GL20.glDeleteShader(vs);
            throw error;
        }

        shaderProgram = GL20.glCreateProgram();
        GL20.glAttachShader(shaderProgram, vs);
        GL20.glAttachShader(shaderProgram, fs);
        GL20.glBindAttribLocation(shaderProgram, 0, "aPos");
        GL20.glLinkProgram(shaderProgram);

        GL20.glDeleteShader(vs);
        GL20.glDeleteShader(fs);
        if (GL20.glGetProgrami(shaderProgram, GL20.GL_LINK_STATUS) == GL11.GL_FALSE) {
            throw new IllegalStateException("Peripheral blur shader: " + GL20.glGetProgramInfoLog(shaderProgram));
        }

        uSamplerLoc = GL20.glGetUniformLocation(shaderProgram, "uSampler");
        uGazeUVLoc = GL20.glGetUniformLocation(shaderProgram, "uGazeUV");
        uFocalRadiusUVLoc = GL20.glGetUniformLocation(shaderProgram, "uFocalRadiusUV");
        uTransitionRadiusUVLoc = GL20.glGetUniformLocation(shaderProgram, "uTransitionRadiusUV");
        uTexelSizeLoc = GL20.glGetUniformLocation(shaderProgram, "uTexelSize");
        uStrengthLoc = GL20.glGetUniformLocation(shaderProgram, "uStrength");

        // Quad geometry
        float[] quadVertices = {
                -1.0f, -1.0f,
                 1.0f, -1.0f,
                 1.0f,  1.0f,
                -1.0f, -1.0f,
                 1.0f,  1.0f,
                -1.0f,  1.0f
        };
        FloatBuffer vBuf = BufferUtils.createFloatBuffer(quadVertices.length);
        vBuf.put(quadVertices).flip();

        quadVao = GL30.glGenVertexArrays();
        quadVbo = GL15.glGenBuffers();

        GL30.glBindVertexArray(quadVao);
        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, quadVbo);
        GL15.glBufferData(GL15.GL_ARRAY_BUFFER, vBuf, GL15.GL_STATIC_DRAW);
        GL20.glEnableVertexAttribArray(0);
        GL20.glVertexAttribPointer(0, 2, GL11.GL_FLOAT, false, 0, 0);

        GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
        GL30.glBindVertexArray(0);

    }

    private int compileShader(int type, String src) {
        int s = GL20.glCreateShader(type);
        GL20.glShaderSource(s, src);
        GL20.glCompileShader(s);
        if (GL20.glGetShaderi(s, GL20.GL_COMPILE_STATUS) == GL11.GL_FALSE) {
            String message = GL20.glGetShaderInfoLog(s);
            GL20.glDeleteShader(s);
            throw new IllegalStateException("Peripheral blur shader: " + message);
        }
        return s;
    }

    private void ensurePingPong(int w, int h) {
        if (pingPongFbo != 0 && currentW == w && currentH == h) {
            return;
        }

        destroyPingPong();

        currentW = w;
        currentH = h;

        pingPongTexture = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, pingPongTexture);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_S, GL12.GL_CLAMP_TO_EDGE);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_WRAP_T, GL12.GL_CLAMP_TO_EDGE);

        // The scene has already passed through the shader pack's final color output.
        org.lwjgl.opengl.GL42.glTexStorage2D(GL11.GL_TEXTURE_2D, 1, GL11.GL_RGBA8, w, h);
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, 0);

        pingPongFbo = GL30.glGenFramebuffers();
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, pingPongFbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0, GL11.GL_TEXTURE_2D, pingPongTexture, 0);
        if (GL30.glCheckFramebufferStatus(GL30.GL_FRAMEBUFFER) != GL30.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("Peripheral blur framebuffer is incomplete");
        }

        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, 0);
    }

    public void render(Framebuffer srcFramebuffer, Vector2f gazeUV) {
        if (srcFramebuffer == null || gazeUV == null || failed) return;
        int w = srcFramebuffer.viewportWidth > 0 ? srcFramebuffer.viewportWidth : srcFramebuffer.textureWidth;
        int h = srcFramebuffer.viewportHeight > 0 ? srcFramebuffer.viewportHeight : srcFramebuffer.textureHeight;
        if (w <= 0 || h <= 0) return;

        boolean wasCull = GL11.glIsEnabled(GL11.GL_CULL_FACE);
        boolean wasDepth = GL11.glIsEnabled(GL11.GL_DEPTH_TEST);
        boolean wasBlend = GL11.glIsEnabled(GL11.GL_BLEND);
        boolean wasDepthMask = GL11.glGetBoolean(GL11.GL_DEPTH_WRITEMASK);
        boolean srgbEnabled = GL11.glIsEnabled(GL30.GL_FRAMEBUFFER_SRGB);
        boolean wasScissor = GL11.glIsEnabled(GL11.GL_SCISSOR_TEST);
        boolean wasStencil = GL11.glIsEnabled(GL11.GL_STENCIL_TEST);
        int program = GL11.glGetInteger(GL20.GL_CURRENT_PROGRAM);
        int vao = GL11.glGetInteger(GL30.GL_VERTEX_ARRAY_BINDING);
        int arrayBuffer = GL11.glGetInteger(GL15.GL_ARRAY_BUFFER_BINDING);
        int readFbo = GL11.glGetInteger(GL30.GL_READ_FRAMEBUFFER_BINDING);
        int drawFbo = GL11.glGetInteger(GL30.GL_DRAW_FRAMEBUFFER_BINDING);
        int activeTexture = GL11.glGetInteger(GL13.GL_ACTIVE_TEXTURE);
        GL13.glActiveTexture(GL13.GL_TEXTURE0);
        int texture = GL11.glGetInteger(GL11.GL_TEXTURE_BINDING_2D);
        int sampler = GL11.glGetInteger(org.lwjgl.opengl.GL33.GL_SAMPLER_BINDING);
        savedViewport.clear();
        GL11.glGetIntegerv(GL11.GL_VIEWPORT, savedViewport);
        savedColorMask.clear();
        GL11.glGetBooleanv(GL11.GL_COLOR_WRITEMASK, savedColorMask);

        try {
            initShaders();
            if (shaderProgram == 0) return;

            ensurePingPong(w, h);
            GL11.glDisable(GL11.GL_SCISSOR_TEST);
            GL11.glDisable(GL11.GL_STENCIL_TEST);
            GL11.glColorMask(true, true, true, true);

            if (srgbEnabled) {
                GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
            }

            FoveationConfig config = FoveationConfig.getInstance();
            boolean head = org.sclera.tracking.EyeTrackingManager.getInstance().getTrackingMode()
                    == org.sclera.tracking.EyeTrackingManager.TrackingMode.HEAD_TRACKING;
            float focalUV = (config.getFocalRadiusDeg() + (head ? 3f : 0f)) / config.getApproxFovDeg();
            float transUV = (config.getTransitionRadiusDeg() + (head ? 4f : 0f)) / config.getApproxFovDeg();
            float strength = config.getSmoothingStrength();


            // Step 1: Copy raw world framebuffer into pingPongFbo via fast GPU blit
            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, srcFramebuffer.fbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, pingPongFbo);
            GL30.glBlitFramebuffer(0, 0, w, h, 0, 0, w, h, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);

            // Step 2: Render smoothed result back into srcFramebuffer
            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, srcFramebuffer.fbo);
            GL11.glViewport(0, 0, w, h);

            GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(false);
            GL11.glDisable(GL11.GL_BLEND);
            GL11.glDisable(GL11.GL_CULL_FACE);

            GL20.glUseProgram(shaderProgram);

            GL20.glUniform1i(uSamplerLoc, 0);
            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, pingPongTexture);
            org.lwjgl.opengl.GL33.glBindSampler(0, 0);

            GL20.glUniform2f(uGazeUVLoc, gazeUV.x, gazeUV.y);
            GL20.glUniform1f(uFocalRadiusUVLoc, focalUV);
            GL20.glUniform1f(uTransitionRadiusUVLoc, transUV);
            GL20.glUniform2f(uTexelSizeLoc, 1.0f / (float) w, 1.0f / (float) h);
            GL20.glUniform1f(uStrengthLoc, strength);

            GL30.glBindVertexArray(quadVao);
            GL11.glDrawArrays(GL11.GL_TRIANGLES, 0, 6);
            GL30.glBindVertexArray(0);
        } catch (RuntimeException error) {
            failed = true;
            org.slf4j.LoggerFactory.getLogger("Sclera").error("Peripheral blur disabled after a rendering error", error);
            destroy();
        } finally {
            // Restore the caller's state, including shader pack bindings.
            GL20.glUseProgram(program);
            GL30.glBindVertexArray(vao);
            GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, arrayBuffer);

            GL13.glActiveTexture(GL13.GL_TEXTURE0);
            GL11.glBindTexture(GL11.GL_TEXTURE_2D, texture);
            org.lwjgl.opengl.GL33.glBindSampler(0, sampler);
            GL13.glActiveTexture(activeTexture);

            GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, readFbo);
            GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, drawFbo);
            GL11.glViewport(savedViewport.get(0), savedViewport.get(1), savedViewport.get(2), savedViewport.get(3));
            GL11.glColorMask(savedColorMask.get(0) != 0, savedColorMask.get(1) != 0,
                    savedColorMask.get(2) != 0, savedColorMask.get(3) != 0);
            if (wasScissor) GL11.glEnable(GL11.GL_SCISSOR_TEST); else GL11.glDisable(GL11.GL_SCISSOR_TEST);
            if (wasStencil) GL11.glEnable(GL11.GL_STENCIL_TEST); else GL11.glDisable(GL11.GL_STENCIL_TEST);

            if (wasBlend) GL11.glEnable(GL11.GL_BLEND); else GL11.glDisable(GL11.GL_BLEND);
            if (wasDepth) GL11.glEnable(GL11.GL_DEPTH_TEST); else GL11.glDisable(GL11.GL_DEPTH_TEST);
            GL11.glDepthMask(wasDepthMask);
            if (wasCull) GL11.glEnable(GL11.GL_CULL_FACE); else GL11.glDisable(GL11.GL_CULL_FACE);
            if (srgbEnabled) GL11.glEnable(GL30.GL_FRAMEBUFFER_SRGB); else GL11.glDisable(GL30.GL_FRAMEBUFFER_SRGB);
        }
    }

    private void destroyPingPong() {
        if (pingPongFbo != 0) {
            GL30.glDeleteFramebuffers(pingPongFbo);
            pingPongFbo = 0;
        }
        if (pingPongTexture != 0) {
            GL11.glDeleteTextures(pingPongTexture);
            pingPongTexture = 0;
        }
        currentW = 0;
        currentH = 0;
    }

    public void destroy() {
        destroyPingPong();
        if (shaderProgram != 0) {
            GL20.glDeleteProgram(shaderProgram);
            shaderProgram = 0;
        }
        if (quadVao != 0) {
            GL30.glDeleteVertexArrays(quadVao);
            quadVao = 0;
        }
        if (quadVbo != 0) {
            GL15.glDeleteBuffers(quadVbo);
            quadVbo = 0;
        }
    }
}
