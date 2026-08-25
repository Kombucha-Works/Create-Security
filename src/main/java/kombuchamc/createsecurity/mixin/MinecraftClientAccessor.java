package kombuchamc.createsecurity.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.Framebuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(MinecraftClient.class)
public interface MinecraftClientAccessor {

    @Accessor("framebuffer")
    Framebuffer getFramebufferField();

    @Mutable
    @Accessor("framebuffer")
    void setFramebufferField(Framebuffer fb);
}

