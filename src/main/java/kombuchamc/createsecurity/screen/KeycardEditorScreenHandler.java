package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.block.entity.KeycardEditorBlockEntity;
import kombuchamc.createsecurity.items.KeycardItem;
import kombuchamc.createsecurity.items.RegisterModItems;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.inventory.Inventory;
import net.minecraft.inventory.SimpleInventory;
import net.minecraft.item.DyeItem;
import net.minecraft.item.ItemStack;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class KeycardEditorScreenHandler extends ScreenHandler {

    private static final int INPUT_COUNT = 3;
    private static final int SLOT_RESULT = 3;
    private static final int PLAYER_START = 4;

    private final Inventory inputs;
    private final Inventory result = new SimpleInventory(1);

    public KeycardEditorScreenHandler(int syncId, PlayerInventory playerInventory, PacketByteBuf buf) {
        this(syncId, playerInventory, new SimpleInventory(INPUT_COUNT));
    }

    public KeycardEditorScreenHandler(int syncId, PlayerInventory playerInventory, Inventory inputs) {
        super(ModScreenHandlers.KEYCARD_EDITOR_SCREEN_HANDLER, syncId);
        this.inputs = inputs;

        addSlot(new KeycardSlot(inputs, KeycardEditorBlockEntity.SLOT_SOURCE, 20, 26));
        addSlot(new KeycardSlot(inputs, KeycardEditorBlockEntity.SLOT_TARGET, 42, 26));
        addSlot(new DyeSlot(inputs, KeycardEditorBlockEntity.SLOT_DYE, 64, 26));
        addSlot(new ResultSlot(result, 0, 134, 26));

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(playerInventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(playerInventory, col, 8 + col * 18, 142));
        }

        updateResult();
    }

    @Override
    public void onContentChanged(Inventory inventory) {
        super.onContentChanged(inventory);
        if (inventory != result) updateResult();
    }

    @Override
    public void sendContentUpdates() {
        updateResult();
        super.sendContentUpdates();
    }

    private void updateResult() {
        ItemStack next = preview();
        if (!ItemStack.areEqual(result.getStack(0), next)) {
            result.setStack(0, next);
        }
    }

    private ItemStack preview() {
        ItemStack target = inputs.getStack(KeycardEditorBlockEntity.SLOT_TARGET);
        if (!isKeycard(target)) return ItemStack.EMPTY;

        ItemStack source = inputs.getStack(KeycardEditorBlockEntity.SLOT_SOURCE);
        ItemStack dye = inputs.getStack(KeycardEditorBlockEntity.SLOT_DYE);
        boolean clones = isKeycard(source) && KeycardItem.getCardId(source) != 0;
        boolean colours = dye.getItem() instanceof DyeItem;
        if (!clones && !colours) return ItemStack.EMPTY;

        ItemStack edited = target.copy();
        if (clones) KeycardItem.setCardId(edited, KeycardItem.getCardId(source));
        if (colours) KeycardItem.setColor(edited, ((DyeItem) dye.getItem()).getColor());
        return edited;
    }

    private void consumeInputs(PlayerEntity player) {
        boolean colours = inputs.getStack(KeycardEditorBlockEntity.SLOT_DYE).getItem() instanceof DyeItem;
        inputs.removeStack(KeycardEditorBlockEntity.SLOT_TARGET, 1);
        if (colours) inputs.removeStack(KeycardEditorBlockEntity.SLOT_DYE, 1);
        inputs.markDirty();
        updateResult();
        beep(player);
    }

    private void beep(PlayerEntity player) {
        World world = player.getWorld();
        if (world.isClient) return;
        BlockPos at = inputs instanceof BlockEntity blockEntity ? blockEntity.getPos() : player.getBlockPos();
        world.playSound(null, at, SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), SoundCategory.BLOCKS,
                0.6f, 1.8f);
    }

    private static boolean isKeycard(ItemStack stack) {
        return stack.isOf(RegisterModItems.KEYCARD);
    }

    @Override
    public boolean canUse(PlayerEntity player) {
        return inputs.canPlayerUse(player);
    }

    @Override
    public void onClosed(PlayerEntity player) {
        super.onClosed(player);
        result.removeStack(0);
    }

    @Override
    public ItemStack quickMove(PlayerEntity player, int invSlot) {
        ItemStack newStack = ItemStack.EMPTY;
        Slot slot = slots.get(invSlot);
        if (!slot.hasStack()) return newStack;

        ItemStack originalStack = slot.getStack();
        newStack = originalStack.copy();

        if (invSlot == SLOT_RESULT) {
            if (!insertItem(originalStack, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
            slot.onQuickTransfer(originalStack, newStack);
        } else if (invSlot < INPUT_COUNT) {
            if (!insertItem(originalStack, PLAYER_START, slots.size(), true)) return ItemStack.EMPTY;
        } else if (!insertItem(originalStack, 0, INPUT_COUNT, false)) {
            return ItemStack.EMPTY;
        }

        if (originalStack.isEmpty()) {
            slot.setStack(ItemStack.EMPTY);
        } else {
            slot.markDirty();
        }
        if (originalStack.getCount() == newStack.getCount()) return ItemStack.EMPTY;
        slot.onTakeItem(player, originalStack);
        return newStack;
    }

    private static class KeycardSlot extends Slot {

        KeycardSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return isKeycard(stack);
        }

        @Override
        public int getMaxItemCount() {
            return 1;
        }
    }

    private static class DyeSlot extends Slot {

        DyeSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return stack.getItem() instanceof DyeItem;
        }
    }

    private class ResultSlot extends Slot {

        ResultSlot(Inventory inventory, int index, int x, int y) {
            super(inventory, index, x, y);
        }

        @Override
        public boolean canInsert(ItemStack stack) {
            return false;
        }

        @Override
        public void onTakeItem(PlayerEntity player, ItemStack stack) {
            consumeInputs(player);
            super.onTakeItem(player, stack);
        }
    }
}
