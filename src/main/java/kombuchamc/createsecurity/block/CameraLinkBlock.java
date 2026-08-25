package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.block.entity.CameraLinkBlockEntity;
import kombuchamc.createsecurity.block.entity.ModBlockEntities;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BlockEntityTicker;
import net.minecraft.block.entity.BlockEntityType;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

public class CameraLinkBlock extends BlockWithEntity implements BlockEntityProvider {

    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    private static final VoxelShape SHAPE_NORTH = VoxelShapes.union(
            Block.createCuboidShape(2, 2, 0, 14, 14, 1),
            Block.createCuboidShape(3, 3, 1, 13, 13, 6),
            Block.createCuboidShape(7, 13, 2, 9, 15, 4),
            Block.createCuboidShape(4, 15, 2, 12, 16, 4)
    );
    private static final VoxelShape SHAPE_SOUTH = VoxelShapes.union(
            Block.createCuboidShape(2, 2, 15, 14, 14, 16),
            Block.createCuboidShape(3, 3, 10, 13, 13, 15),
            Block.createCuboidShape(7, 13, 12, 9, 15, 14),
            Block.createCuboidShape(4, 15, 12, 12, 16, 14)
    );
    private static final VoxelShape SHAPE_EAST = VoxelShapes.union(
            Block.createCuboidShape(15, 2, 2, 16, 14, 14),
            Block.createCuboidShape(10, 3, 3, 15, 13, 13),
            Block.createCuboidShape(12, 13, 7, 14, 15, 9),
            Block.createCuboidShape(12, 15, 4, 14, 16, 12)
    );
    private static final VoxelShape SHAPE_WEST = VoxelShapes.union(
            Block.createCuboidShape(0, 2, 2, 1, 14, 14),
            Block.createCuboidShape(1, 3, 3, 6, 13, 13),
            Block.createCuboidShape(2, 13, 7, 4, 15, 9),
            Block.createCuboidShape(2, 15, 4, 4, 16, 12)
    );

    public CameraLinkBlock(Settings settings) {
        super(settings);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {

        if (!ctx.getSide().getAxis().isHorizontal()) return null;
        return this.getDefaultState().with(FACING, ctx.getSide().getOpposite());
    }

    public static boolean isValidMount(net.minecraft.world.WorldView world, BlockPos pos, Direction clickedSide) {
        return clickedSide.getAxis().isHorizontal()
                && world.getBlockState(pos.offset(clickedSide.getOpposite()))
                        .isOf(RegisterModBlocks.CAMERA_DISPLAY_BLOCK);
    }

    @Override
    public boolean canPlaceAt(BlockState state, net.minecraft.world.WorldView world, BlockPos pos) {
        return world.getBlockState(pos.offset(state.get(FACING)))
                .isOf(RegisterModBlocks.CAMERA_DISPLAY_BLOCK);
    }

    @Override
    public BlockState getStateForNeighborUpdate(BlockState state, Direction direction, BlockState neighborState,
                                                net.minecraft.world.WorldAccess world, BlockPos pos, BlockPos neighborPos) {
        if (direction == state.get(FACING)
                && !neighborState.isOf(RegisterModBlocks.CAMERA_DISPLAY_BLOCK)) {
            return Blocks.AIR.getDefaultState();
        }
        return super.getStateForNeighborUpdate(state, direction, neighborState, world, pos, neighborPos);
    }

    @Override
    public VoxelShape getOutlineShape(BlockState state, BlockView world, BlockPos pos, ShapeContext context) {
        return switch (state.get(FACING)) {
            case SOUTH -> SHAPE_SOUTH;
            case EAST -> SHAPE_EAST;
            case WEST -> SHAPE_WEST;
            default -> SHAPE_NORTH;
        };
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public void onPlaced(World world, BlockPos pos, BlockState state, LivingEntity placer, ItemStack itemStack) {
        if (!world.isClient && placer instanceof PlayerEntity player) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof CameraLinkBlockEntity link) {
                link.setOwner(player.getUuid());
            }
        }
    }

    private boolean isTouchingCameraDisplay(World world, BlockPos pos) {
        for (Direction dir : Direction.values()) {
            if (world.getBlockState(pos.offset(dir)).isOf(RegisterModBlocks.CAMERA_DISPLAY_BLOCK)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public ActionResult onUse(BlockState state, World world, BlockPos pos, PlayerEntity player, Hand hand, BlockHitResult hit) {
        if (!world.isClient) {
            if (!isTouchingCameraDisplay(world, pos)) {
                player.sendMessage(Text.translatable("message.create-security.link_needs_display").formatted(Formatting.RED), true);
                return ActionResult.FAIL;
            }
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof CameraLinkBlockEntity link) {
                if (!link.isOwner(player)
                        && !kombuchamc.createsecurity.config.CSConfigs.canBypassOwner(player)) {
                    player.sendMessage(Text.translatable("message.create-security.not_camera_link_owner").formatted(Formatting.RED), true);
                    return ActionResult.FAIL;
                }
                player.openHandledScreen(link);
            }
        }
        return ActionResult.SUCCESS;
    }

    @Override
    public void onStateReplaced(BlockState state, World world, BlockPos pos, BlockState newState, boolean isMoving) {
        if (state.getBlock() != newState.getBlock()) {
            BlockEntity be = world.getBlockEntity(pos);
            if (be instanceof CameraLinkBlockEntity) {
                ItemScatterer.spawn(world, pos, (CameraLinkBlockEntity) be);
            }

            if (!world.isClient && be instanceof CameraLinkBlockEntity linkBe) {
                linkBe.clearAllLinkedDisplays();
            }
            super.onStateReplaced(state, world, pos, newState, isMoving);
        }
    }

    @Nullable
    @Override
    public BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CameraLinkBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(World world, BlockState state, BlockEntityType<T> type) {
        return checkType(type, ModBlockEntities.CAMERA_LINK_BLOCK_ENTITY,
                (world1, pos, state1, be) -> be.tick(world1, pos, state1));
    }
}
