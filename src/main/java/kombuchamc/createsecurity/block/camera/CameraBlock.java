package kombuchamc.createsecurity.block.camera;

import kombuchamc.createsecurity.block.entity.CameraBlockEntity;
import kombuchamc.createsecurity.block.entity.ModBlockEntities;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.NamedScreenHandlerFactory;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.function.BooleanBiFunction;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;
import java.util.List;

public class CameraBlock extends BlockWithEntity implements BlockEntityProvider {

    public static final EnumProperty<Direction> FACING = Properties.FACING;

    private static final VoxelShape SHAPE_DOWN = VoxelShapes.combine(
            Block.createCuboidShape(5, 0, 5, 11, 2, 11),
            Block.createCuboidShape(7, 2, 7, 9, 3, 9),
            BooleanBiFunction.OR
    );
    private static final VoxelShape SHAPE_UP = VoxelShapes.combine(
            Block.createCuboidShape(5, 14, 5, 11, 16, 11),
            Block.createCuboidShape(7, 13, 7, 9, 14, 9),
            BooleanBiFunction.OR
    );
    private static final VoxelShape SHAPE_NORTH = VoxelShapes.union(
            Block.createCuboidShape(6, 6, 0, 10, 10, 1),
            Block.createCuboidShape(7, 7, 1, 9, 9, 4),
            Block.createCuboidShape(6, 6, 3, 10, 11, 5),
            Block.createCuboidShape(6, 5, 5, 10, 10, 7),
            Block.createCuboidShape(6, 4, 7, 10, 9, 9),
            Block.createCuboidShape(6, 6, 9, 10, 8, 10)
    );
    private static final VoxelShape SHAPE_SOUTH = VoxelShapes.union(
            Block.createCuboidShape(6, 6, 15, 10, 10, 16),
            Block.createCuboidShape(7, 7, 13, 9, 9, 15),
            Block.createCuboidShape(6, 6, 11, 10, 11, 13),
            Block.createCuboidShape(6, 5, 9, 10, 10, 11),
            Block.createCuboidShape(6, 4, 7, 10, 9, 9),
            Block.createCuboidShape(6, 6, 6, 10, 8, 7)
    );
    private static final VoxelShape SHAPE_WEST = VoxelShapes.union(
            Block.createCuboidShape(0, 6, 6, 1, 10, 10),
            Block.createCuboidShape(1, 7, 7, 3, 9, 9),
            Block.createCuboidShape(3, 6, 6, 5, 11, 10),
            Block.createCuboidShape(5, 5, 6, 7, 10, 10),
            Block.createCuboidShape(7, 4, 6, 9, 9, 10),
            Block.createCuboidShape(9, 6, 6, 10, 8, 10)
    );
    private static final VoxelShape SHAPE_EAST = VoxelShapes.union(
            Block.createCuboidShape(15, 6, 6, 16, 10, 10),
            Block.createCuboidShape(13, 7, 7, 15, 9, 9),
            Block.createCuboidShape(11, 6, 6, 13, 11, 10),
            Block.createCuboidShape(9, 5, 6, 11, 10, 10),
            Block.createCuboidShape(7, 4, 6, 9, 9, 10),
            Block.createCuboidShape(6, 6, 6, 7, 8, 10)
    );

    public CameraBlock(Settings settings) {
        super(settings);
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (!world.isClient && placer instanceof PlayerEntity player) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof CameraBlockEntity camera) {
                camera.setOwner(player.getUuid());
                camera.setPlacedAt(world.getTime());
                if (itemStack.hasCustomName()) {
                    camera.setCustomName(itemStack.getName().getString());
                }
            }

            if (world instanceof ServerWorld serverWorld) {
                CameraChunkTickets.add(serverWorld, pos);
            }
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

    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getSide().getOpposite());
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return state.get(FACING).getAxis().isHorizontal()
                ? BlockRenderType.ENTITYBLOCK_ANIMATED
                : BlockRenderType.MODEL;
    }

    @Override

    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CameraBlockEntity(pos, state);
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity blockEntity = world.getBlockEntity(pos);
            if (blockEntity instanceof CameraBlockEntity) {
                ItemScatterer.spawn(world, pos, (CameraBlockEntity) blockEntity);
                world.updateComparators(pos, this);
                FrequencyManager.removeCamera(pos);
                if (world instanceof ServerWorld serverWorld) {
                    FrequencyManager.markDirty(serverWorld);

                    CameraChunkTickets.removeAll(serverWorld, pos);

                    kombuchamc.createsecurity.block.entity.CameraDisplayBlockEntity
                            .clearAllForCamera(serverWorld, pos);
                }
            }
            super.onStateReplaced(state, world, pos, newState, isMoving);
        }

    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof CameraBlockEntity camera) {
                if (!camera.isOwner(player)
                        && !kombuchamc.createsecurity.config.CSConfigs.canBypassOwner(player)) {
                    player.sendMessage(Text.translatable("message.create-security.not_camera_owner").formatted(Formatting.RED), true);
                    return ActionResult.FAIL;
                }
                player.openHandledScreen(camera);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onBreak(World world, BlockPos pos, BlockState state, PlayerEntity player) {
        super.onBreak(world, pos, state, player);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.CAMERA_BLOCK_ENTITY,
                (world1, pos, state1, blockEntity) -> blockEntity.tick(world1, pos, state1));
    }

    @Override
    public boolean emitsRedstonePower(BlockState state) {
        return true;
    }

    @Override
    public int getWeakRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        BlockEntity be = world.getBlockEntity(pos);
        if (be instanceof CameraBlockEntity camera) {
            return camera.isDetecting() ? 15 : 0;
        }
        return 0;
    }

    @Override
    public int getStrongRedstonePower(BlockState state, BlockView world, BlockPos pos, Direction direction) {
        return getWeakRedstonePower(state, world, pos, direction);
    }
}

