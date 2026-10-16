package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class CrystalFX extends VisualModule {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> glow = sg.add(new BoolSetting.Builder()
        .name("glow").description("A soft pulsing glow around every crystal.").defaultValue(true).build());

    private final Setting<Boolean> pops = sg.add(new BoolSetting.Builder()
        .name("pop-shockwave").description("A shockwave ring and flash where a crystal pops.").defaultValue(true).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("shockwave-size").description("How far the shockwave spreads (blocks).").defaultValue(3.5).range(0.5, 10).sliderRange(1, 6).build());

    private final Setting<Double> duration = sg.add(new DoubleSetting.Builder()
        .name("duration").description("How long a shockwave lasts (seconds).").defaultValue(0.6).range(0.1, 3).sliderRange(0.2, 1.5).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow colours instead of the colour below.").defaultValue(false).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Glow and shockwave colour.").defaultValue(new SettingColor(200, 90, 255, 200))
        .visible(() -> !rainbow.get()).build());

    private record Pop(Vec3d pos, double start, Color color) {}

    private final List<Pop> active = new ArrayList<>();
    private final List<Pop> spawns = new ArrayList<>();

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> orbitSparks = feature("orbit-sparks", "Two sparks orbiting each crystal.");
    private final Setting<Boolean> spinRing = feature("spin-ring", "A spinning ring on the ground under each crystal.");
    private final Setting<Boolean> pillar = feature("light-pillar", "A faint beam of light rising from each crystal.");
    private final Setting<Boolean> popSparks = feature("pop-sparks", "A burst of sparks flies out where a crystal pops.");
    private final Setting<Boolean> spawnRing = feature("spawn-ring", "A ring snaps in around a crystal when it's placed.");

    public CrystalFX() {
        super("crystal-fx", "End crystals glow, and every pop sends out a shockwave. Only you see it.");
    }

    @Override
    public void onDeactivate() {
        active.clear();
        spawns.clear();
    }

    @EventHandler
    private void onAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof EndCrystalEntity c) || mc.player == null || c.squaredDistanceTo(mc.player) > 64 * 64) return;
        spawns.add(new Pop(c.getEntityPos(), Fx.time(), tint(Fx.time() * 0.2)));
    }

    private Color tint(double h) {
        return rainbow.get() ? Fx.hue(h, 200) : color.get();
    }

    @EventHandler
    private void onRemoved(EntityRemovedEvent event) {
        if (!pops.get() || !(event.entity instanceof EndCrystalEntity c) || c.getRemovalReason() != null && c.getRemovalReason().shouldSave()) return;
        if (mc.player == null || c.squaredDistanceTo(mc.player) > 64 * 64) return;
        active.add(new Pop(c.getEntityPos(), Fx.time(), tint(Fx.time() * 0.2)));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;
        double now = Fx.time();
        if (glow.get()) {
            for (Entity e : mc.world.getEntities()) {
                if (!(e instanceof EndCrystalEntity c)) continue;
                double pulse = 0.5 + 0.5 * Math.sin(now * 4 + c.getId());
                Vec3d mid = Fx.pos(c, event.tickDelta).add(0, 1.0 + 0.15 * Math.sin(now * 2 + c.getId()), 0);
                Color base = tint(now * 0.2 + c.getId() * 0.13);
                glow(event.renderer, mid, 0.9 + 0.25 * pulse, Fx.alpha(base, (int) (base.a * (0.45 + 0.35 * pulse))));
                Vec3d feet = Fx.pos(c, event.tickDelta).add(0, 0.03, 0);
                if (on(spinRing)) {
                    double spin = now * 2 + c.getId();
                    ring(event.renderer, feet, 0.55, 0.66, detail(48, 16), u -> {
                        Color k = rainbow.get() ? Fx.hue(u + now * 0.3, 200) : base;
                        return Fx.alpha(k, (int) (170 * (0.35 + 0.65 * Math.max(0, Math.sin((u * 3 + spin / (2 * Math.PI)) * 2 * Math.PI)))));
                    });
                }
                if (on(pillar)) beam(event.renderer, feet, 0.12, 5, Fx.alpha(base, (int) (60 + 40 * pulse)));
                if (!on(orbitSparks)) continue;
                for (int k = 0; k < 2; k++) {
                    double a = now * 3 + k * Math.PI + c.getId();
                    spark(event.renderer, mid.add(Math.cos(a) * 0.75, Math.sin(a * 1.5) * 0.35 * (k == 0 ? 1 : -1), Math.sin(a) * 0.75), 0.14, Fx.alpha(base, 240), false);
                }
            }
        }
        active.removeIf(p -> now - p.start() > Math.max(duration.get(), 0.9));
        for (Pop p : active) {
            double age = now - p.start();
            if (age < duration.get()) shockwave(event.renderer, p.pos(), age / duration.get(), size.get(), p.color());
            if (on(popSparks) && age < 0.9) {
                double f = age / 0.9;
                for (int k = 0; k < 18; k++) {
                    double yaw = k * 2.39996 + p.start() * 7, up = 0.35 + 0.6 * ((k * 0.618) % 1);
                    Vec3d dir = new Vec3d(Math.cos(yaw) * (1 - up * 0.5), up, Math.sin(yaw) * (1 - up * 0.5));
                    Vec3d at = p.pos().add(0, 0.8, 0).add(dir.multiply(age * 7)).add(0, -9 * age * age, 0);
                    spark(event.renderer, at, 0.22 * (1 - f), Fx.alpha(p.color(), (int) (255 * (1 - f))), true);
                }
            }
        }
        spawns.removeIf(p -> now - p.start() > 0.35);
        if (on(spawnRing)) for (Pop p : spawns) {
            double f = (now - p.start()) / 0.35, rad = 1.8 - 1.3 * (1 - Math.pow(1 - f, 2));
            ring(event.renderer, p.pos().add(0, 0.05, 0), rad - 0.08, rad, detail(40, 12), u -> Fx.alpha(p.color(), (int) (220 * (1 - f * 0.6))));
            if (f > 0.75) glow(event.renderer, p.pos().add(0, 1, 0), 0.8, Fx.alpha(p.color(), (int) (200 * (1 - f) / 0.25)));
        }
    }
}
