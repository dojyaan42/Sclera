package org.sclera.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerEntity;
import org.sclera.haptics.AdaptiveBowTriggers;
import org.sclera.tracking.EyeTrackingManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.essentials.core.client.tasks.BowItemTask;
import org.vmstudio.visor.api.VisorAPI;
import org.vmstudio.visor.api.client.input.VRInputManager;
import org.vmstudio.visor.api.common.HandType;
import me.phoenixra.atumvr.api.enums.ControllerType;

@Mixin(value = BowItemTask.class, remap = false)
public abstract class AdaptiveBowTriggerMixin {
    // BowItemTask keeps the arrow marked as notched for about 250 ms after it leaves the hitbox.
    @Unique private static final long sclera$NOTCH_TIMEOUT_NANOS = 50_000_000L;
    @Shadow public abstract boolean isActive(ClientPlayerEntity player);
    @Shadow public abstract boolean isNotched();
    @Shadow public abstract HandType getBowHolder();
    @Shadow public abstract float getDrawPercent();

    @Unique private boolean sclera$awaitingNewNotch;
    @Unique private long sclera$lastNotchNanos;

    @Inject(method = "onNotched", at = @At("TAIL"))
    private void sclera$engageTriggerOnNotch(CallbackInfo ci) {
        sclera$lastNotchNanos = System.nanoTime();
        sclera$awaitingNewNotch = false;
        sclera$updateTriggerResistance();
    }

    @Inject(method = "onRun", at = @At("RETURN"))
    private void sclera$updateAdaptiveBowTrigger(ClientPlayerEntity player, CallbackInfo ci) {
        boolean notchAreaActive = isNotched()
                && System.nanoTime() - sclera$lastNotchNanos <= sclera$NOTCH_TIMEOUT_NANOS;
        if (!notchAreaActive) {
            sclera$awaitingNewNotch = false;
            sclera$releaseTrigger();
            return;
        }
        sclera$updateTriggerResistance();
    }

    @Unique
    private void sclera$updateTriggerResistance() {
        MinecraftClient client = MinecraftClient.getInstance();
        boolean enabled = EyeTrackingManager.getInstance().isAdaptiveBowTriggerEnabled();
        boolean active = client != null && client.player != null && isActive(client.player)
                && System.nanoTime() - sclera$lastNotchNanos <= sclera$NOTCH_TIMEOUT_NANOS;
        if (!AdaptiveBowTriggers.shouldApplyNotchResistance(
                enabled, active, isNotched(), sclera$awaitingNewNotch)) {
            sclera$releaseTrigger();
            return;
        }

        if (VisorAPI.client() == null) {
            sclera$releaseTrigger();
            return;
        }
        VRInputManager input = VisorAPI.client().getInputManager();
        HandType pullingHand = getBowHolder() == null ? null : getBowHolder().opposite();
        if (input == null || pullingHand == null) {
            sclera$releaseTrigger();
            return;
        }

        ControllerType controller = pullingHand.asControllerType(input.isLeftHanded());
        byte toolkitController = controller == ControllerType.LEFT ? (byte) 0 : (byte) 1;
        float drawPercent = getDrawPercent();
        int stage = drawPercent < 0.65f ? 0 : drawPercent < 0.90f ? 1 : 2;
        AdaptiveBowTriggers.getInstance().engageNotch(toolkitController, stage);
    }

    @Inject(method = "onClear", at = @At("TAIL"))
    private void sclera$releaseAdaptiveBowTrigger(ClientPlayerEntity player, CallbackInfo ci) {
        sclera$awaitingNewNotch = false;
        sclera$lastNotchNanos = 0L;
        sclera$releaseTrigger();
    }

    @Inject(method = "shoot", at = @At("TAIL"))
    private void sclera$releaseAfterShot(PlayerEntity player, CallbackInfo ci) {
        sclera$awaitingNewNotch = true;
        sclera$releaseTrigger();
    }

    @Inject(method = "cancelDraw", at = @At("TAIL"))
    private void sclera$releaseAfterDrawCancelled(CallbackInfo ci) {
        sclera$releaseTrigger();
    }

    @Unique
    private void sclera$releaseTrigger() {
        AdaptiveBowTriggers.getInstance().release();
    }
}
