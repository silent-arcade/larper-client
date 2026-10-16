package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Deque;

/** Glowing footprints left where you walk, fading away behind you. Only you see them. */
public class Footprints extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> duration = sg.add(new DoubleSetting.Builder()
        .name("duration").description("How long a footprint stays (seconds).").defaultValue(4).range(0.5, 30).sliderRange(1, 12).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("size").description("Footprint size.").defaultValue(1).range(0.4, 3).sliderRange(0.5, 2).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Each step a new colour.").defaultValue(true).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Footprint colour.").defaultValue(new SettingColor(120, 200, 255, 200)).visible(() -> !rainbow.get()).build());

    private record Print(Vec3 pos, float yaw, boolean left, double start, double hue) {}

    private final Deque<Print> prints = new ArrayDeque<>();
    private Vec3 lastStep;
    private boolean left;

    public Footprints() {
        super(AddonTemplate.CATEGORY, "footprints", "Glowing footprints where you walk. Only you see them.");
    }

    @Override
    public void onDeactivate() {
        prints.clear();
        lastStep = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        if (!mc.player.onGround() || mc.player.isShiftKeyDown() && mc.player.getDeltaMovement().horizontalDistanceSqr() < 1e-4) return;
        Vec3 at = mc.player.position();
        if (lastStep != null && lastStep.distanceTo(at) < 0.65 * size.get()) return;
        lastStep = at;
        left = !left;
        prints.add(new Print(at.add(0, 0.015, 0), mc.player.getYRot(), left, Fx.time(), Fx.time() * 0.15));
        while (prints.size() > 200) prints.poll();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double now = Fx.time(), life = duration.get(), k = size.get();
        while (!prints.isEmpty() && now - prints.peek().start() > life) prints.poll();
        for (Print p : prints) {
            double f = (now - p.start()) / life;
            Color base = rainbow.get() ? Fx.hue(p.hue(), 210) : color.get();
            Color c = Fx.alpha(base, (int) (base.a * (1 - f * f)));
            double yaw = Math.toRadians(p.yaw());
            Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3(-fwd.z, 0, fwd.x);
            Vec3 o = p.pos().add(side.scale((p.left() ? 0.13 : -0.13) * k));
            // A sole (long) and a heel (short) pad
            pad(event, o.add(fwd.scale(0.07 * k)), fwd, side, 0.075 * k, 0.12 * k, c);
            pad(event, o.add(fwd.scale(-0.13 * k)), fwd, side, 0.06 * k, 0.06 * k, c);
        }
    }

    private void pad(Render3DEvent event, Vec3 c, Vec3 fwd, Vec3 side, double halfW, double halfL, Color color) {
        Vec3 a = c.add(fwd.scale(halfL)).add(side.scale(halfW)), b = c.add(fwd.scale(halfL)).subtract(side.scale(halfW));
        Vec3 d = c.subtract(fwd.scale(halfL)).add(side.scale(halfW)), e = c.subtract(fwd.scale(halfL)).subtract(side.scale(halfW));
        event.renderer.quad(a.x, a.y, a.z, b.x, b.y, b.z, e.x, e.y, e.z, d.x, d.y, d.z, color);
    }
}
