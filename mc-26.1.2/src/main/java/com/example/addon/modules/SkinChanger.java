package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.util.FileChoices;
import com.example.addon.util.FilePicker;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.renderer.texture.SkinTextureDownloader;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;

/**
 * Your own skin from a PNG file, or anyone's skin by username (looked up from Mojang). Only you see it: it changes how
 * your player looks in third person, your hand, and the inventory, nothing is sent to the server.
 */
public class SkinChanger extends Module {
    public enum Source { Username, File }
    public enum Arms { Auto, Classic, Slim }

    private static final String[] EXTENSIONS = {".png"};
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("larper-client", "skin_changer/current");
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Source> source = sg.add(new EnumSetting.Builder<Source>()
        .name("source").description("Username: copy a player's skin. File: a skin PNG from .minecraft/larper-client/skins.")
        .defaultValue(Source.Username).onChanged(v -> reloadSoon()).build());

    private final Setting<String> username = sg.add(new StringSetting.Builder()
        .name("username").description("Minecraft username whose skin you want.").defaultValue("Notch")
        .visible(() -> source.get() == Source.Username).onChanged(v -> reloadSoon()).build());

    private final Setting<String> file = sg.add(new ProvidedStringSetting.Builder()
        .name("file").description("Skin PNG (64x64 or old 64x32) from .minecraft/larper-client/skins.")
        .defaultValue(FileChoices.NONE).supplier(() -> FileChoices.list(folder(), currentFile(), EXTENSIONS))
        .visible(() -> source.get() == Source.File).onChanged(v -> reloadSoon()).build());

    private final Setting<Arms> arms = sg.add(new EnumSetting.Builder<Arms>()
        .name("arms").description("Arm width. Auto uses the account's model, or detects it from the file.")
        .defaultValue(Arms.Auto).onChanged(v -> reloadSoon()).build());

    private volatile ClientAsset.Texture skin; // null until loaded: you keep your normal skin
    private volatile PlayerModelType model = PlayerModelType.WIDE;
    private int reloadIn = -1, generation;

    public SkinChanger() {
        super(AddonTemplate.CATEGORY, "skin-changer", "Wear any skin: a PNG file or a player's username. Only you see it.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        if (source.get() != Source.File) return null;
        return FilePicker.buttons(theme, "Choose a skin", folder(), file, EXTENSIONS);
    }

    @Override
    public void onActivate() {
        reloadIn = 0;
    }

    @Override
    public void onDeactivate() {
        skin = null;
        reloadIn = -1;
    }

    /** Called from the player mixin: your skin with ours swapped in (cape and elytra kept), or null to leave it. */
    public PlayerSkin apply(PlayerSkin original) {
        ClientAsset.Texture s = skin;
        return s == null ? null : new PlayerSkin(s, original.cape(), original.elytra(), model, original.secure());
    }

    private void reloadSoon() {
        if (isActive()) reloadIn = 15; // wait until you stop typing before looking the name up
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (reloadIn < 0 || reloadIn-- > 0) return;
        load();
    }

    private void load() {
        int gen = ++generation; // a slow lookup that finishes after a newer one is ignored
        SkinTextureDownloader downloader = new SkinTextureDownloader(mc.getProxy(), mc.getTextureManager(), mc); // executor = where textures get registered: the render thread
        if (source.get() == Source.File) {
            if (!FileChoices.isFile(file.get())) return;
            Path path = folder().resolve(file.get()).normalize();
            if (!path.startsWith(folder()) || !Files.isRegularFile(path)) {
                ChatUtils.warning("Skin file %s not found.", file.get());
                return;
            }
            PlayerModelType m = arms.get() == Arms.Auto ? detect(path) : chosen();
            // The downloader reads the file when it exists (the URL is only used if it doesn't) and fixes old 64x32 skins
            finish(gen, downloader.downloadAndRegisterSkin(TEXTURE, path, "", true), m, file.get());
            return;
        }
        String name = username.get().trim();
        if (!USERNAME.matcher(name).matches()) {
            ChatUtils.warning("\"%s\" isn't a valid Minecraft username.", name);
            return;
        }
        CompletableFuture.supplyAsync(() -> lookup(name), Util.nonCriticalIoPool()).thenAccept(found -> {
            if (gen != generation) return;
            if (found == null) {
                mc.execute(() -> ChatUtils.warning("Couldn't find a skin for %s.", name));
                return;
            }
            Path cache = folder().resolve(".cache").resolve(found[0].substring(found[0].lastIndexOf('/') + 1));
            try {
                Files.createDirectories(cache.getParent());
            } catch (IOException ignored) {
            }
            PlayerModelType m = arms.get() == Arms.Auto ? (found[1] != null ? PlayerModelType.SLIM : PlayerModelType.WIDE) : chosen();
            finish(gen, downloader.downloadAndRegisterSkin(TEXTURE, cache, found[0], true), m, name);
        });
    }

    private void finish(int gen, CompletableFuture<ClientAsset.Texture> loading, PlayerModelType m, String what) {
        loading.whenComplete((asset, error) -> mc.execute(() -> {
            if (gen != generation || !isActive()) return;
            if (asset == null) {
                AddonTemplate.LOG.warn("Skin {} failed to load", what, error);
                ChatUtils.warning("Couldn't load the skin %s.", what);
                return;
            }
            model = m;
            skin = asset;
            ChatUtils.info("Skin changed to %s.", what);
        }));
    }

    /** {skin url, "slim" or null}, or null if there's no such player or skin. Runs off the render thread. */
    private static String[] lookup(String name) {
        try {
            JsonObject profile = get("https://api.mojang.com/users/profiles/minecraft/" + name);
            if (profile == null || !profile.has("id")) return null;
            JsonObject session = get("https://sessionserver.mojang.com/session/minecraft/profile/" + profile.get("id").getAsString());
            if (session == null) return null;
            for (var p : session.getAsJsonArray("properties")) {
                JsonObject prop = p.getAsJsonObject();
                if (!"textures".equals(prop.get("name").getAsString())) continue;
                String json = new String(Base64.getDecoder().decode(prop.get("value").getAsString()), StandardCharsets.UTF_8);
                JsonObject textures = JsonParser.parseString(json).getAsJsonObject().getAsJsonObject("textures");
                if (textures == null || !textures.has("SKIN")) return null; // default skin
                JsonObject s = textures.getAsJsonObject("SKIN");
                String url = s.get("url").getAsString();
                // Only fetch images from Mojang's own skin server
                if (!URI.create(url).getHost().equals("textures.minecraft.net")) return null;
                boolean slim = s.has("metadata") && "slim".equals(s.getAsJsonObject("metadata").get("model").getAsString());
                return new String[]{url.replace("http://", "https://"), slim ? "slim" : null};
            }
        } catch (Exception e) {
            AddonTemplate.LOG.warn("Skin lookup for {} failed", name, e);
        }
        return null;
    }

    private static JsonObject get(String url) throws IOException, InterruptedException {
        HttpResponse<String> r = HTTP.send(HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).GET().build(),
            HttpResponse.BodyHandlers.ofString());
        return r.statusCode() == 200 ? JsonParser.parseString(r.body()).getAsJsonObject() : null;
    }

    // Slim skins leave the outer column of each arm empty: pixel (54, 20) is see-through on a slim 64x64 skin
    private static PlayerModelType detect(Path path) {
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img != null && img.getWidth() == 64 && img.getHeight() == 64 && (img.getRGB(54, 20) >>> 24) == 0) return PlayerModelType.SLIM;
        } catch (IOException ignored) {
        }
        return PlayerModelType.WIDE;
    }

    private PlayerModelType chosen() {
        return arms.get() == Arms.Slim ? PlayerModelType.SLIM : PlayerModelType.WIDE;
    }

    private Path folder() {
        Path dir = mc.gameDirectory.toPath().resolve("larper-client").resolve("skins").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ChatUtils.warning("Couldn't create %s", dir);
        }
        return dir;
    }

    // The setting field isn't assigned yet while its own builder runs, so read it lazily
    private String currentFile() {
        return file == null ? null : file.get();
    }
}
