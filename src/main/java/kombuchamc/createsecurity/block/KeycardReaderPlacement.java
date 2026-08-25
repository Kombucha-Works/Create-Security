package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.config.CSConfigs;
import kombuchamc.createsecurity.items.RegisterModItems;
import kombuchamc.createsecurity.network.ModPackets;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.ItemScatterer;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import net.minecraft.world.WorldView;
import net.minecraft.world.event.GameEvent;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class KeycardReaderPlacement {

    private KeycardReaderPlacement() {}

    public static boolean isHoldingReader(PlayerEntity player) {
        return player.getMainHandStack().isOf(RegisterModItems.KEYCARD_READER)
                || player.getOffHandStack().isOf(RegisterModItems.KEYCARD_READER);
    }

    public static boolean isMountableSurface(WorldView world, BlockPos pos, Direction face) {
        if (!KeycardReaderBlock.isSupport(world.getBlockState(pos))) return false;
        if (!KeycardReaderGeometry.mountFaces(world, pos).contains(face)) return false;
        if (KeycardReaderGeometry.fitsInsideHost(KeycardReaderGeometry.hostBox(world, pos), face)) return true;
        return world.getBlockState(pos.offset(face)).isReplaceable();
    }

    public static BlockState mountState(Direction face) {
        return mountState(face, false);
    }

    public static BlockState mountState(Direction face, boolean armed) {
        return RegisterModBlocks.KEYCARD_READER.getDefaultState()
                .with(KeycardReaderBlock.FACING, face)
                .with(KeycardReaderBlock.ARMED, armed);
    }

    public static ActionResult tryPlace(PlayerEntity player, World world, Hand hand, BlockHitResult hit) {
        ItemStack stack = player.getStackInHand(hand);
        if (!stack.isOf(RegisterModItems.KEYCARD_READER)) return ActionResult.PASS;
        if (!(world instanceof ServerWorld serverWorld)) return ActionResult.PASS;

        BlockPos clicked = hit.getBlockPos();
        if (!isMountableSurface(world, clicked, hit.getSide())) return ActionResult.PASS;
        if (!KeycardReaderGeometry.isAimedAtPanel(world, clicked, hit.getSide(), hit.getPos())) {
            return ActionResult.PASS;
        }
        if (readerPosFor(serverWorld, clicked) != null) return ActionResult.PASS;

        BlockPos pos = KeycardReaderGeometry.hostPos(world, clicked);
        KeycardReaderStore.get(serverWorld).add(pos, player.getUuid());
        ModPackets.broadcastKeycardReaderUpdate(serverWorld, pos, true, false);

        world.playSound(null, pos, SoundEvents.BLOCK_METAL_PLACE, SoundCategory.BLOCKS, 1f, 0.9f);
        world.emitGameEvent(player, GameEvent.BLOCK_PLACE, pos);
        if (!player.getAbilities().creativeMode) stack.decrement(1);
        return ActionResult.SUCCESS;
    }

    @Nullable
    public static BlockPos readerPosFor(ServerWorld world, BlockPos pos) {
        KeycardReaderStore store = KeycardReaderStore.get(world);
        BlockPos host = KeycardReaderGeometry.hostPos(world, pos);
        if (store.has(host)) return host;
        BlockPos partner = KeycardReaderGeometry.partner(world, host);
        return partner != null && store.has(partner) ? partner : null;
    }

    @Nullable
    public static BlockPos mountedReaderAt(ServerWorld world, BlockPos pos, Direction face,
                                           @Nullable Vec3d hitPos) {
        BlockPos reader = readerPosFor(world, pos);
        if (reader == null || hitPos == null) return null;
        if (!KeycardReaderGeometry.mountFaces(world, pos).contains(face)) return null;
        return KeycardReaderGeometry.isAimedAtPanel(world, pos, face, hitPos) ? reader : null;
    }

    @Nullable
    public static Vec3d aimPointOn(PlayerEntity player, BlockPos pos, Direction face) {
        HitResult result = player.raycast(6.0, 0f, false);
        if (result instanceof BlockHitResult hit
                && hit.getBlockPos().equals(pos) && hit.getSide() == face) {
            return hit.getPos();
        }
        return null;
    }

    public static boolean canModify(ServerWorld world, @Nullable BlockPos readerPos, PlayerEntity player) {
        if (readerPos == null) return true;
        KeycardReaderStore.Entry entry = KeycardReaderStore.get(world).get(readerPos);
        if (entry == null || entry.owner() == null) return true;
        return entry.owner().equals(player.getUuid()) || CSConfigs.canBypassOwner(player);
    }

    public static void warnNotOwner(PlayerEntity player) {
        player.sendMessage(Text.translatable("message.create-security.not_keycard_reader_owner")
                .formatted(Formatting.RED), true);
    }

    public static boolean removeAndDrop(ServerWorld world, BlockPos pos, @Nullable PlayerEntity player) {
        KeycardReaderStore.Entry entry = KeycardReaderStore.get(world).remove(pos);
        if (entry == null) return false;
        ModPackets.broadcastKeycardReaderUpdate(world, pos, false, false);

        List<Direction> faces = KeycardReaderGeometry.mountFaces(world, pos);
        spawnBreakParticles(world, pos, faces, entry.isArmed());

        if (player == null || !player.getAbilities().creativeMode) {
            Vec3d drop = faces.isEmpty()
                    ? Vec3d.ofCenter(pos)
                    : KeycardReaderGeometry.surfaceAnchor(world, pos, faces.get(0), 0.3);
            ItemScatterer.spawn(world, drop.x, drop.y, drop.z,
                    new ItemStack(RegisterModItems.KEYCARD_READER));
        }
        world.playSound(null, pos, SoundEvents.BLOCK_METAL_BREAK, SoundCategory.BLOCKS, 0.8f, 1.1f);
        return true;
    }

    private static void spawnBreakParticles(ServerWorld world, BlockPos pos, List<Direction> faces,
                                            boolean armed) {
        if (faces.isEmpty()) {
            emitPanelParticles(world, Vec3d.ofCenter(pos), Direction.NORTH, armed);
            return;
        }
        for (Direction face : faces) {
            emitPanelParticles(world, KeycardReaderGeometry.surfaceAnchor(
                    world, pos, face, KeycardReaderGeometry.DEPTH / 2), face, armed);
        }
    }

    private static void emitPanelParticles(ServerWorld world, Vec3d center, Direction face, boolean armed) {
        world.spawnParticles(new BlockStateParticleEffect(ParticleTypes.BLOCK, mountState(face, armed)),
                center.x, center.y, center.z, 12, 0.09, 0.12, 0.09, 0.02);
    }

    public static void dropOrphansAround(ServerWorld world, BlockPos pos, @Nullable PlayerEntity player) {
        dropIfOrphaned(world, pos, player);
        for (Direction dir : Direction.values()) {
            dropIfOrphaned(world, pos.offset(dir), player);
        }
    }

    private static void dropIfOrphaned(ServerWorld world, BlockPos pos, @Nullable PlayerEntity player) {
        if (!KeycardReaderStore.get(world).has(pos)) return;
        if (!KeycardReaderBlock.isSupport(world.getBlockState(pos))
                || KeycardReaderGeometry.mountFaces(world, pos).isEmpty()) {
            removeAndDrop(world, pos, player);
        }
    }

    public static void validate(ServerWorld world) {
        for (KeycardReaderStore.Entry entry : KeycardReaderStore.get(world).all()) {
            BlockPos pos = entry.pos();
            if (!world.isChunkLoaded(pos)) continue;
            if (!KeycardReaderBlock.isSupport(world.getBlockState(pos))
                    || KeycardReaderGeometry.mountFaces(world, pos).isEmpty()) {
                removeAndDrop(world, pos, null);
                continue;
            }
            BlockPos host = KeycardReaderGeometry.hostPos(world, pos);
            if (!host.equals(pos)) {
                KeycardReaderStore store = KeycardReaderStore.get(world);
                store.remove(pos);
                ModPackets.broadcastKeycardReaderUpdate(world, pos, false, false);
                if (!store.has(host)) {
                    store.add(host, entry.owner());
                    store.setCard(host, entry.cardId());
                    ModPackets.broadcastKeycardReaderUpdate(world, host, true, entry.isArmed());
                }
            }
        }
    }
}
