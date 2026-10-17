package com.example.addon.oresim;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.phys.Vec3;

public final class OrePredictor {
    private OrePredictor() {
    }

    // airCheck: drop spots that aren't solid on the client (what Ore Sim shows). Off = the raw seed prediction.
    public static Map<Ore, Set<Vec3>> predict(ClientLevel world, ChunkAccess chunk, long worldSeed, Map<ResourceKey<Biome>, List<Ore>> oreConfig, boolean airCheck) {
        ChunkPos chunkPos = chunk.getPos();
        Set<ResourceKey<Biome>> biomes = new HashSet<>();
        ChunkPos.rangeClosed(chunkPos, 1).forEach(near -> {
            ChunkAccess c = world.getChunk(near.x(), near.z(), ChunkStatus.BIOMES, false);
            if (c == null) return;
            for (LevelChunkSection section : c.getSections()) {
                section.getBiomes().getAll(entry -> entry.unwrapKey().ifPresent(biomes::add));
            }
        });
        Set<Ore> oreSet = biomes.stream().flatMap(b -> ores(oreConfig, b).stream()).collect(Collectors.toSet());

        int chunkX = chunkPos.x() << 4;
        int chunkZ = chunkPos.z() << 4;
        WorldgenRandom random = new WorldgenRandom(WorldgenRandom.Algorithm.XOROSHIRO.newInstance(0));
        long populationSeed = random.setDecorationSeed(worldSeed, chunkX, chunkZ);
        Map<Ore, Set<Vec3>> h = new HashMap<>();

        for (Ore ore : oreSet) {
            Set<Vec3> ores = ConcurrentHashMap.newKeySet();
            random.setFeatureSeed(populationSeed, ore.index, ore.step);
            int repeat = ore.count.sample(random);

            for (int i = 0; i < repeat; i++) {
                if (ore.rarity != 1F && random.nextFloat() >= 1 / ore.rarity) continue;

                int x = random.nextInt(16) + chunkX;
                int z = random.nextInt(16) + chunkZ;
                int y = ore.heightProvider.sample(random, ore.heightContext);
                BlockPos origin = new BlockPos(x, y, z);

                ResourceKey<Biome> biome = chunk.getNoiseBiome(x, y, z).unwrapKey().orElse(null);
                if (biome == null || !ores(oreConfig, biome).contains(ore)) continue;

                if (ore.scattered) ores.addAll(generateHidden(world, random, origin, ore.size, airCheck));
                else ores.addAll(generateNormal(world, random, origin, ore.size, ore.discardOnAirChance, airCheck));
            }
            if (!ores.isEmpty()) h.put(ore, ores);
        }
        return h;
    }

    private static List<Ore> ores(Map<ResourceKey<Biome>, List<Ore>> oreConfig, ResourceKey<Biome> biome) {
        List<Ore> ores = oreConfig.get(biome);
        return ores != null ? ores : oreConfig.values().stream().findAny().orElse(List.of());
    }

    private static ArrayList<Vec3> generateNormal(ClientLevel world, WorldgenRandom random, BlockPos blockPos, int veinSize, float discardOnAir, boolean airCheck) {
        float f = random.nextFloat() * 3.1415927F;
        float g = (float) veinSize / 8.0F;
        int i = Mth.ceil(((float) veinSize / 16.0F * 2.0F + 1.0F) / 2.0F);
        double d = (double) blockPos.getX() + Math.sin(f) * (double) g;
        double e = (double) blockPos.getX() - Math.sin(f) * (double) g;
        double h = (double) blockPos.getZ() + Math.cos(f) * (double) g;
        double j = (double) blockPos.getZ() - Math.cos(f) * (double) g;
        double l = (blockPos.getY() + random.nextInt(3) - 2);
        double m = (blockPos.getY() + random.nextInt(3) - 2);
        int n = blockPos.getX() - Mth.ceil(g) - i;
        int o = blockPos.getY() - 2 - i;
        int p = blockPos.getZ() - Mth.ceil(g) - i;
        int q = 2 * (Mth.ceil(g) + i);
        int r = 2 * (2 + i);

        for (int s = n; s <= n + q; ++s) {
            for (int t = p; t <= p + q; ++t) {
                if (o <= world.getHeight(Heightmap.Types.MOTION_BLOCKING, s, t)) {
                    return generateVeinPart(world, random, veinSize, d, e, h, j, l, m, n, o, p, q, r, discardOnAir, airCheck);
                }
            }
        }
        return new ArrayList<>();
    }

    private static ArrayList<Vec3> generateVeinPart(ClientLevel world, WorldgenRandom random, int veinSize, double startX, double endX, double startZ, double endZ, double startY, double endY, int x, int y, int z, int size, int i, float discardOnAir, boolean airCheck) {
        BitSet bitSet = new BitSet(size * i * size);
        BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();
        double[] ds = new double[veinSize * 4];
        ArrayList<Vec3> poses = new ArrayList<>();

        int n;
        double p, q, r, s;
        for (n = 0; n < veinSize; ++n) {
            float f = (float) n / (float) veinSize;
            p = Mth.lerp(f, startX, endX);
            q = Mth.lerp(f, startY, endY);
            r = Mth.lerp(f, startZ, endZ);
            s = random.nextDouble() * (double) veinSize / 16.0D;
            double m = ((double) (Mth.sin(3.1415927F * f) + 1.0F) * s + 1.0D) / 2.0D;
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
            int ab = Math.max(Mth.floor(v - u), x);
            int ac = Math.max(Mth.floor(w - u), y);
            int ad = Math.max(Mth.floor(aa - u), z);
            int ae = Math.max(Mth.floor(v + u), ab);
            int af = Math.max(Mth.floor(w + u), ac);
            int ag = Math.max(Mth.floor(aa + u), ad);

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
                        if (aj >= -64 && aj < 320 && (!airCheck || world.getBlockState(mutable).canOcclude())) {
                            if (shouldPlace(world, mutable, discardOnAir, random)) poses.add(new Vec3(ah, aj, al));
                        }
                    }
                }
            }
        }
        return poses;
    }

    private static boolean shouldPlace(ClientLevel world, BlockPos orePos, float discardOnAir, WorldgenRandom random) {
        if (discardOnAir == 0F || (discardOnAir != 1F && random.nextFloat() >= discardOnAir)) return true;
        for (Direction direction : Direction.values()) {
            if (!world.getBlockState(orePos.offset(direction.getUnitVec3i())).canOcclude() && discardOnAir != 1F) return false;
        }
        return true;
    }

    private static ArrayList<Vec3> generateHidden(ClientLevel world, WorldgenRandom random, BlockPos blockPos, int size, boolean airCheck) {
        ArrayList<Vec3> poses = new ArrayList<>();
        int i = random.nextInt(size + 1);
        for (int j = 0; j < i; ++j) {
            size = Math.min(j, 7);
            int x = randomCoord(random, size) + blockPos.getX();
            int y = randomCoord(random, size) + blockPos.getY();
            int z = randomCoord(random, size) + blockPos.getZ();
            BlockPos pos = new BlockPos(x, y, z);
            if (!airCheck || world.getBlockState(pos).canOcclude()) {
                if (shouldPlace(world, pos, 1F, random)) poses.add(new Vec3(x, y, z));
            }
        }
        return poses;
    }

    private static int randomCoord(WorldgenRandom random, int size) {
        return Math.round((random.nextFloat() - random.nextFloat()) * (float) size);
    }
}
