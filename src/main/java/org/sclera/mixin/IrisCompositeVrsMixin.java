package org.sclera.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.irisshaders.iris.pathways.FullScreenQuadRenderer;
import net.irisshaders.iris.pipeline.CompositeRenderer;
import org.sclera.render.NvFoveationRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Disables eye-space VRS only while Iris draws a fullscreen composite quad. */
@Mixin(value = CompositeRenderer.class, remap = false)
public class IrisCompositeVrsMixin {
    @WrapOperation(
            method = "renderAll",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/irisshaders/iris/pathways/FullScreenQuadRenderer;renderQuad()V",
                    remap = false
            )
    )
    private void sclera$renderCompositeWithoutEyeVrs(FullScreenQuadRenderer renderer, Operation<Void> original) {
        NvFoveationRenderer vrs = NvFoveationRenderer.getInstance();
        vrs.suspendForIncompatiblePass();
        try {
            original.call(renderer);
        } finally {
            vrs.resumeAfterIncompatiblePass();
        }
    }
}
