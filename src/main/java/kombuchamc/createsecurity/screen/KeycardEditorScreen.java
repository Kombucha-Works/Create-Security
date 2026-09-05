package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.CreateSecurity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public class KeycardEditorScreen extends HandledScreen<KeycardEditorScreenHandler> {

    private static final Identifier TEXTURE =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/keycard_editor_gui.png");

    private static final Identifier DYE_ICON =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/slot/dye.png");

    private static final int DYE_SLOT = 2;
    private static final int ICON_SIZE = 16;

    private static final int LABEL = 0xFFB0B0BC;

    public KeycardEditorScreen(KeycardEditorScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 166;
        playerInventoryTitleY = backgroundHeight - 94;
        titleY = 4;
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);

        Slot dye = handler.getSlot(DYE_SLOT);
        if (!dye.hasStack()) {
            context.drawTexture(DYE_ICON, x + dye.x, y + dye.y, 0, 0,
                    ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
        }
    }

    @Override
    protected void drawForeground(DrawContext context, int mouseX, int mouseY) {
        context.drawText(textRenderer, title, titleX, titleY, LABEL, false);
        context.drawText(textRenderer, playerInventoryTitle, playerInventoryTitleX,
                playerInventoryTitleY, LABEL, false);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        super.render(context, mouseX, mouseY, delta);
        drawMouseoverTooltip(context, mouseX, mouseY);
    }
}
