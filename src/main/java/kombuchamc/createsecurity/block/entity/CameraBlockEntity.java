package kombuchamc.createsecurity.block.entity;

import kombuchamc.createsecurity.block.camera.CameraBlock;
import kombuchamc.createsecurity.block.camera.FrequencyManager;
import kombuchamc.createsecurity.screen.CameraScreenHandler;
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
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import software.bernie.geckolib.animatable.GeoBlockEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;
import java.util.List;
import java.util.UUID;

public class CameraBlockEntity extends BlockEntity implements ExtendedScreenHandlerFactory, ImplementedInventory, GeoBlockEntity {

    private static final int FREQ_SLOT1 = 0;
    private static final int FREQ_SLOT2 = 1;
    private static final int FILTER_SLOT = 2;

    private static final RawAnimation SWEEP_ANIMATION = RawAnimation.begin().thenLoop("animation.camera.sweep");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    private UUID ownerUUID = null;

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

    @org.jetbrains.annotations.Nullable
    private String customName = null;

    public void setCustomName(@org.jetbrains.annotations.Nullable String name) {
        this.customName = name;
        markDirty();
    }

    @org.jetbrains.annotations.Nullable
    public String getCustomName() {
        return customName;
    }

    private long placedAt = 0L;

    public void setPlacedAt(long time) {
        this.placedAt = time;
        markDirty();
    }

    public long getPlacedAt() {
        return placedAt;
    }

    @org.jetbrains.annotations.Nullable
    private UUID portableDisplayId = null;

    public void setPortableDisplayId(@org.jetbrains.annotations.Nullable UUID id) {
        this.portableDisplayId = id;
        markDirty();
    }

    @org.jetbrains.annotations.Nullable
    public UUID getPortableDisplayId() {
        return portableDisplayId;
    }

    private static final int DETECTION_RANGE = 16;
    private static final float DETECTION_ANGLE = 90f;
    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(4, ItemStack.EMPTY);

    private boolean detectionEnabled = false;

    public boolean isDetectionEnabled() { return detectionEnabled; }

    private void updateRedstoneNeighbors(net.minecraft.world.World world) {
        net.minecraft.block.Block block = getCachedState().getBlock();
        world.updateNeighborsAlways(pos, block);
        for (Direction dir : Direction.values()) {
            world.updateNeighborsAlways(pos.offset(dir), block);
        }
    }

    public void setDetectionEnabled(boolean enabled) {
        this.detectionEnabled = enabled;
        markDirty();
        if (world != null) {
            updateRedstoneNeighbors(world);

            world.updateListeners(pos, getCachedState(), getCachedState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }

    private final java.util.Set<UUID> flaggedPlayers = new java.util.HashSet<>();

    public java.util.Set<UUID> getFlaggedPlayers() {
        return flaggedPlayers;
    }

    private int tickCounter = 0;
    private boolean isDetecting = false;

    public boolean isDetecting() {
        return isDetecting;
    }

    public CameraBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.CAMERA_BLOCK_ENTITY, pos, state);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "camera_controller", 0, state -> {

            state.getController().setAnimation(SWEEP_ANIMATION);
            return PlayState.CONTINUE;
        }));
    }

    public void onInventoryChanged() {
        FrequencyManager.updateCamera(pos, inventory.get(FREQ_SLOT1), inventory.get(FREQ_SLOT2), placedAt);
        markDirty();
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }

    @Override
    public double getTick(Object animatable) {
        return System.currentTimeMillis() / 50.0;
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }
    @Override
    public void markDirty() {
        super.markDirty();

        if (world != null && !world.isClient && !(world instanceof ServerWorld)) {
            FrequencyManager.updateCamera(pos, inventory.get(FREQ_SLOT1), inventory.get(FREQ_SLOT2), placedAt);
        }
        if (world instanceof ServerWorld serverWorld) {
            FrequencyManager.updateCamera(pos, inventory.get(FREQ_SLOT1), inventory.get(FREQ_SLOT2), placedAt);
            FrequencyManager.markDirty(serverWorld);

            CameraDisplayBlockEntity.refreshFisheyeForCamera(serverWorld, this.pos,
                    isFisheyeLensInstalled());
        }
    }

    public boolean isFisheyeLensInstalled() {
        ItemStack lensStack = inventory.get(3);
        return lensStack.isOf(kombuchamc.createsecurity.items.RegisterModItems.FISHEYE_LENS);
    }

    @Override
    protected void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        Inventories.writeNbt(nbt, inventory);
        nbt.putBoolean("isDetecting", isDetecting);
        nbt.putBoolean("detectionEnabled", detectionEnabled);
        net.minecraft.nbt.NbtList flaggedList = new net.minecraft.nbt.NbtList();
        for (UUID id : flaggedPlayers) {
            flaggedList.add(net.minecraft.nbt.NbtHelper.fromUuid(id));
        }
        nbt.put("flaggedPlayers", flaggedList);
        if (ownerUUID != null) {
            nbt.putUuid("ownerUUID", ownerUUID);
        }
        if (customName != null) {
            nbt.putString("customName", customName);
        }
        nbt.putLong("placedAt", placedAt);
        if (portableDisplayId != null) {
            nbt.putUuid("portableDisplayId", portableDisplayId);
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        Inventories.readNbt(nbt, inventory);
        isDetecting = nbt.getBoolean("isDetecting");

        detectionEnabled = nbt.getBoolean("detectionEnabled");
        flaggedPlayers.clear();
        for (net.minecraft.nbt.NbtElement e
                : nbt.getList("flaggedPlayers", net.minecraft.nbt.NbtElement.INT_ARRAY_TYPE)) {
            flaggedPlayers.add(net.minecraft.nbt.NbtHelper.toUuid(e));
        }
        if (nbt.containsUuid("ownerUUID")) {
            ownerUUID = nbt.getUuid("ownerUUID");
        }
        customName = nbt.contains("customName") ? nbt.getString("customName") : null;
        placedAt = nbt.getLong("placedAt");
        portableDisplayId = nbt.containsUuid("portableDisplayId")
                ? nbt.getUuid("portableDisplayId") : null;
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity serverPlayerEntity, PacketByteBuf packetByteBuf) {
        packetByteBuf.writeBlockPos(this.pos);

        packetByteBuf.writeBoolean(this.detectionEnabled);
    }

    @Override
    public net.minecraft.nbt.NbtCompound toInitialChunkDataNbt() {
        return createNbt();
    }

    @Override
    public net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> toUpdatePacket() {
        return net.minecraft.network.packet.s2c.play.BlockEntityUpdateS2CPacket.create(this);
    }

    @Override
    public Text getDisplayName() {
        return Text.literal("Camera");
    }

    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new CameraScreenHandler(syncId, playerInventory, this);
    }

    private boolean chunkTicketAdded = false;

    public void tick(World world, BlockPos pos, BlockState state) {
        if (world.isClient) return;

        if (!chunkTicketAdded) {
            chunkTicketAdded = true;
            if (world instanceof ServerWorld serverWorld) {
                kombuchamc.createsecurity.block.camera.CameraChunkTickets.add(serverWorld, pos);
            }
        }

        tickCounter++;
        if (tickCounter < 20) return;
        tickCounter = 0;

        if (!detectionEnabled) {
            if (isDetecting) {
                isDetecting = false;
                updateRedstoneNeighbors(world);
            }
            updateFlaggedPlayers(java.util.Collections.emptySet());
            return;
        }

        Direction lensDir = state.get(CameraBlock.FACING).getOpposite();
        boolean wasDetecting = isDetecting;

        List<PlayerEntity> inCone = playersInCone(world, pos, lensDir);
        ItemStack filterStack = inventory.get(FILTER_SLOT);

        boolean filterActive =
                filterStack.isOf(kombuchamc.createsecurity.items.RegisterModItems.PLAYER_LIST);
        java.util.Set<UUID> newFlagged = new java.util.HashSet<>();
        for (PlayerEntity player : inCone) {
            if (!filterActive || kombuchamc.createsecurity.items.PlayerListItem.matches(
                    filterStack, player.getGameProfile().getName())) {
                newFlagged.add(player.getUuid());
            }
        }
        isDetecting = !newFlagged.isEmpty();
        updateFlaggedPlayers(newFlagged);

        if (isDetecting != wasDetecting) {
            world.markDirty(pos);
            updateRedstoneNeighbors(world);
        }
    }

    private void updateFlaggedPlayers(java.util.Set<UUID> newFlagged) {
        if (flaggedPlayers.equals(newFlagged)) return;
        flaggedPlayers.clear();
        flaggedPlayers.addAll(newFlagged);
        markDirty();
        if (world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(),
                    net.minecraft.block.Block.NOTIFY_LISTENERS);
        }
    }

    private static final double COS_HALF_ANGLE_SQ = square(Math.cos(Math.toRadians(DETECTION_ANGLE / 2f)));

    private static double square(double v) {
        return v * v;
    }

    private Box detectionBox = null;

    private List<PlayerEntity> playersInCone(World world, BlockPos pos, Direction lensDir) {
        if (detectionBox == null) {
            detectionBox = new Box(pos).expand(DETECTION_RANGE);
        }

        List<PlayerEntity> nearby = world.getEntitiesByClass(
                PlayerEntity.class,
                detectionBox,
                player -> !player.isSpectator()
        );
        if (nearby.isEmpty()) return List.of();

        Vec3d cameraPos = Vec3d.ofCenter(pos);

        Vec3d lensOrigin = cameraPos.add(Vec3d.of(lensDir.getVector()).multiply(0.5));
        int lensX = lensDir.getOffsetX();
        int lensY = lensDir.getOffsetY();
        int lensZ = lensDir.getOffsetZ();

        List<PlayerEntity> inCone = new java.util.ArrayList<>();
        for (PlayerEntity player : nearby) {

            double dx = player.getX() - cameraPos.x;
            double dy = player.getBodyY(0.5) - cameraPos.y;
            double dz = player.getZ() - cameraPos.z;
            double lenSq = dx * dx + dy * dy + dz * dz;

            if (lenSq < 1.0e-9) {
                inCone.add(player);
                continue;
            }

            double dot = lensX * dx + lensY * dy + lensZ * dz;
            if (dot > 0 && dot * dot >= COS_HALF_ANGLE_SQ * lenSq
                    && hasLineOfSight(world, lensOrigin, player)) {
                inCone.add(player);
            }
        }

        return inCone;
    }

    private static boolean hasLineOfSight(World world, Vec3d from, PlayerEntity player) {
        Vec3d[] targets = {
                player.getEyePos(),
                new Vec3d(player.getX(), player.getBodyY(0.5), player.getZ()),
                player.getPos()
        };
        for (Vec3d target : targets) {
            net.minecraft.util.hit.BlockHitResult hit = world.raycast(
                    new net.minecraft.world.RaycastContext(from, target,
                            net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                            net.minecraft.world.RaycastContext.FluidHandling.NONE, player));
            if (hit.getType() == net.minecraft.util.hit.HitResult.Type.MISS) {
                return true;
            }
        }
        return false;
    }
}

