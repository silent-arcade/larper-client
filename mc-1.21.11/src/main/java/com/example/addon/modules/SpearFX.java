package com.example.addon.modules;

import com.example.addon.visuals.Fx;
import com.example.addon.visuals.VisualModule;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpearFX extends VisualModule {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> others = sg.add(new BoolSetting.Builder()
        .name("other-players").description("Show the effects for other players' spears too, not just yours.").defaultValue(true).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow colours instead of the colour below.").defaultValue(false).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Colour of the spear effects.").defaultValue(new SettingColor(90, 200, 255, 220))
        .visible(() -> !rainbow.get()).build());

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> tipGlow = feature("tip-glow", "The spear tip glows, brighter and bigger while charging.");
    private final Setting<Boolean> chargeStreaks = feature("charge-streaks", "Speed streaks trail behind you while charging, more the faster you go.");
    private final Setting<Boolean> jabTrail = feature("jab-trail", "A thrust line shoots out along every jab.");
    private final Setting<Boolean> lungeLines = feature("lunge-lines", "Speed lines and a ground ring when you lunge forward.");
    private final Setting<Boolean> hitBurst = feature("hit-burst", "An impact ring and sparks on whatever the spear hits.");

    private record Burst(Vec3d pos, Vec3d dir, double start, Color color) {}

    private final List<Burst> jabs = new ArrayList<>(), lunges = new ArrayList<>(), hits = new ArrayList<>();
    private final Map<Integer, Integer> swingTicks = new HashMap<>();
    private final Map<Integer, Integer> hurt = new HashMap<>();
    private final Map<Integer, Vec3d> lastPos = new HashMap<>();
    private final Map<Integer, Double> lastLunge = new HashMap<>();

    public SpearFX() {
        super("spear-fx", "Effects for spears: glowing tip, charge streaks, jab trails, lunge lines and hit bursts. Only you see them.");
    }

    @Override
    public void onDeactivate() {
        jabs.clear();
        lunges.clear();
        hits.clear();
        swingTicks.clear();
        hurt.clear();
        lastPos.clear();
        lastLunge.clear();
    }

    private Color tint(double h) {
        return rainbow.get() ? Fx.hue(h, 220) : color.get();
    }

    private static boolean spear(ItemStack s) {
        return s.isIn(ItemTags.SPEARS);
    }

    private boolean watched(PlayerEntity p) {
        return spear(p.getMainHandStack()) && (p == mc.player || others.get()) && p.squaredDistanceTo(mc.player) < 64 * 64;
    }

    private static Vec3d tip(PlayerEntity p, float delta) {
        Vec3d look = Vec3d.fromPolar(p.getPitch(delta), p.getYaw(delta));
        return p.getCameraPosVec(delta).add(0, -0.25, 0).add(look.multiply(p.isUsingItem() ? 1.9 : 1.4));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        double now = Fx.time();
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!watched(p)) {
                swingTicks.remove(p.getId());
                lastPos.remove(p.getId());
                continue;
            }
            Vec3d look = p.getRotationVector();
            int ticks = p.handSwinging ? p.handSwingTicks : -1;
            Integer before = swingTicks.put(p.getId(), ticks);
            if (ticks >= 0 && (before == null || before < 0 || ticks < before)) jabs.add(new Burst(p.getEyePos().add(0, -0.25, 0), look, now, tint(now * 0.2)));
            Vec3d prev = lastPos.put(p.getId(), p.getEntityPos());
            if (prev != null) {
                Vec3d move = p.getEntityPos().subtract(prev);
                double horizontal = Math.hypot(move.x, move.z);
                if (horizontal > 0.75 && horizontal < 2.5 && now - lastLunge.getOrDefault(p.getId(), 0.0) > 0.6) {
                    lunges.add(new Burst(prev, new Vec3d(move.x, 0, move.z).normalize(), now, tint(now * 0.2)));
                    lastLunge.put(p.getId(), now);
                }
            }
        }
        hurt.keySet().removeIf(id -> mc.world.getEntityById(id) == null);
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le)) continue;
            Integer was = hurt.put(le.getId(), le.hurtTime);
            if (le.hurtTime == 0 || was == null || le.hurtTime <= was) continue;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != le && watched(p) && p.squaredDistanceTo(le) < 7 * 7) {
                    hits.add(new Burst(le.getBoundingBox().getCenter(), le.getEntityPos().subtract(p.getEntityPos()).normalize(), now, tint(now * 0.2)));
                    break;
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null || mc.player == null) return;
        double now = Fx.time();
        float d = event.tickDelta;
        for (PlayerEntity p : mc.world.getPlayers()) {
            if (!watched(p)) continue;
            boolean charging = p.isUsingItem() && spear(p.getActiveItem());
            Color c = tint(now * 0.2 + p.getId() * 0.1);
            Vec3d tip = tip(p, d);
            double speed = p.getVelocity().horizontalLength();
            if (on(tipGlow) && (p != mc.player || !mc.options.getPerspective().isFirstPerson() || charging)) {
                double pulse = 0.5 + 0.5 * Math.sin(now * (charging ? 12 : 4));
                glow(event.renderer, tip, (charging ? 0.32 + 0.4 * Math.min(1, speed * 3) : 0.18) * (0.85 + 0.3 * pulse), Fx.alpha(c, (int) (c.a * (charging ? 0.9 : 0.55))));
            }
            if (on(chargeStreaks) && charging && speed > 0.08) {
                Vec3d back = p.getVelocity().multiply(1, 0, 1).normalize().multiply(-1);
                Vec3d body = Fx.pos(p, d).add(0, 1.0, 0);
                int n = (int) Math.min(14, 4 + speed * 30);
                for (int k = 0; k < n; k++) {
                    double life = (now * 3 + k * 0.137) % 1, side = Math.sin(k * 2.4) * 0.5, up = Math.cos(k * 1.7) * 0.7;
                    Vec3d right = back.crossProduct(new Vec3d(0, 1, 0));
                    Vec3d at = body.add(right.multiply(side)).add(0, up, 0).add(back.multiply(0.4 + life * 2.2));
                    spark(event.renderer, at, 0.25 * (1 - life), Fx.alpha(c, (int) (200 * (1 - life))), true);
                }
            }
        }
        jabs.removeIf(b -> now - b.start() > 0.25);
        if (on(jabTrail)) for (Burst b : jabs) {
            double f = (now - b.start()) / 0.25, reach = 0.8 + 3.2 * (1 - Math.pow(1 - f, 3));
            for (int k = 0; k < 14; k++) {
                double t = 0.6 + reach * k / 13.0;
                glow(event.renderer, b.pos().add(b.dir().multiply(t)), 0.06 + 0.07 * (k / 13.0), Fx.alpha(b.color(), (int) (220 * (1 - f) * (0.25 + 0.75 * k / 13.0))));
            }
            glow(event.renderer, b.pos().add(b.dir().multiply(0.6 + reach)), 0.32 * (1 - f * 0.5), Fx.alpha(b.color(), (int) (240 * (1 - f))));
        }
        lunges.removeIf(b -> now - b.start() > 0.5);
        if (on(lungeLines)) for (Burst b : lunges) {
            double f = (now - b.start()) / 0.5;
            shockwave(event.renderer, b.pos(), f, 1.6, Fx.alpha(b.color(), 200));
            Vec3d right = b.dir().crossProduct(new Vec3d(0, 1, 0));
            for (int k = 0; k < 10; k++) {
                double side = (k % 5 - 2) * 0.3, up = 0.3 + (k / 5) * 1.0;
                Vec3d at = b.pos().add(right.multiply(side)).add(0, up, 0).add(b.dir().multiply(0.5 + f * 3));
                spark(event.renderer, at, 0.6 * (1 - f), Fx.alpha(b.color(), (int) (220 * (1 - f))), true);
            }
        }
        hits.removeIf(b -> now - b.start() > 0.4);
        if (on(hitBurst)) for (Burst b : hits) {
            double f = (now - b.start()) / 0.4;
            glow(event.renderer, b.pos(), 0.5 + 0.6 * f, Fx.alpha(b.color(), (int) (240 * (1 - f))));
            for (int k = 0; k < 10; k++) {
                double yaw = k * 2.39996, up = Math.sin(k * 1.3) * 0.6;
                Vec3d dir = new Vec3d(Math.cos(yaw), up, Math.sin(yaw)).add(b.dir().multiply(0.8)).normalize();
                spark(event.renderer, b.pos().add(dir.multiply(0.2 + f * 1.6)), 0.18 * (1 - f), Fx.alpha(b.color(), (int) (255 * (1 - f))), true);
            }
        }
    }
}
