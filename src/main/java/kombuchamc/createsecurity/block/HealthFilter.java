package kombuchamc.createsecurity.block;

public enum HealthFilter {

    OFF,
    ABOVE,
    BELOW;

    private static final HealthFilter[] VALUES = values();

    public static HealthFilter byId(int id) {
        return id >= 0 && id < VALUES.length ? VALUES[id] : OFF;
    }

    public HealthFilter next() {
        return VALUES[(ordinal() + 1) % VALUES.length];
    }

    public boolean accepts(float health, int threshold) {
        return switch (this) {
            case ABOVE -> health > threshold;
            case BELOW -> health < threshold;
            default -> true;
        };
    }

    public String translationKey() {
        return "gui.create-security.health." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
