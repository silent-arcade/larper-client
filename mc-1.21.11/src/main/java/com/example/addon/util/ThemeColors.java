package com.example.addon.util;

import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public final class ThemeColors {
    private ThemeColors() {
    }

    private static int get(String name, int fallback) {
        if (!(GuiThemes.get() instanceof MeteorGuiTheme theme)) return fallback;
        Setting<?> s = theme.settings.get(name);
        return s != null && s.get() instanceof SettingColor c ? c.getPacked() : fallback;
    }

    public static int accent() {
        return get("accent-color", 0xFFA45BFF);
    }

    public static int background() {
        return get("background-color", 0xDC0E0A18);
    }

    public static int text() {
        return get("text-color", 0xFFEBE6FF);
    }

    public static int secondaryText() {
        return get("text-secondary-text-color", 0x96EBE6FF);
    }

    public static int mix(int a, int b, double t) {
        int r = (int) ((a >> 16 & 255) * (1 - t) + (b >> 16 & 255) * t), g = (int) ((a >> 8 & 255) * (1 - t) + (b >> 8 & 255) * t), bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return 0xFF000000 | r << 16 | g << 8 | bl;
    }

    public static int withAlpha(int argb, int alpha) {
        return Math.max(0, Math.min(255, alpha)) << 24 | (argb & 0xFFFFFF);
    }
}
