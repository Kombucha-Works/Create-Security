package kombuchamc.createsecurity.block.obsidian;

import kombuchamc.createsecurity.block.KeycardReaderPlacement;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.BlockSetType;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class ObsidianDoorBlock extends DoorBlock {

    public ObsidianDoorBlock(Settings settings) {
        super(settings, BlockSetType.STONE);
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {
        return state.get(HALF) == DoubleBlockHalf.LOWER || super.canPlaceAt(state, world, pos);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock,
                               BlockPos sourcePos, boolean notify) {
        if (!KeycardReaderPlacement.isLocked(world, pos)) {
            super.neighborUpdate(state, world, pos, sourceBlock, sourcePos, notify);
            return;
        }
        boolean powered = world.isReceivingRedstonePower(pos)
                || world.isReceivingRedstonePower(pos.offset(
                        state.get(HALF) == DoubleBlockHalf.LOWER ? Direction.UP : Direction.DOWN));
        if (!getDefaultState().isOf(sourceBlock) && powered != state.get(POWERED)) {
            world.setBlockState(pos, state.with(POWERED, powered), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        return ActionResult.PASS;
    }

    @Override
    public float calcBlockBreakingDelta(BlockState state, PlayerEntity player, BlockView world, BlockPos pos) {
        return ObsidianProtection.breakingDelta(state, player, pos,
                super.calcBlockBreakingDelta(state, player, world, pos));
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.onPlaced(world, pos, state, placer, stack);
        ObsidianProtection.onPlaced(world, pos, state, placer);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean moved) {
        super.onStateReplaced(state, world, pos, newState, moved);
        ObsidianProtection.onStateReplaced(state, world, pos, newState);
    }
}
