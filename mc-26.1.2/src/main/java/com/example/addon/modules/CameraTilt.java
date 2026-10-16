package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

public class CameraTilt extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> strafe = sg.add(new DoubleSetting.Builder()
        .name("strafe-tilt").description("Degrees of lean when strafing at normal speed.").defaultValue(3).range(0, 15).sliderRange(0, 8).build());

    private final Setting<Double> turn = sg.add(new DoubleSetting.Builder()
        .name("turn-tilt").description("Degrees of lean when turning fast with the mouse.").defaultValue(2).range(0, 15).sliderRange(0, 8).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("smoothness").description("How fast the lean follows. Lower = softer.").defaultValue(8).range(1, 30).sliderRange(2, 20).build());

    private final Setting<Boolean> thirdPerson = sg.add(new BoolSetting.Builder()
        .name("third-person").description("Also tilt in third person.").defaultValue(true).build());

    private double roll;
    private float lastYaw;
    private long last;

    public CameraTilt() {
        super(AddonTemplate.VISUAL, "camera-tilt", "The camera leans into strafes and turns, like a racing game.");
    }

    @Override
    public void onActivate() {
        roll = 0;
        last = 0;
    }

    public float roll(Entity e, float tickProgress, boolean thirdPersonView) {
        long now = System.nanoTime();
        double dt = last == 0 ? 0.016 : Math.min(0.1, (now - last) / 1e9);
        last = now;
        float yaw = e.getViewYRot(tickProgress);
        double turnRate = Mth.wrapDegrees(yaw - lastYaw) / Math.max(dt, 1e-3);
        lastYaw = yaw;

        double target = 0;
        if (!thirdPersonView || thirdPerson.get()) {
            double rad = Math.toRadians(yaw);
            Vec3 right = new Vec3(-Math.cos(rad), 0, -Math.sin(rad));
            double side = e.getDeltaMovement().dot(right) / 0.12;
            target = Mth.clamp(side, -1.5, 1.5) * strafe.get() + Mth.clamp(turnRate / 360, -1.5, 1.5) * turn.get();
        }
        roll += (target - roll) * (1 - Math.exp(-dt * speed.get()));
        return (float) roll;
    }
}
