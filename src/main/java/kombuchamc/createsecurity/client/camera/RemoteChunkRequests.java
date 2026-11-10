package kombuchamc.createsecurity.client.camera;

import kombuchamc.createsecurity.block.camera.CameraViewManager;
import kombuchamc.createsecurity.network.ModPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class RemoteChunkRequests {

    private static final Set<BlockPos> watched = new HashSet<>();
    private static Object lastWorld;

    private RemoteChunkRequests() {}

    public static void sync(Set<BlockPos> linkedCameras) {
        Object world = net.minecraft.client.MinecraftClient.getInstance().world;
        if (world != lastWorld) {
            lastWorld = world;
            reset();
        }
        Iterator<BlockPos> it = watched.iterator();
        while (it.hasNext()) {
            BlockPos cameraPos = it.next();
            if (linkedCameras.contains(cameraPos) && !CameraViewManager.isCameraInClientRange(cameraPos)) {
                continue;
            }
            it.remove();
            RemoteChunkStore.clear(cameraPos);
            ModPackets.sendRemoteChunkWatch(cameraPos, false);
        }
        for (BlockPos cameraPos : linkedCameras) {
            if (CameraViewManager.isCameraInClientRange(cameraPos)) continue;
            if (!watched.add(cameraPos.toImmutable())) continue;
            ModPackets.sendRemoteChunkWatch(cameraPos, true);
        }
    }

    public static void reset() {
        watched.clear();
        RemoteChunkStore.clearAll();
    }

    public static boolean isWatching(BlockPos cameraPos) {
        return watched.contains(cameraPos);
    }
}
