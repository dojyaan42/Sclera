package org.sclera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.sclera.feedback.*;
import org.sclera.network.HitFeedbackChannel;
import org.sclera.network.HitFeedbackPayload;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.vmstudio.visor.api.common.HandType;

@Mixin(ServerPlayerInteractionManager.class)
public abstract class ServerBlockFeedbackMixin implements MiningFeedbackAccess {
    @Shadow @Final protected ServerPlayerEntity player;
    @Shadow protected ServerWorld world;
    @Unique private final MiningFeedbackState sclera$miningHands = new MiningFeedbackState();
    @Unique private Long sclera$normalMiningPos;

    @Override public void sclera$recordMiningHand(long position, FeedbackTarget target) {
        sclera$miningHands.remember(position, target);
    }

    @Inject(method = "processBlockBreakingAction", at = @At("HEAD"))
    private void sclera$rememberMiningHand(BlockPos pos, PlayerActionC2SPacket.Action action, Direction direction,
                                          int worldHeight, int sequence, CallbackInfo ci) {
        long position = pos.asLong();
        if (action == PlayerActionC2SPacket.Action.START_DESTROY_BLOCK) {
            if (sclera$normalMiningPos != null && sclera$normalMiningPos != position) {
                sclera$miningHands.forget(sclera$normalMiningPos);
            }
            sclera$normalMiningPos = position;
            sclera$miningHands.remember(position, FeedbackActions.activeHand(player));
        } else if (action == PlayerActionC2SPacket.Action.ABORT_DESTROY_BLOCK) {
            sclera$miningHands.forget(position);
            if (sclera$normalMiningPos != null && sclera$normalMiningPos == position) sclera$normalMiningPos = null;
        }
    }

    @WrapMethod(method = "tryBreakBlock")
    private boolean sclera$confirmBlockBreak(BlockPos pos, Operation<Boolean> original) {
        FeedbackTarget remembered = sclera$miningHands.take(pos.asLong());
        FeedbackTarget target = FeedbackActionContext.current(player.getUuid());
        if (target == FeedbackTarget.UNKNOWN) target = remembered;
        var before = world.getBlockState(pos);
        boolean broken = original.call(pos);
        if (broken && !before.isAir() && !world.getBlockState(pos).isOf(before.getBlock())) {
            HitFeedbackChannel.send(player, HitFeedbackPayload.Event.BLOCK_BREAK, target);
        }
        return broken;
    }

    @WrapMethod(method = "interactItem")
    private ActionResult sclera$scopeUsedItem(ServerPlayerEntity actor, World world, ItemStack stack, Hand hand,
                                              Operation<ActionResult> original) {
        return FeedbackActionContext.call(actor.getUuid(), FeedbackActions.hand(actor, HandType.fromMc(hand)),
                () -> original.call(actor, world, stack, hand));
    }

    @Inject(method = "setWorld", at = @At("TAIL"))
    private void sclera$clearMiningHands(ServerWorld world, CallbackInfo ci) {
        sclera$miningHands.clear();
        sclera$normalMiningPos = null;
    }
}
