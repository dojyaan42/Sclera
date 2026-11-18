package org.sclera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import org.sclera.network.HitFeedbackChannel;
import org.sclera.network.HitFeedbackPayload;
import org.sclera.feedback.FeedbackActionContext;
import org.sclera.feedback.FeedbackTarget;
import org.sclera.feedback.ProjectileFeedbackOrigin;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(LivingEntity.class)
public abstract class ServerHitFeedbackMixin {
    @WrapMethod(method = "damage")
    private boolean sclera$confirmHit(DamageSource source, float amount, Operation<Boolean> original) {
        FeedbackTarget target = FeedbackTarget.UNKNOWN;
        if (source != null && source.getAttacker() instanceof ServerPlayerEntity attacker) {
            target = source.getSource() instanceof ProjectileFeedbackOrigin projectile
                    ? projectile.sclera$getFeedbackOrigin() : FeedbackActionContext.current(attacker.getUuid());
        }
        boolean accepted = original.call(source, amount);
        // getAttacker also identifies the owner of arrows, tridents, and other projectiles.
        if (accepted && amount > 0 && source.getAttacker() instanceof ServerPlayerEntity attacker
                && (Object) this != attacker) {
            HitFeedbackChannel.send(attacker, HitFeedbackPayload.Event.HIT, target);
        }
        return accepted;
    }
}
