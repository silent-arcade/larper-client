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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
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
    public enum LayerView { All, Current, UpToCurrent }

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
        .defaultValue(BlockPos.ZERO).onChanged(v -> rebuildTargets()).build());

    private final Setting<Turn> turn = sgGeneral.add(new EnumSetting.Builder<Turn>()
        .name("rotation").description("Turn the schematic around.").defaultValue(Turn.None).onChanged(v -> rebuildTargets()).build());

    private final Setting<BlockPos> chest = sgGeneral.add(new BlockPosSetting.Builder()
        .name("material-chest").description("Chest with the materials. Look at it and press \"Chest = block I look at\".").defaultValue(BlockPos.ZERO).build());

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

    private final Setting<SettingColor> wrongStateColor = sgPreview.add(new ColorSetting.Builder()
        .name("wrong-rotation-color").description("The right block, but facing the wrong way or in the wrong state.").defaultValue(new SettingColor(255, 170, 30, 70)).visible(preview::get).build());

    private final Setting<LayerView> layerView = sgPreview.add(new EnumSetting.Builder<LayerView>()
        .name("layers").description("All: the whole schematic. Current: only the layer being built now. Up to current: hides the layers above it.").defaultValue(LayerView.All).visible(preview::get).build());

    private final Setting<Boolean> arrows = sgPreview.add(new BoolSetting.Builder()
        .name("facing-arrows").description("Arrows on blocks that face a direction (stairs, observers, pistons, logs...) so you can see which way they'll go.").defaultValue(true).visible(preview::get).build());

    private final Setting<Boolean> lookInfo = sgPreview.add(new BoolSetting.Builder()
        .name("look-info").description("Look at a spot in the schematic to see what block goes there, which way it faces, and whether it's missing or wrong.").defaultValue(true).visible(preview::get).build());

    private final Setting<Boolean> materials = sgPreview.add(new BoolSetting.Builder()
        .name("material-list").description("Show the blocks still needed, how many you have and how many are still to place.").defaultValue(true).build());

    private final Setting<Integer> previewRange = sgPreview.add(new IntSetting.Builder()
        .name("preview-range").description("Only show blocks within this many blocks of you.").defaultValue(32).range(4, 96).sliderRange(8, 64).visible(preview::get).build());

    private final Setting<Integer> maxBlocks = sgPreview.add(new IntSetting.Builder()
        .name("max-blocks").description("Most preview blocks drawn at once (lower it on a low-end PC).").defaultValue(6000).range(100, 50000).sliderRange(500, 20000).visible(preview::get).build());

    private final Setting<Boolean> hud = sgPreview.add(new BoolSetting.Builder()
        .name("progress").description("Show progress, what's next and missing materials at the top of the screen.").defaultValue(true).build());

    private enum Mode { IDLE, PICKING, BUILDING, RESTOCK, DONE }

    private record Target(BlockPos pos, BlockState state, Item item) {}

    private record Plan(BlockPos click, Direction side, Vec3 hit) {}

    private record Pending(BlockPos at, BlockState want, BlockPos clickPos, Direction side, BlockState clicked, int ticks) {}

    private Pending pending;

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
        super(AddonTemplate.AUTOMATION, "schematic-builder", "Builds a schematic like a real player: real left-click breaking, crouches to place against chests and other clickable blocks, learns from its mistakes, and shows a detailed preview.");
    }

    private Path folder() {
        Path dir = mc.gameDirectory.toPath().resolve("larper-client").resolve("schematics").toAbsolutePath().normalize();
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
            if (mc.player != null) origin.set(mc.player.blockPosition());
        };
        WHorizontalList nudge = list.add(theme.horizontalList()).widget();
        nudge.add(theme.button("X-")).widget().action = () -> origin.set(origin.get().offset(-1, 0, 0));
        nudge.add(theme.button("X+")).widget().action = () -> origin.set(origin.get().offset(1, 0, 0));
        nudge.add(theme.button("Y-")).widget().action = () -> origin.set(origin.get().offset(0, -1, 0));
        nudge.add(theme.button("Y+")).widget().action = () -> origin.set(origin.get().offset(0, 1, 0));
        nudge.add(theme.button("Z-")).widget().action = () -> origin.set(origin.get().offset(0, 0, -1));
        nudge.add(theme.button("Z+")).widget().action = () -> origin.set(origin.get().offset(0, 0, 1));
        nudge.add(theme.button("Rotate 90")).widget().action = () -> turn.set(Turn.values()[(turn.get().ordinal() + 1) % 4]);
        WHorizontalList misc = list.add(theme.horizontalList()).widget();
        misc.add(theme.button("Chest = block I look at")).widget().action = () -> {
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
                chest.set(hit.getBlockPos());
                ChatUtils.info("Schematic Builder: material chest set to %s.", hit.getBlockPos().toShortString());
            } else ChatUtils.warning("Look at the chest first.");
        };
        misc.add(theme.button("Support = block in my hand")).widget().action = () -> {
            if (mc.player == null || !(mc.player.getMainHandItem().getItem() instanceof BlockItem bi)) {
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
            case None -> Rotation.NONE;
            case Right90 -> Rotation.CLOCKWISE_90;
            case Turn180 -> Rotation.CLOCKWISE_180;
            case Left90 -> Rotation.COUNTERCLOCKWISE_90;
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
            if (st.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF) && st.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER) continue;
            if (st.hasProperty(BlockStateProperties.BED_PART) && st.getValue(BlockStateProperties.BED_PART) == BedPart.HEAD) continue;
            Item item = st.getBlock().asItem();
            if (item == Items.AIR || !st.getFluidState().isEmpty() && !st.hasProperty(BlockStateProperties.WATERLOGGED)) continue;
            Target t = new Target(o.offset(b.pos()), st, item);
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
            .forEach(e -> ChatUtils.info("  %s x%d (you have %d)", e.getKey().getDefaultInstance().getHoverName().getString(), e.getValue(), count(e.getKey())));
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
        if (mc.options != null) mc.options.keyAttack.setDown(false);
        if (mode == Mode.RESTOCK && mc.player != null && mc.player.containerMenu instanceof ChestMenu) mc.player.closeContainer();
        mode = Mode.IDLE;
        if (why != null) ChatUtils.info("Schematic Builder: %s", why);
    }

    private void releaseKeys() {
        if (mc.options == null) return;
        mc.options.keyAttack.setDown(false);
        mc.options.keyShift.setDown(false);
        mc.options.keyUp.setDown(false);
        mc.options.keyDown.setDown(false);
        mc.options.keyLeft.setDown(false);
        mc.options.keyRight.setDown(false);
        mc.options.keyJump.setDown(false);
    }

    private static double eyeHeight(boolean crouching) {
        return crouching ? 1.27 : 1.62;
    }

    private static boolean worksAt(Vec3 feet, java.util.function.Predicate<Vec3> works) {
        return works.test(feet.add(0, eyeHeight(false), 0)) || works.test(feet.add(0, eyeHeight(true), 0));
    }

    private boolean crouchFor(BlockPos target, java.util.function.Predicate<Vec3> works) {
        if (crouch || playerBox().intersects(new AABB(target)) || !mc.player.onGround() || !works.test(mc.player.position().add(0, eyeHeight(true), 0))) return false;
        crouch = true;
        mc.options.keyShift.setDown(true);
        status = "crouching for the right angle";
        return true;
    }

    private boolean unCrouch() {
        if (!crouch || !mc.player.isShiftKeyDown()) return false;
        crouch = false;
        mc.options.keyShift.setDown(false);
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

    private BlockPos[] towerSpot(BlockPos target, java.util.function.Predicate<Vec3> works, BlockPos... avoid) {
        BlockPos me = mc.player.blockPosition();
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
            if (ground == null || unreachable.contains(ground.above().asLong())) continue;
            BlockPos base = ground.above();
            if (!free(base, target, avoid) || !free(base.above(), target, avoid)) continue;
            for (int h = 1; h <= 5; h++) {
                if (!free(base.above(h + 1), target, avoid)) break;
                double score = h * 8 + base.distSqr(me) / 4;
                if (score >= bestScore) break;
                if (!worksAt(Vec3.atBottomCenterOf(base.above(h)), works)) continue;
                bestScore = score;
                best = new BlockPos[] {base, base.above(h)};
                break;
            }
        }
        return best;
    }

    private boolean free(BlockPos c, BlockPos target, BlockPos... avoid) {
        if (solid(c) || !mc.level.getBlockState(c).getFluidState().isEmpty() || byPos.containsKey(c.asLong()) || c.equals(target)) return false;
        for (BlockPos a : avoid) if (c.equals(a)) return false;
        return true;
    }

    private void towerStep() {
        BlockPos feet = mc.player.blockPosition();
        boolean onColumn = feet.getX() == towerTo.getX() && feet.getZ() == towerTo.getZ();
        if (jumpFrom == null && mc.player.onGround() && onColumn && feet.getY() >= towerTo.getY()) {
            towerTo = null;
            mc.options.keyJump.setDown(false);
            return;
        }
        if (!onColumn) {
            if (unreachable.contains(towerBase.asLong())) {
                towerTo = null;
                return;
            }
            if (jumpFrom == null && mc.player.onGround() && !walkTo(towerBase)) towerTo = null;
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
            if (!mc.player.onGround() || !select(item)) return;
            if (stepToCentre(e -> true)) return;
            if (!turnTowards(Vec3.atCenterOf(feet.below()).add(0, 0.5, 0))) return;
            jumpFrom = feet;
            mc.options.keyJump.setDown(true);
            return;
        }
        mc.options.keyJump.setDown(false);
        if (solid(jumpFrom)) {
            supports.add(jumpFrom);
            usedSupports.add(mc.level.getBlockState(jumpFrom).getBlock());
            jumpFrom = null;
            stallTicks = 0;
            return;
        }
        if (mc.player.onGround()) {
            jumpFrom = null;
            return;
        }
        if (mc.player.getY() < jumpFrom.getY() + 1.02) return;
        HitResult r = mc.player.pick(mc.player.blockInteractionRange(), 1, false);
        if (!(r instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(jumpFrom.below()) || hit.getDirection() != Direction.UP) return;
        if (!mc.player.getMainHandItem().is(item)) return;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }

    private boolean startDig(BlockPos spot) {
        if (spot == null) return false;
        digAt = spot;
        status = "digging a spot to stand in";
        return true;
    }

    private BlockPos digSpot(BlockPos target, java.util.function.Predicate<Vec3> works, BlockPos... avoid) {
        BlockPos me = mc.player.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-3, -2, -3), target.offset(3, 0, 3))) {
            Target t = byPos.get(p.asLong());
            if (t == null || !done(t) || !solid(p.below()) || !free(p.above(), target, avoid) || !free(p.above(2), target, avoid)) continue;
            if (mc.level.getBlockState(p).getDestroySpeed(mc.level, p) < 0 || new AABB(p).minmax(new AABB(p.above())).intersects(new AABB(target))) continue;
            double score = p.distSqr(me);
            if (score >= bestScore) continue;
            pretendAir.add(p.immutable());
            boolean ok = worksAt(Vec3.atBottomCenterOf(p), works);
            pretendAir.clear();
            if (!ok) continue;
            bestScore = score;
            best = p.immutable();
        }
        return best;
    }

    private boolean digStep() {
        if (solid(digAt)) {
            if (!canBreakFrom(mc.player.getEyePosition(), digAt)) {
                if (!walkTo(standSpot(digAt, e -> canBreakFrom(e, digAt), digAt))) digAt = null;
                return true;
            }
            status = "digging a spot to stand in";
            breaking = digAt;
            return true;
        }
        if (!mc.player.blockPosition().equals(digAt)) {
            if (!walkTo(digAt)) digAt = null;
            return true;
        }
        return false;
    }

    private boolean stepToCentre(java.util.function.Predicate<Vec3> works) {
        Vec3 c = Vec3.atBottomCenterOf(mc.player.blockPosition());
        double dx = c.x - mc.player.getX(), dz = c.z - mc.player.getZ();
        if (dx * dx + dz * dz < 0.01 || !mc.player.onGround() || !worksAt(c, works)) return false;
        double yaw = Math.toRadians(mc.player.getYRot());
        double fwd = -dx * Math.sin(yaw) + dz * Math.cos(yaw), left = dx * Math.cos(yaw) + dz * Math.sin(yaw);
        mc.options.keyUp.setDown(fwd > 0.04);
        mc.options.keyDown.setDown(fwd < -0.04);
        mc.options.keyLeft.setDown(left > 0.04);
        mc.options.keyRight.setDown(left < -0.04);
        centering = true;
        status = "stepping into place";
        return true;
    }

    private boolean done(Target t) {
        BlockState got = mc.level.getBlockState(t.pos());
        return matches(got, t.state()) && tuned(got, t.state());
    }

    private static boolean tuned(BlockState got, BlockState want) {
        for (Property<?> p : want.getProperties()) {
            if (TUNE_PROPS.contains(p.getName()) && !got.getValue(p).equals(want.getValue(p))) return false;
        }
        return true;
    }

    private static boolean matches(BlockState got, BlockState want) {
        if (got == null || got.getBlock() != want.getBlock()) return false;
        for (Property<?> p : want.getProperties()) {
            if (FREE_PROPS.contains(p.getName())) continue;
            if (!got.getValue(p).equals(want.getValue(p))) return false;
        }
        return true;
    }

    private int count(Item item) {
        int n = 0;
        for (int i = 0; i < 36; i++) {
            ItemStack s = mc.player.getInventory().getItem(i);
            if (s.is(item)) n += s.getCount();
        }
        return n;
    }

    private boolean solid(BlockPos p) {
        BlockState s = mc.level.getBlockState(p);
        return !s.isAir() && !s.canBeReplaced() && s.getFluidState().isEmpty() && !s.getCollisionShape(mc.level, p).isEmpty();
    }

    private boolean isSupportBlock(BlockState s) {
        return supportBlocks.get().contains(s.getBlock()) || usedSupports.contains(s.getBlock());
    }

    private static boolean canSupport(Block b) {
        float hardness = b.defaultDestroyTime();
        return b.defaultBlockState().isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO) && !(b instanceof FallingBlock)
            && !(b instanceof net.minecraft.world.level.block.EntityBlock) && hardness >= 0 && hardness <= 3 && b.asItem() != Items.AIR;
    }

    private Item supportItem() {
        for (Block b : supportBlocks.get()) if (count(b.asItem()) > 0) return b.asItem();
        return null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.level == null) return;
        if (++previewTicks >= 10) {
            previewTicks = 0;
            refreshPreview();
        }
        if (mode == Mode.PICKING) {
            if (mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) origin.set(hit.getBlockPos().above());
            return;
        }
        if (mode != Mode.BUILDING && mode != Mode.RESTOCK) return;
        if (pending != null && mode == Mode.BUILDING) checkPending();
        if (mode == Mode.BUILDING && mc.screen != null) return;
        String reason = Automation.pauseReason(pauseOnMove.get(), pauseOnPlayer.get(), playerRange.get(), pauseOnHealth.get(), minHealth.get());
        if (reason != null && pauseReason == null) {
            BaritoneBridge.stop();
            walking = false;
            centering = false;
            crouch = false;
            jumpFrom = null;
            releaseKeys();
            if (!reason.startsWith("you're")) ChatUtils.warning("Schematic Builder paused: %s", reason);
        } else if (reason == null && pauseReason != null && !pauseReason.startsWith("you're")) ChatUtils.info("Schematic Builder resumed.");
        pauseReason = reason;
        if (reason != null) return;
        if (walking) {
            if (BaritoneBridge.busy() && ++goalTicks < 600) return;
            walking = false;
            if (walkGoal != null && !mc.player.blockPosition().equals(walkGoal)) unreachable.add(walkGoal.asLong());
        }
        // only breakStep holds left click, and only on the tick it's aimed at the block
        mc.options.keyAttack.setDown(false);
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
            mc.options.keyShift.setDown(crouch);
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
            if (mc.level.getBlockState(breaking).isAir()) {
                breaking = null;
                mc.options.keyAttack.setDown(false);
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
            if (!isSupportBlock(mc.level.getBlockState(s)) || mc.level.getBlockState(s.above()).getBlock() instanceof FallingBlock) {
                supportsToBreak.poll();
                supports.remove(s);
                return;
            }
            if (!s.equals(breakingSupport)) {
                breakingSupport = s;
                supportTicks = 0;
            }
            if (!canBreakFrom(mc.player.getEyePosition(), s)) {
                java.util.function.Predicate<Vec3> reach = e -> canBreakFrom(e, s);
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
                mc.options.keyShift.setDown(false);
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
        BlockState here = mc.level.getBlockState(t.pos());
        if (!here.isAir() && !here.canBeReplaced() && here.getBlock() != t.state().getBlock()) {
            if (!fixWrong.get()) {
                skip(t, "something else is in the way");
                return;
            }
            if (!canBreakFrom(mc.player.getEyePosition(), t.pos())) {
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
            if (chest.get().equals(BlockPos.ZERO)) {
                skip(t, "no " + t.item().getDefaultInstance().getHoverName().getString() + " and no material chest set");
                return;
            }
            mode = Mode.RESTOCK;
            restockStep = 0;
            return;
        }
        ItemStack stack = new ItemStack(t.item());
        java.util.function.Predicate<Vec3> works = e -> plan(t.pos(), t.state(), stack, Set.of(), e) != null;
        if (digAt != null && digStep()) return;
        Plan plan = playerBox().intersects(new AABB(t.pos())) ? null : plan(t.pos(), t.state(), stack, Set.of(), mc.player.getEyePosition());
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
            if (chest.get().equals(BlockPos.ZERO)) {
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
        Vec3 eye = mc.player.getEyePosition();
        double reach = mc.player.blockInteractionRange() + 0.5;
        Target near = null;
        double nearScore = Double.MAX_VALUE;
        Map<Target, Double> order = new HashMap<>();
        for (Target t : targets) {
            if (t.pos().getY() != lowest || skipped.contains(t.pos().asLong()) || deferred.contains(t.pos().asLong()) || done(t)) continue;
            double d = eye.distanceTo(Vec3.atCenterOf(t.pos()));
            double score = d * d + attempts.getOrDefault(t.pos().asLong(), 0) * 400;
            if (score < nearScore) {
                nearScore = score;
                near = t;
            }
            if (d > reach || count(t.item()) == 0 || playerBox().intersects(new AABB(t.pos()))) continue;
            float[] a = anglesTo(eye, Vec3.atCenterOf(t.pos()));
            double turn = Math.hypot(Mth.wrapDegrees(a[0] - mc.player.getYRot()), a[1] - mc.player.getXRot());
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
        if (targets.stream().anyMatch(t -> t.state().getBlock() instanceof net.minecraft.world.level.block.LeverBlock)) ChatUtils.info("Schematic Builder: levers are left off - flip them yourself when you're ready.");
        mode = Mode.DONE;
    }

    private List<BlockPos> supportsFor(Target t) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos s : supports) if (s.distManhattan(t.pos()) <= 12 && !supportsToPlace.contains(s)) out.add(s);
        out.sort(Comparator.comparingInt((BlockPos p) -> -p.getY()));
        return out;
    }

    private void placeSupport(BlockPos s) {
        if (solid(s)) {
            supportsToPlace.poll();
            if (isSupportBlock(mc.level.getBlockState(s))) {
                supports.add(s);
                usedSupports.add(mc.level.getBlockState(s).getBlock());
            }
            crouch = false;
            mc.options.keyShift.setDown(false);
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
        BlockState want = ((BlockItem) item).getBlock().defaultBlockState();
        ItemStack stack = new ItemStack(item);
        Plan p = playerBox().intersects(new AABB(s)) ? null : plan(s, want, stack, Set.of(), mc.player.getEyePosition());
        if (p != null) {
            execute(item, s, null, p);
            return;
        }
        java.util.function.Predicate<Vec3> works = e -> plan(s, want, stack, Set.of(), e) != null;
        if (unCrouch() || crouchFor(s, works) || stepToCentre(works)) return;
        if (walkTo(standSpot(s, works))) return;
        if (startTower(towerSpot(s, works, s))) return;
        supportsToPlace.clear();
        if (current != null) skip(current, "couldn't place a support block");
    }

    private void execute(Item item, BlockPos at, BlockState want, Plan plan) {
        if (!turnTowards(plan.hit())) return;
        HitResult r = mc.player.pick(mc.player.blockInteractionRange(), 1, false);
        if (!(r instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(plan.click()) || hit.getDirection() != plan.side()) {
            nudgeAim(plan.hit());
            return;
        }
        ItemStack stack = new ItemStack(item);
        if (want != null) {
            BlockState got = simulate(at, hit.getBlockPos(), hit.getDirection(), hit.getLocation(), stack, mc.player.getYRot(), mc.player.getXRot(), want);
            if (!matches(got, want)) {
                nudgeAim(plan.hit());
                return;
            }
        }
        if (!select(item)) return;
        BlockState clicked = mc.level.getBlockState(hit.getBlockPos());
        boolean sneak = needsSneak(clicked) && !hit.getBlockPos().equals(at);
        mc.options.keyShift.setDown(sneak || crouch);
        if ((sneak || crouch) && !mc.player.isShiftKeyDown()) return;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        if (want != null) pending = new Pending(at, want, hit.getBlockPos(), hit.getDirection(), clicked, 0);
        mc.options.keyShift.setDown(crouch);
        wait = actionDelay.get() + jitter(3);
    }

    // Right-clicking these opens or changes them instead of placing against them, so crouch (shift) while placing
    private static boolean interactive(BlockState s) {
        Block b = s.getBlock();
        return BlockUtils.isClickable(b) || s.hasBlockEntity() || b instanceof net.minecraft.world.level.block.NoteBlock || b instanceof net.minecraft.world.level.block.DiodeBlock
            || b instanceof net.minecraft.world.level.block.LeverBlock || b instanceof net.minecraft.world.level.block.ButtonBlock || b instanceof net.minecraft.world.level.block.TrapDoorBlock
            || b instanceof net.minecraft.world.level.block.FenceGateBlock || b instanceof net.minecraft.world.level.block.DoorBlock || b instanceof net.minecraft.world.level.block.BedBlock
            || b instanceof net.minecraft.world.level.block.AnvilBlock || b instanceof net.minecraft.world.level.block.AbstractCauldronBlock || b instanceof net.minecraft.world.level.block.ComposterBlock
            || b instanceof net.minecraft.world.level.block.CakeBlock || b instanceof net.minecraft.world.level.block.FlowerPotBlock || b instanceof net.minecraft.world.level.block.RespawnAnchorBlock
            || b instanceof net.minecraft.world.level.block.CraftingTableBlock || b instanceof net.minecraft.world.level.block.GrindstoneBlock || b instanceof net.minecraft.world.level.block.StonecutterBlock
            || b instanceof net.minecraft.world.level.block.LoomBlock || b instanceof net.minecraft.world.level.block.CartographyTableBlock || b instanceof net.minecraft.world.level.block.SmithingTableBlock
            || b instanceof net.minecraft.world.level.block.RedStoneWireBlock || b instanceof net.minecraft.world.level.block.CandleBlock || b instanceof net.minecraft.world.level.block.CandleCakeBlock;
    }

    private static boolean needsSneak(BlockState s) {
        return interactive(s) || com.example.addon.util.BuildMemory.needsSneak(s.getBlock());
    }

    // After every placement, check what really happened and remember it
    private void checkPending() {
        Pending pd = pending;
        if (mc.screen != null && pd.ticks() <= 10) {
            if (mc.player.containerMenu != mc.player.inventoryMenu) mc.player.closeContainer();
            else mc.setScreen(null);
            pending = null;
            if (com.example.addon.util.BuildMemory.learnSneak(pd.clicked().getBlock()))
                ChatUtils.info("Schematic Builder learned: clicking %s opens it, so it will crouch when placing against it.", pd.clicked().getBlock().getName().getString());
            return;
        }
        pending = new Pending(pd.at(), pd.want(), pd.clickPos(), pd.side(), pd.clicked(), pd.ticks() + 1);
        if (pd.ticks() + 1 < 4) return;
        pending = null;
        BlockState got = mc.level.getBlockState(pd.at());
        Block want = pd.want().getBlock();
        String name = want.getName().getString();
        if (matches(got, pd.want())) {
            com.example.addon.util.BuildMemory.recordSide(want, pd.side(), true);
        } else if (got.getBlock() == want) {
            if (com.example.addon.util.BuildMemory.recordSide(want, pd.side(), false))
                ChatUtils.info("Schematic Builder learned: %s came out facing the wrong way when placed on a %s face, so it'll use another face.", name, pd.side().getSerializedName());
        } else if (got.isAir() || got.canBeReplaced()) {
            if (!mc.level.getBlockState(pd.clickPos()).equals(pd.clicked())) {
                if (com.example.addon.util.BuildMemory.learnSneak(pd.clicked().getBlock()))
                    ChatUtils.info("Schematic Builder learned: clicking %s changes it instead of placing, so it will crouch when placing against it.", pd.clicked().getBlock().getName().getString());
            } else if (com.example.addon.util.BuildMemory.recordSide(want, pd.side(), false))
                ChatUtils.info("Schematic Builder learned: %s wouldn't go down on a %s face here, so it'll try another face.", name, pd.side().getSerializedName());
        }
    }

    private boolean select(Item item) {
        if (mc.player.getMainHandItem().is(item)) return true;
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

    private Plan plan(BlockPos t, BlockState want, ItemStack stack, Set<BlockPos> pretend, Vec3 eye) {
        double reach = mc.player.blockInteractionRange() - 0.25;
        Plan best = null;
        double bestDist = Double.MAX_VALUE;
        List<BlockPos> clicks = new ArrayList<>();
        for (Direction d : Direction.values()) clicks.add(t.relative(d));
        if (mc.level.getBlockState(t).getBlock() == want.getBlock()) clicks.add(t);
        for (BlockPos n : clicks) {
            boolean self = n.equals(t);
            boolean fake = pretend.contains(n);
            if (!self && !fake && !solid(n)) continue;
            for (Direction side : Direction.values()) {
                if (!self && !n.relative(side).equals(t)) continue;
                Vec3 normal = Vec3.atLowerCornerOf(side.getUnitVec3i());
                Vec3 center = Vec3.atCenterOf(n).add(normal.scale(0.5));
                if (eye.subtract(center).dot(normal) <= 0.05) continue;
                for (double a : new double[] {0, -0.3, 0.3, -0.45, 0.45}) for (double b : new double[] {0, -0.3, 0.3, -0.45, 0.45}) {
                    Vec3 hit = center.add(faceAxis(side, 0).scale(a)).add(faceAxis(side, 1).scale(b));
                    double dist = eye.distanceTo(hit);
                    if (dist > reach) continue;
                    if (!visible(eye, hit, n, side, fake)) continue;
                    float[] rot = anglesTo(eye, hit);
                    BlockState got = simulate(t, n, side, hit, stack, rot[0], rot[1], want);
                    if (!matches(got, want)) continue;
                    double score = dist + Math.abs(Mth.wrapDegrees(rot[0] - mc.player.getYRot())) / 90 + (a == 0 && b == 0 ? 0 : 0.2) + (Math.abs(a) > 0.4 || Math.abs(b) > 0.4 ? 0.3 : 0)
                        + com.example.addon.util.BuildMemory.sideScore(want.getBlock(), side) * 0.6;
                    if (score < bestDist && robust(t, n, side, hit, stack, rot, want)) {
                        bestDist = score;
                        best = new Plan(n, side, hit);
                    }
                }
            }
        }
        return best;
    }

    private boolean robust(BlockPos t, BlockPos n, Direction side, Vec3 hit, ItemStack stack, float[] rot, BlockState want) {
        for (float[] d : new float[][] {{4, 0}, {-4, 0}, {0, 4}, {0, -4}}) {
            if (!matches(simulate(t, n, side, hit, stack, rot[0] + d[0], Mth.clamp(rot[1] + d[1], -90, 90), want), want)) return false;
        }
        for (int axis = 0; axis < 2; axis++) for (double off : new double[] {0.15, -0.15}) {
            if (!matches(simulate(t, n, side, hit.add(faceAxis(side, axis).scale(off)), stack, rot[0], rot[1], want), want)) return false;
        }
        return true;
    }

    private static Vec3 faceAxis(Direction side, int which) {
        return switch (side.getAxis()) {
            case X -> which == 0 ? new Vec3(0, 1, 0) : new Vec3(0, 0, 1);
            case Y -> which == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
            case Z -> which == 0 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        };
    }

    private boolean visible(Vec3 eye, Vec3 hit, BlockPos n, Direction side, boolean fake) {
        Vec3 dir = hit.subtract(eye).normalize();
        if (fake) return ray(eye, hit.subtract(dir.scale(0.02))).getType() == HitResult.Type.MISS;
        BlockHitResult r = ray(eye, hit.add(dir.scale(0.05)));
        return r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(n) && r.getDirection() == side;
    }

    private BlockHitResult ray(Vec3 from, Vec3 to) {
        Vec3 dir = to.subtract(from).normalize();
        for (int i = 0; ; i++) {
            BlockHitResult r = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
            if (i == 4 || r.getType() != HitResult.Type.BLOCK || !pretendAir.contains(r.getBlockPos())) return r;
            from = r.getLocation().add(dir.scale(0.01));
        }
    }

    private static float[] anglesTo(Vec3 eye, Vec3 p) {
        Vec3 d = p.subtract(eye);
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -90, 90);
        return new float[] {yaw, pitch};
    }

    private BlockState simulate(BlockPos t, BlockPos n, Direction side, Vec3 hit, ItemStack stack, float yaw, float pitch, BlockState want) {
        if (!(stack.getItem() instanceof BlockItem bi)) return null;
        float oy = mc.player.getYRot(), op = mc.player.getXRot();
        mc.player.setYRot(yaw);
        mc.player.setXRot(pitch);
        try {
            BlockPlaceContext ctx = new BlockPlaceContext(mc.player, InteractionHand.MAIN_HAND, stack, new BlockHitResult(hit, side, n, false)) {
                @Override
                public BlockPos getClickedPos() {
                    return t;
                }
            };
            BlockState there = mc.level.getBlockState(t);
            if (!there.isAir() && !there.canBeReplaced(ctx)) return null;
            BlockState r = bi.getBlock().getStateForPlacement(ctx);
            if (want != null && (r == null || r.getBlock() != want.getBlock()) && want.getBlock() != bi.getBlock() && side.getAxis().isHorizontal()) {
                r = want.getBlock().getStateForPlacement(ctx);
            }
            return r;
        } catch (Exception e) {
            return null;
        } finally {
            mc.player.setYRot(oy);
            mc.player.setXRot(op);
        }
    }

    private List<BlockPos> supportChain(Target t, ItemStack stack) {
        List<BlockPos> bestChain = null;
        for (Direction d : Direction.values()) {
            BlockPos s = t.pos().relative(d);
            if (solid(s) || byPos.containsKey(s.asLong())) continue;
            java.util.function.Predicate<Vec3> works = e -> plan(t.pos(), t.state(), stack, Set.of(s), e) != null;
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
                BlockPos nb = c.relative(d);
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
                BlockPos nb = c.relative(d);
                if (parent.containsKey(nb) || nb.equals(avoid) || solid(nb) || byPos.containsKey(nb.asLong())) continue;
                parent.put(nb, c);
                q.add(nb);
            }
        }
        return null;
    }

    private void breakStep(BlockPos p) {
        Vec3 point = breakPoint(mc.player.getEyePosition(), p);
        if (point == null) point = Vec3.atCenterOf(p);
        if (!turnTowards(point)) return;
        HitResult r = mc.player.pick(mc.player.blockInteractionRange(), 1, false);
        if (!(r instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(p)) {
            nudgeAim(point);
            return;
        }
        BlockState s = mc.level.getBlockState(p);
        FindItemResult tool = InvUtils.findFastestTool(s);
        if (tool.found() && tool.isHotbar() && mc.player.getInventory().getSelectedSlot() != tool.slot()) InvUtils.swap(tool.slot(), false);
        // hold left click while the crosshair is on it; the game does the breaking, exactly like a player
        mc.options.keyAttack.setDown(true);
    }

    private boolean inReach(Vec3 p) {
        return mc.player.getEyePosition().distanceTo(p) <= mc.player.blockInteractionRange() - 0.5;
    }

    private AABB playerBox() {
        return mc.player.getBoundingBox();
    }

    private void tune(Target t) {
        BlockPos p = t.pos();
        Vec3 point = mc.level.getBlockState(p).getShape(mc.level, p).bounds().getCenter().add(Vec3.atLowerCornerOf(p));
        if (!sees(mc.player.getEyePosition(), p, point)) {
            if (!walkTo(standSpot(p, e -> sees(e, p, point)))) skip(t, "can't reach it to set it up");
            return;
        }
        status = "setting up " + t.state().getBlock().getName().getString();
        if (!turnTowards(point)) return;
        HitResult r = mc.player.pick(mc.player.blockInteractionRange(), 1, false);
        if (!(r instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK || !hit.getBlockPos().equals(p)) {
            nudgeAim(point);
            return;
        }
        mc.options.keyShift.setDown(false);
        if (mc.player.isShiftKeyDown()) return;
        mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
        mc.player.swing(InteractionHand.MAIN_HAND);
        wait = actionDelay.get() + jitter(3);
    }

    private boolean sees(Vec3 eye, BlockPos p, Vec3 point) {
        if (eye.distanceTo(point) > mc.player.blockInteractionRange() - 0.5) return false;
        BlockHitResult r = mc.level.clip(new ClipContext(eye, point, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return r.getType() == HitResult.Type.BLOCK && r.getBlockPos().equals(p);
    }

    private boolean canBreakFrom(Vec3 eye, BlockPos p) {
        return breakPoint(eye, p) != null;
    }

    private Vec3 breakPoint(Vec3 eye, BlockPos p) {
        Vec3 c = Vec3.atCenterOf(p);
        List<Vec3> points = new ArrayList<>();
        points.add(c);
        for (Direction d : Direction.values()) points.add(c.add(Vec3.atLowerCornerOf(d.getUnitVec3i()).scale(0.4)));
        for (Vec3 pt : points) {
            if (eye.distanceTo(pt) > mc.player.blockInteractionRange() - 0.5) continue;
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

    private BlockPos standSpot(BlockPos target, java.util.function.Predicate<Vec3> works, BlockPos... avoid) {
        BlockPos me = mc.player.blockPosition();
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        for (BlockPos p : BlockPos.betweenClosed(target.offset(-4, -5, -4), target.offset(4, 3, 4))) {
            if (p.equals(me) || !solid(p.below()) || solid(p) || solid(p.above())) continue;
            if (!mc.level.getBlockState(p).getFluidState().isEmpty()) continue;
            if (unreachable.contains(p.asLong())) continue;
            if (pending(p) && p.getY() <= target.getY() || pending(p.above()) && p.getY() + 1 <= target.getY()) continue;
            AABB body = new AABB(p).minmax(new AABB(p.above()));
            if (body.intersects(new AABB(target))) continue;
            boolean blocked = false;
            for (BlockPos a : avoid) if (body.intersects(new AABB(a))) blocked = true;
            if (blocked) continue;
            double score = p.distSqr(me);
            if (score >= bestScore) continue;
            if (!worksAt(Vec3.atBottomCenterOf(p), works)) continue;
            bestScore = score;
            best = p.immutable();
        }
        return best;
    }

    private BlockPos bestSpot(Target target, java.util.function.Predicate<Vec3> works) {
        BlockPos me = mc.player.blockPosition();
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(target.pos().offset(-4, -5, -4), target.pos().offset(4, 3, 4))) {
            if (!p.equals(me) && standable(p, target.pos())) spots.add(p.immutable());
        }
        spots.sort(Comparator.comparingDouble(p -> p.distSqr(me)));
        List<Target> others = new ArrayList<>();
        for (Target t : targets) {
            if (t != target && t.pos().getY() == target.pos().getY() && pending(t.pos()) && t.pos().distSqr(target.pos()) <= 36 && count(t.item()) > 0) others.add(t);
        }
        others.sort(Comparator.comparingDouble(t -> t.pos().distSqr(target.pos())));
        if (others.size() > 16) others = others.subList(0, 16);
        BlockPos best = null;
        double bestScore = Double.MAX_VALUE;
        int tried = 0;
        for (BlockPos p : spots) {
            if (tried >= 8) break;
            if (!worksAt(Vec3.atBottomCenterOf(p), works)) continue;
            tried++;
            Vec3 eye = Vec3.atBottomCenterOf(p).add(0, eyeHeight(false), 0);
            AABB body = new AABB(p).minmax(new AABB(p.above()));
            int covers = 0;
            for (Target o : others) if (!body.intersects(new AABB(o.pos())) && plan(o.pos(), o.state(), new ItemStack(o.item()), Set.of(), eye) != null) covers++;
            double score = p.distSqr(me) / 4 - covers * 3;
            if (score < bestScore) {
                bestScore = score;
                best = p;
            }
        }
        return best;
    }

    private boolean standable(BlockPos p, BlockPos target) {
        if (!solid(p.below()) || solid(p) || solid(p.above()) || !mc.level.getBlockState(p).getFluidState().isEmpty() || unreachable.contains(p.asLong())) return false;
        if (pending(p) && p.getY() <= target.getY() || pending(p.above()) && p.getY() + 1 <= target.getY()) return false;
        return !new AABB(p).minmax(new AABB(p.above())).intersects(new AABB(target));
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
                if (!inReach(Vec3.atCenterOf(c))) {
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
                if (!turnTowards(Vec3.atCenterOf(c))) return;
                HitResult r = mc.player.pick(mc.player.blockInteractionRange(), 1, false);
                if (!(r instanceof BlockHitResult hit) || !hit.getBlockPos().equals(c)) {
                    nudgeAim(Vec3.atCenterOf(c));
                    return;
                }
                mc.gameMode.useItemOn(mc.player, InteractionHand.MAIN_HAND, hit);
                mc.player.swing(InteractionHand.MAIN_HAND);
                restockStep = 2;
                restockTicks = 0;
                wait = actionDelay.get();
            }
            case 2 -> {
                if (!(mc.player.containerMenu instanceof ChestMenu h)) {
                    if (++restockTicks > 60) {
                        ChatUtils.warning("Schematic Builder: the material chest didn't open.");
                        stopBuild("stopped: can't open the material chest at " + c.toShortString() + ".");
                    }
                    return;
                }
                status = "taking materials";
                Map<Item, Integer> need = needs();
                int chestSlots = h.getRowCount() * 9;
                for (int i = 0; i < chestSlots; i++) {
                    ItemStack s = h.getSlot(i).getItem();
                    if (s.isEmpty() || need.getOrDefault(s.getItem(), 0) <= 0 || !roomFor(s)) continue;
                    mc.gameMode.handleContainerInput(h.containerId, i, 0, ContainerInput.QUICK_MOVE, mc.player);
                    wait = actionDelay.get() + jitter(2);
                    return;
                }
                missingItems.clear();
                for (Map.Entry<Item, Integer> e : need.entrySet()) if (e.getValue() > 0 && count(e.getKey()) == 0) missingItems.put(e.getKey(), e.getValue());
                mc.player.closeContainer();
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
                    missingItems.forEach((item, n) -> ChatUtils.warning("  %s x%d", item.getDefaultInstance().getHoverName().getString(), n));
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
            ItemStack inv = mc.player.getInventory().getItem(i);
            if (inv.isEmpty() || ItemStack.isSameItemSameComponents(inv, s) && inv.getCount() < inv.getMaxStackSize()) return true;
        }
        return false;
    }

    private int jitter(int n) {
        return speed.get() == Speed.VeryFast ? 0 : rng.nextInt(n);
    }

    private boolean turnTowards(Vec3 target) {
        float[] want = anglesTo(mc.player.getEyePosition(), target);
        return aim.turn(mc.player, want[0], want[1], turnSpeed.get().floatValue(), Math.abs(want[1]) > 80);
    }

    private void nudgeAim(Vec3 target) {
        float[] want = anglesTo(mc.player.getEyePosition(), target);
        mc.player.setYRot(want[0]);
        mc.player.setXRot(want[1]);
    }

    @EventHandler
    private void onLeftClick(StartBreakingBlockEvent event) {
        if (mode != Mode.PICKING) return;
        event.cancel();
        origin.set(event.blockPos.above());
        mode = Mode.IDLE;
        ChatUtils.info("Schematic Builder: placed at %s. Press Start building when ready.", origin.get().toShortString());
    }

    private final List<Map.Entry<Item, Integer>> needList = new ArrayList<>();
    private int currentLayer = Integer.MIN_VALUE;

    private void refreshPreview() {
        previewList.clear();
        needList.clear();
        if (!isActive() || targets.isEmpty()) return;
        Map<Item, Integer> need = new LinkedHashMap<>();
        currentLayer = Integer.MAX_VALUE;
        for (Target t : targets) {
            if (done(t) || skipped.contains(t.pos().asLong())) continue;
            need.merge(t.item(), 1, Integer::sum);
            currentLayer = Math.min(currentLayer, t.pos().getY());
        }
        needList.addAll(need.entrySet());
        needList.sort((a, b) -> b.getValue() - a.getValue());
        if (!preview.get()) return;
        BlockPos me = mc.player.blockPosition();
        int r = previewRange.get();
        for (Target t : targets) {
            if (Math.abs(t.pos().getX() - me.getX()) > r || Math.abs(t.pos().getY() - me.getY()) > r || Math.abs(t.pos().getZ() - me.getZ()) > r) continue;
            if (layerView.get() == LayerView.Current && t.pos().getY() != currentLayer) continue;
            if (layerView.get() == LayerView.UpToCurrent && t.pos().getY() > currentLayer) continue;
            if (done(t)) continue;
            previewList.add(t);
        }
        if (previewList.size() > maxBlocks.get()) {
            previewList.sort(Comparator.comparingDouble(t -> t.pos().distSqr(me)));
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
            event.renderer.box(new AABB(o.getX(), o.getY(), o.getZ(), o.getX() + s.sizeX, o.getY() + s.sizeY, o.getZ() + s.sizeZ), edge, edge, ShapeMode.Lines, 0);
        }
        if (!preview.get()) return;
        SettingColor miss = missingColor.get(), wrong = wrongColor.get(), rot = wrongStateColor.get();
        SettingColor missLine = new SettingColor(miss.r, miss.g, miss.b, Math.min(255, miss.a * 3 + 40)), wrongLine = new SettingColor(wrong.r, wrong.g, wrong.b, 220);
        SettingColor rotLine = new SettingColor(rot.r, rot.g, rot.b, 230), arrowColor = new SettingColor(255, 255, 255, 200);
        for (Target t : previewList) {
            BlockState here = mc.level.getBlockState(t.pos());
            boolean sameBlock = here.getBlock() == t.state().getBlock();
            boolean isWrong = !here.isAir() && !here.canBeReplaced() && !sameBlock;
            SettingColor fill = isWrong ? wrong : sameBlock ? rot : miss, line = isWrong ? wrongLine : sameBlock ? rotLine : missLine;
            // show the real shape (slabs, stairs, fences...) instead of a full cube where it has one
            AABB shape = shapeOf(t);
            event.renderer.box(shape, fill, line, ShapeMode.Both, 0);
            if (arrows.get() && !isWrong) drawFacing(event, t);
        }
        SettingColor sup = supportColor.get(), supLine = new SettingColor(sup.r, sup.g, sup.b, 230);
        for (BlockPos p : supports) event.renderer.box(p, sup, supLine, ShapeMode.Both, 0);
        for (BlockPos p : supportsToPlace) event.renderer.box(p, sup, supLine, ShapeMode.Lines, 0);
        if (current != null) event.renderer.box(current.pos(), new SettingColor(255, 255, 255, 60), new SettingColor(255, 255, 255, 255), ShapeMode.Both, 0);
    }

    private AABB shapeOf(Target t) {
        var outline = t.state().getShape(mc.level, t.pos());
        if (outline.isEmpty()) return new AABB(t.pos());
        AABB b = outline.bounds();
        if (b.getXsize() < 0.05 || b.getYsize() < 0.05 || b.getZsize() < 0.05) return new AABB(t.pos());
        return b.move(t.pos());
    }

    private static Direction facingOf(BlockState s) {
        if (s.hasProperty(BlockStateProperties.FACING)) return s.getValue(BlockStateProperties.FACING);
        if (s.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) return s.getValue(BlockStateProperties.HORIZONTAL_FACING);
        if (s.hasProperty(BlockStateProperties.FACING_HOPPER)) return s.getValue(BlockStateProperties.FACING_HOPPER);
        return null;
    }

    private void drawFacing(Render3DEvent event, Target t) {
        Vec3 c = Vec3.atCenterOf(t.pos());
        Direction d = facingOf(t.state());
        if (d == null && t.state().hasProperty(BlockStateProperties.AXIS)) {
            Vec3 a = Vec3.atLowerCornerOf(Direction.fromAxisAndDirection(t.state().getValue(BlockStateProperties.AXIS), Direction.AxisDirection.POSITIVE).getUnitVec3i()).scale(0.42);
            event.renderer.line(c.x - a.x, c.y - a.y, c.z - a.z, c.x + a.x, c.y + a.y, c.z + a.z, new SettingColor(255, 255, 255, 200));
            return;
        }
        if (d == null) return;
        Vec3 v = Vec3.atLowerCornerOf(d.getUnitVec3i());
        Vec3 tip = c.add(v.scale(0.42)), tail = c.subtract(v.scale(0.3));
        Vec3 side = d.getAxis() == Direction.Axis.Y ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0).cross(v);
        SettingColor col = new SettingColor(255, 255, 255, 220);
        event.renderer.line(tail.x, tail.y, tail.z, tip.x, tip.y, tip.z, col);
        for (int k = -1; k <= 1; k += 2) {
            Vec3 wing = tip.subtract(v.scale(0.18)).add(side.scale(0.14 * k));
            event.renderer.line(tip.x, tip.y, tip.z, wing.x, wing.y, wing.z, col);
        }
    }

    private String describe(BlockState s) {
        StringBuilder sb = new StringBuilder(s.getBlock().getName().getString());
        List<String> props = new ArrayList<>();
        for (Property<?> p : s.getProperties()) if (!FREE_PROPS.contains(p.getName())) props.add(p.getName() + "=" + s.getValue(p));
        if (!props.isEmpty()) sb.append(" [").append(String.join(", ", props)).append("]");
        return sb.toString();
    }

    private String lookLine() {
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) return null;
        for (BlockPos p : new BlockPos[] {hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection())}) {
            Target t = byPos.get(p.asLong());
            if (t == null) continue;
            BlockState here = mc.level.getBlockState(p);
            String state = done(t) ? "done" : here.isAir() || here.canBeReplaced() ? "missing" : here.getBlock() == t.state().getBlock() ? "wrong rotation (is " + describe(here) + ")" : "wrong block (is " + here.getBlock().getName().getString() + ")";
            return "  looking at: " + describe(t.state()) + " - " + state;
        }
        return null;
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!isActive() || !hud.get() || mc.options.hideGui) return;
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
            if (preview.get() && lookInfo.get()) {
                String look = lookLine();
                if (look != null) lines.add(look);
            }
            if (materials.get() && !needList.isEmpty()) {
                lines.add("  still to place (you have):");
                int i = 0;
                for (Map.Entry<Item, Integer> e : needList) {
                    if (i++ == 4) {
                        lines.add("    ... and " + (needList.size() - 4) + " more");
                        break;
                    }
                    int have = count(e.getKey());
                    lines.add(String.format("    %s  %d  (%d)%s", e.getKey().getDefaultInstance().getHoverName().getString(), e.getValue(), have, have < e.getValue() ? "  - short " + (e.getValue() - have) : ""));
                }
            }
            if (layerView.get() != LayerView.All && currentLayer != Integer.MAX_VALUE && currentLayer != Integer.MIN_VALUE)
                lines.add("  showing layer y=" + currentLayer + (layerView.get() == LayerView.UpToCurrent ? " and below" : ""));
            int learned = com.example.addon.util.BuildMemory.lessons();
            if (learned > 0) lines.add("  learned from " + learned + " mistake(s) so far");
            if (!missingItems.isEmpty()) {
                StringBuilder sb = new StringBuilder("  missing: ");
                int i = 0;
                for (Map.Entry<Item, Integer> e : missingItems.entrySet()) {
                    if (i++ == 3) {
                        sb.append("...");
                        break;
                    }
                    sb.append(e.getKey().getDefaultInstance().getHoverName().getString()).append(" x").append(e.getValue()).append("  ");
                }
                lines.add(sb.toString());
            }
        }
        Automation.status(event.graphics, "Schematic Builder", lines, 0xFF8FD8FF);
    }
}
