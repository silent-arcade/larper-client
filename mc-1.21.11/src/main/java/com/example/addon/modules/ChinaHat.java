package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
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
import net.minecraft.util.math.Vec3d;

/** The classic glowing cone hat floating over your head, slowly turning. Only you see it. */
public class ChinaHat extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> width = sg.add(new DoubleSetting.Builder()
        .name("width").description("Brim radius (blocks).").defaultValue(0.65).range(0.2, 2).sliderRange(0.3, 1.2).build());

    private final Setting<Double> tip = sg.add(new DoubleSetting.Builder()
        .name("height").description("How tall the cone is.").defaultValue(0.3).range(0.05, 1.5).sliderRange(0.1, 0.8).build());

    private final Setting<Double> spin = sg.add(new DoubleSetting.Builder()
        .name("spin").description("Turns per second of the colours.").defaultValue(0.25).range(0, 3).sliderRange(0, 1).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow around the brim.").defaultValue(true).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Hat colour when rainbow is off.").defaultValue(new SettingColor(255, 80, 160, 140))
        .visible(() -> !rainbow.get()).build());

    private final Setting<Boolean> firstPerson = sg.add(new BoolSetting.Builder()
        .name("first-person").description("Also draw it in first person (it sits just above your view).").defaultValue(false).build());

    public ChinaHat() {
        super(AddonTemplate.CATEGORY, "china-hat", "A glowing cone hat over your head. Only you see it.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (!firstPerson.get() && mc.options.getPerspective().isFirstPerson())) return;
        // Anchored to the head, not the hitbox: gliding/swimming shrinks the hitbox, which dropped the hat to the chest.
        // The hat's "up" is the top of your head (perpendicular to where you look), so it tilts with your head.
        float pitch = mc.player.getPitch(event.tickDelta), yaw = mc.player.getYaw(event.tickDelta);
        Vec3d up = Vec3d.fromPolar(pitch - 90, yaw), side = Vec3d.fromPolar(0, yaw + 90), fwd = up.crossProduct(side);
        Vec3d base = mc.player.getCameraPosVec(event.tickDelta).add(up.multiply(0.3));
        Vec3d apex = base.add(up.multiply(tip.get()));
        double r = width.get(), t = Fx.time() * spin.get();
        int segs = 40;
        for (int i = 0; i < segs; i++) {
            double f0 = (double) i / segs, f1 = (double) (i + 1) / segs;
            double a0 = f0 * Math.PI * 2, a1 = f1 * Math.PI * 2;
            Color c0 = shade(f0 + t), c1 = shade(f1 + t), top = Fx.alpha(shade(t), Math.min(255, shade(t).a + 60));
            Vec3d p0 = base.add(side.multiply(Math.cos(a0) * r)).add(fwd.multiply(Math.sin(a0) * r));
            Vec3d p1 = base.add(side.multiply(Math.cos(a1) * r)).add(fwd.multiply(Math.sin(a1) * r));
            event.depthRenderer.quad(apex.x, apex.y, apex.z, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, apex.x, apex.y, apex.z, top, c0, c1, top);
            event.depthRenderer.line(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, Fx.alpha(c0, 255)); // bright brim
        }
    }

    private Color shade(double f) {
        return rainbow.get() ? Fx.hue(f, 150) : color.get();
    }
}
