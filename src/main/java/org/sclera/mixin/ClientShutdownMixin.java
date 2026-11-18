package org.sclera.mixin;

import net.minecraft.client.MinecraftClient;
import org.sclera.haptics.AdaptiveBowTriggers;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public abstract class ClientShutdownMixin {
    @Inject(method = "stop", at = @At("HEAD"))
    private void sclera$releaseAdaptiveBowTriggerOnShutdown(CallbackInfo ci) {
        AdaptiveBowTriggers.getInstance().shutdown();
    }
}
