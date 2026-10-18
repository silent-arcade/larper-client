package com.example.addon.util;

import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.mob.Monster;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import static meteordevelopment.meteorclient.MeteorClient.mc;

// Shared target rules for the combat modules: alive, not you, not a friend
public final class CombatTargets {
    private CombatTargets() {
    }

    public static boolean valid(Entity e, boolean players, boolean mobs) {
        if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive() || le.isDead()) return false;
        if (e instanceof PlayerEntity p) return players && !p.isSpectator() && Friends.get().shouldAttack(p);
        return mobs && e instanceof Monster;
    }

    // Degrees between where you look and the entity's chest
    public static double angleTo(Entity e) {
        Vec3d d = chest(e).subtract(mc.player.getEyePos()).normalize();
        return Math.toDegrees(Math.acos(MathHelper.clamp(d.dotProduct(mc.player.getRotationVector()), -1, 1)));
    }

    public static Vec3d chest(Entity e) {
        return new Vec3d(e.getX(), e.getY() + e.getHeight() * 0.6, e.getZ());
    }

    public static float[] anglesTo(Vec3d p) {
        Vec3d d = p.subtract(mc.player.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -90, 90);
        return new float[] {yaw, pitch};
    }
}
