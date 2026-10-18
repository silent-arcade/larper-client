package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;

public class Footprints extends VisualModule {
    public enum Style { Feet, Paws, Flames }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Feet: shoe prints. Paws: animal paw prints. Flames: little fires burning where you stepped.")
        .defaultValue(Style.Feet).build());

    private final Setting<Double> duration = sg.add(new DoubleSetting.Builder()
        .name("duration").description("How long a footprint stays (seconds).").defaultValue(4).range(0.5, 30).sliderRange(1, 12).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("size").description("Footprint size.").defaultValue(1).range(0.4, 3).sliderRange(0.5, 2).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Each step a new colour.").defaultValue(true).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Footprint colour.").defaultValue(new SettingColor(120, 200, 255, 200)).visible(() -> !rainbow.get()).build());

    private record Print(Vec3d pos, float yaw, boolean left, double start, double hue) {}

    private final Deque<Print> prints = new ArrayDeque<>();
    private Vec3d lastStep;
    private boolean left;

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> glowPatch = feature("glow-patch", "A soft glow under each print.");
    private final Setting<Boolean> stepPing = feature("step-ping", "A small ring ping as each step lands.");
    private final Setting<Boolean> embers = feature("embers", "Flames style: the bright tip and flying embers.");

    public Footprints() {
        super("footprints", "Glowing footprints, paw prints or little flames where you walk. Only you see them.");
    }

    @Override
    public void onDeactivate() {
        prints.clear();
        lastStep = null;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        if (!mc.player.isOnGround() || mc.player.isSneaking() && mc.player.getVelocity().horizontalLengthSquared() < 1e-4) return;
        Vec3d at = Fx.pos(mc.player, 1);
        if (lastStep != null && lastStep.distanceTo(at) < 0.65 * size.get()) return;
        lastStep = at;
        left = !left;
        prints.add(new Print(at.add(0, 0.015, 0), mc.player.getYaw(), left, Fx.time(), Fx.time() * 0.15));
        while (prints.size() > (low() ? 40 : 200)) prints.poll();
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
            Vec3d fwd = new Vec3d(-Math.sin(yaw), 0, Math.cos(yaw)), side = new Vec3d(-fwd.z, 0, fwd.x);
            Vec3d o = p.pos().add(side.multiply((p.left() ? 0.13 : -0.13) * k));
            switch (style.get()) {
                case Feet -> {
                    pad(event, o.add(fwd.multiply(0.07 * k)), fwd, side, 0.075 * k, 0.12 * k, c);
                    pad(event, o.add(fwd.multiply(-0.13 * k)), fwd, side, 0.06 * k, 0.06 * k, c);
                }
                case Paws -> {
                    pad(event, o, fwd, side, 0.07 * k, 0.06 * k, c);
                    for (int toe = 0; toe < 4; toe++) {
                        double ta = Math.toRadians(-45 + toe * 30);
                        Vec3d tp = o.add(fwd.multiply(Math.cos(ta) * 0.12 * k)).add(side.multiply(Math.sin(ta) * 0.12 * k));
                        pad(event, tp, fwd, side, 0.025 * k, 0.03 * k, c);
                    }
                }
                case Flames -> {
                    double flick = 0.75 + 0.25 * Math.sin(now * 15 + p.start() * 31);
                    double h = 0.45 * k * (1 - f) * flick;
                    Color hot = new Color(255, (int) (60 + 140 * (1 - f)), 20, (int) (240 * (1 - f * f)));
                    glow(event.renderer, o.add(0, h * 0.4, 0), 0.22 * k * (1 - f * 0.5) * flick, hot);
                    if (on(embers)) {
                        glow(event.renderer, o.add(0, h * 0.8, 0), 0.12 * k * flick, new Color(255, 230, 140, (int) (230 * (1 - f))));
                        if (Math.sin(now * 9 + p.start() * 17) > 0.6) spark(event.renderer, o.add(0, h * 1.3, 0), 0.08 * k, new Color(255, 200, 80, (int) (220 * (1 - f))), true);
                    }
                }
            }
            if (on(glowPatch)) ring(event.renderer, o, 0, 0.22 * k, 16, u -> Fx.alpha(base, (int) (base.a * 0.18 * (1 - f))));
            double age = now - p.start();
            if (on(stepPing) && age < 0.35) {
                double g = age / 0.35;
                ring(event.renderer, o.add(0, 0.005, 0), 0.08 * k + 0.25 * k * g, 0.11 * k + 0.25 * k * g, 20, u -> Fx.alpha(base, (int) (base.a * (1 - g))));
            }
        }
    }

    private void pad(Render3DEvent event, Vec3d c, Vec3d fwd, Vec3d side, double halfW, double halfL, Color color) {
        Vec3d a = c.add(fwd.multiply(halfL)).add(side.multiply(halfW)), b = c.add(fwd.multiply(halfL)).subtract(side.multiply(halfW));
        Vec3d d = c.subtract(fwd.multiply(halfL)).add(side.multiply(halfW)), e = c.subtract(fwd.multiply(halfL)).subtract(side.multiply(halfW));
        event.renderer.quad(a.x, a.y, a.z, b.x, b.y, b.z, e.x, e.y, e.z, d.x, d.y, d.z, color);
    }
}
