package com.example.addon.oresim;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.utils.Utils;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class Seeds {
    private static final Properties SAVED = new Properties();
    private static boolean loaded;

    private Seeds() {
    }

    public static Long get() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isIntegratedServerRunning() && mc.getServer() != null) return mc.getServer().getOverworld().getSeed();
        load();
        String s = SAVED.getProperty(Utils.getWorldName());
        return s == null ? null : Long.parseLong(s);
    }

    public static void set(String seed) {
        load();
        SAVED.setProperty(Utils.getWorldName(), Long.toString(toSeed(seed)));
        save();
    }

    public static boolean delete() {
        load();
        boolean had = SAVED.remove(Utils.getWorldName()) != null;
        save();
        return had;
    }

    private static long toSeed(String in) {
        try {
            return Long.parseLong(in.strip());
        } catch (NumberFormatException e) {
            return in.strip().hashCode();
        }
    }

    private static Path file() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve("larper-client").resolve("seeds.properties");
    }

    private static void load() {
        if (loaded) return;
        loaded = true;
        if (!Files.isRegularFile(file())) return;
        try (Reader r = Files.newBufferedReader(file())) {
            SAVED.load(r);
        } catch (IOException e) {
            AddonTemplate.LOG.warn("Couldn't read saved seeds: {}", e.toString());
        }
    }

    private static void save() {
        try {
            Files.createDirectories(file().getParent());
            try (Writer w = Files.newBufferedWriter(file())) {
                SAVED.store(w, "Larper Client OreSim seeds (server -> seed)");
            }
        } catch (IOException e) {
            AddonTemplate.LOG.warn("Couldn't save seeds: {}", e.toString());
        }
    }
}
