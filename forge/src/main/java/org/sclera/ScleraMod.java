package org.sclera;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.DistExecutor;
import org.sclera.addon.ScleraVisorAddon;
import org.sclera.forge.ForgePlatform;
import org.vmstudio.visor.api.VisorAPI;

/** Forge entry point; addon registration and initialization match Fabric. */
@Mod(ScleraMod.MOD_ID)
public class ScleraMod {
    public static final String MOD_ID = "sclera";

    public ScleraMod() {
        if (ForgePlatform.getInstance().isModLoaded("visor")) {
            try {
                VisorAPI.registerAddon(new ScleraVisorAddon());
            } catch (Throwable ignored) {
            }
        }
        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ScleraClient::initialize);
    }
}
