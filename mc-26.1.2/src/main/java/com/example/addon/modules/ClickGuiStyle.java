package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.themes.meteor.MeteorGuiTheme;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

public class ClickGuiStyle extends Module {
    public enum Preset { Larper, Ocean, Blood, Mint, Sunset, Mono, Cherry, Gold, Ice, Toxic, Midnight, Candy, Lavender, Forest, Aqua, Copper, Neon, Rose, Dracula, Nord, Matrix, Sakura, Galaxy, Lime, Custom }

    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgLayout = settings.createGroup("Layout");

    private final Setting<Preset> preset = sg.add(new EnumSetting.Builder<Preset>()
        .name("preset").description("The colour scheme. Custom uses the three colours below.").defaultValue(Preset.Larper)
        .onChanged(v -> reapply()).build());

    private final Setting<SettingColor> accent = sg.add(new ColorSetting.Builder()
        .name("accent").description("Custom: main colour (titles, sliders, checkboxes, active modules).").defaultValue(new SettingColor(164, 91, 255))
        .visible(() -> preset.get() == Preset.Custom).onChanged(v -> reapply()).build());

    private final Setting<SettingColor> background = sg.add(new ColorSetting.Builder()
        .name("background").description("Custom: panel background.").defaultValue(new SettingColor(14, 10, 24, 220))
        .visible(() -> preset.get() == Preset.Custom).onChanged(v -> reapply()).build());

    private final Setting<SettingColor> text = sg.add(new ColorSetting.Builder()
        .name("text").description("Custom: text colour.").defaultValue(new SettingColor(235, 230, 255))
        .visible(() -> preset.get() == Preset.Custom).onChanged(v -> reapply()).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow-accent").description("The accent colour slowly cycles through the rainbow.").defaultValue(false)
        .onChanged(v -> reapply()).build());

    private final Setting<Double> rainbowSpeed = sg.add(new DoubleSetting.Builder()
        .name("rainbow-speed").description("How fast the rainbow cycles (changes Meteor's rainbow speed while this is on).").defaultValue(1).range(0.1, 10).sliderRange(0.2, 5)
        .visible(rainbow::get).onChanged(v -> reapply()).build());

    private final Setting<Integer> opacity = sg.add(new IntSetting.Builder()
        .name("background-opacity").description("How see-through the panels are (0 = invisible, 255 = solid).").defaultValue(220).range(0, 255).sliderRange(40, 255)
        .onChanged(v -> reapply()).build());

    private final Setting<AlignmentX> alignment = sgLayout.add(new EnumSetting.Builder<AlignmentX>()
        .name("module-alignment").description("Where module names sit in their row.").defaultValue(AlignmentX.Center)
        .onChanged(v -> reapply()).build());

    private final Setting<Boolean> icons = sgLayout.add(new BoolSetting.Builder()
        .name("category-icons").description("Show the little icon next to each category title.").defaultValue(true)
        .onChanged(v -> reapply()).build());

    private final Setting<Boolean> hideHud = sgLayout.add(new BoolSetting.Builder()
        .name("hide-hud").description("Hide the HUD while the click GUI is open.").defaultValue(false)
        .onChanged(v -> reapply()).build());

    private final Setting<Double> scale = sgLayout.add(new DoubleSetting.Builder()
        .name("gui-scale").description("Size of the click GUI (Meteor's own default is 1).").defaultValue(1).range(0.5, 2).sliderRange(0.6, 1.6)
        .onChanged(v -> reapply()).build());

    public ClickGuiStyle() {
        super(AddonTemplate.VISUAL, "click-gui", "Restyles Meteor's whole click GUI: colour presets or your own colours, opacity, rainbow, titles, layout and size.");
    }

    @Override
    public void onActivate() {
        if (!(GuiThemes.get() instanceof MeteorGuiTheme)) {
            ChatUtils.warning("Click GUI only restyles Meteor's own theme (select \"Meteor\" in Meteor's GUI settings).");
            toggle();
            return;
        }
        apply();
    }

    private Double oldRainbowSpeed;

    @Override
    public void onDeactivate() {
        GuiTheme theme = GuiThemes.get();
        if (theme instanceof MeteorGuiTheme) {
            for (var group : theme.settings) for (Setting<?> s : group) s.reset();
        }
        if (oldRainbowSpeed != null) Config.get().rainbowSpeed.set(oldRainbowSpeed);
        oldRainbowSpeed = null;
    }

    private void reapply() {
        if (isActive()) apply();
    }

    private void apply() {
        if (!(GuiThemes.get() instanceof MeteorGuiTheme theme)) return;
        SettingColor a, b, t;
        switch (preset.get()) {
            case Larper -> { a = c(164, 91, 255, 255); b = c(14, 10, 24, 220); t = c(235, 230, 255, 255); }
            case Ocean -> { a = c(40, 170, 255, 255); b = c(8, 16, 28, 220); t = c(220, 240, 255, 255); }
            case Blood -> { a = c(230, 30, 50, 255); b = c(20, 6, 8, 225); t = c(255, 225, 225, 255); }
            case Mint -> { a = c(60, 230, 160, 255); b = c(8, 22, 18, 220); t = c(220, 255, 240, 255); }
            case Sunset -> { a = c(255, 120, 40, 255); b = c(28, 12, 20, 220); t = c(255, 235, 215, 255); }
            case Mono -> { a = c(230, 230, 230, 255); b = c(14, 14, 14, 225); t = c(240, 240, 240, 255); }
            case Cherry -> { a = c(255, 90, 160, 255); b = c(26, 8, 18, 220); t = c(255, 228, 240, 255); }
            case Gold -> { a = c(255, 195, 50, 255); b = c(22, 17, 6, 225); t = c(255, 245, 220, 255); }
            case Ice -> { a = c(150, 230, 255, 255); b = c(10, 20, 30, 215); t = c(235, 250, 255, 255); }
            case Toxic -> { a = c(150, 255, 40, 255); b = c(10, 18, 6, 225); t = c(235, 255, 220, 255); }
            case Midnight -> { a = c(90, 110, 255, 255); b = c(6, 6, 16, 235); t = c(215, 220, 255, 255); }
            case Candy -> { a = c(255, 120, 220, 255); b = c(30, 14, 34, 215); t = c(255, 240, 250, 255); }
            case Lavender -> { a = c(190, 160, 255, 255); b = c(22, 18, 34, 220); t = c(240, 235, 255, 255); }
            case Forest -> { a = c(80, 170, 70, 255); b = c(12, 22, 12, 225); t = c(225, 240, 215, 255); }
            case Aqua -> { a = c(0, 210, 200, 255); b = c(4, 22, 24, 220); t = c(220, 255, 252, 255); }
            case Copper -> { a = c(215, 120, 70, 255); b = c(26, 16, 12, 225); t = c(250, 230, 215, 255); }
            case Neon -> { a = c(255, 40, 200, 255); b = c(8, 4, 16, 235); t = c(200, 255, 250, 255); }
            case Rose -> { a = c(240, 110, 130, 255); b = c(30, 14, 18, 220); t = c(255, 232, 236, 255); }
            case Dracula -> { a = c(189, 147, 249, 255); b = c(40, 42, 54, 235); t = c(248, 248, 242, 255); }
            case Nord -> { a = c(136, 192, 208, 255); b = c(46, 52, 64, 235); t = c(236, 239, 244, 255); }
            case Matrix -> { a = c(0, 255, 70, 255); b = c(0, 8, 0, 235); t = c(170, 255, 180, 255); }
            case Sakura -> { a = c(255, 170, 200, 255); b = c(40, 24, 32, 215); t = c(255, 245, 250, 255); }
            case Galaxy -> { a = c(120, 80, 255, 255); b = c(10, 6, 30, 230); t = c(220, 210, 255, 255); }
            case Lime -> { a = c(190, 255, 0, 255); b = c(18, 22, 6, 225); t = c(245, 255, 220, 255); }
            default -> { a = new SettingColor(accent.get()); b = new SettingColor(background.get()); t = new SettingColor(text.get()); }
        }
        boolean rb = rainbow.get();
        b = alpha(b, opacity.get());
        set(theme, "accent", a, rb);
        set(theme, "checkbox", a, rb);
        set(theme, "favorite", mix(a, c(255, 210, 60, 255), 0.6), false);
        set(theme, "text", t, false);
        set(theme, "text-secondary-text", alpha(t, 150), false);
        set(theme, "text-highlight", alpha(a, 100), false);
        set(theme, "title-text", a.r * 0.299 + a.g * 0.587 + a.b * 0.114 > 150 ? c(18, 18, 22, 255) : c(255, 255, 255, 255), false);
        set(theme, "logged-in-text", lighten(a, 40), false);
        set(theme, "placeholder", alpha(t, 100), false);
        set(theme, "background", b, false);
        set(theme, "hovered-background", lighten(b, 14), false);
        set(theme, "pressed-background", lighten(b, 28), false);
        set(theme, "module-background", alpha(a, 70), rb);
        set(theme, "outline", lighten(b, 26), false);
        set(theme, "hovered-outline", alpha(a, 160), rb);
        set(theme, "pressed-outline", a, rb);
        set(theme, "separator-text", t, false);
        set(theme, "separator-center", a, rb);
        set(theme, "separator-edges", alpha(a, 50), false);
        set(theme, "Scrollbar", alpha(lighten(b, 40), 200), false);
        set(theme, "hovered-Scrollbar", alpha(lighten(b, 60), 200), false);
        set(theme, "pressed-Scrollbar", alpha(a, 220), false);
        set(theme, "slider-handle", a, rb);
        set(theme, "hovered-slider-handle", lighten(a, 30), false);
        set(theme, "pressed-slider-handle", lighten(a, -30), false);
        set(theme, "slider-left", a, rb);
        set(theme, "slider-right", lighten(b, 40), false);
        theme.scale.set(scale.get());
        theme.moduleAlignment.set(alignment.get());
        theme.categoryIcons.set(icons.get());
        theme.hideHUD.set(hideHud.get());
        if (rb) {
            if (oldRainbowSpeed == null) oldRainbowSpeed = Config.get().rainbowSpeed.get();
            Config.get().rainbowSpeed.set(rainbowSpeed.get());
        } else if (oldRainbowSpeed != null) {
            Config.get().rainbowSpeed.set(oldRainbowSpeed);
            oldRainbowSpeed = null;
        }
    }

    @SuppressWarnings("unchecked")
    private static void set(MeteorGuiTheme theme, String name, SettingColor color, boolean rainbow) {
        Setting<?> s = theme.settings.get(name + "-color");
        if (s == null || !(s.get() instanceof SettingColor)) return;
        color.rainbow(rainbow);
        ((Setting<SettingColor>) s).set(color);
    }

    private static SettingColor c(int r, int g, int b, int a) {
        return new SettingColor(r, g, b, a);
    }

    private static SettingColor alpha(SettingColor c, int a) {
        return new SettingColor(c.r, c.g, c.b, a);
    }

    private static SettingColor lighten(SettingColor c, int by) {
        return new SettingColor(clamp(c.r + by), clamp(c.g + by), clamp(c.b + by), c.a);
    }

    private static SettingColor mix(SettingColor x, SettingColor y, double t) {
        return new SettingColor((int) (x.r + (y.r - x.r) * t), (int) (x.g + (y.g - x.g) * t), (int) (x.b + (y.b - x.b) * t), 255);
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
