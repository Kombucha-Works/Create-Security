package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.block.entity.TurretBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ArrayPropertyDelegate;
import net.minecraft.screen.PropertyDelegate;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;
import org.jetbrains.annotations.Nullable;

public class TurretScreenHandler extends ScreenHandler {

    public static final int BUTTON_PLAYERS = 0;
    public static final int BUTTON_MOBS = 1;
    public static final int BUTTON_HEALTH = 2;
    public static final int SLIDER_EXTEND = 0;
    public static final int SLIDER_SHOOT = 1;
    public static final int SLIDER_HEALTH = 2;

    public static final int PROPERTY_PLAYERS = 0;
    public static final int PROPERTY_MOBS = 1;
    public static final int PROPERTY_EXTEND = 2;
    public static final int PROPERTY_SHOOT = 3;
    public static final int PROPERTY_HEALTH_FILTER = 4;
    public static final int PROPERTY_HEALTH = 5;
    private static final int PROPERTY_COUNT = 6;

    private final Inventory inventory;
    private final PropertyDelegate properties;
    @Nullable
    private final TurretBlockEntity turret;
    public final BlockPos turretPos;

    public TurretScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, new SimpleInventory(TurretBlockEntity.SLOT_COUNT),
                new ArrayPropertyDelegate(PROPERTY_COUNT), null, buf.readBlockPos());
    }

    public TurretScreenHandler(int syncId, PlayerInventory playerInventory, TurretBlockEntity turret) {
        this(syncId, playerInventory, turret, new TurretProperties(turret), turret, turret.getPos());
    }

    private TurretScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory,
                                PropertyDelegate properties, @Nullable TurretBlockEntity turret,
                                BlockPos turretPos) {
        super(ModScreenHandlers.TURRET_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.properties = properties;
        this.turret = turret;
        this.turretPos = turretPos;

        addSlot(new FilteredSlot(inventory, TurretBlockEntity.SLOT_AMMO, 44, 22));
        addSlot(new FilteredSlot(inventory, TurretBlockEntity.SLOT_BARREL, 80, 22));
        addSlot(new FilteredSlot(inventory, TurretBlockEntity.SLOT_PLAYER_LIST, 116, 22));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 144 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 202));
        }

        addProperties(properties);
    }

    public boolean targetsPlayers() {
        return properties.get(PROPERTY_PLAYERS) != 0;
    }

    public int mobTargetId() {
        return properties.get(PROPERTY_MOBS);
    }

    public int extendRange() {
        return clampRange(properties.get(PROPERTY_EXTEND));
    }

    public int shootRange() {
        return clampRange(properties.get(PROPERTY_SHOOT));
    }

    public int healthFilterId() {
        return properties.get(PROPERTY_HEALTH_FILTER);
    }

    public int healthThreshold() {
        return net.minecraft.util.math.MathHelper.clamp(properties.get(PROPERTY_HEALTH),
                TurretBlockEntity.MIN_HEALTH, TurretBlockEntity.MAX_HEALTH);
    }

    private static int clampRange(int value) {
        return net.minecraft.util.math.MathHelper.clamp(value,
                TurretBlockEntity.MIN_RANGE, TurretBlockEntity.MAX_RANGE);
    }

    @Override
    public boolean onButtonClick(PlayerEntity player, int id) {
        if (turret == null) return false;
        if (id == BUTTON_PLAYERS) {
            turret.setTargetPlayers(!turret.targetsPlayers());
            return true;
        }
        if (id == BUTTON_MOBS) {
            turret.cycleMobTarget();
            return true;
        }
        if (id == BUTTON_HEALTH) {
            turret.cycleHealthFilter();
            return true;
        }
        return false;
    }

    public void setSlider(int slider, int value) {
        if (turret == null) return;
        switch (slider) {
            case SLIDER_EXTEND -> turret.setExtendRange(value);
            case SLIDER_SHOOT -> turret.setShootRange(value);
            case SLIDER_HEALTH -> turret.setHealthThreshold(value);
            default -> { }
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = slots.get(invSlot);
        if (!slot.hasStack()) return newStack;

        ItemStack originalStack = slot.getStack();
        newStack = originalStack.copy();

        if (invSlot < TurretBlockEntity.SLOT_COUNT) {
            if (!insertItem(originalStack, TurretBlockEntity.SLOT_COUNT, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (!insertItem(originalStack, 0, TurretBlockEntity.SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (originalStack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        return newStack;
    }

    private static class FilteredSlot extends Slot {

        FilteredSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return TurretBlockEntity.isValidForSlot(getIndex(), stack);
        }

        @Override
        public int getMaxItemCount() {
            return getIndex() == TurretBlockEntity.SLOT_AMMO ? super.getMaxItemCount() : 1;
        }
    }

    private record TurretProperties(TurretBlockEntity turret) implements PropertyDelegate {

        @Override
        public int get(int index) {
            return switch (index) {
                case PROPERTY_PLAYERS -> turret.targetsPlayers() ? 1 : 0;
                case PROPERTY_MOBS -> turret.mobTarget().ordinal();
                case PROPERTY_EXTEND -> turret.extendRange();
                case PROPERTY_SHOOT -> turret.shootRange();
                case PROPERTY_HEALTH_FILTER -> turret.healthFilter().ordinal();
                case PROPERTY_HEALTH -> turret.healthThreshold();
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int size() {
            return PROPERTY_COUNT;
        }
    }
}
