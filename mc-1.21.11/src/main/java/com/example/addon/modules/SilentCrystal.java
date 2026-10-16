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
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

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
    private EndCrystalEntity popped;
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

        if (safe() && humanAim.get() && !(mc.crosshairTarget instanceof EntityHitResult)) {
            EndCrystalEntity near = nearestCrystal(aimRadius.get());
            if (near != null) turnTowards(near.getBoundingBox().getCenter().add(0, (aimJitter - 0.5) * 0.6, 0));
        }

        if (breakTimer <= 0) {
            EndCrystalEntity crystal = aimedCrystal();
            if (crystal != null) {
                pop(crystal);
                breakTimer = delay(breakDelay.get());
                if (safe()) return;
            }
        }

        BlockHitResult hit = mc.crosshairTarget instanceof BlockHitResult b ? b : null;
        if (fast() && popped != null && mc.crosshairTarget instanceof EntityHitResult e && e.getEntity() == popped) {
            BlockPos under = popped.getBlockPos().down();
            hit = new BlockHitResult(Vec3d.ofCenter(under).add(0, 0.5, 0), net.minecraft.util.math.Direction.UP, under, false);
        }
        if (placeTimer <= 0 && hit != null && canCrystal(hit.getBlockPos()) && place(hit)) placeTimer = delay(placeDelay.get());
    }

    private boolean place(BlockHitResult hit) {
        if (mc.player.getOffHandStack().isOf(Items.END_CRYSTAL)) BlockUtils.interact(hit, Hand.OFF_HAND, true);
        else {
            FindItemResult item = InvUtils.findInHotbar(Items.END_CRYSTAL);
            if (!item.found()) return false;
            use(item.slot(), hit);
        }
        return true;
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!fast() || !held() || !(event.entity instanceof EndCrystalEntity c) || mc.crosshairTarget == null) return;
        if (c.getEntityPos().distanceTo(mc.crosshairTarget.getPos()) > assist.get() + 1) return;
        if (!inReach(c)) return;
        if (!selfOk(DamageUtils.crystalDamage(mc.player, c.getEntityPos()))) return;
        pop(c);
        BlockPos under = c.getBlockPos().down();
        if (canCrystal(under)) place(new BlockHitResult(Vec3d.ofCenter(under).add(0, 0.5, 0), net.minecraft.util.math.Direction.UP, under, false));
    }

    private void pop(EndCrystalEntity crystal) {
        popped = crystal;
        Runnable hit = () -> {
            mc.interactionManager.attackEntity(mc.player, crystal);
            mc.player.swingHand(Hand.MAIN_HAND);
        };
        Vec3d mid = crystal.getBoundingBox().getCenter();
        aimJitter = Math.random();
        boolean aimed = mc.crosshairTarget != null && crystal.getBoundingBox().expand(0.5).contains(mc.crosshairTarget.getPos());
        if (!safe() && !aimed && rotate.get()) Rotations.rotate(Rotations.getYaw(mid), Rotations.getPitch(mid), 50, hit);
        else hit.run();
    }

    private EndCrystalEntity aimedCrystal() {
        EndCrystalEntity c = mc.crosshairTarget instanceof EntityHitResult e && e.getEntity() instanceof EndCrystalEntity x ? x : null;
        if (c == null && !safe()) c = nearestCrystal(assist.get());
        return c != null && selfOk(DamageUtils.crystalDamage(mc.player, c.getEntityPos())) ? c : null;
    }

    private EndCrystalEntity nearestCrystal(double radius) {
        if (mc.crosshairTarget == null) return null;
        Vec3d look = mc.crosshairTarget.getPos();
        EndCrystalEntity c = null;
        double best = radius;
        for (Entity e : mc.world.getEntities()) {
            if (!(e instanceof EndCrystalEntity x) || !x.isAlive()) continue;
            double d = x.getEntityPos().distanceTo(look);
            if (d <= best && inReach(x)) {
                best = d;
                c = x;
            }
        }
        return c;
    }

    private boolean inReach(Entity e) {
        double r = mc.player.getEntityInteractionRange();
        return e.getBoundingBox().squaredMagnitude(mc.player.getEyePos()) <= r * r;
    }

    private boolean canCrystal(BlockPos pos) {
        BlockState base = mc.world.getBlockState(pos);
        if (!base.isOf(Blocks.OBSIDIAN) && !base.isOf(Blocks.BEDROCK)) return false;
        BlockPos up = pos.up();
        return mc.world.getBlockState(up).isAir() && mc.world.getOtherEntities(null, new Box(up).stretch(0, 1, 0), e -> !(fast() && e == popped)).isEmpty();
    }
}
