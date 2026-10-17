package com.example.addon.visuals;

import meteordevelopment.meteorclient.utils.render.color.Color;

public enum FxTheme {
    Custom(),
    Void(0x9D4DFF, 0x3A0CA3, 0xF0C8FF),
    Inferno(0xFF5A1F, 0xFFC233, 0xB3120E),
    Frost(0x7FE7FF, 0xFFFFFF, 0x2E86DE),
    Toxic(0x39FF14, 0xD4FF3A, 0x0E8F3A),
    Celestial(0xFFD54A, 0xFFFFFF, 0xFFA8E8),
    Blood(0xE0102E, 0x6A0018, 0xFF6B81),
    Ocean(0x00C2E8, 0x0057B8, 0x9DF3FF),
    Sakura(0xFFB3C9, 0xFF5FA2, 0xFFFFFF),
    Rainbow();

    private final int[] palette;

    FxTheme(int... palette) {
        this.palette = palette;
    }

    // Flows smoothly through the theme's colours as t goes up (time, position along a ring...)
    public Color color(double t, int alpha, Color custom) {
        if (this == Custom) return Fx.alpha(custom, alpha);
        if (this == Rainbow) return Fx.hue(t, alpha);
        double f = (t % 1 + 1) % 1 * palette.length;
        int i = (int) f, a = palette[i % palette.length], b = palette[(i + 1) % palette.length];
        double k = f - i, s = k * k * (3 - 2 * k);
        return new Color(mix(a >> 16, b >> 16, s), mix(a >> 8, b >> 8, s), mix(a, b, s), alpha);
    }

    // A second, contrasting colour for sparks and accents
    public Color secondary(double t, int alpha, Color custom) {
        if (this == Custom) return new Color(Math.min(255, custom.r + 90), Math.min(255, custom.g + 90), Math.min(255, custom.b + 90), alpha);
        return color(t + 0.5, alpha, custom);
    }

    private static int mix(int a, int b, double t) {
        return (int) Math.round((a & 255) * (1 - t) + (b & 255) * t);
    }
}
