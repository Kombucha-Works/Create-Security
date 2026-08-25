package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.block.entity.CameraLinkBlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;

public class CameraLinkScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    public final CameraLinkBlockEntity blockEntity;

    public CameraLinkScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, new SimpleInventory(2), null);
    }

    public CameraLinkScreenHandler(int syncId, PlayerInventory playerInventory, CameraLinkBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity, blockEntity);
    }

    public CameraLinkScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory) {
        this(syncId, playerInventory, inventory, null);
    }

    private CameraLinkScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, CameraLinkBlockEntity blockEntity) {
        super(ModScreenHandlers.CAMERA_LINK_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.blockEntity = blockEntity;

        this.addSlot(new Slot(inventory, 0, 80, 42));
        this.addSlot(new Slot(inventory, 1, 80, 60));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 83 + row * 18));
            }
        }

        for (int col = 0; col < 9; col++) {
            this.addSlot(new Slot(playerInventory, col, 8 + col * 18, 141));
        }
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return this.inventory.canPlayerUse(player);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = this.slots.get(invSlot);

        if (slot.hasStack()) {
            ItemStack originalStack = slot.getStack();
            newStack = originalStack.copy();

            if (invSlot < 2) {
                if (!this.insertItem(originalStack, 2, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.insertItem(originalStack, 0, 2, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (originalStack.isEmpty()) {
                slot.setStack(ItemStack.EMPTY);
            } else {
                slot.markDirty();
            }
        }
        return newStack;
    }
}
