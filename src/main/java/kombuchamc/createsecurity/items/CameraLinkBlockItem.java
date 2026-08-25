package kombuchamc.createsecurity.items;

import kombuchamc.createsecurity.block.CameraLinkBlock;
import net.minecraft.block.Block;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;

public class CameraLinkBlockItem extends BlockItem {

    public CameraLinkBlockItem(Block block, Settings settings) {
        super(block, settings);
    }

    @Override
    public ActionResult place(ItemPlacementContext context) {
        ActionResult result = super.place(context);
        if (!result.isAccepted() && !context.getWorld().isClient && context.getPlayer() != null
                && !CameraLinkBlock.isValidMount(context.getWorld(), context.getBlockPos(), context.getSide())) {
            context.getPlayer().sendMessage(
                    Text.translatable("message.create-security.link_needs_vertical_display")
                            .formatted(Formatting.RED), true);
        }
        return result;
    }
}

