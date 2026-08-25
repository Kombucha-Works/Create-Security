package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.camera.CameraViewManager;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = WorldRenderer.class, priority = 1100)
public abstract class WorldRendererSortMixin {

    @ModifyConstant(method = "renderLayer", constant = @Constant(intValue = 15), require = 0)
    private int createsecurity$noSortDuringInnerRender(int original) {
        return CameraViewManager.isInsideInnerRender() ? 0 : original;
    }
}

