package kombuchamc.createsecurity.block.entity;

import kombuchamc.createsecurity.block.CameraDisplay;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.BlockView;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Queue;
import java.util.Set;

public final class CameraDisplayGroup {

    private static final Map<Direction, Direction> RIGHT_OF = Map.of(
            Direction.NORTH, Direction.EAST,
            Direction.SOUTH, Direction.WEST,
            Direction.WEST,  Direction.NORTH,
            Direction.EAST,  Direction.SOUTH
    );

    public final BlockPos anchor;
    public final Direction facing;
    public final Direction rightAxis;
    public final int width;
    public final int height;
    public final int col;
    public final int row;
    public final boolean valid;
    public final boolean active;
    public final Set<BlockPos> wallMembers;

    private CameraDisplayGroup(BlockPos anchor, Direction facing, Direction rightAxis,
                                int width, int height, int col, int row,
                                boolean valid, boolean active, Set<BlockPos> wallMembers) {
        this.anchor = anchor;
        this.facing = facing;
        this.rightAxis = rightAxis;
        this.width = width;
        this.height = height;
        this.col = col;
        this.row = row;
        this.valid = valid;
        this.active = active;
        this.wallMembers = wallMembers;
    }

    public static CameraDisplayGroup single(BlockPos pos, Direction facing) {
        Direction right = RIGHT_OF.getOrDefault(facing, Direction.EAST);
        Set<BlockPos> wall = new HashSet<>();
        wall.add(pos.toImmutable());
        return new CameraDisplayGroup(pos, facing, right, 1, 1, 0, 0, false, false, wall);
    }

    public Set<BlockPos> getMembers() {
        Set<BlockPos> members = new HashSet<>(width * height);
        if (!valid) return members;
        for (int r = 0; r < height; r++) {
            for (int c = 0; c < width; c++) {
                members.add(anchor.offset(rightAxis, c).offset(Direction.DOWN, r).toImmutable());
            }
        }
        return members;
    }

    public static CameraDisplayGroup compute(BlockView world, BlockPos start) {
        return compute(world, start, true);
    }

    public static CameraDisplayGroup computeTopology(BlockView world, BlockPos start) {
        return compute(world, start, false);
    }

    private static CameraDisplayGroup compute(BlockView world, BlockPos start, boolean requireSameCamera) {
        BlockState startState = world.getBlockState(start);
        if (!startState.isOf(RegisterModBlocks.CAMERA_DISPLAY_BLOCK)) {
            return single(start, Direction.NORTH);
        }
        Direction facing = startState.get(CameraDisplay.FACING);
        Direction right = RIGHT_OF.get(facing);
        if (right == null) return single(start, facing);

        BlockEntity startBe = world.getBlockEntity(start);
        if (!(startBe instanceof CameraDisplayBlockEntity startDisplay)) return single(start, facing);
        BlockPos linkedCamera = startDisplay.getLinkedCameraPos();
        if (requireSameCamera && linkedCamera == null) return single(start, facing);

        Set<BlockPos> members = new HashSet<>();
        Queue<BlockPos> queue = new ArrayDeque<>();
        members.add(start.toImmutable());
        queue.add(start.toImmutable());

        Direction[] steps = { right, right.getOpposite(), Direction.UP, Direction.DOWN };
        while (!queue.isEmpty()) {
            BlockPos cur = queue.poll();
            for (Direction step : steps) {
                BlockPos np = cur.offset(step);
                if (members.contains(np)) continue;
                BlockState ns = world.getBlockState(np);
                if (!ns.isOf(RegisterModBlocks.CAMERA_DISPLAY_BLOCK)) continue;
                if (ns.get(CameraDisplay.FACING) != facing) continue;
                BlockEntity nbe = world.getBlockEntity(np);
                if (!(nbe instanceof CameraDisplayBlockEntity nd)) continue;
                if (requireSameCamera && !Objects.equals(nd.getLinkedCameraPos(), linkedCamera)) continue;
                members.add(np);
                queue.add(np);
            }
        }

        if (members.size() < 6) {
            Set<BlockPos> wall = Set.copyOf(members);
            return new CameraDisplayGroup(
                    start.toImmutable(), facing, right,
                    1, 1, 0, 0,
                    false, false, wall);
        }

        int minRight = Integer.MAX_VALUE, maxRight = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (BlockPos m : members) {
            int r = dotRight(m, right);
            if (r < minRight) minRight = r;
            if (r > maxRight) maxRight = r;
            int y = m.getY();
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        int bboxWidth = maxRight - minRight + 1;
        int bboxHeight = maxY - minY + 1;

        boolean[][] occ = new boolean[bboxWidth][bboxHeight];
        for (BlockPos m : members) {
            int c = dotRight(m, right) - minRight;
            int r = maxY - m.getY();
            occ[c][r] = true;
        }

        boolean[][] anchored = new boolean[bboxWidth][bboxHeight];
        for (BlockPos m : members) {
            for (Direction d : Direction.values()) {
                if (world.getBlockState(m.offset(d)).isOf(RegisterModBlocks.CAMERA_LINK_BLOCK)) {
                    int c = dotRight(m, right) - minRight;
                    int r = maxY - m.getY();
                    anchored[c][r] = true;
                    break;
                }
            }
        }

        int myC = dotRight(start, right) - minRight;
        int myR = maxY - start.getY();

        int maxK = Math.min(bboxWidth / 3, bboxHeight / 2);
        boolean[][] claimed = new boolean[bboxWidth][bboxHeight];
        List<int[]> subRects = new ArrayList<>();

        List<int[]> anchoredCells = new ArrayList<>();
        for (int r = 0; r < bboxHeight; r++) {
            for (int c = 0; c < bboxWidth; c++) {
                if (anchored[c][r]) anchoredCells.add(new int[]{c, r});
            }
        }
        anchoredCells.sort(Comparator.<int[]>comparingInt(a -> a[1]).thenComparingInt(a -> a[0]));

        for (int[] anc : anchoredCells) {
            int ac = anc[0], ar = anc[1];
            if (claimed[ac][ar]) continue;
            int[] best = findLargestSubRectContaining(occ, claimed, bboxWidth, bboxHeight, maxK, ac, ar);
            if (best != null) {
                claimRegion(claimed, best[0], best[1], best[2], best[3]);
                subRects.add(best);
            }
        }

        while (true) {
            int[] best = findLargestSubRectAnywhere(occ, claimed, bboxWidth, bboxHeight, maxK);
            if (best == null) break;
            claimRegion(claimed, best[0], best[1], best[2], best[3]);
            subRects.add(best);
        }

        int myC0 = -1, myR0 = -1, mySubW = -1, mySubH = -1;
        for (int[] sr : subRects) {
            if (myC >= sr[0] && myC < sr[0] + sr[2]
             && myR >= sr[1] && myR < sr[1] + sr[3]) {
                myC0 = sr[0]; myR0 = sr[1]; mySubW = sr[2]; mySubH = sr[3];
                break;
            }
        }

        Set<BlockPos> wall = Set.copyOf(members);

        if (myC0 < 0) {

            if (!subRects.isEmpty()) {
                return new CameraDisplayGroup(
                        start.toImmutable(), facing, right,
                        1, 1, 0, 0,
                        true, false, wall);
            }
            return new CameraDisplayGroup(
                    start.toImmutable(), facing, right,
                    1, 1, 0, 0,
                    false, false, wall);
        }

        int anchorRDot = minRight + myC0;
        int anchorY = maxY - myR0;
        BlockPos anchor = null;
        for (BlockPos m : members) {
            if (m.getY() == anchorY && dotRight(m, right) == anchorRDot) {
                anchor = m.toImmutable();
                break;
            }
        }
        if (anchor == null) return single(start, facing);

        return new CameraDisplayGroup(
                anchor, facing, right, mySubW, mySubH,
                myC - myC0, myR - myR0,
                true, true, wall);
    }

    private static int[] findLargestSubRectContaining(boolean[][] occ, boolean[][] claimed,
                                                       int bboxWidth, int bboxHeight, int maxK,
                                                       int ac, int ar) {
        for (int k = maxK; k >= 1; k--) {
            int subW = 3 * k;
            int subH = 2 * k;
            for (int r0 = 0; r0 + subH <= bboxHeight; r0++) {
                for (int c0 = 0; c0 + subW <= bboxWidth; c0++) {
                    if (ac < c0 || ac >= c0 + subW) continue;
                    if (ar < r0 || ar >= r0 + subH) continue;
                    if (!isFilled(occ, c0, r0, subW, subH)) continue;
                    if (overlapsClaimed(claimed, c0, r0, subW, subH)) continue;
                    return new int[]{c0, r0, subW, subH};
                }
            }
        }
        return null;
    }

    private static int[] findLargestSubRectAnywhere(boolean[][] occ, boolean[][] claimed,
                                                     int bboxWidth, int bboxHeight, int maxK) {
        for (int k = maxK; k >= 1; k--) {
            int subW = 3 * k;
            int subH = 2 * k;
            for (int r0 = 0; r0 + subH <= bboxHeight; r0++) {
                for (int c0 = 0; c0 + subW <= bboxWidth; c0++) {
                    if (!isFilled(occ, c0, r0, subW, subH)) continue;
                    if (overlapsClaimed(claimed, c0, r0, subW, subH)) continue;
                    return new int[]{c0, r0, subW, subH};
                }
            }
        }
        return null;
    }

    private static void claimRegion(boolean[][] claimed, int c0, int r0, int w, int h) {
        for (int dr = 0; dr < h; dr++) {
            for (int dc = 0; dc < w; dc++) {
                claimed[c0 + dc][r0 + dr] = true;
            }
        }
    }

    private static boolean overlapsClaimed(boolean[][] claimed, int c0, int r0, int w, int h) {
        for (int dr = 0; dr < h; dr++) {
            for (int dc = 0; dc < w; dc++) {
                if (claimed[c0 + dc][r0 + dr]) return true;
            }
        }
        return false;
    }

    private static boolean isFilled(boolean[][] occ, int c0, int r0, int w, int h) {
        for (int dr = 0; dr < h; dr++) {
            for (int dc = 0; dc < w; dc++) {
                if (!occ[c0 + dc][r0 + dr]) return false;
            }
        }
        return true;
    }

    private static int dotRight(BlockPos pos, Direction right) {
        return pos.getX() * right.getOffsetX() + pos.getZ() * right.getOffsetZ();
    }
}

