package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.obsidian.ObsidianBlocks;
import kombuchamc.createsecurity.block.obsidian.ObsidianProtection;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientObsidianBreakMixin {

    @Inject(method = "breakBlock", at = @At("HEAD"), cancellable = true)
    private void createsecurity$keepProtectedObsidian(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.world == null || client.player == null) return;
        BlockState state = client.world.getBlockState(pos);
        if (!ObsidianBlocks.isSecured(state)) return;
        if (ObsidianProtection.canBreak(state, client.player, pos)) return;
        cir.setReturnValue(false);
    }
}
