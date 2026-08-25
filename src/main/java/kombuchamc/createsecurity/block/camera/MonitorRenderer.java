package kombuchamc.createsecurity.block.camera;

import com.mojang.blaze3d.systems.RenderSystem;
import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.MonitorBlock;
import kombuchamc.createsecurity.block.entity.MonitorBlockEntity;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.client.render.block.entity.BlockEntityRendererFactory;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

public class MonitorRenderer implements BlockEntityRenderer<MonitorBlockEntity> {

    private static final Identifier ARROW_TEXTURE =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/camera_display_button.png");

    private static final Identifier DIRT_TEXTURE = new Identifier("textures/block/dirt.png");

    private static final float PLANE_LOW = 7f / 16f;
    private static final float PLANE_HIGH = 9f / 16f;
    private static final float IMG_EPS = 0.0015f;
    private static final float NAME_EPS = 0.0025f;
    private static final float ARROW_EPS = 0.004f;

    private static final float[] VX = new float[4];
    private static final float[] VY = new float[4];
    private static final float[] VZ = new float[4];
    private static final float[] US = new float[4];
    private static final float[] VS = new float[4];

    public MonitorRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(MonitorBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = be.getCachedState();
        Direction facing = state.get(MonitorBlock.FACING);

        if (CameraViewManager.isPreRendering()) {
            if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
                immediate.draw();
            }
            drawColorQuad(matrices, facing, MonitorBlock.SCREEN_X_LOW, MonitorBlock.SCREEN_X_HIGH,
                    MonitorBlock.SCREEN_Y_LOW, MonitorBlock.SCREEN_Y_HIGH, IMG_EPS, 0f, 0f, 0f);
            return;
        }

        BlockPos pos = be.getPos();
        Vec3d viewPos = MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        double front = (viewPos.x - (pos.getX() + 0.5)) * facing.getOffsetX()
                     + (viewPos.z - (pos.getZ() + 0.5)) * facing.getOffsetZ();
        if (front > 0) {
            be.markSeenNow();
        }

        BlockPos linkedCamera = be.getLinkedCameraPos();
        SimpleFramebuffer fb = linkedCamera == null ? null
                : CameraViewManager.getFramebuffer(linkedCamera);
        int colorTex = fb == null ? 0 : fb.getColorAttachment();

        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }

        if (linkedCamera != null && !CameraViewManager.isCameraInClientRange(linkedCamera)) {
            RenderSystem.setShaderTexture(0, DIRT_TEXTURE);
            drawBoundTexturedQuad(matrices, facing, MonitorBlock.IMG_X_LOW, MonitorBlock.IMG_X_HIGH,
                    MonitorBlock.IMG_Y_LOW, MonitorBlock.IMG_Y_HIGH, IMG_EPS,
                    1f, 0f, MonitorBlock.DIRT_V_HIGH, MonitorBlock.DIRT_V_LOW,
                    0.25f, 0.25f, 0.25f);
            if (be.getCameraCount() > 1) {
                drawCycleArrows(matrices, facing, pos);
            }
            drawCenteredMessage(matrices, vertexConsumers, facing, net.minecraft.text.Text
                    .translatable("message.create-security.no_signal").getString(), 0xFF5555);
            if (be.getCameraLabel() != null) {
                drawLabel(matrices, vertexConsumers, facing, be.getCameraLabel());
            }
            return;
        }

        if (colorTex <= 0) {
            drawColorQuad(matrices, facing, MonitorBlock.SCREEN_X_LOW, MonitorBlock.SCREEN_X_HIGH,
                    MonitorBlock.SCREEN_Y_LOW, MonitorBlock.SCREEN_Y_HIGH, IMG_EPS, 0f, 0f, 0f);
            return;
        }

        RenderSystem.setShaderTexture(0, colorTex);
        drawBoundTexturedQuad(matrices, facing, MonitorBlock.IMG_X_LOW, MonitorBlock.IMG_X_HIGH,
                MonitorBlock.IMG_Y_LOW, MonitorBlock.IMG_Y_HIGH, IMG_EPS,
                1f, 0f, MonitorBlock.FEED_V_LOW, MonitorBlock.FEED_V_HIGH);

        drawNameOverlay(matrices, facing, linkedCamera);

        if (be.getCameraCount() > 1) {
            drawCycleArrows(matrices, facing, pos);
        }

        if (be.getCameraLabel() != null) {
            drawLabel(matrices, vertexConsumers, facing, be.getCameraLabel());
        }
    }

    private static void drawNameOverlay(MatrixStack matrices, Direction facing, BlockPos cameraPos) {
        int nameTex = CameraViewManager.getNameOverlayTexture(cameraPos);
        if (nameTex <= 0) return;
        computeQuadVerts(facing, MonitorBlock.IMG_X_LOW, MonitorBlock.IMG_X_HIGH,
                MonitorBlock.IMG_Y_LOW, MonitorBlock.IMG_Y_HIGH, NAME_EPS);
        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, nameTex);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        US[0] = 1f; US[1] = 0f; US[2] = 0f; US[3] = 1f;
        VS[0] = MonitorBlock.FEED_V_LOW; VS[1] = MonitorBlock.FEED_V_LOW;
        VS[2] = MonitorBlock.FEED_V_HIGH; VS[3] = MonitorBlock.FEED_V_HIGH;
        for (int i = 0; i < 4; i++) {
            buf.vertex(mat, VX[i], VY[i], VZ[i]).texture(US[i], VS[i]).next();
        }
        tess.draw();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        matrices.pop();
    }

    private static void drawCycleArrows(MatrixStack matrices, Direction facing, BlockPos pos) {
        float[] fwdRect = MonitorBlock.cycleArrowRect();
        float[] backRect = MonitorBlock.backArrowRect();

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d hover = null;
        HitResult target = client.crosshairTarget;
        if (target instanceof BlockHitResult blockHit
                && target.getType() == HitResult.Type.BLOCK
                && blockHit.getSide() == facing
                && blockHit.getBlockPos().equals(pos)) {
            hover = blockHit.getPos();
        }
        Vec3d click = (MonitorBlock.lastArrowClickFacing == facing
                && client.options.useKey.isPressed())
                ? MonitorBlock.lastArrowClickHit : null;

        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, ARROW_TEXTURE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f mat = matrices.peek().getPositionMatrix();
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        addArrowSprite(buf, mat, facing, fwdRect, true, spriteIndex(fwdRect, pos, facing, hover, click));
        addArrowSprite(buf, mat, facing, backRect, false, spriteIndex(backRect, pos, facing, hover, click));

        tess.draw();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
    }

    private static int spriteIndex(float[] rect, BlockPos pos, Direction facing,
                                   Vec3d hover, Vec3d click) {
        boolean hovered = hover != null && hitInRect(hover, pos, facing, rect);
        if (hovered && click != null && hitInRect(click, pos, facing, rect)) return 2;
        return hovered ? 1 : 0;
    }

    private static boolean hitInRect(Vec3d hit, BlockPos pos, Direction facing, float[] rect) {
        double lx = hit.x - pos.getX();
        double ly = hit.y - pos.getY();
        double lz = hit.z - pos.getZ();
        float vx = switch (facing) {
            case NORTH -> (float) lx;
            case SOUTH -> 1f - (float) lx;
            case WEST -> 1f - (float) lz;
            default -> (float) lz;
        };
        float vy = (float) ly;
        float pad = 0.02f;
        return vx >= rect[0] - pad && vx <= rect[1] + pad
            && vy >= rect[2] - pad && vy <= rect[3] + pad;
    }

    private static void addArrowSprite(BufferBuilder buf, Matrix4f mat, Direction facing,
                                       float[] rect, boolean tipAtLowX, int sprite) {
        computeQuadVerts(facing, rect[0], rect[1], rect[2], rect[3], ARROW_EPS);
        float uMin = sprite / 3f;
        float uMax = uMin + 1f / 3f;
        float u0 = tipAtLowX ? uMax : uMin;
        float u1 = tipAtLowX ? uMin : uMax;
        US[0] = u0; US[1] = u1; US[2] = u1; US[3] = u0;
        VS[0] = 1f; VS[1] = 1f; VS[2] = 0f; VS[3] = 0f;
        for (int i = 0; i < 4; i++) {
            buf.vertex(mat, VX[i], VY[i], VZ[i]).texture(US[i], VS[i]).next();
        }
    }

    private static void drawCenteredMessage(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                            Direction facing, String message, int color) {
        net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        float scale = MonitorBlock.IMG_H * 0.2f / 9f;
        float textWidth = tr.getWidth(message) * scale;
        float fx = (MonitorBlock.IMG_X_LOW + MonitorBlock.IMG_X_HIGH) / 2f + textWidth / 2f;
        float fy = (MonitorBlock.IMG_Y_LOW + MonitorBlock.IMG_Y_HIGH) / 2f + 9f * scale / 2f;

        float wx, wz;
        float yaw;
        switch (facing) {
            case NORTH -> { wx = fx;      wz = PLANE_LOW;  yaw = 180f; }
            case SOUTH -> { wx = 1f - fx; wz = PLANE_HIGH; yaw = 0f;   }
            case WEST  -> { wx = PLANE_LOW;  wz = 1f - fx; yaw = -90f; }
            default    -> { wx = PLANE_HIGH; wz = fx;      yaw = 90f;  }
        }

        matrices.push();
        matrices.translate(wx + facing.getOffsetX() * 0.008f,
                           fy,
                           wz + facing.getOffsetZ() * 0.008f);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.scale(scale, -scale, scale);
        tr.draw(message, 0, 0, color, true, matrices.peek().getPositionMatrix(),
                vertexConsumers, net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                0, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }

    private static void drawLabel(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                  Direction facing, String label) {
        net.minecraft.client.font.TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        float margin = MonitorBlock.IMG_H * 0.06f;
        float fx = MonitorBlock.IMG_X_HIGH - margin;
        float fy = MonitorBlock.IMG_Y_HIGH - margin;
        float scale = MonitorBlock.IMG_H * 0.14f / 9f;

        float wx, wz;
        float yaw;
        switch (facing) {
            case NORTH -> { wx = fx;      wz = PLANE_LOW;  yaw = 180f; }
            case SOUTH -> { wx = 1f - fx; wz = PLANE_HIGH; yaw = 0f;   }
            case WEST  -> { wx = PLANE_LOW;  wz = 1f - fx; yaw = -90f; }
            default    -> { wx = PLANE_HIGH; wz = fx;      yaw = 90f;  }
        }

        matrices.push();
        matrices.translate(wx + facing.getOffsetX() * 0.006f,
                           fy,
                           wz + facing.getOffsetZ() * 0.006f);
        matrices.multiply(net.minecraft.util.math.RotationAxis.POSITIVE_Y.rotationDegrees(yaw));
        matrices.scale(scale, -scale, scale);
        tr.draw(label, 0, 0, 0x555555, true, matrices.peek().getPositionMatrix(),
                vertexConsumers, net.minecraft.client.font.TextRenderer.TextLayerType.NORMAL,
                0, net.minecraft.client.render.LightmapTextureManager.MAX_LIGHT_COORDINATE);
        matrices.pop();
    }

    private static void computeQuadVerts(Direction facing,
                                         float xLow, float xHigh, float yLow, float yHigh,
                                         float out) {
        VY[0] = yLow; VY[1] = yLow; VY[2] = yHigh; VY[3] = yHigh;
        switch (facing) {
            case NORTH -> {
                VX[0] = xLow; VX[1] = xHigh; VX[2] = xHigh; VX[3] = xLow;
                VZ[0] = VZ[1] = VZ[2] = VZ[3] = PLANE_LOW - out;
            }
            case SOUTH -> {
                VX[0] = 1f - xLow; VX[1] = 1f - xHigh; VX[2] = 1f - xHigh; VX[3] = 1f - xLow;
                VZ[0] = VZ[1] = VZ[2] = VZ[3] = PLANE_HIGH + out;
            }
            case WEST -> {
                VX[0] = VX[1] = VX[2] = VX[3] = PLANE_LOW - out;
                VZ[0] = 1f - xLow; VZ[1] = 1f - xHigh; VZ[2] = 1f - xHigh; VZ[3] = 1f - xLow;
            }
            default -> {
                VX[0] = VX[1] = VX[2] = VX[3] = PLANE_HIGH + out;
                VZ[0] = xLow; VZ[1] = xHigh; VZ[2] = xHigh; VZ[3] = xLow;
            }
        }
    }

    private static void drawColorQuad(MatrixStack matrices, Direction facing,
                                      float xLow, float xHigh, float yLow, float yHigh,
                                      float out, float r, float g, float b) {
        if (xHigh <= xLow || yHigh <= yLow) return;
        computeQuadVerts(facing, xLow, xHigh, yLow, yHigh, out);
        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        for (int i = 0; i < 4; i++) {
            buf.vertex(mat, VX[i], VY[i], VZ[i]).color(r, g, b, 1f).next();
        }
        tess.draw();
        RenderSystem.enableCull();
        matrices.pop();
    }

    private static void drawBoundTexturedQuad(MatrixStack matrices, Direction facing,
                                              float xLow, float xHigh, float yLow, float yHigh,
                                              float out, float u0, float u1, float v0, float v1) {
        drawBoundTexturedQuad(matrices, facing, xLow, xHigh, yLow, yHigh, out,
                u0, u1, v0, v1, 1f, 1f, 1f);
    }

    private static void drawBoundTexturedQuad(MatrixStack matrices, Direction facing,
                                              float xLow, float xHigh, float yLow, float yHigh,
                                              float out, float u0, float u1, float v0, float v1,
                                              float r, float g, float b) {
        computeQuadVerts(facing, xLow, xHigh, yLow, yHigh, out);
        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(r, g, b, 1f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        US[0] = u0; US[1] = u1; US[2] = u1; US[3] = u0;
        VS[0] = v0; VS[1] = v0; VS[2] = v1; VS[3] = v1;
        for (int i = 0; i < 4; i++) {
            buf.vertex(mat, VX[i], VY[i], VZ[i]).texture(US[i], VS[i]).next();
        }
        tess.draw();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.enableCull();
        matrices.pop();
    }
}

