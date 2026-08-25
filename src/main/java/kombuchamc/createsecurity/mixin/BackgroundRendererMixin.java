package kombuchamc.createsecurity.mixin;

import com.mojang.blaze3d.systems.RenderSystem;
import kombuchamc.createsecurity.block.camera.CameraChunkVisibility;
import kombuchamc.createsecurity.block.camera.CameraViewManager;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BackgroundRenderer.class)
public abstract class BackgroundRendererMixin {

    @Redirect(method = "applyFog",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;"))
    private static Entity createsecurity$nullFocusedEntityForFog(Camera camera) {
        if (CameraViewManager.isInsideInnerRender()) {
            return null;
        }
        return camera.getFocusedEntity();
    }

    @Redirect(method = "render",
              at = @At(value = "INVOKE",
                       target = "Lnet/minecraft/client/render/Camera;getFocusedEntity()Lnet/minecraft/entity/Entity;"))
    private static Entity createsecurity$nullFocusedEntityForBackground(Camera camera) {
        if (CameraViewManager.isInsideInnerRender()) {
            return null;
        }
        return camera.getFocusedEntity();
    }

    @Inject(method = "applyFog", at = @At("TAIL"))
    private static void createsecurity$clampCameraFeedFog(
            Camera camera, BackgroundRenderer.FogType fogType, float viewDistance,
            boolean thickFog, float tickDelta, CallbackInfo ci) {
        if (!CameraViewManager.isInsideInnerRender()) return;
        if (fogType != BackgroundRenderer.FogType.FOG_TERRAIN) return;
        float capBlocks = CameraChunkVisibility.feedViewDistance() * 16f;
        if (RenderSystem.getShaderFogEnd() > capBlocks) {
            RenderSystem.setShaderFogEnd(capBlocks);
            RenderSystem.setShaderFogStart(
                    Math.min(RenderSystem.getShaderFogStart(), capBlocks * 0.7f));
        }
    }
}

