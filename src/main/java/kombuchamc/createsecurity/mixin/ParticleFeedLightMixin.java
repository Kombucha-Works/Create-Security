package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.client.camera.RemoteParticles;
import net.minecraft.client.particle.Particle;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Particle.class)
public abstract class ParticleFeedLightMixin {

    @Shadow
    protected double x;

    @Shadow
    protected double y;

    @Shadow
    protected double z;

    @Inject(method = "getBrightness", at = @At("HEAD"), cancellable = true)
    private void createsecurity$remoteFeedBrightness(float tint, CallbackInfoReturnable<Integer> info) {
        if (!RemoteParticles.isRenderingFeed()) return;
        info.setReturnValue(RemoteParticles.lightAt(x, y, z));
    }
}
