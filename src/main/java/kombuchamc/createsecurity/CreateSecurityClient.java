package kombuchamc.createsecurity;

import net.createmod.catnip.config.ui.BaseConfigScreen;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import kombuchamc.createsecurity.block.entity.ModBlockEntities;
import kombuchamc.createsecurity.config.CSConfigs;
import kombuchamc.createsecurity.block.camera.CameraBlockRenderer;
import kombuchamc.createsecurity.block.camera.CameraDisplayRenderer;
import kombuchamc.createsecurity.network.ModClientPackets;
import kombuchamc.createsecurity.ponder.CreateSecurityPonderPlugin;
import kombuchamc.createsecurity.screen.CameraLinkScreen;
import kombuchamc.createsecurity.screen.CameraScreen;
import kombuchamc.createsecurity.screen.ModScreenHandlers;
import net.createmod.ponder.foundation.PonderIndex;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class CreateSecurityClient implements ClientModInitializer {

    private static boolean warnedTemporalEffects;

    private static void warnAboutTemporalEffects(net.minecraft.client.MinecraftClient client) {
        if (client.player == null
                || !kombuchamc.createsecurity.client.compat.RenderCompat.shadedFeedsActive()) {
            warnedTemporalEffects = false;
            return;
        }
        if (warnedTemporalEffects) return;
        warnedTemporalEffects = true;
        client.player.sendMessage(
                net.minecraft.text.Text.translatable("message.create-security.shaded_feeds_temporal"),false);
    }

    @Override
    public void onInitializeClient() {
        kombuchamc.createsecurity.client.compat.RenderCompat.init();
        net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents.END_CLIENT_TICK.register(client -> {
            warnAboutTemporalEffects(client);
            if (client.world == null || client.isPaused()) return;
            kombuchamc.createsecurity.client.camera.RemoteParticles.tick();
        });
        BaseConfigScreen.setDefaultActionFor(CreateSecurity.MOD_ID, screen -> screen
                .withSpecs(null, CSConfigs.SPEC, null)
                .withButtonLabels("Client Config", "Settings", "Server Config"));
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.ALARM, RenderLayer.getTranslucent());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.TURRET, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.TURRET_MAST_LOWER, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.TURRET_CRADLE, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.TURRET_SHAFT, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.TURRET_GUN_SHOTGUN, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.TURRET_GUN_MINIGUN, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.TURRET_GUN_AUTOMATIC, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.TURRET_GUN_MINIGUN_ROTOR, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.TURRET_GUN_ROCKET, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.PROJECTILE_REGULAR_556, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.PROJECTILE_INCENDIARY_556, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.PROJECTILE_REGULAR_762, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.PROJECTILE_SLUG_12G, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.PROJECTILE_BUCKSHOT_12G, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.PROJECTILE_INCENDIARY_BUCKSHOT_12G, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.PROJECTILE_HE_ROCKET, RenderLayer.getCutout());
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.PROJECTILE_ROCKET, RenderLayer.getCutout());
        BlockEntityRendererFactories.register(ModBlockEntities.CAMERA_BLOCK_ENTITY, CameraBlockRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.CAMERA_DISPLAY_BLOCK_ENTITY, CameraDisplayRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.MONITOR_BLOCK_ENTITY,
                kombuchamc.createsecurity.block.camera.MonitorRenderer::new);
        net.fabricmc.fabric.api.client.model.loading.v1.ModelLoadingPlugin.register(ctx ->
                ctx.modifyModelAfterBake().register((model, context) ->
                        context.id() != null
                                && context.id().toString().equals("create-security:block/turret_base")
                                ? new kombuchamc.createsecurity.client.TurretDisguiseModel(model)
                                : model));
        BlockEntityRendererFactories.register(ModBlockEntities.TURRET_BLOCK_ENTITY,
                kombuchamc.createsecurity.client.TurretRenderer::new);
        HandledScreens.register(ModScreenHandlers.CAMERA_SCREEN_HANDLER, CameraScreen::new);
        HandledScreens.register(ModScreenHandlers.CAMERA_LINK_SCREEN_HANDLER, CameraLinkScreen::new);
        HandledScreens.register(ModScreenHandlers.KEYCARD_EDITOR_SCREEN_HANDLER,
                kombuchamc.createsecurity.screen.KeycardEditorScreen::new);
        net.fabricmc.fabric.api.client.rendering.v1.EntityRendererRegistry.register(
                kombuchamc.createsecurity.entity.ModEntities.BULLET,
                kombuchamc.createsecurity.client.BulletRenderer::new);
        HandledScreens.register(ModScreenHandlers.TURRET_SCREEN_HANDLER,
                kombuchamc.createsecurity.screen.TurretScreen::new);
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(
                (stack, tintIndex) -> tintIndex == 1
                        ? kombuchamc.createsecurity.items.KeycardItem.getTint(stack)
                        : 0xFFFFFF,
                kombuchamc.createsecurity.items.RegisterModItems.KEYCARD);
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.BLOCK.register(
                kombuchamc.createsecurity.client.TurretDisguiseModel::tint,
                RegisterModBlocks.TURRET);
        kombuchamc.createsecurity.block.obsidian.ObsidianProtection.CLIENT_OWNERSHIP =
                kombuchamc.createsecurity.client.ObsidianOwnerClientStore::canBreak;
        BlockRenderLayerMap.INSTANCE.putBlocks(RenderLayer.getCutout(),
                kombuchamc.createsecurity.block.obsidian.ObsidianBlocks.OBSIDIAN_DOOR,
                kombuchamc.createsecurity.block.obsidian.ObsidianBlocks.OBSIDIAN_TRAPDOOR);
        kombuchamc.createsecurity.block.KeycardReaderPlacement.CLIENT_LOOKUP =
                kombuchamc.createsecurity.client.KeycardReaderClientStore::hasGroup;
        kombuchamc.createsecurity.client.KeycardReaderPreview.register();
        kombuchamc.createsecurity.client.KeycardReaderRenderer.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> {
                    kombuchamc.createsecurity.client.KeycardReaderClientStore.clear();
                    kombuchamc.createsecurity.client.ObsidianOwnerClientStore.clear();
                    kombuchamc.createsecurity.client.camera.RemoteChunkRequests.reset();
                });
        ModClientPackets.registerClientPackets();
        PonderIndex.addPlugin(new CreateSecurityPonderPlugin());
    }
}
