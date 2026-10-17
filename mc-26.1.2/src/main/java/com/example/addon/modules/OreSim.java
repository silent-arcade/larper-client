package com.example.addon.modules;

// Ported from Meteor Rejects (https://github.com/AntiCope/meteor-rejects), GPL-3.0.

import com.example.addon.AddonTemplate;
import com.example.addon.oresim.Ore;
import com.example.addon.oresim.OrePredictor;
import com.example.addon.oresim.Seeds;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.BlockUpdateEvent;
import meteordevelopment.meteorclient.events.world.ChunkDataEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.player.PlayerUtils;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class OreSim extends Module {
    private final Map<Long, Map<Ore, Set<Vec3>>> chunkRenderers = new ConcurrentHashMap<>();
    private Long worldSeed;
    private Map<ResourceKey<Biome>, List<Ore>> oreConfig;
    private Dimension lastDimension;

    public enum AirCheck { ON_LOAD, RECHECK, OFF }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> horizontalRadius = sgGeneral.add(new IntSetting.Builder()
        .name("chunk-range").description("Taxi cap distance of chunks being shown.")
        .defaultValue(5).min(1).sliderMax(10)
        .build());

    private final Setting<AirCheck> airCheck = sgGeneral.add(new EnumSetting.Builder<AirCheck>()
        .name("air-check-mode").description("Checks if there is air at a calculated ore pos.")
        .defaultValue(AirCheck.RECHECK)
        .build());

    public OreSim() {
        super(AddonTemplate.CATEGORY, "ore-sim", "Shows where ores generate, worked out from the world seed. From Meteor Rejects.");
        SettingGroup sgOres = settings.createGroup("Ores");
        Ore.oreSettings.forEach(sgOres::add);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || oreConfig == null || worldSeed == null) return;
        int chunkX = mc.player.chunkPosition().x();
        int chunkZ = mc.player.chunkPosition().z();
        int rangeVal = horizontalRadius.get();
        for (int range = 0; range <= rangeVal; range++) {
            for (int x = -range + chunkX; x <= range + chunkX; x++) renderChunk(x, chunkZ + range - rangeVal, event);
            for (int x = -range + 1 + chunkX; x < range + chunkX; x++) renderChunk(x, chunkZ - range + rangeVal + 1, event);
        }
    }

    private void renderChunk(int x, int z, Render3DEvent event) {
        Map<Ore, Set<Vec3>> chunk = chunkRenderers.get(ChunkPos.pack(x, z));
        if (chunk == null) return;
        for (Map.Entry<Ore, Set<Vec3>> ore : chunk.entrySet()) {
            if (!ore.getKey().active.get()) continue;
            for (Vec3 pos : ore.getValue()) {
                event.renderer.boxLines(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, ore.getKey().color, 0);
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (airCheck.get() != AirCheck.RECHECK || event.newState.canOcclude()) return;
        Map<Ore, Set<Vec3>> chunk = chunkRenderers.get(ChunkPos.pack(event.pos));
        if (chunk == null) return;
        Vec3 pos = Vec3.atLowerCornerOf(event.pos);
        for (Set<Vec3> ores : chunk.values()) ores.remove(pos);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.level == null) return;
        Dimension dim = PlayerUtils.getDimension();
        if (dim != lastDimension) {
            lastDimension = dim;
            reload();
        }
    }

    @Override
    public void onActivate() {
        if (Seeds.get() == null) {
            error("No seed found. Set one with .seed <seed> (singleplayer worlds work automatically).");
            toggle();
            return;
        }
        lastDimension = null;
        reload();
    }

    @Override
    public void onDeactivate() {
        chunkRenderers.clear();
        oreConfig = null;
    }

    public void reload() {
        if (!isActive() || mc.level == null) return;
        worldSeed = Seeds.get();
        chunkRenderers.clear();
        if (worldSeed == null) {
            oreConfig = null;
            return;
        }
        oreConfig = Ore.getRegistry(PlayerUtils.getDimension());
        for (ChunkAccess chunk : Utils.chunks(false)) doMathOnChunk(chunk);
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        doMathOnChunk(event.chunk());
    }

    private void doMathOnChunk(ChunkAccess chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkPos.pack();
        ClientLevel world = mc.level;
        if (oreConfig == null || worldSeed == null || chunkRenderers.containsKey(chunkKey) || world == null) return;

        Map<Ore, Set<Vec3>> h = OrePredictor.predict(world, chunk, worldSeed, oreConfig, airCheck.get() != AirCheck.OFF);
        chunkRenderers.put(chunkKey, h);
    }

}
