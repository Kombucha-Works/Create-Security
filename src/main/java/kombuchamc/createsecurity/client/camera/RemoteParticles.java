package kombuchamc.createsecurity.client.camera;

import com.mojang.blaze3d.systems.RenderSystem;
import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.mixin.ParticleManagerAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleFactory;
import net.minecraft.client.particle.ParticleTextureSheet;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.random.Random;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class RemoteParticles {

    private static final int MAX_PER_CAMERA = 1024;

    private static final Map<BlockPos, Deque<Particle>> byCamera = new HashMap<>();
    private static RemoteChunkStore.Region renderingRegion;
    private static boolean factoryFailureLogged;

    private RemoteParticles() {}

    public static void spawn(BlockPos cameraPos, List<ParticleS2CPacket> events) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;
        Deque<Particle> list = byCamera.computeIfAbsent(cameraPos.toImmutable(), key -> new ArrayDeque<>());
        Random random = world.random;
        for (ParticleS2CPacket event : events) {
            if (event.getCount() == 0) {
                add(list, world, event.getParameters(), event.getX(), event.getY(), event.getZ(),
                        event.getSpeed() * event.getOffsetX(),
                        event.getSpeed() * event.getOffsetY(),
                        event.getSpeed() * event.getOffsetZ());
                continue;
            }
            for (int i = 0; i < event.getCount(); i++) {
                add(list, world, event.getParameters(),
                        event.getX() + random.nextGaussian() * event.getOffsetX(),
                        event.getY() + random.nextGaussian() * event.getOffsetY(),
                        event.getZ() + random.nextGaussian() * event.getOffsetZ(),
                        random.nextGaussian() * event.getSpeed(),
                        random.nextGaussian() * event.getSpeed(),
                        random.nextGaussian() * event.getSpeed());
            }
        }
    }

    public static void addBlockBreak(RemoteChunkStore.Region region, BlockPos pos, BlockState state) {
        if (state.isAir()) return;
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null || world != region.world()) return;
        Deque<Particle> list = byCamera.computeIfAbsent(region.camera().toImmutable(), key -> new ArrayDeque<>());
        BlockStateParticleEffect effect = new BlockStateParticleEffect(ParticleTypes.BLOCK, state);
        state.getOutlineShape(region.view(), pos).forEachBox((minX, minY, minZ, maxX, maxY, maxZ) -> {
            double spanX = Math.min(1.0, maxX - minX);
            double spanY = Math.min(1.0, maxY - minY);
            double spanZ = Math.min(1.0, maxZ - minZ);
            int stepsX = Math.max(2, MathHelper.ceil(spanX / 0.25));
            int stepsY = Math.max(2, MathHelper.ceil(spanY / 0.25));
            int stepsZ = Math.max(2, MathHelper.ceil(spanZ / 0.25));
            for (int ix = 0; ix < stepsX; ix++) {
                for (int iy = 0; iy < stepsY; iy++) {
                    for (int iz = 0; iz < stepsZ; iz++) {
                        double fractionX = (ix + 0.5) / stepsX;
                        double fractionY = (iy + 0.5) / stepsY;
                        double fractionZ = (iz + 0.5) / stepsZ;
                        double offsetX = fractionX * spanX + minX;
                        double offsetY = fractionY * spanY + minY;
                        double offsetZ = fractionZ * spanZ + minZ;
                        add(list, world, effect,
                                pos.getX() + offsetX, pos.getY() + offsetY, pos.getZ() + offsetZ,
                                fractionX - 0.5, fractionY - 0.5, fractionZ - 0.5);
                    }
                }
            }
        });
    }

    private static void add(Deque<Particle> list, ClientWorld world, ParticleEffect effect,
                            double x, double y, double z,
                            double velocityX, double velocityY, double velocityZ) {
        Particle particle = create(world, effect, x, y, z, velocityX, velocityY, velocityZ);
        if (particle == null) return;
        list.addLast(particle);
        while (list.size() > MAX_PER_CAMERA) list.pollFirst();
    }

    @Nullable
    @SuppressWarnings("unchecked")
    private static Particle create(ClientWorld world, ParticleEffect effect, double x, double y, double z,
                                   double velocityX, double velocityY, double velocityZ) {
        try {
            ParticleManagerAccessor access =
                    (ParticleManagerAccessor) MinecraftClient.getInstance().particleManager;
            ParticleFactory<ParticleEffect> factory = (ParticleFactory<ParticleEffect>) access.getFactories()
                    .get(Registries.PARTICLE_TYPE.getRawId(effect.getType()));
            if (factory == null) return null;
            return factory.createParticle(effect, world, x, y, z, velocityX, velocityY, velocityZ);
        } catch (Throwable t) {
            if (!factoryFailureLogged) {
                factoryFailureLogged = true;
                CreateSecurity.LOGGER.error("Failed to create a remote camera feed particle", t);
            }
            return null;
        }
    }

    public static void tick() {
        if (byCamera.isEmpty()) return;
        for (Deque<Particle> list : byCamera.values()) {
            Iterator<Particle> it = list.iterator();
            while (it.hasNext()) {
                Particle particle = it.next();
                try {
                    particle.tick();
                } catch (Throwable t) {
                    it.remove();
                    continue;
                }
                if (!particle.isAlive()) it.remove();
            }
        }
    }

    public static void render(RemoteChunkStore.Region region, Camera camera, float tickDelta) {
        Deque<Particle> list = byCamera.get(region.camera());
        if (list == null || list.isEmpty()) return;
        MinecraftClient client = MinecraftClient.getInstance();
        LightmapTextureManager lightmap = client.gameRenderer.getLightmapTextureManager();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        renderingRegion = region;
        lightmap.enable();
        RenderSystem.enableDepthTest();
        try {
            for (ParticleTextureSheet sheet : ParticleManagerAccessor.getTextureSheets()) {
                if (sheet == ParticleTextureSheet.NO_RENDER) continue;
                boolean begun = false;
                for (Particle particle : list) {
                    if (particle.getType() != sheet) continue;
                    if (!begun) {
                        RenderSystem.setShader(GameRenderer::getParticleProgram);
                        sheet.begin(buffer, client.getTextureManager());
                        begun = true;
                    }
                    try {
                        particle.buildGeometry(buffer, camera, tickDelta);
                    } catch (Throwable ignored) {
                    }
                }
                if (begun) sheet.draw(tessellator);
            }
        } finally {
            renderingRegion = null;
            lightmap.disable();
            RenderSystem.depthMask(true);
            RenderSystem.disableBlend();
        }
    }

    public static boolean isRenderingFeed() {
        return renderingRegion != null;
    }

    public static int lightAt(double x, double y, double z) {
        RemoteChunkStore.Region region = renderingRegion;
        if (region == null) return 0;
        return WorldRenderer.getLightmapCoordinates(region.view(), BlockPos.ofFloored(x, y, z));
    }

    public static void clear(BlockPos cameraPos) {
        byCamera.remove(cameraPos);
    }

    public static void clearAll() {
        byCamera.clear();
    }
}
