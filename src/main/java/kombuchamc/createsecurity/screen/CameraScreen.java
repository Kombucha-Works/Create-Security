package kombuchamc.createsecurity.screen;

import com.mojang.blaze3d.systems.RenderSystem;
import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.network.ModClientPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class CameraScreen extends HandledScreen<CameraScreenHandler> {

    private static final Identifier BUTTON_TEXTURE =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/camera_button.png");
    private static final int BUTTON_WIDTH = 18;
    private static final int BUTTON_HEIGHT = 18;

    private static final Identifier TEXTURE =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/camera_gui.png");

    private boolean detectionEnabled = false;

    public CameraScreen(CameraScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        this.backgroundHeight = 166;
        this.backgroundWidth = 176;
    }
    private int getButtonX() { return x + 151; }
    private int getButtonY() { return y + 51; }

    private boolean isHoveringButton(double mouseX, double mouseY) {
        return mouseX >= getButtonX() && mouseX <= getButtonX() + BUTTON_WIDTH
                && mouseY >= getButtonY() && mouseY <= getButtonY() + BUTTON_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isHoveringButton(mouseX, mouseY)) {
            detectionEnabled = !detectionEnabled;
            ModClientPackets.sendToggleDetectionPacket(
                    handler.cameraPos, detectionEnabled);
            this.client.getSoundManager().play(
                    net.minecraft.client.sound.PositionedSoundInstance.master(
                            net.minecraft.sound.SoundEvents.UI_BUTTON_CLICK.value(), 1.0f
                    )
            );
            return true;
        }

        return super.mouseClicked(mouseX, mouseY, button);

    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        titleY = 23;
        if (handler.blockEntity != null) {

            detectionEnabled = handler.blockEntity.isDetectionEnabled();
        } else {

            detectionEnabled = handler.initialDetectionEnabled;
        }
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        int x = (width - backgroundWidth) / 2;
        int y = (height - backgroundHeight) / 2 + 17;

        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);
        int uOffset;
        if (isHoveringButton(mouseX, mouseY)) {
            uOffset = detectionEnabled ? BUTTON_WIDTH * 2 : BUTTON_WIDTH * 3;
        } else {
            uOffset = detectionEnabled ? 0 : BUTTON_WIDTH;
        }
        context.drawTexture(BUTTON_TEXTURE, getButtonX(), getButtonY(),
                uOffset, 0, BUTTON_WIDTH, BUTTON_HEIGHT, BUTTON_WIDTH * 4, BUTTON_HEIGHT);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {

        renderBackground(context);
        drawBackground(context, delta, mouseX, mouseY);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);

        if (isHoveringButton(mouseX, mouseY)) {
            context.drawTooltip(textRenderer,
                    Text.literal(detectionEnabled ? "Player Detection: ON" : "Player Detection: OFF"),
                    mouseX, mouseY);
        }
    }
}

