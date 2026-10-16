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
import net.minecraft.client.texture.PlayerSkinTextureDownloader;
import net.minecraft.entity.player.PlayerSkinType;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.util.AssetInfo;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

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

public class SkinChanger extends Module {
    public enum Source { Username, File }
    public enum Arms { Auto, Classic, Slim }

    private static final String[] EXTENSIONS = {".png"};
    private static final Pattern USERNAME = Pattern.compile("[A-Za-z0-9_]{1,16}");
    private static final Identifier TEXTURE = Identifier.of("larper-client", "skin_changer/current");
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

    private volatile AssetInfo.TextureAsset skin;
    private volatile PlayerSkinType model = PlayerSkinType.WIDE;
    private int reloadIn = -1, generation;

    public SkinChanger() {
        super(AddonTemplate.VISUAL, "skin-changer", "Wear any skin: a PNG file or a player's username. Only you see it.");
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

    public SkinTextures apply(SkinTextures original) {
        AssetInfo.TextureAsset s = skin;
        return s == null ? null : new SkinTextures(s, original.cape(), original.elytra(), model, original.secure());
    }

    private void reloadSoon() {
        if (isActive()) reloadIn = 15;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (reloadIn < 0 || reloadIn-- > 0) return;
        load();
    }

    private void load() {
        int gen = ++generation;
        PlayerSkinTextureDownloader downloader = new PlayerSkinTextureDownloader(mc.getNetworkProxy(), mc.getTextureManager(), mc);
        if (source.get() == Source.File) {
            if (!FileChoices.isFile(file.get())) return;
            Path path = folder().resolve(file.get()).normalize();
            if (!path.startsWith(folder()) || !Files.isRegularFile(path)) {
                ChatUtils.warning("Skin file %s not found.", file.get());
                return;
            }
            PlayerSkinType m = arms.get() == Arms.Auto ? detect(path) : chosen();
            finish(gen, downloader.downloadAndRegisterTexture(TEXTURE, path, "", true), m, file.get());
            return;
        }
        String name = username.get().trim();
        if (!USERNAME.matcher(name).matches()) {
            ChatUtils.warning("\"%s\" isn't a valid Minecraft username.", name);
            return;
        }
        CompletableFuture.supplyAsync(() -> lookup(name), Util.getDownloadWorkerExecutor()).thenAccept(found -> {
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
            PlayerSkinType m = arms.get() == Arms.Auto ? (found[1] != null ? PlayerSkinType.SLIM : PlayerSkinType.WIDE) : chosen();
            finish(gen, downloader.downloadAndRegisterTexture(TEXTURE, cache, found[0], true), m, name);
        });
    }

    private void finish(int gen, CompletableFuture<AssetInfo.TextureAsset> loading, PlayerSkinType m, String what) {
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
                if (textures == null || !textures.has("SKIN")) return null;
                JsonObject s = textures.getAsJsonObject("SKIN");
                String url = s.get("url").getAsString();
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

    private static PlayerSkinType detect(Path path) {
        try {
            BufferedImage img = ImageIO.read(path.toFile());
            if (img != null && img.getWidth() == 64 && img.getHeight() == 64 && (img.getRGB(54, 20) >>> 24) == 0) return PlayerSkinType.SLIM;
        } catch (IOException ignored) {
        }
        return PlayerSkinType.WIDE;
    }

    private PlayerSkinType chosen() {
        return arms.get() == Arms.Slim ? PlayerSkinType.SLIM : PlayerSkinType.WIDE;
    }

    private Path folder() {
        Path dir = mc.runDirectory.toPath().resolve("larper-client").resolve("skins").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ChatUtils.warning("Couldn't create %s", dir);
        }
        return dir;
    }

    private String currentFile() {
        return file == null ? null : file.get();
    }
}
