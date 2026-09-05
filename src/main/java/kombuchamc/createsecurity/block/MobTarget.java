package kombuchamc.createsecurity.block;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.HostileEntity;
import net.minecraft.entity.mob.MobEntity;

public enum MobTarget {

    OFF,
    HOSTILE,
    ALL;

    private static final MobTarget[] VALUES = values();

    public static MobTarget byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : OFF;
    }

    public MobTarget next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public boolean accepts(Entity entity) {
        return switch (this) {
            case ALL -> entity instanceof MobEntity;
            case HOSTILE -> entity instanceof HostileEntity;
            default -> false;
        };
    }

    public String translationKey() {
        return "gui.create-security.mobs." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
