package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.items.RegisterModItems;
import net.minecraft.item.ItemStack;

public enum AmmoType {

    NONE(0f, 0, 0f, 0f, BarrelType.NONE, 0f),
    NORMAL(4f, 1, 0f, 4.0f, BarrelType.AUTOMATIC, 0f),
    FIRE(1.5f, 5, 7f, 3.2f, BarrelType.SHOTGUN, 0f),
    EXPLOSIVE(3f, 1, 0f, 2.6f, BarrelType.ROCKET, 4.0f),
    SLUG(9f, 1, 0f, 5.0f, BarrelType.SHOTGUN, 0f),
    BUCKSHOT(1.5f, 5, 7f, 3.2f, BarrelType.SHOTGUN, 0f),
    INCENDIARY(3f, 1, 0f, 3.9f, BarrelType.AUTOMATIC, 0f),
    HEAVY(0.5f, 1, 0f, 4.4f, BarrelType.MINIGUN, 0f),
    ROCKET(2f, 1, 0f, 2.6f, BarrelType.ROCKET, 1.0f);

    public static final int FIRE_SECONDS = 5;

    private static final AmmoType[] VALUES = values();

    private final float damage;
    private final int pellets;
    private final float spread;
    private final float speed;
    private final BarrelType barrel;
    private final float explosionPower;

    AmmoType(float damage, int pellets, float spread, float speed, BarrelType barrel,
             float explosionPower) {
        this.damage = damage;
        this.pellets = pellets;
        this.spread = spread;
        this.speed = speed;
        this.barrel = barrel;
        this.explosionPower = explosionPower;
    }

    public static AmmoType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public static AmmoType of(ItemStack stack) {
        if (stack.isOf(RegisterModItems.REGULAR_556_AMMO)) return NORMAL;
        if (stack.isOf(RegisterModItems.INCENDIARY_BUCKSHOT_12G)) return FIRE;
        if (stack.isOf(RegisterModItems.HE_ROCKET)) return EXPLOSIVE;
        if (stack.isOf(RegisterModItems.SLUG_12G)) return SLUG;
        if (stack.isOf(RegisterModItems.BUCKSHOT_12G)) return BUCKSHOT;
        if (stack.isOf(RegisterModItems.INCENDIARY_556_AMMO)) return INCENDIARY;
        if (stack.isOf(RegisterModItems.REGULAR_762_AMMO)) return HEAVY;
        if (stack.isOf(RegisterModItems.ROCKET_ROUND)) return ROCKET;
        return NONE;
    }

    public static boolean isAmmo(ItemStack stack) {
        return of(stack) != NONE;
    }

    public float damage() {
        return damage;
    }

    public int pellets() {
        return pellets;
    }

    public float spread() {
        return spread;
    }

    public float speed() {
        return speed;
    }

    public BarrelType barrel() {
        return barrel;
    }

    public boolean worksWith(BarrelType fitted) {
        return this != NONE && barrel == fitted;
    }

    public boolean ignites() {
        return this == FIRE || this == INCENDIARY;
    }

    public float explosionPower() {
        return explosionPower;
    }

    public boolean explodes() {
        return explosionPower > 0f;
    }

    public net.minecraft.block.Block projectileBlock() {
        return switch (this) {
            case FIRE -> RegisterModBlocks.PROJECTILE_INCENDIARY_BUCKSHOT_12G;
            case EXPLOSIVE -> RegisterModBlocks.PROJECTILE_HE_ROCKET;
            case SLUG -> RegisterModBlocks.PROJECTILE_SLUG_12G;
            case BUCKSHOT -> RegisterModBlocks.PROJECTILE_BUCKSHOT_12G;
            case INCENDIARY -> RegisterModBlocks.PROJECTILE_INCENDIARY_556;
            case HEAVY -> RegisterModBlocks.PROJECTILE_REGULAR_762;
            case ROCKET -> RegisterModBlocks.PROJECTILE_ROCKET;
            default -> RegisterModBlocks.PROJECTILE_REGULAR_556;
        };
    }
}
