package kombuchamc.createsecurity.items;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.text.Text;
import net.minecraft.util.DyeColor;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.UUID;

public class KeycardItem extends Item {

    private static final String NBT_OWNER_NAME = "OwnerName";
    private static final String NBT_OWNER_UUID = "OwnerUUID";
    private static final String NBT_CARD_ID = "CardId";
    private static final String NBT_DYE = "Dye";

    public static final int UNCOLORED_TINT = 0xFFFFFF;

    public KeycardItem(Settings settings) {
        super(settings);
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world.isClient || !(entity instanceof PlayerEntity player)) return;
        assignTo(stack, world, player);
    }

    private static void assignTo(ItemStack stack, World world, PlayerEntity player) {
        NbtCompound nbt = stack.getNbt();
        boolean hasId = nbt != null && nbt.contains(NBT_CARD_ID);
        boolean hasOwner = nbt != null && nbt.contains(NBT_OWNER_NAME);
        if (hasId && hasOwner) return;

        NbtCompound target = stack.getOrCreateNbt();
        if (!hasId) {
            target.putInt(NBT_CARD_ID, 100000 + world.random.nextInt(900000));
        }
        if (!hasOwner) {
            target.putString(NBT_OWNER_NAME, player.getGameProfile().getName());
            target.putUuid(NBT_OWNER_UUID, player.getUuid());
        }
    }

    public static void setColor(ItemStack stack, DyeColor color) {
        stack.getOrCreateNbt().putString(NBT_DYE, color.getName());
    }

    @Nullable
    public static DyeColor getColor(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_DYE)) return null;
        return DyeColor.byName(nbt.getString(NBT_DYE), null);
    }

    public static int getTint(ItemStack stack) {
        DyeColor color = getColor(stack);
        if (color == null) return UNCOLORED_TINT;
        float[] rgb = color.getColorComponents();
        return ((int) (rgb[0] * 255f) << 16) | ((int) (rgb[1] * 255f) << 8) | (int) (rgb[2] * 255f);
    }

    public static int getCardId(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null ? 0 : nbt.getInt(NBT_CARD_ID);
    }

    @Nullable
    public static String getOwnerName(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null || !nbt.contains(NBT_OWNER_NAME) ? null : nbt.getString(NBT_OWNER_NAME);
    }

    @Nullable
    public static UUID getOwnerUuid(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt == null || !nbt.containsUuid(NBT_OWNER_UUID) ? null : nbt.getUuid(NBT_OWNER_UUID);
    }

    @Override
    public Text getName(ItemStack stack) {
        DyeColor color = getColor(stack);
        if (color == null) return super.getName(stack);
        return Text.translatable("item.create-security.keycard.colored",
                Text.translatable("color.minecraft." + color.getName()));
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip, TooltipContext context) {
        String owner = getOwnerName(stack);
        int id = getCardId(stack);
        if (owner == null && id == 0) {
            tooltip.add(Text.translatable("tooltip.create-security.keycard_unassigned")
                    .formatted(Formatting.DARK_GRAY));
            return;
        }
        if (owner != null) {
            tooltip.add(Text.translatable("tooltip.create-security.keycard_owner", owner)
                    .formatted(Formatting.GRAY));
        }
        if (id != 0) {
            tooltip.add(Text.translatable("tooltip.create-security.keycard_id", String.valueOf(id))
                    .formatted(Formatting.DARK_GRAY));
        }
    }
}
