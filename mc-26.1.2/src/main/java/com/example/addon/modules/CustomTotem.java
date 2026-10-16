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

/**
 * Your own image as the Totem of Undying: in your hand, in your inventory and in the big pop animation when it
 * saves you, plus an optional sound of your own when it pops. It's a small generated resource pack ("LarperClientTotem") that replaces the totem texture, so it only
 * changes what you see.
 */
public class CustomTotem extends Module {
    private static final String[] EXTENSIONS = {".png", ".jpg", ".jpeg", ".bmp", ".gif"};
    private static final String PACK_NAME = "LarperClientTotem";
    private static final int SIZE = 64; // item textures are square; 64px keeps detail without a huge 3D model

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<String> image = sgGeneral.add(new ProvidedStringSetting.Builder()
        .name("image").description("Totem image from .minecraft/larper-client/totems. PNGs with a transparent background look best.")
        .defaultValue(FileChoices.NONE)
        .supplier(() -> FileChoices.list(folder(), currentImage(), EXTENSIONS))
        .onChanged(s -> rebuildIn = isActive() ? 1 : -1)
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
        super(AddonTemplate.CATEGORY, "custom-totem", "Your own image as the Totem of Undying. Only you see it.");
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

    // Your totem popping plays the vanilla sound right where you stand; swap it for yours. Other players' totems
    // (further away) keep the normal sound.
    @EventHandler
    private void onPlaySound(PlaySoundEvent event) {
        if (mc.player == null || !FileChoices.isFile(sound.get())) return;
        var s = event.sound;
        if (!s.getIdentifier().equals(SoundEvents.TOTEM_USE.location())) return;
        if (mc.player.distanceToSqr(s.getX(), s.getY(), s.getZ()) > 4) return;
        Path file = folder().resolve(sound.get());
        if (!Files.isRegularFile(file) || !CustomSound.isSupported(file)) return;
        event.cancel();
        // start/end are positions in the file (before the speed change); 0 end or an end before the start = to the end
        double from = start.get(), length = stop.get() > from ? stop.get() - from : 0;
        CustomSound.play(file, volume.get().floatValue() * mc.options.getFinalSoundSourceVolume(SoundSource.MASTER), speed.get().floatValue(), from, length);
    }

    @Override
    public void onActivate() {
        folder();
        if (!packEnabled) rebuildIn = 0; // rejoining a world: the totem is still loaded from before
    }

    @Override
    public void onDeactivate() {
        rebuildIn = -1;
        // Meteor also calls this when you leave a world (module still on); reloading then hangs the game
        if (isActive()) return;
        setPackEnabled(false);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (rebuildIn < 0 || rebuildIn-- > 0) return;
        rebuildIn = -1;
        rebuild();
    }

    private void rebuild() {
        if (!FileChoices.isFile(image.get())) {
            setPackEnabled(false);
            return;
        }
        Path source = folder().resolve(image.get());
        if (!Files.isRegularFile(source)) {
            ChatUtils.warning("Totem image not found: %s", image.get());
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                BufferedImage img = ImageIO.read(source.toFile());
                if (img == null) throw new IOException("unsupported image format");
                Path tex = GeneratedPacks.create(PACK_NAME, "Larper Client custom totem").resolve("assets/minecraft/textures/item");
                Files.createDirectories(tex);
                ImageIO.write(square(img), "png", tex.resolve("totem_of_undying.png").toFile());
                mc.execute(() -> {
                    if (!isActive()) return;
                    if (packEnabled) GeneratedPacks.safeReload(); // already on: just pick up the new image
                    else setPackEnabled(true);
                    ChatUtils.info("Custom totem loaded: %s", source.getFileName());
                });
            } catch (IOException e) {
                mc.execute(() -> ChatUtils.warning("Couldn't load totem image %s: %s", source.getFileName(), e.getMessage()));
            }
        });
    }

    // Fits the image into a SIZE x SIZE square, keeping its shape (transparent around it)
    static BufferedImage square(BufferedImage img) {
        BufferedImage out = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_ARGB);
        double k = (double) SIZE / Math.max(img.getWidth(), img.getHeight());
        int w = Math.max(1, (int) Math.round(img.getWidth() * k)), h = Math.max(1, (int) Math.round(img.getHeight() * k));
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.drawImage(img, (SIZE - w) / 2, (SIZE - h) / 2, w, h, null);
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

    // The setting field isn't assigned yet while its own builder runs, so read it lazily
    private String currentImage() {
        return image == null ? null : image.get();
    }

    private String currentSound() {
        return sound == null ? null : sound.get();
    }
}
