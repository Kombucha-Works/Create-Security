package kombuchamc.createsecurity.block.camera;

import kombuchamc.createsecurity.block.RegisterModBlocks;
import kombuchamc.createsecurity.config.CSConfigs;
import kombuchamc.createsecurity.network.ModPackets;
import net.minecraft.entity.Entity;
import net.minecraft.network.packet.s2c.play.ParticleS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import net.minecraft.world.chunk.light.LightingProvider;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;

public final class RemoteChunkFeed {

    public static final ChunkTicketType<BlockPos> FEED =
            ChunkTicketType.create("createsecurity_feed", Comparator.comparingLong(BlockPos::asLong));

    public static final int MAX_RADIUS = 16;

    private static final int CHUNKS_PER_TICK = 4;
    private static final int ENTITY_INTERVAL = 2;
    private static final int FULL_INTERVAL = 20;
    private static final int MAX_ENTITIES = 96;
    private static final double VERTICAL_REACH = 80.0;
    private static final int VERTICAL_SECTIONS = 4;
    private static final int BLOCK_UPDATES_PER_TICK = 128;
    private static final int LIGHT_SECTIONS_PER_TICK = 4;
    private static final int PARTICLE_EVENTS_PER_TICK = 24;
    private static final int PARTICLE_QUEUE_LIMIT = 96;
    private static final int BLOCK_QUEUE_LIMIT = 2048;
    private static final int LIGHT_QUEUE_LIMIT = 256;

    private RemoteChunkFeed() {}

    private static final class Watch {
        final BlockPos camera;
        final int radius;
        final ServerWorld world;
        final Queue<ChunkPos> pending = new ArrayDeque<>();
        final Map<Integer, Long> lastFull = new HashMap<>();
        final Set<Long> sentChunks = new HashSet<>();
        final Set<BlockPos> blockChanges = new LinkedHashSet<>();
        final Set<Long> lightSections = new LinkedHashSet<>();
        final Queue<ParticleS2CPacket> particles = new ArrayDeque<>();

        Watch(BlockPos camera, int radius, ServerWorld world) {
            this.camera = camera;
            this.radius = radius;
            this.world = world;
        }
    }

    private static final Map<UUID, Map<BlockPos, Watch>> watches = new HashMap<>();
    private static final Map<BlockPos, Integer> ticketCounts = new HashMap<>();

    public static void watch(ServerPlayerEntity player, BlockPos cameraPos) {
        ServerWorld world = player.getServerWorld();
        int radius = CSConfigs.cameraFeedRadius();
        if (radius <= 0) return;
        if (!world.getBlockState(cameraPos).isOf(RegisterModBlocks.CAMERA_BLOCK)) return;

        Map<BlockPos, Watch> byCamera = watches.computeIfAbsent(player.getUuid(), id -> new HashMap<>());
        BlockPos key = cameraPos.toImmutable();
        if (byCamera.containsKey(key)) return;

        Watch watch = new Watch(key, radius, world);
        ChunkPos center = new ChunkPos(key);
        List<ChunkPos> area = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                area.add(new ChunkPos(center.x + dx, center.z + dz));
            }
        }
        area.sort(Comparator.comparingInt(pos -> Math.max(Math.abs(pos.x - center.x), Math.abs(pos.z - center.z))));
        watch.pending.addAll(area);
        byCamera.put(key, watch);
        addTicket(world, key, radius);
    }

    public static void unwatch(ServerPlayerEntity player, BlockPos cameraPos) {
        Map<BlockPos, Watch> byCamera = watches.get(player.getUuid());
        if (byCamera == null) return;
        Watch removed = byCamera.remove(cameraPos);
        if (removed == null) return;
        if (byCamera.isEmpty()) watches.remove(player.getUuid());
        removeTicket(removed.world, removed.camera, removed.radius);
        ModPackets.sendRemoteChunkClear(player, removed.camera);
    }

    public static void unwatchAll(ServerPlayerEntity player) {
        Map<BlockPos, Watch> byCamera = watches.remove(player.getUuid());
        if (byCamera == null) return;
        for (Watch watch : byCamera.values()) {
            removeTicket(watch.world, watch.camera, watch.radius);
        }
    }

    public static void tick(ServerWorld world) {
        if (watches.isEmpty()) return;
        List<UUID> gone = new ArrayList<>();
        for (Map.Entry<UUID, Map<BlockPos, Watch>> entry : watches.entrySet()) {
            ServerPlayerEntity player = world.getServer().getPlayerManager().getPlayer(entry.getKey());
            if (player == null) {
                gone.add(entry.getKey());
                continue;
            }
            if (player.getServerWorld() != world) continue;
            if (world.getTime() % ENTITY_INTERVAL == 0) {
                for (Watch watch : entry.getValue().values()) sendEntities(world, player, watch);
            }
            for (Watch watch : entry.getValue().values()) flushChanges(player, watch);
            int budget = CHUNKS_PER_TICK;
            Iterator<Watch> it = entry.getValue().values().iterator();
            while (it.hasNext() && budget > 0) {
                Watch watch = it.next();
                int attempts = watch.pending.size();
                while (budget > 0 && attempts-- > 0) {
                    ChunkPos pos = watch.pending.poll();
                    WorldChunk chunk = world.getChunkManager().getWorldChunk(pos.x, pos.z, false);
                    if (chunk == null) {
                        watch.pending.add(pos);
                        continue;
                    }
                    ModPackets.sendRemoteChunk(player, watch.camera, chunk);
                    watch.sentChunks.add(pos.toLong());
                    budget--;
                }
            }
        }
        for (UUID id : gone) {
            Map<BlockPos, Watch> byCamera = watches.remove(id);
            if (byCamera == null) continue;
            for (Watch watch : byCamera.values()) removeTicket(watch.world, watch.camera, watch.radius);
        }
    }

    public static void onParticles(ServerWorld world, ParticleEffect effect, double x, double y, double z,
                                   int count, double deltaX, double deltaY, double deltaZ, double speed) {
        if (watches.isEmpty()) return;
        ParticleS2CPacket packet = null;
        for (Map<BlockPos, Watch> byCamera : watches.values()) {
            for (Watch watch : byCamera.values()) {
                if (watch.world != world) continue;
                if (watch.particles.size() >= PARTICLE_QUEUE_LIMIT) continue;
                if (!inReach(watch, x, y, z)) continue;
                if (packet == null) {
                    packet = new ParticleS2CPacket(effect, false, x, y, z, (float) deltaX, (float) deltaY,
                            (float) deltaZ, (float) speed, count);
                }
                watch.particles.add(packet);
            }
        }
    }

    private static boolean inReach(Watch watch, double x, double y, double z) {
        double reach = (watch.radius + 1) * 16.0;
        Vec3d center = Vec3d.ofCenter(watch.camera);
        return Math.abs(x - center.x) <= reach && Math.abs(z - center.z) <= reach
                && Math.abs(y - center.y) <= VERTICAL_REACH;
    }

    public static void onBlockChanged(ServerWorld world, BlockPos pos) {
        if (watches.isEmpty()) return;
        long chunkKey = ChunkPos.toLong(pos.getX() >> 4, pos.getZ() >> 4);
        int sectionY = ChunkSectionPos.getSectionCoord(pos.getY());
        for (Map<BlockPos, Watch> byCamera : watches.values()) {
            for (Watch watch : byCamera.values()) {
                if (watch.world != world) continue;
                if (!watch.sentChunks.contains(chunkKey)) continue;
                if (Math.abs(sectionY - ChunkSectionPos.getSectionCoord(watch.camera.getY())) > VERTICAL_SECTIONS) {
                    continue;
                }
                if (watch.blockChanges.size() < BLOCK_QUEUE_LIMIT) watch.blockChanges.add(pos.toImmutable());
            }
        }
    }

    public static void onLightFlush(ServerWorld world, ChunkPos chunkPos, LightingProvider provider,
                                    BitSet skyBits, BitSet blockBits) {
        if (watches.isEmpty()) return;
        long chunkKey = chunkPos.toLong();
        for (Map<BlockPos, Watch> byCamera : watches.values()) {
            for (Watch watch : byCamera.values()) {
                if (watch.world != world) continue;
                if (!watch.sentChunks.contains(chunkKey)) continue;
                int cameraSection = ChunkSectionPos.getSectionCoord(watch.camera.getY());
                markLight(watch, chunkPos, provider, skyBits, cameraSection);
                markLight(watch, chunkPos, provider, blockBits, cameraSection);
            }
        }
    }

    private static void markLight(Watch watch, ChunkPos chunkPos, LightingProvider provider, BitSet bits,
                                  int cameraSection) {
        for (int index = bits.nextSetBit(0); index >= 0; index = bits.nextSetBit(index + 1)) {
            int sectionY = provider.getBottomY() + index;
            if (Math.abs(sectionY - cameraSection) > VERTICAL_SECTIONS) continue;
            if (watch.lightSections.size() >= LIGHT_QUEUE_LIMIT) return;
            watch.lightSections.add(ChunkSectionPos.asLong(chunkPos.x, sectionY, chunkPos.z));
        }
    }

    private static void flushChanges(ServerPlayerEntity player, Watch watch) {
        ServerWorld world = watch.world;
        if (!watch.particles.isEmpty()) {
            List<ParticleS2CPacket> events =
                    new ArrayList<>(Math.min(watch.particles.size(), PARTICLE_EVENTS_PER_TICK));
            while (!watch.particles.isEmpty() && events.size() < PARTICLE_EVENTS_PER_TICK) {
                events.add(watch.particles.poll());
            }
            ModPackets.sendRemoteParticles(player, watch.camera, events);
        }
        if (!watch.blockChanges.isEmpty()) {
            List<BlockPos> batch = new ArrayList<>(Math.min(watch.blockChanges.size(), BLOCK_UPDATES_PER_TICK));
            Iterator<BlockPos> it = watch.blockChanges.iterator();
            while (it.hasNext() && batch.size() < BLOCK_UPDATES_PER_TICK) {
                batch.add(it.next());
                it.remove();
            }
            ModPackets.sendRemoteBlockUpdates(player, watch.camera, world, batch);
        }
        if (watch.lightSections.isEmpty()) return;
        List<Long> sections = new ArrayList<>(LIGHT_SECTIONS_PER_TICK);
        Iterator<Long> lights = watch.lightSections.iterator();
        while (lights.hasNext() && sections.size() < LIGHT_SECTIONS_PER_TICK) {
            sections.add(lights.next());
            lights.remove();
        }
        ModPackets.sendRemoteLight(player, watch.camera, world, sections);
    }

    private static void sendEntities(ServerWorld world, ServerPlayerEntity viewer, Watch watch) {
        double reach = (watch.radius + 1) * 16.0;
        Vec3d center = Vec3d.ofCenter(watch.camera);
        Box box = new Box(center.x - reach, center.y - VERTICAL_REACH, center.z - reach,
                center.x + reach, center.y + VERTICAL_REACH, center.z + reach);
        List<Entity> found = world.getOtherEntities(viewer, box,
                entity -> !entity.isRemoved() && !entity.isSpectator());
        found.sort(Comparator.comparingDouble(entity -> entity.squaredDistanceTo(center)));
        List<Entity> nearest = found.size() > MAX_ENTITIES ? new ArrayList<>(found.subList(0, MAX_ENTITIES)) : found;

        long now = world.getTime();
        Set<Integer> present = new HashSet<>();
        ModPackets.sendRemoteEntities(viewer, watch.camera, nearest, entity -> {
            present.add(entity.getId());
            Long last = watch.lastFull.get(entity.getId());
            if (last != null && now - last < FULL_INTERVAL) return false;
            watch.lastFull.put(entity.getId(), now);
            return true;
        });
        watch.lastFull.keySet().retainAll(present);
    }

    private static void addTicket(ServerWorld world, BlockPos cameraPos, int radius) {
        int count = ticketCounts.merge(cameraPos, 1, Integer::sum);
        if (count == 1) {
            world.getChunkManager().addTicket(FEED, new ChunkPos(cameraPos), radius, cameraPos);
        }
    }

    private static void removeTicket(ServerWorld world, BlockPos cameraPos, int radius) {
        Integer count = ticketCounts.get(cameraPos);
        if (count == null) return;
        if (count <= 1) {
            ticketCounts.remove(cameraPos);
            world.getChunkManager().removeTicket(FEED, new ChunkPos(cameraPos), radius, cameraPos);
        } else {
            ticketCounts.put(cameraPos, count - 1);
        }
    }
}
