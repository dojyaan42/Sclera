package org.sclera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import org.sclera.feedback.FeedbackActionContext;
import org.sclera.feedback.FeedbackActions;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(PlayerEntity.class)
public abstract class PlayerFeedbackActionMixin {
    @WrapMethod(method = "attack")
    private void sclera$scopeAttackHand(Entity target, Operation<Void> original) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            FeedbackActionContext.run(player.getUuid(), FeedbackActions.activeHand(player), () -> original.call(target));
        } else {
            original.call(target);
        }
    }
}
