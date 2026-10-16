package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.cinematic.AngelEntity;
import com.example.addon.cinematic.CinePose;
import meteordevelopment.meteorclient.events.game.GameLeftEvent;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.particle.BlockStateParticleEffect;
import net.minecraft.particle.DustParticleEffect;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CinematicDeath extends VisualModule {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Boolean> onlyMine = sgGeneral.add(new BoolSetting.Builder()
        .name("only-my-kills").description("Only for things you killed. Off = every death you see.")
        .defaultValue(true).build());

    private final Setting<Boolean> sword = sgGeneral.add(new BoolSetting.Builder()
        .name("sword").description("Leaves a diamond sword stuck in the victim.")
        .defaultValue(true).build());

    public enum Targets { Both, Mobs, Players }

    private final Setting<Targets> targets = sgGeneral.add(new EnumSetting.Builder<Targets>()
        .name("targets").description("Who gets the death cinematic: mobs, players, or both.")
        .defaultValue(Targets.Both).build());

    private final Setting<Boolean> blood = sgGeneral.add(new BoolSetting.Builder()
        .name("blood").description("Blood spray from the wound, drips, and a splatter when the body lands.")
        .defaultValue(true).build());

    private final Setting<Boolean> sounds = sgGeneral.add(new BoolSetting.Builder()
        .name("sounds").description("The stab, a slowing heartbeat and the thud of the body. Only you hear them.")
        .defaultValue(true).build());

    private final Setting<Boolean> angels = sgGeneral.add(new BoolSetting.Builder()
        .name("angels").description("Killed players rise into the sky as an angel with their skin.")
        .defaultValue(true).build());

    private final Map<Integer, Long> hits = new HashMap<>();
    private final Map<Integer, Long> started = new HashMap<>();
    private final Map<Integer, Float> deathYaws = new HashMap<>();
    private final Map<Integer, Float> lastT = new HashMap<>();
    private final Map<Integer, Entity.RemovalReason> held = new HashMap<>();
    private final List<Integer> angelDone = new ArrayList<>();
    private final List<AngelEntity> flying = new ArrayList<>();
    private boolean releasing;

    public CinematicDeath() {
        super("cinematic-death", "Knife in the liver, a last look, then they go limp. Players become angels.");
    }

    @Override
    public void onDeactivate() {
        releaseAll();
        for (AngelEntity a : flying) if (mc.world != null) mc.world.removeEntity(a.getId(), Entity.RemovalReason.DISCARDED);
        flying.clear();
        hits.clear();
        started.clear();
        angelDone.clear();
    }

    @EventHandler
    private void onGameLeft(GameLeftEvent event) {
        held.clear();
        flying.clear();
        hits.clear();
        started.clear();
        angelDone.clear();
    }

    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityDamageS2CPacket p)) return;
        mc.execute(() -> {
            if (mc.player != null && p.sourceCauseId() == mc.player.getId()) hits.put(p.entityId(), System.nanoTime());
        });
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.world == null || mc.player == null) return;
        long now = System.nanoTime();
        hits.values().removeIf(t -> now - t > 3_000_000_000L);

        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof LivingEntity le) || e == mc.player || e instanceof AngelEntity) continue;
            if (started.containsKey(e.getId()) || !(le.isDead() || le.deathTime > 0)) continue;
            if (onlyMine.get() && !hits.containsKey(e.getId())) continue;
            if (e instanceof PlayerEntity ? targets.get() == Targets.Mobs : targets.get() == Targets.Players) continue;
            started.put(e.getId(), now);
            deathYaws.put(e.getId(), le.bodyYaw);
        }

        for (var it = started.entrySet().iterator(); it.hasNext(); ) {
            var en = it.next();
            int id = en.getKey();
            float t = (now - en.getValue()) / 1e9f;
            float last = lastT.getOrDefault(id, -1f);
            lastT.put(id, t);
            Entity e = mc.world.getEntityById(id);
            if (e instanceof LivingEntity le && t < CinePose.END) effects(le, last, t);
            if (e instanceof PlayerEntity p && angels.get() && t >= CinePose.ANGEL_AT && !angelDone.contains(id)) {
                angelDone.add(id);
                AngelEntity angel = new AngelEntity(mc.world, p);
                mc.world.addEntity(angel);
                flying.add(angel);
            }
            if (t >= CinePose.END) release(id);
            if (t >= CinePose.END + 5 && e == null) {
                it.remove();
                deathYaws.remove(id);
                lastT.remove(id);
                angelDone.remove((Integer) id);
            }
        }

        for (var it = flying.iterator(); it.hasNext(); ) {
            AngelEntity a = it.next();
            if (a.life < AngelEntity.LIFE) continue;
            mc.world.removeEntity(a.getId(), Entity.RemovalReason.DISCARDED);
            it.remove();
        }
    }

    private static final DustParticleEffect MIST = new DustParticleEffect(0x8A0303, 1.3f);
    private static final BlockStateParticleEffect BLOOD = new BlockStateParticleEffect(ParticleTypes.BLOCK, Blocks.REDSTONE_BLOCK.getDefaultState());
    private static final BlockStateParticleEffect DRIP = new BlockStateParticleEffect(ParticleTypes.FALLING_DUST, Blocks.REDSTONE_BLOCK.getDefaultState());

    private static boolean crossed(float last, float t, float at) {
        return last < at && t >= at;
    }

    private void effects(LivingEntity e, float last, float t) {
        double yaw = Math.toRadians(deathYaw(e));
        double fx = -Math.sin(yaw), fz = Math.cos(yaw);
        double rx = -Math.cos(yaw), rz = -Math.sin(yaw);
        double h = e.getHeight(), w = e.getWidth();
        double wx = e.getX() + fx * w * 0.5 + rx * 0.12, wy = e.getY() + h * 0.55, wz = e.getZ() + fz * w * 0.5 + rz * 0.12;

        if (crossed(last, t, 0)) {
            if (sounds.get()) {
                sound(e, SoundEvents.ITEM_TRIDENT_HIT, 1.0f, 0.75f);
                sound(e, SoundEvents.ENTITY_PLAYER_ATTACK_CRIT, 0.8f, 0.6f);
            }
            if (blood.get()) for (int i = 0, n = detail(26, 8); i < n; i++) {
                double sp = 0.08 + Math.random() * 0.18;
                particle(i % 4 == 0 ? MIST : BLOOD, wx, wy, wz, fx * sp + (Math.random() - 0.5) * 0.12, Math.random() * 0.12, fz * sp + (Math.random() - 0.5) * 0.12);
            }
        }
        if (sounds.get()) for (float beat : new float[]{0.35f, 0.62f, 1.05f, 1.32f}) {
            if (crossed(last, t, beat)) sound(e, SoundEvents.BLOCK_NOTE_BLOCK_BASEDRUM.value(), beat < 1 ? 0.7f : 0.45f, 0.5f);
        }
        if (blood.get() && t > 0.2f && t < 1.15f && Math.random() < (low() ? 0.2 : 0.6)) {
            particle(DRIP, wx + (Math.random() - 0.5) * 0.08, wy - 0.05, wz + (Math.random() - 0.5) * 0.08, 0, 0, 0);
        }
        if (crossed(last, t, 2.05f)) {
            double gx = e.getX() + fx * h * 0.45, gz = e.getZ() + fz * h * 0.45;
            if (sounds.get()) sound(e, SoundEvents.ENTITY_PLAYER_BIG_FALL, 1.0f, 0.8f);
            if (blood.get()) for (int i = 0, n = detail(30, 10); i < n; i++) {
                double a = Math.random() * Math.PI * 2, sp = 0.05 + Math.random() * 0.12;
                particle(BLOOD, gx, e.getY() + 0.1, gz, Math.cos(a) * sp, 0.05 + Math.random() * 0.08, Math.sin(a) * sp);
            }
            BlockState ground = mc.world.getBlockState(e.getBlockPos().down());
            if (!ground.isAir()) for (int i = 0, n = detail(18, 6); i < n; i++) {
                particle(new BlockStateParticleEffect(ParticleTypes.BLOCK, ground), gx + (Math.random() - 0.5) * h * 0.6, e.getY() + 0.05, gz + (Math.random() - 0.5) * h * 0.6, 0, 0.1, 0);
            }
        }
    }

    private void sound(LivingEntity e, SoundEvent sound, float volume, float pitch) {
        mc.world.playSoundClient(e.getX(), e.getY(), e.getZ(), sound, SoundCategory.PLAYERS, volume, pitch, false);
    }

    private void particle(ParticleEffect effect, double x, double y, double z, double vx, double vy, double vz) {
        mc.particleManager.addParticle(effect, x, y, z, vx, vy, vz);
    }

    public float time(LivingEntity e) {
        Long start = started.get(e.getId());
        if (start == null) return -1;
        return Math.min(CinePose.END, (System.nanoTime() - start) / 1e9f);
    }

    public boolean inCinematic(int id) {
        Long start = started.get(id);
        return start != null && (System.nanoTime() - start) / 1e9f < CinePose.END;
    }

    public boolean hold(int id, Entity.RemovalReason reason) {
        if (releasing || !isActive() || !inCinematic(id)) return false;
        held.put(id, reason);
        return true;
    }

    private void release(int id) {
        Entity.RemovalReason reason = held.remove(id);
        if (reason == null || mc.world == null) return;
        if (mc.world.getEntityById(id) instanceof LivingEntity le) le.addDeathParticles();
        releasing = true;
        try {
            mc.world.removeEntity(id, reason);
        } finally {
            releasing = false;
        }
    }

    private void releaseAll() {
        for (Integer id : new ArrayList<>(held.keySet())) release(id);
    }

    private static ItemStack diamondSword;

    public ItemStack swordStack() {
        if (!sword.get()) return ItemStack.EMPTY;
        if (diamondSword == null) diamondSword = new ItemStack(net.minecraft.item.Items.DIAMOND_SWORD);
        return diamondSword;
    }

    public float deathYaw(LivingEntity e) {
        Float yaw = deathYaws.get(e.getId());
        return yaw == null ? e.bodyYaw : yaw;
    }
}
