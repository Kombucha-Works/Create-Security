package kombuchamc.createsecurity.block;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.network.ModPackets;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.damage.DamageType;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import net.minecraft.block.BlockState;
import net.minecraft.particle.VibrationParticleEffect;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Formatting;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import java.util.List;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.event.EntityPositionSource;

public final class CodeLock {

    public static final int MIN_CODE = 1000;
    public static final int MAX_CODE = 9999;

    private static final double REACH_SQUARED = 64;

    private static final float SHOCK_DAMAGE = 6f;

    private static final int ARC_TRAVEL_TICKS = 8;

    private static final RegistryKey<DamageType> SHOCK_TYPE = RegistryKey.of(RegistryKeys.DAMAGE_TYPE,
            new Identifier(CreateSecurity.MOD_ID, "code_lock_shock"));

    private CodeLock() {}

    public static ActionResult onUse(ServerPlayerEntity player, ServerWorld world, BlockPos lockPos,
                                     KeycardReaderStore.Entry entry) {
        boolean setting = !entry.isArmed();
        if (setting && !KeycardReaderPlacement.canModify(world, lockPos, player)) {
            KeycardReaderPlacement.warnNotOwner(player);
            return ActionResult.FAIL;
        }
        ModPackets.sendOpenCodeLock(player, lockPos, setting);
        return ActionResult.SUCCESS;
    }

    public static void onCodeSubmitted(ServerPlayerEntity player, BlockPos lockPos, int code) {
        ServerWorld world = player.getServerWorld();
        if (player.squaredDistanceTo(lockPos.getX() + 0.5, lockPos.getY() + 0.5, lockPos.getZ() + 0.5)
                > REACH_SQUARED) {
            return;
        }
        KeycardReaderStore store = KeycardReaderStore.get(world);
        KeycardReaderStore.Entry entry = store.get(lockPos);
        if (entry == null || entry.kind() != LockKind.CODE) return;
        if (code < MIN_CODE || code > MAX_CODE) return;

        if (!entry.isArmed()) {
            if (!KeycardReaderPlacement.canModify(world, lockPos, player)) {
                KeycardReaderPlacement.warnNotOwner(player);
                return;
            }
            store.setSecret(lockPos, code);
            ModPackets.broadcastKeycardReaderUpdate(world, lockPos, true, true, LockKind.CODE);
            play(world, lockPos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.2f);
            message(player, "message.create-security.code_set", Formatting.GREEN);
            return;
        }

        if (code != entry.secret()) {
            shock(world, lockPos, player);
            message(player, "message.create-security.code_wrong", Formatting.RED);
            return;
        }

        play(world, lockPos, SoundEvents.BLOCK_NOTE_BLOCK_PLING.value(), 1.8f);
        openHost(player, world, lockPos);
        KeycardReaderLights.opened(world, lockPos, player);
    }

    private static void shock(ServerWorld world, BlockPos lockPos, ServerPlayerEntity player) {
        world.playSound(null, lockPos, SoundEvents.BLOCK_CONDUIT_ATTACK_TARGET, SoundCategory.BLOCKS,
                0.9f, 1.4f);
        world.playSound(null, lockPos, SoundEvents.BLOCK_FIRE_EXTINGUISH, SoundCategory.BLOCKS,
                0.5f, 1.9f);

        List<Direction> faces = KeycardReaderGeometry.mountFaces(world, lockPos);
        if (faces.isEmpty()) {
            arc(world, Vec3d.ofCenter(lockPos), player);
        } else {
            for (Direction face : faces) {
                arc(world, KeycardReaderGeometry.surfaceAnchor(
                        world, lockPos, face, KeycardReaderGeometry.DEPTH), player);
            }
        }

        player.damage(new DamageSource(
                world.getRegistryManager().get(RegistryKeys.DAMAGE_TYPE).entryOf(SHOCK_TYPE)),
                SHOCK_DAMAGE);
    }

    private static void arc(ServerWorld world, Vec3d at, ServerPlayerEntity player) {
        VibrationParticleEffect effect = new VibrationParticleEffect(
                new EntityPositionSource(player, 1.0f), ARC_TRAVEL_TICKS);
        world.spawnParticles(effect, at.x, at.y, at.z, 1, 0, 0, 0, 0);
    }

    private static void openHost(ServerPlayerEntity player, ServerWorld world, BlockPos lockPos) {
        BlockState state = world.getBlockState(lockPos);
        if (KeycardReaderDoors.isDoor(state)) {
            KeycardReaderDoors.toggleWithKeycard(world, lockPos, player);
            return;
        }

        BlockHitResult hit = new BlockHitResult(Vec3d.ofCenter(lockPos), Direction.UP, lockPos, false);
        KeycardReaderLock.openUnlocked(player, world, Hand.MAIN_HAND, hit);
    }

    private static void play(ServerWorld world, BlockPos pos, net.minecraft.sound.SoundEvent sound,
                             float pitch) {
        world.playSound(null, pos, sound, SoundCategory.BLOCKS, 0.7f, pitch);
    }

    private static void message(ServerPlayerEntity player, String key, Formatting color) {
        player.sendMessage(Text.translatable(key).formatted(color), true);
    }
}
