package kombuchamc.createsecurity.mixin;

import kombuchamc.createsecurity.block.KeycardReaderPlacement;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.enums.ChestType;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.WorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChestBlock.class)
public class ChestBlockLockMixin {

    @Inject(method = "getPlacementState", at = @At("RETURN"), cancellable = true)
    private void createsecurity$dontPairWithLockedChest(ItemPlacementContext ctx,
                                                        CallbackInfoReturnable<BlockState> cir) {
        BlockState state = cir.getReturnValue();
        if (!wouldPair(state)) return;
        BlockPos pos = ctx.getBlockPos();
        if (!locked(ctx.getWorld(), pos, state)) return;
        cir.setReturnValue(state.with(ChestBlock.CHEST_TYPE, ChestType.SINGLE));
    }

    @Inject(method = "getStateForNeighborUpdate", at = @At("RETURN"), cancellable = true)
    private void createsecurity$keepLockedChestSingle(BlockState state, Direction direction,
                                                      BlockState neighborState, WorldAccess world,
                                                      BlockPos pos, BlockPos neighborPos,
                                                      CallbackInfoReturnable<BlockState> cir) {

        if (!state.contains(ChestBlock.CHEST_TYPE)
                || state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            return;
        }
        BlockState result = cir.getReturnValue();
        if (!wouldPair(result)) return;
        if (!locked(world, pos, result)) return;
        cir.setReturnValue(result.with(ChestBlock.CHEST_TYPE, ChestType.SINGLE));
    }

    private static boolean wouldPair(BlockState state) {
        return state != null && state.contains(ChestBlock.CHEST_TYPE)
                && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE;
    }

    private static boolean locked(WorldAccess world, BlockPos pos, BlockState state) {
        return KeycardReaderPlacement.hasReader(world, pos)
                || KeycardReaderPlacement.hasReader(world, pos.offset(ChestBlock.getFacing(state)));
    }
}
