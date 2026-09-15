package org.sclera.addon;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.screens.ScleraSettingsScreen;
import org.vmstudio.visor.api.common.addon.VisorAddon;

/**
 * Visor VR addon implementation registering Sclera with the Visor platform.
 */
public class ScleraVisorAddon implements VisorAddon {
    @Override
    public void onAddonRegister() {
    }

    @Override
    public void onAddonLoad() {
        EyeTrackingManager.getInstance().init(9000, 9002);
    }

    @Override
    public Screen createAddonSettingsScreen(Screen parent) {
        return new ScleraSettingsScreen(parent);
    }

    @Override
    public String getAddonId() {
        return "sclera";
    }

    @Override
    public Text getAddonName() {
        return Text.literal("Sclera Eye Tracking");
    }

    @Override
    public String getModId() {
        return "sclera";
    }

    @Override
    public String getAddonPackagePath() {
        return "org.sclera";
    }
}