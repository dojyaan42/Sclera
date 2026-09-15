package org.sclera.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(InGameHud.class)
public class InGameHudDebugMixin {

    @Shadow @Final private MinecraftClient client;

    @Inject(method = "render", at = @At("TAIL"))
    private void sclera$renderHudPointer(DrawContext context, float tickDelta, CallbackInfo ci) {
        // Disabled in-game: targeting in the 3D world is visualized directly in true 3D
        // via Visor's 3D crosshair and Minecraft's block outline, avoiding the 2D floating quad illusion.
    }
}
