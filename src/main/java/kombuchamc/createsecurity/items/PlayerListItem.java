package kombuchamc.createsecurity.items;

import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.nbt.NbtString;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TextColor;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.TypedActionResult;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class PlayerListItem extends Item {

    public static final int MAX_PLAYERS = 12;

    private static final String NBT_PLAYERS = "Players";
    private static final String NBT_INCLUDE = "Include";

    private static final Style BODY_COLOR      = Style.EMPTY.withColor(TextColor.fromRgb(13211468));
    private static final Style HIGHLIGHT_COLOR = Style.EMPTY.withColor(TextColor.fromRgb(15850873));

    public PlayerListItem(Settings settings) {
        super(settings);
    }

    @Override
    public TypedActionResult<ItemStack> use(World world, PlayerEntity user, Hand hand) {
        ItemStack stack = user.getStackInHand(hand);
        if (world.isClient) {
            kombuchamc.createsecurity.screen.PlayerListScreenFactory.open(hand, stack);
        }
        return TypedActionResult.success(stack);
    }

    public static List<String> getNames(ItemStack stack) {
        List<String> names = new ArrayList<>();
        NbtCompound nbt = stack.getNbt();
        if (nbt != null) {
            NbtList list = nbt.getList(NBT_PLAYERS, NbtElement.STRING_TYPE);
            for (int i = 0; i < list.size(); i++) names.add(list.getString(i));
        }
        return names;
    }

    public static boolean isIncludeMode(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null || !nbt.contains(NBT_INCLUDE) || nbt.getBoolean(NBT_INCLUDE);
    }

    public static void writeData(ItemStack stack, boolean includeMode, List<String> names) {
        NbtCompound nbt = stack.getOrCreateNbt();
        nbt.putBoolean(NBT_INCLUDE, includeMode);
        NbtList list = new NbtList();
        for (String name : names) list.add(NbtString.of(name));
        nbt.put(NBT_PLAYERS, list);
    }

    public static boolean matches(ItemStack stack, String playerName) {
        boolean listed = false;
        for (String name : getNames(stack)) {
            if (name.equalsIgnoreCase(playerName)) {
                listed = true;
                break;
            }
        }
        return isIncludeMode(stack) == listed;
    }

    public static List<String> sanitizeNames(List<String> raw) {
        List<String> clean = new ArrayList<>();
        for (String name : raw) {
            String trimmed = name.trim();
            if (!trimmed.matches("[A-Za-z0-9_]{1,16}")) continue;
            boolean duplicate = false;
            for (String existing : clean) {
                if (existing.equalsIgnoreCase(trimmed)) {
                    duplicate = true;
                    break;
                }
            }
            if (duplicate) continue;
            clean.add(trimmed);
            if (clean.size() >= MAX_PLAYERS) break;
        }
        return clean;
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        tooltip.add(Text.translatable(isIncludeMode(stack)
                ? "tooltip.create-security.player_list_mode_include"
                : "tooltip.create-security.player_list_mode_exclude").formatted(Formatting.GRAY));
        for (String name : getNames(stack)) {
            tooltip.add(Text.literal("• " + name).formatted(Formatting.DARK_GRAY));
        }
        if (Screen.hasShiftDown()) {
            tooltip.add(Text.translatable("tooltip.create-security.holding_shift"));
            tooltip.add(Text.literal(""));
            addParsedTooltip(tooltip, Text.translatable("tooltip.create-security.player_list").getString());
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

