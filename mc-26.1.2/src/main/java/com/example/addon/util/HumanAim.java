package com.example.addon.util;

import java.util.Random;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public final class HumanAim {
    private final Random rng = new Random();
    private float yawSpeed, pitchSpeed;

    public boolean turn(Player p, float yaw, float pitch, float maxSpeed, boolean ignoreYaw) {
        float dy = ignoreYaw ? 0 : Mth.wrapDegrees(yaw - p.getYRot()), dp = pitch - p.getXRot();
        if (Math.abs(dy) < 0.4f && Math.abs(dp) < 0.4f && Math.abs(yawSpeed) < 1 && Math.abs(pitchSpeed) < 1) {
            yawSpeed = pitchSpeed = 0;
            return true;
        }
        float k = 0.22f + rng.nextFloat() * 0.1f;
        float accel = Math.max(0.6f, maxSpeed * 0.35f);
        yawSpeed = approach(yawSpeed, Mth.clamp(dy * k, -maxSpeed, maxSpeed), accel);
        pitchSpeed = approach(pitchSpeed, Mth.clamp(dp * k * 0.85f, -maxSpeed, maxSpeed), accel);
        p.setYRot(p.getYRot() + yawSpeed + (float) rng.nextGaussian() * 0.03f);
        p.setXRot(Mth.clamp(p.getXRot() + pitchSpeed, -90, 90));
        return false;
    }

    public void reset() {
        yawSpeed = pitchSpeed = 0;
    }

    private static float approach(float v, float want, float accel) {
        return v + Mth.clamp(want - v, -accel, accel);
    }
}
