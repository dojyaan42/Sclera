package org.sclera.forge;

import java.nio.file.Path;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.fml.loading.FMLPaths;

/** Loader access also available during early Mixin configuration. */
public final class ForgePlatform {
    private static final ForgePlatform INSTANCE = new ForgePlatform();

    public static ForgePlatform getInstance() {
        return INSTANCE;
    }

    public boolean isModLoaded(String id) {
        // Oculus is the Forge counterpart of the optional Iris integration.
        String forgeId = id.equals("iris") ? "oculus" : id;
        return FMLLoader.getLoadingModList().getMods().stream()
                .anyMatch(mod -> mod.getModId().equals(forgeId));
    }

    public String getModVersion(String id) {
        return net.minecraftforge.fml.ModList.get().getModContainerById(id)
                .map(container -> container.getModInfo().getVersion().toString())
                .orElse("");
    }

    public Path getConfigDir() {
        return FMLPaths.CONFIGDIR.get();
    }
}
