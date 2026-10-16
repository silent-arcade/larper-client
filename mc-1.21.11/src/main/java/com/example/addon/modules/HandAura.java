package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.mixin.GameRendererAccessor;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.Arm;
import net.minecraft.util.math.Vec3d;

import java.util.function.Supplier;

public class HandAura extends VisualModule {
    public enum Palette {
        Rainbow(0), Red(0xFF2B2B), Crimson(0xB0102A), Orange(0xFF8A1E), Gold(0xFFC83D), Yellow(0xFFF04A),
        Lime(0x9BFF3A), Green(0x2BD964), Mint(0x7DFFD0), Cyan(0x2BE6FF), Sky(0x6EC6FF), Blue(0x2F6BFF),
        Indigo(0x5A3DFF), Purple(0xA45BFF), Magenta(0xFF3DF2), Pink(0xFF7AC8), Rose(0xFF4F7B), White(0xFFFFFF),
        Silver(0xC9D1DA), Shadow(0x3A3550), Custom(0);

        final int rgb;

        Palette(int rgb) {
            this.rgb = rgb;
        }
    }

    public enum Shape { Ring, Helix, Atom, Vortex, Sparkle }

    public enum Particle {
        Dust(null), Flame(() -> ParticleTypes.FLAME), SoulFlame(() -> ParticleTypes.SOUL_FIRE_FLAME),
        EndRod(() -> ParticleTypes.END_ROD), Spark(() -> ParticleTypes.ELECTRIC_SPARK), Enchant(() -> ParticleTypes.ENCHANT),
        Glow(() -> ParticleTypes.GLOW), Totem(() -> ParticleTypes.TOTEM_OF_UNDYING), Witch(() -> ParticleTypes.WITCH),
        Cherry(() -> ParticleTypes.CHERRY_LEAVES), Snow(() -> ParticleTypes.SNOWFLAKE);

        final Supplier<ParticleEffect> effect;

        Particle(Supplier<ParticleEffect> effect) {
            this.effect = effect;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgParticles = settings.createGroup("Particles");

    private final Setting<Palette> palette = sgGeneral.add(new EnumSetting.Builder<Palette>()
        .name("color").description("Aura color. Rainbow cycles through every color.").defaultValue(Palette.Rainbow).build());

    private final Setting<SettingColor> customColor = sgGeneral.add(new ColorSetting.Builder()
        .name("custom-color").description("Color used when color is Custom.").defaultValue(new SettingColor(170, 90, 255))
        .visible(() -> palette.get() == Palette.Custom).build());

    private final Setting<Double> rainbowSpeed = sgGeneral.add(new DoubleSetting.Builder()
        .name("rainbow-speed").description("How fast the rainbow cycles.").defaultValue(1).range(0.1, 5).sliderRange(0.1, 5)
        .visible(() -> palette.get() == Palette.Rainbow).build());

    private final Setting<Boolean> tintHand = sgGeneral.add(new BoolSetting.Builder()
        .name("color-hand").description("Colors your first-person hand.").defaultValue(true).build());

    private final Setting<Boolean> tintItem = sgGeneral.add(new BoolSetting.Builder()
        .name("color-item").description("Colors the item or weapon in your first-person hand.").defaultValue(true).build());

    private final Setting<Boolean> particles = sgParticles.add(new BoolSetting.Builder()
        .name("particles").description("Swirling particles around your hand.").defaultValue(true).build());

    private final Setting<Shape> shape = sgParticles.add(new EnumSetting.Builder<Shape>()
        .name("shape").description("Shape the particles make around your hand.").defaultValue(Shape.Helix)
        .visible(particles::get).build());

    private final Setting<Particle> particle = sgParticles.add(new EnumSetting.Builder<Particle>()
        .name("particle").description("Particle the aura is made of. Dust uses the aura color.").defaultValue(Particle.Dust)
        .visible(particles::get).build());

    private final Setting<Boolean> gradient = sgParticles.add(new BoolSetting.Builder()
        .name("gradient").description("With Rainbow, every particle gets its own color so the whole aura is a rainbow.").defaultValue(true)
        .visible(() -> particles.get() && particle.get() == Particle.Dust && palette.get() == Palette.Rainbow).build());

    private final Setting<Boolean> pulse = sgParticles.add(new BoolSetting.Builder()
        .name("pulse").description("The aura breathes in and out.").defaultValue(true)
        .visible(particles::get).build());

    private final Setting<Boolean> swingBurst = sgParticles.add(new BoolSetting.Builder()
        .name("swing-burst").description("Particles burst out when you swing.").defaultValue(true)
        .visible(particles::get).build());

    private final Setting<Integer> lifetime = sgParticles.add(new IntSetting.Builder()
        .name("lifetime").description("How long each particle lasts (ticks). Short = crisp, long = trails.").defaultValue(8).range(2, 40).sliderRange(2, 40)
        .visible(particles::get).build());

    private final Setting<Double> size = sgParticles.add(new DoubleSetting.Builder()
        .name("size").description("Dust particle size.").defaultValue(0.6).range(0.1, 2).sliderRange(0.1, 2)
        .visible(() -> particles.get() && particle.get() == Particle.Dust).build());

    private final Setting<Integer> density = sgParticles.add(new IntSetting.Builder()
        .name("density").description("Particles per tick.").defaultValue(3).range(1, 12).sliderRange(1, 12)
        .visible(particles::get).build());

    private final Setting<Double> radius = sgParticles.add(new DoubleSetting.Builder()
        .name("radius").description("Size of the aura around your hand.").defaultValue(0.24).range(0.03, 0.6).sliderRange(0.03, 0.6)
        .visible(particles::get).build());

    private final Setting<Double> speed = sgParticles.add(new DoubleSetting.Builder()
        .name("speed").description("How fast the aura swirls.").defaultValue(1).range(0, 5).sliderRange(0, 5)
        .visible(particles::get).build());

    private final Setting<Boolean> firstPerson = sgParticles.add(new BoolSetting.Builder()
        .name("first-person").description("Show particles in first person.").defaultValue(true)
        .visible(particles::get).build());

    private final Setting<Boolean> thirdPerson = sgParticles.add(new BoolSetting.Builder()
        .name("third-person").description("Show particles in third person (F5).").defaultValue(true)
        .visible(particles::get).build());

    private int ticks;

    public HandAura() {
        super("hand-aura", "Colors your hand and weapon, with swirling particles. Only you see it.");
    }

    public int auraRgb() {
        return switch (palette.get()) {
            case Rainbow -> java.awt.Color.HSBtoRGB((float) ((System.nanoTime() / 1e9 * 0.25 * rainbowSpeed.get()) % 1), 0.75f, 1f) & 0xFFFFFF;
            case Custom -> customColor.get().getPacked() & 0xFFFFFF;
            default -> palette.get().rgb;
        };
    }

    public int handColor() {
        return isActive() && tintHand.get() ? 0xFF000000 | auraRgb() : 0;
    }

    public int itemColor() {
        return isActive() && tintItem.get() ? 0xFF000000 | auraRgb() : 0;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null || !particles.get()) return;
        boolean fp = mc.options.getPerspective().isFirstPerson();
        if (fp ? !firstPerson.get() : !thirdPerson.get()) return;

        float side = mc.player.getMainArm() == Arm.RIGHT ? 1 : -1;
        Vec3d hand, a, b;
        double scale = 1;

        if (fp) {
            var cam = mc.gameRenderer.getCamera();
            float fov = ((GameRendererAccessor) mc.gameRenderer).larper$getFov(cam, 1, true);
            scale = Math.tan(Math.toRadians(fov / 2)) / Math.tan(Math.toRadians(35));
            Vec3d forward = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
            Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
            Vec3d up = right.crossProduct(forward).normalize();
            hand = cam.getCameraPos().add(forward.multiply(0.75)).add(right.multiply(0.46 * side * scale)).add(up.multiply(-0.27 * scale));
            a = right;
            b = up;
        } else {
            double yaw = Math.toRadians(mc.player.bodyYaw);
            Vec3d forward = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3d right = new Vec3d(-Math.cos(yaw), 0, -Math.sin(yaw));
            double height = mc.player.isSneaking() ? 0.55 : 0.72;
            hand = mc.player.getLerpedPos(1).add(right.multiply(0.37 * side)).add(forward.multiply(0.08)).add(0, height, 0);
            a = right;
            b = forward;
        }

        Vec3d c = a.crossProduct(b).normalize();

        ticks++;
        int n = detail(density.get(), 3);
        double spd = speed.get();
        double r = radius.get() * scale * (pulse.get() ? 1 + 0.25 * Math.sin(ticks * 0.2) : 1);
        double spin = ticks * 0.25 * spd;

        for (int i = 0; i < n; i++) {
            double t = (double) i / n;
            double u, v, w;
            switch (shape.get()) {
                case Ring -> {
                    double ang = spin + t * Math.PI * 2;
                    u = Math.cos(ang) * r; v = Math.sin(ang) * r; w = 0;
                }
                case Helix -> {
                    double ang = spin * 2 + (i % 2) * Math.PI;
                    double h = ((ticks * 0.08 * spd + (i / 2) * 0.15) % 1) * 2 - 1;
                    u = Math.cos(ang + h * 3) * r; v = h * r * 1.6; w = Math.sin(ang + h * 3) * r;
                }
                case Atom -> {
                    int k = i % 3;
                    double tilt = k * Math.PI / 3, ang = spin * 1.5 + t * Math.PI * 2 + k;
                    u = Math.cos(ang) * r * Math.cos(tilt); v = Math.sin(ang) * r; w = Math.cos(ang) * r * Math.sin(tilt);
                }
                case Vortex -> {
                    double h = (t + ticks * 0.05 * spd) % 1;
                    double rr = r * (1.3 - h), ang = spin * 2 + h * 8 + i;
                    u = Math.cos(ang) * rr; v = (h * 2 - 1) * r * 1.4; w = Math.sin(ang) * rr;
                }
                default -> {
                    Vec3d d = randomDir().multiply(r * Math.cbrt(Math.random()));
                    u = d.x; v = d.y; w = d.z;
                }
            }
            Vec3d pos = hand.add(a.multiply(u)).add(b.multiply(v)).add(c.multiply(w));
            Vec3d tangent = a.multiply(-v).add(b.multiply(u));
            Vec3d vel = tangent.lengthSquared() < 1e-9 ? Vec3d.ZERO : tangent.normalize().multiply(0.006 * spd);
            spawn(effect(t), pos, vel);
        }

        boolean swinging = mc.player.handSwinging;
        if (swingBurst.get() && swinging && !wasSwinging) {
            int ringN = detail(24, 8);
            for (int i = 0; i < ringN; i++) {
                double ang = Math.PI * 2 * i / ringN;
                spawn(effect((double) i / ringN), hand, a.multiply(Math.cos(ang)).add(b.multiply(Math.sin(ang))).multiply(0.12));
            }
            if (!low()) for (int i = 0; i < 10; i++) spawn(effect(i / 10.0), hand, randomDir().multiply(0.04 + Math.random() * 0.06));
        }
        wasSwinging = swinging;
    }

    private boolean wasSwinging;

    private void spawn(ParticleEffect effect, Vec3d pos, Vec3d vel) {
        net.minecraft.client.particle.Particle p = mc.particleManager.addParticle(effect, pos.x, pos.y, pos.z, 0, 0, 0);
        if (p == null) return;
        p.setVelocity(vel.x, vel.y, vel.z);
        p.setMaxAge(lifetime.get());
    }

    private ParticleEffect effect(double offset) {
        if (particle.get() != Particle.Dust) return particle.get().effect.get();
        int rgb = palette.get() == Palette.Rainbow && gradient.get()
            ? java.awt.Color.HSBtoRGB((float) ((System.nanoTime() / 1e9 * 0.25 * rainbowSpeed.get() + offset) % 1), 0.75f, 1f) & 0xFFFFFF
            : auraRgb();
        return new DustParticleEffect(rgb, size.get().floatValue());
    }

    private static Vec3d randomDir() {
        double z = Math.random() * 2 - 1, ang = Math.random() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new Vec3d(Math.cos(ang) * s, z, Math.sin(ang) * s);
    }
}
