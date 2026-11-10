package kombuchamc.createsecurity.client.camera;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.entity.CameraFeedView;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.packet.s2c.play.ChunkData;
import net.minecraft.network.packet.s2c.play.LightData;
import net.minecraft.registry.Registries;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.ChunkNibbleArray;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Environment(EnvType.CLIENT)
public final class RemoteChunkStore {

    private static final int FULL_SKY = 15;
    private static final int NO_BLOCK_LIGHT = 0;
    private static final int MAX_BREAK_PARTICLE_BLOCKS = 8;

    public static final class Region {
        private final BlockPos camera;
        private final ClientWorld world;
        private final Map<Long, WorldChunk> chunks = new ConcurrentHashMap<>();
        private final Map<Long, ChunkNibbleArray> skyLight = new ConcurrentHashMap<>();
        private final Map<Long, ChunkNibbleArray> blockLight = new ConcurrentHashMap<>();
        private final Map<BlockPos, BlockEntity> blockEntities = new HashMap<>();
        private final RemoteRegionView view;
        private volatile long lastArrivalMs;

        Region(BlockPos camera, ClientWorld world) {
            this.camera = camera;
            this.world = world;
            this.view = new RemoteRegionView(this);
        }

        public BlockPos camera() {
            return camera;
        }

        public ClientWorld world() {
            return world;
        }

        public RemoteRegionView view() {
            return view;
        }

        public long lastArrivalMs() {
            return lastArrivalMs;
        }

        @Nullable
        public WorldChunk chunk(int chunkX, int chunkZ) {
            return chunks.get(ChunkPos.toLong(chunkX, chunkZ));
        }

        public boolean hasChunk(int chunkX, int chunkZ) {
            return chunks.containsKey(ChunkPos.toLong(chunkX, chunkZ));
        }

        public int chunkCount() {
            return chunks.size();
        }

        public Collection<BlockEntity> blockEntities() {
            return blockEntities.values();
        }

        int skyLight(BlockPos pos) {
            return light(skyLight, pos, FULL_SKY);
        }

        int blockLight(BlockPos pos) {
            return light(blockLight, pos, NO_BLOCK_LIGHT);
        }

        private static int light(Map<Long, ChunkNibbleArray> source, BlockPos pos, int missing) {
            ChunkNibbleArray nibbles = source.get(ChunkSectionPos.asLong(
                    pos.getX() >> 4, pos.getY() >> 4, pos.getZ() >> 4));
            return nibbles == null ? missing : nibbles.get(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
        }
    }

    public record BlockUpdate(BlockPos pos, BlockState state, @Nullable BlockEntityType<?> type,
                              @Nullable NbtCompound nbt) {}

    public record LightUpdate(long section, @Nullable ChunkNibbleArray sky,
                              @Nullable ChunkNibbleArray block) {}

    private static final Map<BlockPos, Region> regions = new HashMap<>();
    private static boolean blockEntityFailureLogged;

    private RemoteChunkStore() {}

    public static void accept(BlockPos cameraPos, int chunkX, int chunkZ,
                              ChunkData chunkData, LightData lightData) {
        ClientWorld world = MinecraftClient.getInstance().world;
        if (world == null) return;

        BlockPos key = cameraPos.toImmutable();
        Region region = regions.get(key);
        if (region != null && region.world != world) {
            clear(key);
            region = null;
        }
        if (region == null) {
            region = new Region(key, world);
            regions.put(key, region);
        }

        WorldChunk chunk = new WorldChunk(world, new ChunkPos(chunkX, chunkZ));
        chunk.loadFromPacket(chunkData.getSectionsDataBuf(), chunkData.getHeightmap(), visitor -> {});

        Region target = region;
        target.blockEntities.keySet().removeIf(pos -> (pos.getX() >> 4) == chunkX && (pos.getZ() >> 4) == chunkZ);
        chunkData.getBlockEntities(chunkX, chunkZ).accept((pos, type, nbt) -> {
            BlockEntity blockEntity = createBlockEntity(world, chunk.getBlockState(pos), pos, type, nbt);
            if (blockEntity != null) target.blockEntities.put(blockEntity.getPos(), blockEntity);
        });

        LightingProvider provider = world.getLightingProvider();
        storeLight(region.skyLight, chunkX, chunkZ, provider, lightData.getInitedSky(),
                lightData.getUninitedSky(), lightData.getSkyNibbles().iterator());
        storeLight(region.blockLight, chunkX, chunkZ, provider, lightData.getInitedBlock(),
                lightData.getUninitedBlock(), lightData.getBlockNibbles().iterator());

        region.chunks.put(ChunkPos.toLong(chunkX, chunkZ), chunk);
        region.lastArrivalMs = Util.getMeasuringTimeMs();
        RemoteFeedRenderer.onChunkArrived(region, chunkX, chunkZ);
    }

    @Nullable
    private static BlockEntity createBlockEntity(ClientWorld world, BlockState state, BlockPos pos,
                                                 BlockEntityType<?> type, @Nullable NbtCompound nbt) {
        try {
            if (!type.supports(state)) return null;
            BlockEntity blockEntity = type.instantiate(pos.toImmutable(), state);
            if (blockEntity == null || blockEntity instanceof CameraFeedView) return null;
            if (nbt != null) blockEntity.readNbt(nbt);
            blockEntity.setWorld(world);
            return blockEntity;
        } catch (Throwable t) {
            if (!blockEntityFailureLogged) {
                blockEntityFailureLogged = true;
                CreateSecurity.LOGGER.error("Failed to mirror a remote camera block entity at {}", pos, t);
            }
            return null;
        }
    }

    private static void storeLight(Map<Long, ChunkNibbleArray> target, int chunkX, int chunkZ,
                                   LightingProvider provider, BitSet inited, BitSet uninited,
                                   Iterator<byte[]> nibbles) {
        for (int index = 0; index < provider.getHeight(); index++) {
            long sectionKey = ChunkSectionPos.asLong(chunkX, provider.getBottomY() + index, chunkZ);
            if (inited.get(index) && nibbles.hasNext()) {
                target.put(sectionKey, new ChunkNibbleArray(nibbles.next().clone()));
            } else if (uninited.get(index)) {
                target.put(sectionKey, new ChunkNibbleArray());
            }
        }
    }

    public static List<BlockUpdate> readBlockUpdates(PacketByteBuf buf) {
        int count = buf.readVarInt();
        List<BlockUpdate> updates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            BlockPos pos = buf.readBlockPos();
            BlockState state = Block.getStateFromRawId(buf.readVarInt());
            BlockEntityType<?> type = null;
            NbtCompound nbt = null;
            if (buf.readBoolean()) {
                type = Registries.BLOCK_ENTITY_TYPE.get(buf.readVarInt());
                nbt = buf.readNbt();
            }
            updates.add(new BlockUpdate(pos, state, type, nbt));
        }
        return updates;
    }

    public static List<LightUpdate> readLightUpdates(PacketByteBuf buf) {
        int count = buf.readVarInt();
        List<LightUpdate> updates = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            long section = buf.readLong();
            int skyMode = buf.readByte();
            ChunkNibbleArray sky = readNibbles(buf, skyMode);
            int blockMode = buf.readByte();
            ChunkNibbleArray block = readNibbles(buf, blockMode);
            updates.add(new LightUpdate(section, sky, block));
        }
        return updates;
    }

    @Nullable
    private static ChunkNibbleArray readNibbles(PacketByteBuf buf, int mode) {
        if (mode != 2) return null;
        byte[] bytes = new byte[2048];
        buf.readBytes(bytes);
        return new ChunkNibbleArray(bytes);
    }

    public static void applyBlockUpdates(BlockPos cameraPos, List<BlockUpdate> updates) {
        Region region = regions.get(cameraPos);
        if (region == null) return;
        Set<Long> dirty = new LinkedHashSet<>();
        Map<BlockPos, BlockState> broken = new HashMap<>();
        for (BlockUpdate update : updates) {
            BlockPos pos = update.pos();
            WorldChunk chunk = region.chunk(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) continue;
            int index = chunk.getSectionIndex(pos.getY());
            if (index < 0 || index >= chunk.getSectionArray().length) continue;
            ChunkSection section = chunk.getSection(index);
            if (section == null) continue;
            BlockState previous = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
            section.setBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15, update.state());
            if (update.state().isAir() && !previous.isAir()) broken.put(pos, previous);
            for (Map.Entry<Heightmap.Type, Heightmap> entry : chunk.getHeightmaps()) {
                entry.getValue().trackUpdate(pos.getX() & 15, pos.getY(), pos.getZ() & 15, update.state());
            }
            region.blockEntities.remove(pos);
            if (update.type() != null) {
                BlockEntity blockEntity = createBlockEntity(region.world, update.state(), pos,
                        update.type(), update.nbt());
                if (blockEntity != null) region.blockEntities.put(blockEntity.getPos(), blockEntity);
            }
            addDirtySections(dirty, pos);
        }
        if (broken.size() <= MAX_BREAK_PARTICLE_BLOCKS) {
            for (Map.Entry<BlockPos, BlockState> entry : broken.entrySet()) {
                RemoteParticles.addBlockBreak(region, entry.getKey(), entry.getValue());
            }
        }
        RemoteFeedRenderer.markSectionsDirty(region, dirty);
    }

    private static void addDirtySections(Set<Long> dirty, BlockPos pos) {
        int sectionX = ChunkSectionPos.getSectionCoord(pos.getX());
        int sectionY = ChunkSectionPos.getSectionCoord(pos.getY());
        int sectionZ = ChunkSectionPos.getSectionCoord(pos.getZ());
        int localX = pos.getX() & 15;
        int localY = pos.getY() & 15;
        int localZ = pos.getZ() & 15;
        for (int dx = localX == 0 ? -1 : 0; dx <= (localX == 15 ? 1 : 0); dx++) {
            for (int dy = localY == 0 ? -1 : 0; dy <= (localY == 15 ? 1 : 0); dy++) {
                for (int dz = localZ == 0 ? -1 : 0; dz <= (localZ == 15 ? 1 : 0); dz++) {
                    dirty.add(ChunkSectionPos.asLong(sectionX + dx, sectionY + dy, sectionZ + dz));
                }
            }
        }
    }

    public static void applyLightUpdates(BlockPos cameraPos, List<LightUpdate> updates) {
        Region region = regions.get(cameraPos);
        if (region == null) return;
        Set<Long> dirty = new LinkedHashSet<>();
        for (LightUpdate update : updates) {
            store(region.skyLight, update.section(), update.sky());
            store(region.blockLight, update.section(), update.block());
            int sectionX = ChunkSectionPos.unpackX(update.section());
            int sectionY = ChunkSectionPos.unpackY(update.section());
            int sectionZ = ChunkSectionPos.unpackZ(update.section());
            dirty.add(update.section());
            for (Direction direction : Direction.values()) {
                dirty.add(ChunkSectionPos.asLong(sectionX + direction.getOffsetX(),
                        sectionY + direction.getOffsetY(), sectionZ + direction.getOffsetZ()));
            }
        }
        RemoteFeedRenderer.markSectionsDirty(region, dirty);
    }

    private static void store(Map<Long, ChunkNibbleArray> target, long section,
                              @Nullable ChunkNibbleArray nibbles) {
        if (nibbles != null) target.put(section, nibbles);
    }

    @Nullable
    public static Region region(BlockPos cameraPos) {
        return regions.get(cameraPos);
    }

    public static void clear(BlockPos cameraPos) {
        RemoteEntities.clear(cameraPos);
        RemoteParticles.clear(cameraPos);
        Region region = regions.remove(cameraPos);
        if (region != null) RemoteFeedRenderer.release(region);
    }

    public static void clearAll() {
        RemoteEntities.clearAll();
        RemoteParticles.clearAll();
        List<Region> all = new ArrayList<>(regions.values());
        regions.clear();
        for (Region region : all) RemoteFeedRenderer.release(region);
    }

    public static int chunkCount(BlockPos cameraPos) {
        Region region = regions.get(cameraPos);
        return region == null ? 0 : region.chunkCount();
    }
}
