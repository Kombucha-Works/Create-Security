package kombuchamc.createsecurity.block.entity;

import kombuchamc.createsecurity.block.camera.CameraBlock;
import kombuchamc.createsecurity.block.camera.FrequencyManager;
import kombuchamc.createsecurity.block.camera.FrequencyPersistentState;
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
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class MonitorBlockEntity extends BlockEntity
        implements ExtendedScreenHandlerFactory, ImplementedInventory, CameraFeedView {

    private static final Set<MonitorBlockEntity> CLIENT_INSTANCES =
            Collections.newSetFromMap(new ConcurrentHashMap<>());

    public static Set<MonitorBlockEntity> getClientInstances() {
        return CLIENT_INSTANCES;
    }

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(2, ItemStack.EMPTY);
    private UUID ownerUUID = null;

    private int selectedCameraIndex = 0;

    @Nullable private BlockPos linkedCameraPos = null;
    @Nullable private Direction linkedCameraLensDir = null;
    private boolean linkedCameraHasFisheye = false;
    private int cameraCount = 0;
    @Nullable private String cameraLabel = null;

    private boolean resolving = false;
    private int tickCounter = 0;

    private long lastSeenNanos = 0L;

    public MonitorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MONITOR_BLOCK_ENTITY, pos, state);
    }

    public void markSeenNow() {
        lastSeenNanos = System.nanoTime();
    }

    @Override
    public BlockPos getFeedPos() {
        return pos;
    }

    @Override
    public long getLastSeenNanos() {
        return lastSeenNanos;
    }

    public void setOwner(UUID uuid) {
        this.ownerUUID = uuid;
        markDirty();
    }

    public boolean isOwner(PlayerEntity player) {
        return ownerUUID != null && ownerUUID.equals(player.getUuid());
    }

    @Override
    @Nullable
    public BlockPos getLinkedCameraPos() {
        return linkedCameraPos;
    }

    @Override
    @Nullable
    public Direction getLinkedCameraLensDir() {
        return linkedCameraLensDir;
    }

    @Override
    public boolean isLinkedCameraFisheye() {
        return linkedCameraHasFisheye;
    }

    public int getCameraCount() {
        return cameraCount;
    }

    @Nullable
    public String getCameraLabel() {
        return cameraLabel;
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    public void setWorld(World world) {
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
    public void markDirty() {
        super.markDirty();

        if (!resolving && world != null && !world.isClient) {
            resolveAndSync();
        }
    }

    public void cycleCamera(int delta) {
        selectedCameraIndex += delta;
        resolveAndSync();
    }

    public void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;

        if (++tickCounter >= 20) {
            tickCounter = 0;
            resolveAndSync();
        }
    }

    private void resolveAndSync() {
        if (!(world instanceof ServerWorld serverWorld)) return;
        resolving = true;
        try {

            FrequencyPersistentState.getOrCreate(serverWorld);
            String freq = FrequencyManager.computeFrequency(inventory.get(0), inventory.get(1));
            List<BlockPos> cams = freq == null ? Collections.emptyList() : sortedCameras(freq);
            int count = cams.size();
            BlockPos cam = cams.isEmpty()
                    ? null : cams.get(Math.floorMod(selectedCameraIndex, count));
            Direction lensDir = resolveCameraLensDir(cam);
            boolean fisheye = resolveCameraHasFisheye(cam);
            String label = resolveCameraLabel(cams, cam);

            boolean changed = !Objects.equals(linkedCameraPos, cam)
                    || !Objects.equals(linkedCameraLensDir, lensDir)
                    || linkedCameraHasFisheye != fisheye
                    || cameraCount != count
                    || !Objects.equals(cameraLabel, label);
            if (!changed) return;
            linkedCameraPos = cam;
            linkedCameraLensDir = lensDir;
            linkedCameraHasFisheye = fisheye;
            cameraCount = count;
            cameraLabel = label;
            markDirty();
            serverWorld.updateListeners(pos, getCachedState(), getCachedState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        } finally {
            resolving = false;
        }
    }

    private List<BlockPos> sortedCameras(String freq) {
        return FrequencyManager.sortedCameras(world, freq);
    }

    @Nullable
    private Direction resolveCameraLensDir(@Nullable BlockPos cameraPos) {
        if (cameraPos == null || world == null) return null;
        BlockState camState = world.getBlockState(cameraPos);
        if (!camState.isOf(kombuchamc.createsecurity.block.RegisterModBlocks.CAMERA_BLOCK)) return null;

        return camState.get(CameraBlock.FACING).getOpposite();
    }

    private boolean resolveCameraHasFisheye(@Nullable BlockPos cameraPos) {
        if (cameraPos == null || world == null) return false;
        if (!(world.getBlockEntity(cameraPos) instanceof CameraBlockEntity camera)) return false;
        return camera.isFisheyeLensInstalled();
    }

    @Nullable
    private String resolveCameraLabel(List<BlockPos> cams, @Nullable BlockPos cameraPos) {
        if (cameraPos == null || world == null) return null;
        if (world.getBlockEntity(cameraPos) instanceof CameraBlockEntity camera
                && camera.getCustomName() != null) {
            return camera.getCustomName();
        }
        return "Camera " + (cams.indexOf(cameraPos) + 1);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(this.pos);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.create-security.monitor");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CameraLinkScreenHandler(syncId, playerInventory, (net.minecraft.inventory.Inventory) this);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, inventory);
        if (ownerUUID != null) {
            nbt.putUuid("ownerUUID", ownerUUID);
        }
        nbt.putInt("selectedCameraIndex", selectedCameraIndex);
        if (linkedCameraPos != null) {
            nbt.putLong("linkedCamera", linkedCameraPos.asLong());
        }
        if (linkedCameraLensDir != null) {
            nbt.putInt("linkedCameraLensDir", linkedCameraLensDir.getId());
        }
        nbt.putBoolean("linkedCameraHasFisheye", linkedCameraHasFisheye);
        nbt.putInt("cameraCount", cameraCount);
        if (cameraLabel != null) {
            nbt.putString("cameraLabel", cameraLabel);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, inventory);
        ownerUUID = nbt.containsUuid("ownerUUID") ? nbt.getUuid("ownerUUID") : null;
        selectedCameraIndex = nbt.getInt("selectedCameraIndex");
        linkedCameraPos = nbt.contains("linkedCamera")
                ? BlockPos.fromLong(nbt.getLong("linkedCamera")) : null;
        linkedCameraLensDir = nbt.contains("linkedCameraLensDir")
                ? Direction.byId(nbt.getInt("linkedCameraLensDir")) : null;
        linkedCameraHasFisheye = nbt.getBoolean("linkedCameraHasFisheye");
        cameraCount = nbt.getInt("cameraCount");
        cameraLabel = nbt.contains("cameraLabel") ? nbt.getString("cameraLabel") : null;
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

