package kombuchamc.createsecurity.block.camera;

import com.mojang.blaze3d.systems.RenderSystem;
import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.CameraDisplay;
import kombuchamc.createsecurity.block.entity.CameraDisplayBlockEntity;
import kombuchamc.createsecurity.block.entity.CameraDisplayGroup;
import net.minecraft.block.BlockState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.render.*;
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

public class CameraDisplayRenderer implements BlockEntityRenderer<CameraDisplayBlockEntity> {

    private static final float BEZEL = CameraDisplay.BEZEL;

    private static final double SCREEN_PLANE_OFFSET = 0.4375;

    private static final Identifier ARROW_TEXTURE =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/camera_display_button.png");

    private static final Identifier ERROR_TEXTURE =
            new Identifier(CreateSecurity.MOD_ID, "textures/block/camera_display_error.png");

    private static final Identifier DIRT_TEXTURE = new Identifier("textures/block/dirt.png");

    private static final float[] VX = new float[4];
    private static final float[] VY = new float[4];
    private static final float[] VZ = new float[4];
    private static final float[] US = new float[4];
    private static final float[] VS = new float[4];

    public CameraDisplayRenderer(BlockEntityRendererFactory.Context ctx) {}

    @Override
    public void render(CameraDisplayBlockEntity be, float tickDelta, MatrixStack matrices,
                       VertexConsumerProvider vertexConsumers, int light, int overlay) {
        BlockState state = be.getCachedState();
        Direction facing = state.get(CameraDisplay.FACING);

        if (CameraViewManager.isPreRendering()) {
            float pXLow  = state.get(CameraDisplay.RIGHT) ? BEZEL : 0f;
            float pXHigh = state.get(CameraDisplay.LEFT)  ? 1f - BEZEL : 1f;
            float pYLow  = state.get(CameraDisplay.DOWN)  ? BEZEL : 0f;
            float pYHigh = state.get(CameraDisplay.UP)    ? 1f - BEZEL : 1f;
            if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
                immediate.draw();
            }
            CameraDisplayGroup preGroup = be.getGroup();
            boolean showsFeed = be.getLinkedCameraPos() != null
                    && (preGroup == null || !preGroup.valid || preGroup.active);
            if (!showsFeed) {
                drawColorQuad(matrices, facing, pXLow, pXHigh, pYLow, pYHigh, 0f, 0f, 0f);
            } else if (preGroup != null && preGroup.valid) {

                float w = preGroup.width;
                float h = preGroup.height;
                float u0 = (w - preGroup.col) / w;
                float u1 = (w - preGroup.col - 1f) / w;
                float v0 = (preGroup.row + 1f) / h;
                float v1 = preGroup.row / h;
                drawTexturedQuad(matrices, facing, pXLow, pXHigh, pYLow, pYHigh,
                        u0, u1, v0, v1, ERROR_TEXTURE);
            } else {
                drawTexturedQuad(matrices, facing, pXLow, pXHigh, pYLow, pYHigh,
                        1f, 0f, 1f, 0f, ERROR_TEXTURE);
            }
            return;
        }

        BlockPos pos = be.getPos();
        net.minecraft.util.math.Vec3d viewPos =
                net.minecraft.client.MinecraftClient.getInstance().gameRenderer.getCamera().getPos();
        double front = (viewPos.x - (pos.getX() + 0.5)) * facing.getOffsetX()
                     + (viewPos.z - (pos.getZ() + 0.5)) * facing.getOffsetZ();
        if (front > SCREEN_PLANE_OFFSET) {
            be.markSeenNow();
        }

        float xLow  = state.get(CameraDisplay.RIGHT) ? BEZEL : 0f;
        float xHigh = state.get(CameraDisplay.LEFT)  ? 1f - BEZEL : 1f;
        float yLow  = state.get(CameraDisplay.DOWN)  ? BEZEL : 0f;
        float yHigh = state.get(CameraDisplay.UP)    ? 1f - BEZEL : 1f;

        BlockPos linkedCamera = be.getLinkedCameraPos();
        if (linkedCamera == null) {
            drawColorQuad(matrices, facing, xLow, xHigh, yLow, yHigh, 0f, 0f, 0f);
            return;
        }

        if (!CameraViewManager.isCameraInClientRange(linkedCamera)) {
            CameraDisplayGroup rangeGroup = be.getGroup();
            if (rangeGroup != null && rangeGroup.valid && !rangeGroup.active) {
                drawColorQuad(matrices, facing, xLow, xHigh, yLow, yHigh, 0f, 0f, 0f);
                return;
            }
            if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
                immediate.draw();
            }
            drawOutOfRange(matrices, vertexConsumers, facing, state, pos, xLow, xHigh, yLow, yHigh,
                    rangeGroup, be.getCameraCount(), be.getCameraLabel());
            return;
        }

        SimpleFramebuffer fb = CameraViewManager.getFramebuffer(linkedCamera);
        if (fb == null) {
            drawColorQuad(matrices, facing, xLow, xHigh, yLow, yHigh, 0f, 0f, 0f);
            return;
        }

        int colorTex = fb.getColorAttachment();
        if (colorTex <= 0) {
            drawColorQuad(matrices, facing, xLow, xHigh, yLow, yHigh, 0f, 0f, 0f);
            return;
        }

        if (vertexConsumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw();
        }

        CameraDisplayGroup group = be.getGroup();

        if (group != null && group.valid && !group.active) {
            drawColorQuad(matrices, facing, xLow, xHigh, yLow, yHigh, 0f, 0f, 0f);
            return;
        }

        if (group != null && group.valid) {

            float w = group.width;
            float h = group.height;
            float u0 = (w - group.col) / w;
            float u1 = (w - group.col - 1f) / w;
            float v0 = (h - group.row - 1f) / h;
            float v1 = (h - group.row) / h;
            drawTexturedQuad(matrices, facing, xLow, xHigh, yLow, yHigh, u0, u1, v0, v1, colorTex);
            drawNameOverlay(matrices, facing, xLow, xHigh, yLow, yHigh, u0, u1, v0, v1, linkedCamera);

            if (be.getCameraCount() > 1 && group.col == 0 && group.row == group.height - 1) {
                drawCycleArrow(matrices, facing, state, group, pos);
            }

            if (be.getCameraLabel() != null
                    && group.col == group.width - 1 && group.row == 0) {
                drawLabel(matrices, vertexConsumers, facing, be.getCameraLabel(),
                        xHigh, yHigh, (group.height - 2f * BEZEL) / 2f);
            }
        } else {

            float screenW = xHigh - xLow;
            float screenH = yHigh - yLow;
            float imageH = Math.min(screenH, screenW * 2f / 3f);
            float imageW = imageH * 1.5f;
            if (imageW > screenW) {
                imageW = screenW;
                imageH = imageW * 2f / 3f;
            }
            float imgXLow  = xLow + (screenW - imageW) / 2f;
            float imgXHigh = imgXLow + imageW;
            float imgYLow  = yLow + (screenH - imageH) / 2f;
            float imgYHigh = imgYLow + imageH;

            drawLetterboxBars(matrices, facing, xLow, xHigh, yLow, yHigh,
                    imgXLow, imgXHigh, imgYLow, imgYHigh);

            drawTexturedQuad(matrices, facing, imgXLow, imgXHigh, imgYLow, imgYHigh,
                    1f, 0f, 0f, 1f, colorTex);
            drawNameOverlay(matrices, facing, imgXLow, imgXHigh, imgYLow, imgYHigh,
                    1f, 0f, 0f, 1f, linkedCamera);

            if (be.getCameraCount() > 1) {
                drawCycleArrow(matrices, facing, state, group, pos);
            }

            if (be.getCameraLabel() != null) {
                drawLabel(matrices, vertexConsumers, facing, be.getCameraLabel(),
                        imgXHigh, imgYHigh, imageH);
            }
        }
    }

    private static void drawNameOverlay(MatrixStack matrices, Direction facing,
                                        float xLow, float xHigh, float yLow, float yHigh,
                                        float u0, float u1, float v0, float v1,
                                        BlockPos cameraPos) {
        int nameTex = CameraViewManager.getNameOverlayTexture(cameraPos);
        if (nameTex <= 0) return;

        matrices.push();
        matrices.translate(facing.getOffsetX() * 0.002f,
                           facing.getOffsetY() * 0.002f,
                           facing.getOffsetZ() * 0.002f);
        computeQuadVerts(facing, xLow, xHigh, yLow, yHigh, VX, VY, VZ);
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
        US[0] = u0; US[1] = u1; US[2] = u1; US[3] = u0;
        VS[0] = v0; VS[1] = v0; VS[2] = v1; VS[3] = v1;
        for (int i = 0; i < 4; i++) {
            buf.vertex(mat, VX[i], VY[i], VZ[i]).texture(US[i], VS[i]).next();
        }
        tess.draw();

        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        matrices.pop();
    }

    private static void drawOutOfRange(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                       Direction facing, BlockState state, BlockPos pos,
                                       float xLow, float xHigh, float yLow, float yHigh,
                                       CameraDisplayGroup group, int cameraCount, String cameraLabel) {
        String message = net.minecraft.text.Text
                .translatable("message.create-security.no_signal").getString();
        boolean wall = group != null && group.valid;
        RenderSystem.setShaderTexture(0, DIRT_TEXTURE);
        drawBoundTexturedQuad(matrices, facing, xLow, xHigh, yLow, yHigh,
                1f, 0f, 1f, 0f, 0.25f, 0.25f, 0.25f);

        if (cameraCount > 1
                && (!wall || (group.col == 0 && group.row == group.height - 1))) {
            drawCycleArrow(matrices, facing, state, group, pos);
        }

        if (wall) {
            if (group.col != group.width - 1 || group.row != 0) return;
            float imgW = group.width - 2f * BEZEL;
            float imgH = group.height - 2f * BEZEL;
            drawCenteredMessage(matrices, vertexConsumers, facing, message,
                    xHigh - imgW / 2f, yHigh - imgH / 2f, imgH, 0xFF5555);
            if (cameraLabel != null) {
                drawLabel(matrices, vertexConsumers, facing, cameraLabel, xHigh, yHigh, imgH / 2f);
            }
        } else {
            drawCenteredMessage(matrices, vertexConsumers, facing, message,
                    (xLow + xHigh) / 2f, (yLow + yHigh) / 2f, yHigh - yLow, 0xFF5555);
            if (cameraLabel != null) {
                drawLabel(matrices, vertexConsumers, facing, cameraLabel, xHigh, yHigh, yHigh - yLow);
            }
        }
    }

    private static void drawCenteredMessage(MatrixStack matrices, VertexConsumerProvider vertexConsumers,
                                            Direction facing, String message,
                                            float centerFx, float centerFy, float imgH, int color) {
        net.minecraft.client.font.TextRenderer tr =
                net.minecraft.client.MinecraftClient.getInstance().textRenderer;
        float scale = imgH * 0.15f / 9f;
        float fx = centerFx + tr.getWidth(message) * scale / 2f;
        float fy = centerFy + 9f * scale / 2f;

        float wx, wz;
        float yaw;
        switch (facing) {
            case NORTH -> { wx = fx;      wz = 0.0625f;  yaw = 180f; }
            case SOUTH -> { wx = 1f - fx; wz = 0.9375f;  yaw = 0f;   }
            case WEST  -> { wx = 0.0625f; wz = 1f - fx;  yaw = -90f; }
            default    -> { wx = 0.9375f; wz = fx;       yaw = 90f;  }
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
                                  Direction facing, String label,
                                  float leftFx, float topFy, float imgH) {
        net.minecraft.client.font.TextRenderer tr =
                net.minecraft.client.MinecraftClient.getInstance().textRenderer;

        float margin = imgH * 0.05f;
        float fx = leftFx - margin;
        float fy = topFy - margin;

        float scale = imgH * 0.12f / 9f;

        float wx, wz;
        float yaw;
        switch (facing) {
            case NORTH -> { wx = fx;      wz = 0.0625f;  yaw = 180f; }
            case SOUTH -> { wx = 1f - fx; wz = 0.9375f;  yaw = 0f;   }
            case WEST  -> { wx = 0.0625f; wz = 1f - fx;  yaw = -90f; }
            default    -> { wx = 0.9375f; wz = fx;       yaw = 90f;  }
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

    private static void drawCycleArrow(MatrixStack matrices, Direction facing,
                                       BlockState state, CameraDisplayGroup group,
                                       BlockPos pos) {
        float[] fwdRect  = CameraDisplay.cycleArrowRect(state, group);
        float[] backRect = CameraDisplay.backArrowRect(state, group);

        MinecraftClient client = MinecraftClient.getInstance();
        Vec3d hover = null;
        HitResult target = client.crosshairTarget;
        if (target instanceof BlockHitResult blockHit
                && target.getType() == HitResult.Type.BLOCK
                && blockHit.getSide() == facing) {
            hover = blockHit.getPos();
        }
        Vec3d click = (CameraDisplay.lastArrowClickFacing == facing
                && client.options.useKey.isPressed())
                ? CameraDisplay.lastArrowClickHit : null;

        matrices.push();
        matrices.translate(facing.getOffsetX() * 0.004f,
                           facing.getOffsetY() * 0.004f,
                           facing.getOffsetZ() * 0.004f);

        Matrix4f mat = matrices.peek().getPositionMatrix();
        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, ARROW_TEXTURE);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);

        addArrowSprite(buf, mat, facing, fwdRect, true,
                spriteIndex(fwdRect, pos, facing, hover, click));
        addArrowSprite(buf, mat, facing, backRect, false,
                spriteIndex(backRect, pos, facing, hover, click));

        tess.draw();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        matrices.pop();
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
        double depth = switch (facing) {
            case NORTH -> lz;
            case SOUTH -> 1.0 - lz;
            case WEST  -> lx;
            default    -> 1.0 - lx;
        };
        if (depth < -0.1 || depth > 0.2) return false;
        float vx = switch (facing) {
            case NORTH -> (float) lx;
            case SOUTH -> 1f - (float) lx;
            case WEST  -> 1f - (float) lz;
            default    -> (float) lz;
        };
        float vy = (float) ly;
        float pad = 0.03f;
        return vx >= rect[0] - pad && vx <= rect[1] + pad
            && vy >= rect[2] - pad && vy <= rect[3] + pad;
    }

    private static void addArrowSprite(BufferBuilder buf, Matrix4f mat, Direction facing,
                                       float[] rect, boolean tipAtLowX, int sprite) {
        computeQuadVerts(facing, rect[0], rect[1], rect[2], rect[3], VX, VY, VZ);
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

    private static void drawLetterboxBars(MatrixStack matrices, Direction facing,
                                          float xLow, float xHigh, float yLow, float yHigh,
                                          float imgXLow, float imgXHigh,
                                          float imgYLow, float imgYHigh) {
        boolean top    = imgYHigh < yHigh;
        boolean bottom = imgYLow > yLow;
        boolean left   = imgXLow > xLow;
        boolean right  = imgXHigh < xHigh;
        if (!top && !bottom && !left && !right) return;

        matrices.push();
        Matrix4f mat = matrices.peek().getPositionMatrix();

        RenderSystem.disableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.disableBlend();
        RenderSystem.setShader(GameRenderer::getPositionColorProgram);

        Tessellator tess = Tessellator.getInstance();
        BufferBuilder buf = tess.getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        if (top)    addBlackQuad(buf, mat, facing, xLow, xHigh, imgYHigh, yHigh);
        if (bottom) addBlackQuad(buf, mat, facing, xLow, xHigh, yLow, imgYLow);
        if (left)   addBlackQuad(buf, mat, facing, xLow, imgXLow, imgYLow, imgYHigh);
        if (right)  addBlackQuad(buf, mat, facing, imgXHigh, xHigh, imgYLow, imgYHigh);
        tess.draw();

        RenderSystem.enableCull();
        matrices.pop();
    }

    private static void addBlackQuad(BufferBuilder buf, Matrix4f mat, Direction facing,
                                     float xLow, float xHigh, float yLow, float yHigh) {
        computeQuadVerts(facing, xLow, xHigh, yLow, yHigh, VX, VY, VZ);
        for (int i = 0; i < 4; i++) {
            buf.vertex(mat, VX[i], VY[i], VZ[i]).color(0f, 0f, 0f, 1f).next();
        }
    }

    @Override
    public boolean rendersOutsideBoundingBox(CameraDisplayBlockEntity blockEntity) {
        return false;
    }

    private static void computeQuadVerts(Direction facing,
                                          float xLow, float xHigh,
                                          float yLow, float yHigh,
                                          float[] vx, float[] vy, float[] vz) {
        vy[0] = yLow;  vy[1] = yLow;  vy[2] = yHigh; vy[3] = yHigh;
        switch (facing) {
            case NORTH -> {
                vx[0] = xLow;  vx[1] = xHigh; vx[2] = xHigh; vx[3] = xLow;
                vz[0] = vz[1] = vz[2] = vz[3] = 0.0625f;
            }
            case SOUTH -> {
                vx[0] = 1f - xLow;  vx[1] = 1f - xHigh; vx[2] = 1f - xHigh; vx[3] = 1f - xLow;
                vz[0] = vz[1] = vz[2] = vz[3] = 0.9375f;
            }
            case WEST -> {
                vx[0] = vx[1] = vx[2] = vx[3] = 0.0625f;
                vz[0] = 1f - xLow; vz[1] = 1f - xHigh; vz[2] = 1f - xHigh; vz[3] = 1f - xLow;
            }
            default -> {
                vx[0] = vx[1] = vx[2] = vx[3] = 0.9375f;
                vz[0] = xLow; vz[1] = xHigh; vz[2] = xHigh; vz[3] = xLow;
            }
        }
    }

    private static void drawColorQuad(MatrixStack matrices, Direction facing,
                                       float xLow, float xHigh,
                                       float yLow, float yHigh,
                                       float r, float g, float b) {
        computeQuadVerts(facing, xLow, xHigh, yLow, yHigh, VX, VY, VZ);

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

    private static void drawTexturedQuad(MatrixStack matrices, Direction facing,
                                          float xLow, float xHigh,
                                          float yLow, float yHigh,
                                          float u0, float u1, float v0, float v1,
                                          int colorTex) {
        RenderSystem.setShaderTexture(0, colorTex);
        drawBoundTexturedQuad(matrices, facing, xLow, xHigh, yLow, yHigh, u0, u1, v0, v1);
    }

    private static void drawTexturedQuad(MatrixStack matrices, Direction facing,
                                          float xLow, float xHigh,
                                          float yLow, float yHigh,
                                          float u0, float u1, float v0, float v1,
                                          Identifier texture) {
        RenderSystem.setShaderTexture(0, texture);
        drawBoundTexturedQuad(matrices, facing, xLow, xHigh, yLow, yHigh, u0, u1, v0, v1);
    }

    private static void drawBoundTexturedQuad(MatrixStack matrices, Direction facing,
                                          float xLow, float xHigh,
                                          float yLow, float yHigh,
                                          float u0, float u1, float v0, float v1) {
        drawBoundTexturedQuad(matrices, facing, xLow, xHigh, yLow, yHigh,
                u0, u1, v0, v1, 1f, 1f, 1f);
    }

    private static void drawBoundTexturedQuad(MatrixStack matrices, Direction facing,
                                          float xLow, float xHigh,
                                          float yLow, float yHigh,
                                          float u0, float u1, float v0, float v1,
                                          float r, float g, float b) {
        computeQuadVerts(facing, xLow, xHigh, yLow, yHigh, VX, VY, VZ);

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

