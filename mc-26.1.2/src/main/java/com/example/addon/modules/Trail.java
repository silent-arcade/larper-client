package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/** A glowing ribbon that follows behind you as you move, fading out at its tail. Only you see it. */
public class Trail extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

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

    public Trail() {
        super(AddonTemplate.CATEGORY, "trail", "A glowing ribbon trailing behind you. Only you see it.");
    }

    @Override
    public void onDeactivate() {
        points.clear();
    }

    // Only grows while you move; standing still lets it shrink away
    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        Vec3 p = mc.player.position();
        if (points.isEmpty() || points.peekLast().distanceToSqr(p) > 0.0025) points.addLast(p);
        else if (!points.isEmpty()) points.pollFirst();
        while (points.size() > length.get()) points.pollFirst();
    }

    // A ribbon that always turns its face to the camera (like trails in games), so it never disappears edge-on
    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || points.size() < 2) return;
        List<Vec3> list = new ArrayList<>(points);
        list.add(Fx.pos(mc.player, event.tickDelta)); // reach all the way to you this frame
        double t = Fx.time(), half = height.get() / 2, lift = offset.get() + half;
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        int n = list.size();
        Vec3[] lo = new Vec3[n], hi = new Vec3[n];
        for (int i = 0; i < n; i++) {
            Vec3 p = list.get(i).add(0, lift, 0);
            Vec3 dir = list.get(Math.min(n - 1, i + 1)).subtract(list.get(Math.max(0, i - 1)));
            Vec3 side = dir.cross(cam.subtract(p));
            side = side.lengthSqr() < 1e-8 ? new Vec3(0, 1, 0) : side.normalize();
            double w = half * (0.35 + 0.65 * i / (n - 1)); // thin at the tail, full at you
            lo[i] = p.subtract(side.scale(w));
            hi[i] = p.add(side.scale(w));
        }
        for (int i = 1; i < n; i++) {
            Color ca = shade(i - 1, n, t), cb = shade(i, n, t);
            Vec3 a0 = lo[i - 1], a1 = hi[i - 1], b0 = lo[i], b1 = hi[i];
            event.depthRenderer.quad(a0.x, a0.y, a0.z, a1.x, a1.y, a1.z, b1.x, b1.y, b1.z, b0.x, b0.y, b0.z, ca, ca, cb, cb);
            event.depthRenderer.line(a1.x, a1.y, a1.z, b1.x, b1.y, b1.z, Fx.alpha(ca, Math.min(255, ca.a + 60)));
        }
    }

    // Fades from clear at the tail to full at you
    private Color shade(int i, int n, double t) {
        double f = (double) i / (n - 1);
        Color c = rainbow.get() ? Fx.hue(t * 0.15 - f * 0.6, 200) : color.get();
        return Fx.alpha(c, (int) (c.a * f));
    }
}
