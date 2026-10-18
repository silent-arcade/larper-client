package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.CameraType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public class AutoF5 extends Module {
    private final SettingGroup sgWhen = settings.createGroup("When");
    private final SettingGroup sgCamera = settings.createGroup("Camera");

    private final Setting<Boolean> gliding = sgWhen.add(new BoolSetting.Builder()
        .name("elytra").description("While flying with an elytra.").defaultValue(true).build());

    private final Setting<Boolean> riding = sgWhen.add(new BoolSetting.Builder()
        .name("riding").description("While riding something (horse, boat, minecart).").defaultValue(true).build());

    private final Setting<Boolean> swimming = sgWhen.add(new BoolSetting.Builder()
        .name("swimming").description("While swimming.").defaultValue(false).build());

    private final Setting<Boolean> creativeFly = sgWhen.add(new BoolSetting.Builder()
        .name("creative-flying").description("While flying in creative or spectator.").defaultValue(false).build());

    private final Setting<Boolean> bow = sgWhen.add(new BoolSetting.Builder()
        .name("drawing-a-bow").description("While pulling back a bow, crossbow or trident.").defaultValue(false).build());

    private final Setting<Boolean> fast = sgWhen.add(new BoolSetting.Builder()
        .name("moving-fast").description("While you move faster than the speed below (any way: sprinting, speed effects, ice).").defaultValue(false).build());

    private final Setting<Double> fastSpeed = sgWhen.add(new DoubleSetting.Builder()
        .name("fast-speed").description("Blocks per second that counts as fast.").defaultValue(12).range(3, 60).sliderRange(5, 30).visible(fast::get).build());

    private final Setting<Boolean> falling = sgWhen.add(new BoolSetting.Builder()
        .name("falling").description("While falling further than the distance below, to see where you'll land.").defaultValue(false).build());

    private final Setting<Double> fallDistance = sgWhen.add(new DoubleSetting.Builder()
        .name("fall-distance").description("How far you have to fall (blocks).").defaultValue(5).range(2, 50).sliderRange(3, 20).visible(falling::get).build());

    private final Setting<Boolean> combat = sgWhen.add(new BoolSetting.Builder()
        .name("in-combat").description("For a few seconds after you hit something or get hit.").defaultValue(false).build());

    private final Setting<Boolean> enemies = sgWhen.add(new BoolSetting.Builder()
        .name("enemies-near").description("While a player who isn't your friend is close.").defaultValue(false).build());

    private final Setting<Integer> enemyRange = sgWhen.add(new IntSetting.Builder()
        .name("enemy-range").description("How close counts (blocks).").defaultValue(12).range(3, 64).sliderRange(4, 32).visible(enemies::get).build());

    private final Setting<Boolean> front = sgCamera.add(new BoolSetting.Builder()
        .name("front-view").description("Use the front view instead of the back view.").defaultValue(false).build());

    private final Setting<Boolean> smooth = sgCamera.add(new BoolSetting.Builder()
        .name("smooth-zoom").description("The camera glides out of your head when it switches, and back in before going to first person.").defaultValue(true).build());

    private final Setting<Double> zoomTime = sgCamera.add(new DoubleSetting.Builder()
        .name("zoom-time").description("How long the glide takes (seconds).").defaultValue(0.35).range(0.05, 2).sliderRange(0.1, 1).visible(smooth::get).build());

    private final Setting<Boolean> customDistance = sgCamera.add(new BoolSetting.Builder()
        .name("custom-distance").description("Use your own camera distance instead of the normal 4 blocks (it still stops at walls).").defaultValue(false).build());

    private final Setting<Double> distance = sgCamera.add(new DoubleSetting.Builder()
        .name("distance").description("Camera distance (blocks).").defaultValue(6).range(1, 20).sliderRange(2, 12).visible(customDistance::get).build());

    private final Setting<Integer> linger = sgCamera.add(new IntSetting.Builder()
        .name("linger").description("Stay in third person this many ticks after the reason stops, so it doesn't flick back and forth.").defaultValue(10).range(0, 100).sliderRange(0, 40).build());

    private CameraType before;
    private int hold, combatTicks;
    private boolean closing;
    private double progress;
    private long lastFrame;

    public AutoF5() {
        super(AddonTemplate.VISUAL, "auto-f5", "Switches to third person while you fly, ride, swim, fall, fight or go fast, with a smooth camera glide and your own camera distance.");
    }

    @Override
    public void onDeactivate() {
        if (before != null) mc.options.setCameraType(before);
        before = null;
        hold = 0;
        closing = false;
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        combatTicks = 80;
    }

    private boolean enemyNear() {
        for (Player p : mc.level.players()) {
            if (p != mc.player && !p.isSpectator() && Friends.get().shouldAttack(p) && mc.player.distanceTo(p) <= enemyRange.get()) return true;
        }
        return false;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.level == null) return;
        if (mc.player.hurtTime > 0) combatTicks = 80;
        if (combatTicks > 0) combatTicks--;
        var active = mc.player.getUseItem();
        boolean drawing = mc.player.isUsingItem() && (active.is(Items.BOW) || active.is(Items.CROSSBOW) || active.is(Items.TRIDENT));
        Vec3 v = mc.player.getDeltaMovement();
        boolean want = gliding.get() && mc.player.isFallFlying() || riding.get() && mc.player.isPassenger() || swimming.get() && mc.player.isSwimming()
            || creativeFly.get() && mc.player.getAbilities().flying || bow.get() && drawing
            || fast.get() && Math.hypot(v.x, v.z) * 20 >= fastSpeed.get()
            || falling.get() && mc.player.fallDistance >= fallDistance.get() && !mc.player.onGround()
            || combat.get() && combatTicks > 0 || enemies.get() && enemyNear();
        if (want) {
            hold = linger.get();
            closing = false;
        }
        if (want && before == null) {
            before = mc.options.getCameraType();
            if (!before.isFirstPerson()) {
                before = null;
                return;
            }
            progress = 0;
            mc.options.setCameraType(front.get() ? CameraType.THIRD_PERSON_FRONT : CameraType.THIRD_PERSON_BACK);
        } else if (!want && before != null && hold-- <= 0) {
            if (smooth.get() && progress > 0.02) closing = true;
            else finishClose();
        }
    }

    private void finishClose() {
        mc.options.setCameraType(before);
        before = null;
        closing = false;
    }

    public boolean drivesCamera() {
        return before != null && (smooth.get() || customDistance.get());
    }

    // called from the camera every frame while we're in third person
    public Vec3 apply(Vec3 cam, Entity focused, float tickDelta) {
        long now = System.nanoTime();
        double dt = lastFrame == 0 ? 0 : Math.min(0.1, (now - lastFrame) / 1e9);
        lastFrame = now;
        double step = smooth.get() ? dt / zoomTime.get() : 1;
        progress = closing ? Math.max(0, progress - step) : Math.min(1, progress + step);
        if (closing && progress <= 0.02) mc.execute(() -> {
            if (closing && before != null) finishClose();
        });
        Vec3 eye = focused.getEyePosition(tickDelta);
        Vec3 dir = cam.subtract(eye);
        double len = dir.length();
        if (len < 1e-3) return cam;
        dir = dir.scale(1 / len);
        double d = len;
        if (customDistance.get()) {
            Vec3 far = eye.add(dir.scale(distance.get()));
            var hit = mc.level.clip(new ClipContext(eye, far, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, focused));
            d = hit.getType() == HitResult.Type.MISS ? distance.get() : Math.max(0.3, eye.distanceTo(hit.getLocation()) - 0.2);
        }
        double k = progress * progress * (3 - 2 * progress);
        return eye.add(dir.scale(d * (smooth.get() ? k : 1)));
    }
}
