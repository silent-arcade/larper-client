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
import java.util.ArrayList;
import java.util.List;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.resources.Identifier;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

/**
 * Shows your own image as the sky, rendered by the Skyboxify mod (required, not bundled).
 * The image (a 360° panorama works best) is converted into an OptiFine-format sky inside a small generated
 * resource pack, "LarperClientSky", which this module turns on and off. Only you see it.
 */
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

    private int rebuildIn = -1;
    private boolean packEnabled;

    public CustomSky() {
        super(AddonTemplate.CATEGORY, "custom-sky", "Your own image as the sky, using the Skyboxify mod. Only you see it.");
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
        // Rejoining a world: the sky is still loaded from before, no need to rebuild (and reload) it
        if (!packEnabled) rebuildIn = 0;
    }

    @Override
    public void onDeactivate() {
        rebuildIn = -1;
        // Meteor also calls this when you leave a world, while the module is still on. Reloading resources mid-
        // disconnect hangs the game on the Mojang screen, so only take the sky down when you turn the module off.
        if (isActive()) return;
        AnimatedSky.stop();
        setPackEnabled(false);
    }

    // Animated skies swap frames every rendered frame
    @EventHandler
    private void onRender(Render3DEvent event) {
        AnimatedSky.tick();
    }

    @Override
    public meteordevelopment.meteorclient.gui.widgets.WWidget getWidget(meteordevelopment.meteorclient.gui.GuiTheme theme) {
        return com.example.addon.util.FilePicker.buttons(theme, "Choose a sky image", folder(), image, EXTENSIONS);
    }

    public boolean hidesSunMoon() {
        return isActive() && packEnabled && hideSunMoon.get();
    }

    private void rebuildSoon() {
        // Sliders fire on every step; wait until they settle so we don't reload resources dozens of times
        if (isActive()) rebuildIn = 20;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (rebuildIn < 0 || rebuildIn-- > 0) return;
        rebuildIn = -1;
        rebuild();
    }

    private void rebuild() {
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

        // Download/decode + convert off the render thread (big panoramas and long GIFs take a moment), then reload on it
        CompletableFuture.runAsync(() -> {
            try {
                AnimatedSky.Gif gif = AnimatedSky.decode(source != null ? Files.readAllBytes(source) : AnimatedSky.download(url));
                int n = gif.frames().size();
                BufferedImage first = gif.frames().get(0);
                // Stills: at least 512px per side so they stay sharp. Animations: as sharp as memory allows
                int face = n == 1 ? Math.max(512, Math.min(1024, first.getWidth() / 2)) : AnimatedSky.faceSize(n);
                List<BufferedImage> cubes = new ArrayList<>(n);
                for (BufferedImage frame : gif.frames()) {
                    cubes.add(fitMode == Fit.Cube ? SkyboxifyCube.convertFlat(frame, face, yaw) : SkyboxifyCube.convert(frame, face, yaw, aboveHorizon, times));
                }
                writePack(cubes.get(0), rotate, ow, en, rain);
                mc.execute(() -> {
                    if (!isActive()) return;
                    if (packEnabled) GeneratedPacks.safeReload(); // already on: just pick up the new image
                    else setPackEnabled(true);
                    if (n > 1) {
                        List<Identifier> ids = new ArrayList<>();
                        if (ow) ids.add(Identifier.fromNamespaceAndPath("minecraft", "optifine/sky/world0/larper_sky.png"));
                        if (en) ids.add(Identifier.fromNamespaceAndPath("minecraft", "optifine/sky/world1/larper_sky.png"));
                        AnimatedSky.play(cubes, gif.delaysMs(), ids);
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
        String props = "source=./larper_sky.png\nblend=replace\nrotate=" + rotate + "\n" + (rotate ? "speed=1\naxis=0.0 1.0 0.0\n" : "") // vertical axis: the default (sun's) axis flips the sky upside down at night
            + (rain ? "weather=clear rain thunder\n" : ""); // Skyboxify only shows a sky in clear weather unless told otherwise
        // OptiFine dimension folders: world0 = Overworld, world1 = the End
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

    private void setPackEnabled(boolean on) {
        if (packEnabled == on) return;
        packEnabled = on;
        GeneratedPacks.setEnabled(PACK_NAME, on);
    }

    private Path folder() {
        Path dir = mc.gameDirectory.toPath().resolve("larper-client").resolve("skies");
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
}
