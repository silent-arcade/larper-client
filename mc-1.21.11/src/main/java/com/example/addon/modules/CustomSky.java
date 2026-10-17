package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.sky.SkyboxifyCube;
import com.example.addon.util.FileChoices;
import com.example.addon.util.GeneratedPacks;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import com.example.addon.sky.AnimatedSky;
import com.example.addon.sky.VideoSky;
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class CustomSky extends Module {
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".gif"};
    private static final String PACK_NAME = "LarperClientSky";

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> image = sgGeneral.add(new ProvidedStringSetting.Builder()
        .name("image").description("Sky image from .minecraft/larper-client/skies. A 2:1 360° panorama looks best.")
        .defaultValue(FileChoices.NONE)
        .supplier(() -> FileChoices.list(folder(), currentImage(), EXTENSIONS))
        .onChanged(s -> rebuildSoon())
        .build());

    private final Setting<String> link = sgGeneral.add(new StringSetting.Builder()
        .name("gif-link").description("Paste a GIF link (e.g. from Discord) to use it as an animated sky. Overrides the image. Leave empty to use the image.")
        .defaultValue("")
        .onChanged(s -> rebuildSoon())
        .build());

    public enum Fit { Cube, AboveHorizon, FullSphere }

    private final Setting<Fit> fit = sgGeneral.add(new EnumSetting.Builder<Fit>()
        .name("fit").description("Cube lays the image flat on every wall. AboveHorizon curves it over the sky. FullSphere is for 360° panoramas.")
        .defaultValue(Fit.Cube)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Integer> repeat = sgGeneral.add(new IntSetting.Builder()
        .visible(() -> fit.get() != Fit.Cube)
        .name("repeat").description("How many times the image goes around you. Higher = less stretched.")
        .defaultValue(2).range(1, 8).sliderRange(1, 8)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Double> rotation = sgGeneral.add(new DoubleSetting.Builder()
        .name("rotation").description("Turns the image around you (degrees).").defaultValue(0).range(0, 360).sliderRange(0, 360)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Boolean> overworld = sgGeneral.add(new BoolSetting.Builder()
        .name("overworld").description("Use the image as the Overworld sky.").defaultValue(true)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Boolean> end = sgGeneral.add(new BoolSetting.Builder()
        .name("end").description("Use the image as the End sky.").defaultValue(false)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Boolean> rotateWithSun = sgGeneral.add(new BoolSetting.Builder()
        .name("rotate-with-sun").description("The sky turns slowly around you with the time of day (stays upright).").defaultValue(false)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Boolean> inRain = sgGeneral.add(new BoolSetting.Builder()
        .name("in-rain").description("Keep your sky when it rains or storms (otherwise it fades out in bad weather).").defaultValue(true)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Boolean> hideSunMoon = sgGeneral.add(new BoolSetting.Builder()
        .name("hide-sun-moon").description("Hides the sun, moon and stars.").defaultValue(true)
        .build());

    public enum Quality { Low, Medium, High }

    private final SettingGroup sgVideo = settings.createGroup("Video");

    private final Setting<String> video = sgVideo.add(new StringSetting.Builder()
        .name("video").description("Paste a YouTube link (or type the name of a video file in the skies folder) to play it as the sky. Overrides the image and GIF. Leave empty for no video.")
        .defaultValue("")
        .onChanged(s -> rebuildSoon())
        .build());

    private final Setting<Integer> videoFps = sgVideo.add(new IntSetting.Builder()
        .name("fps").description("Frames per second of the sky video, up to 120 (never more than the video itself has).")
        .defaultValue(60).range(10, 120).sliderRange(10, 120)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Quality> quality = sgVideo.add(new EnumSetting.Builder<Quality>()
        .name("quality").description("Low: 360p, lightest. Medium: 540p. High: 720p, sharpest but uses more CPU. Medium and High get YouTube's 60 fps stream when there is one.")
        .defaultValue(Quality.Medium)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Boolean> videoSound = sgVideo.add(new BoolSetting.Builder()
        .name("sound").description("Play the video's sound.").defaultValue(true)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<Integer> volume = sgVideo.add(new IntSetting.Builder()
        .name("volume").description("Video volume (also follows your Master volume).")
        .defaultValue(60).range(0, 100).sliderRange(0, 100)
        .visible(videoSound::get)
        .onChanged(v -> { if (this.player != null) this.player.volume(v / 100f); })
        .build());

    private final Setting<Boolean> loop = sgVideo.add(new BoolSetting.Builder()
        .name("loop").description("Start the video again when it ends.").defaultValue(true)
        .onChanged(v -> rebuildSoon())
        .build());

    private final Setting<meteordevelopment.meteorclient.utils.misc.Keybind> playKey = sgVideo.add(new meteordevelopment.meteorclient.settings.KeybindSetting.Builder()
        .name("play-pause-key").description("Key that pauses and plays the sky video.")
        .defaultValue(meteordevelopment.meteorclient.utils.misc.Keybind.none())
        .action(this::playPause)
        .build());

    private int rebuildIn = -1;
    private boolean packEnabled;
    private VideoSky player;
    private String playerLink;

    public CustomSky() {
        super(AddonTemplate.VISUAL, "custom-sky", "Your own image as the sky, using the Skyboxify mod. Only you see it.");
    }

    public static boolean skyboxifyInstalled() {
        return FabricLoader.getInstance().isModLoaded("skyboxify");
    }

    @Override
    public void onActivate() {
        if (!skyboxifyInstalled()) {
            ChatUtils.error("Custom Sky needs the Skyboxify mod. Install Skyboxify (and its dependencies) and try again.");
            toggle();
            return;
        }
        folder();
        if (!packEnabled) rebuildIn = 0;
    }

    @Override
    public void onDeactivate() {
        rebuildIn = -1;
        if (isActive()) return;
        stopVideo();
        AnimatedSky.stop();
        setPackEnabled(false);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        AnimatedSky.tick();
        if (player != null) player.tick();
    }

    @Override
    public meteordevelopment.meteorclient.gui.widgets.WWidget getWidget(meteordevelopment.meteorclient.gui.GuiTheme theme) {
        var list = theme.verticalList();
        list.add(com.example.addon.util.FilePicker.buttons(theme, "Choose a sky image", folder(), image, EXTENSIONS));
        var row = list.add(theme.horizontalList()).widget();
        row.add(theme.button("Play / Pause video")).widget().action = this::playPause;
        row.add(theme.button("Restart video")).widget().action = () -> {
            if (player != null) player.play(0);
            else playPause();
        };
        row.add(theme.button("Open tools folder")).widget().action = () -> net.minecraft.util.Util.getOperatingSystem().open(tools().toFile());
        return list;
    }

    public boolean hidesSunMoon() {
        return isActive() && packEnabled && hideSunMoon.get();
    }

    private void rebuildSoon() {
        if (isActive()) rebuildIn = 20;
    }

    private int stallRetries;

    @EventHandler
    private void onTick(TickEvent.Post event) {
        GeneratedPacks.tick();
        if (player != null && (player.stalledFor() > 15 || player.ended() && player.position() < 1)) {
            String why = player.lastError() != null ? player.lastError() : "the video isn't arriving fast enough";
            if (stallRetries++ < 1) {
                ChatUtils.warning("Sky video stalled (%s) - trying again...", why);
                double at = player.position();
                stopVideo();
                startVideoAt(video.get().trim(), at);
            } else {
                ChatUtils.warning("Sky video stopped: %s.%s", why, video.get().contains("youtu") && !Files.isRegularFile(tools().resolve("deno.exe"))
                    ? " YouTube slows downloads unless yt-dlp can use deno: put deno.exe in the tools folder, or try again later." : "");
                stopVideo();
            }
        }
        if (rebuildIn < 0 || rebuildIn-- > 0) return;
        rebuildIn = -1;
        rebuild();
    }

    private void rebuild() {
        String vid = video.get().trim();
        if (!vid.isEmpty() && (overworld.get() || end.get())) {
            startVideo(vid);
            return;
        }
        stopVideo();
        String url = link.get().trim();
        if ((url.isEmpty() && !FileChoices.isFile(image.get())) || !(overworld.get() || end.get())) {
            AnimatedSky.stop();
            setPackEnabled(false);
            return;
        }
        Path source = url.isEmpty() ? folder().resolve(image.get()) : null;
        if (source != null && !Files.isRegularFile(source)) {
            ChatUtils.warning("Sky image not found: %s", image.get());
            return;
        }
        String name = source != null ? source.getFileName().toString() : url;
        double yaw = rotation.get();
        Fit fitMode = fit.get();
        boolean aboveHorizon = fitMode == Fit.AboveHorizon;
        int times = repeat.get();
        boolean rotate = rotateWithSun.get(), ow = overworld.get(), en = end.get(), rain = inRain.get();
        if (source == null) ChatUtils.info("Downloading sky GIF...");

        CompletableFuture.runAsync(() -> {
            try {
                AnimatedSky.Gif gif = AnimatedSky.decode(source != null ? Files.readAllBytes(source) : AnimatedSky.download(url));
                int n = gif.frames().size();
                BufferedImage first = gif.frames().get(0);
                int face = Math.max(512, Math.min(n == 1 ? 1024 : 512, first.getWidth() / 2));
                java.util.function.Function<BufferedImage, BufferedImage> toCube = frame -> fitMode == Fit.Cube
                    ? SkyboxifyCube.convertFlat(frame, face, yaw) : SkyboxifyCube.convert(frame, face, yaw, aboveHorizon, times);
                writePack(toCube.apply(first), rotate, ow, en, rain);
                mc.execute(() -> {
                    if (!isActive()) return;
                    if (packEnabled) GeneratedPacks.safeReload();
                    else setPackEnabled(true);
                    if (n > 1) {
                        List<Identifier> ids = new ArrayList<>();
                        if (ow) ids.add(Identifier.of("minecraft", "optifine/sky/world0/larper_sky.png"));
                        if (en) ids.add(Identifier.of("minecraft", "optifine/sky/world1/larper_sky.png"));
                        AnimatedSky.play(gif.frames(), gif.delaysMs(), toCube, face * 3, face * 2, ids);
                    } else AnimatedSky.stop();
                    ChatUtils.info("Custom sky loaded: %s%s", name, n > 1 ? " (" + n + " frames)" : "");
                });
            } catch (Exception e) {
                AddonTemplate.LOG.warn("Custom sky {} failed", name, e);
                mc.execute(() -> ChatUtils.warning("Couldn't load sky %s: %s", name, e.getMessage()));
            }
        });
    }

    private void writePack(BufferedImage cube, boolean rotate, boolean ow, boolean en, boolean rain) throws IOException {
        Path sky = GeneratedPacks.create(PACK_NAME, "Larper Client custom sky").resolve("assets/minecraft/optifine/sky");
        String props = "source=./larper_sky.png\nblend=replace\nrotate=" + rotate + "\n" + (rotate ? "speed=1\naxis=0.0 1.0 0.0\n" : "")
            + (rain ? "weather=clear rain thunder\n" : "");
        for (String dim : new String[]{"world0", "world1"}) {
            Path dir = sky.resolve(dim);
            boolean on = dim.equals("world0") ? ow : en;
            Files.createDirectories(dir);
            Files.deleteIfExists(dir.resolve("larper_sky.properties"));
            Files.deleteIfExists(dir.resolve("larper_sky.png"));
            if (!on) continue;
            ImageIO.write(cube, "png", dir.resolve("larper_sky.png").toFile());
            Files.writeString(dir.resolve("larper_sky.properties"), props);
        }
    }

    private void playPause() {
        if (!isActive() || mc.player == null) return;
        if (player == null) {
            if (video.get().isBlank()) ChatUtils.warning("Custom Sky: paste a YouTube link in the Video settings first.");
            else rebuild();
            return;
        }
        if (player.paused() || player.ended()) {
            player.resume();
            ChatUtils.info("Sky video playing.");
        } else {
            player.pause();
            int t = (int) player.position();
            ChatUtils.info("Sky video paused at %d:%02d.", t / 60, t % 60);
        }
    }

    private void stopVideo() {
        if (player != null) player.stop();
        player = null;
        playerLink = null;
    }

    private void startVideo(String vid) {
        stallRetries = 0;
        startVideoAt(vid, player != null && vid.equals(playerLink) ? player.position() : 0);
    }

    private void startVideoAt(String vid, double from) {
        boolean wasPaused = player != null && vid.equals(playerLink) && player.paused();
        stopVideo();
        AnimatedSky.stop();
        String deno = Files.isRegularFile(tools().resolve("deno.exe")) ? tools().resolve("deno.exe").toAbsolutePath().toString() : null;
        int face = switch (quality.get()) { case Low -> 256; case Medium -> 384; case High -> 512; };
        int sw = switch (quality.get()) { case Low -> 640; case Medium -> 960; case High -> 1280; };
        int sh = sw * 9 / 16, maxHeight = switch (quality.get()) { case Low -> 480; case Medium, High -> 720; };
        Fit fitMode = fit.get();
        double yaw = rotation.get();
        int times = repeat.get(), wantFps = videoFps.get();
        boolean rotate = rotateWithSun.get(), ow = overworld.get(), en = end.get(), rain = inRain.get(), sound = videoSound.get(), again = loop.get();
        float vol = volume.get() / 100f;
        boolean web = vid.regionMatches(true, 0, "http", 0, 4);
        if (web && !vid.regionMatches(true, 0, "https://", 0, 8)) {
            ChatUtils.warning("Custom Sky: the video link must start with https://");
            return;
        }
        if (web) ChatUtils.info("Getting the video...");
        CompletableFuture.runAsync(() -> {
            try {
                String ffmpeg = tool("ffmpeg"), ytdlp = web ? tool("yt-dlp") : "yt-dlp";
                VideoSky.Source src;
                if (web) src = VideoSky.resolve(ytdlp, deno, vid, maxHeight);
                else {
                    Path file = folder().resolve(vid).normalize();
                    if (!file.startsWith(folder()) || !Files.isRegularFile(file)) throw new IOException("no video file called " + vid + " in the skies folder");
                    src = new VideoSky.Source(file.toString(), file.toString(), file.getFileName().toString(), 0);
                }
                int fps = src.fps() > 0 ? Math.min(wantFps, (int) Math.ceil(src.fps())) : wantFps;
                int[] table = SkyboxifyCube.lookup(face, sw, sh, fitMode == Fit.Cube, yaw, fitMode == Fit.AboveHorizon, times);
                writePack(new BufferedImage(face * 3, face * 2, BufferedImage.TYPE_INT_ARGB), rotate, ow, en, rain);
                VideoSky.Source found = src;
                mc.execute(() -> {
                    if (!isActive() || !vid.equals(video.get().trim())) return;
                    if (packEnabled) GeneratedPacks.safeReload();
                    else setPackEnabled(true);
                    List<Identifier> ids = new ArrayList<>();
                    if (ow) ids.add(Identifier.of("minecraft", "optifine/sky/world0/larper_sky.png"));
                    if (en) ids.add(Identifier.of("minecraft", "optifine/sky/world1/larper_sky.png"));
                    stopVideo();
                    player = new VideoSky(ffmpeg, found, fps, face, sw, sh, table, ids, sound, again, vol);
                    playerLink = vid;
                    player.play(from);
                    if (wasPaused) player.pause();
                    ChatUtils.info("Sky video: %s (%d fps)%s", found.title(), fps, wasPaused ? " - paused" : "");
                });
            } catch (IOException e) {
                String msg = e.getMessage() != null && e.getMessage().startsWith("Cannot run program")
                    ? "couldn't get yt-dlp/ffmpeg. Check your internet and try again, or put yt-dlp.exe and ffmpeg.exe (from their official sites) in the tools folder (button in the module)."
                    : e.getMessage();
                mc.execute(() -> ChatUtils.warning("Custom Sky video: %s", msg));
            } catch (Exception e) {
                AddonTemplate.LOG.warn("Sky video failed", e);
                mc.execute(() -> ChatUtils.warning("Custom Sky video failed: %s", e.getMessage()));
            }
        });
    }

    private Path tools() {
        Path dir = mc.runDirectory.toPath().resolve("larper-client").resolve("tools");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir;
    }

    private synchronized String tool(String name) {
        for (String file : new String[]{name + ".exe", name}) {
            Path p = tools().resolve(file);
            if (Files.isRegularFile(p)) return p.toAbsolutePath().toString();
        }
        // Missing on Windows: fetch it once from the official download (yt-dlp's GitHub releases, the ffmpeg.org-linked gyan.dev build)
        if (!System.getProperty("os.name", "").toLowerCase().contains("win")) return name;
        String url = name.equals("ffmpeg") ? "https://www.gyan.dev/ffmpeg/builds/ffmpeg-release-essentials.zip" : "https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp.exe";
        Path exe = tools().resolve(name + ".exe"), tmp = tools().resolve(name + ".download");
        mc.execute(() -> ChatUtils.info("Downloading %s (one time, %s)...", name, name.equals("ffmpeg") ? "~110 MB" : "~18 MB"));
        try {
            var http = java.net.http.HttpClient.newBuilder().followRedirects(java.net.http.HttpClient.Redirect.NORMAL).build();
            var res = http.send(java.net.http.HttpRequest.newBuilder(java.net.URI.create(url)).build(), java.net.http.HttpResponse.BodyHandlers.ofFile(tmp));
            if (res.statusCode() != 200) throw new IOException("HTTP " + res.statusCode());
            if (url.endsWith(".zip")) {
                try (var zip = new java.util.zip.ZipFile(tmp.toFile())) {
                    var entry = zip.stream().filter(e -> e.getName().endsWith("/bin/ffmpeg.exe")).findFirst().orElseThrow(() -> new IOException("no ffmpeg.exe in the download"));
                    try (var in = zip.getInputStream(entry)) {
                        Files.copy(in, exe, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                Files.delete(tmp);
            } else Files.move(tmp, exe, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            mc.execute(() -> ChatUtils.info("Got %s.", name));
            return exe.toAbsolutePath().toString();
        } catch (Exception e) {
            AddonTemplate.LOG.warn("Couldn't download " + name, e);
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
        return name;
    }

    private void setPackEnabled(boolean on) {
        if (packEnabled == on) return;
        packEnabled = on;
        GeneratedPacks.setEnabled(PACK_NAME, on);
    }

    private Path folder() {
        Path dir = mc.runDirectory.toPath().resolve("larper-client").resolve("skies");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ChatUtils.warning("Couldn't create %s", dir);
        }
        return dir;
    }

    private String currentImage() {
        return image == null ? null : image.get();
    }
}
