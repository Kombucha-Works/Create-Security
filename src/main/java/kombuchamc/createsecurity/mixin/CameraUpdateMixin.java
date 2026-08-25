package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.camera.CameraViewManager;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Camera.class)
public abstract class CameraUpdateMixin {

    @Inject(method = "update", at = @At("HEAD"), cancellable = true)
    private void createsecurity$skipUpdateDuringCameraRender(BlockView area, Entity focusedEntity,
                                                              boolean thirdPerson, boolean inverseView,
                                                              float tickDelta, CallbackInfo ci) {
        if (CameraViewManager.isInsideInnerRender()) {
            ci.cancel();
        }
    }
}

