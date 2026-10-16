package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.Vec3d;

/** A glowing ring that slides up and down whoever you're fighting, trailing a fading glow. Only you see it. */
public class TargetCircle extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

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

    public TargetCircle() {
        super(AddonTemplate.CATEGORY, "target-circle", "A glowing ring around whoever you're fighting. Only you see it.");
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
        double dir = Math.cos(t) >= 0 ? -1 : 1; // trail hangs behind the way the ring is moving
        double r = e.getWidth() * 0.75 + 0.15, hue = Fx.time() * 0.25;
        int segs = 48;
        for (int i = 0; i < segs; i++) {
            double f0 = (double) i / segs, f1 = (double) (i + 1) / segs;
            double a0 = f0 * Math.PI * 2, a1 = f1 * Math.PI * 2;
            Color c0 = col(hue + f0), c1 = col(hue + f1);
            double x0 = base.x + Math.cos(a0) * r, z0 = base.z + Math.sin(a0) * r, x1 = base.x + Math.cos(a1) * r, z1 = base.z + Math.sin(a1) * r;
            double ty = y + dir * trail.get();
            event.renderer.quad(x0, y, z0, x1, y, z1, x1, ty, z1, x0, ty, z0, Fx.alpha(c0, c0.a / 2), Fx.alpha(c1, c1.a / 2), Fx.alpha(c1, 0), Fx.alpha(c0, 0));
            event.renderer.line(x0, y, z0, x1, y, z1, c0, c1);
        }
    }

    private Color col(double h) {
        return rainbow.get() ? Fx.hue(h, 230) : color.get();
    }
}
