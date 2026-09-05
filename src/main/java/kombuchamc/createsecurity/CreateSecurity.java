package kombuchamc.createsecurity;

import kombuchamc.createsecurity.block.CameraDisplay;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import kombuchamc.createsecurity.block.entity.ModBlockEntities;
import kombuchamc.createsecurity.items.RegisterModItemGroups;
import kombuchamc.createsecurity.items.RegisterModItems;
import kombuchamc.createsecurity.network.ModPackets;
import kombuchamc.createsecurity.screen.ModScreenHandlers;
import kombuchamc.createsecurity.sound.RegisterModSounds;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.ActionResult;
import net.minecraft.util.math.BlockPos;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CreateSecurity implements ModInitializer {
	public static final String MOD_ID = "create-security";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	private static final int RESYNC_DELAY_TICKS = 2;

	private record DeniedBreak(net.minecraft.server.network.ServerPlayerEntity player,
			net.minecraft.server.world.ServerWorld world, BlockPos pos, long due) {}

	private static final java.util.List<DeniedBreak> DENIED_BREAKS = new java.util.ArrayList<>();

	private static boolean resyncDenied(net.minecraft.world.World world,
			net.minecraft.entity.player.PlayerEntity player, BlockPos pos) {
		if (player instanceof net.minecraft.server.network.ServerPlayerEntity serverPlayer
				&& world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
			DENIED_BREAKS.add(new DeniedBreak(serverPlayer, serverWorld, pos.toImmutable(),
					serverWorld.getTime() + RESYNC_DELAY_TICKS));
		}
		return false;
	}

	private static void flushDeniedBreaks() {
		if (DENIED_BREAKS.isEmpty()) return;
		java.util.Iterator<DeniedBreak> it = DENIED_BREAKS.iterator();
		while (it.hasNext()) {
			DeniedBreak denied = it.next();
			net.minecraft.server.world.ServerWorld world = denied.world();
			if (world.getTime() < denied.due()) continue;
			it.remove();
			if (denied.player().isRemoved()) continue;
			BlockPos pos = denied.pos();
			denied.player().networkHandler.sendPacket(
					new net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket(world, pos));
			net.minecraft.block.entity.BlockEntity be = world.getBlockEntity(pos);
			net.minecraft.network.packet.Packet<net.minecraft.network.listener.ClientPlayPacketListener> update =
					be == null ? null : be.toUpdatePacket();
			if (update != null) denied.player().networkHandler.sendPacket(update);
		}
	}

	@Override
	public void onInitialize() {
		kombuchamc.createsecurity.config.CSConfigs.register();
        RegisterModBlocks.registerModBlocks();
        kombuchamc.createsecurity.block.obsidian.ObsidianBlocks.registerObsidianBlocks();
        RegisterModItemGroups.registerItemGropus();
        RegisterModItems.registerModItems();
		RegisterModSounds.registerModSounds();
		ModBlockEntities.registerBlockEntities();
		kombuchamc.createsecurity.entity.ModEntities.registerEntities();
		ModScreenHandlers.registerScreenHandlers();
		ModPackets.registerServerPackets();
		kombuchamc.createsecurity.recipe.ModRecipes.registerRecipes();
		kombuchamc.createsecurity.block.TurretArmInteraction.register();

		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
				(world, player, pos, state, blockEntity) -> {
			if (world.isClient) return true;
			if (kombuchamc.createsecurity.block.obsidian.ObsidianBlocks.isSecured(state)
					&& !kombuchamc.createsecurity.block.obsidian.ObsidianProtection.canBreak(state, player, pos)) {
				kombuchamc.createsecurity.block.obsidian.ObsidianProtection.warnNotOwner(player);
				return resyncDenied(world, player, pos);
			}
			if (!kombuchamc.createsecurity.block.BlockOwnership.checkAndWarn(blockEntity, player)) {
				return resyncDenied(world, player, pos);
			}
			if (world instanceof net.minecraft.server.world.ServerWorld serverWorld
					&& kombuchamc.createsecurity.block.KeycardReaderPlacement.readerPosFor(serverWorld, pos) != null
					&& !kombuchamc.createsecurity.block.KeycardReaderPlacement.canModify(serverWorld,
							kombuchamc.createsecurity.block.KeycardReaderPlacement.readerPosFor(serverWorld, pos), player)) {
				kombuchamc.createsecurity.block.KeycardReaderPlacement.warnNotOwner(player);
				return resyncDenied(world, player, pos);
			}
			return true;
		});

		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.AFTER.register(
				(world, player, pos, state, blockEntity) -> {
			if (world instanceof net.minecraft.server.world.ServerWorld serverWorld) {
				kombuchamc.createsecurity.block.KeycardReaderPlacement.dropOrphansAround(serverWorld, pos, player);
			}
		});

		net.fabricmc.fabric.api.event.player.AttackBlockCallback.EVENT.register(
				(player, world, hand, pos, direction) -> {
			if (!(world instanceof net.minecraft.server.world.ServerWorld serverWorld)) return ActionResult.PASS;
			net.minecraft.block.BlockState attacked = world.getBlockState(pos);
			if (kombuchamc.createsecurity.block.obsidian.ObsidianBlocks.isSecured(attacked)
					&& !kombuchamc.createsecurity.block.obsidian.ObsidianProtection.canBreak(attacked, player, pos)) {
				kombuchamc.createsecurity.block.obsidian.ObsidianProtection.warnNotOwner(player);
				return ActionResult.FAIL;
			}
			BlockPos readerPos = kombuchamc.createsecurity.block.KeycardReaderPlacement.mountedReaderAt(serverWorld, pos, direction,
					kombuchamc.createsecurity.block.KeycardReaderPlacement.aimPointOn(player, pos, direction));
			if (readerPos == null) return ActionResult.PASS;
			if (!kombuchamc.createsecurity.block.KeycardReaderPlacement.canModify(serverWorld, readerPos, player)) {
				kombuchamc.createsecurity.block.KeycardReaderPlacement.warnNotOwner(player);
				return ActionResult.FAIL;
			}
			kombuchamc.createsecurity.block.KeycardReaderPlacement.removeAndDrop(serverWorld, readerPos, player);
			return ActionResult.SUCCESS;
		});

		net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents.END_WORLD_TICK.register(world -> {
			flushDeniedBreaks();
			kombuchamc.createsecurity.block.KeycardReaderAttachment.clearPending();
			kombuchamc.createsecurity.block.KeycardReaderDoors.tick(world);
			kombuchamc.createsecurity.block.KeycardReaderLights.tick(world);
			if (world.getTime() % 20 == 0) kombuchamc.createsecurity.block.KeycardReaderPlacement.validate(world);
		});

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, server) -> {
					ModPackets.sendKeycardReaderFullSync(handler.player);
					ModPackets.sendObsidianOwnerFullSync(handler.player);
				});

		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD
				.register((player, origin, destination) -> {
					ModPackets.sendKeycardReaderFullSync(player);
					ModPackets.sendObsidianOwnerFullSync(player);
				});

		UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {

			if (world.isClient) return ActionResult.PASS;

			ActionResult readerPlacement = kombuchamc.createsecurity.block.KeycardReaderPlacement
					.tryPlace(player, world, hand, hitResult);
			if (readerPlacement != ActionResult.PASS) return readerPlacement;

			BlockPos blockPos = hitResult.getBlockPos();
			Block block = world.getBlockState(blockPos).getBlock();

			if (world instanceof net.minecraft.server.world.ServerWorld readerWorld && player.isSneaking()
					&& net.minecraft.registry.Registries.ITEM.getId(player.getStackInHand(hand).getItem())
							.getPath().contains("wrench")
					&& kombuchamc.createsecurity.block.KeycardReaderPlacement.mountedReaderAt(readerWorld, blockPos, hitResult.getSide(),
							hitResult.getPos()) != null) {
				BlockPos wrenchedReader = kombuchamc.createsecurity.block.KeycardReaderPlacement.mountedReaderAt(
						readerWorld, blockPos, hitResult.getSide(), hitResult.getPos());
				if (!kombuchamc.createsecurity.block.KeycardReaderPlacement.canModify(readerWorld, wrenchedReader, player)) {
					kombuchamc.createsecurity.block.KeycardReaderPlacement.warnNotOwner(player);
					return ActionResult.FAIL;
				}
				kombuchamc.createsecurity.block.KeycardReaderPlacement.removeAndDrop(readerWorld, wrenchedReader, player);
				return ActionResult.SUCCESS;
			}

			ActionResult readerLock = kombuchamc.createsecurity.block.KeycardReaderLock
					.onUseHost(player, world, hand, hitResult);
			if (readerLock != ActionResult.PASS) return readerLock;

			boolean wrenchable = block instanceof CameraDisplay
					|| block instanceof kombuchamc.createsecurity.block.CameraLinkBlock
					|| block instanceof kombuchamc.createsecurity.block.camera.CameraBlock
					|| block instanceof kombuchamc.createsecurity.block.Alarm
					|| block instanceof kombuchamc.createsecurity.block.MonitorBlock;
			if (wrenchable && player.isSneaking()
					&& net.minecraft.registry.Registries.ITEM.getId(player.getStackInHand(hand).getItem())
							.getPath().contains("wrench")) {

				if (!kombuchamc.createsecurity.block.BlockOwnership.checkAndWarn(
						world.getBlockEntity(blockPos), player)) {
					return ActionResult.FAIL;
				}
				ItemStack blockItem = new ItemStack(block);

				if (!player.getInventory().insertStack(blockItem)) {
					CreateSecurity.LOGGER.info("Failed to add block to Inventory!");
					world.breakBlock(blockPos, true);
				} else {
					world.breakBlock(blockPos, false);
				}

				world.playSound(null, blockPos, SoundEvents.BLOCK_STONE_BREAK, SoundCategory.BLOCKS, 1f, 1f);
				return ActionResult.SUCCESS;
			}

			return ActionResult.PASS;
		});
	}
}
