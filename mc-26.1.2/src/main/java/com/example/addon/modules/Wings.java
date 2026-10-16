package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
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
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import java.util.Random;

public class Wings extends VisualModule {
    public enum Style { Dragon, Mech, Inferno, Shadow, Angel, Butterfly, Energy }

    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgColor = settings.createGroup("Colors");

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Dragon, Mech, Inferno, Shadow, Angel (feathers), Butterfly or Energy (hologram).").defaultValue(Style.Dragon).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("size").description("Wingspan of one wing (blocks).").defaultValue(1.6).range(0.4, 4).sliderRange(0.6, 2.8).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("beat-speed").description("How fast they beat (faster in the air).").defaultValue(1).range(0.1, 4).sliderRange(0.2, 2.5).build());

    private final Setting<Boolean> particles = sg.add(new BoolSetting.Builder()
        .name("particles").description("Embers (Inferno) and smoke (Shadow) coming off the wings.").defaultValue(true)
        .visible(() -> style.get() == Style.Inferno || style.get() == Style.Shadow).build());

    private final Setting<Boolean> hideElytra = sg.add(new BoolSetting.Builder()
        .name("hide-elytra").description("Hide your elytra while the wings are on, so they don't clash.").defaultValue(true).build());

    private final Setting<Boolean> halo = sg.add(new BoolSetting.Builder()
        .name("halo").description("A halo over your head too.").defaultValue(false).build());

    private final Setting<Boolean> rainbow = sgColor.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow flowing across the wings.").defaultValue(false).build());

    private final Setting<Boolean> pulse = sgColor.add(new BoolSetting.Builder()
        .name("pulse").description("The glow breathes slowly.").defaultValue(true).build());

    private final Setting<SettingColor> dragonFill = colorSetting("dragon-membrane", "Dragon: membrane colour.", new SettingColor(55, 4, 8, 200), Style.Dragon);
    private final Setting<SettingColor> dragonEdge = colorSetting("dragon-edge", "Dragon: bones, claw and edge glow.", new SettingColor(235, 45, 25, 255), Style.Dragon);
    private final Setting<SettingColor> mechFill = colorSetting("mech-plates", "Mech: blade plating.", new SettingColor(135, 145, 160, 240), Style.Mech);
    private final Setting<SettingColor> mechEdge = colorSetting("mech-power", "Mech: glowing power lines.", new SettingColor(0, 230, 255, 255), Style.Mech);
    private final Setting<SettingColor> infernoCore = colorSetting("inferno-core", "Inferno: heat at the shoulder.", new SettingColor(255, 235, 140, 210), Style.Inferno);
    private final Setting<SettingColor> infernoTips = colorSetting("inferno-tips", "Inferno: flame tips.", new SettingColor(255, 40, 0, 190), Style.Inferno);
    private final Setting<SettingColor> shadowFill = colorSetting("shadow-body", "Shadow: wing colour.", new SettingColor(8, 4, 14, 215), Style.Shadow);
    private final Setting<SettingColor> shadowEdge = colorSetting("shadow-edge", "Shadow: glowing edge.", new SettingColor(150, 50, 255, 255), Style.Shadow);

    private final Setting<SettingColor> angelFill = colorSetting("angel-feathers", "Angel: feather colour.", new SettingColor(250, 250, 255, 225), Style.Angel);
    private final Setting<SettingColor> angelEdge = colorSetting("angel-glow", "Angel: golden edge glow.", new SettingColor(255, 210, 110, 255), Style.Angel);
    private final Setting<SettingColor> butterflyFill = colorSetting("butterfly-wings", "Butterfly: wing colour.", new SettingColor(30, 110, 255, 210), Style.Butterfly);
    private final Setting<SettingColor> butterflyEdge = colorSetting("butterfly-edge", "Butterfly: border, veins and spots.", new SettingColor(140, 230, 255, 255), Style.Butterfly);
    private final Setting<SettingColor> energyFill = colorSetting("energy-panels", "Energy: hologram panels.", new SettingColor(0, 255, 210, 55), Style.Energy);
    private final Setting<SettingColor> energyEdge = colorSetting("energy-lines", "Energy: hologram lines and nodes.", new SettingColor(0, 255, 230, 255), Style.Energy);

    private final Setting<SettingColor> haloColor = sgColor.add(new ColorSetting.Builder()
        .name("halo-color").description("Halo colour (white = gold).").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(halo::get).build());

    private Setting<SettingColor> colorSetting(String name, String description, SettingColor def, Style forStyle) {
        return sgColor.add(new ColorSetting.Builder().name(name).description(description).defaultValue(def)
            .visible(() -> !rainbow.get() && style.get() == forStyle).build());
    }

    private final Random rng = new Random();
    private double phase;
    private long last;

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> tipGlowOn = feature("tip-glow", "Pulsing glow on the wing tips, blade tips and nodes.");
    private final Setting<Boolean> edgeSparkles = feature("edge-sparkles", "Sparkles running along the wings' leading edge.");
    private final Setting<Boolean> detailOn = feature("extra-detail", "Extra layers: angel coverts, butterfly veins, inferno tongue glows.");

    public Wings() {
        super("wings", "Dragon, mech, fire, shadow, angel, butterfly or energy wings on your back (third person). Only you see them.");
    }

    private static final double[][] DRAGON = {
        {0, 0}, {0.1, 0.2}, {0.14, 0.32}, {0.2, 0.3}, {0.26, 0.46}, {0.32, 0.43}, {0.4, 0.6},
        {0.55, 0.58}, {0.62, 0.66}, {0.7, 0.57}, {0.86, 0.52}, {1.05, 0.4},
        {0.86, 0.26}, {0.8, 0.12}, {0.95, -0.12},
        {0.72, -0.06}, {0.62, -0.18}, {0.7, -0.46},
        {0.5, -0.3}, {0.4, -0.38}, {0.38, -0.62},
        {0.26, -0.42}, {0.15, -0.4}, {0.04, -0.32}};
    private static final int[] DRAGON_TIPS = {11, 14, 17, 20};
    private static final double[] DRAGON_WRIST = {0.4, 0.6};

    private static final double[][] SHADOW = {
        {0, 0}, {0.14, 0.3}, {0.34, 0.5}, {0.6, 0.56}, {0.82, 0.5}, {1.05, 0.36},
        {0.84, 0.28}, {0.98, 0.14}, {0.78, 0.1}, {0.92, -0.06}, {0.7, -0.04}, {0.8, -0.26},
        {0.58, -0.18}, {0.62, -0.44}, {0.46, -0.3}, {0.42, -0.58}, {0.3, -0.38}, {0.2, -0.56}, {0.12, -0.32}, {0.03, -0.38}};

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.options.getCameraType().isFirstPerson() || mc.player.isInvisible()) return;
        advance();

        float td = event.tickDelta;
        double yaw = Math.toRadians(Mth.rotLerp(td, mc.player.yBodyRotO, mc.player.yBodyRot));
        Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
        Vec3 up = new Vec3(0, 1, 0);
        boolean sneak = mc.player.isCrouching();
        float lean = mc.player.getSwimAmount(td);
        double tilt = mc.player.isFallFlying() ? 90 + mc.player.getViewXRot(td)
            : lean > 0 ? lean * (mc.player.isInWater() ? 90 + mc.player.getViewXRot(td) : 90) : 0;
        Vec3 root;
        if (tilt != 0) {
            double a = Math.toRadians(tilt);
            Vec3 bodyUp = up.scale(Math.cos(a)).add(forward.scale(Math.sin(a)));
            forward = forward.scale(Math.cos(a)).subtract(up.scale(Math.sin(a)));
            up = bodyUp;
            root = Fx.pos(mc.player, td).add(up.scale(1.8 * 0.76)).add(forward.scale(-0.26));
        } else {
            root = Fx.pos(mc.player, td).add(0, mc.player.getBbHeight() * (sneak ? 0.8 : 0.76), 0).add(forward.scale(sneak ? -0.12 : -0.26));
            if (sneak) up = up.add(forward.scale(0.45)).normalize();
        }
        Vec3 back = forward.scale(-1);
        double t = phase;

        for (int side = -1; side <= 1; side += 2) {
            Vec3 out = right.scale(side);
            double sweep, lift;
            switch (style.get()) {
                case Dragon -> { sweep = 22; lift = 16 * Math.sin(t * 2.6) + 4; }
                case Mech -> { sweep = 18; lift = 6 * Math.sin(t * 1.6) + 6; }
                case Inferno -> { sweep = 20; lift = 10 * Math.sin(t * 2.2) + 6; }
                case Angel -> { sweep = 18; lift = 18 * Math.sin(t * 2) + 8; }
                case Butterfly -> { sweep = 8; lift = 38 * Math.sin(t * 5); }
                case Energy -> { sweep = 24; lift = 5 * Math.sin(t * 1.2) + 4; }
                default -> { sweep = 26; lift = 12 * Math.sin(t * 2) + 2; }
            }
            double sw = Math.toRadians(sweep), li = Math.toRadians(lift);
            Vec3 span = out.scale(Math.cos(sw)).add(back.scale(Math.sin(sw)));
            Vec3 u = span.scale(Math.cos(li)).add(up.scale(Math.sin(li)));
            Vec3 v = up.scale(Math.cos(li)).subtract(span.scale(Math.sin(li)));
            double len = size.get();
            Frame f = new Frame(root.add(out.scale(0.1)), u.scale(len), v.scale(len), back);

            switch (style.get()) {
                case Dragon -> drawDragon(event.depthRenderer, f, t);
                case Mech -> drawMech(event.depthRenderer, f, t);
                case Inferno -> drawInferno(event.depthRenderer, f, t);
                case Shadow -> drawShadow(event.depthRenderer, f, t);
                case Angel -> drawAngel(event.depthRenderer, f, t);
                case Butterfly -> drawButterfly(event.depthRenderer, f, t);
                case Energy -> drawEnergy(event.depthRenderer, f, t);
            }
        }
    }

    private record Frame(Vec3 origin, Vec3 u, Vec3 v, Vec3 back) {
        Vec3 at(double x, double y) {
            return origin.add(u.scale(x)).add(v.scale(y));
        }

        Vec3 at(double[] p) {
            return at(p[0], p[1]);
        }
    }

    private void drawDragon(Renderer3D r, Frame f, double t) {
        Color fill = fill(t), edge = edge(t);
        fan(r, f, DRAGON, Fx.alpha(fill, (int) (fill.a * 0.75)), fill);
        outline(r, f, DRAGON, edge);
        Vec3 wrist = f.at(DRAGON_WRIST);
        bone(r, f.at(0, 0), wrist, f, 0.05, edge);
        for (int i : DRAGON_TIPS) bone(r, wrist, f.at(DRAGON[i]), f, 0.035, edge);
        tipGlow(r, f, new double[][] {DRAGON[11], DRAGON[14], DRAGON[17], DRAGON[20]}, edge, t);
        edgeSparks(r, f, DRAGON, 1, 11, edge, t);
        Vec3 c1 = f.at(0.44, 0.72), c2 = f.at(0.52, 0.76);
        r.quad(wrist.x, wrist.y, wrist.z, c1.x, c1.y, c1.z, c2.x, c2.y, c2.z, wrist.x, wrist.y, wrist.z, edge, edge, Fx.alpha(edge, 120), edge);
    }

    private void drawMech(Renderer3D r, Frame f, double t) {
        Color plate = fill(t), power = edge(t);
        double spread = 0.85 + 0.15 * Math.sin(t * 1.6);
        int blades = low() ? 3 : 5;
        for (int k = 0; k < blades; k++) {
            double ang = Math.toRadians((30 - k * 80.0 / (blades - 1)) * spread);
            double len = 1.05 - k * 0.12, w = 0.12 - k * 0.012;
            double dx = Math.cos(ang), dy = Math.sin(ang), nx = -dy, ny = dx;
            double bx = 0.06 * dx, by = 0.06 * dy;
            Vec3 a = f.at(bx + nx * w, by + ny * w), b = f.at(bx - nx * w, by - ny * w);
            Vec3 c = f.at(dx * len * 0.82 - nx * w * 0.7, dy * len * 0.82 - ny * w * 0.7);
            Vec3 tip = f.at(dx * len, dy * len);
            r.quad(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, tip.x, tip.y, tip.z, plate, plate, plate, Fx.alpha(plate, plate.a / 2));
            line(r, a, tip, power);
            line(r, b, c, Fx.alpha(power, power.a * 2 / 3));
            line(r, c, tip, power);
            line(r, f.at(dx * 0.1, dy * 0.1), f.at(dx * len * 0.9, dy * len * 0.9), power);
            if (on(tipGlowOn)) glow(r, tip, size.get() * 0.04 * (1 + 0.3 * Math.sin(t * 6 + k)), power);
        }
        glow(r, f.at(0.05, 0.02), size.get() * 0.09, power);
    }

    private void drawInferno(Renderer3D r, Frame f, double t) {
        int tongues = 9;
        double[][] pts = new double[tongues * 2 + 1][];
        pts[0] = new double[]{0, 0};
        for (int i = 0; i < tongues; i++) {
            double a0 = Math.toRadians(70 - 120.0 * i / (tongues - 1)), a1 = Math.toRadians(70 - 120.0 * (i + 0.5) / (tongues - 1));
            double flick = 0.08 * Math.sin(t * 11 + i * 1.7) + 0.05 * Math.sin(t * 17 + i * 3.1);
            double tipLen = (i == 2 || i == 3 ? 1.05 : 0.95 - Math.abs(i - 2.5) * 0.06) + flick;
            pts[i * 2 + 1] = new double[]{Math.cos(a0) * tipLen, Math.sin(a0) * tipLen};
            pts[i * 2 + 2] = new double[]{Math.cos(a1) * tipLen * 0.62, Math.sin(a1) * tipLen * 0.62};
        }
        Color core = rainbow.get() ? Fx.hue(Fx.time() * 0.1, 220) : glow(infernoCore.get(), t), tips = fill(t);
        fan(r, f, pts, core, tips);
        outline(r, f, pts, Fx.alpha(edge(t), 200));
        if (on(detailOn)) {
            double[][] tipPts = new double[tongues][];
            for (int i = 0; i < tongues; i++) tipPts[i] = pts[i * 2 + 1];
            tipGlow(r, f, tipPts, tips, t);
        }
        if (particles.get() && !low() && rng.nextInt(3) == 0) {
            double[] p = pts[1 + rng.nextInt(tongues) * 2];
            Vec3 e = f.at(p);
            mc.particleEngine.createParticle(rng.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, e.x, e.y, e.z, 0.0, 0.03, 0.0);
        }
    }

    private void drawShadow(Renderer3D r, Frame f, double t) {
        Color fill = fill(t), edge = edge(t);
        fan(r, f, SHADOW, fill, Fx.alpha(fill, (int) (fill.a * 0.85)));
        outline(r, f, SHADOW, edge);
        tipGlow(r, f, new double[][] {SHADOW[5], SHADOW[7], SHADOW[9], SHADOW[11], SHADOW[13], SHADOW[15], SHADOW[17]}, edge, t);
        edgeSparks(r, f, SHADOW, 1, 5, edge, t);
        if (particles.get() && !low() && rng.nextInt(4) == 0) {
            Vec3 e = f.at(SHADOW[1 + rng.nextInt(SHADOW.length - 1)]);
            mc.particleEngine.createParticle(ParticleTypes.SMOKE, e.x, e.y, e.z, 0.0, 0.01, 0.0);
        }
    }

    private void drawAngel(Renderer3D r, Frame f, double t) {
        Color fill = fill(t), edge = edge(t);
        int primaries = low() ? 6 : 11;
        for (int i = 0; i < primaries; i++) {
            double k = (double) i / (primaries - 1);
            double ang = Math.toRadians(-95 + k * 55 + 3 * Math.sin(t * 3 + i)), len = 0.45 + k * 0.5;
            feather(r, f, armU(k), armV(k), ang, len, 0.075, fill, edge, k);
        }
        if (on(detailOn)) for (int i = 0; i < 8; i++) {
            double k = i / 7.0;
            feather(r, f, 0.05 + k * 0.7, 0.5 + 0.12 * Math.sin(k * Math.PI), Math.toRadians(-100 + k * 45), 0.22 + k * 0.12, 0.06, Fx.alpha(fill, 245), edge, k);
        }
        for (int i = 0; i < 10; i++) line(r, f.at(armU(i / 10.0), armV(i / 10.0)), f.at(armU((i + 1) / 10.0), armV((i + 1) / 10.0)), edge);
        double tipAng = Math.toRadians(-40);
        tipGlow(r, f, new double[][] {{armU(1) + Math.cos(tipAng) * 0.95, armV(1) + Math.sin(tipAng) * 0.95}}, edge, t);
    }

    private static double armU(double k) {
        return 0.08 + k * 0.82;
    }

    private static double armV(double k) {
        return 0.42 + 0.18 * Math.sin(k * Math.PI) - k * 0.1;
    }

    private void feather(Renderer3D r, Frame f, double bu, double bv, double ang, double len, double w, Color fill, Color edge, double along) {
        double dx = Math.cos(ang), dy = Math.sin(ang), nx = -dy, ny = dx;
        Vec3 a = f.at(bu + nx * w, bv + ny * w), b = f.at(bu - nx * w, bv - ny * w);
        Vec3 c = f.at(bu + dx * len * 0.75 - nx * w * 0.8, bv + dy * len * 0.75 - ny * w * 0.8);
        Vec3 d = f.at(bu + dx * len * 0.75 + nx * w * 0.8, bv + dy * len * 0.75 + ny * w * 0.8);
        Vec3 tip = f.at(bu + dx * len, bv + dy * len);
        Color fc = rainbowAlong(fill, along), soft = Fx.alpha(fc, fc.a * 3 / 4), faint = Fx.alpha(fc, fc.a / 3);
        r.quad(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, d.x, d.y, d.z, fc, fc, soft, soft);
        r.quad(d.x, d.y, d.z, c.x, c.y, c.z, tip.x, tip.y, tip.z, tip.x, tip.y, tip.z, soft, soft, faint, faint);
        line(r, f.at(bu, bv), tip, Fx.alpha(edge, edge.a * 2 / 3));
    }

    private static final double[][] BUTTERFLY_UP = {{0, 0}, {0.12, 0.3}, {0.32, 0.62}, {0.62, 0.8}, {0.92, 0.72}, {1.02, 0.45}, {0.82, 0.18}, {0.45, 0.04}};
    private static final double[][] BUTTERFLY_LOW = {{0, 0}, {0.35, -0.04}, {0.62, -0.22}, {0.66, -0.5}, {0.48, -0.72}, {0.24, -0.62}, {0.06, -0.3}};

    private void drawButterfly(Renderer3D r, Frame f, double t) {
        Color fill = fill(t), edge = edge(t);
        for (double[][] lobe : new double[][][] {BUTTERFLY_UP, BUTTERFLY_LOW}) {
            fan(r, f, lobe, Fx.alpha(fill, Math.min(255, fill.a + 30)), Fx.alpha(fill, fill.a * 2 / 3));
            outline(r, f, lobe, edge);
            if (on(detailOn)) for (int i = 2; i < lobe.length - 1; i++) line(r, f.at(0, 0), f.at(lobe[i]), Fx.alpha(edge, edge.a / 3));
        }
        double pulse = 1 + 0.25 * Math.sin(t * 4);
        glow(r, f.at(0.64, 0.5), size.get() * 0.07 * pulse, edge);
        glow(r, f.at(0.4, -0.42), size.get() * 0.05 * pulse, edge);
        tipGlow(r, f, new double[][] {BUTTERFLY_UP[4], BUTTERFLY_LOW[4]}, edge, t);
    }

    private static final double[][] ENERGY = {{0, 0}, {0.18, 0.42}, {0.5, 0.62}, {0.85, 0.55}, {1.05, 0.3}, {0.95, 0.02}, {0.78, -0.22}, {0.52, -0.4}, {0.24, -0.38}};
    private static final double[] ENERGY_RINGS = {0.35, 0.7, 1};

    private void drawEnergy(Renderer3D r, Frame f, double t) {
        Color panel = fill(t), lines = edge(t);
        int n = ENERGY.length;
        for (int i = 1; i < n - 1; i++) {
            double[] a = ENERGY[i], b = ENERGY[i + 1];
            for (int k = 0; k < ENERGY_RINGS.length; k++) {
                double r0 = k == 0 ? 0 : ENERGY_RINGS[k - 1], r1 = ENERGY_RINGS[k];
                Vec3 p0 = f.at(a[0] * r0, a[1] * r0), p1 = f.at(a[0] * r1, a[1] * r1), p2 = f.at(b[0] * r1, b[1] * r1), p3 = f.at(b[0] * r0, b[1] * r0);
                double flick = 0.5 + 0.5 * Math.sin(t * 6 + i * 2.1 + k * 3.7);
                Color pc = Fx.alpha(rainbowAlong(panel, (double) i / n), (int) (panel.a * (0.5 + flick)));
                r.quad(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, p2.x, p2.y, p2.z, p3.x, p3.y, p3.z, pc);
                line(r, p1, p2, Fx.alpha(lines, lines.a * 2 / 3));
            }
        }
        for (int i = 1; i < n; i++) {
            line(r, f.at(0, 0), f.at(ENERGY[i]), lines);
            if (on(tipGlowOn)) for (double k : ENERGY_RINGS) glow(r, f.at(ENERGY[i][0] * k, ENERGY[i][1] * k), size.get() * 0.022, lines);
        }
        double sweep = (t * 0.6) % 1;
        for (int i = 1; i < n - 1; i++) {
            line(r, f.at(ENERGY[i][0] * sweep, ENERGY[i][1] * sweep), f.at(ENERGY[i + 1][0] * sweep, ENERGY[i + 1][1] * sweep), Fx.alpha(lines, (int) (255 * (1 - sweep * 0.6))));
        }
        glow(r, f.at(0.02, 0), size.get() * 0.08, lines);
    }

    private void tipGlow(Renderer3D r, Frame f, double[][] tipPts, Color c, double t) {
        if (!on(tipGlowOn)) return;
        for (int i = 0; i < tipPts.length; i++) glow(r, f.at(tipPts[i]), size.get() * 0.05 * (1 + 0.35 * Math.sin(t * 4 + i * 1.3)), c);
    }

    private void edgeSparks(Renderer3D r, Frame f, double[][] pts, int a, int b, Color c, double t) {
        if (!on(edgeSparkles)) return;
        for (int k = 0; k < 2; k++) {
            double pos = ((t * 0.45 + k * 0.5) % 1) * (b - a);
            int i = a + (int) pos;
            Vec3 p = f.at(pts[i]).lerp(f.at(pts[Math.min(b, i + 1)]), pos - Math.floor(pos));
            spark(r, p, size.get() * 0.09, Fx.alpha(c, 255), false);
        }
    }

    private void fan(Renderer3D r, Frame f, double[][] pts, Color rootColor, Color edgeColor) {
        Vec3 o = f.at(0, 0);
        int n = pts.length;
        for (int i = 0; i < n; i++) {
            Vec3 pa = f.at(pts[i]), pb = f.at(pts[(i + 1) % n]);
            Color ca = rainbowAlong(edgeColor, (double) i / n), cb = rainbowAlong(edgeColor, (double) (i + 1) / n);
            r.quad(o.x, o.y, o.z, pa.x, pa.y, pa.z, pb.x, pb.y, pb.z, o.x, o.y, o.z, rootColor, ca, cb, rootColor);
        }
    }

    private void outline(Renderer3D r, Frame f, double[][] pts, Color c) {
        int n = pts.length;
        for (int i = 0; i < n; i++) line(r, f.at(pts[i]), f.at(pts[(i + 1) % n]), rainbowAlong(c, (double) i / n));
    }

    private void bone(Renderer3D r, Vec3 a, Vec3 b, Frame f, double width, Color c) {
        Vec3 dir = b.subtract(a);
        Vec3 side = dir.cross(f.back()).normalize().scale(width * size.get() * 0.5);
        Vec3 a0 = a.add(side), a1 = a.subtract(side), b0 = b.add(side.scale(0.3)), b1 = b.subtract(side.scale(0.3));
        r.quad(a0.x, a0.y, a0.z, a1.x, a1.y, a1.z, b1.x, b1.y, b1.z, b0.x, b0.y, b0.z, c, c, c, c);
    }

    private void line(Renderer3D r, Vec3 a, Vec3 b, Color c) {
        r.line(a.x, a.y, a.z, b.x, b.y, b.z, c);
    }

    private Color glow(Color c, double t) {
        double k = pulse.get() ? 0.85 + 0.15 * Math.sin(t * 1.7) : 1;
        return Fx.alpha(c, (int) (c.a * k));
    }

    private Color fill(double t) {
        if (rainbow.get()) return Fx.hue(Fx.time() * 0.1, 170);
        return glow(switch (style.get()) {
            case Dragon -> dragonFill.get();
            case Mech -> mechFill.get();
            case Inferno -> infernoTips.get();
            case Shadow -> shadowFill.get();
            case Angel -> angelFill.get();
            case Butterfly -> butterflyFill.get();
            case Energy -> energyFill.get();
        }, t);
    }

    private Color edge(double t) {
        if (rainbow.get()) return Fx.hue(Fx.time() * 0.1 + 0.15, 255);
        return glow(switch (style.get()) {
            case Dragon -> dragonEdge.get();
            case Mech -> mechEdge.get();
            case Inferno -> infernoTips.get();
            case Shadow -> shadowEdge.get();
            case Angel -> angelEdge.get();
            case Butterfly -> butterflyEdge.get();
            case Energy -> energyEdge.get();
        }, t);
    }

    private Color rainbowAlong(Color c, double along) {
        return rainbow.get() ? Fx.hue(Fx.time() * 0.1 + along * 0.5, c.a) : c;
    }

    private void advance() {
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        boolean air = mc.player != null && !mc.player.onGround();
        phase += dt * speed.get() * (air ? 2 : 1);
    }

    public float time() {
        return (float) phase;
    }

    public boolean hidesElytra() {
        return isActive() && hideElytra.get();
    }

    public boolean halo() {
        return halo.get();
    }

    public int haloColor() {
        return haloColor.get().getPacked() | 0xFF000000;
    }
}
