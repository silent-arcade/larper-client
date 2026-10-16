package com.example.addon.modules;

// Ported from Meteor Rejects (https://github.com/AntiCope/meteor-rejects, GPL-3.0), modules/OreSim.java,
// translated from Mojang mappings to Yarn. The Baritone option is left out (Larper Client doesn't ship Baritone).

import com.example.addon.AddonTemplate;
import com.example.addon.oresim.Ore;
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
import net.minecraft.client.world.ClientWorld;
import net.minecraft.registry.RegistryKey;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import net.minecraft.util.math.random.ChunkRandom;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.chunk.ChunkSection;
import net.minecraft.world.chunk.ChunkStatus;

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
    private final Map<Long, Map<Ore, Set<Vec3d>>> chunkRenderers = new ConcurrentHashMap<>();
    private Long worldSeed;
    private Map<RegistryKey<Biome>, List<Ore>> oreConfig;
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
        int chunkX = mc.player.getChunkPos().x;
        int chunkZ = mc.player.getChunkPos().z;
        int rangeVal = horizontalRadius.get();
        for (int range = 0; range <= rangeVal; range++) {
            for (int x = -range + chunkX; x <= range + chunkX; x++) renderChunk(x, chunkZ + range - rangeVal, event);
            for (int x = -range + 1 + chunkX; x < range + chunkX; x++) renderChunk(x, chunkZ - range + rangeVal + 1, event);
        }
    }

    private void renderChunk(int x, int z, Render3DEvent event) {
        Map<Ore, Set<Vec3d>> chunk = chunkRenderers.get(ChunkPos.toLong(x, z));
        if (chunk == null) return;
        for (Map.Entry<Ore, Set<Vec3d>> ore : chunk.entrySet()) {
            if (!ore.getKey().active.get()) continue;
            for (Vec3d pos : ore.getValue()) {
                event.renderer.boxLines(pos.x, pos.y, pos.z, pos.x + 1, pos.y + 1, pos.z + 1, ore.getKey().color, 0);
            }
        }
    }

    @EventHandler
    private void onBlockUpdate(BlockUpdateEvent event) {
        if (airCheck.get() != AirCheck.RECHECK || event.newState.isOpaque()) return;
        Map<Ore, Set<Vec3d>> chunk = chunkRenderers.get(ChunkPos.toLong(event.pos));
        if (chunk == null) return;
        Vec3d pos = Vec3d.of(event.pos);
        for (Set<Vec3d> ores : chunk.values()) ores.remove(pos);
    }

    // The ore table depends on the dimension; reload when it changes (respawn, portals)
    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.world == null) return;
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

    /** Recomputes everything (new seed, new dimension). */
    public void reload() {
        if (!isActive() || mc.world == null) return;
        worldSeed = Seeds.get();
        chunkRenderers.clear();
        if (worldSeed == null) {
            oreConfig = null;
            return;
        }
        oreConfig = Ore.getRegistry(PlayerUtils.getDimension());
        for (Chunk chunk : Utils.chunks(false)) doMathOnChunk(chunk);
    }

    @EventHandler
    private void onChunkData(ChunkDataEvent event) {
        doMathOnChunk(event.chunk());
    }

    private void doMathOnChunk(Chunk chunk) {
        ChunkPos chunkPos = chunk.getPos();
        long chunkKey = chunkPos.toLong();
        ClientWorld world = mc.world;
        if (oreConfig == null || worldSeed == null || chunkRenderers.containsKey(chunkKey) || world == null) return;

        Set<RegistryKey<Biome>> biomes = new HashSet<>();
        ChunkPos.stream(chunkPos, 1).forEach(near -> {
            Chunk c = world.getChunk(near.x, near.z, ChunkStatus.BIOMES, false);
            if (c == null) return;
            for (ChunkSection section : c.getSectionArray()) {
                section.getBiomeContainer().forEachValue(entry -> entry.getKey().ifPresent(biomes::add));
            }
        });
        Set<Ore> oreSet = biomes.stream().flatMap(b -> getDefaultOres(b).stream()).collect(Collectors.toSet());

        int chunkX = chunkPos.x << 4;
        int chunkZ = chunkPos.z << 4;
        ChunkRandom random = new ChunkRandom(ChunkRandom.RandomProvider.XOROSHIRO.create(0));
        long populationSeed = random.setPopulationSeed(worldSeed, chunkX, chunkZ);
        HashMap<Ore, Set<Vec3d>> h = new HashMap<>();

        for (Ore ore : oreSet) {
            Set<Vec3d> ores = ConcurrentHashMap.newKeySet();
            random.setDecoratorSeed(populationSeed, ore.index, ore.step);
            int repeat = ore.count.get(random);

            for (int i = 0; i < repeat; i++) {
                if (ore.rarity != 1F && random.nextFloat() >= 1 / ore.rarity) continue;

                int x = random.nextInt(16) + chunkX;
                int z = random.nextInt(16) + chunkZ;
                int y = ore.heightProvider.get(random, ore.heightContext);
                BlockPos origin = new BlockPos(x, y, z);

                RegistryKey<Biome> biome = chunk.getBiomeForNoiseGen(x, y, z).getKey().orElse(null);
                if (biome == null || !getDefaultOres(biome).contains(ore)) continue;

                if (ore.scattered) ores.addAll(generateHidden(world, random, origin, ore.size));
                else ores.addAll(generateNormal(world, random, origin, ore.size, ore.discardOnAirChance));
            }
            if (!ores.isEmpty()) h.put(ore, ores);
        }
        chunkRenderers.put(chunkKey, h);
    }

    private List<Ore> getDefaultOres(RegistryKey<Biome> biome) {
        List<Ore> ores = oreConfig.get(biome);
        return ores != null ? ores : oreConfig.values().stream().findAny().orElse(List.of());
    }

    // ====================================
    // Mojang code (ore vein generation)
    // ====================================

    private ArrayList<Vec3d> generateNormal(ClientWorld world, ChunkRandom random, BlockPos blockPos, int veinSize, float discardOnAir) {
        float f = random.nextFloat() * 3.1415927F;
        float g = (float) veinSize / 8.0F;
        int i = MathHelper.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = (double) blockPos.getX() + Math.sin(f) * (double) g;
        double e = (double) blockPos.getX() - Math.sin(f) * (double) g;
        double h = (double) blockPos.getZ() + Math.cos(f) * (double) g;
        double j = (double) blockPos.getZ() - Math.cos(f) * (double) g;
        double l = (blockPos.getY() + random.nextInt(3) - 2);
        double m = (blockPos.getY() + random.nextInt(3) - 2);
        int n = blockPos.getX() - MathHelper.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - MathHelper.ceil(g) - i;
        int q = 2 * (MathHelper.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= world.getTopY(Heightmap.Type.MOTION_BLOCKING, s, t)) {
                    return generateVeinPart(world, random, veinSize, d, e, h, j, l, m, n, o, p, q, r, discardOnAir);
                }
            }
        }
        return new ArrayList<>();
    }

    private ArrayList<Vec3d> generateVeinPart(ClientWorld world, ChunkRandom random, int veinSize, double startX, double endX, double startZ, double endZ, double startY, double endY, int x, int y, int z, int size, int i, float discardOnAir) {
        BitSet bitSet = new BitSet(size * i * size);
        BlockPos.Mutable mutable = new BlockPos.Mutable();
        double[] ds = new double[veinSize * 4];
        ArrayList<Vec3d> poses = new ArrayList<>();

        int n;
        double p, q, r, s;
        for (n = 0; n < veinSize; ++n) {
            float f = (float) n / (float) veinSize;
            p = MathHelper.lerp(f, startX, endX);
            q = MathHelper.lerp(f, startY, endY);
            r = MathHelper.lerp(f, startZ, endZ);
            s = random.nextDouble() * (double) veinSize / 16.0D;
            double m = ((double) (MathHelper.sin(3.1415927F * f) + 1.0F) * s + 1.0D) / 2.0D;
            ds[n * 4] = p;
            ds[n * 4 + 1] = q;
            ds[n * 4 + 2] = r;
            ds[n * 4 + 3] = m;
        }

        for (n = 0; n < veinSize - 1; ++n) {
            if (ds[n * 4 + 3] <= 0.0D) continue;
            for (int o = n + 1; o < veinSize; ++o) {
                if (ds[o * 4 + 3] <= 0.0D) continue;
                p = ds[n * 4] - ds[o * 4];
                q = ds[n * 4 + 1] - ds[o * 4 + 1];
                r = ds[n * 4 + 2] - ds[o * 4 + 2];
                s = ds[n * 4 + 3] - ds[o * 4 + 3];
                if (s * s > p * p + q * q + r * r) {
                    if (s > 0.0D) ds[o * 4 + 3] = -1.0D;
                    else ds[n * 4 + 3] = -1.0D;
                }
            }
        }

        for (n = 0; n < veinSize; ++n) {
            double u = ds[n * 4 + 3];
            if (u < 0.0D) continue;
            double v = ds[n * 4];
            double w = ds[n * 4 + 1];
            double aa = ds[n * 4 + 2];
            int ab = Math.max(MathHelper.floor(v - u), x);
            int ac = Math.max(MathHelper.floor(w - u), y);
            int ad = Math.max(MathHelper.floor(aa - u), z);
            int ae = Math.max(MathHelper.floor(v + u), ab);
            int af = Math.max(MathHelper.floor(w + u), ac);
            int ag = Math.max(MathHelper.floor(aa + u), ad);

            for (int ah = ab; ah <= ae; ++ah) {
                double ai = ((double) ah + 0.5D - v) / u;
                if (ai * ai >= 1.0D) continue;
                for (int aj = ac; aj <= af; ++aj) {
                    double ak = ((double) aj + 0.5D - w) / u;
                    if (ai * ai + ak * ak >= 1.0D) continue;
                    for (int al = ad; al <= ag; ++al) {
                        double am = ((double) al + 0.5D - aa) / u;
                        if (ai * ai + ak * ak + am * am >= 1.0D) continue;
                        int an = ah - x + (aj - y) * size + (al - z) * size * i;
                        if (bitSet.get(an)) continue;
                        bitSet.set(an);
                        mutable.set(ah, aj, al);
                        if (aj >= -64 && aj < 320 && (airCheck.get() == AirCheck.OFF || world.getBlockState(mutable).isOpaque())) {
                            if (shouldPlace(world, mutable, discardOnAir, random)) poses.add(new Vec3d(ah, aj, al));
                        }
                    }
                }
            }
        }
        return poses;
    }

    private boolean shouldPlace(ClientWorld world, BlockPos orePos, float discardOnAir, ChunkRandom random) {
        if (discardOnAir == 0F || (discardOnAir != 1F && random.nextFloat() >= discardOnAir)) return true;
        for (Direction direction : Direction.values()) {
            if (!world.getBlockState(orePos.add(direction.getVector())).isOpaque() && discardOnAir != 1F) return false;
        }
        return true;
    }

    private ArrayList<Vec3d> generateHidden(ClientWorld world, ChunkRandom random, BlockPos blockPos, int size) {
        ArrayList<Vec3d> poses = new ArrayList<>();
        int i = random.nextInt(size + 1);
        for (int j = 0; j < i; ++j) {
            size = Math.min(j, 7);
            int x = randomCoord(random, size) + blockPos.getX();
            int y = randomCoord(random, size) + blockPos.getY();
            int z = randomCoord(random, size) + blockPos.getZ();
            BlockPos pos = new BlockPos(x, y, z);
            if (airCheck.get() == AirCheck.OFF || world.getBlockState(pos).isOpaque()) {
                if (shouldPlace(world, pos, 1F, random)) poses.add(new Vec3d(x, y, z));
            }
        }
        return poses;
    }

    private int randomCoord(ChunkRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) size);
    }
}
