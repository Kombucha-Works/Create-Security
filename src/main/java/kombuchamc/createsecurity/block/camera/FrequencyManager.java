package kombuchamc.createsecurity.block.camera;

import kombuchamc.createsecurity.block.entity.CameraBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.*;

public class FrequencyManager {

    static final Map<String, Set<BlockPos>> camerasByFrequency = new HashMap<>();
    static final Map<BlockPos, String> frequencyByCamera = new HashMap<>();

    static final Map<BlockPos, Long> placedAtByCamera = new HashMap<>();

    private static long revision = 0;

    public static long getRevision() {
        return revision;
    }

    public static String computeFrequency(ItemStack slot1, ItemStack slot2) {
        if (slot1.isEmpty() || slot2.isEmpty()) return null;

        String id1 = Registries.ITEM.getId(slot1.getItem()).toString();
        String id2 = Registries.ITEM.getId(slot2.getItem()).toString();

        List<String> ids = Arrays.asList(id1, id2);
        Collections.sort(ids);

        return ids.get(0) + ":" + ids.get(1);
    }

    public static void updateCamera(BlockPos pos, ItemStack slot1, ItemStack slot2, long placedAt) {
        placedAtByCamera.put(pos.toImmutable(), placedAt);

        String newFreq = computeFrequency(slot1, slot2);
        String oldFreq = frequencyByCamera.get(pos);
        boolean changed = false;

        if (oldFreq != null && !oldFreq.equals(newFreq)) {
            camerasByFrequency.getOrDefault(oldFreq, Collections.emptySet()).remove(pos);
            changed = true;
        }

        if (newFreq == null) {
            changed |= frequencyByCamera.remove(pos) != null;
        } else {
            changed |= !newFreq.equals(oldFreq);
            frequencyByCamera.put(pos, newFreq);
            changed |= camerasByFrequency.computeIfAbsent(newFreq, k -> new HashSet<>()).add(pos);
        }
        if (changed) revision++;
    }

    public static void removeCamera(BlockPos pos) {
        placedAtByCamera.remove(pos);
        String freq = frequencyByCamera.remove(pos);
        if (freq != null) {
            camerasByFrequency.getOrDefault(freq, Collections.emptySet()).remove(pos);
            revision++;
        }
    }

    public static Set<BlockPos> getCamerasOnFrequency(String frequency) {
        return camerasByFrequency.getOrDefault(frequency, Collections.emptySet());
    }

    public static List<BlockPos> sortedCameras(World world, String frequency) {
        List<BlockPos> sorted = new ArrayList<>(getCamerasOnFrequency(frequency));
        adoptLegacyPlacementTimes(world, sorted);
        sorted.sort(Comparator.comparingLong(FrequencyManager::placedAt)
                .thenComparingLong(BlockPos::asLong));
        return sorted;
    }

    static long placedAt(BlockPos pos) {
        return placedAtByCamera.getOrDefault(pos, Long.MAX_VALUE);
    }

    private static void adoptLegacyPlacementTimes(World world, List<BlockPos> cameras) {
        if (world == null) return;
        boolean adopted = false;
        for (BlockPos pos : cameras) {
            if (placedAtByCamera.containsKey(pos)) continue;
            if (world.getBlockEntity(pos) instanceof CameraBlockEntity camera) {
                placedAtByCamera.put(pos.toImmutable(), camera.getPlacedAt());
                adopted = true;
            }
        }
        if (adopted && world instanceof ServerWorld serverWorld) {
            markDirty(serverWorld);
        }
    }

    public static String getFrequency(BlockPos pos) {
        return frequencyByCamera.get(pos);
    }

    public static void markDirty(ServerWorld world) {
        kombuchamc.createsecurity.block.camera.FrequencyPersistentState.getOrCreate(world).markDirty();
    }
}
