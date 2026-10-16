package com.example.addon.cinematic;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.OtherClientPlayerEntity;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.particle.ParticleTypes;

import java.util.UUID;

/**
 * A client-only copy of a killed player wearing their skin, rising into the sky as an angel.
 * It has its own random UUID so it never clashes with the real player when they respawn; the skin comes from
 * the real player's tab-list entry.
 */
public class AngelEntity extends OtherClientPlayerEntity {
    public static final int LIFE = 140; // ticks
    private static int nextId = -2_000_000;

    private final PlayerListEntry entry;
    public final float facing;
    public int life;

    public AngelEntity(ClientWorld world, PlayerEntity victim) {
        super(world, new GameProfile(UUID.randomUUID(), victim.getGameProfile().name()));
        var handler = MinecraftClient.getInstance().getNetworkHandler();
        entry = handler == null ? null : handler.getPlayerListEntry(victim.getUuid());
        setId(nextId--);
        refreshPositionAndAngles(victim.getX(), victim.getY() + 0.3, victim.getZ(), victim.getYaw(), 0);
        facing = victim.bodyYaw;
        setBodyYaw(victim.bodyYaw);
        setHeadYaw(victim.bodyYaw);
        lastBodyYaw = bodyYaw;
        lastHeadYaw = headYaw;
        noClip = true;
    }

    @Override
    protected PlayerListEntry getPlayerListEntry() {
        return entry;
    }

    // Not driven by the server: just float up, faster and faster, trailing light
    @Override
    public void tick() {
        life++;
        age++;
        lastX = getX();
        lastY = getY();
        lastZ = getZ();
        double speed = 0.015 + life * 0.0035;
        setPosition(getX(), getY() + speed, getZ());
        var particles = MinecraftClient.getInstance().particleManager;
        // Light trailing from both wingtips, and the odd feather drifting down
        double yaw = Math.toRadians(facing);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw), bx = Math.sin(yaw), bz = -Math.cos(yaw);
        for (int side = -1; side <= 1; side += 2) {
            double tx = getX() + rx * side * 1.9 + bx * 0.7, ty = getY() + 0.9 + random.nextDouble() * 0.5, tz = getZ() + rz * side * 1.9 + bz * 0.7;
            particles.addParticle(ParticleTypes.END_ROD, tx, ty, tz, 0.0, -0.03, 0.0);
            if (random.nextInt(4) == 0) particles.addParticle(ParticleTypes.WHITE_ASH, tx - rx * side * random.nextDouble(), ty, tz - rz * side * random.nextDouble(), 0.0, -0.02, 0.0);
        }
        if (life % 3 == 0) particles.addParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - 0.5) * 0.6, 0.0, -0.05, 0.0);
    }

    public float time(float tickDelta) {
        return (life + tickDelta) / 20f;
    }
}
