package kombuchamc.createsecurity.block.camera;

import kombuchamc.createsecurity.block.camera.FrequencyManager;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.world.PersistentState;
import net.minecraft.util.math.BlockPos;

public class FrequencyPersistentState extends PersistentState {

    private static final String ID = "createsecurity_frequencies";

    public static FrequencyPersistentState getOrCreate(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                nbt -> fromNbt(nbt),
                FrequencyPersistentState::new,
                ID
        );
    }
    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();

        FrequencyManager.frequencyByCamera.forEach((pos, freq) -> {
            NbtCompound entry = new NbtCompound();
            entry.putLong("pos", pos.asLong());
            entry.putString("freq", freq);
            Long placedAt = FrequencyManager.placedAtByCamera.get(pos);
            if (placedAt != null) {
                entry.putLong("placedAt", placedAt);
            }
            list.add(entry);
        });

        nbt.put("entries", list);
        return nbt;
    }

    public static FrequencyPersistentState fromNbt(NbtCompound nbt) {
        FrequencyPersistentState state = new FrequencyPersistentState();
        NbtList list = nbt.getList("entries", 10);

        for (int i = 0; i < list.size(); i++) {
            NbtCompound entry = list.getCompound(i);
            BlockPos pos = BlockPos.fromLong(entry.getLong("pos"));
            String freq = entry.getString("freq");

            FrequencyManager.frequencyByCamera.put(pos, freq);
            FrequencyManager.camerasByFrequency
                    .computeIfAbsent(freq, k -> new java.util.HashSet<>())
                    .add(pos);

            if (entry.contains("placedAt")) {
                FrequencyManager.placedAtByCamera.put(pos, entry.getLong("placedAt"));
            }
        }

        return state;
    }
}
