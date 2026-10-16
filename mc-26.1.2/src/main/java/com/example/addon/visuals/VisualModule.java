package com.example.addon.visuals;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.world.phys.Vec3;
import java.util.function.DoubleFunction;

public abstract class VisualModule extends Module {
    protected final Setting<Boolean> lowEnd = settings.getDefaultGroup().add(new BoolSetting.Builder()
        .name("low-end-pc").description("Lighter version for low-end PCs: fewer particles and segments, and all the extra features below are skipped.")
        .defaultValue(false).onChanged(v -> onLowEndChanged()).build());

    private SettingGroup features;
    private Setting<Boolean> bloom;

    protected VisualModule(String name, String description) {
        super(AddonTemplate.VISUAL, name, description);
    }

    protected void onLowEndChanged() {
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

    protected void glow(Renderer3D r, Vec3 c, double radius, Color color) {
        Fx.glow(r, c, radius, color, bloom(), low());
    }

    protected void ring(Renderer3D r, Vec3 c, double inner, double outer, int segments, DoubleFunction<Color> colorAt) {
        Fx.ring(r, c, inner, outer, segments, colorAt, bloom(), low());
    }

    protected void shockwave(Renderer3D r, Vec3 at, double f, double size, Color color) {
        Fx.shockwave(r, at, f, size, color, bloom(), low());
    }

    protected void spark(Renderer3D r, Vec3 c, double size, Color color) {
        Fx.spark(r, c, size, color, !bloom());
    }

    protected void spark(Renderer3D r, Vec3 c, double size, Color color, boolean small) {
        Fx.spark(r, c, size, color, small || !bloom());
    }

    protected void beam(Renderer3D r, Vec3 base, double radius, double height, Color color) {
        Fx.beam(r, base, radius, height, color, low());
    }
}
