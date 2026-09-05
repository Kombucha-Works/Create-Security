package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.mixin.TrapdoorBlockInvoker;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.RegistryKey;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

public final class KeycardReaderDoors {

    public static final int CLOSE_DELAY_TICKS = 40;

    private static final Map<RegistryKey<World>, Map<BlockPos, Long>> pending = new HashMap<>();

    private KeycardReaderDoors() {}

    public static boolean isDoor(BlockState state) {
        return state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapdoorBlock;
    }

    public static void toggleWithKeycard(ServerWorld world, BlockPos clicked, PlayerEntity player) {
        BlockPos pos = KeycardReaderGeometry.hostPos(world, clicked);
        BlockState state = world.getBlockState(pos);
        if (isOpen(state)) {
            setOpen(world, pos, state, player, false);
            Map<BlockPos, Long> due = pending.get(world.getRegistryKey());
            if (due != null) due.remove(pos);
            return;
        }
        setOpen(world, pos, state, player, true);
        pending.computeIfAbsent(world.getRegistryKey(), key -> new HashMap<>())
                .put(pos.toImmutable(), world.getTime() + CLOSE_DELAY_TICKS);
    }

    private static boolean isOpen(BlockState state) {
        if (state.getBlock() instanceof DoorBlock) return state.get(DoorBlock.OPEN);
        if (state.getBlock() instanceof TrapdoorBlock) return state.get(TrapdoorBlock.OPEN);
        return false;
    }

    public static void tick(ServerWorld world) {
        Map<BlockPos, Long> due = pending.get(world.getRegistryKey());
        if (due == null || due.isEmpty()) return;

        long now = world.getTime();
        Iterator<Map.Entry<BlockPos, Long>> it = due.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<BlockPos, Long> entry = it.next();
            if (entry.getValue() > now) continue;
            it.remove();
            BlockPos pos = entry.getKey();
            if (!world.isChunkLoaded(pos)) continue;
            setOpen(world, pos, world.getBlockState(pos), null, false);
        }
    }

    private static void setOpen(ServerWorld world, BlockPos pos, BlockState state,
                                @Nullable PlayerEntity player, boolean open) {
        if (state.getBlock() instanceof DoorBlock door) {

            door.setOpen(player, world, state, pos, open);
            return;
        }
        if (state.getBlock() instanceof TrapdoorBlock trapdoor) {
            if (state.get(TrapdoorBlock.OPEN) == open) return;
            world.setBlockState(pos, state.with(TrapdoorBlock.OPEN, open), Block.NOTIFY_LISTENERS);
            if (state.get(TrapdoorBlock.WATERLOGGED)) {
                world.scheduleFluidTick(pos, net.minecraft.fluid.Fluids.WATER,
                        net.minecraft.fluid.Fluids.WATER.getTickRate(world));
            }
            ((TrapdoorBlockInvoker) trapdoor).invokePlayToggleSound(player, world, pos, open);
        }
    }
}
