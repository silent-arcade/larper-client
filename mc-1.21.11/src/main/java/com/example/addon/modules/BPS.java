package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class BPS extends VisualModule {
    private static final int HISTORY = 60;
    private static final int H_TEXT = 22, H_GRAPH = 16;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> x = sgGeneral.add(new IntSetting.Builder()
        .name("x").description("Horizontal position (or use Edit position).").defaultValue(4).range(0, 4000).sliderRange(0, 1000).build());

    private final Setting<Integer> y = sgGeneral.add(new IntSetting.Builder()
        .name("y").description("Vertical position (or use Edit position).").defaultValue(40).range(0, 4000).sliderRange(0, 600).build());

    private final Setting<Integer> width = sgGeneral.add(new IntSetting.Builder()
        .name("width").description("Card width (or drag the right edge in Edit position).").defaultValue(110).range(90, 600).sliderRange(90, 300).build());

    private final Setting<Boolean> graph = sgGeneral.add(new BoolSetting.Builder()
        .name("graph").description("Live graph of your speed over the last 3 seconds.").defaultValue(true).build());

    private final Setting<Boolean> speedColor = sgGeneral.add(new BoolSetting.Builder()
        .name("speed-color").description("Number goes green -> yellow -> red as you speed up.").defaultValue(true).build());

    private final Setting<Double> fastSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("red-at").description("Speed (blocks/s) that shows as full red.").defaultValue(20).range(1, 100).sliderRange(5, 60)
        .visible(speedColor::get).build());

    private final Setting<SettingColor> accent = sgGeneral.add(new ColorSetting.Builder()
        .name("accent").description("Accent bar and graph color (supports rainbow).").defaultValue(new SettingColor(164, 91, 255)).build());

    private final Setting<SettingColor> background = sgGeneral.add(new ColorSetting.Builder()
        .name("background").description("Card background.").defaultValue(new SettingColor(12, 12, 20, 170)).build());

    private final SettingGroup sgWarning = settings.createGroup("Speed Warning");

    private final Setting<Boolean> warning = sgWarning.add(new BoolSetting.Builder()
        .name("speed-warning").description("Flashes the card red with SLOW DOWN when you go too fast.").defaultValue(true).build());

    private final Setting<Double> warnAt = sgWarning.add(new DoubleSetting.Builder()
        .name("warn-at").description("Speed (blocks/s) that triggers the warning.").defaultValue(15).range(1, 100).sliderRange(5, 60)
        .visible(warning::get).build());

    private final Setting<Boolean> warnSound = sgWarning.add(new BoolSetting.Builder()
        .name("warn-sound").description("Plays a ping when you go over the limit. Only you hear it.").defaultValue(true)
        .visible(warning::get).build());

    private final Setting<String> warnText = sgWarning.add(new StringSetting.Builder()
        .name("warning-text").description("What the warning says.").defaultValue("SLOW DOWN")
        .visible(warning::get).build());

    private final Setting<SettingColor> warnColor = sgWarning.add(new ColorSetting.Builder()
        .name("warning-color").description("Warning text and flashing outline color.").defaultValue(new SettingColor(255, 43, 43))
        .visible(warning::get).build());

    private final Setting<Integer> warnDx = sgWarning.add(new IntSetting.Builder()
        .name("warning-x").description("Warning offset from the card (or drag it in Edit position).").defaultValue(30).range(-2000, 2000).sliderRange(-200, 200)
        .visible(warning::get).build());

    private final Setting<Integer> warnDy = sgWarning.add(new IntSetting.Builder()
        .name("warning-y").description("Warning offset from the card (or drag it in Edit position).").defaultValue(-12).range(-2000, 2000).sliderRange(-200, 200)
        .visible(warning::get).build());

    private boolean overLimit;

    private final double[] history = new double[HISTORY];
    private int head;
    private double bps;

    public BPS() {
        super("bps", "Shows your speed in blocks per second.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton edit = theme.button("Edit position");
        edit.action = () -> mc.setScreen(new EditScreen(mc.currentScreen));
        return edit;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        double perTick = Math.hypot(mc.player.getX() - mc.player.lastX, mc.player.getZ() - mc.player.lastZ);
        bps += (perTick * 20 - bps) * 0.3;
        history[head] = bps;
        head = (head + 1) % HISTORY;

        if (!warning.get()) overLimit = false;
        else if (!overLimit && bps > warnAt.get()) {
            overLimit = true;
            if (warnSound.get()) mc.getSoundManager().play(net.minecraft.client.sound.PositionedSoundInstance.master(
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_BIT.value(), 1.6f, 0.6f));
        } else if (overLimit && bps < warnAt.get() * 0.9) overLimit = false;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.options.hudHidden || mc.currentScreen instanceof EditScreen) return;
        draw(event.drawContext, x.get(), y.get(), overLimit);
    }

    private int height() {
        return H_TEXT + (graph.get() ? H_GRAPH : 0);
    }

    private void draw(DrawContext ctx, int x, int y, boolean showWarning) {
        int h = height(), acc = accent.get().getPacked();
        if (showWarning) {
            int a = low() ? 200 : (int) (120 + 135 * (0.5 + 0.5 * Math.sin(System.nanoTime() / 1e9 * 10)));
            int col = warnColor.get().getPacked() & 0x00FFFFFF;
            ctx.fill(x - 2, y - 2, x + width.get() + 2, y + h + 2, a << 24 | col);
            ctx.drawTextWithShadow(mc.textRenderer, warnText.get(), x + warnDx.get(), y + warnDy.get(), 0xFF000000 | col);
        }
        ctx.fill(x, y, x + width.get(), y + h, background.get().getPacked());
        ctx.fill(x, y, x + 2, y + h, acc);

        ctx.drawTextWithShadow(mc.textRenderer, "BPS", x + 6, y + 4, 0xFFA0A0B0);
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(x + 26, y + 3);
        m.scale(1.6f, 1.6f);
        ctx.drawTextWithShadow(mc.textRenderer, String.format("%.2f", bps), 0, 0, numberColor(bps));
        m.popMatrix();
        double peak = 0;
        for (double v : history) peak = Math.max(peak, v);
        String peakText = String.format("max %.1f", peak);
        ctx.drawTextWithShadow(mc.textRenderer, peakText, x + width.get() - 4 - mc.textRenderer.getWidth(peakText), y + 12, 0xFF808090);

        if (!graph.get()) return;
        int gx = x + 6, gy = y + H_TEXT, gw = this.width.get() - 10, gh = H_GRAPH - 3;
        double top = Math.max(peak, 1);
        int step = low() ? 3 : 1;
        for (int i = 0; i < HISTORY; i += step) {
            double v = history[(head + i) % HISTORY];
            int bx0 = gx + i * gw / HISTORY, bx1 = gx + Math.min(HISTORY, i + step) * gw / HISTORY;
            int bh = (int) Math.round(v / top * gh);
            if (bh <= 0) continue;
            ctx.fill(bx0, gy + gh - bh, Math.max(bx1, bx0 + 1), gy + gh, (acc & 0x00FFFFFF) | (0x50 + i * 0xAF / HISTORY) << 24);
            if (!low()) ctx.fill(bx0, gy + gh - bh, Math.max(bx1, bx0 + 1), gy + gh - bh + 1, 0xFFFFFFFF & (acc | 0xFF000000) | 0x00303030);
        }
    }

    private int numberColor(double v) {
        if (!speedColor.get()) return 0xFFFFFFFF;
        float t = (float) Math.min(1, v / fastSpeed.get());
        int r = (int) (255 * Math.min(1, t * 2)), g = (int) (255 * Math.min(1, (1 - t) * 2));
        return 0xFF000000 | r << 16 | g << 8 | 0x40;
    }

    private class EditScreen extends Screen {
        private final Screen parent;
        private boolean dragging, draggingWarning, resizing;
        private double grabX, grabY;

        EditScreen(Screen parent) {
            super(Text.literal("BPS position"));
            this.parent = parent;
        }

        private boolean overWarning(double mx, double my) {
            if (!warning.get()) return false;
            int wx = x.get() + warnDx.get(), wy = y.get() + warnDy.get();
            return mx >= wx - 2 && mx <= wx + textRenderer.getWidth(warnText.get()) + 2 && my >= wy - 2 && my <= wy + textRenderer.fontHeight + 1;
        }

        @Override
        public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
            super.render(ctx, mouseX, mouseY, delta);
            int bx = x.get(), by = y.get();
            draw(ctx, bx, by, warning.get());
            if (warning.get()) {
                int wx = bx + warnDx.get(), wy = by + warnDy.get();
                ctx.fill(wx - 2, wy - 2, wx + textRenderer.getWidth(warnText.get()) + 2, wy + textRenderer.fontHeight + 1, draggingWarning ? 0x80A45BFF : 0x40FFFFFF);
            }
            int ex = bx + BPS.this.width.get();
            ctx.fill(ex - 2, by, ex + 2, by + height(), resizing || overEdge(mouseX, mouseY) ? 0xFFFFFFFF : 0x80FFFFFF);
            ctx.drawCenteredTextWithShadow(textRenderer, "Drag the BPS card, its warning, or its right edge to resize. Press Esc when done.", width / 2, height / 2, 0xFFFFFFFF);
        }

        private boolean overEdge(double mx, double my) {
            int ex = x.get() + BPS.this.width.get();
            return mx >= ex - 3 && mx <= ex + 3 && my >= y.get() && my <= y.get() + height();
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            if (overEdge(click.x(), click.y())) {
                resizing = true;
                return true;
            }
            if (overWarning(click.x(), click.y())) {
                draggingWarning = true;
                grabX = click.x() - (x.get() + warnDx.get());
                grabY = click.y() - (y.get() + warnDy.get());
                return true;
            }
            if (click.x() >= x.get() && click.x() <= x.get() + BPS.this.width.get() && click.y() >= y.get() && click.y() <= y.get() + height()) {
                dragging = true;
                grabX = click.x() - x.get();
                grabY = click.y() - y.get();
                return true;
            }
            return super.mouseClicked(click, doubled);
        }

        @Override
        public boolean mouseDragged(Click click, double dx, double dy) {
            if (draggingWarning) {
                warnDx.set((int) (click.x() - grabX) - x.get());
                warnDy.set((int) (click.y() - grabY) - y.get());
                return true;
            }
            if (resizing) {
                BPS.this.width.set((int) Math.max(90, Math.min(600, click.x() - x.get())));
                return true;
            }
            if (!dragging) return super.mouseDragged(click, dx, dy);
            x.set((int) Math.max(0, Math.min(width - BPS.this.width.get(), click.x() - grabX)));
            y.set((int) Math.max(0, Math.min(height - height(), click.y() - grabY)));
            return true;
        }

        @Override
        public boolean mouseReleased(Click click) {
            dragging = false;
            draggingWarning = false;
            resizing = false;
            return super.mouseReleased(click);
        }

        @Override
        public void close() {
            mc.setScreen(parent);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
