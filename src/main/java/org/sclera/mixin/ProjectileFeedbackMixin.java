package org.sclera.mixin;

import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.item.BowItem;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.network.ServerPlayerEntity;
import org.sclera.feedback.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.api.common.HandType;

@Mixin(ProjectileEntity.class)
public abstract class ProjectileFeedbackMixin implements ProjectileFeedbackOrigin {
    @Unique private FeedbackTarget sclera$feedbackOrigin = FeedbackTarget.UNKNOWN;

    @Inject(method = "setOwner", at = @At("TAIL"))
    private void sclera$rememberLaunchHand(Entity owner, CallbackInfo ci) {
        if (owner instanceof ServerPlayerEntity player) {
            sclera$feedbackOrigin = FeedbackActionContext.current(player.getUuid());
            if (sclera$feedbackOrigin == FeedbackTarget.UNKNOWN && player.isUsingItem()) {
                sclera$feedbackOrigin = player.getActiveItem().getItem() instanceof BowItem
                        ? FeedbackTarget.BOTH : FeedbackActions.hand(player, HandType.fromMc(player.getActiveHand()));
            }
        } else {
            sclera$feedbackOrigin = FeedbackTarget.UNKNOWN;
        }
    }

    @Override public FeedbackTarget sclera$getFeedbackOrigin() { return sclera$feedbackOrigin; }

    @Inject(method = "writeCustomDataToNbt", at = @At("TAIL"))
    private void sclera$saveLaunchHand(NbtCompound nbt, CallbackInfo ci) {
        if (sclera$feedbackOrigin != FeedbackTarget.UNKNOWN) {
            nbt.putByte("ScleraFeedbackTarget", (byte) sclera$feedbackOrigin.code());
        }
    }

    @Inject(method = "readCustomDataFromNbt", at = @At("TAIL"))
    private void sclera$loadLaunchHand(NbtCompound nbt, CallbackInfo ci) {
        sclera$feedbackOrigin = FeedbackTarget.fromCode(nbt.getByte("ScleraFeedbackTarget"));
    }
}
