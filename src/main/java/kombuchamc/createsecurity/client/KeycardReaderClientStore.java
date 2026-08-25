package kombuchamc.createsecurity.client;

import kombuchamc.createsecurity.block.KeycardReaderGeometry;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.BlockView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

@Environment(EnvType.CLIENT)
public final class KeycardReaderClientStore {

    private static final Map<BlockPos, Boolean> readers = new HashMap<>();

    private KeycardReaderClientStore() {}

    public static void replaceAll(List<BlockPos> positions, List<Boolean> armed) {
        readers.clear();
        for (int i = 0; i < positions.size(); i++) {
            readers.put(positions.get(i), armed.get(i));
        }
    }

    public static void set(BlockPos pos, boolean present, boolean armed) {
        if (present) {
            readers.put(pos.toImmutable(), armed);
        } else {
            readers.remove(pos);
        }
    }

    public static void clear() {
        readers.clear();
    }

    public static boolean has(BlockPos pos) {
        return readers.containsKey(pos);
    }

    public static boolean isArmedGroup(BlockView world, BlockPos pos) {
        BlockPos host = KeycardReaderGeometry.hostPos(world, pos);
        Boolean armed = readers.get(host);
        if (armed == null) {
            BlockPos partner = KeycardReaderGeometry.partner(world, host);
            armed = partner == null ? null : readers.get(partner);
        }
        return armed != null && armed;
    }

    public static boolean hasGroup(BlockView world, BlockPos pos) {
        BlockPos host = KeycardReaderGeometry.hostPos(world, pos);
        if (has(host)) return true;
        BlockPos partner = KeycardReaderGeometry.partner(world, host);
        return partner != null && has(partner);
    }

    public static boolean isEmpty() {
        return readers.isEmpty();
    }

    public static void forEachNear(Vec3d center, double range, BiConsumer<BlockPos, Boolean> action) {
        double rangeSq = range * range;
        for (Map.Entry<BlockPos, Boolean> entry : new ArrayList<>(readers.entrySet())) {
            if (entry.getKey().getSquaredDistance(center) > rangeSq) continue;
            action.accept(entry.getKey(), entry.getValue());
        }
    }
}
