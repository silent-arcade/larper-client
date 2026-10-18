package com.example.addon.modules;

import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import com.example.addon.visuals.VisualModule;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class HitParticles extends VisualModule {
    public enum Type { None, Crit, MagicCrit, Hearts, Flame, SoulFlame, EndRod, Totem, Angry, Notes }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colour theme for the burst, ring and star. Custom uses the colour below.").defaultValue(FxTheme.Celestial).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Custom theme colour.").defaultValue(new SettingColor(255, 220, 90, 255)).visible(() -> theme.get() == FxTheme.Custom).build());

    private final Setting<Type> type = sg.add(new EnumSetting.Builder<Type>()
        .name("vanilla-particles").description("Minecraft particles that burst out too (None = only the effects below).").defaultValue(Type.MagicCrit).build());

    private final Setting<Integer> amount = sg.add(new IntSetting.Builder()
        .name("amount").description("How many vanilla particles per hit.").defaultValue(14).range(1, 80).sliderRange(4, 40).visible(() -> type.get() != Type.None).build());

    private final Setting<Boolean> critBoost = sg.add(new BoolSetting.Builder()
        .name("crit-boost").description("Critical hits (while falling) get a bigger, double burst.").defaultValue(true).build());

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> burst = feature("spark-burst", "Two-tone sparks fly out of the hit and fall with gravity.");
    private final Setting<Boolean> ring = feature("impact-ring", "A ring snaps out round the target's feet.");
    private final Setting<Boolean> star = feature("star-flash", "A star of light rays flashes out of the hit point.");
    private final Setting<Boolean> slash = feature("slash", "A quick slash line across the target in the direction you hit.");

    private record Hit(Vec3 pos, Vec3 feet, Vec3 dir, double start, boolean crit, double size) {}

    private final List<Hit> hits = new ArrayList<>();
    private final Random rng = new Random();

    public HitParticles() {
        super("hit-particles", "Every hit explodes: spark burst, impact ring, star flash and a slash, with bigger crit hits. Only you see it.");
    }

    @Override
    public void onDeactivate() {
        hits.clear();
    }

    private ParticleOptions effect() {
        return switch (type.get()) {
            case None, Crit -> ParticleTypes.CRIT;
            case MagicCrit -> ParticleTypes.ENCHANTED_HIT;
            case Hearts -> ParticleTypes.HEART;
            case Flame -> ParticleTypes.FLAME;
            case SoulFlame -> ParticleTypes.SOUL_FIRE_FLAME;
            case EndRod -> ParticleTypes.END_ROD;
            case Totem -> ParticleTypes.TOTEM_OF_UNDYING;
            case Angry -> ParticleTypes.ANGRY_VILLAGER;
            case Notes -> ParticleTypes.NOTE;
        };
    }

    private Color col(double t, int a) {
        return theme.get().color(t, a, color.get());
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        Entity e = event.entity;
        if (!(e instanceof LivingEntity) || mc.level == null || mc.player == null) return;
        boolean crit = critBoost.get() && mc.player.fallDistance > 0 && !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater();
        Vec3 center = e.getBoundingBox().getCenter();
        Vec3 dir = center.subtract(mc.player.getEyePosition()).normalize();
        hits.add(new Hit(center, e.position(), dir, Fx.time(), crit, Math.max(0.8, e.getBbWidth() * 1.4)));
        if (type.get() == Type.None) return;
        ParticleOptions p = effect();
        int n = amount.get() * (crit ? 2 : 1);
        for (int i = 0; i < n; i++) {
            double x = e.getX() + (rng.nextDouble() - 0.5) * e.getBbWidth(), y = e.getY() + rng.nextDouble() * e.getBbHeight(), z = e.getZ() + (rng.nextDouble() - 0.5) * e.getBbWidth();
            mc.level.addParticle(p, x, y, z, (rng.nextDouble() - 0.5) * 0.5, rng.nextDouble() * 0.35, (rng.nextDouble() - 0.5) * 0.5);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double now = Fx.time();
        hits.removeIf(h -> now - h.start() > 0.8);
        for (Hit h : hits) {
            double age = now - h.start(), boost = h.crit() ? 1.6 : 1;
            if (on(burst) && age < 0.8) {
                double f = age / 0.8;
                int n = h.crit() ? 26 : 14;
                for (int k = 0; k < n; k++) {
                    double yaw = k * 2.39996 + h.start() * 9, up = 0.2 + 0.7 * ((k * 0.618) % 1);
                    Vec3 dir = new Vec3(Math.cos(yaw) * (1 - up * 0.5), up, Math.sin(yaw) * (1 - up * 0.5)).add(h.dir().scale(0.6));
                    Vec3 at = h.pos().add(dir.scale(age * 5 * boost)).add(0, -8 * age * age, 0);
                    Color c = k % 2 == 0 ? col(h.start() * 0.3 + k * 0.03, 255) : theme.get().secondary(h.start() * 0.3, 255, color.get());
                    spark(event.renderer, at, 0.16 * (1 - f) * boost, Fx.alpha(c, (int) (255 * (1 - f))), true);
                }
            }
            if (on(ring) && age < 0.45) {
                double f = age / 0.45, r = h.size() * 0.4 + 1.6 * boost * (1 - Math.pow(1 - f, 3));
                ring(event.renderer, h.feet().add(0, 0.05, 0), r - 0.08 * boost, r, detail(48, 14), u -> Fx.alpha(col(u + h.start(), 255), (int) (230 * (1 - f))));
            }
            if (on(star) && age < 0.25) {
                double f = age / 0.25;
                int rays = h.crit() ? 12 : 8;
                for (int k = 0; k < rays; k++) {
                    double a = k * Math.PI * 2 / rays + h.start() * 3;
                    Vec3 side = h.dir().cross(new Vec3(0, 1, 0)).normalize();
                    Vec3 up = side.cross(h.dir()).normalize();
                    Vec3 ray = side.scale(Math.cos(a)).add(up.scale(Math.sin(a)));
                    Vec3 from = h.pos().add(ray.scale(0.15)), to = h.pos().add(ray.scale((0.35 + 0.9 * f) * boost * (k % 2 == 0 ? 1 : 0.6)));
                    event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z, Fx.alpha(col(k * 0.1, 255), (int) (255 * (1 - f))), Fx.alpha(col(k * 0.1 + 0.5, 255), 0));
                }
            }
            if (on(slash) && age < 0.2) {
                double f = age / 0.2;
                Vec3 side = h.dir().cross(new Vec3(0, 1, 0)).normalize();
                Vec3 a = h.pos().add(side.scale(-0.9 * boost)).add(0, 0.5, 0), b = h.pos().add(side.scale(0.9 * boost)).add(0, -0.4, 0);
                Vec3 mid = a.lerp(b, Math.min(1, f * 1.6));
                Color c = Fx.alpha(new Color(255, 255, 255, 255), (int) (255 * (1 - f)));
                event.renderer.line(a.x, a.y, a.z, mid.x, mid.y, mid.z, Fx.alpha(col(0, 255), 0), c);
            }
        }
    }
}
