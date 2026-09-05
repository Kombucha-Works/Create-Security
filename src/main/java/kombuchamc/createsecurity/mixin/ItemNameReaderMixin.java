package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.KeycardReaderAttachment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Item.class)
public class ItemNameReaderMixin {

    @Inject(method = "getName(Lnet/minecraft/item/ItemStack;)Lnet/minecraft/text/Text;",
            at = @At("HEAD"), cancellable = true)
    private void createsecurity$lockedShulkerBoxName(ItemStack stack,
                                                     CallbackInfoReturnable<Text> cir) {
        if (KeycardReaderAttachment.isLockedShulkerBox(stack)) {
            cir.setReturnValue(Text.translatable("item.create-security.locked_shulker_box"));
        }
    }
}
