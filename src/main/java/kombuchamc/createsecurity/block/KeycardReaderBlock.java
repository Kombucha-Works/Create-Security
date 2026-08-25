package kombuchamc.createsecurity.block;

import net.minecraft.block.AbstractFurnaceBlock;
import net.minecraft.block.BarrelBlock;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ChestBlock;
import net.minecraft.block.DoorBlock;
import net.minecraft.block.EnderChestBlock;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.TrapdoorBlock;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.DirectionProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.Direction;

public class KeycardReaderBlock extends Block {

    public static final DirectionProperty FACING = Properties.FACING;
    public static final BooleanProperty ARMED = BooleanProperty.of("armed");

    public KeycardReaderBlock(Settings settings) {
        super(settings);
        setDefaultState(getDefaultState().with(FACING, Direction.NORTH).with(ARMED, false));
    }

    public static boolean isSupport(BlockState state) {
        Block block = state.getBlock();
        return block instanceof DoorBlock
                || block instanceof TrapdoorBlock
                || block instanceof ChestBlock
                || block instanceof EnderChestBlock
                || block instanceof AbstractFurnaceBlock
                || block instanceof BarrelBlock
                || block instanceof ShulkerBoxBlock;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, ARMED);
    }
}
