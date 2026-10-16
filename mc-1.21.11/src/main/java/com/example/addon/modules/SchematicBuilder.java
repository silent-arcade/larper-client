package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.schematic.Schematic;
import com.example.addon.util.Automation;
import com.example.addon.util.BaritoneBridge;
import com.example.addon.util.FileChoices;
import com.example.addon.util.FilePicker;
import meteordevelopment.meteorclient.events.entity.player.StartBreakingBlockEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BlockListSetting;
import meteordevelopment.meteorclient.settings.BlockPosSetting;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.FallingBlock;
import net.minecraft.block.enums.BedPart;
import net.minecraft.block.enums.DoubleBlockHalf;
import net.minecraft.item.BlockItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.screen.GenericContainerScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.state.property.Properties;
import net.minecraft.state.property.Property;
import net.minecraft.util.BlockRotation;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.RaycastContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

public class SchematicBuilder extends Module {
    public enum Portion { Full, ThreeQuarters, Half, Quarter, Custom }
    public enum From { Bottom, Top }
    public enum Turn { None, Right90, Turn180, Left90 }
    public enum Speed { Human, Fast, VeryFast }

    private static final String[] EXTENSIONS = {".litematic", ".schem", ".nbt"};
    private static final Set<String> FREE_PROPS = Set.of("waterlogged", "powered", "power", "lit", "shape", "north", "south", "east", "west",
        "up", "down", "distance", "persistent", "occupied", "triggered", "age", "moisture", "snowy", "in_wall", "open", "extended", "short",
        "locked", "delay", "mode", "has_book", "has_record", "enabled", "attached", "disarmed", "note", "instrument", "honey_level", "bites",
        "candles", "pickles", "eggs", "layers", "charges", "signal_fire", "level", "unstable", "drag", "bottom", "conditional", "hatch", "berries",
        "inverted", "has_bottle_0", "has_bottle_1", "has_bottle_2", "slot_0_occupied", "slot_1_occupied", "slot_2_occupied", "slot_3_occupied",
        "slot_4_occupied", "slot_5_occupied", "flower_amount", "segment_amount", "tip", "thickness", "vertical_direction", "cracked", "ominous",
        "trial_spawner_state", "vault_state", "crafting", "natural", "tilt", "sculk_sensor_phase", "can_summon", "shrieking", "bloom", "creaking_heart_state");

    private static final Set<String> TUNE_PROPS = Set.of("delay", "mode", "note", "inverted");

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgBuild = settings.createGroup("Building");
    private final SettingGroup sgSafety = settings.createGroup("Safety");
    private final SettingGroup sgPreview = settings.createGroup("Preview");

    private final Setting<String> schematic = sgGeneral.add(new ProvidedStringSetting.Builder()
        .name("schematic").description("Schematic from .minecraft/larper-client/schematics (.litematic, .schem or .nbt). Use \"Select file...\" below to add one.")
        .defaultValue(FileChoices.NONE).supplier(() -> FileChoices.list(folder(), currentSchematic(), EXTENSIONS)).onChanged(v -> load()).build());

    private final Setting<BlockPos> origin = sgGeneral.add(new BlockPosSetting.Builder()
        .name("position").description("Where the schematic's lowest corner goes. Use the buttons below (Pick spot / Here) to place it.")
        .defaultValue(BlockPos.ORIGIN).onChanged(v -> rebuildTargets()).build());

    private final Setting<Turn> turn = sgGeneral.add(new EnumSetting.Builder<Turn>()
        .name("rotation").description("Turn the schematic around.").defaultValue(Turn.None).onChanged(v -> rebuildTargets()).build());

    private final Setting<BlockPos> chest = sgGeneral.add(new BlockPosSetting.Builder()
        .name("material-chest").description("Chest with the materials. Look at it and press \"Chest = block I look at\".").defaultValue(BlockPos.ORIGIN).build());

    private final Setting<Portion> portion = sgBuild.add(new EnumSetting.Builder<Portion>()
        .name("build-amount").description("Build the whole schematic or only part of its layers.").defaultValue(Portion.Full).onChanged(v -> rebuildTargets()).build());

    private final Setting<Integer> customPercent = sgBuild.add(new IntSetting.Builder()
        .name("custom-percent").description("Custom: how much of the layers to build.").defaultValue(50).range(1, 100).sliderRange(1, 100)
        .visible(() -> portion.get() == Portion.Custom).onChanged(v -> rebuildTargets()).build());

    private final Setting<From> from = sgBuild.add(new EnumSetting.Builder<From>()
        .name("part").description("Which part to build when not building all of it: the bottom layers or the top layers (it always builds upward inside that part).")
        .defaultValue(From.Bottom).visible(() -> portion.get() != Portion.Full).onChanged(v -> rebuildTargets()).build());

    private final Setting<Speed> speed = sgBuild.add(new EnumSetting.Builder<Speed>()
        .name("speed").description("Human: calm, smooth and humanlike. Fast: quicker turns and less waiting. VeryFast: as fast as it can go without breaking the rules (looks less human). Sets turn-speed and action-delay below.")
        .defaultValue(Speed.Human).onChanged(v -> {
            this.turnSpeed.set(switch (v) { case Human -> 8.0; case Fast -> 16.0; case VeryFast -> 30.0; });
            this.actionDelay.set(switch (v) { case Human -> 4; case Fast -> 2; case VeryFast -> 1; });
        }).build());

    private final Setting<Double> turnSpeed = sgBuild.add(new DoubleSetting.Builder()
        .name("turn-speed").description("Fastest you turn (degrees per tick). Turns speed up and slow down smoothly like a hand on a mouse; lower looks calmer.").defaultValue(8).range(3, 60).sliderRange(5, 40).build());

    private final Setting<Integer> actionDelay = sgBuild.add(new IntSetting.Builder()
        .name("action-delay").description("Ticks to wait after each placement, break or chest click (20 = 1 second).").defaultValue(4).range(0, 40).sliderRange(1, 15).build());

    private final Setting<Boolean> fixWrong = sgBuild.add(new BoolSetting.Builder()
        .name("replace-wrong-blocks").description("Break blocks that are in the way or wrong and put the right one.").defaultValue(true).build());

    private final Setting<List<Block>> supportBlocks = sgGeneral.add(new BlockListSetting.Builder()
        .name("support-blocks").description("Temporary blocks it puts down so observers, dispensers, pistons, stairs etc. can be placed facing the right way; they're broken again afterwards. The first one you have is used. Put some in the chest too.")
        .defaultValue(Blocks.COBBLESTONE, Blocks.DIRT, Blocks.NETHERRACK, Blocks.COBBLED_DEEPSLATE, Blocks.STONE)
        .filter(SchematicBuilder::canSupport).build());

    private final Setting<Boolean> humanWalk = sgBuild.add(new BoolSetting.Builder()
        .name("human-walking").description("Baritone looks where it walks, turns smoothly with a little randomness and never parkours.").defaultValue(true).build());

    private final Setting<Boolean> pauseOnMove = sgSafety.add(new BoolSetting.Builder()
        .name("pause-when-you-move").description("Press a movement key to take over: it pauses, and carries on 2 seconds after you let go.").defaultValue(true).build());

    private final Setting<Boolean> pauseOnPlayer = sgSafety.add(new BoolSetting.Builder()
        .name("pause-near-players").description("Pause while another player (not a friend) is close.").defaultValue(false).build());

    private final Setting<Integer> playerRange = sgSafety.add(new IntSetting.Builder()
        .name("player-range").description("How close counts as near (blocks).").defaultValue(24).range(4, 128).sliderRange(8, 64)
        .visible(pauseOnPlayer::get).build());

    private final Setting<Boolean> pauseOnHealth = sgSafety.add(new BoolSetting.Builder()
        .name("pause-low-health").description("Pause while your health is low (eat or heal, it resumes after).").defaultValue(true).build());

    private final Setting<Double> minHealth = sgSafety.add(new DoubleSetting.Builder()
        .name("min-health").description("Pause below this many hearts.").defaultValue(6).range(1, 10).sliderRange(1, 10)
        .visible(pauseOnHealth::get).build());

    private final Setting<Boolean> preview = sgPreview.add(new BoolSetting.Builder()
        .name("preview").description("Show the schematic where it will be built, like Litematica.").defaultValue(true).build());

    private final Setting<SettingColor> missingColor = sgPreview.add(new ColorSetting.Builder()
        .name("missing-color").description("Blocks still to place.").defaultValue(new SettingColor(80, 170, 255, 40)).visible(preview::get).build());

    private final Setting<SettingColor> wrongColor = sgPreview.add(new ColorSetting.Builder()
        .name("wrong-color").description("Blocks that are wrong or in the way.").defaultValue(new SettingColor(255, 60, 60, 60)).visible(preview::get).build());

    private final Setting<SettingColor> supportColor = sgPreview.add(new ColorSetting.Builder()
        .name("support-color").description("Temporary support blocks.").defaultValue(new SettingColor(255, 150, 40, 70)).visible(preview::get).build());

    private final Setting<Integer> previewRange = sgPreview.add(new IntSetting.Builder()
        .name("preview-range").description("Only show blocks within this many blocks of you.").defaultValue(32).range(4, 96).sliderRange(8, 64).visible(preview::get).build());

    private final Setting<Integer> maxBlocks = sgPreview.add(new IntSetting.Builder()
        .name("max-blocks").description("Most preview blocks drawn at once (lower it on a low-end PC).").defaultValue(6000).range(100, 50000).sliderRange(500, 20000).visible(preview::get).build());

    private final Setting<Boolean> hud = sgPreview.add(new BoolSetting.Builder()
        .name("progress").description("Show progress, what's next and missing materials at the top of the screen.").defaultValue(true).build());

    private enum Mode { IDLE, PICKING, BUILDING, RESTOCK, DONE }

    private record Target(BlockPos pos, BlockState state, Item item) {}

    private record Plan(BlockPos click, Direction side, Vec3d hit) {}

    private Schematic loaded;
    private final List<Target> targets = new ArrayList<>();
    private final Map<Long, Target> byPos = new HashMap<>();
    private final Set<Long> skipped = new HashSet<>();
    private final Set<Long> deferred = new HashSet<>();
    private final Map<Long, Integer> attempts = new HashMap<>();
    private final Set<BlockPos> supports = new HashSet<>();
    private final Set<Block> usedSupports = new HashSet<>();
    private final Deque<BlockPos> supportsToPlace = new ArrayDeque<>();
    private final Deque<BlockPos> supportsToBreak = new ArrayDeque<>();
    private final List<Target> previewList = new ArrayList<>();
    private final Map<Item, Integer> missingItems = new LinkedHashMap<>();
    private final Random rng = new Random();
    private final com.example.addon.util.HumanAim aim = new com.example.addon.util.HumanAim();

    private Mode mode = Mode.IDLE;
    private Target current;
    private BlockPos breaking;
    private int wait, previewTicks, placedCount, goalTicks, restockStep, restockTicks;
    private boolean walking, centering;
    private BlockPos walkGoal;
    private final Set<Long> unreachable = new HashSet<>();
    private int stallTicks;
    private Target stallTarget;
    private Map<String, Object> savedBaritone;
    private String pauseReason;
    private long startedAt;
    private boolean crouch;
    private BlockPos breakingSupport;
    private int supportTicks;
    private BlockPos towerBase, towerTo, jumpFrom, digAt;
    private final Set<BlockPos> pretendAir = new HashSet<>();
    private String status = "";

    public SchematicBuilder() {
        super(AddonTemplate.AUTOMATION, "schematic-builder", "Builds a schematic like a real player: takes materials from your chest, places every block with real rotations and uses temporary supports when needed.");
    }

    private Path folder() {
        Path dir = mc.runDirectory.toPath().resolve("larper-client").resolve("schematics").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (Exception ignored) {
        }
        return dir;
    }

    private String currentSchematic() {
        return schematic == null ? null : schematic.get();
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WVerticalList list = theme.verticalList();
        list.add(FilePicker.buttons(theme, "Choose a schematic", folder(), schematic, EXTENSIONS));
        WHorizontalList place = list.add(theme.horizontalList()).widget();
        place.add(theme.button("Pick spot (look + left-click)")).widget().action = () -> {
            if (!isActive()) toggle();
            mode = Mode.PICKING;
            ChatUtils.info("Schematic Builder: look where it should go and left-click to place it.");
        };
        place.add(theme.button("Here (where I stand)")).widget().action = () -> {
            if (mc.player != null) origin.set(mc.player.getBlockPos());
        };
        WHorizontalList nudge = list.add(theme.horizontalList()).widget();
        nudge.add(theme.button("X-")).widget().action = () -> origin.set(origin.get().add(-1, 0, 0));
        nudge.add(theme.button("X+")).widget().action = () -> origin.set(origin.get().add(1, 0, 0));
        nudge.add(theme.button("Y-")).widget().action = () -> origin.set(origin.get().add(0, -1, 0));
        nudge.add(theme.button("Y+")).widget().action = () -> origin.set(origin.get().add(0, 1, 0));
        nudge.add(theme.button("Z-")).widget().action = () -> origin.set(origin.get().add(0, 0, -1));
        nudge.add(theme.button("Z+")).widget().action = () -> origin.set(origin.get().add(0, 0, 1));
        nudge.add(theme.button("Rotate 90")).widget().action = () -> turn.set(Turn.values()[(turn.get().ordinal() + 1) % 4]);
        WHorizontalList misc = list.add(theme.horizontalList()).widget();
        misc.add(theme.button("Chest = block I look at")).widget().action = () -> {
            if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                chest.set(hit.getBlockPos());
                ChatUtils.info("Schematic Builder: material chest set to %s.", hit.getBlockPos().toShortString());
            } else ChatUtils.warning("Look at the chest first.");
        };
        misc.add(theme.button("Support = block in my hand")).widget().action = () -> {
            if (mc.player == null || !(mc.player.getMainHandStack().getItem() instanceof BlockItem bi)) {
                ChatUtils.warning("Schematic Builder: hold the block you want as support first.");
                return;
            }
            Block b = bi.getBlock();
            if (!canSupport(b)) {
                ChatUtils.warning("Schematic Builder: %s can't be a support (it has to be a plain full block that breaks fast and doesn't fall).", b.getName().getString());
                return;
            }
            List<Block> order = new ArrayList<>(supportBlocks.get());
            order.remove(b);
            order.addFirst(b);
            supportBlocks.set(order);
            ChatUtils.info("Schematic Builder: supports will be %s (used first).", b.getName().getString());
        };
        misc.add(theme.button("Materials list")).widget().action = this::printMaterials;
        WHorizontalList run = list.add(theme.horizontalList()).widget();
        run.add(theme.button("Start building")).widget().action = this::start;
        run.add(theme.button("Stop")).widget().action = () -> {
            if (mode == Mode.BUILDING || mode == Mode.RESTOCK) stopBuild("stopped.");
        };
        return list;
    }

    @Override
    public void onActivate() {
        mode = Mode.IDLE;
        load();
    }

    @Override
    public void onDeactivate() {
        if (mode == Mode.BUILDING || mode == Mode.RESTOCK) stopBuild(null);
        mode = Mode.IDLE;
        releaseKeys();
    }

    private void load() {
        loaded = null;
        targets.clear();
        byPos.clear();
        if (!isActive() || !FileChoices.isFile(schematic.get())) return;
        Path file = folder().resolve(schematic.get()).normalize();
        if (!file.startsWith(folder()) || !Files.isRegularFile(file)) {
            ChatUtils.warning("Schematic %s not found.", schematic.get());
            return;
        }
        try {
            loaded = Schematic.load(file);
            ChatUtils.info("Schematic Builder: loaded %s (%d x %d x %d, %d blocks).", loaded.name, loaded.sizeX, loaded.sizeY, loaded.sizeZ, loaded.blocks.size());
        } catch (Exception e) {
            ChatUtils.warning("Couldn't load %s: %s", schematic.get(), e.getMessage());
        }
        rebuildTargets();
    }

    private Schematic shaped() {
        if (loaded == null) return null;
        return loaded.rotated(switch (turn.get()) {
            case None -> BlockRotation.NONE;
            case Right90 -> BlockRotation.CLOCKWISE_90;
            case Turn180 -> BlockRotation.CLOCKWISE_180;
            case Left90 -> BlockRotation.COUNTERCLOCKWISE_90;
        });
    }

    private void rebuildTargets() {
        targets.clear();
        byPos.clear();
        Schematic s = shaped();
        if (s == null) return;
        double frac = switch (portion.get()) {
            case Full -> 1;
            case ThreeQuarters -> 0.75;
            case Half -> 0.5;
            case Quarter -> 0.25;
            case Custom -> customPercent.get() / 100.0;
        };
        int layers = Math.max(1, (int) Math.ceil(s.sizeY * frac));
        int y0 = from.get() == From.Bottom || portion.get() == Portion.Full ? 0 : s.sizeY - layers, y1 = y0 + layers - 1;
        BlockPos o = origin.get();
        for (Schematic.Block3 b : s.blocks) {
            int y = b.pos().getY();
            if (y < y0 || y > y1) continue;
            BlockState st = b.state();
            if (st.contains(Properties.DOUBLE_BLOCK_HALF) && st.get(Properties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) continue;
            if (st.contains(Properties.BED_PART) && st.get(Properties.BED_PART) == BedPart.HEAD) continue;
            Item item = st.getBlock().asItem();
            if (item == Items.AIR || !st.getFluidState().isEmpty() && !st.contains(Properties.WATERLOGGED)) continue;
            Target t = new Target(o.add(b.pos()), st, item);
            targets.add(t);
            byPos.put(t.pos().asLong(), t);
        }
        targets.sort(Comparator.comparingInt((Target t) -> t.pos().getY()).thenComparingInt(t -> t.pos().getX()).thenComparingInt(t -> t.pos().getZ()));
    }

    private void printMaterials() {
        if (targets.isEmpty()) {
            ChatUtils.warning("Schematic Builder: no schematic loaded (or nothing to build in the chosen part).");
            return;
        }
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Target t : targets) if (!done(t)) need.merge(t.item(), 1, Integer::sum);
        ChatUtils.info("Schematic Builder needs (still to place):");
        need.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
            .forEach(e -> ChatUtils.info("  %s x%d (you have %d)", e.getKey().getName().getString(), e.getValue(), count(e.getKey())));
    }

    private void start() {
        if (!isActive()) toggle();
        if (targets.isEmpty()) {
            ChatUtils.warning("Schematic Builder: pick a schematic and place it first.");
            return;
        }
        if (!BaritoneBridge.available()) ChatUtils.warning("Baritone didn't load: you'll have to walk close to the blocks yourself (it still builds and restocks when in reach).");
        skipped.clear();
        deferred.clear();
        unreachable.clear();
        attempts.clear();
        placedCount = 0;
        current = null;
        breaking = null;
        mode = Mode.BUILDING;
        pauseReason = null;
        crouch = false;
        towerBase = towerTo = jumpFrom = digAt = null;
        startedAt = System.currentTimeMillis();
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("allowBreak", false);
        s.put("allowPlace", false);
        s.put("allowInventory", false);
        s.put("allowParkour", false);
        s.put("allowParkourPlace", false);
        if (humanWalk.get()) s.putAll(BaritoneBridge.humanWalking());
        savedBaritone = BaritoneBridge.apply(s);
        ChatUtils.info("Schematic Builder: building %d blocks.", (int) targets.stream().filter(t -> !done(t)).count());
    }

    private void stopBuild(String why) {
        BaritoneBridge.stop();
        BaritoneBridge.restore(savedBaritone);
        savedBaritone = null;
        walking = false;
        releaseKeys();
        if (mc.interactionManager != null) mc.interactionManager.cancelBlockBreaking();
        if (mode == Mode.RESTOCK && mc.player != null && mc.player.currentScreenHandler instanceof GenericContainerScreenHandler) mc.player.closeHandledScreen();
        mode = Mode.IDLE;
        if (why != null) ChatUtils.info("Schematic Builder: %s", why);
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        mc.options.sneakKey.setPressed(false);
        mc.options.forwardKey.setPressed(false);
        mc.options.backKey.setPressed(false);
        mc.options.leftKey.setPressed(false);
        mc.options.rightKey.setPressed(false);
        mc.options.jumpKey.setPressed(false);
    }

    private static double eyeHeight(boolean crouching) {
        return crouching ? 1.27 : 1.62;
    }

    private static boolean worksAt(Vec3d feet, java.util.function.Predicate<Vec3d> works) {
        return works.test(feet.add(0, eyeHeight(false), 0)) || works.test(feet.add(0, eyeHeight(true), 0));
    }

    private boolean crouchFor(BlockPos target, java.util.function.Predicate<Vec3d> works) {
        if (crouch || playerBox().intersects(new Box(target)) || !mc.player.isOnGround() || !works.test(mc.player.getEntityPos().add(0, eyeHeight(true), 0))) return false;
        crouch = true;
        mc.options.sneakKey.setPressed(true);
        status = "crouching for the right angle";
        return true;
    }

    private boolean unCrouch() {
        if (!crouch || !mc.player.isSneaking()) return false;
        crouch = false;
        mc.options.sneakKey.setPressed(false);
        return true;
    }

    private boolean startTower(BlockPos[] spot) {
        if (spot == null) return false;
        towerBase = spot[0];
        towerTo = spot[1];
        jumpFrom = null;
        status = "pillaring up to reach it";
        return true;
    }

    private BlockPos[] towerSpot(BlockPos target, java.util.function.Predicate<Vec3d> works, BlockPos... avoid) {
        BlockPos me = mc.player.getBlockPos();
        BlockPos[] best = null;
        double bestScore = Double.MAX_VALUE;
        for (int dx = -3; dx <= 3; dx++) for (int dz = -3; dz <= 3; dz++) {
            BlockPos ground = null;
            for (int y = target.getY() + 2; y >= target.getY() - 6; y--) {
                BlockPos q = new BlockPos(target.getX() + dx, y, target.getZ() + dz);
                if (solid(q)) {
                    ground = q;
                    break;
                }
            }
            if (ground == null || unreachable.contains(ground.up().asLong())) continue;
            BlockPos base = ground.up();
            if (!free(base, target, avoid) || !free(base.up(), target, avoid)) continue;
            for (int h = 1; h <= 5; h++) {
                if (!free(base.up(h + 1), target, avoid)) break;
                double score = h * 8 + base.getSquaredDistance(me) / 4;
                if (score >= bestScore) break;
                if (!worksAt(Vec3d.ofBottomCenter(base.up(h)), works)) continue;
                bestScore = score;
                best = new BlockPos[] {base, base.up(h)};
                break;
            }
        }
        return best;
    }

    private boolean free(BlockPos c, BlockPos target, BlockPos... avoid) {
        if (solid(c) || !mc.world.getBlockState(c).getFluidState().isEmpty() || byPos.containsKey(c.asLong()) || c.equals(target)) return false;
        for (BlockPos a : avoid) if (c.equals(a)) return false;
        return true;
    }

    private void towerStep() {
        BlockPos feet = mc.player.getBlockPos();
        boolean onColumn = feet.getX() == towerTo.getX() && feet.getZ() == towerTo.getZ();
        if (jumpFrom == null && mc.player.isOnGround() && onColumn && feet.getY() >= towerTo.getY()) {
            towerTo = null;
            mc.options.jumpKey.setPressed(false);
            return;
        }
        if (!onColumn) {
            if (unreachable.contains(towerBase.asLong())) {
                towerTo = null;
                return;
            }
            if (jumpFrom == null && mc.player.isOnGround() && !walkTo(towerBase)) towerTo = null;
            return;
        }
        Item item = supportItem();
        if (item == null) {
            towerTo = null;
            mode = Mode.RESTOCK;
            restockStep = 0;
            return;
        }
        status = "pillaring up to reach it";
        if (jumpFrom == null) {
            if (!mc.player.isOnGround() || !select(item)) return;
            if (stepToCentre(e -> true)) return;
            if (!turnTowards(Vec3d.ofCenter(feet.down()).add(0, 0.5, 0))) return;
            jumpFrom = feet;
            mc.options.jumpKey.setPressed(true);
            return;
        }
        mc.options.jumpKey.setPressed(false);
        if (solid(jumpFrom)) {
            supports.add(jumpFrom);
            usedSupports.add(mc.world.getBlockState(jumpFrom).getBlock());
            jumpFrom = null;
            stallTicks = 0;
            return;
        }
        if (mc.player.isOnGround()) {
            jumpFrom = null;
            return;
        }
        if (mc.player.getY() < jumpFrom.getY() + 1.02) return;
        HitResult r = mc.player.raycast(mc.player.getBlockInteractionRange(), 1, false);
        if (!(r instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(jumpFrom.down()) || hit.getSide() != Direction.UP) return;
        if (!mc.player.getMainHandStack().isOf(item)) return;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean startDig(BlockPos spot) {
        if (spot == null) return false;
        digAt = spot;
        status = "digging a spot to stand in";
        return true;
    }

    private BlockPos digSpot(BlockPos target, java.util.function.Predicate<Vec3d> works, BlockPos... avoid) {
        BlockPos me = mc.player.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(target.add(-3, -2, -3), target.add(3, 0, 3))) {
            Target t = byPos.get(p.asLong());
            if (t == null || !done(t) || !solid(p.down()) || !free(p.up(), target, avoid) || !free(p.up(2), target, avoid)) continue;
            if (mc.world.getBlockState(p).getHardness(mc.world, p) < 0 || new Box(p).union(new Box(p.up())).intersects(new Box(target))) continue;
            double score = p.getSquaredDistance(me);
            if (score >= bestScore) continue;
            pretendAir.add(p.toImmutable());
            boolean ok = worksAt(Vec3d.ofBottomCenter(p), works);
            pretendAir.clear();
            if (!ok) continue;
            bestScore = score;
            best = p.toImmutable();
        }
        return best;
    }

    private boolean digStep() {
        if (solid(digAt)) {
            if (!canBreakFrom(mc.player.getEyePos(), digAt)) {
                if (!walkTo(standSpot(digAt, e -> canBreakFrom(e, digAt), digAt))) digAt = null;
                return true;
            }
            status = "digging a spot to stand in";
            breaking = digAt;
            return true;
        }
        if (!mc.player.getBlockPos().equals(digAt)) {
            if (!walkTo(digAt)) digAt = null;
            return true;
        }
        return false;
    }

    private boolean stepToCentre(java.util.function.Predicate<Vec3d> works) {
        Vec3d c = Vec3d.ofBottomCenter(mc.player.getBlockPos());
        double dx = c.x - mc.player.getX(), dz = c.z - mc.player.getZ();
        if (dx * dx + dz * dz < 0.01 || !mc.player.isOnGround() || !worksAt(c, works)) return false;
        double yaw = Math.toRadians(mc.player.getYaw());
        double fwd = -dx * Math.sin(yaw) + dz * Math.cos(yaw), left = dx * Math.cos(yaw) + dz * Math.sin(yaw);
        mc.options.forwardKey.setPressed(fwd > 0.04);
        mc.options.backKey.setPressed(fwd < -0.04);
        mc.options.leftKey.setPressed(left > 0.04);
        mc.options.rightKey.setPressed(left < -0.04);
        centering = true;
        status = "stepping into place";
        return true;
    }

    private boolean done(Target t) {
        BlockState got = mc.world.getBlockState(t.pos());
        return matches(got, t.state()) && tuned(got, t.state());
    }

    private static boolean tuned(BlockState got, BlockState want) {
        for (Property<?> p : want.getProperties()) {
            if (TUNE_PROPS.contains(p.getName()) && !got.get(p).equals(want.get(p))) return false;
        }
        return true;
    }

    private static boolean matches(BlockState got, BlockState want) {
        if (got == null || got.getBlock() != want.getBlock()) return false;
        for (Property<?> p : want.getProperties()) {
            if (FREE_PROPS.contains(p.getName())) continue;
            if (!got.get(p).equals(want.get(p))) return false;
        }
        return true;
    }

    private int count(Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getStack(i);
            if (s.isOf(item)) n += s.getCount();
        }
        return n;
    }

    private boolean solid(BlockPos p) {
        BlockState s = mc.world.getBlockState(p);
        return !s.isAir() && !s.isReplaceable() && s.getFluidState().isEmpty() && !s.getCollisionShape(mc.world, p).isEmpty();
    }

    private boolean isSupportBlock(BlockState s) {
        return supportBlocks.get().contains(s.getBlock()) || usedSupports.contains(s.getBlock());
    }

    private static boolean canSupport(Block b) {
        float hardness = b.getHardness();
        return b.getDefaultState().isFullCube(net.minecraft.world.EmptyBlockView.INSTANCE, BlockPos.ORIGIN) && !(b instanceof FallingBlock)
            && !(b instanceof net.minecraft.block.BlockEntityProvider) && hardness >= 0 && hardness <= 3 && b.asItem() != Items.AIR;
    }

    private Item supportItem() {
        for (Block b : supportBlocks.get()) if (count(b.asItem()) > 0) return b.asItem();
        return null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (++previewTicks >= 10) {
            previewTicks = 0;
            refreshPreview();
        }
        if (mode == Mode.PICKING) {
            if (mc.crosshairTarget instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) origin.set(hit.getBlockPos().up());
            return;
        }
        if (mode != Mode.BUILDING && mode != Mode.RESTOCK) return;
        if (mode == Mode.BUILDING && mc.currentScreen != null) return;
        String reason = Automation.pauseReason(pauseOnMove.get(), pauseOnPlayer.get(), playerRange.get(), pauseOnHealth.get(), minHealth.get());
        if (reason != null && pauseReason == null) {
            BaritoneBridge.stop();
            walking = false;
            centering = false;
            crouch = false;
            jumpFrom = null;
            releaseKeys();
            mc.interactionManager.cancelBlockBreaking();
            if (!reason.startsWith("you're")) ChatUtils.warning("Schematic Builder paused: %s", reason);
        } else if (reason == null && pauseReason != null && !pauseReason.startsWith("you're")) ChatUtils.info("Schematic Builder resumed.");
        pauseReason = reason;
        if (reason != null) return;
        if (walking) {
            if (BaritoneBridge.busy() && ++goalTicks < 600) return;
            walking = false;
            if (walkGoal != null && !mc.player.getBlockPos().equals(walkGoal)) unreachable.add(walkGoal.asLong());
        }
        if (wait > 0) {
            wait--;
            return;
        }
        if (mode == Mode.RESTOCK) {
            restock();
            return;
        }
        build();
    }

    private void build() {
        if (centering) {
            centering = false;
            releaseKeys();
            mc.options.sneakKey.setPressed(crouch);
        }
        if (current != stallTarget) {
            stallTarget = current;
            stallTicks = 0;
        } else if (current != null && ++stallTicks > 200) {
            attempts.merge(current.pos().asLong(), 1, Integer::sum);
            if (attempts.get(current.pos().asLong()) > 2) skip(current, "got stuck placing it");
            else deferred.add(current.pos().asLong());
            supportsToPlace.clear();
            current = null;
            stallTarget = null;
            towerTo = jumpFrom = digAt = null;
            crouch = false;
            releaseKeys();
            return;
        }
        if (breaking != null) {
            if (mc.world.getBlockState(breaking).isAir()) {
                breaking = null;
                mc.interactionManager.cancelBlockBreaking();
                wait = actionDelay.get();
            } else breakStep(breaking);
            return;
        }
        if (towerTo != null) {
            towerStep();
            return;
        }
        if (!supportsToBreak.isEmpty()) {
            BlockPos s = supportsToBreak.peek();
            if (!isSupportBlock(mc.world.getBlockState(s)) || mc.world.getBlockState(s.up()).getBlock() instanceof FallingBlock) {
                supportsToBreak.poll();
                supports.remove(s);
                return;
            }
            if (!s.equals(breakingSupport)) {
                breakingSupport = s;
                supportTicks = 0;
            }
            if (!canBreakFrom(mc.player.getEyePos(), s)) {
                java.util.function.Predicate<Vec3d> reach = e -> canBreakFrom(e, s);
                if (++supportTicks > 600 || !stepToCentre(reach) && !walkTo(standSpot(s, reach)) && !startTower(towerSpot(s, reach, s))) {
                    supportsToBreak.poll();
                    ChatUtils.warning("Schematic Builder: couldn't reach the support at %s to remove it.", s.toShortString());
                }
                return;
            }
            status = "removing a support block";
            breaking = s;
            supportsToBreak.poll();
            supports.remove(s);
            return;
        }
        if (current == null || done(current)) {
            if (current != null && done(current)) {
                crouch = false;
                digAt = null;
                mc.options.sneakKey.setPressed(false);
                placedCount++;
                unreachable.clear();
                attempts.remove(current.pos().asLong());
                supportsToBreak.addAll(supportsFor(current));
                current = null;
                return;
            }
            current = pick();
            if (current == null) {
                if (!supportsToBreak.isEmpty()) return;
                finish();
                return;
            }
        }
        Target t = current;
        BlockState here = mc.world.getBlockState(t.pos());
        if (!here.isAir() && !here.isReplaceable() && here.getBlock() != t.state().getBlock()) {
            if (!fixWrong.get()) {
                skip(t, "something else is in the way");
                return;
            }
            if (!canBreakFrom(mc.player.getEyePos(), t.pos())) {
                if (!walkTo(standSpot(t.pos(), e -> canBreakFrom(e, t.pos())))) skip(t, "can't reach the wrong block there");
                return;
            }
            status = "breaking a wrong block";
            breaking = t.pos();
            return;
        }
        if (matches(here, t.state())) {
            tune(t);
            return;
        }
        if (!supportsToPlace.isEmpty()) {
            placeSupport(supportsToPlace.peek());
            return;
        }
        if (count(t.item()) == 0) {
            if (chest.get().equals(BlockPos.ORIGIN)) {
                skip(t, "no " + t.item().getName().getString() + " and no material chest set");
                return;
            }
            mode = Mode.RESTOCK;
            restockStep = 0;
            return;
        }
        ItemStack stack = new ItemStack(t.item());
        java.util.function.Predicate<Vec3d> works = e -> plan(t.pos(), t.state(), stack, Set.of(), e) != null;
        if (digAt != null && digStep()) return;
        Plan plan = playerBox().intersects(new Box(t.pos())) ? null : plan(t.pos(), t.state(), stack, Set.of(), mc.player.getEyePos());
        if (plan != null) {
            status = "placing " + t.state().getBlock().getName().getString();
            execute(t.item(), t.pos(), t.state(), plan);
            return;
        }
        if (unCrouch() || crouchFor(t.pos(), works) || stepToCentre(works)) return;
        if (walkTo(bestSpot(t, works))) return;
        if (startTower(towerSpot(t.pos(), works))) return;
        if (startDig(digSpot(t.pos(), works))) return;
        List<BlockPos> chain = supportChain(t, stack);
        if (chain == null) {
            if (attempts.merge(t.pos().asLong(), 1, Integer::sum) > 2) skip(t, "no way to place it with the right rotation");
            else {
                deferred.add(t.pos().asLong());
                current = null;
            }
            return;
        }
        if (supportItem() == null) {
            if (chest.get().equals(BlockPos.ORIGIN)) {
                skip(t, "it needs a support block and you have none");
                return;
            }
            mode = Mode.RESTOCK;
            restockStep = 0;
            return;
        }
        supportsToPlace.addAll(chain);
        status = "placing " + chain.size() + " support block(s) for " + t.state().getBlock().getName().getString();
    }

    private Target pick() {
        int lowest = Integer.MAX_VALUE;
        for (Target t : targets) {
            if (skipped.contains(t.pos().asLong()) || done(t)) continue;
            if (t.pos().getY() < lowest) lowest = t.pos().getY();
        }
        if (lowest == Integer.MAX_VALUE) return null;
        boolean others = false;
        for (Target t : targets) {
            if (t.pos().getY() == lowest && !skipped.contains(t.pos().asLong()) && !deferred.contains(t.pos().asLong()) && !done(t)) {
                others = true;
                break;
            }
        }
        if (!others) deferred.clear();
        Vec3d eye = mc.player.getEyePos();
        double reach = mc.player.getBlockInteractionRange() + 0.5;
        Target near = null;
        double nearScore = Double.MAX_VALUE;
        Map<Target, Double> order = new HashMap<>();
        for (Target t : targets) {
            if (t.pos().getY() != lowest || skipped.contains(t.pos().asLong()) || deferred.contains(t.pos().asLong()) || done(t)) continue;
            double d = eye.distanceTo(Vec3d.ofCenter(t.pos()));
            double score = d * d + attempts.getOrDefault(t.pos().asLong(), 0) * 400;
            if (score < nearScore) {
                nearScore = score;
                near = t;
            }
            if (d > reach || count(t.item()) == 0 || playerBox().intersects(new Box(t.pos()))) continue;
            float[] a = anglesTo(eye, Vec3d.ofCenter(t.pos()));
            double turn = Math.hypot(MathHelper.wrapDegrees(a[0] - mc.player.getYaw()), a[1] - mc.player.getPitch());
            order.put(t, turn / 10 - d);
        }
        List<Target> reachable = new ArrayList<>(order.keySet());
        reachable.sort(Comparator.comparingDouble(order::get));
        for (Target t : reachable) if (plan(t.pos(), t.state(), new ItemStack(t.item()), Set.of(), eye) != null) return t;
        return near;
    }

    private void skip(Target t, String why) {
        skipped.add(t.pos().asLong());
        ChatUtils.warning("Schematic Builder: skipped %s at %s (%s).", t.state().getBlock().getName().getString(), t.pos().toShortString(), why);
        current = null;
    }

    private void finish() {
        String summary = skipped.isEmpty() ? "done! Everything is built." : "done, but " + skipped.size() + " block(s) were skipped (see chat).";
        stopBuild(summary);
        if (targets.stream().anyMatch(t -> t.state().getBlock() instanceof net.minecraft.block.LeverBlock)) ChatUtils.info("Schematic Builder: levers are left off - flip them yourself when you're ready.");
        mode = Mode.DONE;
    }

    private List<BlockPos> supportsFor(Target t) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos s : supports) if (s.getManhattanDistance(t.pos()) <= 12 && !supportsToPlace.contains(s)) out.add(s);
        out.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()));
        return out;
    }

    private void placeSupport(BlockPos s) {
        if (solid(s)) {
            supportsToPlace.poll();
            if (isSupportBlock(mc.world.getBlockState(s))) {
                supports.add(s);
                usedSupports.add(mc.world.getBlockState(s).getBlock());
            }
            crouch = false;
            mc.options.sneakKey.setPressed(false);
            stallTicks = 0;
            wait = actionDelay.get();
            return;
        }
        Item item = supportItem();
        if (item == null) {
            mode = Mode.RESTOCK;
            restockStep = 0;
            return;
        }
        BlockState want = ((BlockItem) item).getBlock().getDefaultState();
        ItemStack stack = new ItemStack(item);
        Plan p = playerBox().intersects(new Box(s)) ? null : plan(s, want, stack, Set.of(), mc.player.getEyePos());
        if (p != null) {
            execute(item, s, null, p);
            return;
        }
        java.util.function.Predicate<Vec3d> works = e -> plan(s, want, stack, Set.of(), e) != null;
        if (unCrouch() || crouchFor(s, works) || stepToCentre(works)) return;
        if (walkTo(standSpot(s, works))) return;
        if (startTower(towerSpot(s, works, s))) return;
        supportsToPlace.clear();
        if (current != null) skip(current, "couldn't place a support block");
    }

    private void execute(Item item, BlockPos at, BlockState want, Plan plan) {
        if (!turnTowards(plan.hit())) return;
        HitResult r = mc.player.raycast(mc.player.getBlockInteractionRange(), 1, false);
        if (!(r instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(plan.click()) || hit.getSide() != plan.side()) {
            nudgeAim(plan.hit());
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (want != null) {
            BlockState got = simulate(at, hit.getBlockPos(), hit.getSide(), hit.getPos(), stack, mc.player.getYaw(), mc.player.getPitch(), want);
            if (!matches(got, want)) {
                nudgeAim(plan.hit());
                return;
            }
        }
        if (!select(item)) return;
        boolean sneak = BlockUtils.isClickable(mc.world.getBlockState(hit.getBlockPos()).getBlock()) && !hit.getBlockPos().equals(at);
        mc.options.sneakKey.setPressed(sneak || crouch);
        if ((sneak || crouch) && !mc.player.isSneaking()) return;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        mc.options.sneakKey.setPressed(crouch);
        wait = actionDelay.get() + jitter(3);
    }

    private boolean select(Item item) {
        if (mc.player.getMainHandStack().isOf(item)) return true;
        FindItemResult hot = InvUtils.findInHotbar(item);
        if (hot.found()) {
            InvUtils.swap(hot.slot(), false);
            wait = 2;
            return false;
        }
        FindItemResult any = InvUtils.find(item);
        if (!any.found()) return false;
        int slot = mc.player.getInventory().getSelectedSlot();
        InvUtils.move().from(any.slot()).toHotbar(slot);
        wait = actionDelay.get();
        return false;
    }

    private Plan plan(BlockPos t, BlockState want, ItemStack stack, Set<BlockPos> pretend, Vec3d eye) {
        double reach = mc.player.getBlockInteractionRange() - 0.25;
        Plan best = null;
        double bestDist = Double.MAX_VALUE;
        List<BlockPos> clicks = new ArrayList<>();
        for (Direction d : Direction.values()) clicks.add(t.offset(d));
        if (mc.world.getBlockState(t).getBlock() == want.getBlock()) clicks.add(t);
        for (BlockPos n : clicks) {
            boolean self = n.equals(t);
            boolean fake = pretend.contains(n);
            if (!self && !fake && !solid(n)) continue;
            for (Direction side : Direction.values()) {
                if (!self && !n.offset(side).equals(t)) continue;
                Vec3d normal = Vec3d.of(side.getVector());
                Vec3d center = Vec3d.ofCenter(n).add(normal.multiply(0.5));
                if (eye.subtract(center).dotProduct(normal) <= 0.05) continue;
                for (double a : new double[] {0, -0.3, 0.3, -0.45, 0.45}) for (double b : new double[] {0, -0.3, 0.3, -0.45, 0.45}) {
                    Vec3d hit = center.add(faceAxis(side, 0).multiply(a)).add(faceAxis(side, 1).multiply(b));
                    double dist = eye.distanceTo(hit);
                    if (dist > reach) continue;
                    if (!visible(eye, hit, n, side, fake)) continue;
                    float[] rot = anglesTo(eye, hit);
                    BlockState got = simulate(t, n, side, hit, stack, rot[0], rot[1], want);
                    if (!matches(got, want)) continue;
                    double score = dist + Math.abs(MathHelper.wrapDegrees(rot[0] - mc.player.getYaw())) / 90 + (a == 0 && b == 0 ? 0 : 0.2) + (Math.abs(a) > 0.4 || Math.abs(b) > 0.4 ? 0.3 : 0);
                    if (score < bestDist && robust(t, n, side, hit, stack, rot, want)) {
                        bestDist = score;
                        best = new Plan(n, side, hit);
                    }
                }
            }
        }
        return best;
    }

    private boolean robust(BlockPos t, BlockPos n, Direction side, Vec3d hit, ItemStack stack, float[] rot, BlockState want) {
        for (float[] d : new float[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
            if (!matches(simulate(t, n, side, hit, stack, rot[0] + d[0], MathHelper.clamp(rot[1] + d[1], -90, 90), want), want)) return false;
        }
        for (int axis = 0; axis < 2; axis++) for (double off : new double[] {0.15, -0.15}) {
            if (!matches(simulate(t, n, side, hit.add(faceAxis(side, axis).multiply(off)), stack, rot[0], rot[1], want), want)) return false;
        }
        return true;
    }

    private static Vec3d faceAxis(Direction side, int which) {
        return switch (side.getAxis()) {
            case X -> which == 0 ? new Vec3d(0, 1, 0) : new Vec3d(0, 0, 1);
            case Y -> which == 0 ? new Vec3d(1, 0, 0) : new Vec3d(0, 0, 1);
            case Z -> which == 0 ? new Vec3d(1, 0, 0) : new Vec3d(0, 1, 0);
        };
    }

    private boolean visible(Vec3d eye, Vec3d hit, BlockPos n, Direction side, boolean fake) {
        Vec3d dir = hit.subtract(eye).normalize();
        if (fake) return ray(eye, hit.subtract(dir.multiply(0.02))).getType() == HitResult.Type.MISS;
        BlockHitResult r = ray(eye, hit.add(dir.multiply(0.05)));
        return r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(n) && r.getSide() == side;
    }

    private BlockHitResult ray(Vec3d from, Vec3d to) {
        Vec3d dir = to.subtract(from).normalize();
        for (int i = 0; ; i++) {
            BlockHitResult r = mc.world.raycast(new RaycastContext(from, to, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
            if (i == 4 || r.getType() != HitResult.Type.BLOCK || !pretendAir.contains(r.getBlockPos())) return r;
            from = r.getPos().add(dir.multiply(0.01));
        }
    }

    private static float[] anglesTo(Vec3d eye, Vec3d p) {
        Vec3d d = p.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) MathHelper.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -90, 90);
        return new float[] {yaw, pitch};
    }

    private BlockState simulate(BlockPos t, BlockPos n, Direction side, Vec3d hit, ItemStack stack, float yaw, float pitch, BlockState want) {
        if (!(stack.getItem() instanceof BlockItem bi)) return null;
        float oy = mc.player.getYaw(), op = mc.player.getPitch();
        mc.player.setYaw(yaw);
        mc.player.setPitch(pitch);
        try {
            ItemPlacementContext ctx = new ItemPlacementContext(mc.player, Hand.MAIN_HAND, stack, new BlockHitResult(hit, side, n, false)) {
                @Override
                public BlockPos getBlockPos() {
                    return t;
                }
            };
            BlockState there = mc.world.getBlockState(t);
            if (!there.isAir() && !there.canReplace(ctx)) return null;
            BlockState r = bi.getBlock().getPlacementState(ctx);
            if (want != null && (r == null || r.getBlock() != want.getBlock()) && want.getBlock() != bi.getBlock() && side.getAxis().isHorizontal()) {
                r = want.getBlock().getPlacementState(ctx);
            }
            return r;
        } catch (Exception e) {
            return null;
        } finally {
            mc.player.setYaw(oy);
            mc.player.setPitch(op);
        }
    }

    private List<BlockPos> supportChain(Target t, ItemStack stack) {
        List<BlockPos> bestChain = null;
        for (Direction d : Direction.values()) {
            BlockPos s = t.pos().offset(d);
            if (solid(s) || byPos.containsKey(s.asLong())) continue;
            java.util.function.Predicate<Vec3d> works = e -> plan(t.pos(), t.state(), stack, Set.of(s), e) != null;
            if (standSpot(t.pos(), works, s) == null && towerSpot(t.pos(), works, s) == null && digSpot(t.pos(), works, s) == null) continue;
            List<BlockPos> chain = anchor(s, t.pos());
            if (chain != null && (bestChain == null || chain.size() < bestChain.size())) bestChain = chain;
        }
        return bestChain;
    }

    private List<BlockPos> anchor(BlockPos s, BlockPos avoid) {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        Deque<BlockPos> q = new ArrayDeque<>();
        q.add(s);
        parent.put(s, s);
        while (!q.isEmpty()) {
            BlockPos c = q.poll();
            int depth = 0;
            for (BlockPos p = c; !p.equals(s); p = parent.get(p)) depth++;
            for (Direction d : Direction.values()) {
                BlockPos nb = c.offset(d);
                if (!nb.equals(avoid) && solid(nb)) {
                    List<BlockPos> chain = new ArrayList<>();
                    for (BlockPos p = c; ; p = parent.get(p)) {
                        chain.add(p);
                        if (p.equals(s)) break;
                    }
                    return chain;
                }
            }
            if (depth >= 4) continue;
            for (Direction d : Direction.values()) {
                BlockPos nb = c.offset(d);
                if (parent.containsKey(nb) || nb.equals(avoid) || solid(nb) || byPos.containsKey(nb.asLong())) continue;
                parent.put(nb, c);
                q.add(nb);
            }
        }
        return null;
    }

    private void breakStep(BlockPos p) {
        Vec3d point = breakPoint(mc.player.getEyePos(), p);
        if (point == null) point = Vec3d.ofCenter(p);
        if (!turnTowards(point)) return;
        HitResult r = mc.player.raycast(mc.player.getBlockInteractionRange(), 1, false);
        if (!(r instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(p)) {
            nudgeAim(point);
            return;
        }
        BlockState s = mc.world.getBlockState(p);
        FindItemResult tool = InvUtils.findFastestTool(s);
        if (tool.found() && tool.isHotbar() && mc.player.getInventory().getSelectedSlot() != tool.slot()) InvUtils.swap(tool.slot(), false);
        if (mc.interactionManager.updateBlockBreakingProgress(p, hit.getSide())) mc.player.swingHand(Hand.MAIN_HAND);
    }

    private boolean inReach(Vec3d p) {
        return mc.player.getEyePos().distanceTo(p) <= mc.player.getBlockInteractionRange() - 0.5;
    }

    private Box playerBox() {
        return mc.player.getBoundingBox();
    }

    private void tune(Target t) {
        BlockPos p = t.pos();
        Vec3d point = mc.world.getBlockState(p).getOutlineShape(mc.world, p).getBoundingBox().getCenter().add(Vec3d.of(p));
        if (!sees(mc.player.getEyePos(), p, point)) {
            if (!walkTo(standSpot(p, e -> sees(e, p, point)))) skip(t, "can't reach it to set it up");
            return;
        }
        status = "setting up " + t.state().getBlock().getName().getString();
        if (!turnTowards(point)) return;
        HitResult r = mc.player.raycast(mc.player.getBlockInteractionRange(), 1, false);
        if (!(r instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(p)) {
            nudgeAim(point);
            return;
        }
        mc.options.sneakKey.setPressed(false);
        if (mc.player.isSneaking()) return;
        mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
        mc.player.swingHand(Hand.MAIN_HAND);
        wait = actionDelay.get() + jitter(3);
    }

    private boolean sees(Vec3d eye, BlockPos p, Vec3d point) {
        if (eye.distanceTo(point) > mc.player.getBlockInteractionRange() - 0.5) return false;
        BlockHitResult r = mc.world.raycast(new RaycastContext(eye, point, RaycastContext.ShapeType.OUTLINE, RaycastContext.FluidHandling.NONE, mc.player));
        return r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(p);
    }

    private boolean canBreakFrom(Vec3d eye, BlockPos p) {
        return breakPoint(eye, p) != null;
    }

    private Vec3d breakPoint(Vec3d eye, BlockPos p) {
        Vec3d c = Vec3d.ofCenter(p);
        List<Vec3d> points = new ArrayList<>();
        points.add(c);
        for (Direction d : Direction.values()) points.add(c.add(Vec3d.of(d.getVector()).multiply(0.4)));
        for (Vec3d pt : points) {
            if (eye.distanceTo(pt) > mc.player.getBlockInteractionRange() - 0.5) continue;
            BlockHitResult r = ray(eye, pt);
            if (r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(p)) return pt;
        }
        return null;
    }

    private boolean walkTo(BlockPos spot) {
        if (spot == null) return false;
        if (!BaritoneBridge.available()) {
            status = "walk to " + spot.toShortString() + " (no Baritone)";
            return true;
        }
        status = "walking";
        BaritoneBridge.goTo(spot);
        walkGoal = spot;
        walking = true;
        goalTicks = 0;
        return true;
    }

    private BlockPos standSpot(BlockPos target, java.util.function.Predicate<Vec3d> works, BlockPos... avoid) {
        BlockPos me = mc.player.getBlockPos();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.iterate(target.add(-4, -5, -4), target.add(4, 3, 4))) {
            if (p.equals(me) || !solid(p.down()) || solid(p) || solid(p.up())) continue;
            if (!mc.world.getBlockState(p).getFluidState().isEmpty()) continue;
            if (unreachable.contains(p.asLong())) continue;
            if (pending(p) && p.getY() <= target.getY() || pending(p.up()) && p.getY() + 1 <= target.getY()) continue;
            Box body = new Box(p).union(new Box(p.up()));
            if (body.intersects(new Box(target))) continue;
            boolean blocked = false;
            for (BlockPos a : avoid) if (body.intersects(new Box(a))) blocked = true;
            if (blocked) continue;
            double score = p.getSquaredDistance(me);
            if (score >= bestScore) continue;
            if (!worksAt(Vec3d.ofBottomCenter(p), works)) continue;
            bestScore = score;
            best = p.toImmutable();
        }
        return best;
    }

    private BlockPos bestSpot(Target target, java.util.function.Predicate<Vec3d> works) {
        BlockPos me = mc.player.getBlockPos();
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos p : BlockPos.iterate(target.pos().add(-4, -5, -4), target.pos().add(4, 3, 4))) {
            if (!p.equals(me) && standable(p, target.pos())) spots.add(p.toImmutable());
        }
        spots.sort(Comparator.comparingDouble(p -> p.getSquaredDistance(me)));
        List<Target> others = new ArrayList<>();
        for (Target t : targets) {
            if (t != target && t.pos().getY() == target.pos().getY() && pending(t.pos()) && t.pos().getSquaredDistance(target.pos()) <= 36 && count(t.item()) > 0) others.add(t);
        }
        others.sort(Comparator.comparingDouble(t -> t.pos().getSquaredDistance(target.pos())));
        if (others.size() > 16) others = others.subList(0, 16);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int tried = 0;
        for (BlockPos p : spots) {
            if (tried >= 8) break;
            if (!worksAt(Vec3d.ofBottomCenter(p), works)) continue;
            tried++;
            Vec3d eye = Vec3d.ofBottomCenter(p).add(0, eyeHeight(false), 0);
            Box body = new Box(p).union(new Box(p.up()));
            int covers = 0;
            for (Target o : others) if (!body.intersects(new Box(o.pos())) && plan(o.pos(), o.state(), new ItemStack(o.item()), Set.of(), eye) != null) covers++;
            double score = p.getSquaredDistance(me) / 4 - covers * 3;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private boolean standable(BlockPos p, BlockPos target) {
        if (!solid(p.down()) || solid(p) || solid(p.up()) || !mc.world.getBlockState(p).getFluidState().isEmpty() || unreachable.contains(p.asLong())) return false;
        if (pending(p) && p.getY() <= target.getY() || pending(p.up()) && p.getY() + 1 <= target.getY()) return false;
        return !new Box(p).union(new Box(p.up())).intersects(new Box(target));
    }

    private boolean pending(BlockPos p) {
        Target t = byPos.get(p.asLong());
        return t != null && !skipped.contains(p.asLong()) && !done(t);
    }

    private void restock() {
        BlockPos c = chest.get();
        switch (restockStep) {
            case 0 -> {
                status = "going to the material chest";
                if (!inReach(Vec3d.ofCenter(c))) {
                    if (!BaritoneBridge.available()) {
                        status = "walk to the material chest at " + c.toShortString();
                        return;
                    }
                    BaritoneBridge.goNextTo(c);
                    walking = true;
                    goalTicks = 0;
                    return;
                }
                restockStep = 1;
            }
            case 1 -> {
                status = "opening the chest";
                if (!turnTowards(Vec3d.ofCenter(c))) return;
                HitResult r = mc.player.raycast(mc.player.getBlockInteractionRange(), 1, false);
                if (!(r instanceof BlockHitResult hit) || !hit.getBlockPos().equals(c)) {
                    nudgeAim(Vec3d.ofCenter(c));
                    return;
                }
                mc.interactionManager.interactBlock(mc.player, Hand.MAIN_HAND, hit);
                mc.player.swingHand(Hand.MAIN_HAND);
                restockStep = 2;
                restockTicks = 0;
                wait = actionDelay.get();
            }
            case 2 -> {
                if (!(mc.player.currentScreenHandler instanceof GenericContainerScreenHandler h)) {
                    if (++restockTicks > 60) {
                        ChatUtils.warning("Schematic Builder: the material chest didn't open.");
                        stopBuild("stopped: can't open the material chest at " + c.toShortString() + ".");
                    }
                    return;
                }
                status = "taking materials";
                Map<Item, Integer> need = needs();
                int chestSlots = h.getRows() * 9;
                for (int i = 0; i < chestSlots; i++) {
                    ItemStack s = h.getSlot(i).getStack();
                    if (s.isEmpty() || need.getOrDefault(s.getItem(), 0) <= 0 || !roomFor(s)) continue;
                    mc.interactionManager.clickSlot(h.syncId, i, 0, SlotActionType.QUICK_MOVE, mc.player);
                    wait = actionDelay.get() + jitter(2);
                    return;
                }
                missingItems.clear();
                for (Map.Entry<Item, Integer> e : need.entrySet()) if (e.getValue() > 0 && count(e.getKey()) == 0) missingItems.put(e.getKey(), e.getValue());
                mc.player.closeHandledScreen();
                wait = actionDelay.get();
                restockStep = 3;
            }
            default -> {
                for (Target t : targets) {
                    if (!done(t) && !skipped.contains(t.pos().asLong()) && count(t.item()) == 0 && missingItems.containsKey(t.item())) {
                        skipped.add(t.pos().asLong());
                    }
                }
                if (!missingItems.isEmpty()) {
                    ChatUtils.warning("Schematic Builder: the chest is missing:");
                    missingItems.forEach((item, n) -> ChatUtils.warning("  %s x%d", item.getName().getString(), n));
                }
                current = null;
                mode = Mode.BUILDING;
            }
        }
    }

    private Map<Item, Integer> needs() {
        Map<Item, Integer> need = new LinkedHashMap<>();
        for (Target t : targets) if (!done(t) && !skipped.contains(t.pos().asLong())) need.merge(t.item(), 1, Integer::sum);
        need.replaceAll((item, n) -> n - count(item));
        if (supportItem() == null) for (Block b : supportBlocks.get()) need.merge(b.asItem(), 32, Integer::sum);
        return need;
    }

    private boolean roomFor(ItemStack s) {
        for (int i = 0; i < 36; i++) {
            ItemStack inv = mc.player.getInventory().getStack(i);
            if (inv.isEmpty() || ItemStack.areItemsAndComponentsEqual(inv, s) && inv.getCount() < inv.getMaxCount()) return true;
        }
        return false;
    }

    private int jitter(int n) {
        return speed.get() == Speed.VeryFast ? 0 : rng.nextInt(n);
    }

    private boolean turnTowards(Vec3d target) {
        float[] want = anglesTo(mc.player.getEyePos(), target);
        return aim.turn(mc.player, want[0], want[1], turnSpeed.get().floatValue(), Math.abs(want[1]) > 80);
    }

    private void nudgeAim(Vec3d target) {
        float[] want = anglesTo(mc.player.getEyePos(), target);
        mc.player.setYaw(want[0]);
        mc.player.setPitch(want[1]);
    }

    @EventHandler
    private void onLeftClick(StartBreakingBlockEvent event) {
        if (mode != Mode.PICKING) return;
        event.cancel();
        origin.set(event.blockPos.up());
        mode = Mode.IDLE;
        ChatUtils.info("Schematic Builder: placed at %s. Press Start building when ready.", origin.get().toShortString());
    }

    private void refreshPreview() {
        previewList.clear();
        if (!isActive() || targets.isEmpty() || !preview.get()) return;
        BlockPos me = mc.player.getBlockPos();
        int r = previewRange.get();
        for (Target t : targets) {
            if (Math.abs(t.pos().getX() - me.getX()) > r || Math.abs(t.pos().getY() - me.getY()) > r || Math.abs(t.pos().getZ() - me.getZ()) > r) continue;
            if (done(t)) continue;
            previewList.add(t);
        }
        if (previewList.size() > maxBlocks.get()) {
            previewList.sort(Comparator.comparingDouble(t -> t.pos().getSquaredDistance(me)));
            previewList.subList(maxBlocks.get(), previewList.size()).clear();
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!isActive() || targets.isEmpty()) return;
        Schematic s = shaped();
        if (s != null) {
            BlockPos o = origin.get();
            SettingColor edge = new SettingColor(80, 170, 255, 180);
            event.renderer.box(new Box(o.getX(), o.getY(), o.getZ(), o.getX() + s.sizeX, o.getY() + s.sizeY, o.getZ() + s.sizeZ), edge, edge, ShapeMode.Lines, 0);
        }
        if (!preview.get()) return;
        SettingColor miss = missingColor.get(), wrong = wrongColor.get();
        SettingColor missLine = new SettingColor(miss.r, miss.g, miss.b, Math.min(255, miss.a * 3 + 40)), wrongLine = new SettingColor(wrong.r, wrong.g, wrong.b, 220);
        for (Target t : previewList) {
            BlockState here = mc.world.getBlockState(t.pos());
            boolean isWrong = !here.isAir() && !here.isReplaceable();
            event.renderer.box(t.pos(), isWrong ? wrong : miss, isWrong ? wrongLine : missLine, ShapeMode.Both, 0);
        }
        SettingColor sup = supportColor.get(), supLine = new SettingColor(sup.r, sup.g, sup.b, 230);
        for (BlockPos p : supports) event.renderer.box(p, sup, supLine, ShapeMode.Both, 0);
        for (BlockPos p : supportsToPlace) event.renderer.box(p, sup, supLine, ShapeMode.Lines, 0);
        if (current != null) event.renderer.box(current.pos(), new SettingColor(255, 255, 255, 60), new SettingColor(255, 255, 255, 255), ShapeMode.Both, 0);
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isActive() || !hud.get() || mc.options.hudHidden) return;
        List<String> lines = new ArrayList<>();
        if (mode == Mode.PICKING) lines.add("Schematic Builder: look where it should go, left-click to place it");
        else if (loaded == null) lines.add("Schematic Builder: choose a schematic in the module settings");
        else {
            int total = targets.size(), left = 0;
            for (Target t : targets) if (!done(t)) left++;
            String head = String.format("Schematic Builder: %s  %d / %d placed (%d%%)", loaded.name, total - left, total, total == 0 ? 100 : Math.round(100.0 * (total - left) / total));
            lines.add(head);
            if (mode == Mode.BUILDING || mode == Mode.RESTOCK) {
                lines.set(0, head + "  " + Automation.timing(startedAt, placedCount, placedCount + left));
                lines.add(pauseReason != null ? "  PAUSED: " + pauseReason : "  " + status);
            }
            else if (mode == Mode.IDLE) lines.add("  ready: press Start building (preview shows where it goes)");
            else if (mode == Mode.DONE) lines.add("  finished" + (skipped.isEmpty() ? "" : " (" + skipped.size() + " skipped)"));
            if (!missingItems.isEmpty()) {
                StringBuilder sb = new StringBuilder("  missing: ");
                int i = 0;
                for (Map.Entry<Item, Integer> e : missingItems.entrySet()) {
                    if (i++ == 3) {
                        sb.append("...");
                        break;
                    }
                    sb.append(e.getKey().getName().getString()).append(" x").append(e.getValue()).append("  ");
                }
                lines.add(sb.toString());
            }
        }
        Automation.status(event.drawContext, "Schematic Builder", lines, 0xFF8FD8FF);
    }
}
