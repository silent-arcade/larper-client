package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.entity.EntityRemovedEvent;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

public class CrystalFX extends VisualModule {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> outline = sg.add(new BoolSetting.Builder()
        .name("outline").description("A theme-coloured outline around every crystal, visible through walls.").defaultValue(true).build());

    private final Setting<Boolean> recolor = sg.add(new BoolSetting.Builder()
        .name("recolor").description("Recolours the crystal itself with the theme.").defaultValue(true).build());

    private final Setting<Boolean> pops = sg.add(new BoolSetting.Builder()
        .name("pop-shockwave").description("A shockwave ring and flash where a crystal pops.").defaultValue(true).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("shockwave-size").description("How far the shockwave spreads (blocks).").defaultValue(3.5).range(0.5, 10).sliderRange(1, 6).build());

    private final Setting<Double> duration = sg.add(new DoubleSetting.Builder()
        .name("duration").description("How long a shockwave lasts (seconds).").defaultValue(0.6).range(0.1, 3).sliderRange(0.2, 1.5).build());

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colour theme: each one flows between its own colours. Custom uses the colour below.").defaultValue(FxTheme.Void).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Outline, crystal and shockwave colour.").defaultValue(new SettingColor(200, 90, 255, 200))
        .visible(() -> theme.get() == FxTheme.Custom).build());

    private record Pop(Vec3 pos, double start, Color color) {}

    private final List<Pop> active = new ArrayList<>();
    private final List<Pop> spawns = new ArrayList<>();

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> spinRing = feature("spin-ring", "A spinning ring on the ground under each crystal.");
    private final Setting<Boolean> pillar = feature("light-pillar", "A faint beam of light rising from each crystal.");
    private final Setting<Boolean> popSparks = feature("pop-sparks", "A burst of sparks flies out where a crystal pops.");
    private final Setting<Boolean> spawnRing = feature("spawn-ring", "A ring snaps in around a crystal when it's placed.");

    public CrystalFX() {
        super("crystal-fx", "End crystals get a theme-coloured outline and colour, and every pop sends out a shockwave. Only you see it.");
    }

    public boolean outline() {
        return outline.get();
    }

    public boolean recolor() {
        return recolor.get();
    }

    // RGB for one crystal, flowing through the theme over time
    public int colorFor(int key) {
        return tint(Fx.time() * 0.2 + key * 0.13).getPacked() & 0xFFFFFF;
    }

    @Override
    public void onDeactivate() {
        active.clear();
        spawns.clear();
    }

    @EventHandler
    private void onAdded(EntityAddedEvent event) {
        if (!(event.entity instanceof EndCrystal c) || mc.player == null || c.distanceToSqr(mc.player) > 64 * 64) return;
        spawns.add(new Pop(c.position(), Fx.time(), tint(Fx.time() * 0.2)));
    }

    private Color tint(double h) {
        return theme.get().color(h, 200, color.get());
    }

    @EventHandler
    private void onRemoved(EntityRemovedEvent event) {
        if (!pops.get() || !(event.entity instanceof EndCrystal c) || c.getRemovalReason() != null && c.getRemovalReason().shouldSave()) return;
        if (mc.player == null || c.distanceToSqr(mc.player) > 64 * 64) return;
        active.add(new Pop(c.position(), Fx.time(), tint(Fx.time() * 0.2)));
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null) return;
        double now = Fx.time();
        if (on(spinRing) || on(pillar)) {
            for (Entity e : mc.level.entitiesForRendering()) {
                if (!(e instanceof EndCrystal c)) continue;
                double pulse = 0.5 + 0.5 * Math.sin(now * 4 + c.getId());
                Color base = tint(now * 0.2 + c.getId() * 0.13);
                Vec3 feet = Fx.pos(c, event.tickDelta).add(0, 0.03, 0);
                if (on(spinRing)) {
                    double spin = now * 2 + c.getId();
                    ring(event.renderer, feet, 0.55, 0.66, detail(48, 16), u -> {
                        Color k = theme.get().color(u + now * 0.3, 200, color.get());
                        return Fx.alpha(k, (int) (170 * (0.35 + 0.65 * Math.max(0, Math.sin((u * 3 + spin / (2 * Math.PI)) * 2 * Math.PI)))));
                    });
                }
                if (on(pillar)) beam(event.renderer, feet, 0.12, 5, Fx.alpha(base, (int) (60 + 40 * pulse)));
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
                    Vec3 dir = new Vec3(Math.cos(yaw) * (1 - up * 0.5), up, Math.sin(yaw) * (1 - up * 0.5));
                    Vec3 at = p.pos().add(0, 0.8, 0).add(dir.scale(age * 7)).add(0, -9 * age * age, 0);
                    Color sc = k % 2 == 0 ? p.color() : theme.get().secondary(p.start() * 0.2, 255, color.get());
                    spark(event.renderer, at, 0.22 * (1 - f), Fx.alpha(sc, (int) (255 * (1 - f))), true);
                }
            }
        }
        spawns.removeIf(p -> now - p.start() > 0.35);
        if (on(spawnRing)) for (Pop p : spawns) {
            double f = (now - p.start()) / 0.35, rad = 1.8 - 1.3 * (1 - Math.pow(1 - f, 2));
            ring(event.renderer, p.pos().add(0, 0.05, 0), rad - 0.08, rad, detail(40, 12), u -> Fx.alpha(p.color(), (int) (220 * (1 - f * 0.6))));
        }
    }
}
