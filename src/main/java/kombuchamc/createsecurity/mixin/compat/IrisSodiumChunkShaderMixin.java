package kombuchamc.createsecurity.mixin.compat;

import kombuchamc.createsecurity.block.camera.CameraViewManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(targets = "net.irisshaders.iris.compat.sodium.impl.shader_overrides.IrisChunkProgramOverrides", remap = false)
public class IrisSodiumChunkShaderMixin {

    @org.spongepowered.asm.mixin.Unique
    private static boolean createsecurity$logged;

    @Inject(method = "getProgramOverride", at = @At("HEAD"), cancellable = true, remap = false, require = 0)
    private void createsecurity$vanillaTerrainInFeeds(CallbackInfoReturnable<Object> cir) {
        if (!CameraViewManager.wantsVanillaTerrain()) return;
        if (!createsecurity$logged) {
            createsecurity$logged = true;
            kombuchamc.createsecurity.CreateSecurity.LOGGER.info(
                    "Camera feeds are drawing terrain without the pack's Sodium shader"
                            + " (vanillaFeedTerrain=true)");
        }
        cir.setReturnValue(null);
    }
}
