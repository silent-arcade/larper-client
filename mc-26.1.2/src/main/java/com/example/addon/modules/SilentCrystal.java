package com.example.addon.modules;

import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.entity.DamageUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class SilentCrystal extends SilentBase {
    private final Setting<Integer> placeDelay = sg.add(new IntSetting.Builder()
        .name("place-delay").description("Ticks between placing crystals (20 = 1 second).").defaultValue(2).range(0, 20).sliderRange(0, 10).build());

    private final Setting<Integer> breakDelay = sg.add(new IntSetting.Builder()
        .name("break-delay").description("Ticks between popping crystals (20 = 1 second).").defaultValue(2).range(0, 20).sliderRange(0, 10).build());

    private final Setting<Double> assist = sg.add(new DoubleSetting.Builder()
        .name("pop-radius").description("Blatant/Fast: also pops crystals this close to where you look, aiming at them silently.").defaultValue(2).range(0, 6).sliderRange(0, 4)
        .visible(() -> !safe()).build());

    private final Setting<Boolean> rotate = sg.add(new BoolSetting.Builder()
        .name("rotate").description("Blatant/Fast: silently aim at crystals you pop (server only, your camera doesn't move).").defaultValue(true)
        .visible(() -> !safe()).build());

    private int placeTimer, breakTimer;
    private EndCrystal popped;
    private double aimJitter = 0.5;

    public SilentCrystal() {
        super("silent-crystal", "Crystal macro: hold the key on obsidian to place and pop crystals with a silent swap. Safe, Blatant or Fast mode.");
    }

    @Override
    public void onActivate() {
        placeTimer = breakTimer = 0;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (placeTimer > 0) placeTimer--;
        if (breakTimer > 0) breakTimer--;
        if (!held()) return;

        if (safe() && humanAim.get() && !(mc.hitResult instanceof EntityHitResult)) {
            EndCrystal near = nearestCrystal(aimRadius.get());
            if (near != null) turnTowards(near.getBoundingBox().getCenter().add(0, (aimJitter - 0.5) * 0.6, 0));
        }

        if (breakTimer <= 0) {
            EndCrystal crystal = aimedCrystal();
            if (crystal != null) {
                pop(crystal);
                breakTimer = delay(breakDelay.get());
                if (safe()) return;
            }
        }

        BlockHitResult hit = mc.hitResult instanceof BlockHitResult b ? b : null;
        if (fast() && popped != null && mc.hitResult instanceof EntityHitResult e && e.getEntity() == popped) {
            BlockPos under = popped.blockPosition().below();
            hit = new BlockHitResult(Vec3.atCenterOf(under).add(0, 0.5, 0), net.minecraft.core.Direction.UP, under, false);
        }
        if (placeTimer <= 0 && hit != null && canCrystal(hit.getBlockPos()) && place(hit)) placeTimer = delay(placeDelay.get());
    }

    private boolean place(BlockHitResult hit) {
        if (mc.player.getOffhandItem().is(Items.END_CRYSTAL)) BlockUtils.interact(hit, InteractionHand.OFF_HAND, true);
        else {
            FindItemResult item = InvUtils.findInHotbar(Items.END_CRYSTAL);
            if (!item.found()) return false;
            use(item.slot(), hit);
        }
        return true;
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!fast() || !held() || !(event.entity instanceof EndCrystal c) || mc.hitResult == null) return;
        if (c.position().distanceTo(mc.hitResult.getLocation()) > assist.get() + 1) return;
        if (!inReach(c)) return;
        if (!selfOk(DamageUtils.crystalDamage(mc.player, c.position()))) return;
        pop(c);
        BlockPos under = c.blockPosition().below();
        if (canCrystal(under)) place(new BlockHitResult(Vec3.atCenterOf(under).add(0, 0.5, 0), net.minecraft.core.Direction.UP, under, false));
    }

    private void pop(EndCrystal crystal) {
        popped = crystal;
        Runnable hit = () -> {
            mc.gameMode.attack(mc.player, crystal);
            mc.player.swing(InteractionHand.MAIN_HAND);
        };
        Vec3 mid = crystal.getBoundingBox().getCenter();
        aimJitter = Math.random();
        boolean aimed = mc.hitResult != null && crystal.getBoundingBox().inflate(0.5).contains(mc.hitResult.getLocation());
        if (!safe() && !aimed && rotate.get()) Rotations.rotate(Rotations.getYaw(mid), Rotations.getPitch(mid), 50, hit);
        else hit.run();
    }

    private EndCrystal aimedCrystal() {
        EndCrystal c = mc.hitResult instanceof EntityHitResult e && e.getEntity() instanceof EndCrystal x ? x : null;
        if (c == null && !safe()) c = nearestCrystal(assist.get());
        return c != null && selfOk(DamageUtils.crystalDamage(mc.player, c.position())) ? c : null;
    }

    private EndCrystal nearestCrystal(double radius) {
        if (mc.hitResult == null) return null;
        Vec3 look = mc.hitResult.getLocation();
        EndCrystal c = null;
        double best = radius;
        for (Entity e : mc.level.entitiesForRendering()) {
            if (!(e instanceof EndCrystal x) || !x.isAlive()) continue;
            double d = x.position().distanceTo(look);
            if (d <= best && inReach(x)) {
                best = d;
                c = x;
            }
        }
        return c;
    }

    private boolean inReach(Entity e) {
        double r = mc.player.entityInteractionRange();
        return e.getBoundingBox().distanceToSqr(mc.player.getEyePosition()) <= r * r;
    }

    private boolean canCrystal(BlockPos pos) {
        BlockState base = mc.level.getBlockState(pos);
        if (!base.is(Blocks.OBSIDIAN) && !base.is(Blocks.BEDROCK)) return false;
        BlockPos up = pos.above();
        return mc.level.getBlockState(up).isAir() && mc.level.getEntities((net.minecraft.world.entity.Entity) null, new AABB(up).expandTowards(0, 1, 0), e -> !(fast() && e == popped)).isEmpty();
    }
}
