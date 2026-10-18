package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.util.HumanAim;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EntityTypeListSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.entity.SortPriority;
import meteordevelopment.meteorclient.utils.entity.TargetUtils;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.Random;
import java.util.Set;

public class TargetStrafe extends Module {
    public enum Mode { Client, Server }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Client: only on your screen, your model strafes round the target (see it in F5); you don't move. Server: you really strafe - it holds A/D, keeps your distance with W/S and faces the target, so everyone sees it.")
        .defaultValue(Mode.Client).onChanged(m -> release()).build());

    private final Setting<Double> range = sg.add(new DoubleSetting.Builder()
        .name("range").description("Only kicks in once you get this close to a target (blocks).").defaultValue(4).range(1, 32).sliderRange(1, 10).build());

    private final Setting<Double> radius = sg.add(new DoubleSetting.Builder()
        .name("radius").description("How far from the target you strafe (blocks).").defaultValue(2.5).range(0.5, 8).sliderRange(1, 5).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("speed").description("Client mode: how fast your model strafes (blocks per second). Server mode moves at your real walking speed.").defaultValue(6).range(0.5, 40).sliderRange(1, 20)
        .visible(() -> mode.get() == Mode.Client).build());

    private final Setting<Double> turnSpeed = sg.add(new DoubleSetting.Builder()
        .name("turn-speed").description("Server mode: how fast it turns you to face the target (degrees per tick).").defaultValue(14).range(2, 90).sliderRange(4, 40)
        .visible(() -> mode.get() == Mode.Server).build());

    private final Setting<Boolean> clockwise = sg.add(new BoolSetting.Builder()
        .name("clockwise").description("Start strafing clockwise instead of anticlockwise.").defaultValue(false).build());

    private final Setting<Set<EntityType<?>>> entities = sg.add(new EntityTypeListSetting.Builder()
        .name("entities").description("Which kinds of entities you strafe round. Friends are always left out.").defaultValue(EntityType.PLAYER).build());

    private final Setting<Boolean> hop = sg.add(new BoolSetting.Builder()
        .name("hop").description("Strafe-jump hops while circling, like a real PvPer.").defaultValue(true).build());

    private final Setting<Boolean> reverse = sg.add(new BoolSetting.Builder()
        .name("switch-direction").description("Now and then switches between strafing left and right, like real strafing.").defaultValue(true).build());

    private final Setting<Boolean> safe = sg.add(new BoolSetting.Builder()
        .name("edge-safety").description("Server mode: switches direction instead of strafing off a drop or into lava, fire or water.").defaultValue(true)
        .visible(() -> mode.get() == Mode.Server).build());

    private final Random rng = new Random();
    private final HumanAim aim = new HumanAim();
    private Entity target, lastTarget;
    private double blend, lastFrame, angle, nextFlip, walkPos;
    private int dir = 1;
    private boolean driving;

    public TargetStrafe() {
        super(AddonTemplate.CATEGORY, "target-strafe", "Strafes round the nearest target, facing it, hopping and switching left and right. Client: only a visual on your screen (wings and effects follow your model). Server: you really strafe with A/D.");
    }

    @Override
    public void onDeactivate() {
        target = lastTarget = null;
        blend = 0;
        release();
    }

    // give the movement keys back to whatever you're physically holding
    private void release() {
        if (!driving || mc.options == null) return;
        driving = false;
        mc.options.leftKey.setPressed(Input.isPressed(mc.options.leftKey));
        mc.options.rightKey.setPressed(Input.isPressed(mc.options.rightKey));
        mc.options.forwardKey.setPressed(Input.isPressed(mc.options.forwardKey));
        mc.options.backKey.setPressed(Input.isPressed(mc.options.backKey));
        mc.options.jumpKey.setPressed(Input.isPressed(mc.options.jumpKey));
    }

    private int strafeDir() {
        return dir * (clockwise.get() ? -1 : 1);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        target = TargetUtils.get(e -> e != mc.player && e.isAlive() && entities.get().contains(e.getType()) && mc.player.distanceTo(e) <= range.get()
            && !(e instanceof PlayerEntity p && !Friends.get().shouldAttack(p)), SortPriority.LowestDistance);
        double now = Fx.time();
        if (reverse.get() && now > nextFlip) {
            if (nextFlip != 0) dir = -dir;
            nextFlip = now + 1 + rng.nextDouble() * 1.5;
        }
        if (mode.get() == Mode.Server) serverStrafe();
    }

    // Server mode: a real strafe with real key presses - A/D sideways, W/S to hold the distance, facing the target
    private void serverStrafe() {
        if (target == null || mc.currentScreen != null) {
            release();
            return;
        }
        driving = true;
        Vec3d me = mc.player.getEntityPos(), t = target.getEntityPos();
        float yaw = (float) Math.toDegrees(Math.atan2(-(t.x - me.x), t.z - me.z));
        aim.turn(mc.player, yaw, mc.player.getPitch(), turnSpeed.get().floatValue(), false);
        double rad = Math.toRadians(mc.player.getYaw());
        Vec3d left = new Vec3d(Math.cos(rad), 0, Math.sin(rad));
        if (mc.player.horizontalCollision) dir = -dir;
        if (safe.get() && mc.player.isOnGround() && unsafe(me.add(left.multiply(strafeDir() * 1.6)))) dir = -dir;
        double dist = Math.hypot(t.x - me.x, t.z - me.z);
        mc.options.leftKey.setPressed(strafeDir() > 0);
        mc.options.rightKey.setPressed(strafeDir() < 0);
        mc.options.forwardKey.setPressed(dist > radius.get() + 0.35);
        mc.options.backKey.setPressed(dist < radius.get() - 0.35);
        mc.options.jumpKey.setPressed(hop.get() && mc.player.isOnGround());
    }

    private boolean unsafe(Vec3d at) {
        BlockPos feet = BlockPos.ofFloored(at.x, mc.player.getY(), at.z);
        for (int dy = 0; dy >= -1; dy--) {
            BlockState s = mc.world.getBlockState(feet.up(dy));
            if (s.getFluidState().isIn(FluidTags.LAVA) || s.getFluidState().isIn(FluidTags.WATER) || s.isOf(Blocks.FIRE) || s.isOf(Blocks.SOUL_FIRE)) return true;
        }
        for (int dy = 1; dy <= 4; dy++) if (!mc.world.getBlockState(feet.down(dy)).getCollisionShape(mc.world, feet.down(dy)).isEmpty()) return false;
        return true;
    }

    // Client mode: advance the strafe once per frame, even in first person when your model isn't drawn
    @EventHandler
    private void onRender(Render3DEvent event) {
        double now = Fx.time(), dt = lastFrame == 0 ? 0 : Math.min(0.1, now - lastFrame);
        lastFrame = now;
        boolean on = mode.get() == Mode.Client && target != null;
        blend = on ? Math.min(1, blend + dt * 9) : Math.max(0, blend - dt * 9);
        if (on) lastTarget = target;
        if (on && blend <= dt * 9 + 1e-6 && mc.player != null) {
            Vec3d me = mc.player.getLerpedPos(event.tickDelta), c = target.getLerpedPos(event.tickDelta);
            angle = Math.atan2(me.z - c.z, me.x - c.x);
        }
        angle += strafeDir() * speed.get() / radius.get() * dt;
        walkPos += dt * 20 * Math.min(1, speed.get() / 5);
    }

    private Vec3d orbit(Entity t, float tickDelta) {
        Vec3d c = t.getLerpedPos(tickDelta);
        double h = 0;
        if (hop.get()) {
            double ph = Fx.time() / 0.6 % 1;
            h = 4 * 0.42 * ph * (1 - ph);
        }
        return new Vec3d(c.x + Math.cos(angle) * radius.get(), c.y + h, c.z + Math.sin(angle) * radius.get());
    }

    // how far your drawn player is from where you really are; everything that draws on you uses this through Fx.pos
    public Vec3d offset(float tickDelta) {
        Entity t = lastTarget;
        if (!isActive() || mode.get() != Mode.Client || blend <= 0 || t == null || t.isRemoved() || mc.player == null) return Vec3d.ZERO;
        double k = blend * blend * (3 - 2 * blend);
        return orbit(t, tickDelta).subtract(mc.player.getLerpedPos(tickDelta)).multiply(k);
    }

    // Client mode: called when your player is about to be drawn; moves it, faces it at the target and walks its legs
    public void apply(LivingEntityRenderState state, float tickDelta) {
        Entity t = lastTarget;
        Vec3d off = offset(tickDelta);
        if (off == Vec3d.ZERO || t == null) return;
        state.x += off.x;
        state.y += off.y;
        state.z += off.z;
        Vec3d c = t.getLerpedPos(tickDelta), o = orbit(t, tickDelta);
        double k = blend * blend * (3 - 2 * blend);
        float yaw = (float) Math.toDegrees(Math.atan2(-(c.x - o.x), c.z - o.z));
        state.bodyYaw = state.bodyYaw + (float) (MathHelper.wrapDegrees(yaw - state.bodyYaw) * k);
        state.relativeHeadYaw *= (float) (1 - k);
        // legs walk instead of sliding
        state.limbSwingAnimationProgress = (float) walkPos;
        state.limbSwingAmplitude = (float) (state.limbSwingAmplitude + (Math.min(1, speed.get() / 5) - state.limbSwingAmplitude) * k);
    }
}
