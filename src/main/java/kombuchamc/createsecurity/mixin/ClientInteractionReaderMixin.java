package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.KeycardReaderGeometry;
import kombuchamc.createsecurity.block.KeycardReaderPlacement;
import kombuchamc.createsecurity.client.KeycardReaderClientStore;
import kombuchamc.createsecurity.items.RegisterModItems;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientInteractionReaderMixin {

    @Inject(method = "interactBlockInternal", at = @At("HEAD"), cancellable = true)
    private void createsecurity$skipReaderPlacementPrediction(ClientPlayerEntity player, Hand hand,
                                                              BlockHitResult hitResult,
                                                              CallbackInfoReturnable<ActionResult> cir) {
        boolean sneakingWithItem = player.isSneaking()
                && !player.getStackInHand(hand).isEmpty()
                && !player.getStackInHand(hand).isOf(RegisterModItems.KEYCARD);
        if (!sneakingWithItem
                && KeycardReaderClientStore.isArmedGroup(player.getWorld(), hitResult.getBlockPos())) {
            cir.setReturnValue(ActionResult.SUCCESS);
            return;
        }
        if (!player.getStackInHand(hand).isOf(RegisterModItems.KEYCARD_READER)) return;

        BlockPos pos = hitResult.getBlockPos();
        Direction face = hitResult.getSide();
        if (KeycardReaderClientStore.hasGroup(player.getWorld(), pos)) return;
        if (!KeycardReaderPlacement.isMountableSurface(player.getWorld(), pos, face)) return;
        if (!KeycardReaderGeometry.isAimedAtPanel(player.getWorld(), pos, face, hitResult.getPos())) return;

        cir.setReturnValue(ActionResult.SUCCESS);
    }
}
