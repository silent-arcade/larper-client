package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class SmoothCamera extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("follow-speed").description("How fast your view catches up with the mouse. Lower = smoother and floatier.").defaultValue(12).range(1, 60).sliderRange(2, 30).build());

    private double pendX, pendY;
    private long last;

    public SmoothCamera() {
        super(AddonTemplate.VISUAL, "smooth-camera", "Buttery mouse look: your view glides into where you point instead of snapping.");
    }

    @Override
    public void onActivate() {
        pendX = pendY = 0;
        last = 0;
    }

    public double[] filter(double dx, double dy) {
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        pendX += dx;
        pendY += dy;
        double f = 1 - Math.exp(-dt * speed.get());
        double ox = pendX * f, oy = pendY * f;
        pendX -= ox;
        pendY -= oy;
        return new double[] {ox, oy};
    }
}
