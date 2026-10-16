package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.renderer.Renderer2D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;

import java.util.Random;

public class AnimeFX extends VisualModule {
    private final SettingGroup sgSpeed = settings.createGroup("Speed lines");
    private final SettingGroup sgImpact = settings.createGroup("Impact frames");

    private final Setting<Boolean> speedLines = sgSpeed.add(new BoolSetting.Builder()
        .name("speed-lines").description("Lines rushing in from the screen edges when you move fast.").defaultValue(true).build());

    private final Setting<Double> minSpeed = sgSpeed.add(new DoubleSetting.Builder()
        .name("start-speed").description("Speed (blocks per second) where the lines start (sprinting is about 5.6).").defaultValue(5).range(1, 40).sliderRange(3, 20)
        .visible(speedLines::get).build());

    private final Setting<Integer> lineCount = sgSpeed.add(new IntSetting.Builder()
        .name("lines").description("How many lines at full speed.").defaultValue(70).range(10, 300).sliderRange(20, 160)
        .visible(speedLines::get).build());

    private final Setting<SettingColor> lineColor = sgSpeed.add(new ColorSetting.Builder()
        .name("line-color").description("Speed line colour.").defaultValue(new SettingColor(255, 255, 255, 170))
        .visible(speedLines::get).build());

    private final Setting<Boolean> impact = sgImpact.add(new BoolSetting.Builder()
        .name("impact-frames").description("A flash and a burst of lines when you hit something.").defaultValue(true).build());

    private final Setting<Boolean> critsOnly = sgImpact.add(new BoolSetting.Builder()
        .name("crits-only").description("Only on critical hits (falling while you hit).").defaultValue(false)
        .visible(impact::get).build());

    private final Setting<SettingColor> flashColor = sgImpact.add(new ColorSetting.Builder()
        .name("flash-color").description("Impact flash colour.").defaultValue(new SettingColor(255, 255, 255, 120))
        .visible(impact::get).build());

    private final Setting<SettingColor> burstColor = sgImpact.add(new ColorSetting.Builder()
        .name("burst-color").description("Impact burst line colour.").defaultValue(new SettingColor(20, 20, 30, 220))
        .visible(impact::get).build());

    private final Random rng = new Random();
    private double impactAt = -1, speedSmooth;
    private long seed;

    private final Setting<Boolean> vignetteOn = feature("vignette", "Speed lines: the screen edges darken as you speed up.");
    private final Setting<Boolean> bars = feature("impact-bars", "Impact frames: black cinematic bars snap in.");

    public AnimeFX() {
        super("anime-fx", "Manga speed lines when you go fast and impact frames when you hit. Only you see them.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!impact.get() || mc.player == null) return;
        boolean crit = mc.player.fallDistance > 0 && !mc.player.onGround() && !mc.player.onClimbable() && !mc.player.isInWater();
        if (critsOnly.get() && !crit) return;
        impactAt = Fx.time();
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.player == null || mc.options.hideGui) return;
        double w = mc.getWindow().getWidth(), h = mc.getWindow().getHeight(), cx = w / 2, cy = h / 2;
        double now = Fx.time();
        Renderer2D r = Renderer2D.COLOR;
        r.begin();

        if (speedLines.get()) {
            double speed = mc.player.getDeltaMovement().length() * 20;
            speedSmooth += (speed - speedSmooth) * 0.15;
            double amount = Math.min(1, (speedSmooth - minSpeed.get()) / 6);
            if (amount > 0) {
                Random lines = new Random(seed = (long) (now * 20));
                int n = (int) (detail(lineCount.get(), 10) * amount);
                if (on(vignetteOn)) vignette(r, w, h, (int) (150 * amount));
                double reach = Math.hypot(cx, cy);
                for (int i = 0; i < n; i++) {
                    double a = lines.nextDouble() * Math.PI * 2;
                    double inner = reach * (0.55 + lines.nextDouble() * 0.3 - amount * 0.15);
                    double width = 0.004 + lines.nextDouble() * 0.012;
                    wedge(r, cx, cy, a, inner, reach * 1.05, width, Fx.alpha(lineColor.get(), (int) (lineColor.get().a * (0.4 + 0.6 * amount))));
                }
            }
        }

        if (impactAt >= 0) {
            double f = (now - impactAt) / 0.22;
            if (f >= 1) impactAt = -1;
            else {
                Color flash = flashColor.get();
                r.quad(0, 0, w, h, Fx.alpha(flash, (int) (flash.a * (1 - f))));
                Random burst = new Random((long) (impactAt * 1000));
                double reach = Math.hypot(cx, cy);
                if (on(bars)) {
                    double bar = h * 0.09 * (1 - f * f);
                    r.quad(0, 0, w, bar, new Color(0, 0, 0, 255));
                    r.quad(0, h - bar, w, bar, new Color(0, 0, 0, 255));
                }
                for (int i = 0, bursts = detail(26, 10); i < bursts; i++) {
                    double a = burst.nextDouble() * Math.PI * 2;
                    double inner = reach * (0.12 + burst.nextDouble() * 0.25 + f * 0.3);
                    wedge(r, cx, cy, a, inner, reach * 1.05, 0.01 + burst.nextDouble() * 0.03, Fx.alpha(burstColor.get(), (int) (burstColor.get().a * (1 - f))));
                }
            }
        }

        r.end();
        r.render();
    }

    private static void vignette(Renderer2D r, double w, double h, int alpha) {
        Color dark = new Color(0, 0, 0, alpha), clear = new Color(0, 0, 0, 0);
        double bw = w * 0.22, bh = h * 0.22;
        r.quad(0, 0, w, bh, dark, dark, clear, clear);
        r.quad(0, h - bh, w, bh, clear, clear, dark, dark);
        r.quad(0, 0, bw, h, dark, clear, clear, dark);
        r.quad(w - bw, 0, bw, h, clear, dark, dark, clear);
    }

    private static void wedge(Renderer2D r, double cx, double cy, double angle, double inner, double outer, double width, Color c) {
        double x0 = cx + Math.cos(angle) * inner, y0 = cy + Math.sin(angle) * inner;
        double x1 = cx + Math.cos(angle - width) * outer, y1 = cy + Math.sin(angle - width) * outer;
        double x2 = cx + Math.cos(angle + width) * outer, y2 = cy + Math.sin(angle + width) * outer;
        r.triangle(x0, y0, x1, y1, x2, y2, c);
        r.triangle(x0, y0, x2, y2, x1, y1, c);
    }
}
