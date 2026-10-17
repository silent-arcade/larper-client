package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.oresim.Ore;
import com.example.addon.oresim.OrePredictor;
import com.example.addon.oresim.Seeds;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.render.RenderUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DebrisFinder extends Module {
    private enum Kind { CONFIRMED, SUSPECT, GONE }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> range = sg.add(new IntSetting.Builder()
        .name("chunk-range").description("Show debris this many chunks around you.").defaultValue(6).range(1, 16).sliderRange(1, 12).build());

    private final Setting<Boolean> showSuspect = sg.add(new BoolSetting.Builder()
        .name("show-suspect").description("Show spots where the seed puts debris but you see netherrack, basalt or blackstone instead (how anti-xray hides it).").defaultValue(true).build());

    private final Setting<Boolean> showConfirmed = sg.add(new BoolSetting.Builder()
        .name("show-confirmed").description("Show debris you can actually see is there.").defaultValue(true).build());

    private final Setting<SettingColor> confirmedColor = sg.add(new ColorSetting.Builder()
        .name("confirmed-color").description("Colour of confirmed debris.").defaultValue(new SettingColor(255, 70, 220, 90)).visible(showConfirmed::get).build());

    private final Setting<SettingColor> suspectColor = sg.add(new ColorSetting.Builder()
        .name("suspect-color").description("Colour of suspect debris.").defaultValue(new SettingColor(255, 170, 40, 70)).visible(showSuspect::get).build());

    private final Setting<Boolean> tracer = sg.add(new BoolSetting.Builder()
        .name("tracer").description("A line from your crosshair to the nearest debris.").defaultValue(true).build());

    private final Setting<Integer> report = sg.add(new IntSetting.Builder()
        .name("chat-report").description("Every this many seconds, a chat line with what it scanned (0 = off).").defaultValue(10).range(0, 120).sliderRange(0, 60).build());

    private final Map<Long, Set<BlockPos>> predicted = new HashMap<>();
    private Map<ResourceKey<Biome>, List<Ore>> netherOres;
    private Long seed;
    private int chunksScanned, sectionsRead, signatures, reportTicks;

    public DebrisFinder() {
        super(AddonTemplate.CATEGORY, "debris-finder", "Finds ancient debris from the world seed (set it with .seed), and checks each spot against what the server shows you.");
    }

    @Override
    public void onActivate() {
        seed = Seeds.get();
        if (seed == null) {
            error("No seed found. Set one with .seed <seed> (singleplayer worlds work automatically).");
            toggle();
            return;
        }
        netherOres = Ore.getRegistry(Dimension.Nether);
        predicted.clear();
        chunksScanned = sectionsRead = signatures = reportTicks = 0;
        if (PlayerUtils.getDimension() != Dimension.Nether) info("Go to the Nether - ancient debris only generates there.");
        for (ChunkAccess c : Utils.chunks(false)) scan(c);
    }

    @Override
    public void onDeactivate() {
        predicted.clear();
    }

    @EventHandler
    private void onChunk(ChunkDataEvent event) {
        scan(event.chunk());
    }

    private void scan(ChunkAccess chunk) {
        if (mc.level == null || seed == null || PlayerUtils.getDimension() != Dimension.Nether) return;
        long key = chunk.getPos().pack();
        if (predicted.containsKey(key)) return;
        Set<BlockPos> spots = new HashSet<>();
        Map<Ore, Set<Vec3>> all = OrePredictor.predict(mc.level, chunk, seed, netherOres, false);
        all.forEach((ore, set) -> {
            if (!ore.active.name.equals("Ancient Debris")) return;
            for (Vec3 v : set) spots.add(BlockPos.containing(v));
        });
        Set<Integer> sections = new HashSet<>();
        for (BlockPos p : spots) sections.add(p.getY() >> 4);
        predicted.put(key, spots);
        chunksScanned++;
        sectionsRead += sections.size();
        signatures += spots.size();
    }

    private Kind kind(BlockPos p) {
        BlockState s = mc.level.getBlockState(p);
        if (s.is(Blocks.ANCIENT_DEBRIS)) return Kind.CONFIRMED;
        if (s.is(Blocks.NETHERRACK) || s.is(Blocks.BASALT) || s.is(Blocks.BLACKSTONE)) return Kind.SUSPECT;
        return Kind.GONE;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.level == null) return;
        if (PlayerUtils.getDimension() != Dimension.Nether) {
            predicted.clear();
            return;
        }
        if (report.get() <= 0 || ++reportTicks < report.get() * 20) return;
        reportTicks = 0;
        int suspect = 0, confirmed = 0;
        for (Set<BlockPos> spots : predicted.values()) {
            boolean s = false, c = false;
            for (BlockPos p : spots) {
                Kind k = kind(p);
                if (k == Kind.CONFIRMED) c = true;
                else if (k == Kind.SUSPECT) s = true;
            }
            if (c) confirmed++;
            else if (s) suspect++;
        }
        info("last %ds: %d chunks, %d sections read, %d debris-signatures seen, %d suspect chunks, %d confirmed chunks",
            report.get(), chunksScanned, sectionsRead, signatures, suspect, confirmed);
        chunksScanned = sectionsRead = signatures = 0;
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || predicted.isEmpty()) return;
        ChunkPos me = mc.player.chunkPosition();
        int r = range.get();
        BlockPos nearest = null;
        double best = Double.MAX_VALUE;
        for (Map.Entry<Long, Set<BlockPos>> e : predicted.entrySet()) {
            ChunkPos cp = ChunkPos.unpack(e.getKey());
            if (Math.abs(cp.x() - me.x()) > r || Math.abs(cp.z() - me.z()) > r) continue;
            for (BlockPos p : e.getValue()) {
                Kind k = kind(p);
                if (k == Kind.GONE || k == Kind.CONFIRMED && !showConfirmed.get() || k == Kind.SUSPECT && !showSuspect.get()) continue;
                SettingColor c = k == Kind.CONFIRMED ? confirmedColor.get() : suspectColor.get();
                event.renderer.box(new AABB(p), c, new SettingColor(c.r, c.g, c.b, 255), k == Kind.CONFIRMED ? ShapeMode.Both : ShapeMode.Lines, 0);
                double d = p.distToCenterSqr(mc.player.getEyePosition());
                if (d < best) {
                    best = d;
                    nearest = p;
                }
            }
        }
        if (tracer.get() && nearest != null) {
            SettingColor c = kind(nearest) == Kind.CONFIRMED ? confirmedColor.get() : suspectColor.get();
            Vec3 to = Vec3.atCenterOf(nearest);
            event.renderer.line(RenderUtils.center.x, RenderUtils.center.y, RenderUtils.center.z, to.x, to.y, to.z, new SettingColor(c.r, c.g, c.b, 255));
        }
    }
}
