package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class EnergyShield extends VisualModule {
    public enum Style { Bubble, Hex }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Bubble: a filled glowing bubble. Hex: just the bright web of its edges, like a force-field grid.")
        .defaultValue(Style.Bubble).build());

    private final Setting<Boolean> alwaysOn = sg.add(new BoolSetting.Builder()
        .name("always-visible").description("Show the shield all the time (otherwise only when you get hit).").defaultValue(true).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("size").description("Shield radius (blocks).").defaultValue(1.25).range(0.6, 4).sliderRange(0.8, 2.5).build());

    private final Setting<Boolean> thirdPersonOnly = sg.add(new BoolSetting.Builder()
        .name("third-person-only").description("Hide it in first person.").defaultValue(true).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Shield colour.").defaultValue(new SettingColor(70, 190, 255, 90)).build());

    private final Setting<SettingColor> hitColor = sg.add(new ColorSetting.Builder()
        .name("hit-color").description("Ripple colour where you're hit.").defaultValue(new SettingColor(255, 255, 255, 230)).build());

    private static final List<Vec3[]> TRIS = icosphere(2), TRIS_LOW = icosphere(1);

    private double hitAt = -1;
    private Vec3 hitDir = new Vec3(0, 0, 1);
    private int lastHurt;

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> scanBand = feature("scan-band", "A band of light scanning up and down the bubble.");
    private final Setting<Boolean> hitSparks = feature("hit-sparks", "Sparks and a flash where you get hit.");

    public EnergyShield() {
        super("energy-shield", "A glowing energy bubble or hex force-field around you that ripples when you get hit. Only you see it.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (thirdPersonOnly.get() && mc.options.getCameraType().isFirstPerson())) return;
        double now = Fx.time();
        int hurt = mc.player.hurtTime;
        if (hurt > lastHurt) {
            hitAt = now;
            float yaw = mc.player.getHurtDir() + mc.player.getYRot();
            hitDir = Vec3.directionFromRotation(0, yaw).normalize().add(0, 0.2, 0).normalize();
        }
        lastHurt = hurt;
        double sinceHit = hitAt < 0 ? 99 : now - hitAt;
        double showHit = Math.max(0, 1 - sinceHit / 1.2);
        if (!alwaysOn.get() && showHit <= 0) return;

        Vec3 c = Fx.pos(mc.player, event.tickDelta).add(0, mc.player.getBbHeight() * 0.5, 0);
        double R = size.get(), ry = R * 1.15;
        Color base = color.get(), hit = hitColor.get();
        double baseAlpha = alwaysOn.get() ? 1 : showHit;

        for (Vec3[] t : low() ? TRIS_LOW : TRIS) {
            Vec3 mid = t[0].add(t[1]).add(t[2]).scale(1 / 3.0).normalize();
            double shimmer = 0.55 + 0.45 * Math.sin(mid.y * 6 + now * 2.2 + mid.x * 3);
            double ang = Math.acos(Math.max(-1, Math.min(1, mid.dot(hitDir))));
            double ring = showHit > 0 ? Math.exp(-Math.pow((ang - sinceHit * 3.2) * 4, 2)) * showHit : 0;
            double fill = baseAlpha * shimmer * 0.35 + ring;
            Color fc = blend(base, hit, Math.min(1, ring), (int) Math.min(255, base.a * fill + hit.a * ring * 0.6));
            Vec3 a = place(c, t[0], R, ry), b = place(c, t[1], R, ry), d = place(c, t[2], R, ry);
            boolean grid = style.get() == Style.Hex;
            if (!grid || ring > 0.05) event.renderer.quad(a.x, a.y, a.z, b.x, b.y, b.z, d.x, d.y, d.z, a.x, a.y, a.z, grid ? Fx.alpha(fc, (int) (fc.a * ring)) : fc);
            Color edge = blend(base, hit, Math.min(1, ring), (int) Math.min(255, (base.a * 1.6 + 40) * baseAlpha * shimmer + hit.a * ring));
            event.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, edge);
            event.renderer.line(b.x, b.y, b.z, d.x, d.y, d.z, edge);
        }
        double scanY = Math.sin(now * 1.4) * 0.85, rr = Math.sqrt(Math.max(0, 1 - scanY * scanY)) * R;
        if (on(scanBand)) ring(event.renderer, c.add(0, scanY * ry, 0), rr * 0.97, rr, 48, u -> Fx.alpha(base, (int) Math.min(255, base.a * 1.8 * baseAlpha)));
        if (on(hitSparks) && showHit > 0) {
            Vec3 at = place(c, hitDir, R, ry);
            for (int k = 0; k < 6; k++) {
                double a = k * Math.PI / 3 + hitAt * 5, d = sinceHit * 1.6;
                Vec3 off = new Vec3(Math.cos(a), Math.sin(a * 1.7) * 0.6, Math.sin(a)).scale(d * 0.6);
                spark(event.renderer, at.add(off), 0.18 * showHit, Fx.alpha(hit, (int) (hit.a * showHit)), false);
            }
            glow(event.renderer, at, 0.45 * showHit, Fx.alpha(hit, (int) (hit.a * showHit)));
        }
    }

    private static Vec3 place(Vec3 c, Vec3 unit, double r, double ry) {
        return new Vec3(c.x + unit.x * r, c.y + unit.y * ry, c.z + unit.z * r);
    }

    private static Color blend(Color a, Color b, double t, int alpha) {
        return new Color((int) (a.r + (b.r - a.r) * t), (int) (a.g + (b.g - a.g) * t), (int) (a.b + (b.b - a.b) * t), Math.max(0, Math.min(255, alpha)));
    }

    private static List<Vec3[]> icosphere(int levels) {
        double p = (1 + Math.sqrt(5)) / 2;
        Vec3[] v = {
            new Vec3(-1, p, 0), new Vec3(1, p, 0), new Vec3(-1, -p, 0), new Vec3(1, -p, 0),
            new Vec3(0, -1, p), new Vec3(0, 1, p), new Vec3(0, -1, -p), new Vec3(0, 1, -p),
            new Vec3(p, 0, -1), new Vec3(p, 0, 1), new Vec3(-p, 0, -1), new Vec3(-p, 0, 1)};
        int[][] f = {{0, 11, 5}, {0, 5, 1}, {0, 1, 7}, {0, 7, 10}, {0, 10, 11}, {1, 5, 9}, {5, 11, 4}, {11, 10, 2}, {10, 7, 6}, {7, 1, 8},
            {3, 9, 4}, {3, 4, 2}, {3, 2, 6}, {3, 6, 8}, {3, 8, 9}, {4, 9, 5}, {2, 4, 11}, {6, 2, 10}, {8, 6, 7}, {9, 8, 1}};
        List<Vec3[]> tris = new ArrayList<>();
        for (int[] t : f) tris.add(new Vec3[]{v[t[0]].normalize(), v[t[1]].normalize(), v[t[2]].normalize()});
        for (int l = 0; l < levels; l++) {
            List<Vec3[]> next = new ArrayList<>();
            Map<String, Vec3> mids = new HashMap<>();
            for (Vec3[] t : tris) {
                Vec3 ab = t[0].add(t[1]).normalize(), bc = t[1].add(t[2]).normalize(), ca = t[2].add(t[0]).normalize();
                next.add(new Vec3[]{t[0], ab, ca});
                next.add(new Vec3[]{t[1], bc, ab});
                next.add(new Vec3[]{t[2], ca, bc});
                next.add(new Vec3[]{ab, bc, ca});
            }
            tris = next;
        }
        return tris;
    }
}
