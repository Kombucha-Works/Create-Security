package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.network.ModPackets;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class KeycardReaderAttachment {

    private static final String NBT_ROOT = "CreateSecurityReader";
    private static final String NBT_OWNER = "owner";
    private static final String NBT_CARD = "card";
    private static final String NBT_KIND = "kind";

    private static final Map<BlockPos, KeycardReaderStore.Entry> pending = new HashMap<>();

    private KeycardReaderAttachment() {}

    public static void detach(ServerWorld world, BlockPos pos) {
        KeycardReaderStore.Entry entry = KeycardReaderStore.get(world).remove(pos);
        if (entry == null) return;
        ModPackets.broadcastKeycardReaderUpdate(world, pos, false, false, entry.kind());
        pending.put(pos.toImmutable(), entry);
    }

    @Nullable
    public static KeycardReaderStore.Entry claim(BlockPos pos) {
        return pending.remove(pos);
    }

    public static void clearPending() {
        pending.clear();
    }

    public static void write(ItemStack stack, KeycardReaderStore.Entry entry) {
        NbtCompound tag = new NbtCompound();
        if (entry.owner() != null) tag.putUuid(NBT_OWNER, entry.owner());
        if (entry.secret() != 0) tag.putInt(NBT_CARD, entry.secret());
        if (entry.kind() != LockKind.KEYCARD) tag.putInt(NBT_KIND, entry.kind().ordinal());
        stack.getOrCreateNbt().put(NBT_ROOT, tag);
    }

    public static boolean has(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        return nbt != null && nbt.contains(NBT_ROOT, NbtCompound.COMPOUND_TYPE);
    }

    public static LockKind kindOf(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_ROOT, NbtCompound.COMPOUND_TYPE)) return LockKind.KEYCARD;
        return LockKind.byId(nbt.getCompound(NBT_ROOT).getInt(NBT_KIND));
    }

    public static boolean isArmed(ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_ROOT, NbtCompound.COMPOUND_TYPE)) return false;
        return nbt.getCompound(NBT_ROOT).getInt(NBT_CARD) != 0;
    }

    public static boolean isLockedShulkerBox(ItemStack stack) {
        return stack.getItem() instanceof net.minecraft.item.BlockItem blockItem
                && blockItem.getBlock() instanceof net.minecraft.block.ShulkerBoxBlock
                && has(stack);
    }

    public static void restore(ServerWorld world, BlockPos pos, ItemStack stack) {
        NbtCompound nbt = stack.getNbt();
        if (nbt == null || !nbt.contains(NBT_ROOT, NbtCompound.COMPOUND_TYPE)) return;
        NbtCompound tag = nbt.getCompound(NBT_ROOT);

        KeycardReaderStore store = KeycardReaderStore.get(world);
        if (store.has(pos)) return;
        UUID owner = tag.containsUuid(NBT_OWNER) ? tag.getUuid(NBT_OWNER) : null;
        int card = tag.getInt(NBT_CARD);
        LockKind kind = LockKind.byId(tag.getInt(NBT_KIND));
        store.add(pos, owner, kind);
        if (card != 0) store.setSecret(pos, card);
        ModPackets.broadcastKeycardReaderUpdate(world, pos, true, card != 0, kind);
    }
}
