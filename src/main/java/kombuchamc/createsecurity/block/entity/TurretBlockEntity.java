package kombuchamc.createsecurity.block.entity;

import com.simibubi.create.content.kinetics.base.KineticBlockEntity;
import com.simibubi.create.foundation.blockEntity.behaviour.BlockEntityBehaviour;
import kombuchamc.createsecurity.block.AmmoType;
import kombuchamc.createsecurity.block.BarrelType;
import kombuchamc.createsecurity.block.HealthFilter;
import kombuchamc.createsecurity.block.MobTarget;
import kombuchamc.createsecurity.block.TurretBlock;
import kombuchamc.createsecurity.entity.BulletEntity;
import kombuchamc.createsecurity.items.PlayerListItem;
import kombuchamc.createsecurity.items.RegisterModItems;
import kombuchamc.createsecurity.screen.TurretScreenHandler;
import net.fabricmc.fabric.api.screenhandler.v1.ExtendedScreenHandlerFactory;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventories;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.collection.DefaultedList;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.util.math.MathHelper;
import net.fabricmc.fabric.api.rendering.data.v1.RenderAttachmentBlockEntity;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public class TurretBlockEntity extends KineticBlockEntity
        implements RenderAttachmentBlockEntity, ImplementedInventory, ExtendedScreenHandlerFactory {

    public static final int SLOT_AMMO = 0;
    public static final int SLOT_BARREL = 1;
    public static final int SLOT_PLAYER_LIST = 2;
    public static final int SLOT_COUNT = 3;

    public static final int MIN_RANGE = 1;
    public static final int MAX_RANGE = 60;
    public static final int DEFAULT_EXTEND_RANGE = MIN_RANGE;
    public static final int DEFAULT_SHOOT_RANGE = MIN_RANGE;
    public static final int MIN_HEALTH = 1;
    public static final int MAX_HEALTH = 500;
    public static final int DEFAULT_HEALTH = MIN_HEALTH;
    private static final double TRUNNION_HEIGHT = 26 / 16.0;
    private static final int MAX_SHOTS_PER_TICK = 8;
    private static final int SHOT_SOUND_INTERVAL = 2;
    private static final double BORE_SPACING = 2.5 / 16.0;
    private static final int FIRING_LINGER = 10;
    private static final float SPIN_MAX = 30f;
    private static final float SPIN_ACCEL = 1.5f;
    private static final int CHECK_INTERVAL = 5;
    private static final float MAX_TURN_PER_TICK = 12f;
    private static final float MIN_TILT = -60f;
    private static final float MAX_TILT = 30f;
    private static final float DEPLOY_TICKS = 13f;
    private static final float STRESS_IMPACT = 4f;
    private static final float NOMINAL_RPM = 32f;
    private static final float MIN_FACTOR = 0.25f;
    private static final float MAX_FACTOR = 4f;

    private float currentYaw;
    private float previousYaw;
    private float currentTilt;
    private float previousTilt;
    private boolean aimed;
    private float deploy;
    private float previousDeploy;
    private boolean deploySettled;
    private BlockState material;

    @Nullable private java.util.UUID ownerUUID = null;

    private final DefaultedList<ItemStack> inventory = DefaultedList.ofSize(SLOT_COUNT, ItemStack.EMPTY);
    private BarrelType barrel = BarrelType.NONE;
    private LivingEntity trackedTarget;
    private int retargetIn;
    private boolean targetPlayers;
    private MobTarget mobTarget = MobTarget.OFF;
    private int extendRange = DEFAULT_EXTEND_RANGE;
    private float shotBudget;
    private int roundsSinceItem;
    private long lastShotSound;
    private int deployedFor;
    private boolean firing;
    private int firingIdle;
    private float spin;
    private float previousSpin;
    private float spinRate;
    private int shootRange = DEFAULT_SHOOT_RANGE;
    private HealthFilter healthFilter = HealthFilter.OFF;
    private int healthThreshold = DEFAULT_HEALTH;

    public TurretBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.TURRET_BLOCK_ENTITY, pos, state);
    }

    @Override
    public void addBehaviours(List<BlockEntityBehaviour> behaviours) {
        super.addBehaviours(behaviours);
    }

    @Override
    public float calculateStressApplied() {
        lastStressApplied = STRESS_IMPACT;
        return STRESS_IMPACT;
    }

    public void setOwner(java.util.UUID uuid) {
        this.ownerUUID = uuid;
        markDirty();
    }

    public boolean hasOwner() {
        return ownerUUID != null;
    }

    public boolean isOwner(PlayerEntity player) {
        return ownerUUID != null && ownerUUID.equals(player.getUuid());
    }

    @Nullable
    public BlockState getMaterial() {
        return material;
    }

    public void setMaterial(@Nullable BlockState state) {
        this.material = state;
        notifyUpdate();
        syncLight();
    }

    private void syncLight() {
        if (world == null || world.isClient) return;
        BlockState state = world.getBlockState(pos);
        if (!state.contains(TurretBlock.LIGHT)) return;
        int light = material == null ? 0 : material.getLuminance();
        if (state.get(TurretBlock.LIGHT) != light) {
            world.setBlockState(pos, state.with(TurretBlock.LIGHT, light), Block.NOTIFY_LISTENERS);
        }
    }

    @Override
    public Object getRenderAttachmentData() {
        return material;
    }

    @Override
    public DefaultedList<ItemStack> getItems() {
        return inventory;
    }

    @Override
    public boolean canInsert(int slot, ItemStack stack, @Nullable net.minecraft.util.math.Direction side) {
        return isValidForSlot(slot, stack);
    }

    public static boolean isValidForSlot(int slot, ItemStack stack) {
        return switch (slot) {
            case SLOT_BARREL -> BarrelType.isBarrel(stack);
            case SLOT_PLAYER_LIST -> stack.isOf(RegisterModItems.PLAYER_LIST);
            case SLOT_AMMO -> AmmoType.isAmmo(stack);
            default -> false;
        };
    }

    @Override
    public void markDirty() {
        super.markDirty();
        if (world == null || world.isClient) return;
        BarrelType mounted = BarrelType.of(inventory.get(SLOT_BARREL));
        if (mounted != barrel) {
            barrel = mounted;
            notifyUpdate();
        }
    }

    public BarrelType barrel() {
        return barrel;
    }

    public boolean targetsPlayers() {
        return targetPlayers;
    }

    public MobTarget mobTarget() {
        return mobTarget;
    }

    public void setTargetPlayers(boolean value) {
        if (targetPlayers == value) return;
        targetPlayers = value;
        notifyUpdate();
    }

    public void cycleMobTarget() {
        mobTarget = mobTarget.next();
        notifyUpdate();
    }

    public HealthFilter healthFilter() {
        return healthFilter;
    }

    public int healthThreshold() {
        return healthThreshold;
    }

    public void cycleHealthFilter() {
        healthFilter = healthFilter.next();
        notifyUpdate();
    }

    public void setHealthThreshold(int health) {
        int clamped = MathHelper.clamp(health, MIN_HEALTH, MAX_HEALTH);
        if (healthThreshold == clamped) return;
        healthThreshold = clamped;
        notifyUpdate();
    }

    public int extendRange() {
        return extendRange;
    }

    public int shootRange() {
        return shootRange;
    }

    public void setExtendRange(int blocks) {
        int clamped = MathHelper.clamp(blocks, MIN_RANGE, MAX_RANGE);
        if (extendRange == clamped) return;
        extendRange = clamped;
        notifyUpdate();
    }

    public void setShootRange(int blocks) {
        int clamped = MathHelper.clamp(blocks, MIN_RANGE, MAX_RANGE);
        if (shootRange == clamped) return;
        shootRange = clamped;
        notifyUpdate();
    }

    public AmmoType ammo() {
        return AmmoType.of(inventory.get(SLOT_AMMO));
    }

    public boolean canShoot() {
        return ammo().worksWith(barrel);
    }

    @Override
    public Text getDisplayName() {
        return Text.translatable("block.create-security.turret");
    }

    @Nullable
    @Override
    public ScreenHandler createMenu(int syncId, PlayerInventory playerInventory, PlayerEntity player) {
        return new TurretScreenHandler(syncId, playerInventory, this);
    }

    @Override
    public void writeScreenOpeningData(ServerPlayerEntity player, PacketByteBuf buf) {
        buf.writeBlockPos(pos);
    }

    @Override
    protected void write(NbtCompound nbt, boolean clientPacket) {
        super.write(nbt, clientPacket);
        if (material != null) nbt.put("Material", NbtHelper.fromBlockState(material));
        nbt.putByte("Barrel", (byte) barrel.ordinal());
        nbt.putBoolean("TargetPlayers", targetPlayers);
        nbt.putByte("MobTarget", (byte) mobTarget.ordinal());
        nbt.putByte("ExtendRange", (byte) extendRange);
        nbt.putByte("ShootRange", (byte) shootRange);
        nbt.putByte("HealthFilter", (byte) healthFilter.ordinal());
        nbt.putInt("HealthThreshold", healthThreshold);
        if (clientPacket) nbt.putBoolean("Firing", firing);
        if (!clientPacket && ownerUUID != null) nbt.putUuid("ownerUUID", ownerUUID);
        if (!clientPacket) Inventories.writeNbt(nbt, inventory);
    }

    @Override
    protected void read(NbtCompound nbt, boolean clientPacket) {
        super.read(nbt, clientPacket);
        material = nbt.contains("Material")
                ? NbtHelper.toBlockState(
                        world != null ? world.createCommandRegistryWrapper(net.minecraft.registry.RegistryKeys.BLOCK)
                                : net.minecraft.registry.Registries.BLOCK.getReadOnlyWrapper(),
                        nbt.getCompound("Material"))
                : null;
        if (!clientPacket) {
            ownerUUID = nbt.containsUuid("ownerUUID") ? nbt.getUuid("ownerUUID") : null;
            inventory.clear();
            Inventories.readNbt(nbt, inventory);
        }
        barrel = clientPacket
                ? BarrelType.byId(nbt.getByte("Barrel"))
                : BarrelType.of(inventory.get(SLOT_BARREL));
        targetPlayers = nbt.getBoolean("TargetPlayers");
        mobTarget = MobTarget.byId(nbt.getByte("MobTarget"));
        extendRange = nbt.contains("ExtendRange")
                ? MathHelper.clamp(nbt.getByte("ExtendRange"), MIN_RANGE, MAX_RANGE)
                : DEFAULT_EXTEND_RANGE;
        if (clientPacket) firing = nbt.getBoolean("Firing");
        healthFilter = HealthFilter.byId(nbt.getByte("HealthFilter"));
        healthThreshold = nbt.contains("HealthThreshold")
                ? MathHelper.clamp(nbt.getInt("HealthThreshold"), MIN_HEALTH, MAX_HEALTH)
                : DEFAULT_HEALTH;
        shootRange = nbt.contains("ShootRange")
                ? MathHelper.clamp(nbt.getByte("ShootRange"), MIN_RANGE, MAX_RANGE)
                : DEFAULT_SHOOT_RANGE;
        if (clientPacket && world != null) {
            world.updateListeners(pos, getCachedState(), getCachedState(), Block.NOTIFY_ALL);
        }
    }

    public boolean isPowered() {
        return Math.abs(getSpeed()) > 0;
    }

    @Override
    public void tick() {
        super.tick();
        if (world == null) return;

        if (world.isClient) {
            advanceSpin();
            advanceDeploy();
            turnTowardTarget();
            return;
        }
        tickShooting();
        if (world.getTime() % CHECK_INTERVAL != 0) return;

        syncLight();
        boolean deployed = nearestTarget() != null;
        BlockState state = getCachedState();
        if (state.contains(TurretBlock.DEPLOYED) && state.get(TurretBlock.DEPLOYED) != deployed) {
            world.setBlockState(pos, state.with(TurretBlock.DEPLOYED, deployed), Block.NOTIFY_LISTENERS);
        }
    }

    private void advanceSpin() {
        previousSpin = spin;
        float target = firing ? SPIN_MAX * speedFactor() : 0f;
        spinRate += MathHelper.clamp(target - spinRate, -SPIN_ACCEL, SPIN_ACCEL);
        spin = MathHelper.wrapDegrees(spin + spinRate);
    }

    public float renderSpin(float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, previousSpin, spin);
    }

    private void tickShooting() {
        if (firingIdle > 0 && --firingIdle == 0) {
            firing = false;
            notifyUpdate();
        }

        BlockState state = getCachedState();
        boolean out = state.contains(TurretBlock.DEPLOYED) && state.get(TurretBlock.DEPLOYED);
        if (!out || speedFactor() <= 0f) {
            deployedFor = 0;
            return;
        }
        if (deployedFor < DEPLOY_TICKS) {
            deployedFor++;
            return;
        }
        if (!canShoot()) {
            shotBudget = 0f;
            return;
        }

        LivingEntity target = trackedTarget();
        if (target == null) {
            shotBudget = 0f;
            return;
        }

        Vec3d pivot = trunnion();
        Vec3d aimPoint = target.getPos().add(0, target.getHeight() * 0.6, 0);
        Vec3d aim = aimPoint.subtract(pivot);
        if (aim.lengthSquared() > shootRange * (double) shootRange || !hasLineOfSight(pivot, target)) {
            shotBudget = 0f;
            return;
        }

        shotBudget = Math.min(shotBudget + barrel.roundsPerTick() * speedFactor(), MAX_SHOTS_PER_TICK);
        int rounds = (int) shotBudget;
        if (rounds <= 0) return;
        shotBudget -= rounds;

        Vec3d muzzle = pivot.add(aim.normalize().multiply(barrel.muzzleReach()));
        Vec3d direction = aimPoint.subtract(muzzle).normalize();
        for (int i = 0; i < rounds; i++) fire(muzzle, direction);
        consumeAmmo(rounds);

        firingIdle = FIRING_LINGER;
        if (!firing) {
            firing = true;
            notifyUpdate();
        }
        if (world.getTime() - lastShotSound >= SHOT_SOUND_INTERVAL && world instanceof ServerWorld server) {
            lastShotSound = world.getTime();
            server.playSound(null, pos, SoundEvents.ENTITY_FIREWORK_ROCKET_BLAST,
                    SoundCategory.BLOCKS, 0.35f, 1.6f);
        }
    }

    private void consumeAmmo(int rounds) {
        roundsSinceItem += rounds;
        int items = roundsSinceItem / barrel.roundsPerItem();
        if (items <= 0) return;
        roundsSinceItem -= items * barrel.roundsPerItem();
        ItemStack loaded = inventory.get(SLOT_AMMO);
        loaded.decrement(Math.min(items, loaded.getCount()));
        if (loaded.isEmpty()) inventory.set(SLOT_AMMO, ItemStack.EMPTY);
        markDirty();
    }

    private static Vec3d sideways(Vec3d direction) {
        Vec3d side = direction.crossProduct(new Vec3d(0, 1, 0));
        return side.lengthSquared() < 1.0E-6 ? new Vec3d(1, 0, 0) : side.normalize();
    }

    private Vec3d trunnion() {
        return new Vec3d(pos.getX() + 0.5, pos.getY() + TRUNNION_HEIGHT, pos.getZ() + 0.5);
    }

    private boolean hasLineOfSight(Vec3d from, LivingEntity target) {
        Vec3d to = target.getPos().add(0, target.getHeight() * 0.6, 0);
        net.minecraft.world.RaycastContext context = new net.minecraft.world.RaycastContext(from, to,
                net.minecraft.world.RaycastContext.ShapeType.COLLIDER,
                net.minecraft.world.RaycastContext.FluidHandling.NONE, target);
        return net.minecraft.world.BlockView.raycast(from, to, context, this::obstruction,
                ignored -> null) == null;
    }

    private net.minecraft.util.hit.BlockHitResult obstruction(
            net.minecraft.world.RaycastContext context, BlockPos pos) {
        BlockState state = world.getBlockState(pos);
        if (state.isIn(BulletEntity.BULLET_BREAKABLE)) return null;
        return world.raycastBlock(context.getStart(), context.getEnd(), pos,
                context.getBlockShape(state, world, pos), state);
    }

    private void fire(Vec3d muzzle, Vec3d direction) {
        AmmoType ammo = ammo();
        int bores = barrel.bores();
        int pellets = ammo.pellets();
        if (bores <= 0 || pellets <= 0) return;

        float spread = ammo.spread() + barrel.spread();
        Vec3d side = sideways(direction);

        for (int bore = 0; bore < bores; bore++) {
            Vec3d origin = muzzle.add(side.multiply((bore - (bores - 1) / 2.0) * BORE_SPACING));
            for (int pellet = 0; pellet < pellets; pellet++) {
                BulletEntity bullet = new BulletEntity(world, ammo);
                bullet.setPosition(origin.x, origin.y, origin.z);
                bullet.setDamage(ammo.damage());
                bullet.setVelocity(direction.x, direction.y, direction.z, ammo.speed(), spread);
                world.spawnEntity(bullet);
            }
        }

    }

    public float speedFactor() {
        float rpm = Math.abs(getSpeed());
        if (rpm <= 0) return 0f;
        return MathHelper.clamp(rpm / NOMINAL_RPM, MIN_FACTOR, MAX_FACTOR);
    }

    private void advanceDeploy() {
        BlockState state = getCachedState();
        boolean out = state.contains(TurretBlock.DEPLOYED) && state.get(TurretBlock.DEPLOYED);
        if (!deploySettled) {
            deploySettled = true;
            deploy = out ? 1f : 0f;
            previousDeploy = deploy;
            return;
        }

        previousDeploy = deploy;
        float factor = speedFactor();
        if (factor <= 0f) return;

        float rate = factor / DEPLOY_TICKS;
        deploy = MathHelper.clamp(deploy + (out ? rate : -rate), 0f, 1f);
    }

    public float deployProgress(float tickDelta) {
        return MathHelper.lerp(tickDelta, previousDeploy, deploy);
    }

    private void turnTowardTarget() {
        previousYaw = currentYaw;
        previousTilt = currentTilt;

        LivingEntity aimAt = trackedTarget();
        if (aimAt == null) return;

        double dx = aimAt.getX() - (pos.getX() + 0.5);
        double dz = aimAt.getZ() - (pos.getZ() + 0.5);
        double dy = aimAt.getY() + aimAt.getHeight() * 0.6 - (pos.getY() + TRUNNION_HEIGHT);
        float target = (float) Math.toDegrees(Math.atan2(dz, -dx));
        float tilt = MathHelper.clamp(
                (float) -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz))),
                MIN_TILT, MAX_TILT);

        if (!aimed) {
            currentYaw = previousYaw = target;
            currentTilt = previousTilt = tilt;
            aimed = true;
            return;
        }

        float limit = MAX_TURN_PER_TICK * speedFactor();
        if (limit <= 0f) return;
        currentYaw += MathHelper.clamp(MathHelper.wrapDegrees(target - currentYaw), -limit, limit);
        currentTilt += MathHelper.clamp(tilt - currentTilt, -limit, limit);
    }

    private LivingEntity trackedTarget() {
        if (retargetIn-- <= 0 || trackedTarget == null || !trackedTarget.isAlive()
                || trackedTarget.squaredDistanceTo(center()) > extendRange * (double) extendRange) {
            trackedTarget = nearestTarget();
            retargetIn = CHECK_INTERVAL;
        }
        return trackedTarget;
    }

    private LivingEntity nearestTarget() {
        if (world == null) return null;
        LivingEntity best = null;
        double bestDistance = extendRange * (double) extendRange;

        if (targetPlayers) {
            for (PlayerEntity player : world.getPlayers()) {
                if (!isTargetablePlayer(player) || !passesHealthFilter(player)) continue;
                double distance = player.squaredDistanceTo(center());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = player;
                }
            }
        }

        if (mobTarget != MobTarget.OFF) {
            Box box = new Box(pos).expand(extendRange);
            for (MobEntity mob : world.getEntitiesByClass(MobEntity.class, box, mobTarget::accepts)) {
                if (!mob.isAlive() || !passesHealthFilter(mob)) continue;
                double distance = mob.squaredDistanceTo(center());
                if (distance < bestDistance) {
                    bestDistance = distance;
                    best = mob;
                }
            }
        }
        return best;
    }

    private boolean passesHealthFilter(LivingEntity entity) {
        return healthFilter.accepts(entity.getHealth(), healthThreshold);
    }

    private boolean isTargetablePlayer(PlayerEntity player) {
        if (player.isSpectator() || player.isCreative() || !player.isAlive()) return false;
        ItemStack filter = inventory.get(SLOT_PLAYER_LIST);
        return !filter.isOf(RegisterModItems.PLAYER_LIST)
                || PlayerListItem.matches(filter, player.getGameProfile().getName());
    }

    private net.minecraft.util.math.Vec3d center() {
        return new net.minecraft.util.math.Vec3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
    }

    public float renderYaw(float tickDelta) {
        return MathHelper.lerpAngleDegrees(tickDelta, previousYaw, currentYaw);
    }

    public float renderTilt(float tickDelta) {
        return MathHelper.lerp(tickDelta, previousTilt, currentTilt);
    }
}
