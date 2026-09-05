package kombuchamc.createsecurity.block.obsidian;

import kombuchamc.createsecurity.config.CSConfigs;
import kombuchamc.createsecurity.network.ModPackets;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.function.Predicate;

public final class ObsidianProtection {

    @Nullable
    public static Predicate<BlockPos> CLIENT_OWNERSHIP;

    private ObsidianProtection() {}

    public static BlockPos key(BlockState state, BlockPos pos) {
        if (state.getBlock() instanceof DoorBlock && state.contains(DoorBlock.HALF)
                && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.down();
        }
        return pos.toImmutable();
    }

    public static void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        if (!(placer instanceof ServerPlayerEntity player)) return;
        BlockPos key = key(state, pos);
        ObsidianOwnerStore.get(serverWorld).set(key, player.getUuid());
        ModPackets.broadcastObsidianOwner(serverWorld, key, true, player.getUuid());
    }

    public static void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState) {
        if (state.isOf(newState.getBlock())) return;
        if (!(world instanceof ServerWorld serverWorld)) return;
        BlockPos key = key(state, pos);
        if (world.getBlockState(key).getBlock() == state.getBlock()) return;
        if (ObsidianOwnerStore.get(serverWorld).remove(key)) {
            ModPackets.broadcastObsidianOwner(serverWorld, key, false, null);
        }
    }

    public static boolean canBreak(BlockState state, PlayerEntity player, BlockPos pos) {
        BlockPos key = key(state, pos);
        World world = player.getWorld();
        if (world.isClient) {
            return CLIENT_OWNERSHIP == null || CLIENT_OWNERSHIP.test(key);
        }
        if (!(world instanceof ServerWorld serverWorld)) return true;
        if (CSConfigs.canBypassOwner(player)) return true;
        UUID owner = ObsidianOwnerStore.get(serverWorld).get(key);
        return owner == null || owner.equals(player.getUuid());
    }

    public static float breakingDelta(BlockState state, PlayerEntity player, BlockPos pos, float vanilla) {
        return canBreak(state, player, pos) ? vanilla : 0f;
    }

    public static void warnNotOwner(PlayerEntity player) {
        player.sendMessage(Text.translatable("message.create-security.not_obsidian_owner")
                .formatted(Formatting.RED), true);
    }
}
