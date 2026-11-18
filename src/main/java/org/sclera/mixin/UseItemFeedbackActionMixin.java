package org.sclera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.LivingEntity;
import net.minecraft.item.BowItem;
import net.minecraft.server.network.ServerPlayerEntity;
import org.sclera.feedback.FeedbackActionContext;
import org.sclera.feedback.FeedbackActions;
import org.sclera.feedback.FeedbackTarget;
import org.spongepowered.asm.mixin.Mixin;
import org.vmstudio.visor.api.common.HandType;

@Mixin(LivingEntity.class)
public abstract class UseItemFeedbackActionMixin {
    @WrapMethod(method = "stopUsingItem")
    private void sclera$scopeReleasedWeapon(Operation<Void> original) {
        if ((Object) this instanceof ServerPlayerEntity player) {
            FeedbackTarget target = player.getActiveItem().getItem() instanceof BowItem ? FeedbackTarget.BOTH
                    : FeedbackActions.hand(player, HandType.fromMc(player.getActiveHand()));
            FeedbackActionContext.run(player.getUuid(), target, () -> original.call());
        } else {
            original.call();
        }
    }
}
