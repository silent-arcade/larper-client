package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;

/** A rotating rune circle under your feet: glowing rings, rune marks, spokes and orbiting beads (no star shapes). */
public class MagicCircle extends Module {
    public enum Design { Runes, Sun, Ripples, Gear, Portal }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Design> design = sg.add(new EnumSetting.Builder<Design>()
        .name("design").description("Runes: rings and rune marks. Sun: rays. Ripples: pulsing rings. Gear: a turning cog. Portal: a swirl.")
        .defaultValue(Design.Runes).build());

    private final Setting<Double> radius = sg.add(new DoubleSetting.Builder()
        .name("radius").description("Circle size (blocks).").defaultValue(1.3).range(0.4, 5).sliderRange(0.6, 3).build());

    private final Setting<Double> spin = sg.add(new DoubleSetting.Builder()
        .name("spin").description("Turns per second.").defaultValue(0.12).range(0, 2).sliderRange(0, 0.6).build());

    private final Setting<Boolean> thirdPersonOnly = sg.add(new BoolSetting.Builder()
        .name("third-person-only").description("Hide it in first person (it covers the bottom of your screen there).").defaultValue(true).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Slowly cycle through colours.").defaultValue(false).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Rings and runes.").defaultValue(new SettingColor(140, 90, 255, 220)).visible(() -> !rainbow.get()).build());

    private final Setting<SettingColor> accentColor = sg.add(new ColorSetting.Builder()
        .name("accent-color").description("Spokes and beads.").defaultValue(new SettingColor(255, 200, 90, 230)).visible(() -> !rainbow.get()).build());

    public MagicCircle() {
        super(AddonTemplate.CATEGORY, "magic-circle", "A rotating circle under your feet: runes, sun, ripples, gear or portal. Only you see it.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (thirdPersonOnly.get() && mc.options.getCameraType().isFirstPerson())) return;
        double now = Fx.time(), R = radius.get(), turn = now * spin.get() * Math.PI * 2;
        double pulse = 0.85 + 0.15 * Math.sin(now * 2.5);
        Vec3 c = Fx.pos(mc.player, event.tickDelta).add(0, 0.02, 0);
        Color ring = rainbow.get() ? Fx.hue(now * 0.08, 220) : color.get();
        Color accent = rainbow.get() ? Fx.hue(now * 0.08 + 0.5, 230) : accentColor.get();
        Color ringP = Fx.alpha(ring, (int) (ring.a * pulse));

        Color accentP = Fx.alpha(accent, (int) (accent.a * pulse));
        switch (design.get()) {
            case Runes -> runes(event, c, R, turn, ringP, accentP);
            case Sun -> sun(event, c, R, turn, now, ringP, accentP);
            case Ripples -> ripples(event, c, R, now, ring, accentP);
            case Gear -> gear(event, c, R, turn, ringP, accentP);
            case Portal -> portal(event, c, R, turn, now, ring, accent);
        }
    }

    // Rings with rune marks turning one way, spokes and diamond beads turning the other
    private void runes(Render3DEvent event, Vec3 c, double R, double turn, Color ringP, Color accentP) {
        Fx.ring(event.renderer, c, R * 0.93, R, 64, u -> ringP);
        Fx.ring(event.renderer, c, R * 0.80, R * 0.83, 64, u -> Fx.alpha(ringP, ringP.a * 3 / 4));
        Fx.ring(event.renderer, c, R * 0.30, R * 0.33, 40, u -> ringP);
        for (int i = 0; i < 24; i++) {
            double a = -turn * 0.6 + Math.PI * 2 * i / 24;
            line(event, c, a, R * 0.845, a, R * (i % 3 == 0 ? 0.92 : 0.89), ringP);
            if (i % 3 == 0) line(event, c, a - 0.06, R * 0.885, a + 0.06, R * 0.885, ringP);
        }
        Fx.ring(event.renderer, c, R * 0.55, R * 0.57, 48, u -> Fx.alpha(accentP, accentP.a * 2 / 3));
        for (int k = 0; k < 12; k++) {
            double a = turn + k * Math.PI / 6;
            line(event, c, a, R * 0.33, a, R * (k % 2 == 0 ? 0.8 : 0.55), Fx.alpha(accentP, accentP.a / 2));
        }
        for (int k = 0; k < 6; k++) diamond(event, c, -turn * 1.5 + k * Math.PI / 3 + Math.PI / 6, R * 0.68, R * 0.06, accentP);
    }

    // A glowing core with long and short rays that flicker in length
    private void sun(Render3DEvent event, Vec3 c, double R, double turn, double now, Color ringP, Color accentP) {
        Fx.ring(event.renderer, c, 0, R * 0.28, 40, u -> accentP);
        Fx.ring(event.renderer, c, R * 0.28, R * 0.42, 40, u -> Fx.alpha(accentP, 0)); // soft edge (fades out)
        Fx.ring(event.renderer, c, R * 0.33, R * 0.36, 40, u -> ringP);
        int rays = 16;
        for (int k = 0; k < rays; k++) {
            double a = turn + Math.PI * 2 * k / rays, len = (k % 2 == 0 ? 1.0 : 0.72) * (0.9 + 0.1 * Math.sin(now * 4 + k));
            double w = Math.PI / rays * 0.55;
            Vec3 tip = at(c, a, R * len), l = at(c, a - w, R * 0.4), r = at(c, a + w, R * 0.4);
            event.renderer.quad(l.x, c.y, l.z, tip.x, c.y, tip.z, tip.x, c.y, tip.z, r.x, c.y, r.z, Fx.alpha(ringP, ringP.a * 2 / 3), Fx.alpha(ringP, 0), Fx.alpha(ringP, 0), Fx.alpha(ringP, ringP.a * 2 / 3));
        }
    }

    // Rings that spread from your feet and fade, one after another
    private void ripples(Render3DEvent event, Vec3 c, double R, double now, Color ring, Color accentP) {
        for (int i = 0; i < 4; i++) {
            double f = (now * 0.5 + i / 4.0) % 1, r = R * f;
            int a = (int) (ring.a * (1 - f));
            Fx.ring(event.renderer, c, Math.max(0, r - 0.05 - 0.06 * (1 - f)), r, 56, u -> Fx.alpha(ring, a));
        }
        Fx.ring(event.renderer, c, R * 0.12, R * 0.16, 32, u -> accentP);
    }

    // A cog: a rim with square teeth and spokes, turning
    private void gear(Render3DEvent event, Vec3 c, double R, double turn, Color ringP, Color accentP) {
        Fx.ring(event.renderer, c, R * 0.70, R * 0.80, 64, u -> ringP);
        Fx.ring(event.renderer, c, R * 0.16, R * 0.24, 32, u -> ringP);
        int teeth = 12;
        for (int k = 0; k < teeth; k++) {
            double a = turn + Math.PI * 2 * k / teeth, w = Math.PI / teeth * 0.5;
            Vec3 p0 = at(c, a - w, R * 0.8), p1 = at(c, a - w * 0.8, R), p2 = at(c, a + w * 0.8, R), p3 = at(c, a + w, R * 0.8);
            event.renderer.quad(p0.x, c.y, p0.z, p1.x, c.y, p1.z, p2.x, c.y, p2.z, p3.x, c.y, p3.z, ringP);
        }
        for (int k = 0; k < 6; k++) {
            double a = turn + Math.PI * 2 * k / 6;
            line(event, c, a, R * 0.24, a, R * 0.70, accentP);
            line(event, c, a + 0.04, R * 0.24, a + 0.02, R * 0.70, accentP);
        }
    }

    // A swirl: curved arms spiralling into the middle, darkening toward the centre
    private void portal(Render3DEvent event, Vec3 c, double R, double turn, double now, Color ring, Color accent) {
        Fx.ring(event.renderer, c, R * 0.94, R, 64, u -> ring);
        int arms = 5, steps = 24;
        for (int arm = 0; arm < arms; arm++) {
            for (int s = 0; s < steps; s++) {
                double f0 = (double) s / steps, f1 = (double) (s + 1) / steps;
                double a0 = turn * 2 + arm * Math.PI * 2 / arms + f0 * 2.6, a1 = turn * 2 + arm * Math.PI * 2 / arms + f1 * 2.6;
                double r0 = R * (0.95 - f0 * 0.9), r1 = R * (0.95 - f1 * 0.9);
                Color c0 = Fx.alpha(f0 < 0.5 ? ring : accent, (int) (ring.a * (1 - f0 * 0.6))), c1 = Fx.alpha(f1 < 0.5 ? ring : accent, (int) (ring.a * (1 - f1 * 0.6)));
                event.renderer.line(at(c, a0, r0).x, c.y, at(c, a0, r0).z, at(c, a1, r1).x, c.y, at(c, a1, r1).z, c0, c1);
            }
        }
        Fx.ring(event.renderer, c, 0, R * 0.12, 24, u -> Fx.alpha(accent, (int) (accent.a * (0.6 + 0.4 * Math.sin(now * 3)))));
    }

    private void diamond(Render3DEvent event, Vec3 c, double a, double r, double d, Color color) {
        Vec3 b = at(c, a, r);
        event.renderer.quad(b.x + d, c.y, b.z, b.x, c.y, b.z + d, b.x - d, c.y, b.z, b.x, c.y, b.z - d, color);
    }

    private static Vec3 at(Vec3 c, double angle, double r) {
        return new Vec3(c.x + Math.cos(angle) * r, c.y, c.z + Math.sin(angle) * r);
    }

    // A straight line between two points given as (angle, radius) around the centre
    private void line(Render3DEvent event, Vec3 c, double a0, double r0, double a1, double r1, Color color) {
        event.renderer.line(c.x + Math.cos(a0) * r0, c.y, c.z + Math.sin(a0) * r0, c.x + Math.cos(a1) * r1, c.y, c.z + Math.sin(a1) * r1, color);
    }
}
