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
        boolean universal = stack.isOf(RegisterModItems.UNIVERSAL_KEYCARD);

        if (player.isSneaking() && !stack.isEmpty()
                && !stack.isOf(RegisterModItems.KEYCARD) && !universal) {
            return ActionResult.PASS;
        }

        if (entry.kind() == LockKind.CODE) {
            return player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
                    ? CodeLock.onUse(serverPlayer, serverWorld, readerPos, entry)
                    : ActionResult.PASS;
        }
        int heldCard = stack.isOf(RegisterModItems.KEYCARD) ? KeycardItem.getCardId(stack) : 0;
        boolean onPanel = KeycardReaderGeometry.isAimedAtPanel(
                world, hit.getBlockPos(), hit.getSide(), hit.getPos());

        if (!entry.isArmed()) {
            if (!onPanel || heldCard == 0) return ActionResult.PASS;
            store.setSecret(readerPos, heldCard);
            ModPackets.broadcastKeycardReaderUpdate(serverWorld, readerPos, true, true, LockKind.KEYCARD);
            play(serverWorld, readerPos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.2f);
            message(player, "message.create-security.reader_programmed", Formatting.GREEN);
            return ActionResult.SUCCESS;
        }

        if (universal || heldCard == entry.secret()) {
            play(serverWorld, readerPos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.8f);
            ActionResult result = openHost(player, world, hand, hit);
            if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer) {
                KeycardReaderLights.opened(serverWorld, readerPos, serverPlayer);
            }
            return result;
        }

        play(serverWorld, readerPos, SoundEvents.BLOCK_NOTE_BLOCK_BASS.value(), 0.8f);
        message(player, heldCard == 0
                ? "message.create-security.reader_locked"
                : "message.create-security.reader_wrong_card", Formatting.RED);
        return ActionResult.FAIL;
    }

    public static ActionResult openUnlocked(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        return openHost(player, world, hand, hit);
    }

    private static ActionResult openHost(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        BlockState state = world.getBlockState(hit.getBlockPos());

        if (KeycardReaderDoors.isDoor(state) && world instanceof ServerWorld serverWorld) {
            KeycardReaderDoors.toggleWithKeycard(serverWorld, hit.getBlockPos(), player);
            return ActionResult.SUCCESS;
        }
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
