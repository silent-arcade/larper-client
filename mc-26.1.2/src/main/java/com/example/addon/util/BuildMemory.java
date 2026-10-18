package com.example.addon.util;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.Block;

import static meteordevelopment.meteorclient.MeteorClient.mc;

// What the Schematic Builder learned from its mistakes, kept in larper-client/schematic-memory.json so it remembers across games
public final class BuildMemory {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static final class Data {
        Set<String> sneakToClick = new TreeSet<>();
        Map<String, Map<String, Integer>> sides = new TreeMap<>();
    }

    private static Data data;

    private BuildMemory() {
    }

    private static Path file() {
        return mc.gameDirectory.toPath().resolve("larper-client").resolve("schematic-memory.json");
    }

    private static Data data() {
        if (data != null) return data;
        try {
            data = Files.isRegularFile(file()) ? GSON.fromJson(Files.readString(file()), Data.class) : null;
        } catch (Exception e) {
            data = null;
        }
        if (data == null) data = new Data();
        if (data.sneakToClick == null) data.sneakToClick = new TreeSet<>();
        if (data.sides == null) data.sides = new TreeMap<>();
        return data;
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            Files.writeString(file(), GSON.toJson(data()));
        } catch (IOException ignored) {
        }
    }

    private static String id(Block b) {
        return BuiltInRegistries.BLOCK.getKey(b).toString();
    }

    public static int lessons() {
        int n = data().sneakToClick.size();
        for (Map<String, Integer> m : data().sides.values()) for (int v : m.values()) if (v > 0) n++;
        return n;
    }

    public static boolean needsSneak(Block clicked) {
        return data().sneakToClick.contains(id(clicked));
    }

    // returns true the first time it learns this
    public static boolean learnSneak(Block clicked) {
        boolean added = data().sneakToClick.add(id(clicked));
        if (added) save();
        return added;
    }

    // positive = that face has gone wrong for this block before, negative = it has worked
    public static int sideScore(Block placed, Direction side) {
        Map<String, Integer> m = data().sides.get(id(placed));
        return m == null ? 0 : m.getOrDefault(side.getSerializedName(), 0);
    }

    // returns true when this is a new mistake for that block and face
    public static boolean recordSide(Block placed, Direction side, boolean worked) {
        Map<String, Integer> m = data().sides.computeIfAbsent(id(placed), k -> new TreeMap<>());
        int before = m.getOrDefault(side.getSerializedName(), 0);
        int after = Math.max(-10, Math.min(20, before + (worked ? -1 : 4)));
        if (after == before) return false;
        m.put(side.getSerializedName(), after);
        save();
        return !worked && before <= 0;
    }
}
