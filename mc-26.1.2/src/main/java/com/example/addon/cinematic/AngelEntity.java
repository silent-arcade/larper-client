package com.example.addon.cinematic;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.player.RemotePlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;

public class AngelEntity extends RemotePlayer {
    public static final int LIFE = 140;
    private static int nextId = -2_000_000;

    private final PlayerInfo entry;
    public final float facing;
    public int life;

    public AngelEntity(ClientLevel world, Player victim) {
        super(world, new GameProfile(UUID.randomUUID(), victim.getGameProfile().name()));
        var handler = Minecraft.getInstance().getConnection();
        entry = handler == null ? null : handler.getPlayerInfo(victim.getUUID());
        setId(nextId--);
        snapTo(victim.getX(), victim.getY() + 0.3, victim.getZ(), victim.getYRot(), 0);
        facing = victim.yBodyRot;
        setYBodyRot(victim.yBodyRot);
        setYHeadRot(victim.yBodyRot);
        yBodyRotO = yBodyRot;
        yHeadRotO = yHeadRot;
        noPhysics = true;
    }

    @Override
    protected PlayerInfo getPlayerInfo() {
        return entry;
    }

    @Override
    public void tick() {
        life++;
        tickCount++;
        xo = getX();
        yo = getY();
        zo = getZ();
        double speed = 0.015 + life * 0.0035;
        setPos(getX(), getY() + speed, getZ());
        var particles = Minecraft.getInstance().particleEngine;
        double yaw = Math.toRadians(facing);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw), bx = Math.sin(yaw), bz = -Math.cos(yaw);
        for (int side = -1; side <= 1; side += 2) {
            double tx = getX() + rx * side * 1.9 + bx * 0.7, ty = getY() + 0.9 + random.nextDouble() * 0.5, tz = getZ() + rz * side * 1.9 + bz * 0.7;
            particles.createParticle(ParticleTypes.END_ROD, tx, ty, tz, 0.0, -0.03, 0.0);
            if (random.nextInt(4) == 0) particles.createParticle(ParticleTypes.WHITE_ASH, tx - rx * side * random.nextDouble(), ty, tz - rz * side * random.nextDouble(), 0.0, -0.02, 0.0);
        }
        if (life % 3 == 0) particles.createParticle(ParticleTypes.END_ROD, getX() + (random.nextDouble() - 0.5) * 0.6, getY() + random.nextDouble() * 1.8, getZ() + (random.nextDouble() - 0.5) * 0.6, 0.0, -0.05, 0.0);
    }

    public float time(float tickDelta) {
        return (life + tickDelta) / 20f;
    }
}
