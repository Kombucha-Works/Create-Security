package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.camera.CameraViewManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererHandMixin {

    @Inject(method = "renderHand", at = @At("HEAD"), cancellable = true)
    private void createsecurity$skipHandDuringCameraRender(MatrixStack matrices, Camera camera,
                                                            float tickDelta, CallbackInfo ci) {
        if (CameraViewManager.isInsideInnerRender()) {
            ci.cancel();
        }
    }
}

