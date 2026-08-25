package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.camera.CameraViewManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "render", at = @At("TAIL"))
    private void createsecurity$postRenderCameras(float tickDelta, long startTime, boolean tick, CallbackInfo ci) {
        if (!CameraViewManager.isPreRendering()) {
            CameraViewManager.preRenderAllCameras(MinecraftClient.getInstance(), tickDelta, startTime);
        }
    }

    @Inject(method = "renderWorld", at = @At("HEAD"), cancellable = true)
    private void createsecurity$skipWorldRenderWithoutPlayer(float tickDelta, long limitTime,
                                                             net.minecraft.client.util.math.MatrixStack matrices,
                                                             CallbackInfo ci) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) {
            ci.cancel();
        }
    }
}

