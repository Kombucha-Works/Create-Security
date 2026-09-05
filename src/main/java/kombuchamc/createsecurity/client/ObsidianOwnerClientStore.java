package kombuchamc.createsecurity.client;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Environment(EnvType.CLIENT)
public final class ObsidianOwnerClientStore {

    private static final Map<BlockPos, Boolean> blocks = new HashMap<>();
    private static boolean bypass;

    private ObsidianOwnerClientStore() {}

    public static void replaceAll(List<BlockPos> positions, List<Boolean> mine, boolean canBypass) {
        blocks.clear();
        bypass = canBypass;
        for (int i = 0; i < positions.size(); i++) {
            blocks.put(positions.get(i), mine.get(i));
        }
    }

    public static void set(BlockPos pos, boolean present, boolean mine) {
        if (present) {
            blocks.put(pos.toImmutable(), mine);
        } else {
            blocks.remove(pos);
        }
    }

    public static void clear() {
        blocks.clear();
        bypass = false;
    }

    public static boolean canBreak(BlockPos pos) {
        if (bypass) return true;
        Boolean mine = blocks.get(pos);
        return mine == null || mine;
    }
}
