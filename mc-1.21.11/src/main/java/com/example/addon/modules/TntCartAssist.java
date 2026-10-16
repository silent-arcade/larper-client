package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.AbstractRailBlock;
import net.minecraft.block.BlockState;
import net.minecraft.enchantment.Enchantments;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Shoot a Flame bow and a rail + TNT minecart appear in the arrow's path, so the burning arrow sets the TNT off.
 *
 * - The arrow's flight is simulated tick by tick with the game's own physics, re-predicted every tick from the live
 *   arrow until the rail is down.
 * - The cart goes at the first spot where the arrow flies through a cart's body (not only where it lands), within
 *   your normal reach.
 * - Auto timing places the cart as late as is safe, a couple of ticks before the arrow gets there.
 * - Aim is server-side only (your camera doesn't move) and moves like a hand: eased, slightly varied, mouse-step sized,
 *   and it turns back to your view afterwards instead of snapping.
 * This places real blocks, so the server sees it.
 */
public class TntCartAssist extends Module {
    public enum Timing { Auto, Manual }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgAim = settings.createGroup("Aim");
    private final SettingGroup sgRender = settings.createGroup("Render");

    private final Setting<Timing> timing = sgGeneral.add(new EnumSetting.Builder<Timing>()
        .name("timing").description("Auto: place the cart just before the arrow arrives. Manual: after a fixed delay.")
        .defaultValue(Timing.Auto).build());

    private final Setting<Integer> margin = sgGeneral.add(new IntSetting.Builder()
        .name("margin").description("Auto timing: how many ticks before the arrow arrives the cart should be down.")
        .defaultValue(3).range(1, 20).sliderRange(1, 10)
        .visible(() -> timing.get() == Timing.Auto).build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("delay").description("Manual timing: ticks to wait after the shot before placing the rail (20 ticks = 1 second).")
        .defaultValue(2).range(0, 40).sliderRange(0, 20)
        .visible(() -> timing.get() == Timing.Manual).build());

    private final Setting<Integer> cartDelay = sgGeneral.add(new IntSetting.Builder()
        .name("cart-delay").description("Ticks between placing the rail and the TNT minecart (0 = straight after).")
        .defaultValue(0).range(0, 20).sliderRange(0, 10).build());

    private final Setting<Boolean> intercept = sgGeneral.add(new BoolSetting.Builder()
        .name("intercept").description("Put the cart where the arrow first flies low enough to hit it, not only where it lands.")
        .defaultValue(true).build());

    private final Setting<Boolean> silentAim = sgAim.add(new BoolSetting.Builder()
        .name("silent-aim").description("Aim at the spot server-side only; your camera doesn't move.")
        .defaultValue(true).build());

    private final Setting<Double> aimSpeed = sgAim.add(new DoubleSetting.Builder()
        .name("aim-speed").description("Fastest the aim turns, in degrees per tick. Lower looks more human.")
        .defaultValue(40).range(5, 180).sliderRange(10, 90)
        .visible(silentAim::get).build());

    private final Setting<Boolean> humanize = sgAim.add(new BoolSetting.Builder()
        .name("humanize").description("Eased turns with a little variation, small overshoot on big turns, and mouse-sized steps.")
        .defaultValue(true).visible(silentAim::get).build());

    private final Setting<Boolean> smoothReturn = sgAim.add(new BoolSetting.Builder()
        .name("smooth-return").description("Turn back to where you're looking afterwards instead of snapping.")
        .defaultValue(true).visible(silentAim::get).build());

    private final Setting<Boolean> showPath = sgRender.add(new BoolSetting.Builder()
        .name("show-path").description("Draws the predicted arrow path and the cart spot.")
        .defaultValue(true).build());

    private final Setting<SettingColor> pathColor = sgRender.add(new ColorSetting.Builder()
        .name("path-color").description("Color of the predicted path.")
        .defaultValue(new SettingColor(255, 120, 30, 255)).visible(showPath::get).build());

    private final Setting<SettingColor> cartColor = sgRender.add(new ColorSetting.Builder()
        .name("cart-color").description("Color of the cart spot.")
        .defaultValue(new SettingColor(255, 40, 40, 60)).visible(showPath::get).build());

    private enum Stage { IDLE, RAIL, CART, RETURN }

    private final Random rng = new Random();
    private final List<Vec3d> path = new ArrayList<>();
    private Stage stage = Stage.IDLE;
    private PersistentProjectileEntity arrow;
    private BlockPos railPos;
    private int impactIn;      // ticks from the shot until the arrow reaches the cart spot
    private int waited, cartWait, age;
    private float aimYaw, aimPitch;
    private boolean overshot;

    public TntCartAssist() {
        super(AddonTemplate.CATEGORY, "tnt-cart-assist", "Flame bow shot: puts a rail + TNT minecart in the arrow's path.");
    }

    @Override
    public void onDeactivate() {
        stage = Stage.IDLE;
        arrow = null;
        path.clear();
    }

    // Your own arrow appearing while you hold a Flame bow = a flaming shot
    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (mc.player == null || !(event.entity instanceof PersistentProjectileEntity a)) return;
        if (a.getOwner() != mc.player || !holdingFlameBow()) return;
        if (!rail().found() || !cart().found()) {
            warning("Needs a rail and a TNT minecart in your hotbar.");
            return;
        }
        arrow = a;
        railPos = null;
        stage = Stage.RAIL;
        waited = 0;
        age = 0;
        overshot = false;
        startAim();
        if (!repredict()) {
            warning("No spot within your reach on the arrow's path.");
            arrow = null;
            stage = Stage.IDLE;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null || stage == Stage.IDLE) return;
        if (++age > 200) { // gave up (moved away, items gone, ...)
            finish();
            return;
        }
        switch (stage) {
            case RAIL -> tickRail();
            case CART -> tickCart();
            case RETURN -> {
                if (!silentAim.get() || !smoothReturn.get() || turnTo(mc.player.getYaw(), mc.player.getPitch(), 2f)) stage = Stage.IDLE;
            }
            default -> { }
        }
    }

    private void tickRail() {
        waited++;
        // Keep following the live arrow until the rail is down
        if (arrow != null && !arrow.isRemoved()) repredict();
        if (railPos == null || !inReach(railPos)) {
            finish();
            return;
        }
        boolean railThere = mc.world.getBlockState(railPos).getBlock() instanceof AbstractRailBlock;
        Vec3d spot = railSpot();
        boolean aimed = aim(spot); // aim while waiting, so the placement itself is instant
        if (!aimed || !timeToPlace()) return;
        if (railThere) { // a rail is already there: straight to the cart
            placeCart();
            return;
        }
        if (use(rail(), new BlockHitResult(spot, Direction.UP, railPos.down(), false))) {
            stage = Stage.CART;
            cartWait = cartDelay.get();
            if (cartWait <= 0) placeCart(); // same tick, right behind the rail
        } else finish();
    }

    // Same aim as the rail (it's the same spot), and no waiting for the server to confirm the rail: the server handles
    // our packets in order, so the rail is always there by the time it reads the cart
    private void tickCart() {
        if (!inReach(railPos)) {
            finish();
            return;
        }
        aim(railSpot());
        if (cartWait-- > 0) return;
        placeCart();
    }

    private void placeCart() {
        use(cart(), new BlockHitResult(railSpot().add(0, 0.0625, 0), Direction.UP, railPos, false));
        finish();
    }

    private void finish() {
        arrow = null;
        stage = silentAim.get() && smoothReturn.get() ? Stage.RETURN : Stage.IDLE;
    }

    // Manual: fixed delay. Auto: as late as is safe (rail + cart delay + margin before the arrow arrives).
    private boolean timeToPlace() {
        if (timing.get() == Timing.Manual) return waited > delay.get();
        int needed = 1 + cartDelay.get() + margin.get() + pingTicks();
        return impactIn - waited <= needed;
    }

    private Vec3d railSpot() {
        return Vec3d.ofCenter(railPos.down()).add(0, 0.5, 0); // top face of the block under the rail
    }

    // Your ping in ticks (one way there and back), so the cart is on the server before the arrow, even when laggy
    private int pingTicks() {
        var entry = mc.getNetworkHandler() == null ? null : mc.getNetworkHandler().getPlayerListEntry(mc.player.getUuid());
        return entry == null ? 1 : (int) Math.ceil(entry.getLatency() / 50.0) + 1;
    }

    // ---- Prediction ----

    /** Re-simulates the arrow from where it is now and picks the cart spot. False if there's no usable spot. */
    private boolean repredict() {
        Vec3d pos = arrow.getEntityPos(), vel = arrow.getVelocity();
        path.clear();
        path.add(pos);
        BlockPos best = null;
        int bestTick = 0;
        for (int tick = 1; tick <= 200 && best == null; tick++) {
            Vec3d next = pos.add(vel);
            BlockHitResult hit = mc.world.raycast(new RaycastContext(pos, next, RaycastContext.ShapeType.COLLIDER, RaycastContext.FluidHandling.NONE, mc.player));
            Vec3d end = hit.getType() == HitResult.Type.BLOCK ? hit.getPos() : next;
            // Where along this tick's flight would the arrow pass through a cart?
            if (intercept.get()) {
                double len = end.distanceTo(pos);
                Vec3d dir = len > 1e-6 ? end.subtract(pos).multiply(1 / len) : Vec3d.ZERO;
                for (double d = 0; d <= len && best == null; d += 0.2) {
                    BlockPos c = cartAt(pos.add(dir.multiply(d)));
                    if (c != null) { best = c; bestTick = tick; }
                }
            }
            path.add(end);
            if (best == null && hit.getType() == HitResult.Type.BLOCK) {
                BlockPos c = landingSpot(hit);
                if (c != null) { best = c; bestTick = tick; }
                break;
            }
            pos = next;
            boolean water = !mc.world.getFluidState(BlockPos.ofFloored(pos)).isEmpty();
            vel = vel.multiply(water ? 0.6 : 0.99).add(0, -0.05, 0);
        }
        if (best == null) return railPos != null; // keep the spot we already have
        railPos = best;
        impactIn = bestTick + waited;
        return true;
    }

    // A cart standing at the block containing p would be hit (arrow inside the cart's 0.98 x 0.7 body), if it can stand there
    private BlockPos cartAt(Vec3d p) {
        BlockPos c = BlockPos.ofFloored(p);
        double fy = p.y - c.getY(), fx = p.x - c.getX(), fz = p.z - c.getZ();
        if (fy > 0.7 || fx < 0.02 || fx > 0.98 || fz < 0.02 || fz > 0.98) return null;
        return canHoldCart(c) && inReach(c) ? c : null;
    }

    // It hit something: a rail on top of it, or (a wall) on the ground in front of it
    private BlockPos landingSpot(BlockHitResult hit) {
        BlockPos pos = hit.getSide() == Direction.UP ? hit.getBlockPos().up() : hit.getBlockPos().offset(hit.getSide());
        for (int i = 0; i < 4; i++, pos = pos.down()) {
            if (canHoldCart(pos)) return inReach(pos) ? pos : null;
        }
        return null;
    }

    private boolean canHoldCart(BlockPos pos) {
        BlockState state = mc.world.getBlockState(pos);
        BlockPos below = pos.down();
        boolean free = state.isReplaceable() || state.getBlock() instanceof AbstractRailBlock;
        return free && mc.world.getBlockState(below).isSideSolidFullSquare(mc.world, below, Direction.UP);
    }

    // Same reach as your normal clicks (4.5 blocks in survival, 5 in creative): both the block under the rail and the rail
    private boolean inReach(BlockPos rail) {
        return mc.player.canInteractWithBlockAt(rail.down(), 0) && mc.player.canInteractWithBlockAt(rail, 0);
    }

    // ---- Aim ----

    private void startAim() {
        aimYaw = silentAim.get() ? Rotations.serverYaw : mc.player.getYaw();
        aimPitch = silentAim.get() ? Rotations.serverPitch : mc.player.getPitch();
    }

    /** One tick of turning toward the spot. True once it's on target. */
    private boolean aim(Vec3d spot) {
        float yaw = (float) Rotations.getYaw(spot), pitch = (float) Rotations.getPitch(spot);
        if (!silentAim.get()) {
            mc.player.setYaw(yaw);
            mc.player.setPitch(pitch);
            return true;
        }
        return turnTo(yaw, pitch, 1.2f);
    }

    /**
     * Turns the server-side aim part of the way toward yaw/pitch. Humanized: covers 35-60% of what's left each tick
     * (fast at first, easing in), slight random speed, one small overshoot on big turns that gets corrected, tiny
     * jitter, and steps rounded to what your mouse sensitivity can actually produce.
     */
    private boolean turnTo(float yaw, float pitch, float tolerance) {
        float dy = MathHelper.wrapDegrees(yaw - aimYaw), dp = pitch - aimPitch;
        float dist = (float) Math.sqrt(dy * dy + dp * dp);
        if (dist <= tolerance) {
            Rotations.rotate(aimYaw, aimPitch, 50);
            return true;
        }
        float step;
        if (humanize.get()) {
            step = dist * (0.35f + rng.nextFloat() * 0.25f) + 1.5f;
            if (!overshot && dist > 25) { step *= 1.08f; overshot = true; }
            step = Math.min(step, (float) (aimSpeed.get() * (0.85 + rng.nextDouble() * 0.3)));
        } else step = (float) Math.min(aimSpeed.get(), dist);
        step = Math.min(step, dist * 1.08f);
        float my = dy / dist * step, mp = dp / dist * step;
        if (humanize.get()) {
            my += (float) rng.nextGaussian() * 0.12f;
            mp += (float) rng.nextGaussian() * 0.12f;
            float gcd = mouseStep();
            my = Math.round(my / gcd) * gcd;
            mp = Math.round(mp / gcd) * gcd;
        }
        aimYaw += my;
        aimPitch = MathHelper.clamp(aimPitch + mp, -90, 90);
        Rotations.rotate(aimYaw, aimPitch, 50);
        float ry = MathHelper.wrapDegrees(yaw - aimYaw), rp = pitch - aimPitch;
        return Math.sqrt(ry * ry + rp * rp) <= tolerance;
    }

    // The smallest turn a real mouse can make at your sensitivity (how the game turns mouse movement into degrees)
    private float mouseStep() {
        double f = mc.options.getMouseSensitivity().getValue() * 0.6 + 0.2;
        return (float) (f * f * f * 8.0 * 0.15);
    }

    // ---- Items ----

    private boolean use(FindItemResult item, BlockHitResult hit) {
        if (!item.found() || !InvUtils.swap(item.slot(), true)) return false;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        InvUtils.swapBack();
        return true;
    }

    private FindItemResult rail() {
        return InvUtils.findInHotbar(s -> s.getItem() instanceof BlockItem b && b.getBlock() instanceof AbstractRailBlock);
    }

    private FindItemResult cart() {
        return InvUtils.findInHotbar(Items.TNT_MINECART);
    }

    private boolean holdingFlameBow() {
        for (ItemStack s : new ItemStack[]{mc.player.getMainHandStack(), mc.player.getOffHandStack()}) {
            if (s.isOf(Items.BOW) && Utils.hasEnchantment(s, Enchantments.FLAME)) return true;
        }
        return false;
    }

    // ---- Render ----

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!showPath.get() || (stage != Stage.RAIL && stage != Stage.CART) || path.size() < 2) return;
        for (int i = 1; i < path.size(); i++) {
            Vec3d a = path.get(i - 1), b = path.get(i);
            event.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, pathColor.get());
        }
        if (railPos == null) return;
        double x = railPos.getX(), y = railPos.getY(), z = railPos.getZ();
        event.renderer.box(x + 0.02, y, z + 0.02, x + 0.98, y + 0.7, z + 0.98, cartColor.get(), pathColor.get(), ShapeMode.Both, 0);
    }
}
