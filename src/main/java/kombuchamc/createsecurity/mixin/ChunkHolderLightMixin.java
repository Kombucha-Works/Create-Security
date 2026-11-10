package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.camera.RemoteChunkFeed;
import net.minecraft.server.world.ChunkHolder;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.BitSet;

@Mixin(ChunkHolder.class)
public abstract class ChunkHolderLightMixin {

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private BitSet blockLightUpdateBits;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private BitSet skyLightUpdateBits;

    @Shadow
    @org.spongepowered.asm.mixin.Final
    private LightingProvider lightingProvider;

    @Inject(method = "flushUpdates", at = @At("HEAD"))
    private void createsecurity$remoteFeedLight(WorldChunk chunk, CallbackInfo info) {
        if (skyLightUpdateBits.isEmpty() && blockLightUpdateBits.isEmpty()) return;
        if (!(chunk.getWorld() instanceof ServerWorld world)) return;
        RemoteChunkFeed.onLightFlush(world, chunk.getPos(), lightingProvider,
                skyLightUpdateBits, blockLightUpdateBits);
    }
}
