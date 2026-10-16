package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
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
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

public class TargetCircle extends VisualModule {
    public enum Style { Ring, Triple, Pillar }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Ring: one ring sliding up and down. Triple: three rings turning different ways. Pillar: a beam of light around them.")
        .defaultValue(Style.Ring).build());

    private final Setting<Boolean> onLook = sg.add(new BoolSetting.Builder()
        .name("on-crosshair").description("Also circle the mob or player under your crosshair, not just the one you hit.").defaultValue(false).build());

    private final Setting<Double> keep = sg.add(new DoubleSetting.Builder()
        .name("keep").description("Seconds to keep circling after your last hit.").defaultValue(4).range(0.5, 30).sliderRange(1, 10).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("speed").description("How fast it slides up and down.").defaultValue(1).range(0.1, 4).sliderRange(0.3, 2.5).build());

    private final Setting<Double> trail = sg.add(new DoubleSetting.Builder()
        .name("trail").description("Height of the fading glow behind the ring.").defaultValue(0.35).range(0, 1).sliderRange(0, 0.8).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow around the ring.").defaultValue(false).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Ring colour.").defaultValue(new SettingColor(170, 90, 255, 230)).visible(() -> !rainbow.get()).build());

    private Entity target;
    private double hitAt;

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> sparksOn = feature("sparks", "Sparks racing around the ring.");
    private final Setting<Boolean> counterRing = feature("counter-ring", "Ring style: a second faint ring sliding the other way.");
    private final Setting<Boolean> punchOn = feature("hit-punch", "The ring swells for a moment when you hit.");
    private final Setting<Boolean> pillarSparks = feature("rising-sparks", "Pillar style: sparks floating up the beam.");

    public TargetCircle() {
        super("target-circle", "A glowing ring, triple reticle or light pillar around whoever you're fighting. Only you see it.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (event.entity instanceof LivingEntity) {
            target = event.entity;
            hitAt = Fx.time();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        Entity e = target != null && target.isAlive() && Fx.time() - hitAt < keep.get() ? target : null;
        if (e == null && onLook.get() && mc.crosshairTarget instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity) e = hit.getEntity();
        if (e == null) return;

        Vec3d base = Fx.pos(e, event.tickDelta);
        double t = Fx.time() * speed.get() * 2;
        double wave = (Math.sin(t) + 1) / 2, h = e.getHeight();
        double y = base.y + 0.05 + wave * (h - 0.1);
        double dir = Math.cos(t) >= 0 ? -1 : 1;
        if (style.get() != Style.Ring) {
            drawStyle(event, e, base);
            return;
        }
        double sinceHit = Fx.time() - hitAt, punch = on(punchOn) && sinceHit < 0.25 ? 1 + 0.35 * (1 - sinceHit / 0.25) : 1;
        double r = (e.getWidth() * 0.75 + 0.15) * punch, hue = Fx.time() * 0.25;
        int segs = detail(48, 16);
        for (int i = 0; i < segs; i++) {
            double f0 = (double) i / segs, f1 = (double) (i + 1) / segs;
            double a0 = f0 * Math.PI * 2, a1 = f1 * Math.PI * 2;
            Color c0 = col(hue + f0), c1 = col(hue + f1);
            double x0 = base.x + Math.cos(a0) * r, z0 = base.z + Math.sin(a0) * r, x1 = base.x + Math.cos(a1) * r, z1 = base.z + Math.sin(a1) * r;
            if (!low()) {
                double ty = y + dir * trail.get();
                event.renderer.quad(x0, y, z0, x1, y, z1, x1, ty, z1, x0, ty, z0, Fx.alpha(c0, c0.a / 2), Fx.alpha(c1, c1.a / 2), Fx.alpha(c1, 0), Fx.alpha(c0, 0));
            }
            event.renderer.line(x0, y, z0, x1, y, z1, c0, c1);
        }
        if (on(sparksOn)) for (int k = 0; k < 3; k++) {
            double a = Fx.time() * 3 + k * Math.PI * 2 / 3;
            spark(event.renderer, new Vec3d(base.x + Math.cos(a) * r, y, base.z + Math.sin(a) * r), 0.16, col(hue + k / 3.0), false);
        }
        double y2 = base.y + 0.05 + (1 - wave) * (h - 0.1);
        if (on(counterRing)) ring(event.renderer, new Vec3d(base.x, y2, base.z), r * 0.92, r * 0.97, 40, u -> Fx.alpha(col(hue + 0.5 + u), 90));
    }

    private void drawStyle(Render3DEvent event, Entity e, Vec3d base) {
        double now = Fx.time(), sinceHit = now - hitAt, punch = on(punchOn) && sinceHit < 0.25 ? 1 + 0.35 * (1 - sinceHit / 0.25) : 1;
        double r = (e.getWidth() * 0.75 + 0.15) * punch, h = e.getHeight(), hue = now * 0.25;
        if (style.get() == Style.Triple) {
            for (int k = 0; k < 3; k++) {
                double y = base.y + h * (0.15 + 0.35 * k) + 0.08 * Math.sin(now * 3 + k);
                double spin = now * speed.get() * (k % 2 == 0 ? 2 : -2.6) + k;
                double rr = r * (1 - 0.12 * k);
                int segs = detail(40, 12);
                for (int i = 0; i < segs; i++) {
                    double f0 = (double) i / segs, f1 = (double) (i + 1) / segs;
                    if ((f0 * 3) % 1 > 0.8) continue;
                    double a0 = spin + f0 * Math.PI * 2, a1 = spin + f1 * Math.PI * 2;
                    Color c0 = col(hue + f0 + k * 0.2);
                    event.renderer.line(base.x + Math.cos(a0) * rr, y, base.z + Math.sin(a0) * rr, base.x + Math.cos(a1) * rr, y, base.z + Math.sin(a1) * rr, c0);
                }
                if (on(sparksOn)) spark(event.renderer, new Vec3d(base.x + Math.cos(spin) * rr, y, base.z + Math.sin(spin) * rr), 0.14, col(hue + k * 0.2), false);
            }
        } else {
            Color c = col(hue);
            beam(event.renderer, base.add(0, 0.02, 0), r, h + 1.2, Fx.alpha(c, (int) (c.a * 0.35)));
            ring(event.renderer, base.add(0, 0.03, 0), r * 0.92, r, 40, u -> col(hue + u));
            if (on(pillarSparks)) {
                for (int k = 0; k < 6; k++) {
                    double life = (now * 0.8 + k / 6.0) % 1, a = k * 1.05 + now;
                    spark(event.renderer, base.add(Math.cos(a) * r * 0.8, life * (h + 1), Math.sin(a) * r * 0.8), 0.1 * (1 - life), Fx.alpha(col(hue + k / 6.0), (int) (230 * (1 - life))), false);
                }
            }
        }
    }

    private Color col(double h) {
        return rainbow.get() ? Fx.hue(h, 230) : color.get();
    }
}
