package org.sclera.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3fc;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.GazeCursorHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.vmstudio.visor.api.client.player.pose.PlayerPoseType;
import org.vmstudio.visor.api.common.HandType;
import org.vmstudio.visor.api.common.player.VRPose;
import org.vmstudio.visor.compatibility.immportals.ImmPortalsCompatHelper;
import org.vmstudio.visor.core.client.ClientContext;
import org.vmstudio.visor.core.client.VisorState;
import org.vmstudio.visor.core.client.player.VRLocalPlayerImpl;
import org.vmstudio.visor.core.client.player.pose.LocalPlayerPose;

/**
 * Injects eye tracking targeting into GameRenderer raycasting.
 * Overrides crosshair target and hand pick results when gaze targeting is active.
 */
@Mixin(value = GameRenderer.class, priority = 1200)
public abstract class GameRendererTargetingMixin {

    private static java.lang.reflect.Field sclera$aimHitPosField;
    private static java.lang.reflect.Field sclera$handHitResultField;
    private static java.lang.reflect.Field sclera$handAimHitPosField;
    private static java.lang.reflect.Field sclera$handPickEntityField;

    private static HitResult sclera$cachedGazeHit;
    private static Vec3d sclera$cachedGazeAimPos;
    private static Entity sclera$cachedGazeEntity;
    private static long sclera$lastGazeComputeTime = 0;

    private void sclera$setAimHitPos(Vec3d pos) {
        try {
            if (sclera$aimHitPosField == null) {
                sclera$aimHitPosField = GameRenderer.class.getDeclaredField("visor$aimHitPos");
                sclera$aimHitPosField.setAccessible(true);
            }
            sclera$aimHitPosField.set(this, pos);
        } catch (Throwable ignored) {}
    }

    private void sclera$setHandPick(HandType handType, HitResult hit, Vec3d aimPos, Entity entity) {
        if (handType == null) return;
        int idx = handType.ordinal();

        try {
            if (sclera$handHitResultField == null) {
                sclera$handHitResultField = GameRenderer.class.getDeclaredField("visor$handHitResult");
                sclera$handHitResultField.setAccessible(true);
            }
            HitResult[] hits = (HitResult[]) sclera$handHitResultField.get(this);
            if (hits != null && idx < hits.length) {
                hits[idx] = hit;
            }
        } catch (Throwable ignored) {}

        try {
            if (sclera$handAimHitPosField == null) {
                sclera$handAimHitPosField = GameRenderer.class.getDeclaredField("visor$handAimHitPos");
                sclera$handAimHitPosField.setAccessible(true);
            }
            Vec3d[] aims = (Vec3d[]) sclera$handAimHitPosField.get(this);
            if (aims != null && idx < aims.length) {
                aims[idx] = aimPos;
            }
        } catch (Throwable ignored) {}

        try {
            if (sclera$handPickEntityField == null) {
                sclera$handPickEntityField = GameRenderer.class.getDeclaredField("visor$handPickEntity");
                sclera$handPickEntityField.setAccessible(true);
            }
            Entity[] entities = (Entity[]) sclera$handPickEntityField.get(this);
            if (entities != null && idx < entities.length) {
                entities[idx] = entity;
            }
        } catch (Throwable ignored) {}
    }

    private void sclera$computeGazeTarget(MinecraftClient mc, LocalPlayerPose renderPose) {
        long now = System.currentTimeMillis();

        VRPose gazePose = GazeCursorHandler.getInstance().getGazePose(renderPose);
        if (gazePose == null) return;

        // Raycast against blocks using Visor helper (supports Immersive Portals)
        double reach = mc.interactionManager != null ? mc.interactionManager.getReachDistance() : 4.5;
        HitResult blockHit = ImmPortalsCompatHelper.pickBlock(mc.world, gazePose, reach, false, mc.player);

        Vector3fc pos = gazePose.getPosition();
        Vector3fc dir = gazePose.getDirection();
        Vec3d minPos = new Vec3d(pos.x(), pos.y(), pos.z());
        Vec3d maxPos = new Vec3d(
            pos.x() + dir.x() * reach,
            pos.y() + dir.y() * reach,
            pos.z() + dir.z() * reach
        );

        HitResult finalHit = blockHit;
        Entity hitEntity = null;
        double blockDistSq = reach * reach;
        if (blockHit != null && blockHit.getType() != HitResult.Type.MISS) {
            blockDistSq = minPos.squaredDistanceTo(blockHit.getPos());
        }

        // Raycast against entities within reach
        try {
            Box box = mc.player.getBoundingBox().stretch(new Vec3d(dir.x() * reach, dir.y() * reach, dir.z() * reach)).expand(1.0);
            EntityHitResult entityHit = ProjectileUtil.raycast(mc.player, minPos, maxPos, box, e -> !e.isSpectator() && e.canHit(), blockDistSq);
            if (entityHit != null) {
                finalHit = entityHit;
                hitEntity = entityHit.getEntity();
            }
        } catch (Throwable ignored) {}

        Vec3d aimHitPos = (finalHit != null && finalHit.getType() != HitResult.Type.MISS) ? finalHit.getPos() : maxPos;

        sclera$cachedGazeHit = finalHit;
        sclera$cachedGazeAimPos = aimHitPos;
        sclera$cachedGazeEntity = hitEntity;
        sclera$lastGazeComputeTime = now;
    }

    @Inject(method = "updateTargetedEntity", at = @At("TAIL"))
    private void sclera$onUpdateTargetedEntity(float tickDelta, CallbackInfo ci) {
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        VRLocalPlayerImpl localPlayer = ClientContext.localPlayer;
        if (localPlayer == null || VisorState.get().isNotActive()) return;

        GazeCursorHandler.getInstance().updateBlinkWorld();

        LocalPlayerPose renderPose = localPlayer.getPoseData(PlayerPoseType.RENDER);
        if (renderPose == null) return;

        HandType activeHand = localPlayer.getActiveHand();
        if (activeHand == null) activeHand = HandType.MAIN;

        if (!manager.shouldUseGazeInWorld(renderPose, activeHand)) {
            return;
        }

        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc == null || mc.world == null || mc.player == null) return;

        sclera$computeGazeTarget(mc, renderPose);
        if (sclera$cachedGazeHit == null) return;

        mc.crosshairTarget = sclera$cachedGazeHit;
        if (sclera$cachedGazeEntity != null) {
            mc.targetedEntity = sclera$cachedGazeEntity;
        }

        sclera$setAimHitPos(sclera$cachedGazeAimPos);

        // Active hand represents gaze target
        sclera$setHandPick(activeHand, sclera$cachedGazeHit, sclera$cachedGazeAimPos, sclera$cachedGazeEntity);
    }
}
