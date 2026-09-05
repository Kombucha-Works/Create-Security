package kombuchamc.createsecurity.screen;

import kombuchamc.createsecurity.CreateSecurity;
import kombuchamc.createsecurity.block.HealthFilter;
import kombuchamc.createsecurity.block.MobTarget;
import kombuchamc.createsecurity.block.entity.TurretBlockEntity;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.screen.slot.Slot;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

@Environment(EnvType.CLIENT)
public class TurretScreen extends HandledScreen<TurretScreenHandler> {

    private static final Identifier TEXTURE =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/turret_gui.png");
    private static final Identifier BUTTONS =
            new Identifier(CreateSecurity.MOD_ID, "textures/gui/turret_button.png");

    private static final int BUTTON_SIZE = 18;
    private static final int CONFIRM_TICKS = 100;
    private static final int SHEET_FRAMES = 16;
    private static final int PLAYERS_X = 44;
    private static final int MOBS_X = 80;
    private static final int HEALTH_X = 116;
    private static final int BUTTON_Y = 44;

    private static final int SLIDER_X = 8;
    private static final int SLIDER_W = 160;
    private static final int SLIDER_H = 20;
    private static final int EXTEND_Y = 66;
    private static final int SHOOT_Y = 88;
    private static final int HEALTH_Y = 110;

    private static final int LABEL = 0xFF404040;

    private static final int ICON_SIZE = 16;
    private static final long HINT_INTERVAL_MS = 1000L;

    private static final Identifier[] AMMO_HINTS = hints("5.56", "7.62x51", "12_gauge", "rocket");
    private static final Identifier[] BARREL_HINTS =
            hints("shotgun_barrel", "minigun_barrel", "automatic_barrel", "rocket_barrel");

    private static Identifier[] hints(String... names) {
        Identifier[] out = new Identifier[names.length];
        for (int i = 0; i < names.length; i++) {
            out[i] = new Identifier(CreateSecurity.MOD_ID, "textures/gui/slot/" + names[i] + ".png");
        }
        return out;
    }

    private RangeSlider extendSlider;
    private RangeSlider shootSlider;
    private RangeSlider healthSlider;

    public TurretScreen(TurretScreenHandler handler, PlayerInventory inventory, Text title) {
        super(handler, inventory, title);
        backgroundWidth = 176;
        backgroundHeight = 226;
        playerInventoryTitleY = backgroundHeight - 94;
    }

    @Override
    protected void init() {
        super.init();
        titleX = (backgroundWidth - textRenderer.getWidth(title)) / 2;
        titleY = 6;

        extendSlider = addDrawableChild(new RangeSlider(x + SLIDER_X, y + EXTEND_Y,
                TurretBlockEntity.MIN_RANGE, TurretBlockEntity.MAX_RANGE, handler.extendRange(),
                TurretScreenHandler.SLIDER_EXTEND, "gui.create-security.turret.extend"));
        shootSlider = addDrawableChild(new RangeSlider(x + SLIDER_X, y + SHOOT_Y,
                TurretBlockEntity.MIN_RANGE, TurretBlockEntity.MAX_RANGE, handler.shootRange(),
                TurretScreenHandler.SLIDER_SHOOT, "gui.create-security.turret.shoot"));
        healthSlider = addDrawableChild(new RangeSlider(x + SLIDER_X, y + HEALTH_Y,
                TurretBlockEntity.MIN_HEALTH, TurretBlockEntity.MAX_HEALTH, handler.healthThreshold(),
                TurretScreenHandler.SLIDER_HEALTH, "gui.create-security.turret.health"));
        updateHealthSlider();
    }

    private void updateHealthSlider() {
        healthSlider.active = HealthFilter.byId(handler.healthFilterId()) != HealthFilter.OFF;
    }

    @Override
    protected void handledScreenTick() {
        super.handledScreenTick();
        extendSlider.syncFrom(handler.extendRange());
        shootSlider.syncFrom(handler.shootRange());
        healthSlider.syncFrom(handler.healthThreshold());
        updateHealthSlider();
    }

    private boolean isOverButton(double mouseX, double mouseY, int buttonX) {
        return mouseX >= x + buttonX && mouseX < x + buttonX + BUTTON_SIZE
                && mouseY >= y + BUTTON_Y && mouseY < y + BUTTON_Y + BUTTON_SIZE;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (isOverButton(mouseX, mouseY, PLAYERS_X)) {
                send(TurretScreenHandler.BUTTON_PLAYERS);
                return true;
            }
            if (isOverButton(mouseX, mouseY, MOBS_X)) {
                send(TurretScreenHandler.BUTTON_MOBS);
                return true;
            }
            if (isOverButton(mouseX, mouseY, HEALTH_X)) {
                send(TurretScreenHandler.BUTTON_HEALTH);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (button == 0 && isDragging() && getFocused() instanceof SliderWidget slider) {
            return slider.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0 && isDragging() && getFocused() instanceof SliderWidget slider) {
            setDragging(false);
            slider.mouseReleased(mouseX, mouseY, button);
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private void sendSlider(int slider, int value) {
        if (client == null) return;
        kombuchamc.createsecurity.network.ModPackets.sendTurretSlider(handler.syncId, slider, value);
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }

    private void send(int id) {
        if (client == null || client.interactionManager == null) return;
        client.interactionManager.clickButton(handler.syncId, id);
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
    }

    @Override
    protected void drawBackground(DrawContext context, float delta, int mouseX, int mouseY) {
        context.drawTexture(TEXTURE, x, y, 0, 0, backgroundWidth, backgroundHeight);

        drawButton(context, PLAYERS_X, handler.targetsPlayers() ? 1 : 0,
                isOverButton(mouseX, mouseY, PLAYERS_X), 0, 2);
        drawButton(context, MOBS_X, handler.mobTargetId(),
                isOverButton(mouseX, mouseY, MOBS_X), 4, 3);
        drawButton(context, HEALTH_X, handler.healthFilterId(),
                isOverButton(mouseX, mouseY, HEALTH_X), 10, 3);

        drawSlotHint(context, TurretBlockEntity.SLOT_AMMO, AMMO_HINTS);
        drawSlotHint(context, TurretBlockEntity.SLOT_BARREL, BARREL_HINTS);
    }

    private void drawSlotHint(DrawContext context, int index, Identifier[] frames) {
        Slot slot = handler.getSlot(index);
        if (slot.hasStack()) return;
        int frame = (int) (Util.getMeasuringTimeMs() / HINT_INTERVAL_MS % frames.length);
        context.drawTexture(frames[frame], x + slot.x, y + slot.y, 0, 0,
                ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);
    }

    private void drawButton(DrawContext context, int buttonX, int state, boolean hovered,
                            int base, int stateCount) {
        int frame = base + state + (hovered ? stateCount : 0);
        context.drawTexture(BUTTONS, x + buttonX, y + BUTTON_Y, frame * BUTTON_SIZE, 0,
                BUTTON_SIZE, BUTTON_SIZE, BUTTON_SIZE * SHEET_FRAMES, BUTTON_SIZE);
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

        if (isOverButton(mouseX, mouseY, PLAYERS_X)) {
            context.drawTooltip(textRenderer, Text.translatable("gui.create-security.turret.players",
                    Text.translatable(handler.targetsPlayers()
                            ? "gui.create-security.on" : "gui.create-security.off")), mouseX, mouseY);
        } else if (isOverButton(mouseX, mouseY, MOBS_X)) {
            context.drawTooltip(textRenderer, Text.translatable("gui.create-security.turret.mobs",
                    Text.translatable(MobTarget.byId(handler.mobTargetId()).translationKey())),
                    mouseX, mouseY);
        } else if (isOverButton(mouseX, mouseY, HEALTH_X)) {
            context.drawTooltip(textRenderer, Text.translatable("gui.create-security.turret.filter",
                    Text.translatable(HealthFilter.byId(handler.healthFilterId()).translationKey())),
                    mouseX, mouseY);
        }
    }

    @Environment(EnvType.CLIENT)
    private class RangeSlider extends SliderWidget {

        private final int min;
        private final int max;
        private final int slider;
        private final String key;
        private boolean held;
        private int pending;
        private int pendingTicks;

        RangeSlider(int x, int y, int min, int max, int start, int slider, String key) {
            super(x, y, SLIDER_W, SLIDER_H, Text.empty(), toProgress(start, min, max));
            this.min = min;
            this.max = max;
            this.slider = slider;
            this.key = key;
            updateMessage();
        }

        private static double toProgress(int value, int min, int max) {
            return (MathHelper.clamp(value, min, max) - min) / (double) (max - min);
        }

        int intValue() {
            return min + (int) Math.round(value * (max - min));
        }

        void syncFrom(int synced) {
            if (held) return;
            if (pendingTicks > 0) {
                if (synced != pending) {
                    pendingTicks--;
                    return;
                }
                pendingTicks = 0;
            }
            if (intValue() == synced) return;
            value = toProgress(synced, min, max);
            updateMessage();
        }

        private void submit() {
            pending = intValue();
            pendingTicks = CONFIRM_TICKS;
            sendSlider(slider, pending);
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.translatable(key, intValue()));
        }

        @Override
        protected void applyValue() {
            updateMessage();
        }

        @Override
        public void onClick(double mouseX, double mouseY) {
            held = true;
            super.onClick(mouseX, mouseY);
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            held = false;
            submit();
        }

        @Override
        public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
            if (!super.keyPressed(keyCode, scanCode, modifiers)) return false;
            submit();
            return true;
        }
    }
}
