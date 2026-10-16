package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

public class Compass extends VisualModule {
    private static final String[] LABELS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgColors = settings.createGroup("Colors");

    private final Setting<Integer> offsetX = sgGeneral.add(new IntSetting.Builder()
        .name("x").description("Horizontal position (0 = screen center).").defaultValue(0).sliderRange(-1000, 1000).build());

    private final Setting<Integer> offsetY = sgGeneral.add(new IntSetting.Builder()
        .name("y").description("Vertical position from the top of the screen.").defaultValue(6).sliderRange(0, 1000).build());

    private final Setting<Integer> width = sgGeneral.add(new IntSetting.Builder()
        .name("width").description("Width of the compass.").defaultValue(280).min(40).sliderRange(40, 800).build());

    private final Setting<Integer> height = sgGeneral.add(new IntSetting.Builder()
        .name("height").description("Height of the compass.").defaultValue(26).min(12).sliderRange(12, 100).build());

    private final Setting<Boolean> showNumbers = sgGeneral.add(new BoolSetting.Builder()
        .name("show-numbers").description("Shows degree numbers between the directions.").defaultValue(true).build());

    private final Setting<Boolean> showHeading = sgGeneral.add(new BoolSetting.Builder()
        .name("show-heading").description("Shows your exact heading under the compass.").defaultValue(true).build());

    private final Setting<SettingColor> bgColor = sgColors.add(new ColorSetting.Builder()
        .name("background").description("Background color.").defaultValue(new SettingColor(10, 10, 20, 170)).build());

    private final Setting<SettingColor> accentColor = sgColors.add(new ColorSetting.Builder()
        .name("accent").description("Border lines and the center pointer.").defaultValue(new SettingColor(150, 60, 255, 255)).build());

    private final Setting<SettingColor> northColor = sgColors.add(new ColorSetting.Builder()
        .name("north").description("Color of N.").defaultValue(new SettingColor(255, 70, 70, 255)).build());

    private final Setting<SettingColor> cardinalColor = sgColors.add(new ColorSetting.Builder()
        .name("cardinals").description("Color of E, S and W.").defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private final Setting<SettingColor> interColor = sgColors.add(new ColorSetting.Builder()
        .name("in-betweens").description("Color of NE, SE, SW and NW.").defaultValue(new SettingColor(190, 160, 255, 255)).build());

    private final Setting<SettingColor> numberColor = sgColors.add(new ColorSetting.Builder()
        .name("numbers").description("Color of the degree numbers.").defaultValue(new SettingColor(140, 140, 160, 255)).build());

    private final Setting<SettingColor> tickColor = sgColors.add(new ColorSetting.Builder()
        .name("ticks").description("Color of the tick marks.").defaultValue(new SettingColor(200, 200, 220, 160)).build());

    private final Setting<SettingColor> headingColor = sgColors.add(new ColorSetting.Builder()
        .name("heading").description("Color of the heading readout.").defaultValue(new SettingColor(255, 255, 255, 255)).build());

    private double smooth = Double.NaN;

    public Compass() {
        super("compass", "A stylish compass strip with directions and degrees based on where you're facing.");
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.options.hideGui) return;

        int w = width.get(), h = height.get();
        double bx = event.screenWidth / 2.0 - w / 2.0 + offsetX.get(), by = offsetY.get(), cx = bx + w / 2.0;

        double target = ((mc.player.getYRot() + 180) % 360 + 360) % 360;
        if (low() || Double.isNaN(smooth)) smooth = target;
        else smooth = ((smooth + (((target - smooth) % 360 + 540) % 360 - 180) * 0.25) % 360 + 360) % 360;
        double heading = smooth;

        HudRenderer r = HudRenderer.INSTANCE;
        r.begin(event.graphics);

        Color bg = bgColor.get(), clear = withAlpha(bg, 0);
        r.quad(bx, by, w / 2.0, h, clear, bg, bg, clear);
        r.quad(cx, by, w / 2.0, h, bg, clear, clear, bg);
        Color acc = accentColor.get(), accClear = withAlpha(acc, 0);
        r.quad(bx, by, w / 2.0, 1, accClear, acc, acc, accClear);
        r.quad(cx, by, w / 2.0, 1, acc, accClear, accClear, acc);
        r.quad(bx, by + h - 1, w / 2.0, 1, accClear, acc, acc, accClear);
        r.quad(cx, by + h - 1, w / 2.0, 1, acc, accClear, accClear, acc);

        if (!low()) {
            Color glowC = withAlpha(acc, 0.35), glowClear = withAlpha(acc, 0);
            r.quad(cx - 18, by, 18, h, glowClear, glowC, glowC, glowClear);
            r.quad(cx, by, 18, h, glowC, glowClear, glowClear, glowC);
        }
        for (int deg = 0; deg < 360; deg += low() ? 15 : 5) {
            double px = labelX(deg, heading, w);
            if (Double.isNaN(px)) continue;
            double edge = Math.abs(px - w / 2.0) / (w / 2.0);
            double fade = 1 - edge * edge;
            double x = bx + px;

            if (deg % 45 == 0) {
                String label = LABELS[deg / 45];
                boolean cardinal = deg % 90 == 0;
                Color c = deg == 0 ? northColor.get() : cardinal ? cardinalColor.get() : interColor.get();
                double scale = cardinal ? 1.15 : 0.85;
                drawCentered(r, label, x, by + (h - r.textHeight(true, scale)) / 2, withAlpha(c, fade), scale);
            } else if (deg % 15 == 0 && showNumbers.get() && !low()) {
                drawCentered(r, String.valueOf(deg), x, by + (h - r.textHeight(true, 0.6)) / 2, withAlpha(numberColor.get(), fade), 0.6);
            } else {
                double len = h * 0.18;
                Color t = withAlpha(tickColor.get(), fade);
                r.quad(x - 0.5, by + 1, 1, len, t);
                r.quad(x - 0.5, by + h - 1 - len, 1, len, t);
            }
        }

        r.triangle(cx - 4, by, cx + 4, by, cx, by + 5, acc);
        r.triangle(cx - 4, by + h, cx, by + h - 5, cx + 4, by + h, acc);

        if (showHeading.get()) {
            String text = Math.round(heading) % 360 + "° " + LABELS[(int) Math.round(heading / 45) % 8];
            double tw = r.textWidth(text, true, 0.8), th = r.textHeight(true, 0.8);
            double hx = cx - tw / 2 - 5, hy = by + h + 3;
            r.quad(hx, hy, tw + 10, th + 4, bg);
            r.quad(hx, hy + th + 3, tw + 10, 1, acc);
            r.text(text, cx - tw / 2, hy + 2, headingColor.get(), true, 0.8);
        }

        r.end();
    }

    private static void drawCentered(HudRenderer r, String text, double x, double y, Color color, double scale) {
        r.text(text, x - r.textWidth(text, true, scale) / 2, y, color, true, scale);
    }

    private static Color withAlpha(Color c, double factor) {
        return new Color(c.r, c.g, c.b, (int) (c.a * factor));
    }

    static double labelX(double labelAngle, double heading, double w) {
        double delta = ((labelAngle - heading) % 360 + 540) % 360 - 180;
        return Math.abs(delta) > 90 ? Double.NaN : w / 2 + delta / 180 * w;
    }
}
