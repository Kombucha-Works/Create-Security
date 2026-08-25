package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.camera.CameraViewManager;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererLabelMixin {

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;)Z",
            at = @At("HEAD"), cancellable = true)
    private void createsecurity$forceLabelInCameraFeed(LivingEntity entity,
                                                       CallbackInfoReturnable<Boolean> cir) {
        if (CameraViewManager.isInsideInnerRender() && entity instanceof AbstractClientPlayerEntity) {
            cir.setReturnValue(false);
        }
    }
}

