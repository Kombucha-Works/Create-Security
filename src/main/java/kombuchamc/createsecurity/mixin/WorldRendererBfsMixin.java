package kombuchamc.createsecurity.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import kombuchamc.createsecurity.block.camera.CameraChunkVisibility;
import kombuchamc.createsecurity.block.camera.CameraViewManager;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WorldRenderer.class)
public abstract class WorldRendererBfsMixin {

    @Shadow protected abstract void applyFrustum(Frustum frustum);

    @Inject(method = "setupTerrain", at = @At("HEAD"), cancellable = true)
    private void createsecurity$cameraSetupTerrain(Camera camera, Frustum frustum,
                                                   boolean hasForcedFrustum, boolean spectator,
                                                   CallbackInfo ci) {
        if (!CameraViewManager.isInsideInnerRender()) return;
        Frustum coverFrustum = new Frustum(frustum).coverBoxAroundSetPosition(8);

        WorldRenderer self = (WorldRenderer) (Object) this;
        BlockPos cameraPos = CameraViewManager.getCurrentCameraPos();
        java.util.List<Object> cameraInfos = null;
        if (cameraPos != null) {
            CameraChunkVisibility.requestUpdate(self, cameraPos);
            cameraInfos = CameraChunkVisibility.getChunkInfos(self, cameraPos);
        }
        if (cameraInfos != null) {
            @SuppressWarnings({"unchecked", "rawtypes"})
            ObjectArrayList<Object> chunkInfos =
                    (ObjectArrayList) ((WorldRendererAccessor) this).getChunkInfos();
            chunkInfos.clear();
            for (Object info : cameraInfos) {
                if (coverFrustum.isVisible(((ChunkInfoAccessor) info).getChunk().getBoundingBox())) {
                    chunkInfos.add(info);
                }
            }
        } else {
            this.applyFrustum(coverFrustum);
        }
        ci.cancel();
    }

    @Inject(method = "scheduleTerrainUpdate", at = @At("HEAD"), cancellable = true)
    private void createsecurity$suppressTerrainUpdate(CallbackInfo ci) {
        if (CameraViewManager.isInsideInnerRender()) {
            ci.cancel();
        }
    }
}

