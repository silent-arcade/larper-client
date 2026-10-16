package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.util.PositionScreen;
import com.example.addon.util.ThemeColors;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ModuleList extends Module implements PositionScreen.Placeable {
    public enum Corner { TopRight, TopLeft, BottomRight, BottomLeft }

    public enum Which { All, LarperOnly }

    public enum Colors { Theme, Rainbow, Custom }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Which> which = sg.add(new EnumSetting.Builder<Which>()
        .name("modules").description("All: every active module. LarperOnly: only Larper Client modules.").defaultValue(Which.All).build());

    private final Setting<Boolean> hideVisuals = sg.add(new BoolSetting.Builder()
        .name("hide-visuals").description("Don't list Larper Visual modules.").defaultValue(false).build());

    private final Setting<Corner> corner = sg.add(new EnumSetting.Builder<Corner>()
        .name("corner").description("Which corner of the screen the list sits in (Edit position sets this for you).").defaultValue(Corner.TopRight).build());

    private final Setting<Integer> offsetX = sg.add(new IntSetting.Builder()
        .name("x-offset").description("Distance from the side of the screen.").defaultValue(2).range(0, 4000).sliderRange(0, 400).build());

    private final Setting<Integer> offsetY = sg.add(new IntSetting.Builder()
        .name("y-offset").description("Distance from the top or bottom of the screen.").defaultValue(2).range(0, 4000).sliderRange(0, 400).build());

    private final Setting<Double> scale = sg.add(new DoubleSetting.Builder()
        .name("scale").description("Size of the list (you can also resize it in Edit position).").defaultValue(1).range(0.5, 3).sliderRange(0.5, 2).build());

    private final Setting<Colors> colors = sg.add(new EnumSetting.Builder<Colors>()
        .name("colors").description("Theme: your click GUI's accent colour. Rainbow: flows down the list. Custom: the colour below.").defaultValue(Colors.Theme).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Custom text colour.").defaultValue(new SettingColor(164, 91, 255))
        .visible(() -> colors.get() == Colors.Custom).build());

    private final Setting<Boolean> background = sg.add(new BoolSetting.Builder()
        .name("background").description("A bar behind each line, in your click GUI's background colour.").defaultValue(true).build());

    private final Setting<Boolean> sideBar = sg.add(new BoolSetting.Builder()
        .name("side-bar").description("A coloured bar on the screen-edge side of each line.").defaultValue(true).build());

    private final Setting<Boolean> animate = sg.add(new BoolSetting.Builder()
        .name("animate").description("Modules slide in and out when turned on or off.").defaultValue(true).build());

    private final Map<Module, Double> slide = new HashMap<>();
    private long lastFrame;

    public ModuleList() {
        super(AddonTemplate.VISUAL, "module-list", "Shows the modules you have turned on, in a list on screen. Colours follow your click GUI theme.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        var b = theme.button("Edit position");
        b.action = () -> mc.setScreen(new PositionScreen("Module List", this, mc.screen));
        return b;
    }

    @Override
    public void onDeactivate() {
        slide.clear();
    }

    private boolean listed(Module m) {
        if (m == this) return false;
        boolean larper = m.category == AddonTemplate.CATEGORY || m.category == AddonTemplate.VISUAL || m.category == AddonTemplate.AUTOMATION;
        if (which.get() == Which.LarperOnly && !larper) return false;
        return !(hideVisuals.get() && m.category == AddonTemplate.VISUAL);
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.player == null || mc.options.hideGui || mc.screen instanceof PositionScreen) return;
        drawOverlay(event.graphics);
    }

    @Override
    public int[] drawOverlay(GuiGraphicsExtractor ctx) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        double speed = animate.get() ? dt * 8 : 1;
        for (Module m : Modules.get().getAll()) {
            boolean show = m.isActive() && listed(m);
            double v = slide.getOrDefault(m, 0.0);
            v = show ? Math.min(1, v + speed) : Math.max(0, v - speed);
            if (v <= 0) slide.remove(m);
            else slide.put(m, v);
        }
        List<String> names = new ArrayList<>();
        List<Double> eases = new ArrayList<>();
        List<Module> shown = new ArrayList<>(slide.keySet());
        shown.sort((a, b) -> Integer.compare(mc.font.width(b.title), mc.font.width(a.title)));
        for (Module m : shown) {
            names.add(m.title);
            eases.add(1 - Math.pow(1 - slide.get(m), 3));
        }
        if (names.isEmpty() && mc.screen instanceof PositionScreen) {
            for (String n : new String[]{"Module List", "Example Module", "Turn things on"}) {
                names.add(n);
                eases.add(1.0);
            }
        }

        float s = scale.get().floatValue();
        int sw = (int) (mc.getWindow().getGuiScaledWidth() / s), sh = (int) (mc.getWindow().getGuiScaledHeight() / s);
        int ox = (int) (offsetX.get() / s), oy = (int) (offsetY.get() / s), lineH = 11;
        boolean right = corner.get() == Corner.TopRight || corner.get() == Corner.BottomRight;
        boolean bottom = corner.get() == Corner.BottomRight || corner.get() == Corner.BottomLeft;
        double t = System.currentTimeMillis() / 1000.0;
        int accent = ThemeColors.accent(), bg = ThemeColors.background();

        var mat = ctx.pose();
        mat.pushMatrix();
        mat.scale(s, s);
        double y = bottom ? sh - oy - lineH : oy;
        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        for (int i = 0; i < names.size(); i++) {
            String name = names.get(i);
            double ease = eases.get(i);
            int w = mc.font.width(name) + 6;
            int shift = (int) ((1 - ease) * (w + 4));
            int x = right ? sw - ox - w + shift : ox - shift;
            int col = switch (colors.get()) {
                case Rainbow -> java.awt.Color.HSBtoRGB((float) ((t * 0.25 + i * 0.06) % 1), 0.7f, 1f);
                case Custom -> color.get().getPacked();
                case Theme -> accent;
            } & 0xFFFFFF;
            int alpha = (int) (255 * ease);
            if (background.get()) ctx.fill(x, (int) y, x + w, (int) y + lineH, ThemeColors.withAlpha(bg, (int) ((bg >>> 24) * ease)));
            if (sideBar.get()) {
                int bx = right ? x + w : x - 2;
                ctx.fill(bx, (int) y, bx + 2, (int) y + lineH, alpha << 24 | col);
            }
            ctx.text(mc.font, name, x + 3, (int) y + 2, Math.max(4, alpha) << 24 | col);
            minX = Math.min(minX, x - 2);
            maxX = Math.max(maxX, x + w + 2);
            minY = Math.min(minY, (int) y);
            maxY = Math.max(maxY, (int) y + lineH);
            y += (bottom ? -lineH : lineH) * ease;
        }
        mat.popMatrix();
        if (minX == Integer.MAX_VALUE) return new int[]{0, 0, 0, 0};
        return new int[]{(int) (minX * s), (int) (minY * s), (int) ((maxX - minX) * s), (int) ((maxY - minY) * s)};
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
        offsetX.set(Math.max(0, right ? screenWidth - left - width - 2 : left + 2));
        offsetY.set(Math.max(0, bottom ? screenHeight - top - height : top));
    }
}
