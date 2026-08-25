package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.items.PlayerListItem;
import kombuchamc.createsecurity.network.ModClientPackets;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.tooltip.Tooltip;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Hand;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class PlayerListScreen extends Screen {

    private static final Identifier TEXTURE =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/player_list_gui.png");

    private static final int BG_W = 220;
    private static final int BG_H = 180;
    private static final int LIST_TOP = 46;
    private static final int ROW_H = 16;
    private static final int ROWS_PER_COLUMN = 6;
    private static final int COLUMN_W = 100;

    private final Hand hand;
    private final List<String> names;
    private boolean includeMode;

    private int panelX;
    private int panelY;
    private TextFieldWidget nameField;

    public PlayerListScreen(Hand hand, List<String> names, boolean includeMode) {
        super(Text.translatable("screen.create-security.player_list"));
        this.hand = hand;
        this.names = new ArrayList<>(names);
        this.includeMode = includeMode;
    }

    @Override
    protected void init() {
        panelX = (width - BG_W) / 2;
        panelY = (height - BG_H) / 2;

        String pendingInput = nameField == null ? "" : nameField.getText();
        nameField = new TextFieldWidget(textRenderer, panelX + 12, panelY + 22, 128, 16,
                Text.translatable("screen.create-security.player_list_name_hint"));
        nameField.setMaxLength(16);
        nameField.setText(pendingInput);
        addDrawableChild(nameField);

        addDrawableChild(ButtonWidget.builder(
                        Text.translatable("screen.create-security.player_list_add"), b -> addName())
                .dimensions(panelX + 146, panelY + 20, 60, 20).build());

        for (int i = 0; i < names.size(); i++) {
            final int index = i;
            int col = i / ROWS_PER_COLUMN;
            int row = i % ROWS_PER_COLUMN;
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> removeName(index))
                    .dimensions(panelX + 12 + col * COLUMN_W + 82, panelY + LIST_TOP + row * ROW_H, 14, 14)
                    .build());
        }

        addDrawableChild(ButtonWidget.builder(modeLabel(), b -> {
            toggleMode();
            b.setMessage(modeLabel());
            b.setTooltip(Tooltip.of(modeDescription()));
        }).dimensions(panelX + 12, panelY + 152, 60, 20).tooltip(Tooltip.of(modeDescription())).build());

        addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, b -> close())
                .dimensions(panelX + 146, panelY + 152, 60, 20).build());
    }

    private void addName() {
        String name = nameField.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{1,16}") || names.size() >= PlayerListItem.MAX_PLAYERS) return;
        for (String existing : names) {
            if (existing.equalsIgnoreCase(name)) return;
        }
        names.add(name);
        nameField.setText("");
        sync();
        clearAndInit();
    }

    private void removeName(int index) {
        if (index < 0 || index >= names.size()) return;
        names.remove(index);
        sync();
        clearAndInit();
    }

    private void toggleMode() {
        includeMode = !includeMode;
        sync();
    }

    private Text modeLabel() {
        return Text.translatable(includeMode
                ? "screen.create-security.player_list_include"
                : "screen.create-security.player_list_exclude");
    }

    private Text modeDescription() {
        return Text.translatable(includeMode
                ? "screen.create-security.player_list_mode_include_desc"
                : "screen.create-security.player_list_mode_exclude_desc");
    }

    private void sync() {
        ModClientPackets.sendPlayerListUpdate(hand, includeMode, names);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER)
                && nameField.isFocused()) {
            addName();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);
        context.drawTexture(TEXTURE, panelX, panelY, 0, 0, BG_W, BG_H);

        context.drawText(textRenderer, title,
                panelX + (BG_W - textRenderer.getWidth(title)) / 2, panelY + 8, 0x404040, false);

        for (int i = 0; i < names.size(); i++) {
            int col = i / ROWS_PER_COLUMN;
            int row = i % ROWS_PER_COLUMN;
            String shown = textRenderer.trimToWidth(names.get(i), 78);
            context.drawText(textRenderer, shown,
                    panelX + 12 + col * COLUMN_W,
                    panelY + LIST_TOP + row * ROW_H + 3, 0x404040, false);
        }

        String counter = names.size() + "/" + PlayerListItem.MAX_PLAYERS;
        context.drawText(textRenderer, counter,
                panelX + BG_W / 2 - textRenderer.getWidth(counter) / 2, panelY + 158, 0x404040, false);

        super.render(context, mouseX, mouseY, delta);
    }
}

