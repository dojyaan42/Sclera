package org.sclera.addon;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.sclera.haptics.AdaptiveBowTriggers;
import org.sclera.config.ScleraClientConfig;
import org.sclera.network.ScleraGazeChannel;
import org.sclera.network.HitFeedbackChannel;
import org.sclera.tracking.EyeTrackingManager;
import org.sclera.ui.screens.ScleraSettingsScreen;
import org.vmstudio.visor.api.common.addon.VisorAddon;

/**
 * Visor VR addon implementation registering Sclera with the Visor platform.
 */
public class ScleraVisorAddon implements VisorAddon {
    @Override
    public void onAddonRegister() {
        ScleraGazeChannel.register(this);
        HitFeedbackChannel.register(this);
    }

    @Override
    public void onAddonLoad() {
        if (org.vmstudio.visor.api.ModLoader.get().isDedicatedServer()) return;
        EyeTrackingManager manager = EyeTrackingManager.getInstance();
        manager.attachPreferences(ScleraClientConfig.getInstance());
        manager.init(9000, 9002);
        org.sclera.render.FoveationConfig.getInstance();
        AdaptiveBowTriggers toolkit = AdaptiveBowTriggers.getInstance();
        toolkit.initialize();
        toolkit.checkAvailability(availability -> {
            boolean usePsvr2 = availability.bowTriggers();
            manager.setEyeTrackingSource(usePsvr2
                    ? EyeTrackingManager.EyeTrackingSource.PSVR2
                    : EyeTrackingManager.EyeTrackingSource.VRCFT);
        });
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
