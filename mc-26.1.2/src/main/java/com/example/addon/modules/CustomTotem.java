package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.sound.CustomSound;
import com.example.addon.util.FileChoices;
import com.example.addon.util.GeneratedPacks;
import meteordevelopment.meteorclient.events.world.PlaySoundEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import com.example.addon.sky.AnimatedSky;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class CustomTotem extends Module {
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".gif"};
    private static final String PACK_NAME = "LarperClientTotem";
    private static final int SIZE = 128;
    private static final int MAX_FRAMES = 120;

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> image = sgGeneral.add(new ProvidedStringSetting.Builder()
        .name("image").description("Totem image from .minecraft/larper-client/totems. PNGs with a transparent background look best.")
        .defaultValue(FileChoices.NONE)
        .supplier(() -> FileChoices.list(folder(), currentImage(), EXTENSIONS))
        .onChanged(s -> rebuildIn = isActive() ? 1 : -1)
        .build());

    private final Setting<String> link = sgGeneral.add(new StringSetting.Builder()
        .name("gif-link").description("Paste a GIF (or image) link to use as the totem. Overrides the image. Leave empty to use the image.")
        .defaultValue("")
        .onChanged(s -> rebuildIn = isActive() ? 20 : -1)
        .build());

    private final Setting<String> sound = sgGeneral.add(new ProvidedStringSetting.Builder()
        .name("sound").description("Your own .mp3 or .wav (from .minecraft/larper-client/totems) that plays when your totem pops.")
        .defaultValue(FileChoices.NONE)
        .supplier(() -> FileChoices.list(folder(), currentSound(), CustomSound.EXTENSIONS))
        .build());

    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder()
        .name("volume").description("Totem sound volume.").defaultValue(1).range(0, 1).sliderRange(0, 1)
        .visible(() -> FileChoices.isFile(sound.get()))
        .build());

    private final Setting<Double> speed = sgGeneral.add(new DoubleSetting.Builder()
        .name("sound-speed").description("Playback speed of your pop sound (1 = normal, 2 = twice as fast and higher, 0.5 = slow and deep).")
        .defaultValue(1).range(0.25, 4).sliderRange(0.5, 2)
        .visible(() -> FileChoices.isFile(sound.get()))
        .build());

    private final Setting<Double> start = sgGeneral.add(new DoubleSetting.Builder()
        .name("sound-start").description("Seconds into the file where the pop sound starts.")
        .defaultValue(0).range(0, 600).sliderRange(0, 10)
        .visible(() -> FileChoices.isFile(sound.get()))
        .build());

    private final Setting<Double> stop = sgGeneral.add(new DoubleSetting.Builder()
        .name("sound-end").description("Seconds into the file where it stops. 0 = play to the end.")
        .defaultValue(0).range(0, 600).sliderRange(0, 10)
        .visible(() -> FileChoices.isFile(sound.get()))
        .build());

    private int rebuildIn = -1;
    private boolean packEnabled;

    public CustomTotem() {
        super(AddonTemplate.VISUAL, "custom-totem", "Your own image as the Totem of Undying. Only you see it.");
    }

    @Override
    public meteordevelopment.meteorclient.gui.widgets.WWidget getWidget(meteordevelopment.meteorclient.gui.GuiTheme theme) {
        var list = theme.verticalList();
        list.add(theme.label("Image"));
        list.add(com.example.addon.util.FilePicker.buttons(theme, "Choose a totem image", folder(), image, EXTENSIONS));
        list.add(theme.label("Sound"));
        list.add(com.example.addon.util.FilePicker.buttons(theme, "Choose a totem sound", folder(), sound, CustomSound.EXTENSIONS));
        return list;
    }

    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (mc.player == null || !FileChoices.isFile(sound.get())) return;
        var s = event.sound;
        if (!s.getIdentifier().equals(SoundEvents.TOTEM_USE.location())) return;
        if (mc.player.distanceToSqr(s.getX(), s.getY(), s.getZ()) > 4) return;
        Path file = folder().resolve(sound.get());
        if (!Files.isRegularFile(file) || !CustomSound.isSupported(file)) return;
        event.cancel();
        double from = start.get(), length = stop.get() > from ? stop.get() - from : 0;
        CustomSound.play(file, volume.get().floatValue() * mc.options.getFinalSoundSourceVolume(SoundSource.MASTER), speed.get().floatValue(), from, length);
    }

    @Override
    public void onActivate() {
        folder();
        if (!packEnabled) rebuildIn = 0;
    }

    @Override
    public void onDeactivate() {
        rebuildIn = -1;
        if (isActive()) return;
        setPackEnabled(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        GeneratedPacks.tick();
        if (rebuildIn < 0 || rebuildIn-- > 0) return;
        rebuildIn = -1;
        rebuild();
    }

    private void rebuild() {
        String url = link.get().trim();
        if (url.isEmpty() && !FileChoices.isFile(image.get())) {
            setPackEnabled(false);
            return;
        }
        Path source = url.isEmpty() ? folder().resolve(image.get()) : null;
        if (source != null && !Files.isRegularFile(source)) {
            ChatUtils.warning("Totem image not found: %s", image.get());
            return;
        }
        String name = source != null ? source.getFileName().toString() : url;
        if (source == null) ChatUtils.info("Downloading totem GIF...");
        CompletableFuture.runAsync(() -> {
            try {
                AnimatedSky.Gif gif = AnimatedSky.decode(source != null ? Files.readAllBytes(source) : AnimatedSky.download(url));
                int n = Math.min(gif.frames().size(), MAX_FRAMES);
                BufferedImage strip = new BufferedImage(SIZE, SIZE * n, BufferedImage.TYPE_INT_ARGB);
                Graphics2D g = strip.createGraphics();
                for (int i = 0; i < n; i++) g.drawImage(square(gif.frames().get(i)), 0, i * SIZE, null);
                g.dispose();
                Path tex = GeneratedPacks.create(PACK_NAME, "Larper Client custom totem").resolve("assets/minecraft/textures/item");
                Files.createDirectories(tex);
                ImageIO.write(strip, "png", tex.resolve("totem_of_undying.png").toFile());
                Path meta = tex.resolve("totem_of_undying.png.mcmeta");
                if (n > 1) Files.writeString(meta, animation(gif.delaysMs(), n));
                else Files.deleteIfExists(meta);
                mc.execute(() -> {
                    if (!isActive()) return;
                    if (packEnabled) GeneratedPacks.safeReload();
                    else setPackEnabled(true);
                    ChatUtils.info("Custom totem loaded: %s%s", name, n > 1 ? " (" + n + " frames)" : "");
                });
            } catch (Exception e) {
                mc.execute(() -> ChatUtils.warning("Couldn't load totem %s: %s", name, e.getMessage()));
            }
        });
    }

    static String animation(int[] delaysMs, int frames) {
        StringBuilder json = new StringBuilder("{\"animation\":{\"interpolate\":false,\"frames\":[");
        for (int i = 0; i < frames; i++) {
            if (i > 0) json.append(',');
            json.append("{\"index\":").append(i).append(",\"time\":").append(Math.max(1, Math.round(delaysMs[i] / 50f))).append('}');
        }
        return json.append("]}}").toString();
    }

    static BufferedImage square(BufferedImage img) {
        double k = (double) SIZE / Math.max(img.getWidth(), img.getHeight());
        int w = Math.max(1, (int) Math.round(img.getWidth() * k)), h = Math.max(1, (int) Math.round(img.getHeight() * k));
        BufferedImage cur = img;
        while (cur.getWidth() / 2 >= w && cur.getHeight() / 2 >= h) cur = scale(cur, cur.getWidth() / 2, cur.getHeight() / 2);
        cur = scale(cur, w, h);
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(cur, (SIZE - w) / 2, (SIZE - h) / 2, null);
        g.dispose();
        return out;
    }

    private static BufferedImage scale(BufferedImage src, int w, int h) {
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, w, h, null);
        g.dispose();
        return out;
    }

    private void setPackEnabled(boolean on) {
        if (packEnabled == on) return;
        packEnabled = on;
        GeneratedPacks.setEnabled(PACK_NAME, on);
    }

    private Path folder() {
        Path dir = mc.gameDirectory.toPath().resolve("larper-client").resolve("totems");
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

    private String currentSound() {
        return sound == null ? null : sound.get();
    }
}
