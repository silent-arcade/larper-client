package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class DamageNumbers extends VisualModule {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> range = sg.add(new IntSetting.Builder()
        .name("range").description("Show numbers for entities within this many blocks.").defaultValue(24).range(4, 64).sliderRange(8, 48).build());

    private final Setting<Double> scale = sg.add(new DoubleSetting.Builder()
        .name("scale").description("Number size.").defaultValue(2.4).range(0.5, 4).sliderRange(0.6, 3).build());

    private final Setting<Double> duration = sg.add(new DoubleSetting.Builder()
        .name("duration").description("How long a number floats (seconds).").defaultValue(1.2).range(0.3, 4).sliderRange(0.5, 2.5).build());

    private final Setting<Boolean> hearts = sg.add(new BoolSetting.Builder()
        .name("in-hearts").description("Show hearts (half the health points), like the health bar.").defaultValue(false).build());

    private final Setting<Boolean> heals = sg.add(new BoolSetting.Builder()
        .name("heals").description("Also show healing, with a +.").defaultValue(true).build());

    private final Setting<SettingColor> damageColor = sg.add(new ColorSetting.Builder()
        .name("damage-color").description("Normal hits.").defaultValue(new SettingColor(255, 235, 90)).build());

    private final Setting<SettingColor> bigColor = sg.add(new ColorSetting.Builder()
        .name("big-hit-color").description("Hits of 6+ health (3 hearts).").defaultValue(new SettingColor(255, 120, 40)).build());

    private final Setting<SettingColor> killColor = sg.add(new ColorSetting.Builder()
        .name("kill-color").description("The hit that kills.").defaultValue(new SettingColor(255, 50, 60)).build());

    private final Setting<SettingColor> healColor = sg.add(new ColorSetting.Builder()
        .name("heal-color").description("Healing.").defaultValue(new SettingColor(90, 255, 120)).visible(heals::get).build());

    private record Pop(Vec3d pos, Vec3d drift, String text, Color color, double start, boolean big) {}

    private final Map<Integer, Float> health = new HashMap<>();
    private final List<Pop> pops = new ArrayList<>();
    private final Random rng = new Random();

    private final Setting<Boolean> shakeOn = feature("big-hit-shake", "Big hits and kills shake for a moment as they pop.");

    public DamageNumbers() {
        super("damage-numbers", "Floating damage numbers when mobs and players get hit. Only you see them.");
    }

    @Override
    public void onDeactivate() {
        health.clear();
        pops.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        Map<Integer, Float> seen = new HashMap<>();
        double r2 = range.get() * range.get();
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || e.squaredDistanceTo(mc.player) > r2) continue;
            float now = le.getHealth(), was = health.getOrDefault(e.getId(), now);
            seen.put(e.getId(), now);
            float diff = was - now;
            if (Math.abs(diff) < 0.05f || (diff < 0 && !heals.get())) continue;
            boolean kill = now <= 0, big = diff >= 6;
            Color c = diff < 0 ? healColor.get() : kill ? killColor.get() : big ? bigColor.get() : damageColor.get();
            double shown = Math.abs(diff) / (hearts.get() ? 2 : 1);
            String text = (diff < 0 ? "+" : "") + (shown == Math.floor(shown) ? String.valueOf((int) shown) : String.format("%.1f", shown)) + (hearts.get() ? "❤" : "");
            Vec3d at = e.getEntityPos().add((rng.nextDouble() - 0.5) * e.getWidth(), e.getHeight() * 0.85, (rng.nextDouble() - 0.5) * e.getWidth());
            Vec3d drift = new Vec3d((rng.nextDouble() - 0.5) * 0.6, 0.9, (rng.nextDouble() - 0.5) * 0.6);
            pops.add(new Pop(at, drift, text, c, Fx.time(), big || kill));
            if (low() && pops.size() > 12) pops.remove(0);
        }
        health.clear();
        health.putAll(seen);
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        double now = Fx.time(), life = duration.get();
        pops.removeIf(p -> now - p.start() > life);
        TextRenderer text = TextRenderer.get();
        for (Pop p : pops) {
            double f = (now - p.start()) / life;
            double rise = 1 - Math.pow(1 - f, 2);
            Vec3d at = p.pos().add(p.drift().multiply(rise));
            Vector3d v = new Vector3d(at.x, at.y, at.z);
            double pop = f < 0.12 ? 0.6 + f / 0.12 * 0.6 : 1.2 - Math.min(0.2, (f - 0.12) * 0.6);
            double s = scale.get() * (p.big() ? 1.35 : 1) * pop;
            if (!NametagUtils.to2D(v, s)) continue;
            int alpha = (int) (p.color().a * (f < 0.7 ? 1 : 1 - (f - 0.7) / 0.3));
            NametagUtils.begin(v);
            text.begin(1, false, true);
            double shake = p.big() && on(shakeOn) && f < 0.25 ? (1 - f / 0.25) * 2.5 : 0;
            double sx = shake * Math.sin(now * 97 + p.start() * 13), sy = shake * Math.cos(now * 83 + p.start() * 7);
            text.render(p.text(), -text.getWidth(p.text(), true) / 2 + sx, sy, Fx.alpha(p.color(), alpha), true);
            text.end();
            NametagUtils.end();
        }
    }
}
