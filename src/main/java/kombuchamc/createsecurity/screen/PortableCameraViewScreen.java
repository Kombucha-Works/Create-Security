package kombuchamc.createsecurity.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.camera.CameraViewManager;
import kombuchamc.createsecurity.network.PortableCameraViewEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.SimpleFramebuffer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.client.util.math.MatrixStack;
import org.joml.Matrix4f;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class PortableCameraViewScreen extends Screen {

    private static final Identifier ARROW_TEXTURE =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/camera_display_button.png");

    private static final Identifier DIRT_TEXTURE = new Identifier("textures/block/dirt.png");

    private static final long FLIP_DURATION_MS = 250;

    private static final float FLIP_START_ANGLE = 80f;

    private final long openedAtMs = System.currentTimeMillis();
    private long closingAtMs = -1;
    private float closingFromFlip = 1f;
    private boolean openSoundPlayed = false;
    private static final int ARROW_W = 14;
    private static final int ARROW_H = 22;
    private static final int BOTTOM_MARGIN = 10;

    private final List<PortableCameraViewEntry> entries;
    private int index = 0;

    public PortableCameraViewScreen(List<PortableCameraViewEntry> entries) {
        super(Text.translatable("screen.create-security.portable_camera_view"));
        this.entries = entries;
    }

    private static float ease(float p) {
        return 0.5f - 0.5f * (float) Math.cos(p * Math.PI);
    }

    private static void playFlipSound() {
        MinecraftClient.getInstance().getSoundManager().play(
                net.minecraft.client.sound.PositionedSoundInstance.master(
                        net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }

    private float flipProgress() {
        if (closingAtMs >= 0) {
            long elapsed = System.currentTimeMillis() - closingAtMs;
            if (elapsed >= FLIP_DURATION_MS) return 0f;
            return closingFromFlip * (1f - ease(elapsed / (float) FLIP_DURATION_MS));
        }
        long elapsed = System.currentTimeMillis() - openedAtMs;
        if (elapsed >= FLIP_DURATION_MS) return 1f;
        return ease(elapsed / (float) FLIP_DURATION_MS);
    }

    @Override
    public void close() {
        if (closingAtMs < 0) {
            closingFromFlip = flipProgress();
            closingAtMs = System.currentTimeMillis();
            playFlipSound();
            return;
        }
        super.close();
    }

    @Override
    protected void init() {
        if (!openSoundPlayed) {
            openSoundPlayed = true;
            playFlipSound();
        }
        if (entries.size() > 1) {
            int y = height - ARROW_H - BOTTOM_MARGIN;
            addDrawableChild(new ArrowButton(width / 2 - 40 - ARROW_W / 2, y, -1));
            addDrawableChild(new ArrowButton(width / 2 + 40 - ARROW_W / 2, y, 1));
        }
        select(index);
    }

    private void cycle(int delta) {
        select(Math.floorMod(index + delta, entries.size()));
    }

    private void select(int i) {
        index = i;
        PortableCameraViewEntry entry = entries.get(index);
        CameraViewManager.setPortableView(entry.pos(), entry.lensDir(), entry.fisheye());
    }

    @Override
    public void removed() {
        CameraViewManager.clearPortableView();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (entries.size() > 1) {
            if (keyCode == GLFW.GLFW_KEY_LEFT) {
                cycle(-1);
                return true;
            }
            if (keyCode == GLFW.GLFW_KEY_RIGHT) {
                cycle(1);
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        float flip = flipProgress();

        float aspect = CameraViewManager.getCurrentAspect();
        int imgW = width;
        int imgH = Math.round(width / aspect);
        if (imgH > height) {
            imgH = height;
            imgW = Math.round(height * aspect);
        }
        int x0 = (width - imgW) / 2;
        int y0 = (height - imgH) / 2;

        boolean flipping = flip < 1f;
        MatrixStack modelView = RenderSystem.getModelViewStack();
        if (flipping) {
            Matrix4f perspective = new Matrix4f();
            perspective.m23(-1f / (height * 2.5f));
            modelView.push();
            modelView.translate(width / 2f, height, 0f);
            modelView.peek().getPositionMatrix().mul(perspective);
            modelView.multiply(RotationAxis.POSITIVE_X
                    .rotationDegrees(FLIP_START_ANGLE * (1f - flip)));
            modelView.translate(-width / 2f, -height, 0f);
            RenderSystem.applyModelViewMatrix();
        }

        context.fill(0, 0, width, height, 0xFF000000);

        PortableCameraViewEntry entry = entries.get(index);
        SimpleFramebuffer fb = CameraViewManager.getFramebuffer(entry.pos());
        boolean inRange = CameraViewManager.isCameraInClientRange(entry.pos());
        if (inRange && fb != null && fb.getColorAttachment() > 0) {
            drawFramebuffer(context, fb.getColorAttachment(), x0, y0, x0 + imgW, y0 + imgH);
            int nameTex = CameraViewManager.getNameOverlayTexture(entry.pos());
            if (nameTex > 0) {
                RenderSystem.enableBlend();
                RenderSystem.defaultBlendFunc();
                drawFramebuffer(context, nameTex, x0, y0, x0 + imgW, y0 + imgH);
                RenderSystem.disableBlend();
            }
        } else {
            context.setShaderColor(0.25f, 0.25f, 0.25f, 1f);
            context.drawTexture(DIRT_TEXTURE, x0, y0, 0, 0f, 0f, imgW, imgH, 64, 64);
            context.setShaderColor(1f, 1f, 1f, 1f);
            context.drawCenteredTextWithShadow(textRenderer,
                    Text.translatable(inRange
                            ? "screen.create-security.portable_no_signal"
                            : "screen.create-security.portable_out_of_range"),
                    width / 2, height / 2 - 4, 0xFF5555);
        }

        String label = entry.label() != null ? entry.label() : "Camera " + (index + 1);

        context.drawTextWithShadow(textRenderer, label, x0 + 8, y0 + 8, 0x555555);

        Text exitHint = Text.translatable("screen.create-security.portable_exit_hint", "ESC");
        context.drawTextWithShadow(textRenderer, exitHint,
                x0 + imgW - textRenderer.getWidth(exitHint) - 8, y0 + 8, 0x555555);
        if (entries.size() > 1) {
            context.drawCenteredTextWithShadow(textRenderer,
                    (index + 1) + " / " + entries.size(),
                    width / 2, height - BOTTOM_MARGIN - ARROW_H / 2 - 4, 0xFFFFFF);
        }

        super.render(context, mouseX, mouseY, delta);

        if (flipping) {
            context.draw();
            modelView.pop();
            RenderSystem.applyModelViewMatrix();
        }

        if (closingAtMs >= 0 && System.currentTimeMillis() - closingAtMs >= FLIP_DURATION_MS) {
            super.close();
        }
    }

    private static void drawFramebuffer(DrawContext context, int colorTex,
                                        int x0, int y0, int x1, int y1) {
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderTexture(0, colorTex);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
        BufferBuilder buf = Tessellator.getInstance().getBuffer();
        buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
        buf.vertex(mat, x0, y0, 0).texture(0f, 1f).next();
        buf.vertex(mat, x0, y1, 0).texture(0f, 0f).next();
        buf.vertex(mat, x1, y1, 0).texture(1f, 0f).next();
        buf.vertex(mat, x1, y0, 0).texture(1f, 1f).next();
        Tessellator.getInstance().draw();
        RenderSystem.enableCull();
    }

    private class ArrowButton extends ClickableWidget {
        private final int dir;

        private boolean held = false;

        ArrowButton(int x, int y, int dir) {
            super(x, y, ARROW_W, ARROW_H, Text.literal(dir > 0 ? ">" : "<"));
            this.dir = dir;
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            held = true;
            playDownSound(MinecraftClient.getInstance().getSoundManager());
            cycle(dir);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            held = false;
        }

        @Override
        protected void renderButton(DrawContext context, int mouseX, int mouseY, float delta) {

            int sprite = isHovered() ? (held ? 2 : 1) : 0;
            float uMin = sprite / 3f;
            float uMax = uMin + 1f / 3f;
            float uLeft  = dir > 0 ? uMin : uMax;
            float uRight = dir > 0 ? uMax : uMin;

            RenderSystem.setShader(GameRenderer::getPositionTexProgram);
            RenderSystem.setShaderTexture(0, ARROW_TEXTURE);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            Matrix4f mat = context.getMatrices().peek().getPositionMatrix();
            int bx0 = getX(), by0 = getY(), bx1 = bx0 + getWidth(), by1 = by0 + getHeight();
            BufferBuilder buf = Tessellator.getInstance().getBuffer();
            buf.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
            buf.vertex(mat, bx0, by0, 0).texture(uLeft, 0f).next();
            buf.vertex(mat, bx0, by1, 0).texture(uLeft, 1f).next();
            buf.vertex(mat, bx1, by1, 0).texture(uRight, 1f).next();
            buf.vertex(mat, bx1, by0, 0).texture(uRight, 0f).next();
            Tessellator.getInstance().draw();
            RenderSystem.disableBlend();
        }

        @Override
        protected void appendClickableNarrations(NarrationMessageBuilder builder) {
            appendDefaultNarrations(builder);
        }
    }
}

