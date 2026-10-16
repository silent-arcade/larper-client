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
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.phys.Vec3;
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

        final Supplier<ParticleOptions> effect;

        Particle(Supplier<ParticleOptions> effect) {
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
        if (mc.player == null || mc.level == null || !particles.get()) return;
        boolean fp = mc.options.getCameraType().isFirstPerson();
        if (fp ? !firstPerson.get() : !thirdPerson.get()) return;

        float side = mc.player.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        Vec3 hand, a, b;
        double scale = 1;

        if (fp) {
            var cam = mc.gameRenderer.getMainCamera();
            float fov = cam.getFov();
            scale = Math.tan(Math.toRadians(fov / 2)) / Math.tan(Math.toRadians(35));
            Vec3 forward = Vec3.directionFromRotation(cam.xRot(), cam.yRot());
            Vec3 right = forward.cross(new Vec3(0, 1, 0)).normalize();
            Vec3 up = right.cross(forward).normalize();
            hand = cam.position().add(forward.scale(0.75)).add(right.scale(0.46 * side * scale)).add(up.scale(-0.27 * scale));
            a = right;
            b = up;
        } else {
            double yaw = Math.toRadians(mc.player.yBodyRot);
            Vec3 forward = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw));
            Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
            double height = mc.player.isShiftKeyDown() ? 0.55 : 0.72;
            hand = mc.player.getPosition(1).add(right.scale(0.37 * side)).add(forward.scale(0.08)).add(0, height, 0);
            a = right;
            b = forward;
        }

        Vec3 c = a.cross(b).normalize();

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
                    Vec3 d = randomDir().scale(r * Math.cbrt(Math.random()));
                    u = d.x; v = d.y; w = d.z;
                }
            }
            Vec3 pos = hand.add(a.scale(u)).add(b.scale(v)).add(c.scale(w));
            Vec3 tangent = a.scale(-v).add(b.scale(u));
            Vec3 vel = tangent.lengthSqr() < 1e-9 ? Vec3.ZERO : tangent.normalize().scale(0.006 * spd);
            spawn(effect(t), pos, vel);
        }

        boolean swinging = mc.player.swinging;
        if (swingBurst.get() && swinging && !wasSwinging) {
            int ringN = detail(24, 8);
            for (int i = 0; i < ringN; i++) {
                double ang = Math.PI * 2 * i / ringN;
                spawn(effect((double) i / ringN), hand, a.scale(Math.cos(ang)).add(b.scale(Math.sin(ang))).scale(0.12));
            }
            if (!low()) for (int i = 0; i < 10; i++) spawn(effect(i / 10.0), hand, randomDir().scale(0.04 + Math.random() * 0.06));
        }
        wasSwinging = swinging;
    }

    private boolean wasSwinging;

    private void spawn(ParticleOptions effect, Vec3 pos, Vec3 vel) {
        net.minecraft.client.particle.Particle p = mc.particleEngine.createParticle(effect, pos.x, pos.y, pos.z, 0, 0, 0);
        if (p == null) return;
        p.setParticleSpeed(vel.x, vel.y, vel.z);
        p.setLifetime(lifetime.get());
    }

    private ParticleOptions effect(double offset) {
        if (particle.get() != Particle.Dust) return particle.get().effect.get();
        int rgb = palette.get() == Palette.Rainbow && gradient.get()
            ? java.awt.Color.HSBtoRGB((float) ((System.nanoTime() / 1e9 * 0.25 * rainbowSpeed.get() + offset) % 1), 0.75f, 1f) & 0xFFFFFF
            : auraRgb();
        return new DustParticleOptions(rgb, size.get().floatValue());
    }

    private static Vec3 randomDir() {
        double z = Math.random() * 2 - 1, ang = Math.random() * Math.PI * 2, s = Math.sqrt(1 - z * z);
        return new Vec3(Math.cos(ang) * s, z, Math.sin(ang) * s);
    }
}
