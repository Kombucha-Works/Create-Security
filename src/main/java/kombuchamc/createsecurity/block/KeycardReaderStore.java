package kombuchamc.createsecurity.block;

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

public class KeycardReaderStore extends PersistentState {

    private static final String ID = "createsecurity_keycard_readers";
    private static final String NBT_ENTRIES = "entries";
    private static final String NBT_POS = "pos";
    private static final String NBT_OWNER = "owner";
    private static final String NBT_CARD = "card";

    public record Entry(BlockPos pos, @Nullable UUID owner, int cardId) {

        public boolean isArmed() {
            return cardId != 0;
        }
    }

    private final Map<BlockPos, Entry> readers = new HashMap<>();

    public static KeycardReaderStore get(ServerWorld world) {
        return world.getPersistentStateManager().getOrCreate(
                KeycardReaderStore::fromNbt, KeycardReaderStore::new, ID);
    }

    public boolean has(BlockPos pos) {
        return readers.containsKey(pos);
    }

    @Nullable
    public Entry get(BlockPos pos) {
        return readers.get(pos);
    }

    public List<Entry> all() {
        return new ArrayList<>(readers.values());
    }

    public void add(BlockPos pos, @Nullable UUID owner) {
        BlockPos key = pos.toImmutable();
        readers.put(key, new Entry(key, owner, 0));
        markDirty();
    }

    public void setCard(BlockPos pos, int cardId) {
        Entry entry = readers.get(pos);
        if (entry == null) return;
        readers.put(entry.pos(), new Entry(entry.pos(), entry.owner(), cardId));
        markDirty();
    }

    public boolean isArmed(BlockPos pos) {
        Entry entry = readers.get(pos);
        return entry != null && entry.isArmed();
    }

    @Nullable
    public Entry remove(BlockPos pos) {
        Entry removed = readers.remove(pos);
        if (removed != null) markDirty();
        return removed;
    }

    @Override
    public NbtCompound writeNbt(NbtCompound nbt) {
        NbtList list = new NbtList();
        for (Entry entry : readers.values()) {
            NbtCompound tag = new NbtCompound();
            tag.putLong(NBT_POS, entry.pos().asLong());
            if (entry.owner() != null) tag.putUuid(NBT_OWNER, entry.owner());
            if (entry.cardId() != 0) tag.putInt(NBT_CARD, entry.cardId());
            list.add(tag);
        }
        nbt.put(NBT_ENTRIES, list);
        return nbt;
    }

    public static KeycardReaderStore fromNbt(NbtCompound nbt) {
        KeycardReaderStore store = new KeycardReaderStore();
        NbtList list = nbt.getList(NBT_ENTRIES, NbtElement.COMPOUND_TYPE);
        for (int i = 0; i < list.size(); i++) {
            NbtCompound tag = list.getCompound(i);
            BlockPos pos = BlockPos.fromLong(tag.getLong(NBT_POS));
            UUID owner = tag.containsUuid(NBT_OWNER) ? tag.getUuid(NBT_OWNER) : null;
            store.readers.put(pos, new Entry(pos, owner, tag.getInt(NBT_CARD)));
        }
        return store;
    }
}
