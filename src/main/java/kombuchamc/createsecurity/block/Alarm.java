package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.sound.RegisterModSounds;
import net.minecraft.block.Block;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.minecraft.block.Blocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShapeContext;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.particle.DustParticleEffect;
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
import net.minecraft.util.math.random.Random;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import net.minecraft.world.WorldAccess;
import net.minecraft.world.WorldView;
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

    public static final BooleanProperty LIT = Properties.LIT;

    private static final int BLINK_TICKS = 10;

    private static final int SIREN_TICKS = 14;

    private static final int TICK_TICKS = 2;
    private static final float SIREN_VOLUME = 3f;

    private static final DustParticleEffect GLOW =
            new DustParticleEffect(DustParticleEffect.RED, 1.4f);
    private static final double GLOW_RADIUS = 0.45;
    private static final float SIREN_PITCH = 1f;

    public Alarm(Settings settings) {
        super(settings);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        boolean powered = ctx.getWorld().isReceivingRedstonePower(ctx.getBlockPos());
        return this.getDefaultState()
                .with(FACING, ctx.getSide())
                .with(POWERED, powered)
                .with(LIT, powered);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, POWERED, LIT);
    }

    private static Direction supportSide(BlockState state) {
        return state.get(FACING).getOpposite();
    }

    @Override
    public boolean canPlaceAt(BlockState state, WorldView world, BlockPos pos) {

        Direction support = supportSide(state);
        return Block.sideCoversSmallSquare(world, pos.offset(support), support.getOpposite());
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction,
                                                BlockState neighborState, WorldAccess world,
                                                BlockPos pos, BlockPos neighborPos) {
        if (direction == supportSide(state) && !canPlaceAt(state, world, pos)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public void onBlockAdded(BlockState state, World world, BlockPos pos, BlockState oldState, boolean notify) {
        super.onBlockAdded(state, world, pos, oldState, notify);

        if (world.isClient || oldState.isOf(this) || !state.get(POWERED)) return;
        start(world, pos);
    }

    private void start(World world, BlockPos pos) {
        wail(world, pos);
        world.scheduleBlockTick(pos, this, TICK_TICKS);
    }

    private static void glow(ServerWorld world, BlockPos pos, Direction facing) {
        double x = pos.getX() + 0.5 + facing.getOffsetX() * 0.3;
        double y = pos.getY() + 0.5 + facing.getOffsetY() * 0.3;
        double z = pos.getZ() + 0.5 + facing.getOffsetZ() * 0.3;
        world.spawnParticles(GLOW, x, y, z, 3, GLOW_RADIUS, GLOW_RADIUS, GLOW_RADIUS, 0);
    }

    private static void wail(World world, BlockPos pos) {
        world.playSound(null, pos, RegisterModSounds.ALARM, SoundCategory.BLOCKS,
                SIREN_VOLUME, SIREN_PITCH);
    }

    private static void stopWail(World world, BlockPos pos) {
        if (!(world instanceof ServerWorld serverWorld)) return;
        StopSoundS2CPacket packet = new StopSoundS2CPacket(
                RegisterModSounds.ALARM.getId(), SoundCategory.BLOCKS);
        for (ServerPlayerEntity player : PlayerLookup.tracking(serverWorld, pos)) {
            player.networkHandler.sendPacket(packet);
        }
    }

    @Override
    public void scheduledTick(BlockState state, ServerWorld world, BlockPos pos, Random random) {
        if (!state.get(POWERED)) {
            if (state.get(LIT)) world.setBlockState(pos, state.with(LIT, false), Block.NOTIFY_LISTENERS);
            return;
        }

        long time = world.getTime();
        boolean lit = time % (BLINK_TICKS * 2L) < BLINK_TICKS;
        if (state.get(LIT) != lit) {
            world.setBlockState(pos, state.with(LIT, lit), Block.NOTIFY_LISTENERS);
        }

        if (lit) glow(world, pos, state.get(FACING));
        if (time % SIREN_TICKS < TICK_TICKS) wail(world, pos);
        world.scheduleBlockTick(pos, this, TICK_TICKS);
    }

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {
        if (world.isClient) return;
        boolean isPowered = world.isReceivingRedstonePower(pos);
        if (state.get(POWERED) == isPowered) return;
        world.setBlockState(pos, state.with(POWERED, isPowered).with(LIT, isPowered));
        if (isPowered) {
            start(world, pos);
        } else {
            stopWail(world, pos);
        }
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        if (!world.isClient && state.get(POWERED)) stopWail(world, pos);
        super.onBreak(world, pos, state, player);
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
