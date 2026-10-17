package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.util.PositionScreen;
import com.example.addon.util.ThemeColors;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class Keybinds extends Module implements PositionScreen.Placeable {
    public enum Corner { TopLeft, TopRight, BottomLeft, BottomRight }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Corner> corner = sg.add(new EnumSetting.Builder<Corner>()
        .name("corner").description("Which corner of the screen the list sits in (Edit position sets this for you).").defaultValue(Corner.TopLeft).build());

    private final Setting<Integer> offsetX = sg.add(new IntSetting.Builder()
        .name("x-offset").description("Distance from the side of the screen.").defaultValue(4).range(0, 4000).sliderRange(0, 400).build());

    private final Setting<Integer> offsetY = sg.add(new IntSetting.Builder()
        .name("y-offset").description("Distance from the top or bottom of the screen.").defaultValue(40).range(0, 4000).sliderRange(0, 400).build());

    private final Setting<Double> scale = sg.add(new DoubleSetting.Builder()
        .name("scale").description("Size of the list (you can also resize it in Edit position).").defaultValue(1).range(0.5, 3).sliderRange(0.5, 2).build());

    private final Setting<Boolean> showOff = sg.add(new BoolSetting.Builder()
        .name("show-off-modules").description("Also list modules that are off. Turn off to only see what's on.").defaultValue(true).build());

    private final Setting<Boolean> settingKeys = sg.add(new BoolSetting.Builder()
        .name("setting-keys").description("Also list keys you've set inside modules (like Pearl Catch's or XP Macro's key). Keys left at their default aren't shown.").defaultValue(true).build());

    private final Setting<Boolean> background = sg.add(new BoolSetting.Builder()
        .name("background").description("Box behind the list, in your click GUI's background colour.").defaultValue(true).build());

    private record Line(String id, String name, String key, boolean on, boolean pressed) {}

    private final java.util.Map<String, Float> glow = new java.util.HashMap<>();
    private long lastFrame;

    public Keybinds() {
        super(AddonTemplate.VISUAL, "keybinds", "Shows your keybinds on screen; green means that module is on. Colours follow your click GUI theme.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        var b = theme.button("Edit position");
        b.action = () -> mc.setScreen(new PositionScreen("Keybinds", this, mc.screen));
        return b;
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.player == null || mc.options.hideGui || mc.screen instanceof PositionScreen) return;
        drawOverlay(event.graphics);
    }

    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        for (Module m : Modules.get().getAll()) {
            boolean on = m.isActive();
            if (!on && !showOff.get()) continue;
            if (m.keybind.isSet()) lines.add(new Line(m.name, m.title, m.keybind.toString(), on, m.keybind.isPressed()));
            if (!settingKeys.get()) continue;
            for (var group : m.settings) for (Setting<?> st : group) {
                if (st instanceof KeybindSetting && st.get() instanceof Keybind k && k.isSet() && !k.equals(st.getDefaultValue()))
                    lines.add(new Line(m.name + "/" + st.name, m.title + " · " + st.title, k.toString(), on, k.isPressed()));
            }
        }
        lines.sort(Comparator.comparing((Line l) -> !l.on()).thenComparing(Line::name, String.CASE_INSENSITIVE_ORDER));
        return lines;
    }

    private static int lerp(int a, int b, float t) {
        int r = 0;
        for (int sh = 0; sh <= 24; sh += 8) r |= Math.round((a >>> sh & 255) * (1 - t) + (b >>> sh & 255) * t) << sh;
        return r;
    }

    private static void round(GuiGraphicsExtractor ctx, int x1, int y1, int x2, int y2, int color) {
        ctx.fill(x1 + 1, y1, x2 - 1, y2, color);
        ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, color);
        ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, color);
    }

    @Override
    public int[] drawOverlay(GuiGraphicsExtractor ctx) {
        List<Line> lines = lines();
        long now = System.nanoTime();
        float dt = lastFrame == 0 ? 0 : Math.min(0.1f, (now - lastFrame) / 1e9f);
        lastFrame = now;
        for (Line l : lines) {
            float g = glow.getOrDefault(l.id(), l.on() ? 1f : 0f);
            glow.put(l.id(), l.on() ? Math.min(1, g + dt * 5) : Math.max(0, g - dt * 5));
        }

        var tr = mc.font;
        int on = (int) lines.stream().filter(Line::on).count();
        String title = "KEYBINDS", count = lines.isEmpty() ? "none set" : on + "/" + lines.size() + " ON";
        int nameW = tr.width(title) + tr.width(count) + 14, keyW = 0;
        for (Line l : lines) {
            nameW = Math.max(nameW, tr.width(l.name()) + 16);
            keyW = Math.max(keyW, tr.width(l.key()) + 8);
        }
        int rowH = 14, head = 16, pad = 4;
        int w = nameW + keyW + pad * 2 + 4, h = head + Math.max(1, lines.size()) * rowH + pad;

        float sc = scale.get().floatValue();
        int sw = (int) (mc.getWindow().getGuiScaledWidth() / sc), sh = (int) (mc.getWindow().getGuiScaledHeight() / sc);
        int ox = (int) (offsetX.get() / sc), oy = (int) (offsetY.get() / sc);
        int x = switch (corner.get()) { case TopLeft, BottomLeft -> ox; default -> sw - ox - w; };
        int y = switch (corner.get()) { case TopLeft, TopRight -> oy; default -> sh - oy - h; };

        int accent = ThemeColors.accent() | 0xFF000000, bg = ThemeColors.background(), text = ThemeColors.text() | 0xFF000000;
        int dim = ThemeColors.mix(text, bg, 0.5), green = 0xFF4CFF7A;
        double t = System.currentTimeMillis() / 1000.0;

        var m = ctx.pose();
        m.pushMatrix();
        m.scale(sc, sc);
        if (background.get()) {
            round(ctx, x + 2, y + 2, x + w + 2, y + h + 2, 0x50000000);
            round(ctx, x, y, x + w, y + h, bg);
        }
        int strips = 24;
        for (int i = 0; i < strips; i++) {
            int x1 = x + w * i / strips, x2 = x + w * (i + 1) / strips;
            float f = (float) i / (strips - 1);
            int c = lerp(accent, ThemeColors.withAlpha(accent, 40), f);
            ctx.fill(i == 0 ? x1 + 1 : x1, y, i == strips - 1 ? x2 - 1 : x2, y + head, c);
            if (i == 0) ctx.fill(x1, y + 1, x1 + 1, y + head, c);
        }
        int shine = (int) ((t * 40) % (w + 40)) - 20;
        if (shine > 0 && shine < w - 2) ctx.fill(x + shine, y + 1, x + Math.min(w - 1, shine + 6), y + head - 1, 0x22FFFFFF);
        ctx.text(tr, title, x + pad + 2, y + 4, readableOn(accent));
        ctx.text(tr, count, x + w - pad - 2 - tr.width(count), y + 4, ThemeColors.withAlpha(readableOn(accent), 200));

        if (lines.isEmpty()) ctx.text(tr, "Bind a key to a module", x + pad + 2, y + head + 3, dim);
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            float g = glow.getOrDefault(l.id(), 0f);
            int ry = y + head + i * rowH + 1;
            if (g > 0) {
                ctx.fill(x + 2, ry, x + w - 2, ry + rowH - 1, ThemeColors.withAlpha(green, (int) (34 * g)));
                ctx.fill(x + 2, ry, x + 4, ry + rowH - 1, ThemeColors.withAlpha(green, (int) (255 * g)));
            }
            int dotX = x + pad + 3, dotY = ry + rowH / 2 - 2;
            if (g > 0) {
                float pulse = 0.6f + 0.4f * (float) Math.sin(t * 4 + i);
                ctx.fill(dotX - 2, dotY - 2, dotX + 6, dotY + 6, ThemeColors.withAlpha(green, (int) (45 * g * pulse)));
                ctx.fill(dotX - 1, dotY - 1, dotX + 5, dotY + 5, ThemeColors.withAlpha(green, (int) (90 * g * pulse)));
            }
            ctx.fill(dotX, dotY, dotX + 4, dotY + 4, lerp(ThemeColors.withAlpha(dim, 200), green, g));
            ctx.text(tr, l.name(), x + pad + 12, ry + 3, lerp(dim, 0xFFFFFFFF, g));

            int kw = tr.width(l.key()) + 8, kx = x + w - pad - kw, ky = ry + 1, kh = rowH - 3;
            int capBg = l.pressed() ? accent : lerp(ThemeColors.mix(bg, 0xFF000000, 0.35), ThemeColors.mix(accent, bg, 0.75), g);
            round(ctx, kx, ky + 1, kx + kw, ky + kh + 1, ThemeColors.withAlpha(0xFF000000, 120));
            round(ctx, kx, ky, kx + kw, ky + kh, ThemeColors.withAlpha(l.pressed() ? accent : lerp(dim, accent, g), 255));
            round(ctx, kx + 1, ky + 1, kx + kw - 1, ky + kh - 1, capBg);
            ctx.text(tr, l.key(), kx + 4, ky + 2, l.pressed() ? readableOn(accent) : lerp(dim, text, Math.max(g, 0.4f)), false);
        }
        m.popMatrix();
        return new int[]{(int) (x * sc), (int) (y * sc), (int) ((w + 2) * sc), (int) ((h + 2) * sc)};
    }

    static int readableOn(int bg) {
        double lum = (bg >> 16 & 255) * 0.299 + (bg >> 8 & 255) * 0.587 + (bg & 255) * 0.114;
        return lum > 150 ? 0xFF121216 : 0xFFFFFFFF;
    }

    @Override
    public double getScale() {
        return scale.get();
    }

    @Override
    public void setScale(double value) {
        scale.set(value);
    }

    @Override
    public void placeAt(int left, int top, int width, int height, int screenWidth, int screenHeight) {
        boolean right = left + width / 2 > screenWidth / 2, bottom = top + height / 2 > screenHeight / 2;
        corner.set(bottom ? (right ? Corner.BottomRight : Corner.BottomLeft) : (right ? Corner.TopRight : Corner.TopLeft));
        offsetX.set(right ? screenWidth - left - width : left);
        offsetY.set(bottom ? screenHeight - top - height : top);
    }
}
