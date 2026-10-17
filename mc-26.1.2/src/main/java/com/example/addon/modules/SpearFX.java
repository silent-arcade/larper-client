package com.example.addon.modules;

import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import com.example.addon.visuals.VisualModule;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SpearFX extends VisualModule {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> others = sg.add(new BoolSetting.Builder()
        .name("other-players").description("Show the effects for other players' spears too, not just yours.").defaultValue(true).build());

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colour theme: each one flows between its own colours. Custom uses the colour below.").defaultValue(FxTheme.Frost).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Colour of the spear effects.").defaultValue(new SettingColor(90, 200, 255, 220))
        .visible(() -> theme.get() == FxTheme.Custom).build());

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> tipGlow = feature("tip-outline", "An outlined cube spins on the spear tip, bigger while charging.");
    private final Setting<Boolean> chargeStreaks = feature("charge-streaks", "Speed streaks trail behind you while charging, more the faster you go.");
    private final Setting<Boolean> jabTrail = feature("jab-trail", "A thrust line shoots out along every jab.");
    private final Setting<Boolean> lungeLines = feature("lunge-lines", "Speed lines and a ground ring when you lunge forward.");
    private final Setting<Boolean> hitBurst = feature("hit-burst", "An impact ring and sparks on whatever the spear hits.");

    private record Burst(Vec3 pos, Vec3 dir, double start, Color color) {}

    private final List<Burst> jabs = new ArrayList<>(), lunges = new ArrayList<>(), hits = new ArrayList<>();
    private final Map<Integer, Integer> swingTicks = new HashMap<>();
    private final Map<Integer, Integer> hurt = new HashMap<>();
    private final Map<Integer, Vec3> lastPos = new HashMap<>();
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
        return theme.get().color(h, 220, color.get());
    }

    private static boolean spear(ItemStack s) {
        return s.is(ItemTags.SPEARS);
    }

    private boolean watched(Player p) {
        return spear(p.getMainHandItem()) && (p == mc.player || others.get()) && p.distanceToSqr(mc.player) < 64 * 64;
    }

    private static Vec3 tip(Player p, float delta) {
        Vec3 look = Vec3.directionFromRotation(p.getViewXRot(delta), p.getViewYRot(delta));
        return p.getEyePosition(delta).add(0, -0.25, 0).add(look.scale(p.isUsingItem() ? 1.9 : 1.4));
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        double now = Fx.time();
        for (Player p : mc.level.players()) {
            if (!watched(p)) {
                swingTicks.remove(p.getId());
                lastPos.remove(p.getId());
                continue;
            }
            Vec3 look = p.getLookAngle();
            int ticks = p.swinging ? p.swingTime : -1;
            Integer before = swingTicks.put(p.getId(), ticks);
            if (ticks >= 0 && (before == null || before < 0 || ticks < before)) jabs.add(new Burst(p.getEyePosition().add(0, -0.25, 0), look, now, tint(now * 0.2)));
            Vec3 prev = lastPos.put(p.getId(), p.position());
            if (prev != null) {
                Vec3 move = p.position().subtract(prev);
                double horizontal = Math.hypot(move.x, move.z);
                if (horizontal > 0.75 && horizontal < 2.5 && now - lastLunge.getOrDefault(p.getId(), 0.0) > 0.6) {
                    lunges.add(new Burst(prev, new Vec3(move.x, 0, move.z).normalize(), now, tint(now * 0.2)));
                    lastLunge.put(p.getId(), now);
                }
            }
        }
        hurt.keySet().removeIf(id -> mc.level.getEntity(id) == null);
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le)) continue;
            Integer was = hurt.put(le.getId(), le.hurtTime);
            if (le.hurtTime == 0 || was == null || le.hurtTime <= was) continue;
            for (Player p : mc.level.players()) {
                if (p != le && watched(p) && p.distanceToSqr(le) < 7 * 7) {
                    hits.add(new Burst(le.getBoundingBox().getCenter(), le.position().subtract(p.position()).normalize(), now, tint(now * 0.2)));
                    break;
                }
            }
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null || mc.player == null) return;
        double now = Fx.time();
        float d = event.tickDelta;
        for (Player p : mc.level.players()) {
            if (!watched(p)) continue;
            boolean charging = p.isUsingItem() && spear(p.getUseItem());
            Color c = tint(now * 0.2 + p.getId() * 0.1);
            Vec3 tip = tip(p, d);
            double speed = p.getDeltaMovement().horizontalDistance();
            if (on(tipGlow) && (p != mc.player || !mc.options.getCameraType().isFirstPerson() || charging)) {
                double pulse = 0.5 + 0.5 * Math.sin(now * (charging ? 12 : 4));
                outline(event.renderer, tip, (charging ? 0.14 + 0.16 * Math.min(1, speed * 3) : 0.08) * (0.85 + 0.3 * pulse), Fx.alpha(c, charging ? 255 : 180));
            }
            if (on(chargeStreaks) && charging && speed > 0.08) {
                Vec3 back = p.getDeltaMovement().multiply(1, 0, 1).normalize().scale(-1);
                Vec3 body = Fx.pos(p, d).add(0, 1.0, 0);
                int n = (int) Math.min(14, 4 + speed * 30);
                for (int k = 0; k < n; k++) {
                    double life = (now * 3 + k * 0.137) % 1, side = Math.sin(k * 2.4) * 0.5, up = Math.cos(k * 1.7) * 0.7;
                    Vec3 right = back.cross(new Vec3(0, 1, 0));
                    Vec3 at = body.add(right.scale(side)).add(0, up, 0).add(back.scale(0.4 + life * 2.2));
                    spark(event.renderer, at, 0.25 * (1 - life), Fx.alpha(c, (int) (200 * (1 - life))), true);
                }
            }
        }
        jabs.removeIf(b -> now - b.start() > 0.25);
        if (on(jabTrail)) for (Burst b : jabs) {
            double f = (now - b.start()) / 0.25, reach = 0.8 + 3.2 * (1 - Math.pow(1 - f, 3));
            Vec3 from = b.pos().add(b.dir().scale(0.6)), to = b.pos().add(b.dir().scale(0.6 + reach));
            event.renderer.line(from.x, from.y, from.z, to.x, to.y, to.z, Fx.alpha(b.color(), 40), Fx.alpha(b.color(), (int) (255 * (1 - f))));
            outline(event.renderer, to, 0.12 * (1 - f * 0.5), Fx.alpha(b.color(), (int) (240 * (1 - f))));
        }
        lunges.removeIf(b -> now - b.start() > 0.5);
        if (on(lungeLines)) for (Burst b : lunges) {
            double f = (now - b.start()) / 0.5;
            shockwave(event.renderer, b.pos(), f, 1.6, Fx.alpha(b.color(), 200));
            Vec3 right = b.dir().cross(new Vec3(0, 1, 0));
            for (int k = 0; k < 10; k++) {
                double side = (k % 5 - 2) * 0.3, up = 0.3 + (k / 5) * 1.0;
                Vec3 at = b.pos().add(right.scale(side)).add(0, up, 0).add(b.dir().scale(0.5 + f * 3));
                spark(event.renderer, at, 0.6 * (1 - f), Fx.alpha(b.color(), (int) (220 * (1 - f))), true);
            }
        }
        hits.removeIf(b -> now - b.start() > 0.4);
        if (on(hitBurst)) for (Burst b : hits) {
            double f = (now - b.start()) / 0.4;
            outline(event.renderer, b.pos(), 0.25 + 0.35 * f, Fx.alpha(b.color(), (int) (240 * (1 - f))));
            for (int k = 0; k < 10; k++) {
                double yaw = k * 2.39996, up = Math.sin(k * 1.3) * 0.6;
                Vec3 dir = new Vec3(Math.cos(yaw), up, Math.sin(yaw)).add(b.dir().scale(0.8)).normalize();
                Color sc = k % 2 == 0 ? b.color() : theme.get().secondary(b.start() * 0.2, 255, color.get());
                spark(event.renderer, b.pos().add(dir.scale(0.2 + f * 1.6)), 0.18 * (1 - f), Fx.alpha(sc, (int) (255 * (1 - f))), true);
            }
        }
    }
}
