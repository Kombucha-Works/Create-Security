package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.items.KeycardItem;
import kombuchamc.createsecurity.items.RegisterModItems;
import kombuchamc.createsecurity.network.ModPackets;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public final class KeycardReaderLock {

    private static boolean unlocking;

    private KeycardReaderLock() {}

    public static ActionResult onUseHost(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        if (unlocking) return ActionResult.PASS;
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

        BlockPos readerPos = KeycardReaderPlacement.readerPosFor(serverWorld, hit.getBlockPos());
        if (readerPos == null) return ActionResult.PASS;

        KeycardReaderStore store = KeycardReaderStore.get(serverWorld);
        KeycardReaderStore.Entry entry = store.get(readerPos);
        if (entry == null) return ActionResult.PASS;

        ItemStack stack = player.getStackInHand(hand);
        if (player.isSneaking() && !stack.isEmpty() && !stack.isOf(RegisterModItems.KEYCARD)) {
            return ActionResult.PASS;
        }
        int heldCard = stack.isOf(RegisterModItems.KEYCARD) ? KeycardItem.getCardId(stack) : 0;
        boolean onPanel = KeycardReaderGeometry.isAimedAtPanel(
                world, hit.getBlockPos(), hit.getSide(), hit.getPos());

        if (!entry.isArmed()) {
            if (!onPanel || heldCard == 0) return ActionResult.PASS;
            store.setCard(readerPos, heldCard);
            ModPackets.broadcastKeycardReaderUpdate(serverWorld, readerPos, true, true);
            play(serverWorld, readerPos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.2f);
            message(player, "message.create-security.reader_programmed", Formatting.GREEN);
            return ActionResult.SUCCESS;
        }

        if (heldCard == entry.cardId()) {
            play(serverWorld, readerPos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.8f);
            return openHost(player, world, hand, hit);
        }

        play(serverWorld, readerPos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
        message(player, heldCard == 0
                ? "message.create-security.reader_locked"
                : "message.create-security.reader_wrong_card", Formatting.RED);
        return ActionResult.FAIL;
    }

    private static ActionResult openHost(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        BlockState state = world.getBlockState(hit.getBlockPos());
        unlocking = true;
        try {
            ActionResult result = state.onUse(world, player, hand, hit);
            return result == ActionResult.PASS ? ActionResult.SUCCESS : result;
        } finally {
            unlocking = false;
        }
    }

    private static void play(ServerWorld world, BlockPos pos, net.minecraft.sound.SoundEvent sound, float pitch) {
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 0.7f, pitch);
    }

    private static void message(PlayerEntity player, String key, Formatting color) {
        player.sendMessage(Text.translatable(key).formatted(color), true);
    }
}
