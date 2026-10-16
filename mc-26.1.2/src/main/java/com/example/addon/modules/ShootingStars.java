package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class ShootingStars extends VisualModule {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> perMinute = sg.add(new DoubleSetting.Builder()
        .name("per-minute").description("About how many shooting stars a minute.").defaultValue(20).range(1, 300).sliderRange(5, 120).build());

    private final Setting<Boolean> showers = sg.add(new BoolSetting.Builder()
        .name("meteor-showers").description("Sometimes a burst of many at once.").defaultValue(true).build());

    private final Setting<Boolean> nightOnly = sg.add(new BoolSetting.Builder()
        .name("night-only").description("Only at night (and in the End).").defaultValue(true).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Every star a different colour.").defaultValue(false).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Tail colour (the head is near white).").defaultValue(new SettingColor(170, 210, 255, 230))
        .visible(() -> !rainbow.get()).build());

    private final Setting<Boolean> comets = sg.add(new BoolSetting.Builder()
        .name("comets").description("Now and then a big, slow, fiery comet instead of a shooting star.").defaultValue(true).build());

    private record Star(Vec3 start, Vec3 dir, double speed, double length, double born, double life, Color color, boolean comet) {}

    private final List<Star> stars = new ArrayList<>();
    private final Random rng = new Random();
    private double last, showerUntil;

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> stardust = feature("stardust", "Twinkles left hanging along each star's path.");
    private final Setting<Boolean> flare = feature("star-flare", "A cross-shaped flare on each star's head.");

    public ShootingStars() {
        super("shooting-stars", "Shooting stars, meteor showers and fiery comets across the sky. Only you see them.");
    }

    @Override
    public void onDeactivate() {
        stars.clear();
    }

    private boolean dark() {
        if (!nightOnly.get()) return true;
        if (mc.level.dimension() == net.minecraft.world.level.Level.END) return true;
        long t = mc.level.getOverworldClockTime() % 24000;
        return t > 12800 && t < 23200;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        double now = Fx.time(), dt = Math.min(0.2, now - last);
        last = now;
        Vec3 eye = mc.gameRenderer.getMainCamera().position();

        if (dark()) {
            if (showers.get() && now > showerUntil + 60 && rng.nextDouble() < dt / 90) showerUntil = now + 6;
            double rate = perMinute.get() / 60 * (now < showerUntil ? 12 : 1);
            if (rng.nextDouble() < rate * dt) spawn(eye, now);
        }

        for (Iterator<Star> it = stars.iterator(); it.hasNext(); ) {
            Star s = it.next();
            double age = now - s.born();
            if (age > s.life()) {
                it.remove();
                continue;
            }
            double f = age / s.life();
            double fade = Math.sin(f * Math.PI);
            Vec3 head = eye.add(s.start()).add(s.dir().scale(s.speed() * age));
            Vec3 tailEnd = head.subtract(s.dir().scale(s.length() * (0.4 + 0.6 * Math.min(1, f * 3))));
            Vec3 side = s.dir().cross(head.subtract(eye)).normalize();
            int segs = detail(12, 5);
            for (int i = 0; i < segs; i++) {
                double u0 = (double) i / segs, u1 = (double) (i + 1) / segs;
                Vec3 p0 = head.lerp(tailEnd, u0), p1 = head.lerp(tailEnd, u1);
                double wide = s.comet() ? 1.6 : 0.45, w0 = wide * (1 - u0), w1 = wide * (1 - u1);
                Color c0 = Fx.alpha(s.color(), (int) (s.color().a * fade * (1 - u0))), c1 = Fx.alpha(s.color(), (int) (s.color().a * fade * (1 - u1)));
                Vec3 a0 = p0.add(side.scale(w0)), b0 = p0.subtract(side.scale(w0)), a1 = p1.add(side.scale(w1)), b1 = p1.subtract(side.scale(w1));
                event.renderer.quad(a0.x, a0.y, a0.z, b0.x, b0.y, b0.z, b1.x, b1.y, b1.z, a1.x, a1.y, a1.z, c0, c0, c1, c1);
            }
            glow(event.renderer, head, s.comet() ? 4.5 : 1.6, Fx.alpha(s.color(), (int) (230 * fade)));
            if (on(stardust)) for (int k = 1; k <= 4; k++) {
                Vec3 dust = head.lerp(tailEnd, k / 5.0).add(side.scale(Math.sin(k * 7.3 + s.born()) * 0.6));
                double tw = 0.5 + 0.5 * Math.sin(now * 20 + k * 3.1);
                spark(event.renderer, dust, 0.5 * tw, Fx.alpha(s.color(), (int) (180 * fade * tw * (1 - k / 5.0))), true);
            }
            if (on(flare)) spark(event.renderer, head, 3.2, Fx.alpha(s.color(), (int) (210 * fade)), true);
        }
    }

    private void spawn(Vec3 eye, double now) {
        double yaw = rng.nextDouble() * Math.PI * 2, elev = Math.toRadians(25 + rng.nextDouble() * 50), dist = 110;
        Vec3 start = new Vec3(Math.cos(yaw) * Math.cos(elev), Math.sin(elev), Math.sin(yaw) * Math.cos(elev)).scale(dist);
        double heading = yaw + Math.PI / 2 + (rng.nextDouble() - 0.5);
        Vec3 dir = new Vec3(Math.cos(heading), -0.25 - rng.nextDouble() * 0.35, Math.sin(heading)).normalize();
        Color c = rainbow.get() ? Fx.hue(rng.nextDouble(), 230) : color.get();
        if (comets.get() && rng.nextInt(12) == 0) {
            Color fire = rainbow.get() ? c : new Color(255, 150 + rng.nextInt(60), 60, 240);
            stars.add(new Star(start, dir, 18 + rng.nextDouble() * 8, 55 + rng.nextDouble() * 20, now, 3 + rng.nextDouble() * 1.5, fire, true));
            return;
        }
        stars.add(new Star(start, dir, 60 + rng.nextDouble() * 60, 18 + rng.nextDouble() * 22, now, 0.7 + rng.nextDouble() * 0.8, c, false));
    }
}
