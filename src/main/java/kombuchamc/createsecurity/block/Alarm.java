package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.sound.RegisterModSounds;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.network.packet.s2c.play.StopSoundS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

public class Alarm extends Block {

    public static final VoxelShape SHAPE_UP = Block.createCuboidShape(5, 0, 5, 11, 7, 11);
    public static final VoxelShape SHAPE_DOWN = Block.createCuboidShape(5, 9, 5, 11, 16, 11);
    public static final VoxelShape SHAPE_NORTH = Block.createCuboidShape(5, 5, 9, 11, 11, 16);
    public static final VoxelShape SHAPE_SOUTH = Block.createCuboidShape(5, 5, 0, 11, 11, 7);
    public static final VoxelShape SHAPE_EAST = Block.createCuboidShape(0, 5, 5, 7, 11, 11);
    public static final VoxelShape SHAPE_WEST = Block.createCuboidShape(9, 5, 5, 16, 11, 11);

    public static final EnumProperty<Direction> FACING = Properties.FACING;
    public static final BooleanProperty POWERED = Properties.POWERED;

    public Alarm(Settings settings) {
        super(settings);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState()
                .with(FACING, ctx.getSide())
                .with(POWERED, ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos()));
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;
        boolean isPowered = world.isReceivingRedstonePower(pos);
        if (state.get(POWERED) == isPowered) return;
        world.setBlockState(pos, state.with(POWERED, isPowered));
        if (isPowered) {
            world.playSound(null, pos, RegisterModSounds.ALARM, SoundCategory.BLOCKS, 3f, 1f);
        } else {
            stopAlarmSound(world, pos);
        }
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && state.get(POWERED)) {
            stopAlarmSound(world, pos);
        }
        super.onBreak(world, pos, state, player);
    }

    private static void stopAlarmSound(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        StopSoundS2CPacket packet =
                new StopSoundS2CPacket(RegisterModSounds.ALARM.getId(), SoundCategory.BLOCKS);
        for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
            player.networkHandler.sendPacket(packet);
        }
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case UP -> SHAPE_UP;
            case DOWN -> SHAPE_DOWN;
            case NORTH -> SHAPE_NORTH;
            case SOUTH -> SHAPE_SOUTH;
            case WEST -> SHAPE_WEST;
            case EAST -> SHAPE_EAST;
        };
    }
}
