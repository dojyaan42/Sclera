package org.sclera;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.sclera.addon.ScleraVisorAddon;
import org.vmstudio.visor.api.VisorAPI;

/**
 * Main Fabric mod initializer for Sclera VR Eye Tracking.
 */
public class ScleraMod implements ModInitializer {
    public static final String MOD_ID = "sclera";

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().getEnvironmentType() == net.fabricmc.api.EnvType.CLIENT) {
            org.sclera.config.ScleraSettings.initialize();
        }
        if (FabricLoader.getInstance().isModLoaded("visor")) {
            try {
                VisorAPI.registerAddon(new ScleraVisorAddon());
            } catch (Throwable ignored) {
            }
        }
    }
}
