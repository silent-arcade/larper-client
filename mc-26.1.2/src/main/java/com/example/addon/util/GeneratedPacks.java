package com.example.addon.util;

import net.minecraft.SharedConstants;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.metadata.pack.PackFormat;
import net.minecraft.server.packs.repository.PackRepository;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public final class GeneratedPacks {
    private GeneratedPacks() {
    }

    public static Path dir(String name) {
        return Minecraft.getInstance().getResourcePackDirectory().resolve(name);
    }

    public static Path create(String name, String description) throws IOException {
        Path pack = dir(name);
        Files.createDirectories(pack);
        PackFormat v = SharedConstants.getCurrentVersion().packVersion(PackType.CLIENT_RESOURCES);
        Files.writeString(pack.resolve("pack.mcmeta"), String.format(
            "{\"pack\":{\"description\":\"%s\",\"min_format\":[%d,%d],\"max_format\":[%d,%d]}}",
            description, v.major(), v.minor(), v.major(), v.minor()));
        return pack;
    }

    public static void setEnabled(String name, boolean on) {
        Minecraft mc = Minecraft.getInstance();
        String id = "file/" + name;
        PackRepository packs = mc.getResourcePackRepository();
        packs.reload();
        boolean changed = on ? packs.addPack(id) : packs.removePack(id);
        if (!changed) {
            if (on) safeReload();
            return;
        }
        if (on && !mc.options.resourcePacks.contains(id)) mc.options.resourcePacks.add(id);
        if (!on) mc.options.resourcePacks.remove(id);
        mc.options.save();
        safeReload();
    }

    public static void safeReload() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || !mc.isRunning()) return;
        if (mc.getOverlay() == null) mc.reloadResourcePacks();
        else reloadQueued = true;
    }

    private static boolean reloadQueued;

    public static void tick() {
        if (reloadQueued && Minecraft.getInstance().getOverlay() == null) {
            reloadQueued = false;
            safeReload();
        }
    }
}
