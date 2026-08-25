package kombuchamc.createsecurity.block.camera;

import kombuchamc.createsecurity.mixin.BuiltChunkStorageAccessor;
import kombuchamc.createsecurity.mixin.WorldRendererAccessor;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.render.chunk.ChunkBuilder;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Util;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public final class CameraChunkVisibility {

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final long REFRESH_MS = 500;

    public static int feedViewDistance() {
        return kombuchamc.createsecurity.config.CSConfigs.cameraRenderDistance();
    }

    private record Result(List<Object> chunkInfos, ChunkBuilder.BuiltChunk[] storageIdentity, long timestamp) {}

    private static final Map<BlockPos, Result> results = new ConcurrentHashMap<>();
    private static final Set<BlockPos> inFlight = ConcurrentHashMap.newKeySet();

    @Nullable
    public static List<Object> getChunkInfos(WorldRenderer wr, BlockPos cameraPos) {
        Result r = results.get(cameraPos);
        if (r == null) return null;
        ChunkBuilder.BuiltChunk[] current = ((WorldRendererAccessor) wr).getChunkStorage().chunks;
        if (r.storageIdentity != current) return null;
        return r.chunkInfos;
    }

    public static void requestUpdate(WorldRenderer wr, BlockPos cameraPos) {
        BuiltChunkStorage storage = ((WorldRendererAccessor) wr).getChunkStorage();
        Result r = results.get(cameraPos);
        long now = System.currentTimeMillis();
        if (r != null && now - r.timestamp < REFRESH_MS && r.storageIdentity == storage.chunks) return;
        BlockPos key = cameraPos.toImmutable();
        if (!inFlight.add(key)) return;
        Util.getMainWorkerExecutor().execute(() -> {
            try {
                ChunkBuilder.BuiltChunk[] identity = ((WorldRendererAccessor) wr).getChunkStorage().chunks;
                List<Object> infos = computeBfs(wr, key);
                if (infos != null) {
                    results.put(key, new Result(infos, identity, System.currentTimeMillis()));
                }
            } catch (Throwable ignored) {

            } finally {
                inFlight.remove(key);
            }
        });
    }

    public static void cleanup(BlockPos cameraPos) {
        results.remove(cameraPos);
    }

    private static final class Node {
        final ChunkBuilder.BuiltChunk chunk;
        byte directions;
        byte cullingState;

        Node(ChunkBuilder.BuiltChunk chunk, @Nullable Direction direction) {
            this.chunk = chunk;
            if (direction != null) addDirection(direction);
        }

        void addDirection(Direction d) {
            directions = (byte) (directions | (1 << d.ordinal()));
        }

        boolean hasDirection(int ordinal) {
            return (directions & (1 << ordinal)) > 0;
        }

        boolean hasAnyDirection() {
            return directions != 0;
        }

        void updateCullingState(byte parent, Direction from) {
            cullingState = (byte) (cullingState | parent | (1 << from.ordinal()));
        }

        boolean canCull(Direction from) {
            return (cullingState & (1 << from.ordinal())) > 0;
        }
    }

    @Nullable
    private static List<Object> computeBfs(WorldRenderer wr, BlockPos cameraPos) {
        MinecraftClient client = MinecraftClient.getInstance();
        ClientWorld world = client.world;
        if (world == null) return null;

        WorldRendererAccessor acc = (WorldRendererAccessor) wr;
        BuiltChunkStorage storage = acc.getChunkStorage();
        BuiltChunkStorageAccessor storageAcc = (BuiltChunkStorageAccessor) storage;
        int viewDistance = Math.min(acc.getViewDistanceField(), feedViewDistance());
        boolean culling = client.chunkCullingEnabled;

        Queue<Node> queue = new ArrayDeque<>();
        Map<ChunkBuilder.BuiltChunk, Node> seen = new IdentityHashMap<>();

        ChunkBuilder.BuiltChunk start = storageAcc.invokeGetRenderedChunk(cameraPos);
        if (start != null) {
            Node n = new Node(start, null);
            queue.add(n);
            seen.put(start, n);
        }

        List<Node> out = new ArrayList<>();
        while (!queue.isEmpty()) {
            Node node = queue.poll();
            out.add(node);

            for (Direction direction : DIRECTIONS) {
                ChunkBuilder.BuiltChunk adjacent = getAdjacentChunk(
                        storageAcc, world, viewDistance, cameraPos, node.chunk, direction);
                if (adjacent == null) continue;
                if (culling && node.canCull(direction.getOpposite())) continue;

                if (culling && node.hasAnyDirection()) {
                    ChunkBuilder.ChunkData data = node.chunk.getData();
                    boolean visible = false;
                    for (int j = 0; j < DIRECTIONS.length; j++) {
                        if (node.hasDirection(j)
                                && data.isVisibleThrough(DIRECTIONS[j].getOpposite(), direction)) {
                            visible = true;
                            break;
                        }
                    }
                    if (!visible) continue;
                }

                Node existing = seen.get(adjacent);
                if (existing != null) {
                    existing.addDirection(direction);
                } else if (adjacent.shouldBuild()) {
                    Node next = new Node(adjacent, direction);
                    next.updateCullingState(node.cullingState, direction);
                    queue.add(next);
                    seen.put(adjacent, next);
                }
            }
        }

        java.lang.reflect.Constructor<?> ctor = chunkInfoConstructor();
        List<Object> infos = new ArrayList<>(out.size());
        try {
            for (Node n : out) {
                infos.add(ctor.newInstance(n.chunk, null, 0));
            }
        } catch (ReflectiveOperationException e) {
            return null;
        }
        return infos;
    }

    private static volatile java.lang.reflect.Constructor<?> chunkInfoCtor;

    private static java.lang.reflect.Constructor<?> chunkInfoConstructor() {
        java.lang.reflect.Constructor<?> c = chunkInfoCtor;
        if (c != null) return c;
        for (Class<?> inner : WorldRenderer.class.getDeclaredClasses()) {
            for (java.lang.reflect.Constructor<?> candidate : inner.getDeclaredConstructors()) {
                Class<?>[] params = candidate.getParameterTypes();
                if (params.length == 3
                        && params[0] == ChunkBuilder.BuiltChunk.class
                        && params[1] == Direction.class
                        && params[2] == int.class) {
                    candidate.setAccessible(true);
                    chunkInfoCtor = candidate;
                    return candidate;
                }
            }
        }
        throw new IllegalStateException(
                "create-security: could not locate WorldRenderer.ChunkInfo(BuiltChunk, Direction, int) constructor");
    }

    @Nullable
    private static ChunkBuilder.BuiltChunk getAdjacentChunk(BuiltChunkStorageAccessor storage,
                                                            ClientWorld world, int viewDistance,
                                                            BlockPos cameraPos,
                                                            ChunkBuilder.BuiltChunk chunk,
                                                            Direction direction) {
        BlockPos np = chunk.getNeighborPosition(direction);
        if (MathHelper.abs(cameraPos.getX() - np.getX()) > viewDistance * 16) return null;
        if (MathHelper.abs(cameraPos.getY() - np.getY()) > viewDistance * 16
                || np.getY() < world.getBottomY() || np.getY() >= world.getTopY()) return null;
        if (MathHelper.abs(cameraPos.getZ() - np.getZ()) > viewDistance * 16) return null;
        return storage.invokeGetRenderedChunk(np);
    }
}

