package kombuchamc.createsecurity.block.obsidian;

import net.minecraft.block.BlockState;
import net.minecraft.block.SmokerBlock;

import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class ObsidianSmokerBlock extends SmokerBlock {

    public ObsidianSmokerBlock(Settings settings) {
        super(settings);
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
