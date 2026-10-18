package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.orbit.EventHandler;

public class Drugs extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> strength = sg.add(new DoubleSetting.Builder()
        .name("strength").description("How trippy it gets.").defaultValue(1).range(0.1, 3).sliderRange(0.2, 2).build());

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colours the screen cycles through. Rainbow = every colour.").defaultValue(FxTheme.Rainbow).build());

    private final Setting<Double> fadeIn = sg.add(new DoubleSetting.Builder()
        .name("kick-in").description("Seconds it takes to build up to full strength after you turn it on.").defaultValue(4).range(0, 30).sliderRange(0, 15).build());

    private final Setting<Boolean> colors = sg.add(new BoolSetting.Builder()
        .name("color-wash").description("The whole screen slowly shifts colour.").defaultValue(true).build());

    private final Setting<Boolean> waves = sg.add(new BoolSetting.Builder()
        .name("color-waves").description("Bands of colour roll down the screen.").defaultValue(true).build());

    private final Setting<Boolean> vignette = sg.add(new BoolSetting.Builder()
        .name("vignette").description("The edges of the screen pulse dark and glow.").defaultValue(true).build());

    private final Setting<Boolean> wobble = sg.add(new BoolSetting.Builder()
        .name("wobble").description("The camera sways and tilts.").defaultValue(true).build());

    private final Setting<Boolean> breathe = sg.add(new BoolSetting.Builder()
        .name("breathe").description("Your field of view pulses in and out.").defaultValue(true).build());

    // colour used if the Custom theme is picked (this module has no colour picker)
    private static final Color FALLBACK = new Color(255, 90, 255, 255);
    private double startedAt;

    public Drugs() {
        super(AddonTemplate.VISUAL, "drugs", "Trippy screen that builds up: colour wash, rolling colour waves, a pulsing vignette, a swaying camera and breathing FOV. Only you see it.");
    }

    @Override
    public void onActivate() {
        startedAt = Fx.time();
    }

    private double level() {
        double ramp = fadeIn.get() <= 0 ? 1 : Math.min(1, (Fx.time() - startedAt) / fadeIn.get());
        return strength.get() * ramp * ramp * (3 - 2 * ramp);
    }

    // camera roll in degrees, read by the camera mixin
    public float roll() {
        double t = Fx.time();
        return wobble.get() ? (float) ((Math.sin(t * 0.9) * 6 + Math.sin(t * 2.3) * 2) * level()) : 0;
    }

    @EventHandler
    private void onFov(GetFovEvent event) {
        if (breathe.get()) event.fov += (float) ((Math.sin(Fx.time() * 1.6) * 8 + Math.sin(Fx.time() * 0.37) * 5) * level());
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.options.hideGui) return;
        double t = Fx.time(), lv = level();
        int w = mc.getWindow().getGuiScaledWidth(), h = mc.getWindow().getGuiScaledHeight();
        if (colors.get()) {
            Color c = theme.get().color(t * 0.12, (int) Math.min(110, 38 * lv), FALLBACK);
            event.graphics.fill(0, 0, w, h, c.getPacked());
        }
        if (waves.get()) {
            int bands = 12, bh = h / bands + 1;
            for (int i = 0; i < bands; i++) {
                double phase = i / (double) bands - t * 0.18;
                int a = (int) Math.min(90, (12 + 18 * Math.sin(phase * Math.PI * 2 + t)) * lv);
                if (a <= 0) continue;
                event.graphics.fill(0, i * bh, w, i * bh + bh, theme.get().color(phase, a, FALLBACK).getPacked());
            }
        }
        if (vignette.get()) {
            int steps = 12, step = Math.max(2, Math.min(w, h) / 36);
            double pulse = 0.6 + 0.4 * Math.sin(t * 2.1);
            int rgb = theme.get().color(t * 0.2 + 0.5, 255, FALLBACK).getPacked() & 0xFFFFFF;
            // stacked translucent bands, darkest at the very edge, so it fades smoothly inwards
            for (int i = 0; i < steps; i++) {
                double k = 1 - i / (double) steps;
                int a = (int) (Math.min(200, 150 * lv * pulse) * k * k), in = i * step, c = rgb | a << 24;
                event.graphics.fill(in, in, w - in, in + step, c);
                event.graphics.fill(in, h - in - step, w - in, h - in, c);
                event.graphics.fill(in, in + step, in + step, h - in - step, c);
                event.graphics.fill(w - in - step, in + step, w - in, h - in - step, c);
                if (a <= 0) break;
            }
        }
    }
}
