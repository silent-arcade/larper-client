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
import net.minecraft.client.gui.DrawContext;

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

    private record Line(String text, boolean on) {}

    public Keybinds() {
        super(AddonTemplate.VISUAL, "keybinds", "Shows your keybinds on screen; green means that module is on. Colours follow your click GUI theme.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        var b = theme.button("Edit position");
        b.action = () -> mc.setScreen(new PositionScreen("Keybinds", this, mc.currentScreen));
        return b;
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.player == null || mc.options.hudHidden || mc.currentScreen instanceof PositionScreen) return;
        drawOverlay(event.drawContext);
    }

    private List<Line> lines() {
        List<Line> lines = new ArrayList<>();
        for (Module m : Modules.get().getAll()) {
            boolean on = m.isActive();
            if (!on && !showOff.get()) continue;
            if (m.keybind.isSet()) lines.add(new Line(m.title + " [" + m.keybind + "]", on));
            if (!settingKeys.get()) continue;
            for (var group : m.settings) for (Setting<?> s : group) {
                if (s instanceof KeybindSetting && s.get() instanceof Keybind k && k.isSet() && !k.equals(s.getDefaultValue())) lines.add(new Line(m.title + ": " + s.title + " [" + k + "]", on));
            }
        }
        if (lines.isEmpty()) lines.add(new Line("No keybinds set", false));
        lines.sort(Comparator.comparing((Line l) -> !l.on()).thenComparing(Line::text, String.CASE_INSENSITIVE_ORDER));
        return lines;
    }

    @Override
    public int[] drawOverlay(DrawContext ctx) {
        List<Line> lines = lines();
        float s = scale.get().floatValue();
        int w = mc.textRenderer.getWidth("Keybinds") + 6;
        for (Line l : lines) w = Math.max(w, mc.textRenderer.getWidth(l.text()) + 6);
        int h = (lines.size() + 1) * 10 + 6;
        int sw = (int) (mc.getWindow().getScaledWidth() / s), sh = (int) (mc.getWindow().getScaledHeight() / s);
        int ox = (int) (offsetX.get() / s), oy = (int) (offsetY.get() / s);
        int x = switch (corner.get()) { case TopLeft, BottomLeft -> ox; default -> sw - ox - w; };
        int y = switch (corner.get()) { case TopLeft, TopRight -> oy; default -> sh - oy - h; };

        int accent = ThemeColors.accent();
        var m = ctx.getMatrices();
        m.pushMatrix();
        m.scale(s, s);
        if (background.get()) ctx.fill(x, y, x + w, y + h, ThemeColors.background());
        ctx.fill(x, y, x + w, y + 12, ThemeColors.withAlpha(accent, 200));
        ctx.drawTextWithShadow(mc.textRenderer, "Keybinds", x + 3, y + 2, readableOn(accent));
        for (int i = 0; i < lines.size(); i++) {
            Line l = lines.get(i);
            ctx.drawTextWithShadow(mc.textRenderer, l.text(), x + 3, y + 5 + (i + 1) * 10, l.on() ? 0xFF55FF55 : ThemeColors.mix(ThemeColors.text(), ThemeColors.background(), 0.45));
        }
        m.popMatrix();
        return new int[]{(int) (x * s), (int) (y * s), (int) (w * s), (int) (h * s)};
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
