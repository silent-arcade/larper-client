package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Soft glowing specks drifting around you: snow, petals, fireflies or embers. Only you see them. */
public class Atmosphere extends Module {
    public enum Style { Snow, Petals, Fireflies, Embers }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("How the specks move.").defaultValue(Style.Fireflies).build());

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

    public Atmosphere() {
        super(AddonTemplate.CATEGORY, "atmosphere", "Snow, petals, fireflies or embers drifting around you. Only you see them.");
    }

    @Override
    public void onDeactivate() {
        specks.clear();
    }

    private Speck spawn(Vec3 me, double now, boolean anywhere) {
        Speck s = new Speck();
        double r = radius.get(), a = rng.nextDouble() * Math.PI * 2, d = Math.sqrt(rng.nextDouble()) * r;
        s.x = me.x + Math.cos(a) * d;
        s.z = me.z + Math.sin(a) * d;
        s.y = switch (style.get()) {
            case Snow, Petals -> me.y + (anywhere ? rng.nextDouble() * 6 - 1 : 5 + rng.nextDouble());
            case Embers -> me.y + (anywhere ? rng.nextDouble() * 5 - 1 : -1);
            case Fireflies -> me.y + rng.nextDouble() * 3;
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
        Vec3 me = Fx.pos(mc.player, event.tickDelta);
        double r = radius.get();
        while (specks.size() < amount.get()) specks.add(spawn(me, now, specks.isEmpty() || dt == 0.1));
        if (specks.size() > amount.get()) specks.subList(amount.get(), specks.size()).clear();

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
            // Recycle once it's had its time or wandered out of the area around you
            boolean gone = age > s.life || Math.hypot(s.x - me.x, s.z - me.z) > r * 1.2 || Math.abs(s.y - me.y) > 8;
            if (gone) {
                specks.set(i, spawn(me, now, false));
                continue;
            }
            double fade = Math.min(1, Math.min(age / 0.8, (s.life - age) / 0.8));
            double blink = style.get() == Style.Fireflies ? 0.35 + 0.65 * Math.max(0, Math.sin(w * 2.3)) : style.get() == Style.Embers ? 0.6 + 0.4 * Math.sin(w * 9) : 1;
            Color base = rainbow.get() ? Fx.hue(s.hue + now * 0.05, 220) : color.get();
            double rad = 0.05 * size.get() * (style.get() == Style.Petals ? 1.3 : 1);
            Fx.glow(event.renderer, new Vec3(s.x, s.y, s.z), rad, Fx.alpha(base, (int) (base.a * fade * blink)));
        }
    }
}
