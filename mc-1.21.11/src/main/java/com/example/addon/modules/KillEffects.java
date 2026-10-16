package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LightningEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/** A visual when something you hit dies: lightning, a shockwave, hearts or rising souls. Only you see it. */
public class KillEffects extends Module {
    public enum Style { Lightning, Shockwave, Hearts, Souls }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("The effect.").defaultValue(Style.Shockwave).build());

    private final Setting<Boolean> playersOnly = sg.add(new BoolSetting.Builder()
        .name("players-only").description("Only for player kills.").defaultValue(false).build());

    private final Setting<Boolean> sound = sg.add(new BoolSetting.Builder()
        .name("thunder-sound").description("Lightning plays its thunder.").defaultValue(true)
        .visible(() -> style.get() == Style.Lightning).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow shockwave and sparks.").defaultValue(false)
        .visible(() -> style.get() == Style.Shockwave).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Shockwave, spark and soul colour.").defaultValue(new SettingColor(255, 60, 90, 220))
        .visible(() -> (style.get() == Style.Shockwave && !rainbow.get()) || style.get() == Style.Souls).build());

    private record Wave(Vec3d pos, double start) {}

    private final Map<Integer, Double> hits = new HashMap<>(); // entity id -> when you last hit it
    private final List<Wave> waves = new ArrayList<>();
    private final Random rng = new Random();

    public KillEffects() {
        super(AddonTemplate.CATEGORY, "kill-effects", "Lightning, shockwave, hearts or souls when you kill something. Only you see it.");
    }

    @Override
    public void onDeactivate() {
        hits.clear();
        waves.clear();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof LivingEntity) hits.put(event.entity.getId(), Fx.time());
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null) return;
        double now = Fx.time();
        hits.entrySet().removeIf(h -> {
            if (now - h.getValue() > 3) return true; // only kills within 3 s of your hit count
            Entity e = mc.world.getEntityById(h.getKey());
            if (!(e instanceof LivingEntity le) || !(le.isDead() || le.getHealth() <= 0)) return false;
            if (!playersOnly.get() || e instanceof PlayerEntity) play(le);
            return true;
        });
    }

    private void play(LivingEntity e) {
        Vec3d p = e.getEntityPos();
        switch (style.get()) {
            case Lightning -> {
                LightningEntity bolt = new LightningEntity(EntityType.LIGHTNING_BOLT, mc.world);
                bolt.setCosmetic(true);
                bolt.setPosition(p);
                if (!sound.get()) bolt.setSilent(true);
                mc.world.addEntity(bolt);
            }
            case Shockwave -> {
                waves.add(new Wave(p.add(0, 0.05, 0), Fx.time()));
                waves.add(new Wave(p.add(0, e.getHeight() * 0.5, 0), Fx.time() + 0.08));
                Color c = rainbow.get() ? Fx.hue(rng.nextDouble(), 255) : color.get();
                var dust = new DustParticleEffect(c.getPacked() & 0xFFFFFF, 1.4f);
                for (int i = 0; i < 40; i++) {
                    double a = rng.nextDouble() * Math.PI * 2, s = 0.2 + rng.nextDouble() * 0.4;
                    mc.particleManager.addParticle(dust, p.x, p.y + rng.nextDouble() * e.getHeight(), p.z, Math.cos(a) * s, rng.nextDouble() * 0.3, Math.sin(a) * s);
                }
            }
            case Hearts -> {
                for (int i = 0; i < 14; i++) {
                    mc.particleManager.addParticle(ParticleTypes.HEART, p.x + (rng.nextDouble() - 0.5) * 1.2, p.y + 0.3 + rng.nextDouble() * e.getHeight(),
                        p.z + (rng.nextDouble() - 0.5) * 1.2, 0, 0.1, 0);
                }
            }
            case Souls -> {
                var dust = new DustParticleEffect(color.get().getPacked() & 0xFFFFFF, 1f);
                for (int i = 0; i < 18; i++) {
                    double x = p.x + (rng.nextDouble() - 0.5) * 0.8, y = p.y + rng.nextDouble() * e.getHeight(), z = p.z + (rng.nextDouble() - 0.5) * 0.8;
                    mc.particleManager.addParticle(i % 3 == 0 ? ParticleTypes.SCULK_SOUL : ParticleTypes.SOUL, x, y, z, 0, 0.06 + rng.nextDouble() * 0.05, 0);
                    mc.particleManager.addParticle(dust, x, y, z, 0, 0.12, 0);
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double now = Fx.time();
        waves.removeIf(w -> now - w.start() > 0.7);
        for (Wave w : waves) {
            double f = (now - w.start()) / 0.7;
            if (f < 0) continue;
            double outer = 0.3 + 3 * (1 - Math.pow(1 - f, 3)), inner = Math.max(0, outer - 0.35 * (1 - f) - 0.05);
            int a = (int) (220 * (1 - f));
            Fx.ring(event.renderer, w.pos(), inner, outer, 48, u -> rainbow.get() ? Fx.hue(now * 0.3 + u, a) : Fx.alpha(color.get(), (int) (color.get().a * (1 - f))));
        }
    }
}
