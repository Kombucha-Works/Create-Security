package kombuchamc.createsecurity.entity;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.AmmoType;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.entity.projectile.ProjectileUtil;
import net.minecraft.block.Block;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;

public class BulletEntity extends ProjectileEntity {

    public static final RegistryKey<DamageType> BULLET_DAMAGE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            new Identifier(CreateSecurity.MOD_ID, "turret_bullet"));

    private static final TrackedData<Integer> AMMO =
            DataTracker.registerData(BulletEntity.class, TrackedDataHandlerRegistry.INTEGER);

    public static final TagKey<Block> BULLET_BREAKABLE = TagKey.of(RegistryKeys.BLOCK,
            new Identifier(CreateSecurity.MOD_ID, "bullet_breakable"));

    private static final int MAX_AGE = 40;
    private static final int MAX_PIERCE = 4;
    private static final int SHIELD_BULLETS = 20;
    private static final double DRAG = 0.99;
    private static final double GRAVITY = 0.005;

    private float damage = AmmoType.NORMAL.damage();
    private int life;

    public BulletEntity(EntityType<? extends BulletEntity> type, World world) {
        super(type, world);
    }

    public BulletEntity(World world, AmmoType ammo) {
        this(ModEntities.BULLET, world);
        setAmmo(ammo);
        damage = ammo.damage();
    }

    @Override
    protected void initDataTracker() {
        dataTracker.startTracking(AMMO, AmmoType.NORMAL.ordinal());
    }

    public AmmoType ammo() {
        return AmmoType.byId(dataTracker.get(AMMO));
    }

    public void setAmmo(AmmoType ammo) {
        dataTracker.set(AMMO, ammo.ordinal());
    }

    public void setDamage(float damage) {
        this.damage = damage;
    }

    @Override
    public void tick() {
        super.tick();
        if (++life > MAX_AGE) {
            discard();
            return;
        }

        BlockPos shattered = null;
        for (int pierced = 0; pierced <= MAX_PIERCE; pierced++) {
            HitResult hit = ProjectileUtil.getCollision(this, this::canHit);
            if (hit.getType() == HitResult.Type.MISS) break;
            BlockPos struck = hit instanceof BlockHitResult block ? block.getBlockPos() : null;
            if (struck != null && struck.equals(shattered)) break;
            onCollision(hit);
            if (isRemoved()) return;
            shattered = struck;
        }

        Vec3d velocity = getVelocity();
        setPosition(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        setVelocity(velocity.multiply(DRAG).subtract(0, GRAVITY, 0));
        updateRotation();

        if (getWorld().isClient) {
            getWorld().addParticle(ParticleTypes.CRIT, getX(), getY(), getZ(), 0, 0, 0);
        }
    }

    @Override
    protected boolean canHit(Entity entity) {
        return super.canHit(entity) && !(entity instanceof BulletEntity);
    }

    @Override
    protected void onEntityHit(EntityHitResult result) {
        super.onEntityHit(result);
        Entity hit = result.getEntity();
        AmmoType ammo = ammo();

        if (!getWorld().isClient) {
            if (hit instanceof LivingEntity blocking && blockWithShield(blocking)) {
                impact(result.getPos(), ammo);
                return;
            }
            hit.damage(damageSource(), damage);
            if (ammo.ignites()) hit.setOnFireFor(AmmoType.FIRE_SECONDS);
            if (hit instanceof LivingEntity living) living.timeUntilRegen = 0;
        }

        impact(result.getPos(), ammo);
    }

    private boolean blockWithShield(LivingEntity living) {
        if (!living.isBlocking()) return false;
        Vec3d incoming = getPos().relativize(living.getPos()).normalize();
        if (new Vec3d(incoming.x, 0, incoming.z).dotProduct(living.getRotationVec(1f)) >= 0) return false;

        ItemStack shield = living.getActiveItem();
        if (shield.getMaxDamage() <= 0) return true;

        EquipmentSlot slot = living.getActiveHand() == Hand.MAIN_HAND
                ? EquipmentSlot.MAINHAND
                : EquipmentSlot.OFFHAND;
        shield.damage(Math.max(1, shield.getMaxDamage() / SHIELD_BULLETS), living,
                holder -> holder.sendEquipmentBreakStatus(slot));
        if (shield.isEmpty()) {
            living.equipStack(slot, ItemStack.EMPTY);
            living.clearActiveItem();
            shieldSound(living, SoundEvents.ITEM_SHIELD_BREAK, 0.8f);
        } else {
            shieldSound(living, SoundEvents.ITEM_SHIELD_BLOCK, 1f);
        }
        return true;
    }

    private void shieldSound(LivingEntity living, net.minecraft.sound.SoundEvent sound, float volume) {
        getWorld().playSound(null, living.getX(), living.getY(), living.getZ(), sound,
                living.getSoundCategory(), volume, 0.8f + random.nextFloat() * 0.4f);
    }

    @Override
    protected void onBlockHit(BlockHitResult result) {
        super.onBlockHit(result);
        if (shatter(result.getBlockPos())) return;
        impact(result.getPos(), ammo());
    }

    private boolean shatter(BlockPos pos) {
        if (!getWorld().getBlockState(pos).isIn(BULLET_BREAKABLE)) return false;
        if (getWorld() instanceof ServerWorld server) {
            server.breakBlock(pos, false, this, 512);
        }
        return true;
    }

    private DamageSource damageSource() {
        return new DamageSource(
                getWorld().getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(BULLET_DAMAGE),
                this, getOwner());
    }

    private void impact(Vec3d at, AmmoType ammo) {
        if (getWorld() instanceof ServerWorld server) {
            if (ammo.explodes()) {
                server.createExplosion(this, at.x, at.y, at.z,
                        ammo.explosionPower(), World.ExplosionSourceType.TNT);
            } else {
                server.spawnParticles(ParticleTypes.CRIT, at.x, at.y, at.z, 6, 0.1, 0.1, 0.1, 0.05);
                server.playSound(null, at.x, at.y, at.z, SoundEvents.BLOCK_COPPER_HIT,
                        SoundCategory.BLOCKS, 0.4f, 1.6f);
            }
        }
        discard();
    }

    @Override
    protected void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putByte("Ammo", (byte) ammo().ordinal());
        nbt.putFloat("Damage", damage);
        nbt.putShort("Life", (short) life);
    }

    @Override
    protected void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        setAmmo(AmmoType.byId(nbt.getByte("Ammo")));
        damage = nbt.getFloat("Damage");
        life = nbt.getShort("Life");
    }

    @Override
    public boolean shouldRender(double distance) {
        return distance < 4096;
    }
}
