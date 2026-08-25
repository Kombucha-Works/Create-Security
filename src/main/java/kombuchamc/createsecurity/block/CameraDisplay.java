package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.block.entity.CameraDisplayBlockEntity;
import kombuchamc.createsecurity.block.entity.CameraDisplayGroup;
import kombuchamc.createsecurity.block.entity.CameraLinkBlockEntity;
import kombuchamc.createsecurity.block.entity.ModBlockEntities;
import net.minecraft.block.*;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.BooleanProperty;
import net.minecraft.state.property.EnumProperty;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

public class CameraDisplay extends BlockWithEntity {

    public static final EnumProperty<Direction> FACING = Properties.HORIZONTAL_FACING;

    public static final float BEZEL = 2f / 16f;

    public static float[] cycleArrowRect(BlockState state, @Nullable CameraDisplayGroup group) {
        float xLow  = state.get(RIGHT) ? BEZEL : 0f;
        float xHigh = state.get(LEFT)  ? 1f - BEZEL : 1f;
        float yLow  = state.get(DOWN)  ? BEZEL : 0f;
        float yHigh = state.get(UP)    ? 1f - BEZEL : 1f;

        float imgXLow, imgYLow, imgH;
        if (group != null && group.valid && group.active) {

            imgXLow = BEZEL;
            imgYLow = BEZEL;
            imgH = (group.height - 2f * BEZEL) / 2f;
        } else {

            float screenW = xHigh - xLow;
            float screenH = yHigh - yLow;
            float imageH = Math.min(screenH, screenW * 2f / 3f);
            float imageW = imageH * 1.5f;
            if (imageW > screenW) {
                imageW = screenW;
                imageH = imageW * 2f / 3f;
            }
            imgXLow = xLow + (screenW - imageW) / 2f;
            imgYLow = yLow + (screenH - imageH) / 2f;
            imgH = imageH;
        }

        float margin = imgH * 0.06f;
        float ah = imgH * 0.24f;
        float aw = ah * 7f / 11f;
        float minX = imgXLow + margin;
        float minY = imgYLow + margin;
        return new float[]{minX, minX + aw, minY, minY + ah};
    }

    private static boolean inRect(float[] rect, float x, float y, float pad) {
        return x >= rect[0] - pad && x <= rect[1] + pad && y >= rect[2] - pad && y <= rect[3] + pad;
    }

    public static float[] backArrowRect(BlockState state, @Nullable CameraDisplayGroup group) {
        float[] fwd = cycleArrowRect(state, group);
        float w = fwd[1] - fwd[0];
        float gap = w * 0.4f;

        return new float[]{fwd[1] + gap, fwd[1] + gap + w, fwd[2], fwd[3]};
    }

    public static net.minecraft.util.math.Vec3d lastArrowClickHit;
    public static Direction lastArrowClickFacing;

    public static final BooleanProperty UP = BooleanProperty.of("up");
    public static final BooleanProperty DOWN = BooleanProperty.of("down");
    public static final BooleanProperty RIGHT = BooleanProperty.of("right");
    public static final BooleanProperty LEFT = BooleanProperty.of("left");

    public static final BooleanProperty TOPRIGHT = BooleanProperty.of("topright");
    public static final BooleanProperty TOPLEFT = BooleanProperty.of("topleft");
    public static final BooleanProperty BOTTOMRIGHT = BooleanProperty.of("bottomright");
    public static final BooleanProperty BOTTOMLEFT = BooleanProperty.of("bottomleft");

    @Override
    public void neighborUpdate(BlockState state, World world, BlockPos pos, Block sourceBlock, BlockPos sourcePos, boolean notify) {

        BlockPos leftBlockPos = pos
                .offset(state.get(FACING).rotateClockwise(Direction.Axis.Y));
        BlockPos leftUpBlockPos = leftBlockPos.offset(Direction.UP);
        BlockPos leftDownBlockPos = leftBlockPos.offset(Direction.DOWN);

        BlockPos rightBlockPos = pos
                .offset(state.get(FACING).rotateCounterclockwise(Direction.Axis.Y));
        BlockPos rightUpBlockPos = rightBlockPos.offset(Direction.UP);
        BlockPos rightDownBlockPos = rightBlockPos.offset(Direction.DOWN);

        CameraDisplayGroup group = CameraDisplayGroup.computeTopology(world, pos);
        Set<BlockPos> activeSubRect = (group.valid && group.active)
                ? group.getMembers()
                : Collections.emptySet();

        boolean ISUP    = !activeSubRect.contains(pos.up());
        boolean ISDOWN  = !activeSubRect.contains(pos.down());
        boolean ISLEFT  = !activeSubRect.contains(leftBlockPos);
        boolean ISRIGHT = !activeSubRect.contains(rightBlockPos);

        boolean ISTOPRIGHT = !(activeSubRect.contains(pos.up())
                            && activeSubRect.contains(rightBlockPos)
                            && activeSubRect.contains(rightUpBlockPos));
        boolean ISTOPLEFT = !(activeSubRect.contains(pos.up())
                           && activeSubRect.contains(leftBlockPos)
                           && activeSubRect.contains(leftUpBlockPos));
        boolean ISBOTTOMRIGHT = !(activeSubRect.contains(pos.down())
                               && activeSubRect.contains(rightBlockPos)
                               && activeSubRect.contains(rightDownBlockPos));
        boolean ISBOTTOMLEFT = !(activeSubRect.contains(pos.down())
                              && activeSubRect.contains(leftBlockPos)
                              && activeSubRect.contains(leftDownBlockPos));

        world.setBlockState(pos, world.getBlockState(pos)
                .with(UP, ISUP)
                .with(DOWN, ISDOWN)
                .with(RIGHT, ISRIGHT)
                .with(LEFT, ISLEFT)
                .with(TOPRIGHT, ISTOPRIGHT)
                .with(TOPLEFT, ISTOPLEFT)
                .with(BOTTOMRIGHT, ISBOTTOMRIGHT)
                .with(BOTTOMLEFT, ISBOTTOMLEFT));

        if (world.isClient) {
            CameraDisplayBlockEntity.invalidateAllClientGroups();
        }

        if (!world.isClient) {
            boolean hasAdjacentLink = false;
            for (Direction dir : Direction.values()) {
                if (world.getBlockState(pos.offset(dir)).isOf(RegisterModBlocks.CAMERA_LINK_BLOCK)) {
                    hasAdjacentLink = true;
                    break;
                }
            }
            if (!hasAdjacentLink) {
                BlockEntity be = world.getBlockEntity(pos);
                if (be instanceof CameraDisplayBlockEntity display && display.getLinkedCameraPos() != null) {
                    display.setLinkedCamera(null, null);
                }
            }

            Set<BlockPos> triggeredLinks = new HashSet<>();
            for (BlockPos memberPos : group.wallMembers) {
                for (Direction dir : Direction.values()) {
                    BlockPos linkPos = memberPos.offset(dir);
                    if (triggeredLinks.contains(linkPos)) continue;
                    if (world.getBlockState(linkPos).isOf(RegisterModBlocks.CAMERA_LINK_BLOCK)) {
                        BlockEntity linkBe = world.getBlockEntity(linkPos);
                        if (linkBe != null) {
                            linkBe.markDirty();
                            triggeredLinks.add(linkPos);
                        }
                    }
                }
            }
        }
        }

    @Override
    public net.minecraft.util.ActionResult onUse(BlockState state, World world, BlockPos pos,
                                                 net.minecraft.entity.player.PlayerEntity player,
                                                 net.minecraft.util.Hand hand,
                                                 net.minecraft.util.hit.BlockHitResult hit) {
        Direction facing = state.get(FACING);
        if (hit.getSide() != facing) return net.minecraft.util.ActionResult.PASS;

        BlockEntity be = world.getBlockEntity(pos);
        if (!(be instanceof CameraDisplayBlockEntity display)) return net.minecraft.util.ActionResult.PASS;
        if (display.getLinkedCameraPos() == null || display.getCameraCount() < 2) {
            return net.minecraft.util.ActionResult.PASS;
        }

        CameraDisplayGroup group = CameraDisplayGroup.compute(world, pos);
        if (group != null && group.valid && !group.active) {
            return net.minecraft.util.ActionResult.PASS;
        }

        net.minecraft.util.math.Vec3d local = hit.getPos().subtract(pos.getX(), pos.getY(), pos.getZ());
        float vy = (float) local.y;
        float vx = switch (facing) {
            case NORTH -> (float) local.x;
            case SOUTH -> 1f - (float) local.x;
            case WEST -> 1f - (float) local.z;
            default -> (float) local.z;
        };

        if (group != null && group.valid && group.active) {
            vx += group.col;
            vy += group.height - 1 - group.row;
        }
        float pad = 0.03f;
        int delta;
        if (inRect(cycleArrowRect(state, group), vx, vy, pad)) {
            delta = 1;
        } else if (inRect(backArrowRect(state, group), vx, vy, pad)) {
            delta = -1;
        } else {
            return net.minecraft.util.ActionResult.PASS;
        }

        if (world.isClient) {
            lastArrowClickHit = hit.getPos();
            lastArrowClickFacing = facing;

            BlockPos predicted = delta > 0 ? display.getNextCameraPos() : display.getPrevCameraPos();
            if (predicted != null) {
                Direction lensDir = null;
                BlockState camState = world.getBlockState(predicted);
                if (camState.isOf(RegisterModBlocks.CAMERA_BLOCK)) {
                    lensDir = camState.get(kombuchamc.createsecurity.block.camera.CameraBlock.FACING)
                            .getOpposite();
                }

                Set<BlockPos> targets = (group != null && group.valid && group.active)
                        ? group.getMembers()
                        : Collections.singleton(pos);
                for (BlockPos memberPos : targets) {
                    if (world.getBlockEntity(memberPos) instanceof CameraDisplayBlockEntity member) {
                        member.applyPredictedCameraCycle(delta, predicted, lensDir);
                    }
                }
            }
        }

        if (!world.isClient) {
            BlockPos linkPos = display.getLinkBlockPos();
            if (linkPos != null
                    && world.getBlockEntity(linkPos) instanceof CameraLinkBlockEntity link) {
                link.cycleCamera(delta);
                world.playSound(null, pos, net.minecraft.sound.SoundEvents.BLOCK_STONE_BUTTON_CLICK_ON,
                        net.minecraft.sound.SoundCategory.BLOCKS, 0.4f, 1.4f);
            }
        }
        return net.minecraft.util.ActionResult.success(world.isClient);
    }

    @Override
    protected void appendProperties(StateManager.Builder<Block, BlockState> builder) {
        builder.add(FACING, UP, DOWN, RIGHT, LEFT, TOPRIGHT, TOPLEFT, BOTTOMRIGHT, BOTTOMLEFT);
    }

    @Override
    public BlockState getPlacementState(ItemPlacementContext ctx) {
        return this.getDefaultState().with(FACING, ctx.getHorizontalPlayerFacing().getOpposite());
    }

    public CameraDisplay(Settings settings) {
        super(settings);
    }

    @Override
    public BlockRenderType getRenderType(BlockState state) {
        return BlockRenderType.MODEL;
    }

    @Override
    public @Nullable BlockEntity createBlockEntity(BlockPos pos, BlockState state) {
        return new CameraDisplayBlockEntity(pos, state);
    }
}

