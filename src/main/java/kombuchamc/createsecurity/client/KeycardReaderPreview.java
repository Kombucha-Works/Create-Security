package kombuchamc.createsecurity.client;

import com.mojang.blaze3d.systems.RenderSystem;
import kombuchamc.createsecurity.block.KeycardReaderBlock;
import kombuchamc.createsecurity.block.KeycardReaderGeometry;
import kombuchamc.createsecurity.block.KeycardReaderPlacement;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.World;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Environment(EnvType.CLIENT)
public final class KeycardReaderPreview {

    private static final long REFRESH_MS = 150;

    private static final double GHOST_HALF_WIDTH = KeycardReaderGeometry.PANEL_HALF_WIDTH;
    private static final double GHOST_HALF_HEIGHT = KeycardReaderGeometry.PANEL_HALF_HEIGHT;
    private static final double GHOST_SURFACE_OFFSET = 0.003;

    private static final float[] COLOR = {0.25f, 1.0f, 0.42f, 0.32f};
    private static final float[] COLOR_AIMED = {0.6f, 1.0f, 0.7f, 0.55f};

    private static final List<BlockPos> hosts = new ArrayList<>();
    private static long lastRefresh;

    private record Spot(BlockPos support, Direction face) {}

    private KeycardReaderPreview() {}

    public static void register() {
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null || client.player == null) return;
            if (!KeycardReaderPlacement.isHoldingReader(client.player)) {
                hosts.clear();
                return;
            }
            refresh(client.world, client.player.getBlockPos());
            if (hosts.isEmpty()) return;
            draw(client.world, context.matrixStack(), context.camera().getPos(), aimedSpot(client));
        });
    }

    private static Spot aimedSpot(MinecraftClient client) {
        if (client.crosshairTarget instanceof BlockHitResult hit
                && hit.getType() == HitResult.Type.BLOCK
                && KeycardReaderGeometry.isAimedAtPanel(
                        client.world, hit.getBlockPos(), hit.getSide(), hit.getPos())) {
            return new Spot(KeycardReaderGeometry.hostPos(client.world, hit.getBlockPos()), hit.getSide());
        }
        return null;
    }

    private static void refresh(World world, BlockPos origin) {
        long now = System.currentTimeMillis();
        if (now - lastRefresh < REFRESH_MS) return;
        lastRefresh = now;

        hosts.clear();
        Set<BlockPos> seen = new HashSet<>();
        int range = kombuchamc.createsecurity.config.CSConfigs.keycardReaderMarkerDistance();
        for (BlockPos pos : BlockPos.iterate(origin.add(-range, -range, -range),
                origin.add(range, range, range))) {
            if (!KeycardReaderBlock.isSupport(world.getBlockState(pos))) continue;
            BlockPos host = KeycardReaderGeometry.hostPos(world, pos).toImmutable();
            if (!seen.add(host)) continue;
            hosts.add(host);
        }
    }

    private static void draw(World world, MatrixStack matrices, Vec3d cam, Spot aimed) {
        matrices.push();
        matrices.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder buffer = tessellator.getBuffer();
        buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (BlockPos host : hosts) {
            if (!KeycardReaderBlock.isSupport(world.getBlockState(host))) continue;
            if (KeycardReaderClientStore.hasGroup(world, host)) continue;
            for (Direction face : KeycardReaderGeometry.mountFaces(world, host)) {
                if (!KeycardReaderPlacement.isMountableSurface(world, host, face)) continue;
                Spot spot = new Spot(host, face);
                boolean isAimed = aimed != null && aimed.face() == face
                        && aimed.support().equals(host);
                addGhost(world, buffer, mat, spot, isAimed ? COLOR_AIMED : COLOR);
            }
        }
        tessellator.draw();

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        matrices.pop();
    }

    private static void addGhost(World world, BufferBuilder buffer, Matrix4f mat, Spot spot, float[] color) {
        Direction face = spot.face();
        Vec3d anchor = KeycardReaderGeometry.surfaceAnchor(
                world, spot.support(), face, GHOST_SURFACE_OFFSET);

        Vec3d wide;
        Vec3d tall;
        if (face.getAxis() == Direction.Axis.Y) {
            wide = new Vec3d(1, 0, 0);
            tall = new Vec3d(0, 0, 1);
        } else if (face.getAxis() == Direction.Axis.Z) {
            wide = new Vec3d(1, 0, 0);
            tall = new Vec3d(0, 1, 0);
        } else {
            wide = new Vec3d(0, 0, 1);
            tall = new Vec3d(0, 1, 0);
        }
        wide = wide.multiply(GHOST_HALF_WIDTH);
        tall = tall.multiply(GHOST_HALF_HEIGHT);
        Vec3d depth = Vec3d.of(face.getVector()).multiply(KeycardReaderGeometry.DEPTH);

        Vec3d backBottomLeft = anchor.subtract(wide).subtract(tall);
        Vec3d backBottomRight = anchor.add(wide).subtract(tall);
        Vec3d backTopRight = anchor.add(wide).add(tall);
        Vec3d backTopLeft = anchor.subtract(wide).add(tall);

        Vec3d frontBottomLeft = backBottomLeft.add(depth);
        Vec3d frontBottomRight = backBottomRight.add(depth);
        Vec3d frontTopRight = backTopRight.add(depth);
        Vec3d frontTopLeft = backTopLeft.add(depth);

        quad(buffer, mat, color, backBottomLeft, backBottomRight, backTopRight, backTopLeft);
        quad(buffer, mat, color, frontBottomLeft, frontBottomRight, frontTopRight, frontTopLeft);
        quad(buffer, mat, color, backBottomLeft, backBottomRight, frontBottomRight, frontBottomLeft);
        quad(buffer, mat, color, backTopLeft, backTopRight, frontTopRight, frontTopLeft);
        quad(buffer, mat, color, backBottomLeft, backTopLeft, frontTopLeft, frontBottomLeft);
        quad(buffer, mat, color, backBottomRight, backTopRight, frontTopRight, frontBottomRight);
    }

    private static void quad(BufferBuilder buffer, Matrix4f mat, float[] color,
                             Vec3d a, Vec3d b, Vec3d c, Vec3d d) {
        vertex(buffer, mat, a, color);
        vertex(buffer, mat, b, color);
        vertex(buffer, mat, c, color);
        vertex(buffer, mat, d, color);
    }

    private static void vertex(BufferBuilder buffer, Matrix4f mat, Vec3d pos, float[] color) {
        buffer.vertex(mat, (float) pos.x, (float) pos.y, (float) pos.z)
                .color(color[0], color[1], color[2], color[3]).next();
    }
}
