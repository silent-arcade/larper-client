package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
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
import net.minecraft.util.math.Vec3d;

public class ChinaHat extends VisualModule {
    public enum Style { Cone, Crown, Halo }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Cone: the classic China hat. Crown: a spiked crown with glowing gems. Halo: a floating ring of light.")
        .defaultValue(Style.Cone).build());

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

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> tipGlow = feature("tip-glow", "Glowing tip (Cone), gems (Crown) and beads (Halo).");
    private final Setting<Boolean> sparkles = feature("sparkles", "Sparkles riding around the brim or halo.");

    public ChinaHat() {
        super("china-hat", "A glowing cone hat, spiked crown or halo over your head. Only you see it.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (!firstPerson.get() && mc.options.getPerspective().isFirstPerson())) return;
        float pitch = mc.player.getPitch(event.tickDelta), yaw = mc.player.getYaw(event.tickDelta);
        Vec3d up = Vec3d.fromPolar(pitch - 90, yaw), side = Vec3d.fromPolar(0, yaw + 90), fwd = up.crossProduct(side);
        Vec3d base = mc.player.getCameraPosVec(event.tickDelta).add(up.multiply(0.3));
        Vec3d apex = base.add(up.multiply(tip.get()));
        double r = width.get(), t = Fx.time() * spin.get();
        if (style.get() != Style.Cone) {
            drawStyle(event, base, up, side, fwd, r, t);
            return;
        }
        int segs = detail(40, 14);
        for (int i = 0; i < segs; i++) {
            double f0 = (double) i / segs, f1 = (double) (i + 1) / segs;
            double a0 = f0 * Math.PI * 2, a1 = f1 * Math.PI * 2;
            Color c0 = shade(f0 + t), c1 = shade(f1 + t), top = Fx.alpha(shade(t), Math.min(255, shade(t).a + 60));
            Vec3d p0 = base.add(side.multiply(Math.cos(a0) * r)).add(fwd.multiply(Math.sin(a0) * r));
            Vec3d p1 = base.add(side.multiply(Math.cos(a1) * r)).add(fwd.multiply(Math.sin(a1) * r));
            event.depthRenderer.quad(apex.x, apex.y, apex.z, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, apex.x, apex.y, apex.z, top, c0, c1, top);
            event.depthRenderer.line(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, Fx.alpha(c0, 255));
        }
        if (on(tipGlow)) glow(event.depthRenderer, apex, 0.07, Fx.alpha(shade(t), 230));
        if (on(sparkles)) for (int k = 0; k < 3; k++) {
            double a = Fx.time() * 2.2 + k * Math.PI * 2 / 3;
            Vec3d p = base.add(side.multiply(Math.cos(a) * r)).add(fwd.multiply(Math.sin(a) * r));
            spark(event.depthRenderer, p, 0.09, Fx.alpha(shade(t + k / 3.0), 255), false);
        }
    }

    private void drawStyle(Render3DEvent event, Vec3d base, Vec3d up, Vec3d side, Vec3d fwd, double r, double t) {
        if (style.get() == Style.Halo) {
            Vec3d c = base.add(up.multiply(0.25 + 0.05 * Math.sin(Fx.time() * 2)));
            double hr = r * 0.55;
            int segs = detail(48, 16);
            for (int i = 0; i < segs; i++) {
                double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
                Vec3d p0 = c.add(side.multiply(Math.cos(a0) * hr)).add(fwd.multiply(Math.sin(a0) * hr));
                Vec3d p1 = c.add(side.multiply(Math.cos(a1) * hr)).add(fwd.multiply(Math.sin(a1) * hr));
                Color c0 = shade((double) i / segs + t);
                event.depthRenderer.line(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, Fx.alpha(c0, 255));
                if (on(tipGlow) && i % 4 == 0) glow(event.depthRenderer, p0, 0.06, c0);
            }
            if (on(sparkles)) spark(event.depthRenderer, c.add(side.multiply(Math.cos(Fx.time() * 3) * hr)).add(fwd.multiply(Math.sin(Fx.time() * 3) * hr)), 0.12, Fx.alpha(shade(t), 255), false);
            return;
        }
        double cr = r * 0.5, band = 0.08, spikeH = tip.get() * 0.9;
        int spikes = 8;
        for (int k = 0; k < spikes; k++) {
            double a0 = Math.PI * 2 * k / spikes, a1 = Math.PI * 2 * (k + 1) / spikes, am = (a0 + a1) / 2;
            Vec3d b0 = base.add(side.multiply(Math.cos(a0) * cr)).add(fwd.multiply(Math.sin(a0) * cr));
            Vec3d b1 = base.add(side.multiply(Math.cos(a1) * cr)).add(fwd.multiply(Math.sin(a1) * cr));
            Vec3d t0 = b0.add(up.multiply(band)), t1 = b1.add(up.multiply(band));
            Vec3d peak = base.add(side.multiply(Math.cos(am) * cr)).add(fwd.multiply(Math.sin(am) * cr)).add(up.multiply(band + spikeH));
            Color c = shade((double) k / spikes + t), dim = Fx.alpha(c, c.a * 2 / 3);
            event.depthRenderer.quad(b0.x, b0.y, b0.z, b1.x, b1.y, b1.z, t1.x, t1.y, t1.z, t0.x, t0.y, t0.z, dim, dim, c, c);
            event.depthRenderer.quad(t0.x, t0.y, t0.z, t1.x, t1.y, t1.z, peak.x, peak.y, peak.z, peak.x, peak.y, peak.z, c, c, Fx.alpha(c, 255), Fx.alpha(c, 255));
            event.depthRenderer.line(t0.x, t0.y, t0.z, peak.x, peak.y, peak.z, Fx.alpha(c, 255));
            if (on(tipGlow)) glow(event.depthRenderer, peak, 0.05 * (1 + 0.3 * Math.sin(Fx.time() * 4 + k)), Fx.alpha(c, 255));
        }
    }

    private Color shade(double f) {
        return rainbow.get() ? Fx.hue(f, 150) : color.get();
    }
}
