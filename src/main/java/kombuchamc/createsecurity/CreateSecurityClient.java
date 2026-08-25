package kombuchamc.createsecurity;

import com.simibubi.create.foundation.config.ui.BaseConfigScreen;
import kombuchamc.createsecurity.block.RegisterModBlocks;
import kombuchamc.createsecurity.block.entity.ModBlockEntities;
import kombuchamc.createsecurity.config.CSConfigs;
import kombuchamc.createsecurity.block.camera.CameraBlockRenderer;
import kombuchamc.createsecurity.block.camera.CameraDisplayRenderer;
import kombuchamc.createsecurity.network.ModClientPackets;
import kombuchamc.createsecurity.ponder.CreateSecurityPonderIndex;
import kombuchamc.createsecurity.screen.CameraLinkScreen;
import kombuchamc.createsecurity.screen.CameraScreen;
import kombuchamc.createsecurity.screen.ModScreenHandlers;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.blockrenderlayer.v1.BlockRenderLayerMap;
import net.minecraft.client.gui.screen.ingame.HandledScreens;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactories;

public class CreateSecurityClient implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        BaseConfigScreen.setDefaultActionFor(CreateSecurity.MOD_ID, screen -> screen
                .withSpecs(null, CSConfigs.SPEC, null)
                .withTitles("Client Config", "Settings", "Server Config"));
        BlockRenderLayerMap.INSTANCE.putBlock(RegisterModBlocks.ALARM, RenderLayer.getTranslucent());
        BlockEntityRendererFactories.register(ModBlockEntities.CAMERA_BLOCK_ENTITY, CameraBlockRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.CAMERA_DISPLAY_BLOCK_ENTITY, CameraDisplayRenderer::new);
        BlockEntityRendererFactories.register(ModBlockEntities.MONITOR_BLOCK_ENTITY,
                kombuchamc.createsecurity.block.camera.MonitorRenderer::new);
        HandledScreens.register(ModScreenHandlers.CAMERA_SCREEN_HANDLER, CameraScreen::new);
        HandledScreens.register(ModScreenHandlers.CAMERA_LINK_SCREEN_HANDLER, CameraLinkScreen::new);
        net.fabricmc.fabric.api.client.rendering.v1.ColorProviderRegistry.ITEM.register(
                (stack, tintIndex) -> tintIndex == 1
                        ? kombuchamc.createsecurity.items.KeycardItem.getTint(stack)
                        : 0xFFFFFF,
                kombuchamc.createsecurity.items.RegisterModItems.KEYCARD);
        kombuchamc.createsecurity.client.KeycardReaderPreview.register();
        kombuchamc.createsecurity.client.KeycardReaderRenderer.register();
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents.DISCONNECT.register(
                (handler, client) -> kombuchamc.createsecurity.client.KeycardReaderClientStore.clear());
        ModClientPackets.registerClientPackets();
        CreateSecurityPonderIndex.register();
    }
}

