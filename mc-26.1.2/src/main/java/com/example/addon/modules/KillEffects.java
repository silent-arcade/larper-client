package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class KillEffects extends VisualModule {
    public enum Style { Lightning, Shockwave, Hearts, Souls, Vortex, Supernova }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Lightning, Shockwave, Hearts, Souls, Vortex (a spiral of sparks spinning up) or Supernova (a flash and a burst of stars).").defaultValue(Style.Shockwave).build());

    private final Setting<Boolean> playersOnly = sg.add(new BoolSetting.Builder()
        .name("players-only").description("Only for player kills.").defaultValue(false).build());

    private final Setting<Boolean> sound = sg.add(new BoolSetting.Builder()
        .name("thunder-sound").description("Lightning plays its thunder.").defaultValue(true)
        .visible(() -> style.get() == Style.Lightning).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow shockwave and sparks.").defaultValue(false)
        .visible(() -> style.get() == Style.Shockwave || style.get() == Style.Vortex || style.get() == Style.Supernova).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Shockwave, spark and soul colour.").defaultValue(new SettingColor(255, 60, 90, 220))
        .visible(() -> style.get() == Style.Souls || style.get() != Style.Lightning && style.get() != Style.Hearts && !rainbow.get()).build());

    private record Wave(Vec3 pos, double start, boolean pillar) {}

    private final Map<Integer, Double> hits = new HashMap<>();
    private final List<Wave> waves = new ArrayList<>();

    private record Burst(Vec3 pos, double start, double height, boolean nova, long seed) {}

    private final List<Burst> bursts = new ArrayList<>();
    private final Random rng = new Random();

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> pillar = feature("light-pillar", "Shockwave and Vortex: a pillar of light where they died.");

    public KillEffects() {
        super("kill-effects", "Lightning, shockwave, hearts, souls, vortex or supernova when you kill something. Only you see it.");
    }

    @Override
    public void onDeactivate() {
        hits.clear();
        waves.clear();
        bursts.clear();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof LivingEntity) hits.put(event.entity.getId(), Fx.time());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null) return;
        double now = Fx.time();
        hits.entrySet().removeIf(h -> {
            if (now - h.getValue() > 3) return true;
            Entity e = mc.level.getEntity(h.getKey());
            if (!(e instanceof LivingEntity le) || !(le.isDeadOrDying() || le.getHealth() <= 0)) return false;
            if (!playersOnly.get() || e instanceof Player) play(le);
            return true;
        });
    }

    private void play(LivingEntity e) {
        Vec3 p = e.position();
        switch (style.get()) {
            case Lightning -> {
                LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, mc.level);
                bolt.setVisualOnly(true);
                bolt.setPos(p);
                if (!sound.get()) bolt.setSilent(true);
                mc.level.addEntity(bolt);
            }
            case Shockwave -> {
                waves.add(new Wave(p.add(0, 0.05, 0), Fx.time(), true));
                waves.add(new Wave(p.add(0, e.getBbHeight() * 0.5, 0), Fx.time() + 0.08, false));
                Color c = rainbow.get() ? Fx.hue(rng.nextDouble(), 255) : color.get();
                var dust = new DustParticleOptions(c.getPacked() & 0xFFFFFF, 1.4f);
                for (int i = 0, n = detail(40, 12); i < n; i++) {
                    double a = rng.nextDouble() * Math.PI * 2, s = 0.2 + rng.nextDouble() * 0.4;
                    mc.particleEngine.createParticle(dust, p.x, p.y + rng.nextDouble() * e.getBbHeight(), p.z, Math.cos(a) * s, rng.nextDouble() * 0.3, Math.sin(a) * s);
                }
            }
            case Vortex -> bursts.add(new Burst(p, Fx.time(), e.getBbHeight(), false, rng.nextLong()));
            case Supernova -> {
                bursts.add(new Burst(p.add(0, e.getBbHeight() * 0.5, 0), Fx.time(), e.getBbHeight(), true, rng.nextLong()));
                waves.add(new Wave(p.add(0, 0.05, 0), Fx.time(), false));
            }
            case Hearts -> {
                for (int i = 0, n = detail(14, 5); i < n; i++) {
                    mc.particleEngine.createParticle(ParticleTypes.HEART, p.x + (rng.nextDouble() - 0.5) * 1.2, p.y + 0.3 + rng.nextDouble() * e.getBbHeight(),
                        p.z + (rng.nextDouble() - 0.5) * 1.2, 0, 0.1, 0);
                }
            }
            case Souls -> {
                var dust = new DustParticleOptions(color.get().getPacked() & 0xFFFFFF, 1f);
                for (int i = 0, n = detail(18, 6); i < n; i++) {
                    double x = p.x + (rng.nextDouble() - 0.5) * 0.8, y = p.y + rng.nextDouble() * e.getBbHeight(), z = p.z + (rng.nextDouble() - 0.5) * 0.8;
                    mc.particleEngine.createParticle(i % 3 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL, x, y, z, 0, 0.06 + rng.nextDouble() * 0.05, 0);
                    mc.particleEngine.createParticle(dust, x, y, z, 0, 0.12, 0);
                }
            }
        }
    }

    private Color tint(double h) {
        return rainbow.get() ? Fx.hue(h, 255) : color.get();
    }

    private void drawBurst(Render3DEvent event, Burst b, double now) {
        if (b.nova()) {
            double f = (now - b.start()) / 1.0;
            if (f < 0.25) glow(event.renderer, b.pos(), 0.5 + 3 * f, new Color(255, 255, 255, (int) (255 * (1 - f / 0.25))));
            Random r = new Random(b.seed());
            int n = detail(40, 12);
            double dist = (1 - Math.pow(1 - f, 2)) * 4;
            for (int i = 0; i < n; i++) {
                double z = r.nextDouble() * 2 - 1, a = r.nextDouble() * Math.PI * 2, s = Math.sqrt(1 - z * z), sp = 0.6 + r.nextDouble() * 0.4;
                Vec3 p = b.pos().add(Math.cos(a) * s * dist * sp, z * dist * sp, Math.sin(a) * s * dist * sp);
                spark(event.renderer, p, 0.25 * (1 - f), Fx.alpha(tint(now * 0.3 + (double) i / n), (int) (255 * (1 - f))), low());
            }
            return;
        }
        double f = (now - b.start()) / 1.4;
        int per = detail(12, 5);
        for (int arm = 0; arm < 3; arm++) {
            for (int k = 0; k < per; k++) {
                double along = (double) k / per, h = (f * 1.6 + along * 0.5) * (b.height() + 2.5);
                if (h > b.height() + 3) continue;
                double rad = 1.1 * (1 - along * 0.6) * (1 - f * 0.5), ang = f * 12 + along * 3 + arm * Math.PI * 2 / 3;
                Vec3 p = b.pos().add(Math.cos(ang) * rad, h, Math.sin(ang) * rad);
                spark(event.renderer, p, 0.16 * (1 - along * 0.5), Fx.alpha(tint(now * 0.3 + arm / 3.0 + along * 0.2), (int) (255 * (1 - f))), low());
            }
        }
        if (on(pillar)) beam(event.renderer, b.pos(), 0.3, (b.height() + 2) * Math.min(1, f * 3), Fx.alpha(tint(now * 0.3), (int) (120 * (1 - f))));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double now = Fx.time();
        bursts.removeIf(b -> now - b.start() > (b.nova() ? 1.0 : 1.4));
        for (Burst b : bursts) drawBurst(event, b, now);
        waves.removeIf(w -> now - w.start() > 0.7);
        for (Wave w : waves) {
            double f = (now - w.start()) / 0.7;
            if (f < 0) continue;
            double outer = 0.3 + 3 * (1 - Math.pow(1 - f, 3)), inner = Math.max(0, outer - 0.35 * (1 - f) - 0.05);
            int a = (int) (220 * (1 - f));
            ring(event.renderer, w.pos(), inner, outer, 48, u -> rainbow.get() ? Fx.hue(now * 0.3 + u, a) : Fx.alpha(color.get(), (int) (color.get().a * (1 - f))));
            if (w.pillar() && on(pillar)) {
                Color c = rainbow.get() ? Fx.hue(now * 0.3, 255) : color.get();
                beam(event.renderer, w.pos(), 0.45 * (1 - f * 0.5), 6 * (1 - Math.pow(f, 2)), Fx.alpha(c, (int) (c.a * 0.55 * (1 - f))));
                glow(event.renderer, w.pos().add(0, 1, 0), 1.2 * (1 - f) + 0.2, Fx.alpha(c, (int) (200 * (1 - f))));
            }
        }
    }
}
