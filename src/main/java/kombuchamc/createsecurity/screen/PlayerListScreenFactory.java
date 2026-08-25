package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.items.PlayerListItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;

public class PlayerListScreenFactory {
    public static void open(Hand hand, ItemStack stack) {
        MinecraftClient.getInstance().setScreen(new PlayerListScreen(hand,
                PlayerListItem.getNames(stack), PlayerListItem.isIncludeMode(stack)));
    }
}

