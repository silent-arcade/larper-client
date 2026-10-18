package com.example.addon.util;

import meteordevelopment.meteorclient.systems.friends.Friends;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import static meteordevelopment.meteorclient.MeteorClient.mc;

// Shared target rules for the combat modules: alive, not you, not a friend
public final class CombatTargets {
    private CombatTargets() {
    }

    public static boolean valid(Entity e, boolean players, boolean mobs) {
        if (!(e instanceof LivingEntity le) || e == mc.player || !le.isAlive() || le.isDeadOrDying()) return false;
        if (e instanceof Player p) return players && !p.isSpectator() && Friends.get().shouldAttack(p);
        return mobs && e instanceof Enemy;
    }

    // Degrees between where you look and the entity's chest
    public static double angleTo(Entity e) {
        Vec3 d = chest(e).subtract(mc.player.getEyePosition()).normalize();
        return Math.toDegrees(Math.acos(Mth.clamp(d.dot(mc.player.getLookAngle()), -1, 1)));
    }

    public static Vec3 chest(Entity e) {
        return new Vec3(e.getX(), e.getY() + e.getBbHeight() * 0.6, e.getZ());
    }

    public static float[] anglesTo(Vec3 p) {
        Vec3 d = p.subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -90, 90);
        return new float[] {yaw, pitch};
    }
}
