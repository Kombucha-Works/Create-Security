package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.camera.RemoteChunkFeed;
import net.minecraft.block.BlockState;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerWorld.class)
public class ServerWorldFeedMixin {

    @Inject(method = "updateListeners", at = @At("HEAD"))
    private void createsecurity$remoteFeedBlockUpdate(BlockPos pos, BlockState oldState, BlockState newState,
                                                      int flags, CallbackInfo info) {
        RemoteChunkFeed.onBlockChanged((ServerWorld) (Object) this, pos);
    }

    @Inject(method = "spawnParticles(Lnet/minecraft/particle/ParticleEffect;DDDIDDDD)I", at = @At("HEAD"))
    private void createsecurity$remoteFeedParticles(ParticleEffect particle, double x, double y, double z,
                                                    int count, double deltaX, double deltaY, double deltaZ,
                                                    double speed, CallbackInfoReturnable<Integer> info) {
        RemoteChunkFeed.onParticles((ServerWorld) (Object) this, particle, x, y, z, count,
                deltaX, deltaY, deltaZ, speed);
    }
}
