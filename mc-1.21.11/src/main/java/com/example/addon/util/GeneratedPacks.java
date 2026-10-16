package com.example.addon.util;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.PackVersion;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GeneratedPacks {
    private GeneratedPacks() {
    }

    public static Path dir(String name) {
        return MinecraftClient.getInstance().getResourcePackDir().resolve(name);
    }

    public static Path create(String name, String description) throws IOException {
        Path pack = dir(name);
        Files.createDirectories(pack);
        PackVersion v = SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES);
        Files.writeString(pack.resolve("pack.mcmeta"), String.format(
            "{\"pack\":{\"description\":\"%s\",\"min_format\":[%d,%d],\"max_format\":[%d,%d]}}",
            description, v.major(), v.minor(), v.major(), v.minor()));
        return pack;
    }

    public static void setEnabled(String name, boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String id = "file/" + name;
        ResourcePackManager packs = mc.getResourcePackManager();
        packs.scanPacks();
        boolean changed = on ? packs.enable(id) : packs.disable(id);
        if (!changed) {
            if (on) safeReload();
            return;
        }
        if (on && !mc.options.resourcePacks.contains(id)) mc.options.resourcePacks.add(id);
        if (!on) mc.options.resourcePacks.remove(id);
        mc.options.write();
        safeReload();
    }

    public static void safeReload() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world == null || !mc.isRunning()) return;
        if (mc.getOverlay() == null) mc.reloadResources();
        else reloadQueued = true;
    }

    private static boolean reloadQueued;

    public static void tick() {
        if (reloadQueued && MinecraftClient.getInstance().getOverlay() == null) {
            reloadQueued = false;
            safeReload();
        }
    }
}
