package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.items.RegisterModItems;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import org.jetbrains.annotations.Nullable;

public enum BarrelType {

    NONE(0, 0f, 0, 1, 0f),
    SHOTGUN(40, 8f, 2, 1, 13 / 16f),
    MINIGUN(4000, 6f, 1, 25, 14 / 16f),
    AUTOMATIC(120, 2f, 1, 1, 14 / 16f),
    ROCKET(30, 1f, 1, 1, 14 / 16f);

    private static final BarrelType[] VALUES = values();

    private final int roundsPerMinute;
    private final float spread;
    private final int bores;
    private final int roundsPerItem;
    private final float muzzleReach;

    BarrelType(int roundsPerMinute, float spread, int bores, int roundsPerItem, float muzzleReach) {
        this.roundsPerMinute = roundsPerMinute;
        this.spread = spread;
        this.bores = bores;
        this.roundsPerItem = roundsPerItem;
        this.muzzleReach = muzzleReach;
    }

    public float muzzleReach() {
        return muzzleReach;
    }

    public int roundsPerMinute() {
        return roundsPerMinute;
    }

    public int roundsPerItem() {
        return roundsPerItem;
    }

    public float roundsPerTick() {
        return roundsPerMinute / 1200f;
    }

    public float spread() {
        return spread;
    }

    public int bores() {
        return bores;
    }

    @Nullable
    public Block rotorBlock() {
        return this == MINIGUN ? RegisterModBlocks.TURRET_GUN_MINIGUN_ROTOR : null;
    }

    public static BarrelType byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : NONE;
    }

    public static BarrelType of(ItemStack stack) {
        if (stack.isOf(RegisterModItems.SHOTGUN_BARREL)) return SHOTGUN;
        if (stack.isOf(RegisterModItems.MINIGUN_BARREL)) return MINIGUN;
        if (stack.isOf(RegisterModItems.AUTOMATIC_BARREL)) return AUTOMATIC;
        if (stack.isOf(RegisterModItems.ROCKET_BARREL)) return ROCKET;
        return NONE;
    }

    public static boolean isBarrel(ItemStack stack) {
        return of(stack) != NONE;
    }

    @Nullable
    public Block gunBlock() {
        return switch (this) {
            case SHOTGUN -> RegisterModBlocks.TURRET_GUN_SHOTGUN;
            case MINIGUN -> RegisterModBlocks.TURRET_GUN_MINIGUN;
            case AUTOMATIC -> RegisterModBlocks.TURRET_GUN_AUTOMATIC;
            case ROCKET -> RegisterModBlocks.TURRET_GUN_ROCKET;
            default -> null;
        };
    }

}
