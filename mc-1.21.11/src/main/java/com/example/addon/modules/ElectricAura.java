package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
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

public class ElectricAura extends VisualModule {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> arcs = sg.add(new IntSetting.Builder()
        .name("arcs").description("Arcs at once.").defaultValue(5).range(1, 20).sliderRange(1, 12).build());

    private final Setting<Double> rate = sg.add(new DoubleSetting.Builder()
        .name("strikes-per-second").description("How often the arcs jump to new spots.").defaultValue(14).range(2, 40).sliderRange(4, 30).build());

    private final Setting<Double> spread = sg.add(new DoubleSetting.Builder()
        .name("spread").description("How far out from your body the arcs reach (blocks).").defaultValue(0.5).range(0.1, 2).sliderRange(0.2, 1.2).build());

    private final Setting<Boolean> sparks = sg.add(new BoolSetting.Builder()
        .name("sparks").description("Glowing sparks where the arcs touch.").defaultValue(true).build());

    private final Setting<Boolean> skyStrikes = sg.add(new BoolSetting.Builder()
        .name("sky-strikes").description("Every few seconds a bolt comes down out of the sky onto you.").defaultValue(false).build());

    private final Setting<Boolean> thirdPersonOnly = sg.add(new BoolSetting.Builder()
        .name("third-person-only").description("Hide it in first person.").defaultValue(true).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Every arc a different colour.").defaultValue(false).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Arc colour (the core is always near white).").defaultValue(new SettingColor(110, 170, 255, 230))
        .visible(() -> !rainbow.get()).build());

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> forks = feature("forks", "Bolts split into a forked branch.");
    private final Setting<Boolean> doubleStrand = feature("double-strand", "Sky strikes: a second coloured strand beside the bolt.");

    public ElectricAura() {
        super("electric-aura", "Crackling lightning arcs around your body, with optional strikes from the sky. Only you see them.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (thirdPersonOnly.get() && mc.options.getPerspective().isFirstPerson())) return;
        double now = Fx.time();
        Vec3d feet = Fx.pos(mc.player, event.tickDelta);
        double h = mc.player.getHeight(), w = mc.player.getWidth() * 0.5 + 0.05;
        long frame = (long) (now * rate.get());
        if (skyStrikes.get()) skyStrike(event, feet, h, now);
        int n = low() ? Math.max(1, arcs.get() / 2) : arcs.get();
        for (int i = 0; i < n; i++) {
            Random r = new Random(frame * 31 + i * 977L);
            Vec3d a = feet.add(around(r, w), r.nextDouble() * h, 0).add(0, 0, around(r, w));
            double ang = r.nextDouble() * Math.PI * 2, out = w + spread.get() * (0.4 + r.nextDouble() * 0.6);
            Vec3d b = feet.add(Math.cos(ang) * out, r.nextDouble() * h * 1.1, Math.sin(ang) * out);
            List<Vec3d> bolt = new ArrayList<>();
            bolt.add(a);
            jag(bolt, a, b, 0.22, low() ? 3 : 4, r);
            bolt.add(b);
            Color base = rainbow.get() ? Fx.hue(r.nextDouble(), 230) : color.get();
            Color core = new Color(Math.min(255, base.r + 150), Math.min(255, base.g + 150), Math.min(255, base.b + 150), base.a);
            for (int k = 0; k + 1 < bolt.size(); k++) {
                Vec3d p = bolt.get(k), q = bolt.get(k + 1);
                event.renderer.line(p.x, p.y, p.z, q.x, q.y, q.z, core);
                event.renderer.line(p.x + 0.015, p.y + 0.015, p.z, q.x + 0.015, q.y + 0.015, q.z, Fx.alpha(base, base.a / 2));
            }
            if (on(forks) && bolt.size() > 6) {
                Vec3d from = bolt.get(bolt.size() / 3), to = from.add((r.nextDouble() - 0.5) * 0.5, (r.nextDouble() - 0.5) * 0.4, (r.nextDouble() - 0.5) * 0.5);
                List<Vec3d> fork = new ArrayList<>();
                fork.add(from);
                jag(fork, from, to, 0.12, 2, r);
                fork.add(to);
                for (int k = 0; k + 1 < fork.size(); k++) {
                    Vec3d p = fork.get(k), q = fork.get(k + 1);
                    event.renderer.line(p.x, p.y, p.z, q.x, q.y, q.z, Fx.alpha(core, core.a * 2 / 3));
                }
            }
            if (sparks.get() && !low()) {
                glow(event.renderer, b, 0.07, Fx.alpha(base, 200));
                glow(event.renderer, a, 0.05, Fx.alpha(base, 140));
            }
        }
    }

    private double nextStrike, strikeAt = -1;

    private void skyStrike(Render3DEvent event, Vec3d feet, double h, double now) {
        if (now > nextStrike) {
            strikeAt = now;
            nextStrike = now + 3 + Math.random() * 4;
        }
        double f = (now - strikeAt) / 0.25;
        if (strikeAt < 0 || f > 1) return;
        Random r = new Random((long) (strikeAt * 1000) + (long) (f * 4));
        Vec3d top = feet.add((r.nextDouble() - 0.5) * 6, 30, (r.nextDouble() - 0.5) * 6), bottom = feet.add(0, h * 0.6, 0);
        List<Vec3d> bolt = new ArrayList<>();
        bolt.add(top);
        jag(bolt, top, bottom, 4, low() ? 4 : 6, r);
        bolt.add(bottom);
        Color base = rainbow.get() ? Fx.hue(strikeAt, 255) : color.get();
        Color core = new Color(Math.min(255, base.r + 170), Math.min(255, base.g + 170), Math.min(255, base.b + 170), (int) (255 * (1 - f * 0.5)));
        for (int k = 0; k + 1 < bolt.size(); k++) {
            Vec3d p = bolt.get(k), q = bolt.get(k + 1);
            event.renderer.line(p.x, p.y, p.z, q.x, q.y, q.z, core);
            if (on(doubleStrand)) event.renderer.line(p.x + 0.05, p.y, p.z + 0.05, q.x + 0.05, q.y, q.z + 0.05, Fx.alpha(base, 160));
        }
        glow(event.renderer, feet.add(0, 0.3, 0), 1.6 * (1 - f), Fx.alpha(base, (int) (220 * (1 - f))));
    }

    private static double around(Random r, double w) {
        return (r.nextDouble() * 2 - 1) * w;
    }

    private static void jag(List<Vec3d> out, Vec3d a, Vec3d b, double amount, int depth, Random r) {
        if (depth == 0) return;
        Vec3d mid = a.add(b).multiply(0.5).add((r.nextDouble() - 0.5) * amount, (r.nextDouble() - 0.5) * amount, (r.nextDouble() - 0.5) * amount);
        jag(out, a, mid, amount * 0.55, depth - 1, r);
        out.add(mid);
        jag(out, mid, b, amount * 0.55, depth - 1, r);
    }
}
