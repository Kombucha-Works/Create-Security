package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.camera.CameraViewManager;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererProjMixin {

    @Inject(method = "getBasicProjectionMatrix", at = @At("HEAD"), cancellable = true)
    private void createsecurity$overrideProjMatrixDuringInnerRender(double fov, CallbackInfoReturnable<Matrix4f> cir) {
        if (CameraViewManager.isInsideInnerRender()) {
            GameRenderer self = (GameRenderer) (Object) this;
            double fovDeg = CameraViewManager.getCurrentFov();

            float aspect = CameraViewManager.getCurrentAspect();
            Matrix4f matrix = new Matrix4f();
            matrix.perspective(
                    (float) (fovDeg * Math.PI / 180.0),
                    aspect,
                    0.05f,
                    self.getViewDistance() * 4.0f
            );
            cir.setReturnValue(matrix);
        }
    }

    @Inject(method = "bobView", at = @At("HEAD"), cancellable = true, require = 0)
    private void createsecurity$skipBobView(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CameraViewManager.isInsideInnerRender()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRenderBlockOutline", at = @At("HEAD"), cancellable = true)
    private void createsecurity$noBlockOutlineDuringInnerRender(CallbackInfoReturnable<Boolean> cir) {
        if (CameraViewManager.isInsideInnerRender()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = {"bobViewWhenHurt", "tiltViewWhenHurt", "applyDamageTilt"},
            at = @At("HEAD"), cancellable = true, require = 0)
    private void createsecurity$skipHurtBob(MatrixStack matrices, float tickDelta, CallbackInfo ci) {
        if (CameraViewManager.isInsideInnerRender()) {
            ci.cancel();
        }
    }

    @Redirect(method = "renderWorld",
              at = @At(value = "FIELD",
                       target = "Lnet/minecraft/client/network/ClientPlayerEntity;prevNauseaIntensity:F"),
              require = 0)
    private float createsecurity$noPrevNausea(ClientPlayerEntity player) {

        if (CameraViewManager.isInsideInnerRender() || player == null) return 0f;
        return player.prevNauseaIntensity;
    }

    @Redirect(method = "renderWorld",
              at = @At(value = "FIELD",
                       target = "Lnet/minecraft/client/network/ClientPlayerEntity;nauseaIntensity:F"),
              require = 0)
    private float createsecurity$noCurrentNausea(ClientPlayerEntity player) {
        if (CameraViewManager.isInsideInnerRender() || player == null) return 0f;
        return player.nauseaIntensity;
    }
}

