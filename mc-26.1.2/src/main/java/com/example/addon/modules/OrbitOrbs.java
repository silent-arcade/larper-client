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
import net.minecraft.world.phys.Vec3;

public class OrbitOrbs extends VisualModule {
    public enum Style { Ring, Atom, Helix, Planets }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Ring: one flat circle. Atom: tilted orbits. Helix: spiralling up and down. Planets: each at its own distance, with a ring and a moon.")
        .defaultValue(Style.Atom).build());

    private final Setting<Integer> count = sg.add(new IntSetting.Builder()
        .name("orbs").description("How many orbs.").defaultValue(3).range(1, 12).sliderRange(1, 8).build());

    private final Setting<Double> radius = sg.add(new DoubleSetting.Builder()
        .name("radius").description("How far from you they orbit.").defaultValue(0.9).range(0.3, 4).sliderRange(0.4, 2.5).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("speed").description("Turns per second.").defaultValue(0.6).range(0.05, 4).sliderRange(0.1, 2).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("size").description("Orb size.").defaultValue(0.12).range(0.03, 0.6).sliderRange(0.05, 0.3).build());

    private final Setting<Integer> trail = sg.add(new IntSetting.Builder()
        .name("trail").description("Trail length (0 = none).").defaultValue(14).range(0, 40).sliderRange(0, 30).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Each orb a different, slowly shifting colour.").defaultValue(true).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Orb colour when rainbow is off.").defaultValue(new SettingColor(120, 200, 255, 210))
        .visible(() -> !rainbow.get()).build());

    private final Setting<Boolean> firstPerson = sg.add(new BoolSetting.Builder()
        .name("first-person").description("Also show them in first person (they swing past your view).").defaultValue(true).build());

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> cometBeads = feature("comet-beads", "Glowing beads along each orb's trail.");
    private final Setting<Boolean> pulseOn = feature("pulse", "Orbs gently swell and shrink.");
    private final Setting<Boolean> planetRings = feature("planet-rings", "Planets style: a ring and a moon on each planet.");

    public OrbitOrbs() {
        super("orbit-orbs", "Glowing orbs (or little planets) orbiting around you. Only you see them.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (!firstPerson.get() && mc.options.getCameraType().isFirstPerson())) return;
        Vec3 base = Fx.pos(mc.player, event.tickDelta).add(0, mc.player.getBbHeight() * 0.55, 0);
        double t = Fx.time();
        int n = count.get(), tl = low() ? trail.get() / 3 : trail.get();
        for (int i = 0; i < n; i++) {
            Color c = rainbow.get() ? Fx.hue(t * 0.08 + (double) i / n, 220) : color.get();
            for (int k = tl; k > 0; k--) {
                Vec3 a = orbit(i, n, t - k * 0.02), b = orbit(i, n, t - (k - 1) * 0.02);
                double fade = 1 - (double) k / (tl + 1);
                event.depthRenderer.line(base.x + a.x, base.y + a.y, base.z + a.z, base.x + b.x, base.y + b.y, base.z + b.z, Fx.alpha(c, (int) (c.a * fade * 0.8)));
                if (on(cometBeads) && k % 3 == 0) glow(event.depthRenderer, base.add(a), size.get() * 0.55 * fade, Fx.alpha(c, (int) (c.a * fade * 0.6)));
            }
            double pulse = on(pulseOn) ? 1 + 0.18 * Math.sin(t * 5 + i * 1.3) : 1;
            Vec3 at = base.add(orbit(i, n, t));
            glow(event.depthRenderer, at, size.get() * pulse, c);
            if (style.get() == Style.Planets && on(planetRings)) planet(event, at, i, t, c);
        }
    }

    private void planet(Render3DEvent event, Vec3 at, int i, double t, Color c) {
        double rr = size.get() * 2.2, tilt = 0.5 + i * 0.3;
        int segs = 24;
        for (int k = 0; k < segs; k++) {
            double a0 = Math.PI * 2 * k / segs, a1 = Math.PI * 2 * (k + 1) / segs;
            Vec3 p0 = at.add(Math.cos(a0) * rr, Math.sin(a0) * rr * Math.sin(tilt) * 0.4, Math.sin(a0) * rr * Math.cos(tilt));
            Vec3 p1 = at.add(Math.cos(a1) * rr, Math.sin(a1) * rr * Math.sin(tilt) * 0.4, Math.sin(a1) * rr * Math.cos(tilt));
            event.depthRenderer.line(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, Fx.alpha(c, c.a * 2 / 3));
        }
        double ma = t * 4 + i;
        spark(event.depthRenderer, at.add(Math.cos(ma) * rr * 1.4, Math.sin(ma * 0.7) * rr * 0.5, Math.sin(ma) * rr * 1.4), size.get() * 0.9, Fx.alpha(c, 255), true);
    }

    private Vec3 orbit(int i, int n, double t) {
        double r = radius.get(), phase = (double) i / n * Math.PI * 2, a = t * speed.get() * Math.PI * 2 + phase;
        return switch (style.get()) {
            case Ring -> new Vec3(Math.cos(a) * r, Math.sin(t * 2 + phase) * 0.06, Math.sin(a) * r);
            case Helix -> new Vec3(Math.cos(a) * r, Math.sin(a * 0.5 + phase) * 0.8, Math.sin(a) * r);
            case Planets -> {
                double pr = r * (0.55 + 0.6 * i / Math.max(1, n - 1)), pa = t * speed.get() * Math.PI * 2 * (1.6 - 0.8 * i / Math.max(1, n - 1)) + phase;
                yield new Vec3(Math.cos(pa) * pr, 0.05 * Math.sin(t + i), Math.sin(pa) * pr);
            }
            case Atom -> {
                double tilt = Math.PI / n * i + 0.5, turn = phase + t * 0.2;
                double x = Math.cos(a) * r, z = Math.sin(a) * r;
                double y = z * Math.sin(tilt);
                z *= Math.cos(tilt);
                yield new Vec3(x * Math.cos(turn) - z * Math.sin(turn), y, x * Math.sin(turn) + z * Math.cos(turn));
            }
        };
    }
}
