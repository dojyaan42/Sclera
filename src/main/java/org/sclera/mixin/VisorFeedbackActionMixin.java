package org.sclera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.server.network.ServerPlayerEntity;
import org.sclera.feedback.*;
import org.spongepowered.asm.mixin.Mixin;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.network.VisorPayloadToClient;
import org.vmstudio.visor.api.common.network.VisorPayloadToServer;
import org.vmstudio.visor.api.common.network.toserver.SwingAttackPayloadToServer;
import org.vmstudio.visor.api.common.network.toserver.SwingBlockPayloadToServer;
import org.vmstudio.visor.core.server.network.ServerPacketHandler;

import java.util.function.Consumer;

/** Physical swings carry their own hand; the player's general active hand may be different. */
@Mixin(value = ServerPacketHandler.class, remap = false)
public abstract class VisorFeedbackActionMixin {
    @WrapMethod(method = "handlePacket", remap = false)
    private static void sclera$scopeSwingHand(VisorPayloadToServer payload, ServerPlayerEntity player,
                                              Consumer<VisorPayloadToClient> response, Operation<Void> original) {
        HandType hand;
        if (payload instanceof SwingAttackPayloadToServer attack) {
            hand = attack.mainHand() ? HandType.MAIN : HandType.OFFHAND;
        } else if (payload instanceof SwingBlockPayloadToServer block) {
            hand = block.mainHand() ? HandType.MAIN : HandType.OFFHAND;
            ((MiningFeedbackAccess) player.interactionManager).sclera$recordMiningHand(
                    block.blockPos().asLong(), FeedbackActions.hand(player, hand));
        } else {
            original.call(payload, player, response);
            return;
        }
        FeedbackTarget target = FeedbackActions.hand(player, hand);
        FeedbackActionContext.run(player.getUuid(), target, () -> original.call(payload, player, response));
    }
}
