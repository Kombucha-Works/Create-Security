package kombuchamc.createsecurity.block.obsidian;

import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtElement;
import net.minecraft.nbt.NbtList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.PersistentState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ObsidianOwnerStore extends PersistentState {

    private static final String ID = "createsecurity_obsidian_owners";
    private static final String NBT_ENTRIES = "entries";
    private static final String NBT_POS = "pos";
    private static final String NBT_OWNER = "owner";

    private final Map<BlockPos, UUID> owners = new HashMap<>();

    public static ObsidianOwnerStore get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                ObsidianOwnerStore::fromNbt, ObsidianOwnerStore::new, ID);
    }

    public void set(BlockPos pos, UUID owner) {
        owners.put(pos.toImmutable(), owner);
        markDirty();
    }

    @Nullable
    public UUID get(BlockPos pos) {
        return owners.get(pos);
    }

    public boolean remove(BlockPos pos) {
        if (owners.remove(pos) == null) return false;
        markDirty();
        return true;
    }

    public List<BlockPos> allPositions() {
        return new ArrayList<>(owners.keySet());
    }

    public List<BlockPos> ownedBy(UUID owner) {
        List<BlockPos> owned = new ArrayList<>();
        for (Map.Entry<BlockPos, UUID> entry : owners.entrySet()) {
            if (entry.getValue().equals(owner)) owned.add(entry.getKey());
        }
        return owned;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Map.Entry<BlockPos, UUID> entry : owners.entrySet()) {
            NbtCompound tag = new NbtCompound();
            tag.putLong(NBT_POS, entry.getKey().asLong());
            tag.putUuid(NBT_OWNER, entry.getValue());
            list.add(tag);
        }
        nbt.put(NBT_ENTRIES, list);
        return nbt;
    }

    public static ObsidianOwnerStore fromNbt(NbtCompound nbt) {
        ObsidianOwnerStore store = new ObsidianOwnerStore();
        NbtList list = nbt.getList(NBT_ENTRIES, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            if (!tag.containsUuid(NBT_OWNER)) continue;
            store.owners.put(BlockPos.fromLong(tag.getLong(NBT_POS)), tag.getUuid(NBT_OWNER));
        }
        return store;
    }
}
