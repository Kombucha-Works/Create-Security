package kombuchamc.createsecurity.block.camera;

import kombuchamc.createsecurity.config.CSConfigs;
import net.minecraft.server.world.ChunkTicketType;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;

import java.util.Comparator;

public final class CameraChunkTickets {
    private CameraChunkTickets() {}

    public static final ChunkTicketType<BlockPos> CAMERA =
            ChunkTicketType.create("createsecurity_camera", Comparator.comparingLong(BlockPos::asLong));

    public static final int MAX_RADIUS = 8;
    private static final int LEGACY_RADIUS = 31;

    public static void add(ServerWorld world, BlockPos pos) {
        world.getChunkManager().addTicket(
                CAMERA, new ChunkPos(pos), CSConfigs.cameraSimulationDistance(), pos);
    }

    public static void removeAll(ServerWorld world, BlockPos pos) {
        ChunkPos chunkPos = new ChunkPos(pos);
        for (int radius = 0; radius <= MAX_RADIUS; radius++) {
            world.getChunkManager().removeTicket(CAMERA, chunkPos, radius, pos);
        }
        world.getChunkManager().removeTicket(CAMERA, chunkPos, LEGACY_RADIUS, pos);
    }
}
