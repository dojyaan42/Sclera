package org.sclera.mixin;

import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.core.client.VisorState;

/** Prevents a stale hand target from drawing a block outline during another VR render pass. */
@Mixin(WorldRenderer.class)
public class BlockOutlineMixin {
    @Inject(method = "drawBlockOutline", at = @At("HEAD"), cancellable = true)
    private void sclera$onlyDrawCurrentTarget(MatrixStack matrices, VertexConsumer vertexConsumer,
            Entity entity, double cameraX, double cameraY, double cameraZ, BlockPos pos, BlockState state,
            CallbackInfo ci) {
        if (VisorState.get().isNotActive()) return;

        MinecraftClient client = MinecraftClient.getInstance();
        if (!(client.crosshairTarget instanceof BlockHitResult target)
                || !target.getBlockPos().equals(pos)) {
            ci.cancel();
        }
    }
}
