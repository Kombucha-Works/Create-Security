package kombuchamc.createsecurity.items;


import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.MutableText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import net.minecraft.text.Style;
import net.minecraft.text.TextColor;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TooltipItem extends Item {

    private final String tooltipKey;

    private static final Style BODY_COLOR      = Style.EMPTY.withColor(TextColor.fromRgb(13211468));
    private static final Style HIGHLIGHT_COLOR = Style.EMPTY.withColor(TextColor.fromRgb(15850873));

    public TooltipItem(Settings settings, String tooltipKey) {
        super(settings);
        this.tooltipKey = tooltipKey;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        if (Screen.hasShiftDown()) {
            tooltip.add(Text.translatable("tooltip.create-security.holding_shift"));
            tooltip.add(Text.literal(""));
            addParsedTooltip(tooltip, Text.translatable(tooltipKey).getString());
        } else {
            tooltip.add(Text.translatable("tooltip.create-security.hold_shift"));
        }
    }

    private static void addParsedTooltip(List<Text> tooltip, String raw) {
        for (String line : raw.split("/n", -1))
            tooltip.add(parseHighlighted(line));
    }

    private static MutableText parseHighlighted(String raw) {
        MutableText result = Text.literal("");
        String[] parts = raw.split("_", -1);
        for (int i = 0; i < parts.length; i++) {
            if (parts[i].isEmpty()) continue;
            Style style = (i % 2 == 1) ? HIGHLIGHT_COLOR : BODY_COLOR;
            result.append(Text.literal(parts[i]).setStyle(style));
        }
        return result;
    }
}
