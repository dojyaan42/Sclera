package org.sclera.mixin;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.mob.EndermanEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;
import net.minecraft.item.Items;
import org.joml.Vector3f;
import org.sclera.tracking.EndermanGaze;
import org.sclera.tracking.ServerGazeRegistry;
import org.spongepowered.asm.mixin.Mixin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.atomic.AtomicBoolean;

// Wrap the complete method so Visor's cancellable HMD injector cannot bypass eye
// samples. Without a fresh sample, invoke the original Visor/vanilla method once.
@Mixin(EndermanEntity.class)
public abstract class EndermanGazeMixin {
    private static final Logger SCLERA_LOGGER = LoggerFactory.getLogger("Sclera/EndermanGaze");
    private static final AtomicBoolean LOGGED_SERVER_SAMPLE = new AtomicBoolean();
    private static final AtomicBoolean LOGGED_FIRST_STARE_RESULT = new AtomicBoolean();

    @WrapMethod(method = "isPlayerStaring")
    private boolean sclera$checkEyeGaze(PlayerEntity entity, Operation<Boolean> original) {
        if (!(entity instanceof ServerPlayerEntity player)) return original.call(entity);
        ServerGazeRegistry.Sample sample = ServerGazeRegistry.getFresh(player.getUuid(), System.nanoTime());
        if (sample == null) return original.call(entity);
        if (LOGGED_SERVER_SAMPLE.compareAndSet(false, true)) {
            SCLERA_LOGGER.info("Applying a fresh eye-gaze sample to Enderman stare detection");
        }

        EndermanEntity enderman = (EndermanEntity) (Object) this;
        if (player.getEquippedStack(EquipmentSlot.HEAD).isOf(Items.CARVED_PUMPKIN)
                || player.getWorld() != enderman.getWorld()) {
            return false;
        }

        Vec3d playerEye = player.getEyePos();
        Vec3d endermanEye = enderman.getEyePos();
        boolean looking = EndermanGaze.isLookingAt(
                new Vector3f((float) playerEye.x, (float) playerEye.y, (float) playerEye.z),
                sample.direction(),
                new Vector3f((float) endermanEye.x, (float) endermanEye.y, (float) endermanEye.z));
        if (LOGGED_FIRST_STARE_RESULT.compareAndSet(false, true)) {
            SCLERA_LOGGER.info("First eye-gaze Enderman cone check: {}", looking ? "matched" : "missed");
        }
        if (!looking) {
            return false;
        }

        HitResult visibility = player.getWorld().raycast(new RaycastContext(
                playerEye, endermanEye, RaycastContext.ShapeType.COLLIDER,
                RaycastContext.FluidHandling.NONE, player));
        return visibility.getType() == HitResult.Type.MISS;
    }
}
