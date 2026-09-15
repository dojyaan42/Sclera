package org.sclera.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.util.math.Vec3d;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Intercepts projectile velocity to aim arrows directly at the gaze target.
 */
@Mixin(value = ProjectileEntity.class, priority = 1200)
public abstract class GazeProjectileMixin {

    @Inject(
        method = "setVelocity(Lnet/minecraft/entity/Entity;FFFFF)V",
        at = @At("TAIL")
    )
    private void sclera$redirectProjectileToGaze(
        Entity shooter,
        float pitch,
        float yaw,
        float roll,
        float speed,
        float divergence,
        CallbackInfo ci
    ) {
        try {
            if (shooter == null) return;

            MinecraftClient mc = MinecraftClient.getInstance();
            if (mc == null || mc.player == null) return;

            // Only redirect projectiles launched by the local VR player
            if (!shooter.getUuid().equals(mc.player.getUuid())) return;
            if (!GazeCursorHandler.isVRActive()) return;

            EyeTrackingManager manager = EyeTrackingManager.getInstance();
            if (manager == null || !manager.isTrackingActive() || !manager.isBowGazeAimEnabled()) return;

            // If gaze is disabled in the 3D world, preserve 100% native Visor hand aim
            if (!manager.isGazeInWorld()) {
                return;
            }

            ProjectileEntity projectile = (ProjectileEntity) (Object) this;

            // Only redirect arrows / persistent projectiles (fixes VisorEssentials Better Bow upward arcing)
            if (!(projectile instanceof PersistentProjectileEntity)) {
                return;
            }

            GazeCursorHandler cursorHandler = GazeCursorHandler.getInstance();
            Vec3d targetPoint = cursorHandler.computeGazeAimPoint(projectile.getWorld(), shooter, 128.0);
            if (targetPoint == null) return;

            Vec3d handPos = cursorHandler.getLatestHandPos();
            Vec3d launchPos = handPos != null ? handPos : projectile.getPos();

            // Preserve current projectile speed / tension calculated by VisorEssentials / vanilla
            double currentSpeed = projectile.getVelocity().length();
            if (currentSpeed < 0.1) {
                currentSpeed = speed > 0.1f ? speed : 3.0;
            }

            // Ballistic arc compensation: Minecraft arrows drop 0.05 blocks/tick^2 with 0.99 drag.
            // Elevate the aim vector so the gravitational arc lands dead center on the gaze target.
            double dx = targetPoint.x - launchPos.x;
            double dz = targetPoint.z - launchPos.z;
            double horizDist = Math.sqrt(dx * dx + dz * dz);
            double clampedDist = Math.min(horizDist, 64.0);
            double speedFactor = Math.max(currentSpeed, 1.0) * 0.95;
            double timeEst = clampedDist / speedFactor;
            double gravityDrop = 0.025 * (timeEst * timeEst);

            Vec3d compensatedTarget = new Vec3d(targetPoint.x, targetPoint.y + gravityDrop, targetPoint.z);
            Vec3d aimVec = compensatedTarget.subtract(launchPos);
            if (aimVec.lengthSquared() > 0.0001) {
                aimVec = aimVec.normalize();
            } else {
                Vec3d gazeDir = cursorHandler.getLatestGazeDir();
                if (gazeDir != null) {
                    aimVec = gazeDir;
                } else {
                    return;
                }
            }

            // Align physical arrow spawn position with the bow hand along the shot vector
            if (handPos != null) {
                Vec3d spawnPos = handPos.add(aimVec.multiply(0.35));
                projectile.setPosition(spawnPos.x, spawnPos.y, spawnPos.z);
            }

            // Re-aim the projectile using Minecraft's native projectile physics
            projectile.setVelocity(aimVec.x, aimVec.y, aimVec.z, (float) currentSpeed, divergence);

        } catch (Throwable ignored) {}
    }
}
