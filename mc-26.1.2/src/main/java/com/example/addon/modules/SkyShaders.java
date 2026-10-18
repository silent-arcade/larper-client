package com.example.addon.modules;

import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import com.example.addon.visuals.VisualModule;
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
import java.util.List;
import java.util.function.DoubleUnaryOperator;

public class SkyShaders extends VisualModule {
    public enum Style { Glow, Nebula, Galaxy, Aurora, Vortex, All }
    public enum Quality { Low, Medium, High, Ultra }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Glow: the whole sky softly shifts colour. Nebula: waves of colour rippling across the sky. Galaxy: a glowing star band arching overhead. Aurora: northern-lights ribbons waving all round you. Vortex: spiral arms swirling round the top of the sky. All: everything at once.")
        .defaultValue(Style.All).build());

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colours of the sky. The Shader Hands module can make your hand follow these.").defaultValue(FxTheme.Void).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("custom-color").description("Custom theme colour.").defaultValue(new SettingColor(150, 90, 255, 255)).visible(() -> theme.get() == FxTheme.Custom).build());

    private final Setting<Double> strength = sg.add(new DoubleSetting.Builder()
        .name("strength").description("How strong the sky colours are.").defaultValue(1).range(0.1, 3).sliderRange(0.2, 2).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("speed").description("How fast everything drifts and shifts.").defaultValue(1).range(0, 5).sliderRange(0, 3).build());

    private final Setting<Quality> quality = sg.add(new EnumSetting.Builder<Quality>()
        .name("quality").description("How finely the sky is drawn. Higher = smoother colour blending but more work for your PC. Ultra is silky smooth.").defaultValue(Quality.High).build());

    private final Setting<Boolean> nightOnly = sg.add(new BoolSetting.Builder()
        .name("brighter-at-night").description("Faint in the day, full strength at night.").defaultValue(true).build());

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> stars = feature("twinkling-stars", "Hundreds of tiny stars twinkling across the sky.");
    private final Setting<Boolean> horizon = feature("horizon-glow", "A glowing band of colour all along the horizon.");
    private final Setting<Boolean> storm = feature("thunderstorm", "Branching lightning flashes across the distant sky, lighting it up, followed by a rumble of thunder.");

    private final Setting<Double> stormEvery = sg.add(new DoubleSetting.Builder()
        .name("lightning-every").description("Average seconds between lightning strikes.").defaultValue(6).range(1, 60).sliderRange(2, 20).build());

    private final Setting<Boolean> thunderSound = sg.add(new BoolSetting.Builder()
        .name("thunder-sound").description("A rumble of thunder a moment after each strike (only you hear it).").defaultValue(true).build());

    // a strike: its jagged path as directions on the sky (so it stays put as you move), when it hit, and when its thunder plays
    private record Bolt(java.util.List<Vec3> path, java.util.List<java.util.List<Vec3>> forks, double start, double thunderAt) {}

    private final java.util.List<Bolt> bolts = new java.util.ArrayList<>();
    private final java.util.Random rng = new java.util.Random();
    private double nextStrike;

    public SkyShaders() {
        super("sky-shaders", "Shader-style sky: a colour glow that shifts across the whole sky, drifting nebula clouds and a glowing galaxy band, in any theme. Only you see it.");
    }

    // the colour the sky is showing right now, for Hand Aura to follow
    public int currentRgb() {
        return colorAt(Fx.time() * 0.05 * speed.get());
    }

    // a colour from the sky's theme, for Shader Hands
    public int colorAt(double t) {
        return theme.get().color(t, 255, color.get()).getPacked() & 0xFFFFFF;
    }

    private double level() {
        if (!nightOnly.get() || mc.level == null) return strength.get();
        long t = mc.level.getOverworldClockTime() % 24000;
        double night = t > 13000 && t < 23000 ? 1 : t > 12000 && t <= 13000 ? (t - 12000) / 1000.0 : t >= 23000 ? (24000 - t) / 1000.0 : 0;
        return strength.get() * (0.3 + 0.7 * night);
    }

    // grid density for the chosen quality (low-end-pc still divides it down)
    private int q(int high) {
        double k = switch (quality.get()) { case Low -> 0.35; case Medium -> 0.65; case High -> 1; case Ultra -> 1.5; };
        return detail(Math.max(4, (int) Math.round(high * k)), 4);
    }

    private Color col(double t, double alpha) {
        return theme.get().color(t, (int) Math.max(0, Math.min(255, alpha)), color.get());
    }

    // a point on a dome round the camera, past anything that's loaded, so terrain always covers it
    private static Vec3 dome(Vec3 cam, double r, double azimuth, double elevation) {
        double ce = Math.cos(elevation);
        return cam.add(Math.cos(azimuth) * ce * r, Math.sin(elevation) * r, Math.sin(azimuth) * ce * r);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || !mc.level.dimensionType().hasSkyLight()) return;
        Vec3 cam = mc.gameRenderer.getMainCamera().position();
        double r = Math.max(180, mc.options.getEffectiveRenderDistance() * 16 + 64), t = Fx.time() * speed.get(), lv = level();
        Style s = style.get();
        if (s == Style.Glow || s == Style.All) skyGlow(event, cam, r, t, lv);
        if (s == Style.Nebula || s == Style.All) nebula(event, cam, r * 0.97, t, lv);
        if (s == Style.Galaxy || s == Style.All) galaxy(event, cam, r * 0.95, t, lv);
        if (s == Style.Aurora || s == Style.All) aurora(event, cam, r * 0.9, t, lv);
        if (s == Style.Vortex || s == Style.All) vortex(event, cam, r * 0.96, t, lv);
        if (on(horizon)) horizon(event, cam, r * 0.98, t, lv);
        if (on(storm)) thunderstorm(event, cam, r * 0.92, lv);
        if (on(stars)) stars(event, cam, r * 0.93, t, lv);
    }

    private double flash() {
        double now = Fx.time(), f = 0;
        for (Bolt b : bolts) {
            double age = now - b.start();
            if (age >= 0 && age < 0.45) f = Math.max(f, (1 - age / 0.45) * (age < 0.08 || age > 0.16 ? 1 : 0.35));
        }
        return f;
    }

    private void skyGlow(Render3DEvent event, Vec3 cam, double r, double t, double lv) {
        lv *= 1 + 3 * flash();
        // fine grid: colours blend in straight lines across each square, so coarse squares show creases
        int lon = q(240), lat = q(60);
        for (int j = 0; j < lat; j++) {
            double e0 = Math.toRadians(j * 90.0 / lat), e1 = Math.toRadians((j + 1) * 90.0 / lat);
            // fades out completely overhead so the top of the dome doesn't show a fan
            double a0 = 70 * lv * Math.pow(1 - j / (double) lat, 1.4), a1 = 70 * lv * Math.pow(1 - (j + 1) / (double) lat, 1.4);
            for (int i = 0; i < lon; i++) {
                double z0 = Math.PI * 2 * i / lon, z1 = Math.PI * 2 * (i + 1) / lon;
                double u0 = i / (double) lon, u1 = (i + 1) / (double) lon;
                Vec3 p00 = dome(cam, r, z0, e0), p10 = dome(cam, r, z1, e0), p11 = dome(cam, r, z1, e1), p01 = dome(cam, r, z0, e1);
                Color c00 = col(u0 + j * 0.03 + t * 0.02, a0), c10 = col(u1 + j * 0.03 + t * 0.02, a0);
                Color c11 = col(u1 + (j + 1) * 0.03 + t * 0.02, a1), c01 = col(u0 + (j + 1) * 0.03 + t * 0.02, a1);
                event.depthRenderer.quad(p00.x, p00.y, p00.z, p10.x, p10.y, p10.z, p11.x, p11.y, p11.z, p01.x, p01.y, p01.z, c00, c10, c11, c01);
            }
        }
    }

    // flowing waves of colour across the whole sky, like a noise shader (no round shapes)
    private void nebula(Render3DEvent event, Vec3 cam, double r, double t, double lv) {
        int lon = q(240), lat = q(60);
        double[][] alpha = new double[lon + 1][lat + 1];
        for (int i = 0; i <= lon; i++) for (int j = 0; j <= lat; j++) {
            double az = Math.PI * 2 * i / lon, el = Math.toRadians(4 + 82.0 * j / lat);
            double wave = 0.5 + 0.5 * Math.sin(az * 2 + t * 0.08 + 1.5 * Math.sin(el * 2.5 + t * 0.05));
            double ripple = 0.5 + 0.5 * Math.sin(el * 3 - az + t * 0.04);
            double k = wave * ripple;
            alpha[i][j] = 120 * lv * k * k * (3 - 2 * k) * (1 - el / (Math.PI / 2));
        }
        for (int i = 0; i < lon; i++) for (int j = 0; j < lat; j++) {
            double z0 = Math.PI * 2 * i / lon, z1 = Math.PI * 2 * (i + 1) / lon;
            double e0 = Math.toRadians(4 + 82.0 * j / lat), e1 = Math.toRadians(4 + 82.0 * (j + 1) / lat);
            Vec3 p00 = dome(cam, r, z0, e0), p10 = dome(cam, r, z1, e0), p11 = dome(cam, r, z1, e1), p01 = dome(cam, r, z0, e1);
            double h0 = i / (double) lon, h1 = (i + 1) / (double) lon;
            event.depthRenderer.quad(p00.x, p00.y, p00.z, p10.x, p10.y, p10.z, p11.x, p11.y, p11.z, p01.x, p01.y, p01.z,
                col(h0 + j * 0.025 + t * 0.015, alpha[i][j]), col(h1 + j * 0.025 + t * 0.015, alpha[i + 1][j]),
                col(h1 + (j + 1) * 0.025 + t * 0.015, alpha[i + 1][j + 1]), col(h0 + (j + 1) * 0.025 + t * 0.015, alpha[i][j + 1]));
        }
    }

    // A band between two edges, cut into thin rows so colours blend smoothly (one big quad per segment shows
    // zig-zag stripes because each of its two triangles blends differently). profile = brightness across the band.
    private void band(Render3DEvent e, List<Vec3> lo, List<Vec3> hi, List<Color> loC, List<Color> hiC, int rows, DoubleUnaryOperator profile) {
        for (int i = 0; i + 1 < lo.size(); i++) {
            for (int row = 0; row < rows; row++) {
                double v0 = row / (double) rows, v1 = (row + 1) / (double) rows;
                Vec3 p00 = lo.get(i).lerp(hi.get(i), v0), p10 = lo.get(i + 1).lerp(hi.get(i + 1), v0);
                Vec3 p11 = lo.get(i + 1).lerp(hi.get(i + 1), v1), p01 = lo.get(i).lerp(hi.get(i), v1);
                e.depthRenderer.quad(p00.x, p00.y, p00.z, p10.x, p10.y, p10.z, p11.x, p11.y, p11.z, p01.x, p01.y, p01.z,
                    mix(loC.get(i), hiC.get(i), v0, profile), mix(loC.get(i + 1), hiC.get(i + 1), v0, profile),
                    mix(loC.get(i + 1), hiC.get(i + 1), v1, profile), mix(loC.get(i), hiC.get(i), v1, profile));
            }
        }
    }

    private static Color mix(Color a, Color b, double v, DoubleUnaryOperator profile) {
        return new Color((int) (a.r + (b.r - a.r) * v), (int) (a.g + (b.g - a.g) * v), (int) (a.b + (b.b - a.b) * v),
            (int) Math.max(0, Math.min(255, a.a * profile.applyAsDouble(v))));
    }

    private Color second(double t, double alpha) {
        return theme.get().secondary(t, (int) Math.max(0, Math.min(255, alpha)), color.get());
    }

    // one smooth glowing band arching across the sky, bright in the middle and fading at the edges
    private void galaxy(Render3DEvent event, Vec3 cam, double r, double t, double lv) {
        int n = q(360);
        double tilt = Math.toRadians(35), spin = t * 0.004;
        Vec3 normal = new Vec3(0, Math.cos(tilt), -Math.sin(tilt));
        DoubleUnaryOperator profile = v -> Math.pow(Math.sin(Math.PI * v), 1.6);
        List<Vec3> lo = new ArrayList<>(), hi = new ArrayList<>();
        List<Color> cl = new ArrayList<>(), ch = new ArrayList<>();
        for (int k = 0; k <= n; k++) {
            double a = Math.PI * 2 * k / n + spin;
            Vec3 dir = new Vec3(Math.cos(a), Math.sin(a) * Math.sin(tilt), Math.sin(a) * Math.cos(tilt));
            if (dir.y < -0.1) {
                if (lo.size() > 1) band(event, lo, hi, cl, ch, q(8), profile);
                lo.clear();
                hi.clear();
                cl.clear();
                ch.clear();
                continue;
            }
            double width = r * (0.07 + 0.03 * Math.sin(a * 3 + t * 0.1));
            Vec3 c = cam.add(dir.scale(r));
            lo.add(c.subtract(normal.scale(width)));
            hi.add(c.add(normal.scale(width)));
            double glowK = 0.7 + 0.3 * Math.sin(a * 5 + t * 0.2);
            cl.add(col(k / (double) n + t * 0.02, 130 * lv * glowK));
            ch.add(second(k / (double) n + t * 0.02, 130 * lv * glowK));
            if (k % 3 == 0) {
                for (int j = 0; j < 2; j++) {
                    double h = ((k * 7 + j * 13) % 17 / 17.0 - 0.5) * 1.6;
                    double tw = 0.5 + 0.5 * Math.sin(t * 3 + k * 1.7 + j);
                    spark(event.depthRenderer, c.add(normal.scale(h * width)), r * 0.004, col(k * 0.05 + j * 0.3, 255 * Math.min(1, lv) * tw), true);
                }
            }
        }
        if (lo.size() > 1) band(event, lo, hi, cl, ch, q(8), profile);
    }

    // northern lights: three waving curtains, one colour at the bottom fading into a second colour at the top,
    // with bright rays shimmering along them
    private void aurora(Render3DEvent event, Vec3 cam, double r, double t, double lv) {
        int n = q(300);
        for (int ribbon = 0; ribbon < 3; ribbon++) {
            List<Vec3> lo = new ArrayList<>(), hi = new ArrayList<>();
            List<Color> cl = new ArrayList<>(), ch = new ArrayList<>();
            for (int k = 0; k <= n; k++) {
                double az = Math.PI * 2 * k / n;
                double base = Math.toRadians(14 + ribbon * 8 + 6 * Math.sin(az * 3 + t * 0.2 + ribbon * 1.7) + 3 * Math.sin(az * 7 - t * 0.35 + ribbon));
                double height = Math.toRadians(14 + 10 * (0.5 + 0.5 * Math.sin(az * 2 + t * 0.15 + ribbon * 2)));
                double rays = 0.55 + 0.45 * Math.pow(0.5 + 0.5 * Math.sin(az * 23 + t * 0.9 + ribbon * 3) * Math.sin(az * 5 - t * 0.3), 2);
                lo.add(dome(cam, r, az, base));
                hi.add(dome(cam, r, az, base + height));
                double hue = az / Math.PI + ribbon * 0.33 + t * 0.03;
                cl.add(col(hue, 170 * lv * rays));
                ch.add(second(hue, 170 * lv * rays));
            }
            band(event, lo, hi, cl, ch, q(16), v -> Math.pow(1 - v, 1.3) * (0.35 + 0.65 * Math.min(1, v * 6)));
        }
    }

    // spiral arms swirling round the top of the sky, bright down the middle of each arm
    private void vortex(Render3DEvent event, Vec3 cam, double r, double t, double lv) {
        int arms = 4, steps = q(140);
        for (int arm = 0; arm < arms; arm++) {
            List<Vec3> lo = new ArrayList<>(), hi = new ArrayList<>();
            List<Color> cl = new ArrayList<>(), ch = new ArrayList<>();
            for (int k = 0; k <= steps; k++) {
                double f = k / (double) steps;
                double el = Math.toRadians(36 + 52 * f);
                double az = arm * Math.PI * 2 / arms + f * 4.2 + t * 0.05;
                double w = 0.38 * (1 - f * 0.8);
                lo.add(dome(cam, r, az - w, el));
                hi.add(dome(cam, r, az + w, el));
                double fade = Math.sin(Math.PI * f), hue = f * 0.8 + arm * 0.25 + t * 0.03;
                cl.add(col(hue, 150 * lv * fade));
                ch.add(second(hue, 150 * lv * fade));
            }
            band(event, lo, hi, cl, ch, q(16), v -> Math.pow(Math.sin(Math.PI * v), 1.4));
        }
    }

    // a soft glowing band all along the horizon
    private void horizon(Render3DEvent event, Vec3 cam, double r, double t, double lv) {
        int n = q(200);
        List<Vec3> lo = new ArrayList<>(), hi = new ArrayList<>();
        List<Color> cl = new ArrayList<>(), ch = new ArrayList<>();
        for (int k = 0; k <= n; k++) {
            double az = Math.PI * 2 * k / n, hue = az / (Math.PI * 2) * 2 + t * 0.02;
            lo.add(dome(cam, r, az, Math.toRadians(-3)));
            hi.add(dome(cam, r, az, Math.toRadians(18 + 4 * Math.sin(az * 2 + t * 0.1))));
            cl.add(col(hue, 120 * lv));
            ch.add(second(hue, 120 * lv));
        }
        band(event, lo, hi, cl, ch, q(16), v -> Math.pow(1 - v, 2));
    }

    private Vec3 skyDir(double azimuth, double elevation) {
        double ce = Math.cos(elevation);
        return new Vec3(Math.cos(azimuth) * ce, Math.sin(elevation), Math.sin(azimuth) * ce);
    }

    private java.util.List<Vec3> jag(double az, double topEl, double botEl, int steps, double wiggle) {
        java.util.List<Vec3> out = new java.util.ArrayList<>();
        double a = az;
        for (int k = 0; k <= steps; k++) {
            double f = k / (double) steps;
            a += (rng.nextDouble() - 0.5) * wiggle;
            out.add(skyDir(a, topEl + (botEl - topEl) * f + (rng.nextDouble() - 0.5) * 0.01));
        }
        return out;
    }

    private void strike() {
        double az = rng.nextDouble() * Math.PI * 2, top = Math.toRadians(20 + rng.nextDouble() * 14), bot = Math.toRadians(1 + rng.nextDouble() * 4);
        java.util.List<Vec3> path = jag(az, top, bot, 14, 0.06);
        java.util.List<java.util.List<Vec3>> forks = new java.util.ArrayList<>();
        for (int f = 0; f < 1 + rng.nextInt(3); f++) {
            int from = 2 + rng.nextInt(8);
            Vec3 d = path.get(from);
            double fAz = Math.atan2(d.z, d.x) + (rng.nextBoolean() ? 1 : -1) * 0.04, fEl = Math.asin(d.y);
            forks.add(jag(fAz, fEl, fEl - Math.toRadians(6 + rng.nextDouble() * 10), 6, 0.08));
        }
        double now = Fx.time();
        bolts.add(new Bolt(path, forks, now, now + 0.6 + rng.nextDouble() * 2));
    }

    // jagged bolts on the far sky: a bright core with a soft glow round it, flickering twice, fading out
    private void thunderstorm(Render3DEvent event, Vec3 cam, double r, double lv) {
        double now = Fx.time();
        if (now > nextStrike) {
            if (nextStrike != 0) strike();
            nextStrike = now + stormEvery.get() * (0.4 + rng.nextDouble() * 1.2);
        }
        bolts.removeIf(b -> now - b.start() > 0.6 && now > b.thunderAt());
        for (Bolt b : bolts) {
            if (thunderSound.get() && b.thunderAt() > 0 && now >= b.thunderAt()) {
                mc.getSoundManager().play(net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(net.minecraft.sounds.SoundEvents.LIGHTNING_BOLT_THUNDER, 0.8f + rng.nextFloat() * 0.3f, 0.35f));
                bolts.set(bolts.indexOf(b), new Bolt(b.path(), b.forks(), b.start(), -1));
                break;
            }
        }
        Vec3 view = mc.gameRenderer.getMainCamera().position();
        for (Bolt b : bolts) {
            double age = now - b.start();
            if (age > 0.6) continue;
            double a = (1 - age / 0.6) * (age < 0.07 || age > 0.15 ? 1 : 0.3) * Math.min(1, lv + 0.4);
            boltLine(event, b.path(), cam, view, r, r * 0.006, a);
            for (var fork : b.forks()) boltLine(event, fork, cam, view, r, r * 0.004, a * 0.75);
        }
    }

    private void boltLine(Render3DEvent event, java.util.List<Vec3> dirs, Vec3 cam, Vec3 view, double r, double width, double a) {
        Color core = new Color(255, 255, 255, (int) (255 * a)), glowC = col(0.5, 170 * a), clear = Fx.alpha(glowC, 0);
        for (int k = 0; k + 1 < dirs.size(); k++) {
            Vec3 p0 = cam.add(dirs.get(k).scale(r)), p1 = cam.add(dirs.get(k + 1).scale(r));
            Vec3 along = p1.subtract(p0), side = along.cross(p0.subtract(view)).normalize();
            double taper = 1 - k / (double) dirs.size() * 0.5;
            // soft coloured glow either side, then the white-hot core
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Vec3 w = side.scale(width * 4 * taper * sgn);
                event.depthRenderer.quad(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p1.x + w.x, p1.y + w.y, p1.z + w.z, p0.x + w.x, p0.y + w.y, p0.z + w.z, glowC, glowC, clear, clear);
            }
            Vec3 c = side.scale(width * taper);
            event.depthRenderer.quad(p0.x - c.x, p0.y - c.y, p0.z - c.z, p1.x - c.x, p1.y - c.y, p1.z - c.z, p1.x + c.x, p1.y + c.y, p1.z + c.z, p0.x + c.x, p0.y + c.y, p0.z + c.z, core, core, core, core);
        }
    }

    private void stars(Render3DEvent event, Vec3 cam, double r, double t, double lv) {
        int n = detail(220, 60);
        for (int k = 0; k < n; k++) {
            double az = k * 2.39996, el = Math.asin(((k * 0.618034) % 1) * 0.98);
            double tw = Math.pow(0.5 + 0.5 * Math.sin(t * (1.5 + k % 5 * 0.4) + k), 3);
            spark(event.depthRenderer, dome(cam, r, az, el), r * (0.003 + 0.002 * (k % 3)), new Color(255, 255, 255, (int) Math.min(255, 230 * Math.min(1, lv) * tw)), true);
        }
    }
}
