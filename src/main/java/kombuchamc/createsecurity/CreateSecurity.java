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

	@Override
	public void onInitialize() {
		kombuchamc.createsecurity.config.CSConfigs.register();
        RegisterModBlocks.registerModBlocks();
        RegisterModItemGroups.registerItemGropus();
        RegisterModItems.registerModItems();
		RegisterModSounds.registerModSounds();
		ModBlockEntities.registerBlockEntities();
		ModScreenHandlers.registerScreenHandlers();
		ModPackets.registerServerPackets();
		kombuchamc.createsecurity.recipe.ModRecipes.registerRecipes();

		net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents.BEFORE.register(
				(world, player, pos, state, blockEntity) -> {
			if (world.isClient) return true;
			if (!kombuchamc.createsecurity.block.BlockOwnership.checkAndWarn(blockEntity, player)) return false;
			if (world instanceof net.minecraft.server.world.ServerWorld serverWorld
					&& kombuchamc.createsecurity.block.KeycardReaderPlacement.readerPosFor(serverWorld, pos) != null
					&& !kombuchamc.createsecurity.block.KeycardReaderPlacement.canModify(serverWorld,
							kombuchamc.createsecurity.block.KeycardReaderPlacement.readerPosFor(serverWorld, pos), player)) {
				kombuchamc.createsecurity.block.KeycardReaderPlacement.warnNotOwner(player);
				return false;
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
			if (world.getTime() % 20 == 0) kombuchamc.createsecurity.block.KeycardReaderPlacement.validate(world);
		});

		net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents.JOIN.register(
				(handler, sender, server) -> ModPackets.sendKeycardReaderFullSync(handler.player));

		net.fabricmc.fabric.api.entity.event.v1.ServerEntityWorldChangeEvents.AFTER_PLAYER_CHANGE_WORLD
				.register((player, origin, destination) -> ModPackets.sendKeycardReaderFullSync(player));

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

