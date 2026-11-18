package org.sclera;

import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.fml.ModLoadingContext;
import org.sclera.ui.screens.ScleraSettingsScreen;

public final class ScleraClient {
    private ScleraClient() {}

    public static void initialize() {
        org.sclera.config.ScleraSettings.initialize();
        ModLoadingContext.get().registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (client, parent) -> new ScleraSettingsScreen(parent)));
    }
}
