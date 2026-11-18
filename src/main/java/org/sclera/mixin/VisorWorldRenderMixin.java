package org.sclera.mixin;

import me.phoenixra.atumvr.api.enums.EyeType;
import me.phoenixra.atumvr.api.rendering.AtumVRRenderContext;
import org.joml.Matrix4f;
import org.sclera.render.NvFoveationRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.api.client.render.VRRenderPass;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.render.VRRendererBase;
import org.vmstudio.visor.core.client.render.context.RenderContext;

/**
 * Owns VRS around the complete eye game render, including shader composition (1.3.3 scope).
 */
@Mixin(value = org.vmstudio.visor.core.client.provider.VisorScene.class, remap = false)
public class VisorWorldRenderMixin {
    @Inject(method = "render", at = @At("HEAD"))
    private void sclera$onBeginScene(AtumVRRenderContext context, CallbackInfo ci) {
        NvFoveationRenderer.getInstance().beginScene();
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void sclera$onEndScene(AtumVRRenderContext context, CallbackInfo ci) {
        NvFoveationRenderer.getInstance().endEye();
    }

    @Inject(
        method = "renderPass",
        at = @At(
            value = "INVOKE",
            target = "Lorg/vmstudio/visor/api/compatibility/mcversion/render/McRenderUtils;renderGame(Lnet/minecraft/class_757;FJZ)V"
        )
    )
    private void sclera$onBeforeRenderGame(VRRenderPass pass, RenderContext context, CallbackInfo ci) {
        if (pass == null || !pass.isEye()) return;
        if (context == null || !context.renderLevel()) return;

        net.minecraft.client.MinecraftClient mc = net.minecraft.client.MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;

        if (org.vmstudio.visor.core.client.render.VRRenderState.isInMainMenu()) return;

        EyeType eye = pass.getEyeOrLeft();
        boolean isLeft = (eye == EyeType.LEFT);

        VRRendererBase vrRenderer = ClientContext.renderer;
        if (vrRenderer != null) {
            Matrix4f proj = vrRenderer.getEyeProjection(eye);

            NvFoveationRenderer.getInstance().beginEye(isLeft, proj);
        }
    }

    @com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation(method = "renderPass", at = @At(
            value = "INVOKE", target = "Lorg/vmstudio/visor/api/compatibility/mcversion/render/McRenderUtils;renderGame(Lnet/minecraft/class_757;FJZ)V"))
    private void sclera$restoreAfterFailure(@org.spongepowered.asm.mixin.injection.Coerce Object renderer,
            float tickDelta, long time, boolean renderLevel,
            com.llamalad7.mixinextras.injector.wrapoperation.Operation<Void> original) {
        try { original.call(renderer, tickDelta, time, renderLevel); }
        finally { NvFoveationRenderer.getInstance().endEye(); }
    }

    @Inject(
        method = "renderPass",
        at = @At(
            value = "INVOKE",
            target = "Lorg/vmstudio/visor/api/compatibility/mcversion/render/McRenderUtils;renderGame(Lnet/minecraft/class_757;FJZ)V",
            shift = At.Shift.AFTER
        )
    )
    private void sclera$onAfterRenderGame(VRRenderPass pass, RenderContext context, CallbackInfo ci) {

        // Restore VRS before drawing the optional filter and overlays.
        NvFoveationRenderer.getInstance().endEye();
        if (pass == null || !pass.isEye() || context == null || !context.renderLevel()) return;
        org.sclera.render.FoveationConfig config = org.sclera.render.FoveationConfig.getInstance();
        NvFoveationRenderer foveation = NvFoveationRenderer.getInstance();
        boolean left = pass.getEyeOrLeft() == EyeType.LEFT;
        boolean smoothing = config.isPeripheralSmoothingEnabled() && config.getSmoothingStrength() > 0f;
        if (config.isEnabled() && smoothing && !config.isTestCutoutMode()
                && !foveation.isMenu() && foveation.isEyeVrsActive(left)) {
            org.sclera.render.PeripheralSmoothingRenderer.getInstance().render(
                    net.minecraft.client.MinecraftClient.getInstance().getFramebuffer(),
                    foveation.getEyeGazeUV(left));
        }
    }


    @Inject(method = "renderPass", at = @At("RETURN"))
    private void sclera$onEndRenderPass(VRRenderPass pass, RenderContext context, CallbackInfo ci) {
        NvFoveationRenderer.getInstance().endEye();
    }
}
