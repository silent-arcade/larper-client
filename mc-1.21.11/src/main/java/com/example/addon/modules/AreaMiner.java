package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.util.Automation;
import com.example.addon.util.BaritoneBridge;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.events.entity.player.InteractBlockEvent;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dir;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.registry.tag.ItemTags;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.List;

public class AreaMiner extends Module {
    private enum Phase { SELECT, GOTO_START, DESCEND, TUNNEL, SHIFT }
    public enum Pickaxe { ThreeByThree, Normal }

    private final SettingGroup sgArea = settings.getDefaultGroup();
    private final SettingGroup sgMining = settings.createGroup("Mining");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgPreview = settings.createGroup("Preview");

    private final Setting<BlockPos> corner1 = sgArea.add(new BlockPosSetting.Builder()
        .name("start-pos").description("Start corner of the area. Left-click a block, or use the buttons above.")
        .defaultValue(BlockPos.ORIGIN).build());

    private final Setting<BlockPos> corner2 = sgArea.add(new BlockPosSetting.Builder()
        .name("end-pos").description("End corner (the opposite corner). Right-click a block, or use the buttons above.").defaultValue(BlockPos.ORIGIN).build());

    private final Setting<Boolean> showArea = sgArea.add(new BoolSetting.Builder()
        .name("show-area").description("Outline the area.").defaultValue(true).build());

    private final Setting<SettingColor> areaColor = sgArea.add(new ColorSetting.Builder()
        .name("area-color").description("Outline colour.").defaultValue(new SettingColor(255, 170, 0, 255)).visible(showArea::get).build());

    private final Setting<Pickaxe> pickaxe = sgMining.add(new EnumSetting.Builder<Pickaxe>()
        .name("pickaxe").description("ThreeByThree: hit only the middle of each 3x3 face and let the pickaxe break the rest. Normal: break every block.")
        .defaultValue(Pickaxe.ThreeByThree).build());

    private final Setting<Integer> settleTicks = sgMining.add(new IntSetting.Builder()
        .name("3x3-wait").description("Ticks to wait after a middle block breaks, for the server to break the other 8, before hitting anything else.")
        .defaultValue(4).range(0, 20).sliderRange(0, 10).visible(() -> pickaxe.get() == Pickaxe.ThreeByThree).build());

    private final Setting<Double> turnSpeed = sgMining.add(new DoubleSetting.Builder()
        .name("turn-speed").description("Fastest you turn (degrees per tick). Turns speed up and slow down smoothly like a hand on a mouse; lower looks calmer.")
        .defaultValue(9).range(2, 90).sliderRange(4, 40).build());

    private final Setting<Boolean> humanize = sgMining.add(new BoolSetting.Builder()
        .name("humanize").description("Slight wobble while aiming, a random spot on each block, and short random pauses between blocks.")
        .defaultValue(true).build());

    private final Setting<Boolean> humanWalk = sgMining.add(new BoolSetting.Builder()
        .name("human-walking").description("When Baritone walks you to the area it looks where it walks, turns smoothly and never parkours.").defaultValue(true).build());

    private final Setting<Boolean> pauseOnMove = sgSafety.add(new BoolSetting.Builder()
        .name("pause-when-you-move").description("Press a movement key to take over: it pauses, and carries on 2 seconds after you let go.").defaultValue(true).build());

    private final Setting<Boolean> pauseOnPlayer = sgSafety.add(new BoolSetting.Builder()
        .name("pause-near-players").description("Pause while another player (not a friend) is close.").defaultValue(true).build());

    private final Setting<Integer> playerRange = sgSafety.add(new IntSetting.Builder()
        .name("player-range").description("How close counts as near (blocks).").defaultValue(24).range(4, 128).sliderRange(8, 64)
        .visible(pauseOnPlayer::get).build());

    private final Setting<Boolean> pauseOnHealth = sgSafety.add(new BoolSetting.Builder()
        .name("pause-low-health").description("Pause while your health is low.").defaultValue(true).build());

    private final Setting<Double> minHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("min-health").description("Pause below this many hearts.").defaultValue(6).range(1, 10).sliderRange(1, 10)
        .visible(pauseOnHealth::get).build());

    private final Setting<Boolean> stopAtLiquid = sgSafety.add(new BoolSetting.Builder()
        .name("stop-at-liquid").description("Stop instead of digging into lava or water.").defaultValue(true).build());

    private final Setting<Boolean> preview = sgPreview.add(new BoolSetting.Builder()
        .name("preview").description("Show every block that's still going to be mined, like a Litematica overlay. It shrinks as they get mined.").defaultValue(true).build());

    private final Setting<SettingColor> previewFill = sgPreview.add(new ColorSetting.Builder()
        .name("block-color").description("Fill of the blocks to mine.").defaultValue(new SettingColor(80, 190, 255, 38)).visible(preview::get).build());

    private final Setting<SettingColor> previewLine = sgPreview.add(new ColorSetting.Builder()
        .name("outline-color").description("Outline around the blocks to mine.").defaultValue(new SettingColor(80, 190, 255, 150)).visible(preview::get).build());

    private final Setting<Boolean> highlightOres = sgPreview.add(new BoolSetting.Builder()
        .name("highlight-ores").description("Ores (and ancient debris) in the area show in their own colour.").defaultValue(true).visible(preview::get).build());

    private final Setting<SettingColor> oreColor = sgPreview.add(new ColorSetting.Builder()
        .name("ore-color").description("Colour for ores in the area.").defaultValue(new SettingColor(255, 200, 40, 90)).visible(() -> preview.get() && highlightOres.get()).build());

    private final Setting<Boolean> showTarget = sgPreview.add(new BoolSetting.Builder()
        .name("show-target").description("Highlight the block being broken right now.").defaultValue(true).visible(preview::get).build());

    private final Setting<Boolean> layerOnly = sgPreview.add(new BoolSetting.Builder()
        .name("current-layer-only").description("Only show the 3-block layer being mined now (or next), instead of the whole area.").defaultValue(false).visible(preview::get).build());

    private final Setting<Integer> previewRange = sgPreview.add(new IntSetting.Builder()
        .name("preview-range").description("Only show blocks within this many blocks of you.").defaultValue(24).range(4, 64).sliderRange(8, 48).visible(preview::get).build());

    private final Setting<Integer> maxBlocks = sgPreview.add(new IntSetting.Builder()
        .name("max-blocks").description("Most blocks drawn at once (the nearest ones). Lower it on a low-end PC.").defaultValue(5000).range(100, 50000).sliderRange(500, 20000).visible(preview::get).build());

    private final Setting<Boolean> showProgress = sgPreview.add(new BoolSetting.Builder()
        .name("progress").description("Show how many blocks are left (and the percentage done) at the top of the screen.").defaultValue(true).build());

    private static final int ALL_SIDES = Dir.UP | Dir.DOWN | Dir.NORTH | Dir.SOUTH | Dir.WEST | Dir.EAST;
    private int minX, minY, minZ, maxX, maxY, maxZ;
    private final Set<Long> previewSet = new HashSet<>();
    private final List<BlockPos> previewList = new ArrayList<>();
    private int previewTicks, remaining = -1, startTotal = -1;
    private Phase phase;
    private int bandBottom, rowZ, rowDir, goalX, shiftTo, stuckTicks;
    private boolean edgeFirst;
    private String pauseReason;
    private long startedAt;
    private java.util.Map<String, Object> savedBaritone;
    private BlockPos startStand, lastPos, descendSpot;
    private boolean baritone;
    private boolean picked1;
    private BlockPos mining;
    private long lastClickTick = -1;
    private int waitTicks;
    private final java.util.Random rng = new java.util.Random();
    private final com.example.addon.util.HumanAim aim = new com.example.addon.util.HumanAim();
    private Vec3d aimOffset = Vec3d.ZERO;

    public AreaMiner() {
        super(AddonTemplate.AUTOMATION, "area-miner", "Clears an area between two corners in 3x3 tunnels (made for 3x3 pickaxes).");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        WHorizontalList start = list.add(theme.horizontalList()).widget();
        start.add(theme.button("Set START = block I look at")).widget().action = () -> setCorner(corner1, "START", false);
        start.add(theme.button("START = where I stand")).widget().action = () -> setCorner(corner1, "START", true);
        WHorizontalList end = list.add(theme.horizontalList()).widget();
        end.add(theme.button("Set END = block I look at")).widget().action = () -> setCorner(corner2, "END", false);
        end.add(theme.button("END = where I stand")).widget().action = () -> setCorner(corner2, "END", true);
        WHorizontalList go = list.add(theme.horizontalList()).widget();
        WButton mine = go.add(theme.button("Start mining")).widget();
        mine.action = () -> {
            if (!isActive()) toggle();
            beginMining();
        };
        go.add(theme.button("Clear area")).widget().action = () -> {
            corner1.set(BlockPos.ORIGIN);
            corner2.set(BlockPos.ORIGIN);
            picked1 = false;
            ChatUtils.info("Area Miner: area cleared.");
        };
        return list;
    }

    private void setCorner(Setting<BlockPos> corner, String n, boolean standing) {
        if (mc.player == null) return;
        BlockPos pos = !standing && mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
            ? hit.getBlockPos() : mc.player.getBlockPos();
        corner.set(pos);
        if (corner == corner1) picked1 = true;
        ChatUtils.info("Area Miner %s: %d %d %d%s", n, pos.getX(), pos.getY(), pos.getZ(), sizeText());
    }

    private boolean isSet(Setting<BlockPos> c) {
        return !c.get().equals(BlockPos.ORIGIN);
    }

    private String sizeText() {
        if (!isSet(corner1) || !isSet(corner2)) return "";
        BlockPos a = corner1.get(), b = corner2.get();
        return String.format(" - area %d x %d x %d", Math.abs(a.getX() - b.getX()) + 1, Math.abs(a.getY() - b.getY()) + 1, Math.abs(a.getZ() - b.getZ()) + 1);
    }

    @Override
    public void onActivate() {
        phase = Phase.SELECT;
        picked1 = false;
        mining = null;
        ChatUtils.info("Area Miner: left-click the START block, right-click the END block, then sneak + right-click to start. (Steps are shown on screen.)");
        if (isSet(corner1) && isSet(corner2)) ChatUtils.info("(Your last area is still set: sneak + right-click to mine it again.)");
    }

    @EventHandler
    private void onLeftClick(StartBreakingBlockEvent event) {
        if (phase != Phase.SELECT) return;
        event.cancel();
        corner1.set(event.blockPos);
        picked1 = true;
        ChatUtils.info("START: %s%s", event.blockPos.toShortString(), sizeText());
    }

    @EventHandler
    private void onRightClick(InteractBlockEvent event) {
        if (phase != Phase.SELECT) return;
        event.cancel();
        if (mc.world.getTime() == lastClickTick) return;
        lastClickTick = mc.world.getTime();
        if (mc.player.isSneaking()) {
            beginMining();
            return;
        }
        corner2.set(event.result.getBlockPos());
        ChatUtils.info("END: %s%s. Sneak + right-click to start.", corner2.get().toShortString(), sizeText());
    }

    private void beginMining() {
        BlockPos a = corner1.get(), b = corner2.get();
        if (a.equals(BlockPos.ORIGIN) && b.equals(BlockPos.ORIGIN)) {
            ChatUtils.error("Set a START and an END position first.");
            return;
        }
        minX = Math.min(a.getX(), b.getX()); maxX = Math.max(a.getX(), b.getX());
        minY = Math.min(a.getY(), b.getY()); maxY = Math.max(a.getY(), b.getY());
        minZ = Math.min(a.getZ(), b.getZ()); maxZ = Math.max(a.getZ(), b.getZ());
        rowDir = 1;
        rowZ = rowCenter(minZ + 1);
        startStand = new BlockPos(clampX(minX + 1), maxY + 1, rowZ);
        bandBottom = maxY + 1;
        phase = Phase.GOTO_START;
        startTotal = -1;
        refreshPreview();
        startTotal = remaining;
        pauseReason = null;
        stuckTicks = 0;
        startedAt = System.currentTimeMillis();
        baritone = BaritoneBridge.available();
        if (baritone) {
            if (humanWalk.get() && savedBaritone == null) savedBaritone = BaritoneBridge.apply(BaritoneBridge.humanWalking());
            BaritoneBridge.command("goto " + startStand.getX() + " " + startStand.getY() + " " + startStand.getZ());
        } else ChatUtils.info("Walk to the green marker on top of the area to start (Baritone didn't load, so it can't walk you there).");
        ChatUtils.info("Area Miner: mining %d x %d x %d blocks.", maxX - minX + 1, maxY - minY + 1, maxZ - minZ + 1);
    }

    public void startSaved() {
        if (isActive()) beginMining();
    }

    @Override
    public void onDeactivate() {
        release();
        if (baritone) BaritoneBridge.command("stop");
        baritone = false;
        BaritoneBridge.restore(savedBaritone);
        savedBaritone = null;
    }

    private void release() {
        if (mc.options == null) return;
        mc.options.forwardKey.setPressed(false);
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
    }

    private void stop(String why) {
        ChatUtils.info("Area Miner: %s", why);
        toggle();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player != null && mc.world != null && ++previewTicks >= 10) {
            previewTicks = 0;
            refreshPreview();
        }
        if (mc.player == null || mc.world == null || phase == Phase.SELECT) return;

        String reason = Automation.pauseReason(pauseOnMove.get(), pauseOnPlayer.get(), playerRange.get(), pauseOnHealth.get(), minHealth.get());
        if (reason != null && pauseReason == null) {
            if (!reason.startsWith("you're")) ChatUtils.warning("Area Miner paused: %s", reason);
            if (baritone && phase == Phase.GOTO_START) BaritoneBridge.command("pause");
        } else if (reason == null && pauseReason != null) {
            if (!pauseReason.startsWith("you're")) ChatUtils.info("Area Miner resumed.");
            if (baritone && phase == Phase.GOTO_START) BaritoneBridge.command("resume");
        }
        pauseReason = reason;
        if (reason != null || mc.currentScreen != null) {
            release();
            stuckTicks = 0;
            return;
        }

        BlockPos feet = mc.player.getBlockPos();
        if (phase == Phase.GOTO_START) {
            if (arrived(startStand) && mc.player.isOnGround()) {
                if (baritone) BaritoneBridge.command("stop");
                phase = Phase.DESCEND;
                descendSpot = startStand;
            }
            return;
        }

        if (phase != Phase.DESCEND && feet.getY() < bandBottom - 1 && mc.player.isOnGround()) {
            stop("fell below the current layer (cave?) - stopped.");
            return;
        }

        stuckTicks = feet.equals(lastPos) ? stuckTicks + 1 : 0;
        lastPos = feet;
        if (stuckTicks > 400) {
            stop("stuck for 20 seconds - stopped.");
            return;
        }

        holdPickaxe();
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }
        switch (phase) {
            case DESCEND -> descend(feet);
            case TUNNEL -> tunnel(feet);
            case SHIFT -> shift(feet);
            default -> { }
        }
    }

    private void descend(BlockPos feet) {
        int target = Math.max(minY, bandBottom - 3);
        if (bandBottom <= minY) {
            stop("finished - the area is cleared.");
            return;
        }
        if (feet.getY() > target) {
            if (!mc.player.isOnGround()) return;
            BlockPos spot = new BlockPos(descendSpot.getX(), feet.getY(), descendSpot.getZ());
            if (!near(spot)) {
                walkTo(spot);
                return;
            }
            mc.options.forwardKey.setPressed(false);
            BlockPos below = feet.down();
            List<BlockPos> floor = new ArrayList<>();
            for (int dz = -1; dz <= 1; dz++) for (int dx = -1; dx <= 1; dx++) floor.add(below.add(dx, 0, dz));
            clearOf(floor, Direction.EAST, Direction.SOUTH);
            return;
        }
        bandBottom = target;
        phase = Phase.TUNNEL;
        edgeFirst = true;
        goalX = Math.abs(feet.getX() - minX) <= Math.abs(feet.getX() - maxX) ? minX : maxX;
    }

    private void tunnel(BlockPos feet) {
        if (feet.getX() == goalX && near(new BlockPos(goalX, feet.getY(), rowZ))) {
            if (edgeFirst) {
                edgeFirst = false;
                goalX = goalX == minX ? maxX : minX;
                return;
            }
            int next = rowCenter(rowZ + 3 * rowDir);
            if (next != rowZ && next >= minZ && next <= maxZ) {
                shiftTo = next;
                phase = Phase.SHIFT;
            } else {
                rowDir = -rowDir;
                phase = Phase.DESCEND;
                descendSpot = new BlockPos(clampX(feet.getX()), feet.getY(), rowZ);
            }
            return;
        }
        int dir = Integer.signum(goalX - feet.getX());
        if (dir == 0) {
            walkTo(new BlockPos(goalX, feet.getY(), rowZ));
            return;
        }
        BlockPos ahead = new BlockPos(feet.getX() + dir, feet.getY(), rowZ);
        List<BlockPos> face = new ArrayList<>();
        for (int dy = 0; dy < 3; dy++) for (int dz = -1; dz <= 1; dz++) face.add(ahead.add(0, dy, dz));
        if (!clearOf(face, Direction.UP, Direction.SOUTH)) return;
        walkTo(ahead);
    }

    private void shift(BlockPos feet) {
        if (feet.getZ() == shiftTo && near(new BlockPos(feet.getX(), feet.getY(), shiftTo))) {
            BlockPos beyond = new BlockPos(feet.getX(), feet.getY(), shiftTo + Integer.signum(shiftTo - rowZ));
            List<BlockPos> far = new ArrayList<>();
            for (int dy = 0; dy < 3; dy++) for (int dx = -1; dx <= 1; dx++) far.add(beyond.add(dx, dy, 0));
            if (!clearOf(far, Direction.UP, Direction.EAST)) return;
            rowZ = shiftTo;
            goalX = feet.getX() == minX ? maxX : minX;
            edgeFirst = false;
            phase = Phase.TUNNEL;
            return;
        }
        int dir = Integer.signum(shiftTo - feet.getZ());
        BlockPos ahead = new BlockPos(feet.getX(), feet.getY(), feet.getZ() + dir);
        List<BlockPos> face = new ArrayList<>();
        for (int dy = 0; dy < 3; dy++) for (int dx = -1; dx <= 1; dx++) face.add(ahead.add(dx, dy, 0));
        if (!clearOf(face, Direction.UP, Direction.EAST)) return;
        walkTo(ahead);
    }

    private boolean clearOf(List<BlockPos> face, Direction u, Direction v) {
        BlockPos middle = face.get(face.size() / 2);
        if (mining != null && mc.world.getBlockState(mining).isAir()) {
            boolean wasMiddle = mining.equals(middle);
            mining = null;
            waitTicks = (wasMiddle && pickaxe.get() == Pickaxe.ThreeByThree ? settleTicks.get() : 0) + (humanize.get() ? rng.nextInt(3) : 0);
            if (waitTicks > 0) return false;
        }
        BlockPos target = null;
        Vec3d targetPoint = null;
        int best = Integer.MIN_VALUE;
        boolean threeByThree = pickaxe.get() == Pickaxe.ThreeByThree;
        for (BlockPos p : face) {
            if (!inArea(p)) continue;
            BlockState s = mc.world.getBlockState(p);
            if (stopAtLiquid.get() && !s.getFluidState().isEmpty()) {
                stop("liquid ahead at " + p.toShortString() + " - stopped.");
                return false;
            }
            if (!breakable(p)) continue;
            int score = threeByThree ? reach(p, u, v) * 2 + (p.equals(middle) ? 1 : 0) : (p.equals(middle) ? (u == Direction.UP ? 1 : -1) : 0);
            if (threeByThree && u == Direction.UP && breaksFloor(p)) score -= 100;
            Vec3d seen = seenPoint(p);
            if (seen != null) score += 1000;
            if (score > best) {
                best = score;
                target = p;
                targetPoint = seen;
            }
        }
        if (target == null) return true;
        mine(target, targetPoint != null ? targetPoint : Vec3d.ofCenter(target));
        return false;
    }

    private boolean breakable(BlockPos p) {
        BlockState s = mc.world.getBlockState(p);
        return !s.isAir() && s.getHardness(mc.world, p) >= 0 && s.getFluidState().isEmpty();
    }

    private int reach(BlockPos p, Direction u, Direction v) {
        int n = 0;
        for (int a = -1; a <= 1; a++) for (int b = -1; b <= 1; b++) {
            BlockPos q = p.offset(u, a).offset(v, b);
            if (inArea(q) && breakable(q) && (u != Direction.UP || q.getY() >= bandBottom)) n++;
        }
        return n;
    }

    private boolean breaksFloor(BlockPos p) {
        if (p.getY() > bandBottom) return false;
        BlockPos below = p.down();
        return inArea(below) && breakable(below);
    }

    private Vec3d seenPoint(BlockPos p) {
        Vec3d eye = mc.player.getEyePos(), c = Vec3d.ofCenter(p);
        double range = mc.player.getBlockInteractionRange();
        List<Vec3d> points = new ArrayList<>();
        points.add(c);
        for (Direction d : Direction.values()) points.add(c.add(Vec3d.of(d.getVector()).multiply(0.4)));
        for (Vec3d pt : points) {
            if (eye.distanceTo(pt) > range) continue;
            boolean all = true;
            for (Vec3d off : new Vec3d[] {Vec3d.ZERO, new Vec3d(0.12, 0, 0), new Vec3d(-0.12, 0, 0), new Vec3d(0, 0.12, 0), new Vec3d(0, -0.12, 0), new Vec3d(0, 0, 0.12), new Vec3d(0, 0, -0.12)}) {
                if (!hits(eye, pt.add(off), p)) {
                    all = false;
                    break;
                }
            }
            if (all) return pt;
        }
        return null;
    }

    private boolean hits(Vec3d eye, Vec3d pt, BlockPos p) {
        BlockHitResult r = mc.world.raycast(new net.minecraft.world.RaycastContext(eye, pt, net.minecraft.world.RaycastContext.ShapeType.OUTLINE, net.minecraft.world.RaycastContext.FluidHandling.NONE, mc.player));
        return r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(p);
    }

    private void mine(BlockPos pos, Vec3d point) {
        mc.options.forwardKey.setPressed(false);
        if (!pos.equals(mining)) {
            mining = pos;
            aimOffset = humanize.get() && pickaxe.get() != Pickaxe.ThreeByThree ? new Vec3d(rng.nextDouble() - 0.5, rng.nextDouble() - 0.5, rng.nextDouble() - 0.5).multiply(0.5) : Vec3d.ZERO;
        }
        HitResult seen = mc.player.raycast(mc.player.getBlockInteractionRange(), 1, false);
        Vec3d aim = point.add(aimOffset.multiply(0.3));
        turnTowards(hits(mc.player.getEyePos(), aim, pos) ? aim : point);
        if (!(seen instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(pos)) return;
        if (mc.interactionManager.updateBlockBreakingProgress(pos, hit.getSide())) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private void walkTo(BlockPos pos) {
        mining = null;
        Vec3d c = Vec3d.ofBottomCenter(pos);
        double dx = c.x - mc.player.getX(), dz = c.z - mc.player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
        float yawErr = Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw()));
        turnTowardsAngles(yaw, 15);
        mc.options.forwardKey.setPressed(Math.hypot(dx, dz) > 0.2 && yawErr < 35);
    }

    private boolean turnTowards(Vec3d target) {
        Vec3d d = target.subtract(mc.player.getEyePos());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -90, 90);
        return turnTowardsAngles(yaw, pitch);
    }

    private boolean turnTowardsAngles(float yaw, float pitch) {
        aim.turn(mc.player, yaw, pitch, turnSpeed.get().floatValue(), false);
        return Math.abs(MathHelper.wrapDegrees(yaw - mc.player.getYaw())) < 4 && Math.abs(pitch - mc.player.getPitch()) < 4;
    }

    private boolean arrived(BlockPos pos) {
        return Math.hypot(pos.getX() + 0.5 - mc.player.getX(), pos.getZ() + 0.5 - mc.player.getZ()) < 1.5 && Math.abs(pos.getY() - mc.player.getY()) < 1.1;
    }

    private boolean near(BlockPos pos) {
        return Math.hypot(pos.getX() + 0.5 - mc.player.getX(), pos.getZ() + 0.5 - mc.player.getZ()) < 0.25 && Math.abs(pos.getY() - mc.player.getY()) < 1.1;
    }

    private void holdPickaxe() {
        if (mc.player.getMainHandStack().isIn(ItemTags.PICKAXES)) return;
        FindItemResult pick = InvUtils.findInHotbar(s -> s.isIn(ItemTags.PICKAXES));
        if (pick.found()) InvUtils.swap(pick.slot(), false);
    }

    private int rowCenter(int z) {
        if (maxZ - minZ < 2) return (minZ + maxZ) / 2;
        return MathHelper.clamp(z, minZ + 1, maxZ - 1);
    }

    private int clampX(int x) {
        if (maxX - minX < 2) return (minX + maxX) / 2;
        return MathHelper.clamp(x, minX + 1, maxX - 1);
    }

    private boolean inArea(BlockPos p) {
        return p.getX() >= minX && p.getX() <= maxX && p.getY() >= minY && p.getY() <= maxY && p.getZ() >= minZ && p.getZ() <= maxZ;
    }

    private int[] previewBounds() {
        if (phase != null && phase != Phase.SELECT) return new int[] {minX, minY, minZ, maxX, maxY, maxZ};
        if (!isSet(corner1) || !isSet(corner2)) return null;
        BlockPos a = corner1.get(), b = corner2.get();
        return new int[] {Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ())};
    }

    private static boolean isOre(BlockState s) {
        String path = net.minecraft.registry.Registries.BLOCK.getId(s.getBlock()).getPath();
        return path.endsWith("_ore") || path.equals("ancient_debris");
    }

    private void refreshPreview() {
        previewSet.clear();
        previewList.clear();
        int[] b = previewBounds();
        if (b == null || !isActive()) {
            remaining = -1;
            return;
        }
        long volume = (long) (b[3] - b[0] + 1) * (b[4] - b[1] + 1) * (b[5] - b[2] + 1);
        if (showProgress.get() && volume <= 250_000) {
            int n = 0;
            for (BlockPos q : BlockPos.iterate(b[0], b[1], b[2], b[3], b[4], b[5])) if (breakable(q)) n++;
            remaining = n;
        } else remaining = -1;
        if (!preview.get()) return;

        int y0 = b[1], y1 = b[4];
        if (layerOnly.get()) {
            boolean digging = phase == Phase.TUNNEL || phase == Phase.SHIFT;
            int top = digging ? Math.min(b[4], bandBottom + 2) : (phase == null || phase == Phase.SELECT || phase == Phase.GOTO_START ? b[4] : bandBottom - 1);
            y1 = Math.min(b[4], top);
            y0 = Math.max(b[1], y1 - 2);
        }
        BlockPos me = mc.player.getBlockPos();
        int rr = previewRange.get();
        int x0 = Math.max(b[0], me.getX() - rr), x1 = Math.min(b[3], me.getX() + rr);
        int z0 = Math.max(b[2], me.getZ() - rr), z1 = Math.min(b[5], me.getZ() + rr);
        y0 = Math.max(y0, me.getY() - rr);
        y1 = Math.min(y1, me.getY() + rr);
        if (x0 > x1 || y0 > y1 || z0 > z1) return;
        for (BlockPos q : BlockPos.iterate(x0, y0, z0, x1, y1, z1)) {
            if (!breakable(q)) continue;
            BlockPos im = q.toImmutable();
            previewList.add(im);
        }
        if (previewList.size() > maxBlocks.get()) {
            previewList.sort((p1, p2) -> Double.compare(p1.getSquaredDistance(me), p2.getSquaredDistance(me)));
            previewList.subList(maxBlocks.get(), previewList.size()).clear();
        }
        for (BlockPos q : previewList) previewSet.add(q.asLong());
    }

    private void renderPreview(Render3DEvent event) {
        SettingColor fill = previewFill.get(), line = previewLine.get(), ore = oreColor.get();
        SettingColor oreLine = new SettingColor(ore.r, ore.g, ore.b, Math.min(255, ore.a * 2 + 40));
        for (BlockPos q : previewList) {
            int exclude = 0;
            for (Direction d : Direction.values()) if (previewSet.contains(q.offset(d).asLong())) exclude |= Dir.get(d);
            boolean isOre = highlightOres.get() && isOre(mc.world.getBlockState(q));
            if (exclude == ALL_SIDES && !isOre) continue;
            if (isOre) event.renderer.box(q, ore, oreLine, ShapeMode.Both, 0);
            else event.renderer.box(q, fill, line, ShapeMode.Both, exclude);
        }
        if (showTarget.get() && mining != null && isActive()) {
            event.renderer.box(mining, new SettingColor(255, 255, 255, 70), new SettingColor(255, 255, 255, 255), ShapeMode.Both, 0);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (preview.get() && isActive() && !previewList.isEmpty()) renderPreview(event);
        if (!showArea.get()) return;
        BlockPos a = corner1.get(), b = corner2.get();
        if (isActive() && phase == Phase.SELECT && picked1 && mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) b = hit.getBlockPos();
        if (a.equals(BlockPos.ORIGIN) && b.equals(BlockPos.ORIGIN)) return;
        Box box = new Box(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()),
            Math.max(a.getX(), b.getX()) + 1, Math.max(a.getY(), b.getY()) + 1, Math.max(a.getZ(), b.getZ()) + 1);
        event.renderer.box(box, areaColor.get(), areaColor.get(), ShapeMode.Lines, 0);
        if (isSet(corner1)) event.renderer.box(new Box(a), new SettingColor(60, 255, 90, 50), new SettingColor(60, 255, 90, 255), ShapeMode.Both, 0);
        if (isSet(corner2) || picked1) event.renderer.box(new Box(b), new SettingColor(255, 70, 70, 50), new SettingColor(255, 70, 70, 255), ShapeMode.Both, 0);
        if (isActive() && phase == Phase.GOTO_START && startStand != null) {
            event.renderer.box(new Box(startStand), new SettingColor(60, 255, 90, 60), new SettingColor(60, 255, 90, 255), ShapeMode.Both, 0);
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.player == null || mc.options.hudHidden) return;
        if (showArea.get()) {
            label(event.drawContext, corner1, "START", 0xFF3CFF5A);
            label(event.drawContext, corner2, "END", 0xFFFF4646);
        }
        if (isActive() && showProgress.get() && remaining >= 0 && phase != Phase.SELECT) {
            String text = startTotal > 0
                ? String.format("Area Miner: %d blocks left  (%d%% done, %s)", remaining, Math.round(100.0 * (startTotal - remaining) / startTotal), Automation.timing(startedAt, startTotal - remaining, startTotal))
                : String.format("Area Miner: %d blocks left", remaining);
            List<String> lines = new ArrayList<>();
            lines.add(text);
            if (pauseReason != null) lines.add("  PAUSED: " + pauseReason);
            else if (phase == Phase.GOTO_START) lines.add(baritone ? "  walking to the start (green marker)" : "  walk to the green marker to start");
            Automation.status(event.drawContext, "Area Miner", lines, 0xFF8FD8FF);
        }
        if (!isActive() || phase != Phase.SELECT) return;
        boolean s1 = isSet(corner1) && picked1, s2 = isSet(corner2) && s1;
        String[] lines = {
            "AREA MINER - set your area",
            (s1 ? "[done] " : "> ") + "1. Left-click the START block" + (s1 ? "  (" + corner1.get().toShortString() + ")" : ""),
            (s2 ? "[done] " : s1 ? "> " : "   ") + "2. Right-click the END block" + (s2 ? "  (" + corner2.get().toShortString() + ")" : ""),
            (s2 ? "> " : "   ") + "3. Sneak + right-click to start mining" + (s2 ? sizeText().replace(" - ", "  (") + ")" : ""),
            remaining >= 0 && s2 ? "   Preview: " + remaining + " blocks to mine (shown in blue, ores in gold)" : "",
        };
        lines = java.util.Arrays.stream(lines).filter(l -> !l.isEmpty()).toArray(String[]::new);
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.textRenderer.getWidth(l));
        int x = (mc.getWindow().getScaledWidth() - w) / 2, y = 8, h = lines.length * 11 + 6;
        event.drawContext.fill(x - 6, y - 4, x + w + 6, y + h - 4, 0xA0101018);
        for (int i = 0; i < lines.length; i++) {
            int col = i == 0 ? 0xFFFFAA00 : lines[i].startsWith("[done]") ? 0xFF6CFF7A : lines[i].startsWith(">") ? 0xFFFFFFFF : 0xFF8A8A96;
            event.drawContext.drawTextWithShadow(mc.textRenderer, lines[i], x, y + i * 11, col);
        }
    }

    private void label(net.minecraft.client.gui.DrawContext ctx, Setting<BlockPos> corner, String text, int color) {
        if (!isSet(corner)) return;
        org.joml.Vector3d v = new org.joml.Vector3d(corner.get().getX() + 0.5, corner.get().getY() + 1.6, corner.get().getZ() + 0.5);
        if (!meteordevelopment.meteorclient.utils.render.NametagUtils.to2D(v, 1)) return;
        double s = mc.getWindow().getScaleFactor();
        int w = mc.textRenderer.getWidth(text), x = (int) (v.x / s) - w / 2, y = (int) (v.y / s);
        ctx.fill(x - 3, y - 2, x + w + 3, y + 10, 0x90000000);
        ctx.drawTextWithShadow(mc.textRenderer, text, x, y, color);
    }
}
