package kombuchamc.createsecurity.mixin;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleManager;
import net.minecraft.client.particle.ParticleTextureSheet;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(ParticleManager.class)
public interface ParticleManagerAccessor {

    @Accessor("factories")
    Int2ObjectMap<ParticleFactory<?>> getFactories();

    @Accessor("PARTICLE_TEXTURE_SHEETS")
    static List<ParticleTextureSheet> getTextureSheets() {
        throw new AssertionError();
    }
}
