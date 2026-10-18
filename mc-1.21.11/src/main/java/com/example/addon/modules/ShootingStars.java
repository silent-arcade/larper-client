package com.example.addon.modules;

import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import com.example.addon.visuals.VisualModule;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ShootingStars extends VisualModule {
    public enum Style { Mixed, Classic, Comet, Fireball, Neon, Rainbow }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Classic: thin bright streaks. Comet: big slow fiery ones shedding sparks. Fireball: orange-to-red, dropping embers. Neon: a white-hot core with glowing coloured edges. Rainbow: every tail runs through all the colours. Mixed: a random style for each star.")
        .defaultValue(Style.Mixed).build());

    private final Setting<Double> perMinute = sg.add(new DoubleSetting.Builder()
        .name("per-minute").description("About how many shooting stars a minute.").defaultValue(60).range(1, 600).sliderRange(10, 300).build());

    private final Setting<Boolean> showers = sg.add(new BoolSetting.Builder()
        .name("meteor-showers").description("Every so often a burst of loads at once.").defaultValue(true).build());

    private final Setting<Boolean> nightOnly = sg.add(new BoolSetting.Builder()
        .name("night-only").description("Only at night (and in the End).").defaultValue(true).build());

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colours of the Classic and Neon stars (Comets and Fireballs stay fiery, Rainbow stays rainbow).").defaultValue(FxTheme.Frost).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Custom theme colour.").defaultValue(new SettingColor(170, 210, 255, 230)).visible(() -> theme.get() == FxTheme.Custom).build());

    private record Star(Vec3d start, Vec3d dir, double speed, double length, double width, double born, double life, Style style, double hue) {}

    private final List<Star> stars = new ArrayList<>();
    private final Random rng = new Random();
    private double last, showerUntil;

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> stardust = feature("stardust", "Twinkles left hanging along each star's path.");
    private final Setting<Boolean> flare = feature("star-flare", "A bright pointed flare on each star's head.");

    public ShootingStars() {
        super("shooting-stars", "Shooting stars in six styles - classic streaks, fiery comets, fireballs, neon and rainbow - with meteor showers. Only you see them.");
    }

    @Override
    public void onDeactivate() {
        stars.clear();
    }

    private boolean dark() {
        if (!nightOnly.get()) return true;
        if (mc.world.getRegistryKey() == net.minecraft.world.World.END) return true;
        long t = mc.world.getTimeOfDay() % 24000;
        return t > 12800 && t < 23200;
    }

    // colour at a point along the tail (u = 0 at the head, 1 at the tip)
    private Color tail(Star s, double u, int alpha) {
        return switch (s.style()) {
            case Comet -> new Color(255, (int) (230 - 110 * u), (int) (150 - 120 * u), alpha);
            case Fireball -> new Color(255, (int) (200 - 160 * u), (int) (60 - 50 * u), alpha);
            case Rainbow -> Fx.hue(s.hue() + u * 0.8, alpha);
            default -> theme.get().color(s.hue() + u * 0.3, alpha, color.get());
        };
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.world == null) return;
        double now = Fx.time(), dt = Math.min(0.2, now - last);
        last = now;
        Vec3d eye = mc.gameRenderer.getCamera().getCameraPos();
        // far past anything loaded, and depth-tested, so terrain and clouds stay in front
        double dist = Math.max(160, mc.options.getClampedViewDistance() * 16 + 40);

        if (dark()) {
            if (showers.get() && now > showerUntil + 40 && rng.nextDouble() < dt / 60) showerUntil = now + 8;
            double rate = perMinute.get() / 60 * (now < showerUntil ? 10 : 1);
            int n = (int) (rate * dt) + (rng.nextDouble() < rate * dt % 1 ? 1 : 0);
            for (int i = 0; i < n; i++) spawn(now, dist);
        }

        Renderer3D r = event.depthRenderer;
        for (Iterator<Star> it = stars.iterator(); it.hasNext(); ) {
            Star s = it.next();
            double age = now - s.born();
            if (age > s.life()) {
                it.remove();
                continue;
            }
            double f = age / s.life(), fade = Math.sin(f * Math.PI);
            Vec3d head = eye.add(s.start()).add(s.dir().multiply(s.speed() * age));
            Vec3d tipEnd = head.subtract(s.dir().multiply(s.length() * (0.4 + 0.6 * Math.min(1, f * 3))));
            Vec3d side = s.dir().crossProduct(head.subtract(eye)).normalize();
            int segs = detail(16, 6);
            for (int i = 0; i < segs; i++) {
                double u0 = (double) i / segs, u1 = (double) (i + 1) / segs;
                Vec3d p0 = head.lerp(tipEnd, u0), p1 = head.lerp(tipEnd, u1);
                double w0 = s.width() * (1 - u0), w1 = s.width() * (1 - u1);
                Color c0 = tail(s, u0, (int) (230 * fade * (1 - u0))), c1 = tail(s, u1, (int) (230 * fade * (1 - u1)));
                Color e0 = Fx.alpha(c0, 0), e1 = Fx.alpha(c1, 0);
                Vec3d a0 = p0.add(side.multiply(w0)), b0 = p0.subtract(side.multiply(w0)), a1 = p1.add(side.multiply(w1)), b1 = p1.subtract(side.multiply(w1));
                // bright down the middle, soft at the edges
                r.quad(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, a1.x, a1.y, a1.z, a0.x, a0.y, a0.z, c0, c1, e1, e0);
                r.quad(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, b1.x, b1.y, b1.z, b0.x, b0.y, b0.z, c0, c1, e1, e0);
                if (s.style() == Style.Neon) {
                    Color core = new Color(255, 255, 255, (int) (240 * fade * (1 - u0)));
                    Vec3d k0 = side.multiply(w0 * 0.18), k1 = side.multiply(w1 * 0.18);
                    r.quad(p0.x - k0.x, p0.y - k0.y, p0.z - k0.z, p1.x - k1.x, p1.y - k1.y, p1.z - k1.z, p1.x + k1.x, p1.y + k1.y, p1.z + k1.z, p0.x + k0.x, p0.y + k0.y, p0.z + k0.z, core, core, core, core);
                }
            }
            double scale = dist / 110;
            if ((s.style() == Style.Comet || s.style() == Style.Fireball) && !low()) {
                // sparks breaking off and falling behind
                for (int k = 0; k < 6; k++) {
                    double life = (now * 2.5 + k * 0.17 + s.born()) % 1;
                    Vec3d at = head.lerp(tipEnd, life * 0.7).add(side.multiply(Math.sin(k * 3.7 + s.born() * 9) * s.width() * 1.5 * life)).add(0, -life * life * 3 * scale, 0);
                    spark(r, at, 0.5 * scale * (1 - life), tail(s, life, (int) (230 * fade * (1 - life))), true);
                }
            }
            if (on(stardust)) for (int k = 1; k <= 4; k++) {
                Vec3d dust = head.lerp(tipEnd, k / 5.0).add(side.multiply(Math.sin(k * 7.3 + s.born()) * 0.6 * scale));
                double tw = 0.5 + 0.5 * Math.sin(now * 20 + k * 3.1);
                spark(r, dust, 0.45 * scale * tw, tail(s, k / 5.0, (int) (180 * fade * tw * (1 - k / 5.0))), true);
            }
            if (on(flare)) spark(r, head, (s.style() == Style.Comet ? 5 : 2.8) * scale, new Color(255, 255, 255, (int) (230 * fade)), true);
        }
    }

    private void spawn(double now, double dist) {
        double yaw = rng.nextDouble() * Math.PI * 2, elev = Math.toRadians(20 + rng.nextDouble() * 50);
        Vec3d start = new Vec3d(Math.cos(yaw) * Math.cos(elev), Math.sin(elev), Math.sin(yaw) * Math.cos(elev)).multiply(dist);
        double heading = yaw + Math.PI / 2 + (rng.nextDouble() - 0.5);
        Vec3d dir = new Vec3d(Math.cos(heading), -0.25 - rng.nextDouble() * 0.35, Math.sin(heading)).normalize();
        Style st = style.get();
        if (st == Style.Mixed) {
            int roll = rng.nextInt(20);
            st = roll == 0 ? Style.Comet : roll < 3 ? Style.Fireball : roll < 7 ? Style.Neon : roll < 10 ? Style.Rainbow : Style.Classic;
        }
        double scale = dist / 110 * 1.4, hue = rng.nextDouble();
        switch (st) {
            case Comet -> stars.add(new Star(start, dir, (18 + rng.nextDouble() * 8) * scale, (55 + rng.nextDouble() * 20) * scale, 1.6 * scale, now, 3 + rng.nextDouble() * 1.5, st, hue));
            case Fireball -> stars.add(new Star(start, dir, (35 + rng.nextDouble() * 20) * scale, (30 + rng.nextDouble() * 15) * scale, 1.0 * scale, now, 1.5 + rng.nextDouble(), st, hue));
            case Neon -> stars.add(new Star(start, dir, (55 + rng.nextDouble() * 50) * scale, (22 + rng.nextDouble() * 20) * scale, 0.7 * scale, now, 0.8 + rng.nextDouble() * 0.8, st, hue));
            default -> stars.add(new Star(start, dir, (60 + rng.nextDouble() * 60) * scale, (18 + rng.nextDouble() * 22) * scale, 0.45 * scale, now, 0.7 + rng.nextDouble() * 0.8, st, hue));
        }
    }
}
