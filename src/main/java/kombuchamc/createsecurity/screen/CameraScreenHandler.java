package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.block.entity.CameraBlockEntity;
import kombuchamc.createsecurity.items.RegisterModItems;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.util.math.BlockPos;

public class CameraScreenHandler extends ScreenHandler {

    private final Inventory inventory;
    public final CameraBlockEntity blockEntity;
    public BlockPos cameraPos;

    public boolean initialDetectionEnabled = false;

    public CameraScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, new SimpleInventory(4), null);
        this.cameraPos = buf.readBlockPos();
        this.initialDetectionEnabled = buf.readBoolean();
    }

    public CameraScreenHandler(int syncId, PlayerInventory playerInventory, CameraBlockEntity blockEntity) {
        this(syncId, playerInventory, blockEntity, blockEntity);
        this.cameraPos = blockEntity.getPos();
    }

    private CameraScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inventory, CameraBlockEntity blockEntity) {
        super(ModScreenHandlers.CAMERA_SCREEN_HANDLER, syncId);
        this.inventory = inventory;
        this.blockEntity = blockEntity;

        this.addSlot(new Slot(inventory, 0, 8, 34));
        this.addSlot(new Slot(inventory, 1, 8, 52));

        this.addSlot(new Slot(inventory, 2, 134, 52) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(RegisterModItems.PLAYER_LIST);
            }
        });

        this.addSlot(new Slot(inventory, 3, 134, 34) {
            @Override
            public boolean canInsert(ItemStack stack) {
                return stack.isOf(RegisterModItems.FISHEYE_LENS);
            }

            @Override
            public int getMaxItemCount() {
                return 1;
            }
        });

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

            if (invSlot < 4) {
                if (!this.insertItem(originalStack, 4, this.slots.size(), true)) {
                    return ItemStack.EMPTY;
                }
            } else {
                if (!this.insertItem(originalStack, 0, 4, false)) {
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
