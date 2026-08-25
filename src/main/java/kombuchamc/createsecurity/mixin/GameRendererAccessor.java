package kombuchamc.createsecurity.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GameRenderer.class)
public interface GameRendererAccessor {

    @Accessor("camera")
    Camera getCameraField();

    @Mutable
    @Accessor("camera")
    void setCameraField(Camera camera);
}

