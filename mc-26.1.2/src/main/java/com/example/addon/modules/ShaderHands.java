package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.render.color.Color;

public class ShaderHands extends Module {
    public enum Source { SkyShaders, Theme }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Source> source = sg.add(new EnumSetting.Builder<Source>()
        .name("source").description("SkyShaders: your hand flows through the same colours as the sky (uses the Sky Shaders theme). Theme: pick a theme just for your hand.").defaultValue(Source.SkyShaders).build());

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colours for your hand.").defaultValue(FxTheme.Rainbow).visible(() -> source.get() == Source.Theme).build());

    private final Setting<Double> flow = sg.add(new DoubleSetting.Builder()
        .name("flow-speed").description("How fast the colour flows.").defaultValue(1).range(0, 5).sliderRange(0, 3).build());

    private final Setting<Double> offset = sg.add(new DoubleSetting.Builder()
        .name("color-offset").description("Shift your hand ahead of or behind the sky's colour, so it contrasts instead of matching.").defaultValue(0).range(0, 1).sliderRange(0, 1).visible(() -> source.get() == Source.SkyShaders).build());

    private final Setting<Boolean> pulse = sg.add(new BoolSetting.Builder()
        .name("pulse").description("The colour breathes brighter and darker, like it's glowing.").defaultValue(true).build());

    private final Setting<Boolean> hand = sg.add(new BoolSetting.Builder()
        .name("hand").description("Colour your hand.").defaultValue(true).build());

    private final Setting<Boolean> item = sg.add(new BoolSetting.Builder()
        .name("held-item").description("Colour the item you hold (not totems).").defaultValue(true).build());

    private static final Color FALLBACK = new Color(170, 90, 255, 255);

    public ShaderHands() {
        super(AddonTemplate.VISUAL, "shader-hands", "Your hand and held item flow through the Sky Shaders colours (or a theme of their own), breathing like a glow. Only you see it.");
    }

    private int rgb() {
        double t = Fx.time() * flow.get();
        int c = source.get() == Source.SkyShaders
            ? Modules.get().get(SkyShaders.class).colorAt(t * 0.05 + offset.get())
            : theme.get().color(t * 0.1, 255, FALLBACK).getPacked() & 0xFFFFFF;
        if (!pulse.get()) return c;
        double k = 0.72 + 0.28 * Math.sin(Fx.time() * 2.4);
        return (int) ((c >> 16 & 255) * k) << 16 | (int) ((c >> 8 & 255) * k) << 8 | (int) ((c & 255) * k);
    }

    // Shader Hands wins when it's on, otherwise Hand Aura (0 = vanilla)
    public static int handTint(HandAura aura) {
        int c = Modules.get().get(ShaderHands.class).handColor();
        return c != 0 ? c : aura == null ? 0 : aura.handColor();
    }

    public static int itemTint(HandAura aura) {
        int c = Modules.get().get(ShaderHands.class).itemColor();
        return c != 0 ? c : aura == null ? 0 : aura.itemColor();
    }

    // 0 = leave it to Hand Aura / vanilla
    public int handColor() {
        return isActive() && hand.get() ? 0xFF000000 | rgb() : 0;
    }

    public int itemColor() {
        return isActive() && item.get() ? 0xFF000000 | rgb() : 0;
    }
}
