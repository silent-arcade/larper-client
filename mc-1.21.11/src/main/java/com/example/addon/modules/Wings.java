package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.Renderer3D;
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
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;

/**
 * Wings drawn on your back (third person), built from light rather than a model:
 *   Dragon: bat-like wings with thick bones, a wrist claw, a spiked top edge and dark membranes
 *   Mech: five sharp metal blades with glowing power lines, spreading and folding
 *   Inferno: wings of fire, white-hot at the shoulder to red at the flickering flame tips, shedding embers
 *   Shadow: jagged black wings with a glowing edge, trailing smoke
 * Only you see them.
 */
public class Wings extends Module {
    public enum Style { Dragon, Mech, Inferno, Shadow }

    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgColor = settings.createGroup("Colors");

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("What kind of wings.").defaultValue(Style.Dragon).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("size").description("Wingspan of one wing (blocks).").defaultValue(1.6).range(0.4, 4).sliderRange(0.6, 2.8).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("beat-speed").description("How fast they beat (faster in the air).").defaultValue(1).range(0.1, 4).sliderRange(0.2, 2.5).build());

    private final Setting<Boolean> particles = sg.add(new BoolSetting.Builder()
        .name("particles").description("Embers (Inferno) and smoke (Shadow) coming off the wings.").defaultValue(true)
        .visible(() -> style.get() == Style.Inferno || style.get() == Style.Shadow).build());

    private final Setting<Boolean> halo = sg.add(new BoolSetting.Builder()
        .name("halo").description("A halo over your head too.").defaultValue(false).build());

    private final Setting<Boolean> rainbow = sgColor.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow flowing across the wings.").defaultValue(false).build());

    private final Setting<Boolean> pulse = sgColor.add(new BoolSetting.Builder()
        .name("pulse").description("The glow breathes slowly.").defaultValue(true).build());

    // Each style keeps its own colours; only the current style's show
    private final Setting<SettingColor> dragonFill = colorSetting("dragon-membrane", "Dragon: membrane colour.", new SettingColor(55, 4, 8, 200), Style.Dragon);
    private final Setting<SettingColor> dragonEdge = colorSetting("dragon-edge", "Dragon: bones, claw and edge glow.", new SettingColor(235, 45, 25, 255), Style.Dragon);
    private final Setting<SettingColor> mechFill = colorSetting("mech-plates", "Mech: blade plating.", new SettingColor(135, 145, 160, 240), Style.Mech);
    private final Setting<SettingColor> mechEdge = colorSetting("mech-power", "Mech: glowing power lines.", new SettingColor(0, 230, 255, 255), Style.Mech);
    private final Setting<SettingColor> infernoCore = colorSetting("inferno-core", "Inferno: heat at the shoulder.", new SettingColor(255, 235, 140, 210), Style.Inferno);
    private final Setting<SettingColor> infernoTips = colorSetting("inferno-tips", "Inferno: flame tips.", new SettingColor(255, 40, 0, 190), Style.Inferno);
    private final Setting<SettingColor> shadowFill = colorSetting("shadow-body", "Shadow: wing colour.", new SettingColor(8, 4, 14, 215), Style.Shadow);
    private final Setting<SettingColor> shadowEdge = colorSetting("shadow-edge", "Shadow: glowing edge.", new SettingColor(150, 50, 255, 255), Style.Shadow);

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

    public Wings() {
        super(AddonTemplate.CATEGORY, "wings", "Dragon, mech, fire or shadow wings on your back (third person). Only you see them.");
    }

    // ---- Shapes: points in the wing's own plane (u = out along the span, v = up), root at (0, 0) ----

    // Dragon: leading edge with spikes up to the wrist and out to the tip, then scalloped membrane between finger tips
    private static final double[][] DRAGON = {
        {0, 0}, {0.1, 0.2}, {0.14, 0.32}, {0.2, 0.3}, {0.26, 0.46}, {0.32, 0.43}, {0.4, 0.6},
        {0.55, 0.58}, {0.62, 0.66}, {0.7, 0.57}, {0.86, 0.52}, {1.05, 0.4},
        {0.86, 0.26}, {0.8, 0.12}, {0.95, -0.12},
        {0.72, -0.06}, {0.62, -0.18}, {0.7, -0.46},
        {0.5, -0.3}, {0.4, -0.38}, {0.38, -0.62},
        {0.26, -0.42}, {0.15, -0.4}, {0.04, -0.32}};
    private static final int[] DRAGON_TIPS = {11, 14, 17, 20};
    private static final double[] DRAGON_WRIST = {0.4, 0.6};

    // Shadow: ragged, torn points all along the trailing edge
    private static final double[][] SHADOW = {
        {0, 0}, {0.14, 0.3}, {0.34, 0.5}, {0.6, 0.56}, {0.82, 0.5}, {1.05, 0.36},
        {0.84, 0.28}, {0.98, 0.14}, {0.78, 0.1}, {0.92, -0.06}, {0.7, -0.04}, {0.8, -0.26},
        {0.58, -0.18}, {0.62, -0.44}, {0.46, -0.3}, {0.42, -0.58}, {0.3, -0.38}, {0.2, -0.56}, {0.12, -0.32}, {0.03, -0.38}};

    // ---- Drawing ----

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.options.getPerspective().isFirstPerson() || mc.player.isInvisible()) return;
        advance();

        float td = event.tickDelta;
        double yaw = Math.toRadians(MathHelper.lerpAngleDegrees(td, mc.player.lastBodyYaw, mc.player.bodyYaw));
        Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
        Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw)); // the player's right
        Vec3d up = new Vec3d(0, 1, 0);
        boolean sneak = mc.player.isInSneakingPose();
        // Gliding/swimming/crawling tips the body forward around the feet (as vanilla renders it), and the hitbox shrinks,
        // so anchor to the tilted body instead of the hitbox height. tilt: degrees from upright toward face-down.
        float lean = mc.player.getLeaningPitch(td);
        double tilt = mc.player.isGliding() ? 90 + mc.player.getPitch(td)
            : lean > 0 ? lean * (mc.player.isTouchingWater() ? 90 + mc.player.getPitch(td) : 90) : 0;
        Vec3d root;
        if (tilt != 0) {
            double a = Math.toRadians(tilt);
            Vec3d bodyUp = up.multiply(Math.cos(a)).add(forward.multiply(Math.sin(a)));
            forward = forward.multiply(Math.cos(a)).subtract(up.multiply(Math.sin(a)));
            up = bodyUp;
            root = Fx.pos(mc.player, td).add(up.multiply(1.8 * 0.76)).add(forward.multiply(-0.26));
        } else {
            root = Fx.pos(mc.player, td).add(0, mc.player.getHeight() * (sneak ? 0.8 : 0.76), 0).add(forward.multiply(sneak ? -0.12 : -0.26));
            if (sneak) up = up.add(forward.multiply(0.45)).normalize(); // lean with the body
        }
        Vec3d back = forward.multiply(-1);
        double t = phase;

        for (int side = -1; side <= 1; side += 2) {
            Vec3d out = right.multiply(side);
            double sweep, lift;
            switch (style.get()) {
                case Dragon -> { sweep = 22; lift = 16 * Math.sin(t * 2.6) + 4; }
                case Mech -> { sweep = 18; lift = 6 * Math.sin(t * 1.6) + 6; }
                case Inferno -> { sweep = 20; lift = 10 * Math.sin(t * 2.2) + 6; }
                default -> { sweep = 26; lift = 12 * Math.sin(t * 2) + 2; }
            }
            double sw = Math.toRadians(sweep), li = Math.toRadians(lift);
            // span: out to the side, swept back behind you, raised/lowered by the beat
            Vec3d span = out.multiply(Math.cos(sw)).add(back.multiply(Math.sin(sw)));
            Vec3d u = span.multiply(Math.cos(li)).add(up.multiply(Math.sin(li)));
            Vec3d v = up.multiply(Math.cos(li)).subtract(span.multiply(Math.sin(li)));
            double len = size.get();
            Frame f = new Frame(root.add(out.multiply(0.1)), u.multiply(len), v.multiply(len), back);

            switch (style.get()) {
                case Dragon -> drawDragon(event.depthRenderer, f, t);
                case Mech -> drawMech(event.depthRenderer, f, t);
                case Inferno -> drawInferno(event.depthRenderer, f, t);
                case Shadow -> drawShadow(event.depthRenderer, f, t);
            }
        }
    }

    private record Frame(Vec3d origin, Vec3d u, Vec3d v, Vec3d back) {
        Vec3d at(double x, double y) {
            return origin.add(u.multiply(x)).add(v.multiply(y));
        }

        Vec3d at(double[] p) {
            return at(p[0], p[1]);
        }
    }

    private void drawDragon(Renderer3D r, Frame f, double t) {
        Color fill = fill(t), edge = edge(t);
        fan(r, f, DRAGON, Fx.alpha(fill, (int) (fill.a * 0.75)), fill);
        outline(r, f, DRAGON, edge);
        Vec3d wrist = f.at(DRAGON_WRIST);
        bone(r, f.at(0, 0), wrist, f, 0.05, edge);
        for (int i : DRAGON_TIPS) bone(r, wrist, f.at(DRAGON[i]), f, 0.035, edge);
        // hooked claw at the wrist
        Vec3d c1 = f.at(0.44, 0.72), c2 = f.at(0.52, 0.76);
        r.quad(wrist.x, wrist.y, wrist.z, c1.x, c1.y, c1.z, c2.x, c2.y, c2.z, wrist.x, wrist.y, wrist.z, edge, edge, Fx.alpha(edge, 120), edge);
    }

    // Five sharp blades fanning out, spreading and folding with the beat, each with a glowing power line
    private void drawMech(Renderer3D r, Frame f, double t) {
        Color plate = fill(t), power = edge(t);
        double spread = 0.85 + 0.15 * Math.sin(t * 1.6);
        for (int k = 0; k < 5; k++) {
            double ang = Math.toRadians((30 - k * 20) * spread);
            double len = 1.05 - k * 0.12, w = 0.12 - k * 0.012;
            double dx = Math.cos(ang), dy = Math.sin(ang), nx = -dy, ny = dx;
            double bx = 0.06 * dx, by = 0.06 * dy;
            Vec3d a = f.at(bx + nx * w, by + ny * w), b = f.at(bx - nx * w, by - ny * w);
            Vec3d c = f.at(dx * len * 0.82 - nx * w * 0.7, dy * len * 0.82 - ny * w * 0.7);
            Vec3d tip = f.at(dx * len, dy * len);
            r.quad(a.x, a.y, a.z, b.x, b.y, b.z, c.x, c.y, c.z, tip.x, tip.y, tip.z, plate, plate, plate, Fx.alpha(plate, plate.a / 2));
            line(r, a, tip, power);
            line(r, b, c, Fx.alpha(power, power.a * 2 / 3));
            line(r, c, tip, power);
            line(r, f.at(dx * 0.1, dy * 0.1), f.at(dx * len * 0.9, dy * len * 0.9), power); // power line down the middle
        }
        Fx.glow(r, f.at(0.05, 0.02), size.get() * 0.09, power); // reactor at the shoulder
    }

    // Flames: a fan from the shoulder to flickering pointed tongues, white-hot at the root, red at the tips
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
        if (particles.get() && rng.nextInt(3) == 0) {
            double[] p = pts[1 + rng.nextInt(tongues) * 2];
            Vec3d e = f.at(p);
            mc.particleManager.addParticle(rng.nextBoolean() ? ParticleTypes.FLAME : ParticleTypes.SMALL_FLAME, e.x, e.y, e.z, 0.0, 0.03, 0.0);
        }
    }

    private void drawShadow(Renderer3D r, Frame f, double t) {
        Color fill = fill(t), edge = edge(t);
        fan(r, f, SHADOW, fill, Fx.alpha(fill, (int) (fill.a * 0.85)));
        outline(r, f, SHADOW, edge);
        if (particles.get() && rng.nextInt(4) == 0) {
            Vec3d e = f.at(SHADOW[1 + rng.nextInt(SHADOW.length - 1)]);
            mc.particleManager.addParticle(ParticleTypes.SMOKE, e.x, e.y, e.z, 0.0, 0.01, 0.0);
        }
    }

    // ---- Primitives ----

    // Filled shape: a fan from the root, one colour at the root blending to another at the edge
    private void fan(Renderer3D r, Frame f, double[][] pts, Color rootColor, Color edgeColor) {
        Vec3d o = f.at(0, 0);
        int n = pts.length;
        for (int i = 0; i < n; i++) {
            Vec3d pa = f.at(pts[i]), pb = f.at(pts[(i + 1) % n]);
            Color ca = rainbowAlong(edgeColor, (double) i / n), cb = rainbowAlong(edgeColor, (double) (i + 1) / n);
            r.quad(o.x, o.y, o.z, pa.x, pa.y, pa.z, pb.x, pb.y, pb.z, o.x, o.y, o.z, rootColor, ca, cb, rootColor);
        }
    }

    private void outline(Renderer3D r, Frame f, double[][] pts, Color c) {
        int n = pts.length;
        for (int i = 0; i < n; i++) line(r, f.at(pts[i]), f.at(pts[(i + 1) % n]), rainbowAlong(c, (double) i / n));
    }

    // A thick bone: a thin tapered strip between two points, lying in the wing
    private void bone(Renderer3D r, Vec3d a, Vec3d b, Frame f, double width, Color c) {
        Vec3d dir = b.subtract(a);
        Vec3d side = dir.crossProduct(f.back()).normalize().multiply(width * size.get() * 0.5);
        Vec3d a0 = a.add(side), a1 = a.subtract(side), b0 = b.add(side.multiply(0.3)), b1 = b.subtract(side.multiply(0.3));
        r.quad(a0.x, a0.y, a0.z, a1.x, a1.y, a1.z, b1.x, b1.y, b1.z, b0.x, b0.y, b0.z, c, c, c, c);
    }

    private void line(Renderer3D r, Vec3d a, Vec3d b, Color c) {
        r.line(a.x, a.y, a.z, b.x, b.y, b.z, c);
    }

    // ---- Colours ----

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
        }, t);
    }

    private Color edge(double t) {
        if (rainbow.get()) return Fx.hue(Fx.time() * 0.1 + 0.15, 255);
        return glow(switch (style.get()) {
            case Dragon -> dragonEdge.get();
            case Mech -> mechEdge.get();
            case Inferno -> infernoTips.get();
            case Shadow -> shadowEdge.get();
        }, t);
    }

    // With rainbow on, the colour flows along the wing's edge
    private Color rainbowAlong(Color c, double along) {
        return rainbow.get() ? Fx.hue(Fx.time() * 0.1 + along * 0.5, c.a) : c;
    }

    // Beat time, running faster while you're in the air
    private void advance() {
        long now = System.nanoTime();
        double dt = last == 0 ? 0 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        boolean air = mc.player != null && !mc.player.isOnGround();
        phase += dt * speed.get() * (air ? 2 : 1);
    }

    // ---- Halo (drawn by the entity renderer, see LivingEntityRendererMixin) ----

    public float time() {
        return (float) phase;
    }

    public boolean halo() {
        return halo.get();
    }

    public int haloColor() {
        return haloColor.get().getPacked() | 0xFF000000;
    }
}
