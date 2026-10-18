package com.example.addon.visuals;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.function.DoubleFunction;

public abstract class VisualModule extends Module {
    protected final Setting<Boolean> lowEnd = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("low-end-pc").description("Lighter version for low-end PCs: fewer particles and segments, and all the extra features below are skipped.")
        .defaultValue(false).onChanged(v -> onLowEndChanged()).build());

    private final SettingGroup look = settings.createGroup("Look");

    private final Setting<FxTheme> colorTheme = look.add(new meteordevelopment.meteorclient.settings.EnumSetting.Builder<FxTheme>()
        .name("color-theme").description("Recolour every effect of this module with a theme that flows across rings and between effects. Custom = the module's own colours.")
        .defaultValue(FxTheme.Custom).build());

    private final Setting<Integer> opacity = look.add(new meteordevelopment.meteorclient.settings.IntSetting.Builder()
        .name("opacity").description("How strong the effects are (%). Lower = subtler, higher = punchier.").defaultValue(100).range(10, 200).sliderRange(20, 200).build());

    private final Setting<Double> fadeIn = look.add(new meteordevelopment.meteorclient.settings.DoubleSetting.Builder()
        .name("fade-in").description("Seconds the effects take to fade in when you turn the module on (0 = instant).").defaultValue(0.3).range(0, 3).sliderRange(0, 1).build());

    private SettingGroup features;
    private Setting<Boolean> bloom;
    private double enabledAt;

    protected VisualModule(String name, String description) {
        super(AddonTemplate.VISUAL, name, description);
    }

    protected void onLowEndChanged() {
    }

    @Override
    public void toggle() {
        super.toggle();
        if (isActive()) enabledAt = Fx.time();
    }

    // every helper below runs its colour through here: theme, opacity and the fade-in
    protected Color tint(Color c, double t) {
        double fade = fadeIn.get() <= 0 ? 1 : Math.min(1, (Fx.time() - enabledAt) / fadeIn.get());
        int a = (int) Math.min(255, c.a * opacity.get() / 100.0 * fade);
        if (colorTheme.get() == FxTheme.Custom) return Fx.alpha(c, a);
        return colorTheme.get().color(t, a, c);
    }

    private Color tint(Color c, Vec3d at) {
        return tint(c, Fx.time() * 0.15 + (at.x + at.z) * 0.05);
    }

    protected boolean low() {
        return lowEnd.get();
    }

    protected Setting<Boolean> feature(String name, String description) {
        if (features == null) features = settings.createGroup("Features");
        return features.add(new BoolSetting.Builder().name(name).description(description).defaultValue(true).build());
    }

    protected boolean on(Setting<Boolean> feature) {
        return feature.get() && !low();
    }

    protected Setting<Boolean> addBloom() {
        bloom = feature("bloom", "Soft glowing halos around lights and feathered edges on rings.");
        return bloom;
    }

    protected boolean bloom() {
        return (bloom == null || bloom.get()) && !low();
    }

    protected int detail(int n, int min) {
        return low() ? Math.max(Math.min(min, n), n / 3) : n;
    }

    protected void glow(Renderer3D r, Vec3d c, double radius, Color color) {
        Fx.glow(r, c, radius, tint(color, c), bloom(), low());
    }

    protected void outline(Renderer3D r, Box box, Color raw) {
        Color color = tint(raw, box.getCenter());
        r.box(box, color, color, ShapeMode.Lines, 0);
        if (bloom()) r.box(box.expand(0.03), Fx.alpha(color, color.a / 3), Fx.alpha(color, color.a / 3), ShapeMode.Lines, 0);
    }

    protected void outline(Renderer3D r, Vec3d c, double half, Color color) {
        outline(r, new Box(c.x - half, c.y - half, c.z - half, c.x + half, c.y + half, c.z + half), color);
    }

    protected void ring(Renderer3D r, Vec3d c, double inner, double outer, int segments, DoubleFunction<Color> colorAt) {
        double now = Fx.time();
        Fx.ring(r, c, inner, outer, segments, u -> tint(colorAt.apply(u), u + now * 0.2), bloom(), low());
    }

    protected void shockwave(Renderer3D r, Vec3d at, double f, double size, Color color) {
        Fx.shockwave(r, at, f, size, tint(color, at), bloom(), low());
    }

    protected void spark(Renderer3D r, Vec3d c, double size, Color color) {
        Fx.spark(r, c, size, tint(color, c), !bloom());
    }

    protected void spark(Renderer3D r, Vec3d c, double size, Color color, boolean small) {
        Fx.spark(r, c, size, tint(color, c), small || !bloom());
    }

    protected void beam(Renderer3D r, Vec3d base, double radius, double height, Color color) {
        Fx.beam(r, base, radius, height, tint(color, base), low());
    }
}
