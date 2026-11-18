package org.sclera.mixin;

import net.fabricmc.loader.api.FabricLoader;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

import java.util.List;
import java.util.Set;

/**
 * Mixin configuration plugin to dynamically gate mixins based on optional runtime dependencies.
 * Uses FabricLoader.isModLoaded instead of Class.forName to prevent ReEntrantTransformerError.
 */
public class ScleraMixinPlugin implements IMixinConfigPlugin {

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.endsWith("VisorWorldRenderMixin")) {
            String visorVersion = FabricLoader.getInstance().getModContainer("visor").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
            return supportsVisorSceneRenderHooks(visorVersion);
        }
        if (mixinClassName.endsWith("VisorWorldRenderMixin05")) {
            String visorVersion = FabricLoader.getInstance().getModContainer("visor").map(mod -> mod.getMetadata().getVersion().getFriendlyString()).orElse("");
            return usesLegacyVisorSceneRenderHooks(visorVersion);
        }
        if (mixinClassName.endsWith("VROverlayInventoryMixin")
                || mixinClassName.endsWith("BowAimCrosshairMixin")
                || mixinClassName.endsWith("AdaptiveBowTriggerMixin")) {
            return FabricLoader.getInstance().isModLoaded("visor_essentials");
        }
        if (mixinClassName.endsWith("IrisPipelineVrsMixin")
                || mixinClassName.endsWith("IrisCompositeVrsMixin")) {
            return FabricLoader.getInstance().isModLoaded("iris");
        }
        return true;
    }

    static boolean supportsVisorSceneRenderHooks(String version) {
        int[] parts = parseVersionPrefix(version);
        return parts != null && (parts[0] > 0 || (parts[0] == 0 && parts[1] >= 6));
    }

    static boolean usesLegacyVisorSceneRenderHooks(String version) {
        int[] parts = parseVersionPrefix(version);
        return parts != null && parts[0] == 0 && parts[1] == 5;
    }

    private static int[] parseVersionPrefix(String version) {
        if (version == null) return null;
        String[] parts = version.split("[^0-9]+");
        if (parts.length < 2) return null;
        try {
            int major = Integer.parseInt(parts[0]);
            int minor = Integer.parseInt(parts[1]);
            return new int[]{major, minor};
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }
}
