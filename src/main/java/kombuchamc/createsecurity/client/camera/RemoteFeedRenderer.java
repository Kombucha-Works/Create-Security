package kombuchamc.createsecurity.client.camera;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.systems.VertexSorter;
import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.camera.CameraViewManager;
import kombuchamc.createsecurity.mixin.WorldRendererAccessor;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.DiffuseLighting;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRenderDispatcher;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.entity.EntityRenderDispatcher;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.block.BlockRenderManager;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.fluid.FluidState;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeCoords;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

@Environment(EnvType.CLIENT)
public final class RemoteFeedRenderer {

    private static final int VERTICAL_SECTIONS = 4;
    private static final int BUILDER_SETS = 3;
    private static final long SETTLE_MS = 1000L;
    private static final int MIN_REACH_CHUNKS = 2;

    private static final int[][] NEIGHBOURS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};

    private static final Identifier RAIN = new Identifier("textures/environment/rain.png");
    private static final Identifier SNOW = new Identifier("textures/environment/snow.png");
    private static final float[] WEATHER_DX = new float[1024];
    private static final float[] WEATHER_DZ = new float[1024];

    static {
        for (int row = 0; row < 32; row++) {
            for (int column = 0; column < 32; column++) {
                float dx = column - 16;
                float dz = row - 16;
                float length = MathHelper.sqrt(dx * dx + dz * dz);
                WEATHER_DX[row << 5 | column] = -dz / length;
                WEATHER_DZ[row << 5 | column] = dx / length;
            }
        }
    }

    private RemoteFeedRenderer() {}

    private static final class BuilderSet {
        final Map<RenderLayer, BufferBuilder> builders = new IdentityHashMap<>();

        BuilderSet() {
            for (RenderLayer layer : RenderLayer.getBlockLayers()) {
                builders.put(layer, new BufferBuilder(layer.getExpectedBufferSize()));
            }
        }
    }

    private static final class Section {
        final BlockPos origin;
        final Map<RenderLayer, VertexBuffer> buffers = new IdentityHashMap<>();

        Section(BlockPos origin) {
            this.origin = origin;
        }

        void close() {
            for (VertexBuffer buffer : buffers.values()) buffer.close();
            buffers.clear();
        }
    }

    private static final class RegionState {
        final RemoteChunkStore.Region region;
        final Map<Long, Section> sections = new HashMap<>();
        final Set<Long> dirtyChunks = new LinkedHashSet<>();
        final Set<Long> incompleteChunks = new HashSet<>();
        final Set<Long> pendingSections = new LinkedHashSet<>();
        int reachChunks;
        volatile boolean released;
        VertexBuffer cloudsBuffer;
        boolean cloudsDirty = true;
        int lastCloudsBlockX = Integer.MIN_VALUE;
        int lastCloudsBlockY = Integer.MIN_VALUE;
        int lastCloudsBlockZ = Integer.MIN_VALUE;
        Vec3d lastCloudsColor = Vec3d.ZERO;
        CloudRenderMode lastCloudRenderMode;

        RegionState(RemoteChunkStore.Region region) {
            this.region = region;
        }
    }

    private record Result(RegionState state, long sectionKey, BlockPos origin, BuilderSet set,
                          Map<RenderLayer, BufferBuilder.BuiltBuffer> built, Throwable failure) {}

    private static final ArrayDeque<BuilderSet> freeSets = new ArrayDeque<>();
    private static int createdSets;
    private static final Map<RemoteChunkStore.Region, RegionState> states = new IdentityHashMap<>();
    private static final ConcurrentLinkedQueue<Result> results = new ConcurrentLinkedQueue<>();
    private static boolean buildFailureLogged;
    private static boolean weatherFailureLogged;
    private static boolean particleFailureLogged;
    private static boolean cloudFailureLogged;
    private static boolean skyBroken;
    private static boolean objectFailureLogged;

    public static void onChunkArrived(RemoteChunkStore.Region region, int chunkX, int chunkZ) {
        RegionState state = states.computeIfAbsent(region, RegionState::new);
        state.dirtyChunks.add(ChunkPos.toLong(chunkX, chunkZ));
        ChunkPos center = new ChunkPos(region.camera());
        state.reachChunks = Math.max(state.reachChunks,
                Math.max(Math.abs(chunkX - center.x), Math.abs(chunkZ - center.z)));
        for (int[] offset : NEIGHBOURS) {
            long neighbour = ChunkPos.toLong(chunkX + offset[0], chunkZ + offset[1]);
            if (state.incompleteChunks.contains(neighbour)) state.dirtyChunks.add(neighbour);
        }
    }

    public static void markSectionsDirty(RemoteChunkStore.Region region, java.util.Collection<Long> sectionKeys) {
        if (sectionKeys.isEmpty()) return;
        RegionState state = states.get(region);
        if (state == null) return;
        ClientWorld world = region.world();
        int cameraSection = ChunkSectionPos.getSectionCoord(region.camera().getY());
        int lowest = Math.max(world.getBottomSectionCoord(), cameraSection - VERTICAL_SECTIONS);
        int highest = Math.min(world.getTopSectionCoord() - 1, cameraSection + VERTICAL_SECTIONS);
        for (long sectionKey : sectionKeys) {
            int sectionY = ChunkSectionPos.unpackY(sectionKey);
            if (sectionY < lowest || sectionY > highest) continue;
            WorldChunk chunk = region.chunk(ChunkSectionPos.unpackX(sectionKey),
                    ChunkSectionPos.unpackZ(sectionKey));
            if (chunk == null) continue;
            ChunkSection section = chunk.getSection(chunk.getSectionIndex(sectionY << 4));
            if (section == null || section.isEmpty()) {
                Section old = state.sections.remove(sectionKey);
                if (old != null) old.close();
                state.pendingSections.remove(sectionKey);
                continue;
            }
            state.pendingSections.add(sectionKey);
        }
    }

    public static void release(RemoteChunkStore.Region region) {
        RegionState state = states.remove(region);
        if (state == null) return;
        state.released = true;
        if (state.cloudsBuffer != null) {
            state.cloudsBuffer.close();
            state.cloudsBuffer = null;
        }
        for (Section section : state.sections.values()) section.close();
        state.sections.clear();
        state.pendingSections.clear();
        state.dirtyChunks.clear();
    }

    public static void tick() {
        drainResults();
        for (RegionState state : states.values()) {
            expandDirtyChunks(state);
            submitPending(state);
        }
    }

    private static void expandDirtyChunks(RegionState state) {
        if (state.dirtyChunks.isEmpty()) return;
        RemoteChunkStore.Region region = state.region;
        boolean settled = Util.getMeasuringTimeMs() - region.lastArrivalMs() > SETTLE_MS;
        ClientWorld world = region.world();
        int cameraSection = ChunkSectionPos.getSectionCoord(region.camera().getY());
        int lowest = Math.max(world.getBottomSectionCoord(), cameraSection - VERTICAL_SECTIONS);
        int highest = Math.min(world.getTopSectionCoord() - 1, cameraSection + VERTICAL_SECTIONS);

        Iterator<Long> it = state.dirtyChunks.iterator();
        while (it.hasNext()) {
            long chunkKey = it.next();
            int chunkX = ChunkPos.getPackedX(chunkKey);
            int chunkZ = ChunkPos.getPackedZ(chunkKey);
            WorldChunk chunk = region.chunk(chunkX, chunkZ);
            if (chunk == null) {
                it.remove();
                continue;
            }
            boolean complete = true;
            for (int[] offset : NEIGHBOURS) {
                if (!region.hasChunk(chunkX + offset[0], chunkZ + offset[1])) {
                    complete = false;
                    break;
                }
            }
            if (!complete && !settled) continue;

            it.remove();
            if (complete) {
                state.incompleteChunks.remove(chunkKey);
            } else {
                state.incompleteChunks.add(chunkKey);
            }

            for (int sectionY = lowest; sectionY <= highest; sectionY++) {
                long sectionKey = ChunkSectionPos.asLong(chunkX, sectionY, chunkZ);
                ChunkSection section = chunk.getSection(chunk.getSectionIndex(sectionY << 4));
                if (section == null || section.isEmpty()) {
                    Section old = state.sections.remove(sectionKey);
                    if (old != null) old.close();
                    continue;
                }
                state.pendingSections.add(sectionKey);
            }
        }
    }

    private static void submitPending(RegionState state) {
        Iterator<Long> it = state.pendingSections.iterator();
        while (it.hasNext()) {
            BuilderSet set = acquireSet();
            if (set == null) return;
            long sectionKey = it.next();
            it.remove();
            BlockPos origin = new BlockPos(ChunkSectionPos.unpackX(sectionKey) << 4,
                    ChunkSectionPos.unpackY(sectionKey) << 4, ChunkSectionPos.unpackZ(sectionKey) << 4);
            Vec3d eye = Vec3d.ofCenter(state.region.camera());
            Util.getMainWorkerExecutor().execute(() -> results.add(build(state, sectionKey, origin, set, eye)));
        }
    }

    private static BuilderSet acquireSet() {
        BuilderSet set = freeSets.poll();
        if (set != null) return set;
        if (createdSets >= BUILDER_SETS) return null;
        createdSets++;
        return new BuilderSet();
    }

    private static Result build(RegionState state, long sectionKey, BlockPos origin, BuilderSet set, Vec3d eye) {
        Map<RenderLayer, BufferBuilder.BuiltBuffer> built = new IdentityHashMap<>();
        try {
            RemoteChunkStore.Region region = state.region;
            WorldChunk chunk = region.chunk(origin.getX() >> 4, origin.getZ() >> 4);
            if (state.released || chunk == null) return new Result(state, sectionKey, origin, set, built, null);
            ChunkSection section = chunk.getSection(chunk.getSectionIndex(origin.getY()));

            BlockRenderManager manager = MinecraftClient.getInstance().getBlockRenderManager();
            RemoteRegionView view = region.view();
            MatrixStack matrices = new MatrixStack();
            Random random = Random.create();
            Set<RenderLayer> begun = Collections.newSetFromMap(new IdentityHashMap<>());
            BlockPos.Mutable pos = new BlockPos.Mutable();

            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState blockState = section.getBlockState(x, y, z);
                        if (blockState.isAir()) continue;
                        pos.set(origin.getX() + x, origin.getY() + y, origin.getZ() + z);

                        FluidState fluid = blockState.getFluidState();
                        if (!fluid.isEmpty()) {
                            manager.renderFluid(pos, view, begin(set, begun, RenderLayers.getFluidLayer(fluid)),
                                    blockState, fluid);
                        }
                        if (blockState.getRenderType() == BlockRenderType.MODEL) {
                            BufferBuilder buffer = begin(set, begun, RenderLayers.getBlockLayer(blockState));
                            matrices.push();
                            matrices.translate(x, y, z);
                            manager.renderBlock(blockState, pos, view, matrices, buffer, true, random);
                            matrices.pop();
                        }
                    }
                }
            }

            for (RenderLayer layer : begun) {
                BufferBuilder buffer = set.builders.get(layer);
                if (layer == RenderLayer.getTranslucent()) {
                    buffer.setSorter(VertexSorter.byDistance((float) (eye.x - origin.getX()),
                            (float) (eye.y - origin.getY()), (float) (eye.z - origin.getZ())));
                }
                BufferBuilder.BuiltBuffer finished = buffer.endNullable();
                if (finished != null) built.put(layer, finished);
            }
            return new Result(state, sectionKey, origin, set, built, null);
        } catch (Throwable t) {
            for (BufferBuilder buffer : set.builders.values()) {
                if (!buffer.isBuilding()) continue;
                BufferBuilder.BuiltBuffer leftover = buffer.endNullable();
                if (leftover != null) leftover.release();
            }
            for (BufferBuilder.BuiltBuffer finished : built.values()) finished.release();
            built.clear();
            return new Result(state, sectionKey, origin, set, built, t);
        }
    }

    private static BufferBuilder begin(BuilderSet set, Set<RenderLayer> begun, RenderLayer layer) {
        BufferBuilder buffer = set.builders.computeIfAbsent(layer,
                missing -> new BufferBuilder(missing.getExpectedBufferSize()));
        if (begun.add(layer)) buffer.begin(layer.getDrawMode(), layer.getVertexFormat());
        return buffer;
    }

    private static void drainResults() {
        Result result;
        while ((result = results.poll()) != null) {
            RegionState state = result.state();
            boolean live = !state.released && states.get(state.region) == state;
            if (live) {
                Section section = new Section(result.origin());
                for (Map.Entry<RenderLayer, BufferBuilder.BuiltBuffer> entry : result.built().entrySet()) {
                    VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
                    buffer.bind();
                    buffer.upload(entry.getValue());
                    section.buffers.put(entry.getKey(), buffer);
                }
                VertexBuffer.unbind();
                Section old = section.buffers.isEmpty()
                        ? state.sections.remove(result.sectionKey())
                        : state.sections.put(result.sectionKey(), section);
                if (old != null) old.close();
            } else {
                for (BufferBuilder.BuiltBuffer finished : result.built().values()) finished.release();
            }
            freeSets.add(result.set());
            if (result.failure() != null && !buildFailureLogged) {
                buildFailureLogged = true;
                CreateSecurity.LOGGER.error("Failed to build a remote camera feed section", result.failure());
            }
        }
    }

    public static void render(MinecraftClient client, BlockPos cameraPos, Camera camera, float fov, float tickDelta) {
        MatrixStack modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.loadIdentity();
        RenderSystem.applyModelViewMatrix();
        try {
            renderInternal(client, cameraPos, camera, fov, tickDelta);
        } finally {
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void renderInternal(MinecraftClient client, BlockPos cameraPos, Camera camera,
                                       float fov, float tickDelta) {
        ClientWorld world = client.world;
        if (world == null) return;
        RemoteChunkStore.Region region = RemoteChunkStore.region(cameraPos);
        RegionState state = region == null ? null : states.get(region);
        int reach = Math.max(MIN_REACH_CHUNKS, state == null ? MIN_REACH_CHUNKS : state.reachChunks);
        float far = (reach + 1) * 16f;

        Matrix4f projection = new Matrix4f().perspective((float) Math.toRadians(fov),
                CameraViewManager.getCurrentAspect(), 0.05f, far * 4f);
        RenderSystem.setProjectionMatrix(projection, VertexSorter.BY_DISTANCE);

        MatrixStack view = new MatrixStack();
        view.multiply(RotationAxis.POSITIVE_X.rotationDegrees(camera.getPitch()));
        view.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(camera.getYaw() + 180f));
        Matrix4f rotation = new Matrix4f(view.peek().getPositionMatrix());
        RenderSystem.setInverseViewRotationMatrix(new Matrix3f(view.peek().getNormalMatrix()).invert());

        BackgroundRenderer.render(camera, tickDelta, world, reach, client.gameRenderer.getSkyDarkness(tickDelta));
        BackgroundRenderer.clearFog();
        RenderSystem.clear(GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT, MinecraftClient.IS_SYSTEM_MAC);

        if (!skyBroken) {
            try {
                BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_SKY, far, false, tickDelta);
                client.worldRenderer.renderSky(view, projection, tickDelta, camera, false,
                        () -> BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_SKY, far, false, tickDelta));
            } catch (Throwable t) {
                skyBroken = true;
                CreateSecurity.LOGGER.warn("Drawing the sky for a remote camera feed failed;"
                        + " remote feeds will show the sky colour without the sky itself", t);
            }
        }

        BackgroundRenderer.applyFog(camera, BackgroundRenderer.FogType.FOG_TERRAIN, far, false, tickDelta);
        Vec3d eye = camera.getPos();
        Frustum frustum = new Frustum(rotation, projection);
        frustum.setPosition(eye.x, eye.y, eye.z);

        List<Section> visible = new ArrayList<>();
        if (state != null) {
            for (Section section : state.sections.values()) {
                BlockPos o = section.origin;
                if (frustum.isVisible(new Box(o.getX(), o.getY(), o.getZ(), o.getX() + 16, o.getY() + 16, o.getZ() + 16))) {
                    visible.add(section);
                }
            }
            visible.sort(Comparator.comparingDouble((Section section) -> eye.squaredDistanceTo(
                    section.origin.getX() + 8, section.origin.getY() + 8, section.origin.getZ() + 8)).reversed());
        }

        for (RenderLayer layer : RenderLayer.getBlockLayers()) {
            if (layer == RenderLayer.getTranslucent() && region != null) {
                renderObjects(client, region, camera, view, frustum, eye, tickDelta);
            }
            drawLayer(layer, visible, rotation, projection, eye);
        }

        if (region != null) {
            try {
                RemoteParticles.render(region, camera, tickDelta);
            } catch (Throwable t) {
                if (!particleFailureLogged) {
                    particleFailureLogged = true;
                    CreateSecurity.LOGGER.error("Failed to draw particles for a remote camera feed", t);
                }
            }
        }

        if (region != null) {
            try {
                renderWeather(client, region, rotation, eye, tickDelta);
            } catch (Throwable t) {
                if (!weatherFailureLogged) {
                    weatherFailureLogged = true;
                    CreateSecurity.LOGGER.error("Failed to draw weather for a remote camera feed", t);
                }
            }
        }

        if (state != null) {
            try {
                renderClouds(client, state, view, projection, eye, tickDelta);
            } catch (Throwable t) {
                if (!cloudFailureLogged) {
                    cloudFailureLogged = true;
                    CreateSecurity.LOGGER.error("Failed to draw clouds for a remote camera feed", t);
                }
            }
        }
    }

    private static void renderClouds(MinecraftClient client, RegionState state, MatrixStack view,
                                     Matrix4f projection, Vec3d eye, float tickDelta) {
        if (client.options.getCloudRenderModeValue() == CloudRenderMode.OFF) return;
        WorldRenderer renderer = client.worldRenderer;
        WorldRendererAccessor access = (WorldRendererAccessor) renderer;

        VertexBuffer savedBuffer = access.getCloudsBuffer();
        boolean savedDirty = access.getCloudsDirty();
        int savedX = access.getLastCloudsBlockX();
        int savedY = access.getLastCloudsBlockY();
        int savedZ = access.getLastCloudsBlockZ();
        Vec3d savedColor = access.getLastCloudsColor();
        CloudRenderMode savedMode = access.getLastCloudRenderMode();

        access.setCloudsBuffer(state.cloudsBuffer);
        access.setCloudsDirty(state.cloudsDirty);
        access.setLastCloudsBlockX(state.lastCloudsBlockX);
        access.setLastCloudsBlockY(state.lastCloudsBlockY);
        access.setLastCloudsBlockZ(state.lastCloudsBlockZ);
        access.setLastCloudsColor(state.lastCloudsColor);
        access.setLastCloudRenderMode(state.lastCloudRenderMode);
        try {
            renderer.renderClouds(view, projection, tickDelta, eye.x, eye.y, eye.z);
        } finally {
            state.cloudsBuffer = access.getCloudsBuffer();
            state.cloudsDirty = access.getCloudsDirty();
            state.lastCloudsBlockX = access.getLastCloudsBlockX();
            state.lastCloudsBlockY = access.getLastCloudsBlockY();
            state.lastCloudsBlockZ = access.getLastCloudsBlockZ();
            state.lastCloudsColor = access.getLastCloudsColor();
            state.lastCloudRenderMode = access.getLastCloudRenderMode();

            access.setCloudsBuffer(savedBuffer);
            access.setCloudsDirty(savedDirty);
            access.setLastCloudsBlockX(savedX);
            access.setLastCloudsBlockY(savedY);
            access.setLastCloudsBlockZ(savedZ);
            access.setLastCloudsColor(savedColor);
            access.setLastCloudRenderMode(savedMode);
            RenderSystem.depthMask(true);
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
        }
    }

    private static void renderWeather(MinecraftClient client, RemoteChunkStore.Region region,
                                      Matrix4f rotation, Vec3d eye, float tickDelta) {
        ClientWorld world = region.world();
        float gradient = world.getRainGradient(tickDelta);
        if (gradient <= 0f) return;

        RemoteRegionView view = region.view();
        LightmapTextureManager lightmap = client.gameRenderer.getLightmapTextureManager();
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        int radius = MinecraftClient.isFancyGraphicsOrBetter() ? 10 : 5;
        int centerX = MathHelper.floor(eye.x);
        int centerY = MathHelper.floor(eye.y);
        int centerZ = MathHelper.floor(eye.z);
        int ticks = (int) world.getTime();
        float ticksDelta = ticks + tickDelta;
        BlockPos.Mutable pos = new BlockPos.Mutable();
        int mode = -1;

        MatrixStack modelView = RenderSystem.getModelViewStack();
        modelView.push();
        modelView.multiplyPositionMatrix(rotation);
        RenderSystem.applyModelViewMatrix();
        lightmap.enable();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(MinecraftClient.isFabulousGraphicsOrBetter());
        RenderSystem.setShader(GameRenderer::getParticleProgram);

        try {
            for (int z = centerZ - radius; z <= centerZ + radius; z++) {
                for (int x = centerX - radius; x <= centerX + radius; x++) {
                    WorldChunk chunk = region.chunk(x >> 4, z >> 4);
                    if (chunk == null) continue;

                    int index = (z - centerZ + 16) * 32 + x - centerX + 16;
                    double jitterX = WEATHER_DX[index] * 0.5;
                    double jitterZ = WEATHER_DZ[index] * 0.5;
                    Biome biome = chunk.getBiomeForNoiseGen(BiomeCoords.fromBlock(x),
                            BiomeCoords.fromBlock(centerY), BiomeCoords.fromBlock(z)).value();
                    if (!biome.hasPrecipitation()) continue;

                    int top = chunk.sampleHeightmap(Heightmap.Type.MOTION_BLOCKING, x & 15, z & 15) + 1;
                    int bottom = Math.max(centerY - radius, top);
                    int ceiling = Math.max(centerY + radius, top);
                    int lightY = Math.max(top, centerY);
                    if (bottom == ceiling) continue;

                    Random random = Random.create(
                            x * x * 3121 + x * 45238971 ^ z * z * 418711 + z * 13761);
                    pos.set(x, bottom, z);
                    Biome.Precipitation precipitation = biome.getPrecipitation(pos);
                    double offsetX = x + 0.5 - eye.x;
                    double offsetZ = z + 0.5 - eye.z;
                    float distance = (float) Math.sqrt(offsetX * offsetX + offsetZ * offsetZ) / radius;

                    if (precipitation == Biome.Precipitation.RAIN) {
                        if (mode != 0) {
                            if (mode >= 0) tessellator.draw();
                            mode = 0;
                            RenderSystem.setShaderTexture(0, RAIN);
                            buffer.begin(VertexFormat.DrawMode.QUADS,
                                    VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
                        }
                        int phase = (ticks + x * x * 3121 + x * 45238971
                                + z * z * 418711 + z * 13761) & 31;
                        float scroll = -((float) phase + tickDelta) / 32f * (3f + random.nextFloat());
                        float alpha = ((1f - distance * distance) * 0.5f + 0.5f) * gradient;
                        pos.set(x, lightY, z);
                        int light = WorldRenderer.getLightmapCoordinates(view, pos);
                        weatherQuad(buffer, x, z, bottom, ceiling, eye, jitterX, jitterZ,
                                0f, bottom * 0.25f + scroll, 1f, ceiling * 0.25f + scroll,
                                alpha, light);
                    } else if (precipitation == Biome.Precipitation.SNOW) {
                        if (mode != 1) {
                            if (mode >= 0) tessellator.draw();
                            mode = 1;
                            RenderSystem.setShaderTexture(0, SNOW);
                            buffer.begin(VertexFormat.DrawMode.QUADS,
                                    VertexFormats.POSITION_TEXTURE_COLOR_LIGHT);
                        }
                        float scroll = -((float) (ticks & 511) + tickDelta) / 512f;
                        float driftU = (float) (random.nextDouble()
                                + (double) ticksDelta * 0.01 * (double) (float) random.nextGaussian());
                        float driftV = (float) (random.nextDouble()
                                + (double) (ticksDelta * (float) random.nextGaussian()) * 0.001);
                        float alpha = ((1f - distance * distance) * 0.3f + 0.5f) * gradient;
                        pos.set(x, lightY, z);
                        int light = WorldRenderer.getLightmapCoordinates(view, pos);
                        int sky = (light >> 16 & 65535) * 3 + 240;
                        int block = (light & 65535) * 3 + 240;
                        weatherQuad(buffer, x, z, bottom, ceiling, eye, jitterX, jitterZ,
                                driftU, bottom * 0.25f + scroll + driftV,
                                1f + driftU, ceiling * 0.25f + scroll + driftV,
                                alpha, block / 4 | sky / 4 << 16);
                    }
                }
            }
            if (mode >= 0) tessellator.draw();
        } finally {
            RenderSystem.enableCull();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            lightmap.disable();
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
        }
    }

    private static void weatherQuad(BufferBuilder buffer, int x, int z, int bottom, int ceiling, Vec3d eye,
                                    double jitterX, double jitterZ, float minU, float minV,
                                    float maxU, float maxV, float alpha, int light) {
        double westX = x - eye.x - jitterX + 0.5;
        double westZ = z - eye.z - jitterZ + 0.5;
        double eastX = x - eye.x + jitterX + 0.5;
        double eastZ = z - eye.z + jitterZ + 0.5;
        double high = ceiling - eye.y;
        double low = bottom - eye.y;
        buffer.vertex(westX, high, westZ).texture(minU, minV)
                .color(1f, 1f, 1f, alpha).light(light).next();
        buffer.vertex(eastX, high, eastZ).texture(maxU, minV)
                .color(1f, 1f, 1f, alpha).light(light).next();
        buffer.vertex(eastX, low, eastZ).texture(maxU, maxV)
                .color(1f, 1f, 1f, alpha).light(light).next();
        buffer.vertex(westX, low, westZ).texture(minU, maxV)
                .color(1f, 1f, 1f, alpha).light(light).next();
    }

    private static void drawLayer(RenderLayer layer, List<Section> visible, Matrix4f rotation,
                                  Matrix4f projection, Vec3d eye) {
        if (visible.isEmpty()) return;
        layer.startDrawing();
        ShaderProgram shader = RenderSystem.getShader();
        if (shader != null) {
            for (Section section : visible) {
                VertexBuffer buffer = section.buffers.get(layer);
                if (buffer == null || buffer.isClosed()) continue;
                Matrix4f modelView = new Matrix4f(rotation).translate(
                        (float) (section.origin.getX() - eye.x),
                        (float) (section.origin.getY() - eye.y),
                        (float) (section.origin.getZ() - eye.z));
                buffer.bind();
                buffer.draw(modelView, projection, shader);
            }
            VertexBuffer.unbind();
        }
        layer.endDrawing();
    }

    private static void renderObjects(MinecraftClient client, RemoteChunkStore.Region region, Camera camera,
                                      MatrixStack view, Frustum frustum, Vec3d eye, float tickDelta) {
        EntityRenderDispatcher entityDispatcher = client.getEntityRenderDispatcher();
        BlockEntityRenderDispatcher blockEntityDispatcher = client.getBlockEntityRenderDispatcher();
        Camera savedEntityCamera = entityDispatcher.camera;
        Camera savedBlockEntityCamera = blockEntityDispatcher.camera;
        VertexConsumerProvider.Immediate immediate = client.getBufferBuilders().getEntityVertexConsumers();
        RemoteRegionView regionView = region.view();

        entityDispatcher.configure(region.world(), camera, null);
        blockEntityDispatcher.configure(region.world(), camera, null);
        DiffuseLighting.enableForLevel(view.peek().getPositionMatrix());
        try {
            for (BlockEntity blockEntity : region.blockEntities()) {
                try {
                    BlockEntityRenderer<BlockEntity> renderer = blockEntityDispatcher.get(blockEntity);
                    if (renderer == null) continue;
                    BlockPos pos = blockEntity.getPos();
                    if (!renderer.rendersOutsideBoundingBox(blockEntity) && !frustum.isVisible(new Box(pos))) continue;
                    if (!renderer.isInRenderDistance(blockEntity, eye)) continue;
                    view.push();
                    view.translate(pos.getX() - eye.x, pos.getY() - eye.y, pos.getZ() - eye.z);
                    renderer.render(blockEntity, tickDelta, view, immediate,
                            WorldRenderer.getLightmapCoordinates(regionView, pos), OverlayTexture.DEFAULT_UV);
                    view.pop();
                } catch (Throwable t) {
                    if (!objectFailureLogged) {
                        objectFailureLogged = true;
                        CreateSecurity.LOGGER.error("Failed to render a remote camera block entity", t);
                    }
                }
            }
            RemoteEntities.render(region.camera(), regionView, frustum, eye, view, immediate);
            immediate.draw();
        } finally {
            if (savedEntityCamera != null) entityDispatcher.configure(region.world(), savedEntityCamera, client.targetedEntity);
            if (savedBlockEntityCamera != null) {
                blockEntityDispatcher.configure(region.world(), savedBlockEntityCamera, client.crosshairTarget);
            }
        }
    }
}
