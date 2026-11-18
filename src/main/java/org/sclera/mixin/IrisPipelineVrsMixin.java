package org.sclera.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.mixin.LevelRendererAccessor;
import net.irisshaders.iris.pipeline.FinalPassRenderer;
import net.irisshaders.iris.pipeline.IrisRenderingPipeline;
import net.irisshaders.iris.shadows.ShadowRenderer;
import net.minecraft.client.render.Camera;
import org.sclera.render.NvFoveationRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Keeps eye-space VRS away from Iris render targets that use different coordinates. */
@Mixin(value = IrisRenderingPipeline.class, remap = false)
public class IrisPipelineVrsMixin {
    @WrapOperation(
            method = "renderShadows",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/shadows/ShadowRenderer;renderShadows(Lnet/irisshaders/iris/mixin/LevelRendererAccessor;Lnet/minecraft/class_4184;)V",
                    remap = false
            )
    )
    private void sclera$renderShadowsWithoutEyeVrs(ShadowRenderer renderer,
            LevelRendererAccessor levelRenderer, Camera camera, Operation<Void> original) {
        NvFoveationRenderer vrs = NvFoveationRenderer.getInstance();
        vrs.suspendForIncompatiblePass();
        try {
            original.call(renderer, levelRenderer, camera);
        } finally {
            vrs.resumeAfterIncompatiblePass();
        }
    }

    @WrapOperation(
            method = "finalizeLevelRendering",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pipeline/FinalPassRenderer;renderFinalPass()V",
                    remap = false
            )
    )
    private void sclera$renderFinalPassWithoutEyeVrs(FinalPassRenderer renderer, Operation<Void> original) {
        NvFoveationRenderer vrs = NvFoveationRenderer.getInstance();
        vrs.suspendForIncompatiblePass();
        try {
            original.call(renderer);
        } finally {
            vrs.resumeAfterIncompatiblePass();
        }
    }
}
