package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.KeycardReaderGeometry;
import kombuchamc.createsecurity.client.KeycardReaderClientStore;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientBlockBreakReaderMixin {

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private void createsecurity$keepHostWhileRemovingReader(BlockPos pos,
                                                            CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null) return;
        if (!KeycardReaderClientStore.hasGroup(client.world, pos)) return;
        if (!(client.crosshairTarget instanceof BlockHitResult hit)) return;
        if (!hit.getBlockPos().equals(pos)) return;
        if (!KeycardReaderGeometry.isAimedAtPanel(client.world, pos, hit.getSide(), hit.getPos())) return;

        cir.setReturnValue(false);
    }
}
