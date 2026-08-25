package kombuchamc.createsecurity.client;

import kombuchamc.createsecurity.block.KeycardReaderBlock;
import kombuchamc.createsecurity.block.KeycardReaderGeometry;
import kombuchamc.createsecurity.block.KeycardReaderPlacement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.block.BlockState;
import net.minecraft.block.ShulkerBoxBlock;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;

@Environment(EnvType.CLIENT)
public final class KeycardReaderRenderer {

    private static final double RENDER_RANGE = 64;

    private KeycardReaderRenderer() {}

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || KeycardReaderClientStore.isEmpty()) return;

            VertexConsumerProvider consumers = context.consumers();
            MatrixStack matrices = context.matrixStack();
            if (consumers == null || matrices == null) return;

            Vec3d cam = context.camera().getPos();
            float tickDelta = context.tickDelta();
            KeycardReaderClientStore.forEachNear(cam, RENDER_RANGE, (pos, armed) -> {
                if (!KeycardReaderBlock.isSupport(client.world.getBlockState(pos))) return;
                for (Direction face : KeycardReaderGeometry.mountFaces(client.world, pos)) {
                    renderPanel(client, matrices, consumers, cam, pos, face, armed, tickDelta);
                }
            });
        });
    }

    private static void renderPanel(MinecraftClient client, MatrixStack matrices,
                                    VertexConsumerProvider consumers, Vec3d cam,
                                    BlockPos pos, Direction face, boolean armed, float tickDelta) {
        Vec3d origin = KeycardReaderGeometry.renderOrigin(client.world, pos, face);
        BlockPos lightPos = BlockPos.ofFloored(KeycardReaderGeometry.surfaceAnchor(
                client.world, pos, face, KeycardReaderGeometry.DEPTH + 0.01));
        BlockState state = KeycardReaderPlacement.mountState(face, armed);

        matrices.push();
        float lid = lidProgress(client, pos, tickDelta);
        if (lid > 0) {
            double centerX = pos.getX() + 0.5;
            double centerY = pos.getY() + 0.5;
            double centerZ = pos.getZ() + 0.5;
            matrices.translate(centerX - cam.x, centerY - cam.y, centerZ - cam.z);
            matrices.translate(face.getOffsetX() * 0.5 * lid,
                    face.getOffsetY() * 0.5 * lid,
                    face.getOffsetZ() * 0.5 * lid);
            matrices.multiply(lidAxis(face).rotationDegrees(270f * lid));
            matrices.translate(origin.x - centerX, origin.y - centerY, origin.z - centerZ);
        } else {
            matrices.translate(origin.x - cam.x, origin.y - cam.y, origin.z - cam.z);
        }
        client.getBlockRenderManager().renderBlockAsEntity(state, matrices, consumers,
                WorldRenderer.getLightmapCoordinates(client.world, lightPos),
                OverlayTexture.DEFAULT_UV);
        matrices.pop();
    }

    private static float lidProgress(MinecraftClient client, BlockPos pos, float tickDelta) {
        if (!(client.world.getBlockState(pos).getBlock() instanceof ShulkerBoxBlock)) return 0;
        return client.world.getBlockEntity(pos) instanceof ShulkerBoxBlockEntity shulker
                ? shulker.getAnimationProgress(tickDelta)
                : 0;
    }

    private static RotationAxis lidAxis(Direction face) {
        return switch (face) {
            case UP -> RotationAxis.POSITIVE_Y;
            case DOWN -> RotationAxis.NEGATIVE_Y;
            case NORTH -> RotationAxis.NEGATIVE_Z;
            case SOUTH -> RotationAxis.POSITIVE_Z;
            case WEST -> RotationAxis.NEGATIVE_X;
            case EAST -> RotationAxis.POSITIVE_X;
        };
    }
}
