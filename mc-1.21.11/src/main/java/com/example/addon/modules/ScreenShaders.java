package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.mixin.GameRendererAccessor;
import com.example.addon.util.GeneratedPacks;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Identifier;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

public class ScreenShaders extends VisualModule {
    public enum Preset { Chromatic, RetroCRT, Dreamy, Cinematic, Noir, Vaporwave, Comic, Custom }

    private static final String PACK = "LarperClientShaders";

    private static final float[][] PRESETS = {
        {1, 1, 1, 0, 1.10f, 1.05f, 0, 0, 1.3f, 0.25f, 0, 0, 0, 0, 0, 0},
        {1, 1, 1, 0, 1.15f, 1.15f, 0, 0.15f, 0.6f, 0.55f, 0.35f, 0.6f, 0.5f, 0.6f, 0, 0},
        {1, 0.85f, 1, 0.12f, 1.25f, 0.95f, 0.03f, 0.9f, 0.25f, 0.3f, 0, 0, 0.1f, 0, 0, 0},
        {1, 1, 1, 0, 1.10f, 1.20f, -0.02f, 0.2f, 0.15f, 0.45f, 0, 0, 0.15f, 0, 0, 1},
        {1, 1, 1, 0, 0, 1.35f, 0, 0.1f, 0, 0.6f, 0, 0, 0.35f, 0.2f, 0, 0},
        {1, 0.4f, 0.9f, 0.25f, 1.4f, 1.05f, 0.02f, 0.4f, 0.5f, 0.3f, 0, 0.25f, 0, 0, 0, 0},
        {1, 1, 1, 0, 1.5f, 1.3f, 0, 0, 0, 0.2f, 0, 0, 0, 0, 5, 0},
    };

    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgCustom = settings.createGroup("Custom");

    private final Setting<Preset> preset = sgGeneral.add(new EnumSetting.Builder<Preset>()
        .name("preset").description("The look. Custom uses the settings below.")
        .defaultValue(Preset.Cinematic).build());

    private final Setting<SettingColor> tint = sgCustom.add(new ColorSetting.Builder()
        .name("tint").description("Colour to tint the screen with (its alpha is how strong).").defaultValue(new SettingColor(255, 140, 220, 40))
        .visible(this::custom).onChanged(c -> rewriteSoon()).build());

    private final Setting<Double> saturation = custom("saturation", "0 = black and white, 1 = normal, 2 = vivid.", 1.2, 0, 3);
    private final Setting<Double> contrast = custom("contrast", "1 = normal.", 1.1, 0.3, 2.5);
    private final Setting<Double> brightness = custom("brightness", "0 = normal.", 0, -0.4, 0.4);
    private final Setting<Double> glow = custom("glow", "Bright things glow and bleed light.", 0.4, 0, 3);
    private final Setting<Double> chroma = custom("chromatic", "Red/blue split toward the edges.", 0.5, 0, 4);
    private final Setting<Double> vignette = custom("vignette", "Darker edges.", 0.35, 0, 1);
    private final Setting<Double> curve = custom("screen-curve", "Old TV curved screen.", 0, 0, 1);
    private final Setting<Double> scanlines = custom("scanlines", "Horizontal TV lines.", 0, 0, 1);
    private final Setting<Double> grain = custom("film-grain", "Animated film grain.", 0.1, 0, 1.5);
    private final Setting<Double> flicker = custom("flicker", "Old projector brightness flicker.", 0, 0, 1);
    private final Setting<Double> splitTone = custom("movie-grade", "Teal shadows and orange highlights.", 0.3, 0, 1.5);
    private final Setting<Integer> posterize = sgCustom.add(new IntSetting.Builder()
        .name("posterize").description("Cartoon colour bands (0 = off; fewer = more cartoony).").defaultValue(0).range(0, 16).sliderRange(0, 12)
        .visible(this::custom).onChanged(v -> rewriteSoon()).build());

    private int rewriteIn = -1;

    public ScreenShaders() {
        super("screen-shaders", "Screen shaders: chromatic, retro CRT, dreamy glow, movie grade, noir, vaporwave, comic or custom.");
    }

    private Setting<Double> custom(String name, String description, double def, double min, double max) {
        return sgCustom.add(new DoubleSetting.Builder().name(name).description(description).defaultValue(def).range(min, max).sliderRange(min, max)
            .visible(this::custom).onChanged(v -> rewriteSoon()).build());
    }

    @Override
    protected void onLowEndChanged() {
        rewriteSoon();
    }

    private boolean custom() {
        return preset != null && preset.get() == Preset.Custom;
    }

    private void rewriteSoon() {
        if (isActive()) rewriteIn = 15;
    }

    @Override
    public void onActivate() {
        try {
            writeAll();
            GeneratedPacks.setEnabled(PACK, true);
        } catch (IOException e) {
            ChatUtils.error("Couldn't set up the shaders: %s", e.getMessage());
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        rewriteIn = -1;
        Identifier current = mc.gameRenderer == null ? null : mc.gameRenderer.getPostProcessorId();
        if (current != null && current.getNamespace().equals("larper-client")) mc.gameRenderer.clearPostProcessor();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        GeneratedPacks.tick();
        if (rewriteIn >= 0 && rewriteIn-- == 0) {
            try {
                writeAll();
                GeneratedPacks.safeReload();
            } catch (IOException e) {
                ChatUtils.error("Couldn't update the shader: %s", e.getMessage());
            }
        }
        if (mc.world == null || mc.getOverlay() != null) return;
        Identifier want = Identifier.of("larper-client", preset.get().name().toLowerCase(Locale.ROOT));
        if (!want.equals(mc.gameRenderer.getPostProcessorId())) ((GameRendererAccessor) mc.gameRenderer).larper$setPostProcessor(want);
    }

    private void writeAll() throws IOException {
        Path dir = GeneratedPacks.create(PACK, "Larper Client screen shaders").resolve("assets/larper-client/post_effect");
        Files.createDirectories(dir);
        Preset[] all = Preset.values();
        for (int i = 0; i < all.length; i++) {
            float[] v = (all[i] == Preset.Custom ? customValues() : PRESETS[i]).clone();
            if (low()) {
                v[7] = 0;
                v[8] = 0;
            }
            Files.writeString(dir.resolve(all[i].name().toLowerCase(Locale.ROOT) + ".json"), json(v));
        }
    }

    private float[] customValues() {
        SettingColor c = tint.get();
        return new float[]{c.r / 255f, c.g / 255f, c.b / 255f, c.a / 255f,
            saturation.get().floatValue(), contrast.get().floatValue(), brightness.get().floatValue(), glow.get().floatValue(),
            chroma.get().floatValue(), vignette.get().floatValue(), curve.get().floatValue(), scanlines.get().floatValue(),
            grain.get().floatValue(), flicker.get().floatValue(), posterize.get(), splitTone.get().floatValue()};
    }

    private static String json(float[] v) {
        String[] names = {"Tint", "Grade", "Lens", "Extra"};
        StringBuilder uniforms = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            if (i > 0) uniforms.append(",\n");
            uniforms.append(String.format(Locale.ROOT, "          { \"name\": \"%s\", \"type\": \"vec4\", \"value\": [%f, %f, %f, %f] }",
                names[i], v[i * 4], v[i * 4 + 1], v[i * 4 + 2], v[i * 4 + 3]));
        }
        return """
            {
              "targets": { "swap": {} },
              "passes": [
                {
                  "vertex_shader": "minecraft:core/screenquad",
                  "fragment_shader": "larper-client:post/larper_fx",
                  "inputs": [ { "sampler_name": "In", "target": "minecraft:main" } ],
                  "output": "swap",
                  "uniforms": { "LarperFx": [
            %s
                  ] }
                },
                {
                  "vertex_shader": "minecraft:core/screenquad",
                  "fragment_shader": "minecraft:post/blit",
                  "inputs": [ { "sampler_name": "In", "target": "swap" } ],
                  "uniforms": { "BlitConfig": [ { "name": "ColorModulate", "type": "vec4", "value": [1.0, 1.0, 1.0, 1.0] } ] },
                  "output": "minecraft:main"
                }
              ]
            }
            """.formatted(uniforms);
    }
}
