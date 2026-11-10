package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.camera.CameraViewManager;
import kombuchamc.createsecurity.client.compat.RenderCompat;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void createsecurity$postRenderCameras(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        if (CameraViewManager.isPreRendering()) return;
        CameraViewManager.preRenderAllCameras(MinecraftClient.getInstance(), tickDelta, startTime, false);
    }

    @Inject(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/render/GameRenderer;renderWorld(FJLnet/minecraft/client/util/math/MatrixStack;)V"))
    private void createsecurity$preRenderShadedCameras(float tickDelta, long startTime, boolean tick,
                                                       CallbackInfo ci) {
        if (CameraViewManager.isPreRendering()) return;
        if (!RenderCompat.shadedFeedsActive()) return;
        CameraViewManager.shadedFrameTick();
        CameraViewManager.preRenderAllCameras(MinecraftClient.getInstance(), tickDelta, startTime, true);
    }

    @Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
    private void createsecurity$beginStolenFrame(float tickDelta, long limitTime, MatrixStack matrices,
                                                 CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            ci.cancel();
            return;
        }
        if (CameraViewManager.isPreRendering()) return;
        CameraViewManager.beginStolenFrameIfPending(client, tickDelta);
    }

    @Inject(method = "renderWorld", at = @At("TAIL"))
    private void createsecurity$endStolenFrame(float tickDelta, long limitTime, MatrixStack matrices,
                                               CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (CameraViewManager.endStolenFrameIfActive(client)) return;
        if (CameraViewManager.isPreRendering()) return;
        CameraViewManager.drawDeferredPanels();
        if (!RenderCompat.shadedFeedsActive()) return;
        CameraViewManager.savePlayerView(client);
    }
}
