package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.block.entity.ModBlockEntities;
import kombuchamc.createsecurity.block.entity.MonitorBlockEntity;
import net.minecraft.block.Block;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.BlockWithEntity;
import net.minecraft.block.ShapeContext;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class MonitorBlock extends BlockWithEntity {

    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE_NS = VoxelShapes.union(
            Block.createCuboidShape(6, 0, 6, 10, 1, 10),
            Block.createCuboidShape(7, 1, 7, 9, 3, 9),
            Block.createCuboidShape(2, 3, 7, 14, 10, 9)
    );
    private static final VoxelShape SHAPE_EW = VoxelShapes.union(
            Block.createCuboidShape(6, 0, 6, 10, 1, 10),
            Block.createCuboidShape(7, 1, 7, 9, 3, 9),
            Block.createCuboidShape(7, 3, 2, 9, 10, 14)
    );

    public static final float SCREEN_X_LOW  = 2f / 16f;
    public static final float SCREEN_X_HIGH = 14f / 16f;
    public static final float SCREEN_Y_LOW  = 4f / 16f;
    public static final float SCREEN_Y_HIGH = 10f / 16f;

    public static final float IMG_X_LOW = SCREEN_X_LOW;
    public static final float IMG_X_HIGH = SCREEN_X_HIGH;
    public static final float IMG_Y_LOW = SCREEN_Y_LOW;
    public static final float IMG_Y_HIGH = SCREEN_Y_HIGH;
    public static final float IMG_W = IMG_X_HIGH - IMG_X_LOW;
    public static final float IMG_H = IMG_Y_HIGH - IMG_Y_LOW;

    private static final float FEED_KEPT_HEIGHT = Math.min(1f, 1.5f / (IMG_W / IMG_H));
    public static final float FEED_V_LOW = (1f - FEED_KEPT_HEIGHT) / 2f;
    public static final float FEED_V_HIGH = 1f - FEED_V_LOW;

    private static final float DIRT_KEPT_HEIGHT = Math.min(1f, 1f / (IMG_W / IMG_H));
    public static final float DIRT_V_LOW = (1f - DIRT_KEPT_HEIGHT) / 2f;
    public static final float DIRT_V_HIGH = 1f - DIRT_V_LOW;

    public static Vec3d lastArrowClickHit;
    public static Direction lastArrowClickFacing;

    public MonitorBlock(Settings settings) {
        super(settings);
    }

    public static float[] cycleArrowRect() {
        float margin = IMG_H * 0.06f;
        float ah = IMG_H * 0.3f;
        float aw = ah * 7f / 11f;
        float minX = IMG_X_LOW + margin;
        float minY = IMG_Y_LOW + margin;
        return new float[]{minX, minX + aw, minY, minY + ah};
    }

    public static float[] backArrowRect() {
        float margin = IMG_H * 0.06f;
        float ah = IMG_H * 0.3f;
        float aw = ah * 7f / 11f;
        float maxX = IMG_X_HIGH - margin;
        float minY = IMG_Y_LOW + margin;
        return new float[]{maxX - aw, maxX, minY, minY + ah};
    }

    private static boolean inRect(float[] rect, float x, float y, float pad) {
        return x >= rect[0] - pad && x <= rect[1] + pad && y >= rect[2] - pad && y <= rect[3] + pad;
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        if (!canPlaceAt(this.getDefaultState(), ctx.getWorld(), ctx.getBlockPos())) return null;
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    @Override
    public boolean canPlaceAt(BlockState state, net.minecraft.world.WorldView world, BlockPos pos) {
        return Block.sideCoversSmallSquare(world, pos.down(), Direction.UP);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                net.minecraft.world.WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == Direction.DOWN && !canPlaceAt(state, world, pos)) {
            return net.minecraft.block.Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return state.get(FACING).getAxis() == Direction.Axis.Z ? SHAPE_NS : SHAPE_EW;
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (!world.isClient && placer instanceof PlayerEntity player) {
            if (world.getBlockEntity(pos) instanceof MonitorBlockEntity monitor) {
                monitor.setOwner(player.getUuid());
            }
        }
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player,
                              Hand hand, BlockHitResult hit) {
        Direction facing = state.get(FACING);
        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof MonitorBlockEntity monitor)) return ActionResult.PASS;

        if (hit.getSide() == facing
                && monitor.getLinkedCameraPos() != null && monitor.getCameraCount() > 1) {
            Vec3d local = hit.getPos().subtract(pos.getX(), pos.getY(), pos.getZ());
            float vy = (float) local.y;
            float vx = switch (facing) {
                case NORTH -> (float) local.x;
                case SOUTH -> 1f - (float) local.x;
                case WEST -> 1f - (float) local.z;
                default -> (float) local.z;
            };
            float pad = 0.02f;
            int delta = 0;
            if (inRect(cycleArrowRect(), vx, vy, pad)) {
                delta = 1;
            } else if (inRect(backArrowRect(), vx, vy, pad)) {
                delta = -1;
            }
            if (delta != 0) {
                if (world.isClient) {
                    lastArrowClickHit = hit.getPos();
                    lastArrowClickFacing = facing;
                } else {
                    monitor.cycleCamera(delta);
                    world.playSound(null, pos, SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON,
                            SoundCategory.BLOCKS, 0.4f, 1.4f);
                }
                return ActionResult.success(world.isClient);
            }
        }

        if (!world.isClient) {
            if (!monitor.isOwner(player)
                    && !kombuchamc.createsecurity.config.CSConfigs.canBypassOwner(player)) {
                player.sendMessage(Text.translatable("message.create-security.not_monitor_owner")
                        .formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            player.openHandledScreen(monitor);
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof MonitorBlockEntity monitor) {
                ItemScatterer.spawn(world, pos, monitor);
            }
            super.onStateReplaced(state, world, pos, newState, isMoving);
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new MonitorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.MONITOR_BLOCK_ENTITY,
                (world1, pos, state1, be) -> be.tick(world1, pos, state1));
    }
}

