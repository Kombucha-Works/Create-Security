package kombuchamc.createsecurity.mixin;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.gl.Framebuffer;
import net.minecraft.client.gl.PostEffectProcessor;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.concurrent.atomic.AtomicBoolean;

@Mixin(WorldRenderer.class)
public interface WorldRendererAccessor {

    @Accessor("chunkInfos")
    ObjectArrayList<?> getChunkInfos();

    @Accessor("frustum")
    Frustum getFrustumField();

    @Mutable
    @Accessor("frustum")
    void setFrustumField(Frustum frustum);

    @Accessor("lastTranslucentSortX")
    double getLastTranslucentSortX();

    @Accessor("lastTranslucentSortX")
    void setLastTranslucentSortX(double v);

    @Accessor("lastTranslucentSortY")
    double getLastTranslucentSortY();

    @Accessor("lastTranslucentSortY")
    void setLastTranslucentSortY(double v);

    @Accessor("lastTranslucentSortZ")
    double getLastTranslucentSortZ();

    @Accessor("lastTranslucentSortZ")
    void setLastTranslucentSortZ(double v);

    @Accessor("transparencyPostProcessor")
    PostEffectProcessor getTransparencyPostProcessor();

    @Mutable
    @Accessor("transparencyPostProcessor")
    void setTransparencyPostProcessor(PostEffectProcessor processor);

    @Accessor("translucentFramebuffer")
    Framebuffer getTranslucentFramebufferField();

    @Mutable
    @Accessor("translucentFramebuffer")
    void setTranslucentFramebufferField(Framebuffer framebuffer);

    @Accessor("particlesFramebuffer")
    Framebuffer getParticlesFramebufferField();

    @Mutable
    @Accessor("particlesFramebuffer")
    void setParticlesFramebufferField(Framebuffer framebuffer);

    @Accessor("weatherFramebuffer")
    Framebuffer getWeatherFramebufferField();

    @Mutable
    @Accessor("weatherFramebuffer")
    void setWeatherFramebufferField(Framebuffer framebuffer);

    @Accessor("cloudsFramebuffer")
    Framebuffer getCloudsFramebufferField();

    @Mutable
    @Accessor("cloudsFramebuffer")
    void setCloudsFramebufferField(Framebuffer framebuffer);

    @Accessor("entityFramebuffer")
    Framebuffer getEntityFramebufferField();

    @Mutable
    @Accessor("entityFramebuffer")
    void setEntityFramebufferField(Framebuffer framebuffer);

    @Accessor("entityOutlinesFramebuffer")
    Framebuffer getEntityOutlinesFramebufferField();

    @Mutable
    @Accessor("entityOutlinesFramebuffer")
    void setEntityOutlinesFramebufferField(Framebuffer framebuffer);

    @Accessor("chunks")
    BuiltChunkStorage getChunkStorage();

    @Accessor("viewDistance")
    int getViewDistanceField();

    @Accessor("updateFinished")
    AtomicBoolean getUpdateFinished();

}

