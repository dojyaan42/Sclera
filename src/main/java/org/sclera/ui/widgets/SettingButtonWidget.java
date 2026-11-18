package org.sclera.ui.widgets;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import java.util.function.Supplier;

/**
 * Clean Minecraft setting button displaying a left-aligned label and right-aligned status badge.
 */
public class SettingButtonWidget extends ButtonWidget {
    private final Supplier<String> titleSupplier;
    private final Supplier<String> valueSupplier;
    private final Supplier<Boolean> activeSupplier;
    private boolean centeredTitle;

    public SettingButtonWidget(int x, int y, int width, int height, String title, Supplier<String> valueSupplier, PressAction onPress, Tooltip tooltip) {
        this(x, y, width, height, () -> title, valueSupplier, null, onPress, tooltip);
    }

    public SettingButtonWidget(int x, int y, int width, int height, Supplier<String> titleSupplier, Supplier<String> valueSupplier, PressAction onPress, Tooltip tooltip) {
        this(x, y, width, height, titleSupplier, valueSupplier, null, onPress, tooltip);
    }

    public SettingButtonWidget(int x, int y, int width, int height, String title, Supplier<String> valueSupplier, Supplier<Boolean> activeSupplier, PressAction onPress, Tooltip tooltip) {
        this(x, y, width, height, () -> title, valueSupplier, activeSupplier, onPress, tooltip);
    }

    public SettingButtonWidget(int x, int y, int width, int height, Supplier<String> titleSupplier, Supplier<String> valueSupplier, Supplier<Boolean> activeSupplier, PressAction onPress, Tooltip tooltip) {
        super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.titleSupplier = titleSupplier;
        this.valueSupplier = valueSupplier;
        this.activeSupplier = activeSupplier;
        if (tooltip != null) {
            this.setTooltip(tooltip);
        }
    }

    public SettingButtonWidget centerTitle() {
        centeredTitle = true;
        return this;
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        if (activeSupplier != null) {
            this.active = activeSupplier.get();
        }
        super.renderButton(context, mouseX, mouseY, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        String title = titleSupplier != null ? titleSupplier.get() : "";
        String value = valueSupplier != null ? valueSupplier.get() : "";

        int textColor = this.active ? (this.isHovered() ? 0xFFFFA0 : 0xFFFFFF) : 0x666666;
        int titleX = centeredTitle ? this.getX() + (this.getWidth() - mc.textRenderer.getWidth(title)) / 2 : this.getX() + 8;
        context.drawTextWithShadow(mc.textRenderer, Text.literal(title), titleX, this.getY() + (this.getHeight() - 8) / 2, textColor);

        if (!value.isEmpty()) {
            int valW = mc.textRenderer.getWidth(value);
            int valColor = this.active ? 0xFFFFFF : 0x777777;
            context.drawTextWithShadow(mc.textRenderer, Text.literal(value), this.getX() + this.getWidth() - valW - 8, this.getY() + (this.getHeight() - 8) / 2, valColor);
        }
    }
}
