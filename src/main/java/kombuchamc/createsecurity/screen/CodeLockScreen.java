package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.network.ModClientPackets;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

@Environment(EnvType.CLIENT)
public class CodeLockScreen extends Screen {

    private static final int DIGITS = 4;

    private static final int KEY = 25;
    private static final int GAP = 4;
    private static final int PAD = 8;
    private static final int TITLE_H = 10;
    private static final int DISPLAY_H = 16;

    private static final int KEYPAD_W = 3 * KEY + 2 * GAP;
    private static final int KEYPAD_H = 4 * KEY + 3 * GAP;

    private static final int PANEL_W = 110;

    private static final int PANEL_H = PAD + TITLE_H + DISPLAY_H + KEYPAD_H + PAD + 11;

    private static final int PANEL_BG = 0xF0202024;
    private static final int PANEL_EDGE = 0xFF3C3C46;
    private static final int DISPLAY_BG = 0xFF101014;
    private static final int DISPLAY_TEXT = 0xFF54F07A;
    private static final int LABEL = 0xFFB0B0BC;

    private final BlockPos pos;
    private final boolean setting;
    private final StringBuilder entered = new StringBuilder();

    private int panelX;
    private int panelY;
    private int keypadX;
    private int keypadTop;
    private int displayY;

    public CodeLockScreen(BlockPos pos, boolean setting) {
        super(Text.translatable(setting
                ? "screen.create-security.code_lock_set"
                : "screen.create-security.code_lock_enter"));
        this.pos = pos;
        this.setting = setting;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;

        int keypadTop = panelY + 40;
        for (int i = 0; i < 9; i++) {
            int digit = i + 1;
            int col = i % 3;
            int row = i / 3;
            addKey(String.valueOf(digit), panelX + (PANEL_W - (KEY * 3 + GAP * 2)) / 2 + col * (KEY + GAP),
                    keypadTop + row * (KEY + GAP), b -> press(digit));
        }
        int bottom = keypadTop + 3 * (KEY + GAP);
        addKey("<", panelX + (PANEL_W - (KEY * 3 + GAP * 2)) / 2, bottom, b -> backspace());
        addKey("0", panelX + (PANEL_W - (KEY * 3 + GAP * 2)) / 2 + (KEY + GAP), bottom, b -> press(0));
        addKey("\u2714", panelX + (PANEL_W - (KEY * 3 + GAP * 2)) / 2 + 2 * (KEY + GAP), bottom, b -> submit());
    }

    private void addKey(String label, int x, int y, ButtonWidget.PressAction action) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), action)
                .dimensions(x, y, KEY, KEY).build());
    }

    private void press(int digit) {
        if (entered.length() >= DIGITS) return;
        entered.append(digit);
        if (entered.length() == DIGITS) submit();
    }

    private void backspace() {
        if (entered.length() > 0) entered.deleteCharAt(entered.length() - 1);
    }

    private void submit() {
        if (entered.length() != DIGITS) return;
        int code = Integer.parseInt(entered.toString());
        if (code >= 1000) ModClientPackets.sendCodeLockSubmit(pos, code);
        close();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode >= GLFW.GLFW_KEY_0 && keyCode <= GLFW.GLFW_KEY_9) {
            press(keyCode - GLFW.GLFW_KEY_0);
            return true;
        }
        if (keyCode >= GLFW.GLFW_KEY_KP_0 && keyCode <= GLFW.GLFW_KEY_KP_9) {
            press(keyCode - GLFW.GLFW_KEY_KP_0);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            backspace();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            submit();
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        renderBackground(context);

        context.fill(panelX - 1, panelY - 1, panelX + PANEL_W + 1, panelY + PANEL_H + 1, PANEL_EDGE);
        context.fill(panelX, panelY, panelX + PANEL_W, panelY + PANEL_H, PANEL_BG);

        context.drawCenteredTextWithShadow(textRenderer, title, panelX + PANEL_W / 2,
                panelY + PAD, LABEL);

        context.fill(panelX + PANEL_W / 2 - KEYPAD_W / 2 - 1, panelY + 20, panelX + PANEL_W / 2 - KEYPAD_W / 2 + KEYPAD_W - 1, panelY + 20 + DISPLAY_H, DISPLAY_BG);

        context.drawCenteredTextWithShadow(textRenderer, Text.literal(display()),
                panelX + PANEL_W / 2, panelY + 24, DISPLAY_TEXT);

        super.render(context, mouseX, mouseY, delta);
    }

    private String display() {
        StringBuilder shown = new StringBuilder();
        for (int i = 0; i < DIGITS; i++) {
            shown.append(i < entered.length() ? "\u25CF" : "\u25CB");
            if (i < DIGITS - 1) shown.append("  ");
        }
        return shown.toString();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
