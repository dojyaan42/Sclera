package org.sclera;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import org.sclera.addon.ScleraVisorAddon;
import org.sclera.tracking.EyeTrackingManager;
import org.vmstudio.visor.api.VisorAPI;

/**
 * Main Fabric mod initializer for Sclera VR Eye Tracking.
 */
public class ScleraMod implements ModInitializer {
    public static final String MOD_ID = "sclera";

    @Override
    public void onInitialize() {
        if (FabricLoader.getInstance().isModLoaded("visor")) {
            try {
                VisorAPI.registerAddon(new ScleraVisorAddon());
            } catch (Throwable t) {
                t.printStackTrace();
            }
        } else {
            EyeTrackingManager.getInstance().init(9000, 9002);
        }
    }
}