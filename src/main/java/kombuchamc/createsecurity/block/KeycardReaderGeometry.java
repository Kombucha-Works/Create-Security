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
import net.minecraft.block.enums.ChestType;
import net.minecraft.block.enums.DoorHinge;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.world.BlockView;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class KeycardReaderGeometry {

    public static final double DEPTH = 2.0 / 16.0;

    public static final double PANEL_HALF_WIDTH = 3.0 / 16.0;
    public static final double PANEL_HALF_HEIGHT = 4.0 / 16.0;

    private static final double DOOR_HANDLE_OFFSET = 4.0 / 16.0;
    private static final double CHEST_LIFT = 1.0 / 16.0;
    private static final double BARREL_SIDE_OFFSET = 3.0 / 16.0;
    private static final double AIM_TOLERANCE = 0.01;

    private static final Box FULL_BLOCK = new Box(0, 0, 0, 1, 1, 1);

    private KeycardReaderGeometry() {}

    public static Box hostBox(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof ShulkerBoxBlock) return FULL_BLOCK;
        VoxelShape shape = state.getOutlineShape(world, pos);
        return shape.isEmpty() ? FULL_BLOCK : shape.getBoundingBox();
    }

    @Nullable
    public static Direction frontFace(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof ChestBlock) return state.get(ChestBlock.FACING);
        if (block instanceof EnderChestBlock) return state.get(EnderChestBlock.FACING);
        if (block instanceof AbstractFurnaceBlock) return state.get(AbstractFurnaceBlock.FACING);
        if (block instanceof BarrelBlock) return state.get(BarrelBlock.FACING);
        if (block instanceof ShulkerBoxBlock) return state.get(ShulkerBoxBlock.FACING);
        return null;
    }

    public static double surface(Box box, Direction face) {
        return switch (face) {
            case NORTH -> box.minZ;
            case SOUTH -> box.maxZ;
            case WEST -> box.minX;
            case EAST -> box.maxX;
            case DOWN -> box.minY;
            case UP -> box.maxY;
        };
    }

    private static boolean isPositive(Direction face) {
        return face.getDirection() == Direction.AxisDirection.POSITIVE;
    }

    public static boolean fitsInsideHost(Box box, Direction face) {
        double surface = surface(box, face);
        return isPositive(face) ? surface <= 1 - DEPTH : surface >= DEPTH;
    }

    public static Direction.Axis thinAxis(Box box) {
        double sizeX = box.maxX - box.minX;
        double sizeY = box.maxY - box.minY;
        double sizeZ = box.maxZ - box.minZ;
        if (sizeX <= sizeY && sizeX <= sizeZ) return Direction.Axis.X;
        return sizeY <= sizeZ ? Direction.Axis.Y : Direction.Axis.Z;
    }

    @Nullable
    public static BlockPos partner(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock) {
            return state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER ? pos.down() : pos.up();
        }
        if (state.getBlock() instanceof ChestBlock
                && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
            return pos.offset(ChestBlock.getFacing(state));
        }
        return null;
    }

    public static BlockPos hostPos(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.getBlock() instanceof DoorBlock
                && state.get(DoorBlock.HALF) == DoubleBlockHalf.UPPER) {
            return pos.down();
        }
        if (state.getBlock() instanceof ChestBlock
                && state.get(ChestBlock.CHEST_TYPE) == ChestType.RIGHT) {
            return pos.offset(ChestBlock.getFacing(state));
        }
        return pos;
    }

    public static List<Direction> mountFaces(BlockView world, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        Direction front = frontFace(state);
        if (front != null) return List.of(front);
        if (state.getBlock() instanceof DoorBlock || state.getBlock() instanceof TrapdoorBlock) {
            Direction.Axis axis = thinAxis(hostBox(world, pos));
            return List.of(Direction.from(axis, Direction.AxisDirection.NEGATIVE),
                    Direction.from(axis, Direction.AxisDirection.POSITIVE));
        }
        return List.of();
    }

    @Nullable
    public static Direction handleDirection(BlockState state) {
        if (!(state.getBlock() instanceof DoorBlock)) return null;
        Direction facing = state.get(DoorBlock.FACING);
        boolean rightHinge = state.get(DoorBlock.HINGE) == DoorHinge.RIGHT;
        Direction hinge = rightHinge ? facing.rotateYClockwise() : facing.rotateYCounterclockwise();
        Direction handle = hinge.getOpposite();
        if (state.get(DoorBlock.OPEN)) {
            handle = rightHinge ? handle.rotateYClockwise() : handle.rotateYCounterclockwise();
        }
        return handle;
    }

    public static Vec3d surfaceAnchor(BlockView world, BlockPos pos, Direction face, double outward) {
        BlockState state = world.getBlockState(pos);
        Box box = hostBox(world, pos);
        double along = surface(box, face) + (isPositive(face) ? outward : -outward);

        double x = pos.getX() + (box.minX + box.maxX) / 2;
        double y = pos.getY() + (box.minY + box.maxY) / 2;
        double z = pos.getZ() + (box.minZ + box.maxZ) / 2;

        Direction handle = handleDirection(state);
        if (handle != null) {
            y = pos.getY() + 1.0;
            x += handle.getOffsetX() * DOOR_HANDLE_OFFSET;
            z += handle.getOffsetZ() * DOOR_HANDLE_OFFSET;
        } else if (state.getBlock() instanceof ChestBlock
                || state.getBlock() instanceof EnderChestBlock) {
            y += CHEST_LIFT;
            if (state.getBlock() instanceof ChestBlock
                    && state.get(ChestBlock.CHEST_TYPE) != ChestType.SINGLE) {
                Direction connected = ChestBlock.getFacing(state);
                x = pos.getX() + 0.5 + connected.getOffsetX() * 0.5;
                z = pos.getZ() + 0.5 + connected.getOffsetZ() * 0.5;
            }
        } else if (state.getBlock() instanceof BarrelBlock) {
            Direction front = state.get(BarrelBlock.FACING);
            Direction right = front.getAxis().isHorizontal()
                    ? front.rotateYCounterclockwise()
                    : Direction.EAST;
            x += right.getOffsetX() * BARREL_SIDE_OFFSET;
            z += right.getOffsetZ() * BARREL_SIDE_OFFSET;
        }

        return switch (face.getAxis()) {
            case X -> new Vec3d(pos.getX() + along, y, z);
            case Y -> new Vec3d(x, pos.getY() + along, z);
            case Z -> new Vec3d(x, y, pos.getZ() + along);
        };
    }

    public static boolean isAimedAtPanel(BlockView world, BlockPos pos, Direction face, Vec3d hitPos) {
        BlockPos host = hostPos(world, pos);
        if (!mountFaces(world, host).contains(face)) return false;

        Vec3d delta = hitPos.subtract(surfaceAnchor(world, host, face, 0));
        double acrossWidth;
        double acrossHeight;
        if (face.getAxis() == Direction.Axis.Y) {
            acrossWidth = delta.x;
            acrossHeight = delta.z;
        } else if (face.getAxis() == Direction.Axis.Z) {
            acrossWidth = delta.x;
            acrossHeight = delta.y;
        } else {
            acrossWidth = delta.z;
            acrossHeight = delta.y;
        }
        return Math.abs(acrossWidth) <= PANEL_HALF_WIDTH + AIM_TOLERANCE
                && Math.abs(acrossHeight) <= PANEL_HALF_HEIGHT + AIM_TOLERANCE;
    }

    public static Vec3d renderOrigin(BlockView world, BlockPos pos, Direction face) {
        Vec3d anchor = surfaceAnchor(world, pos, face, 0);
        Box box = hostBox(world, pos);
        double along = isPositive(face) ? surface(box, face) : surface(box, face) - 1;
        return switch (face.getAxis()) {
            case X -> new Vec3d(pos.getX() + along, anchor.y - 0.5, anchor.z - 0.5);
            case Y -> new Vec3d(anchor.x - 0.5, pos.getY() + along, anchor.z - 0.5);
            case Z -> new Vec3d(anchor.x - 0.5, anchor.y - 0.5, pos.getZ() + along);
        };
    }
}
