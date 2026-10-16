package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
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
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class Trail extends VisualModule {
    public enum Style { Ribbon, Comet, Lightning }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Ribbon: a glowing band. Comet: a tail of glowing beads. Lightning: a crackling bolt following you.")
        .defaultValue(Style.Ribbon).build());

    private final Setting<Integer> length = sg.add(new IntSetting.Builder()
        .name("length").description("How long the trail is, in ticks of movement (20 = 1 second).").defaultValue(24).range(4, 100).sliderRange(5, 60).build());

    private final Setting<Double> height = sg.add(new DoubleSetting.Builder()
        .name("width").description("Ribbon width (blocks).").defaultValue(0.5).range(0.05, 2).sliderRange(0.1, 1.8).build());

    private final Setting<Double> offset = sg.add(new DoubleSetting.Builder()
        .name("offset").description("How high above your feet the ribbon starts.").defaultValue(0.6).range(0, 2).sliderRange(0, 1.8).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow along the trail.").defaultValue(true).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Trail colour when rainbow is off.").defaultValue(new SettingColor(170, 90, 255, 170))
        .visible(() -> !rainbow.get()).build());

    private final ArrayDeque<Vec3> points = new ArrayDeque<>();

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> edgeLines = feature("edge-lines", "Ribbon: bright lines along both edges.");
    private final Setting<Boolean> twinkles = feature("twinkles", "Little stars twinkling off the trail (and the comet's head flare).");
    private final Setting<Boolean> doubleStrand = feature("double-strand", "Lightning: a second faint strand beside the bolt.");

    public Trail() {
        super("trail", "A glowing ribbon, comet tail or lightning bolt trailing behind you. Only you see it.");
    }

    @Override
    public void onDeactivate() {
        points.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        Vec3 p = mc.player.position();
        if (points.isEmpty() || points.peekLast().distanceToSqr(p) > 0.0025) points.addLast(p);
        else if (!points.isEmpty()) points.pollFirst();
        while (points.size() > length.get()) points.pollFirst();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || points.size() < 2) return;
        List<Vec3> list = new ArrayList<>(points);
        list.add(Fx.pos(mc.player, event.tickDelta));
        double t = Fx.time(), half = height.get() / 2, lift = offset.get() + half;
        if (style.get() != Style.Ribbon) {
            drawStyle(event, list, t, lift, half);
            return;
        }
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        int n = list.size();
        Vec3[] lo = new Vec3[n], hi = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 p = list.get(i).add(0, lift, 0);
            Vec3 dir = list.get(Math.min(n - 1, i + 1)).subtract(list.get(Math.max(0, i - 1)));
            Vec3 side = dir.cross(cam.subtract(p));
            side = side.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : side.normalize();
            double w = half * (0.35 + 0.65 * i / (n - 1));
            lo[i] = p.subtract(side.scale(w));
            hi[i] = p.add(side.scale(w));
        }
        int prev = 0;
        for (int i = 1; i < n; i++) {
            if (low() && i % 2 == 1 && i != n - 1) continue;
            int p = prev;
            prev = i;
            Color ca = shade(p, n, t), cb = shade(i, n, t);
            Vec3 a0 = lo[p], a1 = hi[p], b0 = lo[i], b1 = hi[i];
            event.depthRenderer.quad(a0.x, a0.y, a0.z, a1.x, a1.y, a1.z, b1.x, b1.y, b1.z, b0.x, b0.y, b0.z, ca, ca, cb, cb);
            if (on(edgeLines)) {
                event.depthRenderer.line(a1.x, a1.y, a1.z, b1.x, b1.y, b1.z, Fx.alpha(ca, Math.min(255, ca.a + 60)));
                event.depthRenderer.line(a0.x, a0.y, a0.z, b0.x, b0.y, b0.z, Fx.alpha(ca, Math.min(255, ca.a + 30)));
            }
            if (!on(twinkles)) continue;
            double tw = Math.sin(t * 7 + i * 2.39);
            if (tw > 0.75) spark(event.depthRenderer, i % 2 == 0 ? a1 : a0, 0.09 * (tw - 0.75) * 4, Fx.alpha(cb, Math.min(255, cb.a + 50)), false);
        }
    }

    private void drawStyle(Render3DEvent event, List<Vec3> list, double t, double lift, double half) {
        int n = list.size();
        if (style.get() == Style.Comet) {
            int step = low() ? 3 : 1;
            for (int i = n - 1; i >= 0; i -= step) {
                double f = (double) i / (n - 1);
                Color c = shade(i, n, t);
                glow(event.depthRenderer, list.get(i).add(0, lift, 0), half * (0.25 + 0.75 * f), c);
            }
            if (on(twinkles)) spark(event.depthRenderer, list.get(n - 1).add(0, lift, 0), half * 1.6, Fx.alpha(shade(n - 1, n, t), 255), false);
            return;
        }
        java.util.Random r = new java.util.Random((long) (t * 15));
        Vec3 prev = null;
        for (int i = 0; i < n; i++) {
            double f = (double) i / (n - 1), jag = half * 0.6 * (i == n - 1 ? 0 : 1);
            Vec3 p = list.get(i).add((r.nextDouble() - 0.5) * jag, lift + (r.nextDouble() - 0.5) * jag, (r.nextDouble() - 0.5) * jag);
            if (prev != null) {
                Color c = shade(i, n, t);
                event.depthRenderer.line(prev.x, prev.y, prev.z, p.x, p.y, p.z, Fx.alpha(c, Math.min(255, c.a + 80)));
                if (on(doubleStrand)) event.depthRenderer.line(prev.x + 0.03, prev.y + 0.03, prev.z, p.x + 0.03, p.y + 0.03, p.z, Fx.alpha(c, c.a / 2));
                if (on(twinkles) && r.nextInt(6) == 0) spark(event.depthRenderer, p, 0.12 * f, c, true);
            }
            prev = p;
        }
        glow(event.depthRenderer, list.get(n - 1).add(0, lift, 0), half * 0.6, Fx.alpha(shade(n - 1, n, t), 230));
    }

    private Color shade(int i, int n, double t) {
        double f = (double) i / (n - 1);
        Color c = rainbow.get() ? Fx.hue(t * 0.15 - f * 0.6, 200) : color.get();
        return Fx.alpha(c, (int) (c.a * f));
    }
}
