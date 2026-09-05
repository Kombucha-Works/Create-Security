package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.network.ModPackets;
import net.minecraft.block.BlockState;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

public final class KeycardReaderLights {

    private static final Map<RegistryKey<World>, Map<BlockPos, UUID>> open = new HashMap<>();

    private KeycardReaderLights() {}

    public static void opened(ServerWorld world, BlockPos lockPos, ServerPlayerEntity player) {
        open.computeIfAbsent(world.getRegistryKey(), key -> new HashMap<>())
                .put(lockPos.toImmutable(), player.getUuid());
        broadcast(world, lockPos, true);
    }

    public static boolean isUnlocked(ServerWorld world, BlockPos pos) {
        Map<BlockPos, UUID> lit = open.get(world.getRegistryKey());
        return lit != null && lit.containsKey(pos);
    }

    public static void tick(ServerWorld world) {
        Map<BlockPos, UUID> lit = open.get(world.getRegistryKey());
        if (lit == null || lit.isEmpty()) return;

        Iterator<Map.Entry<BlockPos, UUID>> it = lit.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, UUID> entry = it.next();
            if (stillOpen(world, entry.getKey(), entry.getValue())) continue;
            it.remove();
            broadcast(world, entry.getKey(), false);
        }
    }

    private static boolean stillOpen(ServerWorld world, BlockPos pos, UUID viewerId) {
        if (!world.isChunkLoaded(pos)) return false;

        BlockState state = world.getBlockState(pos);
        if (KeycardReaderDoors.isDoor(state)) {
            return state.contains(Properties.OPEN) && state.get(Properties.OPEN);
        }

        ServerPlayerEntity viewer = world.getServer().getPlayerManager().getPlayer(viewerId);
        return viewer != null && viewer.currentScreenHandler != viewer.playerScreenHandler;
    }

    private static void broadcast(ServerWorld world, BlockPos pos, boolean unlocked) {
        KeycardReaderStore.Entry entry = KeycardReaderStore.get(world).get(pos);
        if (entry == null) return;
        ModPackets.broadcastKeycardReaderUpdate(world, pos, true, entry.isArmed(), entry.kind(), unlocked);
    }
}
