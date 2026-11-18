package org.sclera.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import org.sclera.config.ScleraClientConfig;
import org.sclera.haptics.AdaptiveBowTriggers;
import org.sclera.haptics.DamageVibration;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.api.VisorAPI;

@Mixin(ClientPlayNetworkHandler.class)
public abstract class ClientDamageVibrationMixin {
    @Shadow @Final private MinecraftClient client;

    // TAIL runs after vanilla moves packet handling onto the client thread.
    // Unlike a health comparison, this also handles damage absorbed by golden hearts.
    @Inject(method = "onEntityDamage", at = @At("TAIL"))
    private void sclera$vibrateOnDamage(EntityDamageS2CPacket packet, CallbackInfo ci) {
        if (client.player == null || VisorAPI.client() == null) return;
        DamageVibration.onDamage(packet.entityId(), client.player.getId(),
                ScleraClientConfig.getInstance().isDamageVibrationEnabled(),
                GazeCursorHandler.isVRActive(), AdaptiveBowTriggers.getInstance().isAvailable(),
                VisorAPI.client().getInputManager());
    }
}
