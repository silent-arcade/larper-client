package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class Atmosphere extends VisualModule {
    public enum Style { Snow, Petals, Fireflies, Embers, Aurora }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Snow, Petals, Fireflies, Embers, or Aurora (northern lights waving across the sky).").defaultValue(Style.Fireflies).build());

    private final Setting<Integer> amount = sg.add(new IntSetting.Builder()
        .name("amount").description("How many are around you at once.").defaultValue(60).range(5, 300).sliderRange(10, 150).build());

    private final Setting<Double> radius = sg.add(new DoubleSetting.Builder()
        .name("radius").description("How far around you they spread (blocks).").defaultValue(8).range(2, 24).sliderRange(3, 16).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("size").description("Speck size.").defaultValue(1).range(0.3, 4).sliderRange(0.5, 2.5).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Every speck a different colour.").defaultValue(false).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Speck colour.").defaultValue(new SettingColor(200, 255, 120, 200)).visible(() -> !rainbow.get()).build());

    private static final class Speck {
        double x, y, z, vx, vy, vz, born, life, phase, hue;
    }

    private final List<Speck> specks = new ArrayList<>();
    private final Random rng = new Random();
    private double last;

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> streaks = feature("streaks", "Snow, petals and embers leave a short fading streak.");
    private final Setting<Boolean> twinkles = feature("twinkles", "Bright fireflies and snowflakes twinkle.");
    private final Setting<Boolean> auroraEdge = feature("aurora-edge", "Aurora: a bright line along the bottom of the curtains.");

    public Atmosphere() {
        super("atmosphere", "Snow, petals, fireflies, embers or an aurora in the sky. Only you see them.");
    }

    @Override
    public void onDeactivate() {
        specks.clear();
    }

    private void aurora(Render3DEvent event, Vec3d me, double now) {
        int cols = detail(60, 20), layers = low() ? 1 : 2;
        for (int layer = 0; layer < layers; layer++) {
            double dist = 38 + layer * 18, baseY = me.y + 30 + layer * 6, height = 26 - layer * 6;
            Vec3d prevBottom = null, prevTop = null;
            Color prevC = null;
            for (int i = 0; i <= cols; i++) {
                double u = (double) i / cols, x = (u - 0.5) * 140;
                double z = dist + 8 * Math.sin(x * 0.045 + now * 0.25 + layer) + 4 * Math.sin(x * 0.11 - now * 0.4);
                double y = baseY + 3 * Math.sin(x * 0.07 + now * 0.3);
                double flicker = 0.6 + 0.4 * Math.sin(x * 0.2 + now * 1.3 + layer * 2);
                double edgeFade = Math.min(1, Math.min(u, 1 - u) * 6);
                Color base = rainbow.get() ? Fx.hue(0.33 + 0.25 * Math.sin(x * 0.02 + now * 0.1), 200) : color.get();
                Color c = Fx.alpha(base, (int) Math.min(255, base.a * 1.1 * flicker * edgeFade));
                Vec3d bottom = new Vec3d(me.x + x, y, me.z + z), top = bottom.add(0, height * (0.8 + 0.2 * flicker), 0);
                if (prevBottom != null) {
                    Color clear = Fx.alpha(c, 0), pClear = Fx.alpha(prevC, 0);
                    event.renderer.quad(prevBottom.x, prevBottom.y, prevBottom.z, bottom.x, bottom.y, bottom.z, top.x, top.y, top.z, prevTop.x, prevTop.y, prevTop.z, prevC, c, clear, pClear);
                    if (on(auroraEdge)) event.renderer.line(prevBottom.x, prevBottom.y, prevBottom.z, bottom.x, bottom.y, bottom.z, Fx.alpha(c, Math.min(255, c.a * 2)));
                }
                prevBottom = bottom;
                prevTop = top;
                prevC = c;
            }
        }
    }

    private Speck spawn(Vec3d me, double now, boolean anywhere) {
        Speck s = new Speck();
        double r = radius.get(), a = rng.nextDouble() * Math.PI * 2, d = Math.sqrt(rng.nextDouble()) * r;
        s.x = me.x + Math.cos(a) * d;
        s.z = me.z + Math.sin(a) * d;
        s.y = switch (style.get()) {
            case Snow, Petals -> me.y + (anywhere ? rng.nextDouble() * 6 - 1 : 5 + rng.nextDouble());
            case Embers -> me.y + (anywhere ? rng.nextDouble() * 5 - 1 : -1);
            case Fireflies -> me.y + rng.nextDouble() * 3;
            case Aurora -> me.y;
        };
        s.born = now;
        s.life = 4 + rng.nextDouble() * 6;
        s.phase = rng.nextDouble() * 100;
        s.hue = rng.nextDouble();
        return s;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null) return;
        double now = Fx.time(), dt = Math.min(0.1, now - last);
        last = now;
        Vec3d me = Fx.pos(mc.player, event.tickDelta);
        if (style.get() == Style.Aurora) {
            aurora(event, me, now);
            return;
        }
        double r = radius.get();
        int want = detail(amount.get(), 5);
        while (specks.size() < want) specks.add(spawn(me, now, specks.isEmpty() || dt == 0.1));
        if (specks.size() > want) specks.subList(want, specks.size()).clear();

        for (int i = 0; i < specks.size(); i++) {
            Speck s = specks.get(i);
            double age = now - s.born, w = s.phase + now;
            switch (style.get()) {
                case Snow -> { s.vx = Math.sin(w * 0.7) * 0.25; s.vy = -0.6; s.vz = Math.cos(w * 0.5) * 0.25; }
                case Petals -> { s.vx = Math.sin(w * 1.3) * 0.8 + 0.3; s.vy = -0.45; s.vz = Math.cos(w * 1.1) * 0.6; }
                case Fireflies -> { s.vx = Math.sin(w * 0.9) * 0.4; s.vy = Math.sin(w * 1.7) * 0.25; s.vz = Math.cos(w * 0.8) * 0.4; }
                case Embers -> { s.vx = Math.sin(w * 2.1) * 0.2; s.vy = 0.9; s.vz = Math.cos(w * 1.9) * 0.2; }
            }
            s.x += s.vx * dt;
            s.y += s.vy * dt;
            s.z += s.vz * dt;
            boolean gone = age > s.life || Math.hypot(s.x - me.x, s.z - me.z) > r * 1.2 || Math.abs(s.y - me.y) > 8;
            if (gone) {
                specks.set(i, spawn(me, now, false));
                continue;
            }
            double fade = Math.min(1, Math.min(age / 0.8, (s.life - age) / 0.8));
            double blink = style.get() == Style.Fireflies ? 0.35 + 0.65 * Math.max(0, Math.sin(w * 2.3)) : style.get() == Style.Embers ? 0.6 + 0.4 * Math.sin(w * 9) : 1;
            Color base = rainbow.get() ? Fx.hue(s.hue + now * 0.05, 220) : color.get();
            double rad = 0.05 * size.get() * (style.get() == Style.Petals ? 1.3 : 1);
            Vec3d at = new Vec3d(s.x, s.y, s.z);
            Color c = Fx.alpha(base, (int) (base.a * fade * blink));
            glow(event.renderer, at, rad, c);
            if (on(streaks) && (style.get() == Style.Embers || style.get() == Style.Snow || style.get() == Style.Petals)) {
                Vec3d tail = at.subtract(s.vx * 0.12, s.vy * 0.12, s.vz * 0.12);
                event.renderer.line(at.x, at.y, at.z, tail.x, tail.y, tail.z, c, Fx.alpha(c, 0));
            }
            if (on(twinkles) && ((style.get() == Style.Fireflies && blink > 0.9) || (style.get() == Style.Snow && Math.sin(w * 3.1) > 0.97))) spark(event.renderer, at, rad * 3, c, true);
        }
    }
}
