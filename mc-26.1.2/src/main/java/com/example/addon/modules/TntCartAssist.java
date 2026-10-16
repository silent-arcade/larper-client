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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.arrow.AbstractArrow;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.BaseRailBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

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
    private final List<Vec3> path = new ArrayList<>();
    private Stage stage = Stage.IDLE;
    private AbstractArrow arrow;
    private BlockPos railPos;
    private int impactIn;
    private int waited, cartWait, age;
    private float aimYaw, aimPitch;
    private boolean overshot;

    public TntCartAssist() {
        super(AddonTemplate.AUTOMATION, "tnt-cart-assist", "Flame bow shot: puts a rail + TNT minecart in the arrow's path.");
    }

    @Override
    public void onDeactivate() {
        stage = Stage.IDLE;
        arrow = null;
        path.clear();
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (mc.player == null || !(event.entity instanceof AbstractArrow a)) return;
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
        if (mc.player == null || mc.level == null || stage == Stage.IDLE) return;
        if (++age > 200) {
            finish();
            return;
        }
        switch (stage) {
            case RAIL -> tickRail();
            case CART -> tickCart();
            case RETURN -> {
                if (!silentAim.get() || !smoothReturn.get() || turnTo(mc.player.getYRot(), mc.player.getXRot(), 2f)) stage = Stage.IDLE;
            }
            default -> { }
        }
    }

    private void tickRail() {
        waited++;
        if (arrow != null && !arrow.isRemoved()) repredict();
        if (railPos == null || !inReach(railPos)) {
            finish();
            return;
        }
        boolean railThere = mc.level.getBlockState(railPos).getBlock() instanceof BaseRailBlock;
        Vec3 spot = railSpot();
        boolean aimed = aim(spot);
        if (!aimed || !timeToPlace()) return;
        if (railThere) {
            placeCart();
            return;
        }
        if (use(rail(), new BlockHitResult(spot, Direction.UP, railPos.below(), false))) {
            stage = Stage.CART;
            cartWait = cartDelay.get();
            if (cartWait <= 0) placeCart();
        } else finish();
    }

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

    private boolean timeToPlace() {
        if (timing.get() == Timing.Manual) return waited > delay.get();
        int needed = 1 + cartDelay.get() + margin.get() + pingTicks();
        return impactIn - waited <= needed;
    }

    private Vec3 railSpot() {
        return Vec3.atCenterOf(railPos.below()).add(0, 0.5, 0);
    }

    private int pingTicks() {
        var entry = mc.getConnection() == null ? null : mc.getConnection().getPlayerInfo(mc.player.getUUID());
        return entry == null ? 1 : (int) Math.ceil(entry.getLatency() / 50.0) + 1;
    }

    private boolean repredict() {
        Vec3 pos = arrow.position(), vel = arrow.getDeltaMovement();
        path.clear();
        path.add(pos);
        BlockPos best = null;
        int bestTick = 0;
        for (int tick = 1; tick <= 200 && best == null; tick++) {
            Vec3 next = pos.add(vel);
            BlockHitResult hit = mc.level.clip(new ClipContext(pos, next, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
            Vec3 end = hit.getType() == HitResult.Type.BLOCK ? hit.getLocation() : next;
            if (intercept.get()) {
                double len = end.distanceTo(pos);
                Vec3 dir = len > 1e-6 ? end.subtract(pos).scale(1 / len) : Vec3.ZERO;
                for (double d = 0; d <= len && best == null; d += 0.2) {
                    BlockPos c = cartAt(pos.add(dir.scale(d)));
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
            boolean water = !mc.level.getFluidState(BlockPos.containing(pos)).isEmpty();
            vel = vel.scale(water ? 0.6 : 0.99).add(0, -0.05, 0);
        }
        if (best == null) return railPos != null;
        railPos = best;
        impactIn = bestTick + waited;
        return true;
    }

    private BlockPos cartAt(Vec3 p) {
        BlockPos c = BlockPos.containing(p);
        double fy = p.y - c.getY(), fx = p.x - c.getX(), fz = p.z - c.getZ();
        if (fy > 0.7 || fx < 0.02 || fx > 0.98 || fz < 0.02 || fz > 0.98) return null;
        return canHoldCart(c) && inReach(c) ? c : null;
    }

    private BlockPos landingSpot(BlockHitResult hit) {
        BlockPos pos = hit.getDirection() == Direction.UP ? hit.getBlockPos().above() : hit.getBlockPos().relative(hit.getDirection());
        for (int i = 0; i < 4; i++, pos = pos.below()) {
            if (canHoldCart(pos)) return inReach(pos) ? pos : null;
        }
        return null;
    }

    private boolean canHoldCart(BlockPos pos) {
        BlockState state = mc.level.getBlockState(pos);
        BlockPos below = pos.below();
        boolean free = state.canBeReplaced() || state.getBlock() instanceof BaseRailBlock;
        return free && mc.level.getBlockState(below).isFaceSturdy(mc.level, below, Direction.UP);
    }

    private boolean inReach(BlockPos rail) {
        return mc.player.isWithinBlockInteractionRange(rail.below(), 0) && mc.player.isWithinBlockInteractionRange(rail, 0);
    }

    private void startAim() {
        aimYaw = silentAim.get() ? Rotations.serverYaw : mc.player.getYRot();
        aimPitch = silentAim.get() ? Rotations.serverPitch : mc.player.getXRot();
    }

    private boolean aim(Vec3 spot) {
        float yaw = (float) Rotations.getYaw(spot), pitch = (float) Rotations.getPitch(spot);
        if (!silentAim.get()) {
            mc.player.setYRot(yaw);
            mc.player.setXRot(pitch);
            return true;
        }
        return turnTo(yaw, pitch, 1.2f);
    }

    private boolean turnTo(float yaw, float pitch, float tolerance) {
        float dy = Mth.wrapDegrees(yaw - aimYaw), dp = pitch - aimPitch;
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
        aimPitch = Mth.clamp(aimPitch + mp, -90, 90);
        Rotations.rotate(aimYaw, aimPitch, 50);
        float ry = Mth.wrapDegrees(yaw - aimYaw), rp = pitch - aimPitch;
        return Math.sqrt(ry * ry + rp * rp) <= tolerance;
    }

    private float mouseStep() {
        double f = mc.options.sensitivity().get() * 0.6 + 0.2;
        return (float) (f * f * f * 8.0 * 0.15);
    }

    private boolean use(FindItemResult item, BlockHitResult hit) {
        if (!item.found() || !InvUtils.swap(item.slot(), true)) return false;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        InvUtils.swapBack();
        return true;
    }

    private FindItemResult rail() {
        return InvUtils.findInHotbar(s -> s.getItem() instanceof BlockItem b && b.getBlock() instanceof BaseRailBlock);
    }

    private FindItemResult cart() {
        return InvUtils.findInHotbar(Items.TNT_MINECART);
    }

    private boolean holdingFlameBow() {
        for (ItemStack s : new ItemStack[]{mc.player.getMainHandItem(), mc.player.getOffhandItem()}) {
            if (s.is(Items.BOW) && Utils.hasEnchantment(s, Enchantments.FLAME)) return true;
        }
        return false;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!showPath.get() || (stage != Stage.RAIL && stage != Stage.CART) || path.size() < 2) return;
        for (int i = 1; i < path.size(); i++) {
            Vec3 a = path.get(i - 1), b = path.get(i);
            event.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, pathColor.get());
        }
        if (railPos == null) return;
        double x = railPos.getX(), y = railPos.getY(), z = railPos.getZ();
        event.renderer.box(x + 0.02, y, z + 0.02, x + 0.98, y + 0.7, z + 0.98, cartColor.get(), pathColor.get(), ShapeMode.Both, 0);
    }
}
