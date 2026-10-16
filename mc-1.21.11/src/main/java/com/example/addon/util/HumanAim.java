package com.example.addon.util;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;

import java.util.Random;

public final class HumanAim {
    private final Random rng = new Random();
    private float yawSpeed, pitchSpeed;

    public boolean turn(PlayerEntity p, float yaw, float pitch, float maxSpeed, boolean ignoreYaw) {
        float dy = ignoreYaw ? 0 : MathHelper.wrapDegrees(yaw - p.getYaw()), dp = pitch - p.getPitch();
        if (Math.abs(dy) < 0.4f && Math.abs(dp) < 0.4f && Math.abs(yawSpeed) < 1 && Math.abs(pitchSpeed) < 1) {
            yawSpeed = pitchSpeed = 0;
            return true;
        }
        float k = 0.22f + rng.nextFloat() * 0.1f;
        float accel = Math.max(0.6f, maxSpeed * 0.35f);
        yawSpeed = approach(yawSpeed, MathHelper.clamp(dy * k, -maxSpeed, maxSpeed), accel);
        pitchSpeed = approach(pitchSpeed, MathHelper.clamp(dp * k * 0.85f, -maxSpeed, maxSpeed), accel);
        p.setYaw(p.getYaw() + yawSpeed + (float) rng.nextGaussian() * 0.03f);
        p.setPitch(MathHelper.clamp(p.getPitch() + pitchSpeed, -90, 90));
        return false;
    }

    public void reset() {
        yawSpeed = pitchSpeed = 0;
    }

    private static float approach(float v, float want, float accel) {
        return v + MathHelper.clamp(want - v, -accel, accel);
    }
}
