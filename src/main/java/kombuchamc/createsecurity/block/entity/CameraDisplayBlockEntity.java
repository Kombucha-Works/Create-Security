package kombuchamc.createsecurity.block.entity;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class CameraDisplayBlockEntity extends BlockEntity implements CameraFeedView {

    private static final Set<CameraDisplayBlockEntity> CLIENT_INSTANCES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static Set<CameraDisplayBlockEntity> getClientInstances() {
        return CLIENT_INSTANCES;
    }

    private static final java.util.Map<BlockPos, Set<BlockPos>> SERVER_DISPLAYS_BY_CAMERA =
            new java.util.concurrent.ConcurrentHashMap<>();

    public static void clearAllForCamera(net.minecraft.server.world.ServerWorld world, BlockPos cameraPos) {
        Set<BlockPos> displays = SERVER_DISPLAYS_BY_CAMERA.remove(cameraPos);
        if (displays == null) return;
        for (BlockPos displayPos : displays) {
            BlockEntity be = world.getBlockEntity(displayPos);
            if (be instanceof CameraDisplayBlockEntity display) {
                display.setLinkedCamera(null, null);
            }
        }
    }

    public static void refreshFisheyeForCamera(net.minecraft.server.world.ServerWorld world,
                                                BlockPos cameraPos, boolean hasFisheye) {
        Set<BlockPos> displays = SERVER_DISPLAYS_BY_CAMERA.get(cameraPos);
        if (displays == null) return;
        for (BlockPos displayPos : displays) {
            BlockEntity be = world.getBlockEntity(displayPos);
            if (be instanceof CameraDisplayBlockEntity display
                    && display.linkedCameraPos != null
                    && display.linkedCameraLensDir != null) {
                display.setLinkedCamera(display.linkedCameraPos, display.linkedCameraLensDir, hasFisheye,
                        display.linkBlockPos, display.cameraCount, display.cameraLabel,
                        display.nextCameraPos, display.prevCameraPos,
                        display.nextCameraFisheye, display.prevCameraFisheye);
            }
        }
    }

    @Nullable private BlockPos linkedCameraPos = null;

    @Nullable private Direction linkedCameraLensDir = null;

    private boolean linkedCameraHasFisheye = false;

    @Nullable private BlockPos linkBlockPos = null;

    private int cameraCount = 0;

    @Nullable private String cameraLabel = null;

    @Nullable private BlockPos nextCameraPos = null;
    @Nullable private BlockPos prevCameraPos = null;
    private boolean nextCameraFisheye = false;
    private boolean prevCameraFisheye = false;

    @Nullable private CameraDisplayGroup cachedGroup = null;

    private long lastSeenNanos = 0L;

    public void markSeenNow() {
        lastSeenNanos = System.nanoTime();
    }

    @Override
    public BlockPos getFeedPos() {
        return pos;
    }

    public long getLastSeenNanos() {
        return lastSeenNanos;
    }

    public CameraDisplayGroup getGroup() {
        if (cachedGroup == null && world != null) {
            cachedGroup = CameraDisplayGroup.compute(world, pos);
        }
        return cachedGroup;
    }

    public void invalidateGroup() {
        cachedGroup = null;
    }

    public static void invalidateAllClientGroups() {
        for (CameraDisplayBlockEntity be : CLIENT_INSTANCES) {
            be.cachedGroup = null;
        }
    }

    public CameraDisplayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA_DISPLAY_BLOCK_ENTITY, pos, state);
    }

    @Nullable
    public BlockPos getLinkedCameraPos() {
        return linkedCameraPos;
    }

    @Nullable
    public Direction getLinkedCameraLensDir() {
        return linkedCameraLensDir;
    }

    public boolean isLinkedCameraFisheye() {
        return linkedCameraHasFisheye;
    }

    public void setLinkedCamera(@Nullable BlockPos cameraPos, @Nullable Direction lensDir, boolean hasFisheye,
                                @Nullable BlockPos linkPos, int cameraCount, @Nullable String cameraLabel,
                                @Nullable BlockPos nextCameraPos, @Nullable BlockPos prevCameraPos,
                                boolean nextCameraFisheye, boolean prevCameraFisheye) {
        BlockPos oldCamera = this.linkedCameraPos;
        boolean changed = !java.util.Objects.equals(oldCamera, cameraPos)
                       || !java.util.Objects.equals(this.linkedCameraLensDir, lensDir)
                       || this.linkedCameraHasFisheye != hasFisheye
                       || !java.util.Objects.equals(this.linkBlockPos, linkPos)
                       || this.cameraCount != cameraCount
                       || !java.util.Objects.equals(this.cameraLabel, cameraLabel)
                       || !java.util.Objects.equals(this.nextCameraPos, nextCameraPos)
                       || !java.util.Objects.equals(this.prevCameraPos, prevCameraPos)
                       || this.nextCameraFisheye != nextCameraFisheye
                       || this.prevCameraFisheye != prevCameraFisheye;

        if (!changed) return;
        this.linkedCameraPos = cameraPos;
        this.linkedCameraLensDir = lensDir;
        this.linkedCameraHasFisheye = hasFisheye;
        this.linkBlockPos = linkPos;
        this.cameraCount = cameraCount;
        this.cameraLabel = cameraLabel;
        this.nextCameraPos = nextCameraPos;
        this.prevCameraPos = prevCameraPos;
        this.nextCameraFisheye = nextCameraFisheye;
        this.prevCameraFisheye = prevCameraFisheye;
        markDirty();
        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {

            if (oldCamera != null) {
                Set<BlockPos> oldSet = SERVER_DISPLAYS_BY_CAMERA.get(oldCamera);
                if (oldSet != null) {
                    oldSet.remove(this.pos);
                    if (oldSet.isEmpty()) SERVER_DISPLAYS_BY_CAMERA.remove(oldCamera);
                }
            }
            if (cameraPos != null) {
                SERVER_DISPLAYS_BY_CAMERA
                        .computeIfAbsent(cameraPos.toImmutable(), k -> Collections.newSetFromMap(new ConcurrentHashMap<>()))
                        .add(this.pos.toImmutable());
            }

            kombuchamc.createsecurity.network.ModPackets.sendDisplayLinkSync(
                    serverWorld, this.pos, cameraPos, lensDir, hasFisheye, cameraCount, cameraLabel,
                    nextCameraPos, prevCameraPos, nextCameraFisheye, prevCameraFisheye);
        }
    }

    public void setLinkedCamera(@Nullable BlockPos cameraPos, @Nullable Direction lensDir) {
        setLinkedCamera(cameraPos, lensDir, false, null, 0, null, null, null, false, false);
    }

    @Nullable
    public BlockPos getLinkBlockPos() {
        return linkBlockPos;
    }

    public int getCameraCount() {
        return cameraCount;
    }

    @Nullable
    public String getCameraLabel() {
        return cameraLabel;
    }

    @Nullable
    public BlockPos getNextCameraPos() {
        return nextCameraPos;
    }

    @Nullable
    public BlockPos getPrevCameraPos() {
        return prevCameraPos;
    }

    public void applyLinkSyncFromServer(@Nullable BlockPos cameraPos, @Nullable Direction lensDir,
                                        boolean hasFisheye, int cameraCount, @Nullable String cameraLabel,
                                        @Nullable BlockPos nextCameraPos, @Nullable BlockPos prevCameraPos,
                                        boolean nextCameraFisheye, boolean prevCameraFisheye) {
        this.linkedCameraPos = cameraPos;
        this.linkedCameraLensDir = lensDir;
        this.linkedCameraHasFisheye = hasFisheye;
        this.cameraCount = cameraCount;
        this.cameraLabel = cameraLabel;
        this.nextCameraPos = nextCameraPos;
        this.prevCameraPos = prevCameraPos;
        this.nextCameraFisheye = nextCameraFisheye;
        this.prevCameraFisheye = prevCameraFisheye;

        invalidateAllClientGroups();
    }

    public void applyPredictedCameraCycle(int delta, BlockPos cameraPos, @Nullable Direction lensDir) {
        BlockPos oldCamera = this.linkedCameraPos;
        boolean oldFisheye = this.linkedCameraHasFisheye;
        this.linkedCameraPos = cameraPos;
        this.linkedCameraLensDir = lensDir;
        this.linkedCameraHasFisheye = delta > 0 ? nextCameraFisheye : prevCameraFisheye;
        this.cameraLabel = null;

        if (delta > 0) {
            this.prevCameraPos = oldCamera;
            this.prevCameraFisheye = oldFisheye;
            this.nextCameraPos = cameraCount == 2 ? oldCamera : null;
            this.nextCameraFisheye = cameraCount == 2 && oldFisheye;
        } else {
            this.nextCameraPos = oldCamera;
            this.nextCameraFisheye = oldFisheye;
            this.prevCameraPos = cameraCount == 2 ? oldCamera : null;
            this.prevCameraFisheye = cameraCount == 2 && oldFisheye;
        }
        invalidateAllClientGroups();
    }

    @Override
    public void setWorld(net.minecraft.world.World world) {
        super.setWorld(world);
        if (world.isClient) {
            CLIENT_INSTANCES.add(this);
        }
    }

    @Override
    public void markRemoved() {
        super.markRemoved();
        CLIENT_INSTANCES.remove(this);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        if (linkedCameraPos != null) {
            nbt.putLong("linkedCamera", linkedCameraPos.asLong());
        }
        if (linkedCameraLensDir != null) {
            nbt.putInt("linkedCameraLensDir", linkedCameraLensDir.getId());
        }
        nbt.putBoolean("linkedCameraHasFisheye", linkedCameraHasFisheye);
        if (linkBlockPos != null) {
            nbt.putLong("linkBlockPos", linkBlockPos.asLong());
        }
        nbt.putInt("cameraCount", cameraCount);
        if (cameraLabel != null) {
            nbt.putString("cameraLabel", cameraLabel);
        }
        if (nextCameraPos != null) {
            nbt.putLong("nextCameraPos", nextCameraPos.asLong());
        }
        if (prevCameraPos != null) {
            nbt.putLong("prevCameraPos", prevCameraPos.asLong());
        }
        nbt.putBoolean("nextCameraFisheye", nextCameraFisheye);
        nbt.putBoolean("prevCameraFisheye", prevCameraFisheye);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        linkedCameraPos = nbt.contains("linkedCamera")
                ? BlockPos.fromLong(nbt.getLong("linkedCamera"))
                : null;
        linkedCameraLensDir = nbt.contains("linkedCameraLensDir")
                ? Direction.byId(nbt.getInt("linkedCameraLensDir"))
                : null;
        linkedCameraHasFisheye = nbt.getBoolean("linkedCameraHasFisheye");
        linkBlockPos = nbt.contains("linkBlockPos")
                ? BlockPos.fromLong(nbt.getLong("linkBlockPos"))
                : null;
        cameraCount = nbt.getInt("cameraCount");
        cameraLabel = nbt.contains("cameraLabel") ? nbt.getString("cameraLabel") : null;
        nextCameraPos = nbt.contains("nextCameraPos")
                ? BlockPos.fromLong(nbt.getLong("nextCameraPos"))
                : null;
        prevCameraPos = nbt.contains("prevCameraPos")
                ? BlockPos.fromLong(nbt.getLong("prevCameraPos"))
                : null;
        nextCameraFisheye = nbt.getBoolean("nextCameraFisheye");
        prevCameraFisheye = nbt.getBoolean("prevCameraFisheye");
    }

    @Override
    public NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public @Nullable Packet<ClientPlayPacketListener> toUpdatePacket() {
        return BlockEntityUpdateS2CPacket.create(this);
    }
}

