package kombuchamc.createsecurity.items;

import net.minecraft.client.item.TooltipContext;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class UniversalKeycardItem extends Item {

    private static final String ID_PLACEHOLDER = "000000";

    public UniversalKeycardItem(Settings settings) {
        super(settings);
    }

    @Override
    public boolean hasGlint(ItemStack stack) {
        return true;
    }

    @Override
    public void inventoryTick(ItemStack stack, World world, Entity entity, int slot, boolean selected) {
        super.inventoryTick(stack, world, entity, slot, selected);
        if (world.isClient || !(entity instanceof PlayerEntity player)) return;
        KeycardItem.assignOwner(stack, player);
    }

    @Override
    public void appendTooltip(ItemStack stack, @Nullable World world, List<Text> tooltip,
                              TooltipContext context) {
        String owner = KeycardItem.getOwnerName(stack);
        if (owner == null) {
            tooltip.add(Text.translatable("tooltip.create-security.keycard_unassigned")
                    .formatted(Formatting.GRAY));
            return;
        }
        tooltip.add(Text.translatable("tooltip.create-security.keycard_owner", owner)
                .formatted(Formatting.GRAY));
        tooltip.add(Text.translatable("tooltip.create-security.keycard_id",
                        Text.literal(ID_PLACEHOLDER).formatted(Formatting.OBFUSCATED))
                .formatted(Formatting.GRAY));
    }
}
