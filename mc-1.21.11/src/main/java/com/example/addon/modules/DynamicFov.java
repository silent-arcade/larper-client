package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.GetFovEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class DynamicFov extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> start = sg.add(new DoubleSetting.Builder()
        .name("start-speed").description("Speed (blocks per second) where the FOV starts widening. Sprinting is about 5.6.").defaultValue(5).range(0, 40).sliderRange(0, 15).build());

    private final Setting<Double> perBlock = sg.add(new DoubleSetting.Builder()
        .name("strength").description("Extra degrees of FOV per block per second above the start speed.").defaultValue(1.5).range(0.1, 10).sliderRange(0.2, 5).build());

    private final Setting<Double> max = sg.add(new DoubleSetting.Builder()
        .name("max-extra").description("Most extra degrees it can add.").defaultValue(25).range(1, 60).sliderRange(5, 40).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("smoothness").description("How fast the FOV follows your speed. Lower = smoother.").defaultValue(4).range(0.5, 20).sliderRange(1, 10).build());

    private double extra;
    private long last;

    public DynamicFov() {
        super(AddonTemplate.VISUAL, "dynamic-fov", "Your FOV smoothly widens the faster you go: sprinting, falling, elytra.");
    }

    @Override
    public void onActivate() {
        extra = 0;
        last = 0;
    }

    public double extra() {
        return extra;
    }

    @EventHandler
    private void onFov(GetFovEvent event) {
        if (mc.player == null) return;
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        double bps = mc.player.getVelocity().length() * 20;
        double target = Math.min(max.get(), Math.max(0, bps - start.get()) * perBlock.get());
        extra += (target - extra) * (1 - Math.exp(-dt * speed.get()));
        event.fov += (float) extra;
    }
}
