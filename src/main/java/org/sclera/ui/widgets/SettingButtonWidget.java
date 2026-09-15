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
    private final String title;
    private final Supplier<String> valueSupplier;

    public SettingButtonWidget(int x, int y, int width, int height, String title, Supplier<String> valueSupplier, PressAction onPress, Tooltip tooltip) {
        super(x, y, width, height, Text.empty(), onPress, DEFAULT_NARRATION_SUPPLIER);
        this.title = title;
        this.valueSupplier = valueSupplier;
        if (tooltip != null) {
            this.setTooltip(tooltip);
        }
    }

    @Override
    public void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {
        super.renderButton(context, mouseX, mouseY, delta);
        MinecraftClient mc = MinecraftClient.getInstance();
        String value = valueSupplier != null ? valueSupplier.get() : "";

        int textColor = this.active ? (this.isHovered() ? 0xFFFFA0 : 0xFFFFFF) : 0x666666;
        context.drawTextWithShadow(mc.textRenderer, Text.literal(title), this.getX() + 8, this.getY() + (this.getHeight() - 8) / 2, textColor);

        if (!value.isEmpty()) {
            int valW = mc.textRenderer.getWidth(value);
            context.drawTextWithShadow(mc.textRenderer, Text.literal(value), this.getX() + this.getWidth() - valW - 8, this.getY() + (this.getHeight() - 8) / 2, 0xFFFFFF);
        }
    }
}
