package com.example.addon.util;

import net.minecraft.SharedConstants;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.PackVersion;
import net.minecraft.resource.ResourcePackManager;
import net.minecraft.resource.ResourceType;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Small resource packs this addon writes into the resourcepacks folder (custom sky, custom totem) and switches on/off. */
public final class GeneratedPacks {
    private GeneratedPacks() {
    }

    public static Path dir(String name) {
        return MinecraftClient.getInstance().getResourcePackDir().resolve(name);
    }

    /** Creates the pack folder with a pack.mcmeta for this game version. */
    public static Path create(String name, String description) throws IOException {
        Path pack = dir(name);
        Files.createDirectories(pack);
        PackVersion v = SharedConstants.getGameVersion().packVersion(ResourceType.CLIENT_RESOURCES);
        Files.writeString(pack.resolve("pack.mcmeta"), String.format(
            "{\"pack\":{\"description\":\"%s\",\"min_format\":[%d,%d],\"max_format\":[%d,%d]}}",
            description, v.major(), v.minor(), v.major(), v.minor()));
        return pack;
    }

    /** Turns the pack on or off, saves the pack list and reloads resources (when it's safe). */
    public static void setEnabled(String name, boolean on) {
        MinecraftClient mc = MinecraftClient.getInstance();
        String id = "file/" + name;
        ResourcePackManager packs = mc.getResourcePackManager();
        packs.scanPacks();
        boolean changed = on ? packs.enable(id) : packs.disable(id);
        if (!changed) {
            // Already on from last session: its files were just rewritten, so reload anyway or the old image stays
            if (on) safeReload();
            return;
        }
        // Save the pack list ourselves: GameOptions.refreshResourcePacks() also reloads resources on its own
        if (on && !mc.options.resourcePacks.contains(id)) mc.options.resourcePacks.add(id);
        if (!on) mc.options.resourcePacks.remove(id);
        mc.options.write();
        safeReload();
    }

    // Reloading while leaving a world (or while another reload runs) leaves the game stuck on the Mojang
    // loading screen. Outside a world the pack change is saved and simply applies on the next resource load.
    public static void safeReload() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.world != null && mc.isRunning() && mc.getOverlay() == null) mc.reloadResources();
    }
}
