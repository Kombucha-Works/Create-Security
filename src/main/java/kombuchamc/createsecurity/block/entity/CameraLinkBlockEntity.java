package kombuchamc.createsecurity.block.entity;

import kombuchamc.createsecurity.block.camera.FrequencyManager;
import kombuchamc.createsecurity.screen.CameraLinkScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

public class CameraLinkBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory, ImplementedInventory {

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
    private UUID ownerUUID = null;

    private int selectedCameraIndex = 0;

    private final Set<BlockPos> previouslyLinked = new HashSet<>();

    private long lastSeenFreqRevision = Long.MIN_VALUE;

    private static final int FULL_REFRESH_SECONDS = 10;
    private int secondsSinceRefresh = 0;

    public CameraLinkBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA_LINK_BLOCK_ENTITY, pos, state);
    }

    public void setOwner(UUID uuid) {
        this.ownerUUID = uuid;
        markDirty();
    }

    public UUID getOwnerUUID() {
        return ownerUUID;
    }

    public boolean isOwner(PlayerEntity player) {
        return ownerUUID != null && ownerUUID.equals(player.getUuid());
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, inventory);
        if (ownerUUID != null) {
            nbt.putUuid("ownerUUID", ownerUUID);
        }
        nbt.putInt("selectedCameraIndex", selectedCameraIndex);
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, inventory);
        if (nbt.containsUuid("ownerUUID")) {
            ownerUUID = nbt.getUuid("ownerUUID");
        }
        selectedCameraIndex = nbt.getInt("selectedCameraIndex");
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Camera Link");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CameraLinkScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world != null && !world.isClient) {
            updateAdjacentDisplays();
        }
    }

    private void updateAdjacentDisplays() {
        if (world == null) return;

        if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
            kombuchamc.createsecurity.block.camera.FrequencyPersistentState.getOrCreate(serverWorld);
        }
        String freq = FrequencyManager.computeFrequency(inventory.get(0), inventory.get(1));
        List<BlockPos> cams = freq == null ? java.util.Collections.emptyList() : sortedCameras(freq);
        int cameraCount = cams.size();
        BlockPos firstCamera = cams.isEmpty()
                ? null : cams.get(Math.floorMod(selectedCameraIndex, cameraCount));

        BlockPos nextCamera = cameraCount > 1
                ? cams.get(Math.floorMod(selectedCameraIndex + 1, cameraCount)) : null;
        BlockPos prevCamera = cameraCount > 1
                ? cams.get(Math.floorMod(selectedCameraIndex - 1, cameraCount)) : null;
        boolean nextFisheye = resolveCameraHasFisheye(nextCamera);
        boolean prevFisheye = resolveCameraHasFisheye(prevCamera);
        Direction lensDir = resolveCameraLensDir(firstCamera);
        boolean hasFisheye = resolveCameraHasFisheye(firstCamera);
        String cameraLabel = resolveCameraLabel(freq, firstCamera);

        Set<BlockPos> nowLinked = new HashSet<>();
        for (Direction dir : Direction.values()) {
            BlockPos adjPos = pos.offset(dir);
            if (nowLinked.contains(adjPos)) continue;
            BlockEntity adjacent = world.getBlockEntity(adjPos);
            if (!(adjacent instanceof CameraDisplayBlockEntity adjDisplay)) continue;

            CameraDisplayGroup topo = CameraDisplayGroup.computeTopology(world, adjPos);
            if (topo.valid && topo.active) {

                for (BlockPos memberPos : topo.getMembers()) {
                    BlockEntity memberBe = world.getBlockEntity(memberPos);
                    if (memberBe instanceof CameraDisplayBlockEntity memberDisplay) {
                        memberDisplay.setLinkedCamera(firstCamera, lensDir, hasFisheye, pos,
                                cameraCount, cameraLabel,
                                nextCamera, prevCamera, nextFisheye, prevFisheye);
                    }
                    nowLinked.add(memberPos);
                }
            } else {
                adjDisplay.setLinkedCamera(firstCamera, lensDir, hasFisheye, pos,
                        cameraCount, cameraLabel,
                        nextCamera, prevCamera, nextFisheye, prevFisheye);
                nowLinked.add(adjPos);
            }
        }

        for (BlockPos oldPos : previouslyLinked) {
            if (nowLinked.contains(oldPos)) continue;
            BlockEntity be = world.getBlockEntity(oldPos);
            if (be instanceof CameraDisplayBlockEntity oldDisplay
                    && oldDisplay.getLinkedCameraPos() != null) {
                oldDisplay.setLinkedCamera(null, null);
            }
        }
        previouslyLinked.clear();
        previouslyLinked.addAll(nowLinked);

        lastSeenFreqRevision = FrequencyManager.getRevision();
        secondsSinceRefresh = 0;
    }

    public void clearAllLinkedDisplays() {
        if (world == null) return;
        for (BlockPos oldPos : previouslyLinked) {
            BlockEntity be = world.getBlockEntity(oldPos);
            if (be instanceof CameraDisplayBlockEntity oldDisplay
                    && oldDisplay.getLinkedCameraPos() != null) {
                oldDisplay.setLinkedCamera(null, null);
            }
        }
        previouslyLinked.clear();
    }

    private boolean resolveCameraHasFisheye(@org.jetbrains.annotations.Nullable BlockPos cameraPos) {
        if (cameraPos == null || world == null) return false;
        BlockEntity be = world.getBlockEntity(cameraPos);
        if (!(be instanceof CameraBlockEntity camera)) return false;

        ItemStack lensStack = camera.getStack(3);
        return lensStack.isOf(kombuchamc.createsecurity.items.RegisterModItems.FISHEYE_LENS);
    }

    private List<BlockPos> sortedCameras(String freq) {
        return FrequencyManager.sortedCameras(world, freq);
    }

    private String resolveCameraLabel(String freq, @org.jetbrains.annotations.Nullable BlockPos cameraPos) {
        if (cameraPos == null || world == null) return null;
        BlockEntity be = world.getBlockEntity(cameraPos);
        if (be instanceof CameraBlockEntity camera && camera.getCustomName() != null) {
            return camera.getCustomName();
        }
        int index = sortedCameras(freq).indexOf(cameraPos);
        return "Camera " + (index + 1);
    }

    public void cycleCamera(int delta) {
        selectedCameraIndex += delta;
        markDirty();
    }

    @org.jetbrains.annotations.Nullable
    private Direction resolveCameraLensDir(@org.jetbrains.annotations.Nullable BlockPos cameraPos) {
        if (cameraPos == null || world == null) return null;
        BlockState camState = world.getBlockState(cameraPos);
        if (!camState.isOf(kombuchamc.createsecurity.block.RegisterModBlocks.CAMERA_BLOCK)) return null;
        Direction facing = camState.get(kombuchamc.createsecurity.block.camera.CameraBlock.FACING);
        if (facing == null) return null;

        return facing.getOpposite();
    }

    private int tickCounter = 0;

    public void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;

        if (++tickCounter >= 20) {
            tickCounter = 0;
            if (FrequencyManager.getRevision() == lastSeenFreqRevision
                    && ++secondsSinceRefresh < FULL_REFRESH_SECONDS) {
                return;
            }
            updateAdjacentDisplays();
        }
    }
}
