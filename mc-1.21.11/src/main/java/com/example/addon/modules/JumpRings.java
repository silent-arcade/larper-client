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

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class JumpRings extends VisualModule {
    public enum Shape { Ring, Hex, Stars }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Shape> shape = sg.add(new EnumSetting.Builder<Shape>()
        .name("shape").description("Ring: a round shockwave. Hex: a hexagon. Stars: a five-point star spinning outward.")
        .defaultValue(Shape.Ring).build());

    private final Setting<Boolean> onJump = sg.add(new BoolSetting.Builder()
        .name("on-jump").description("A ring when you jump.").defaultValue(true).build());

    private final Setting<Boolean> onLand = sg.add(new BoolSetting.Builder()
        .name("on-land").description("A ring when you land; bigger falls make bigger rings.").defaultValue(true).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("size").description("How far a ring spreads (blocks).").defaultValue(2).range(0.5, 8).sliderRange(0.5, 5).build());

    private final Setting<Double> duration = sg.add(new DoubleSetting.Builder()
        .name("duration").description("How long a ring lasts (seconds).").defaultValue(0.6).range(0.1, 3).sliderRange(0.2, 1.5).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Rainbow around each ring.").defaultValue(true).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Ring colour when rainbow is off.").defaultValue(new SettingColor(90, 230, 255, 200))
        .visible(() -> !rainbow.get()).build());

    private record Ring(Vec3d pos, double start, double scale) {}

    private final List<Ring> rings = new ArrayList<>();
    private boolean wasOnGround = true;
    private double peakY;

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> echoRing = feature("echo-ring", "A second, slower ring following the first.");
    private final Setting<Boolean> sparksOn = feature("sparks", "Sparks thrown outward with the wave (and twinkles on the star points).");

    public JumpRings() {
        super("jump-rings", "Shockwave rings, hexagons or stars when you jump and land. Only you see them.");
    }

    @Override
    public void onDeactivate() {
        rings.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        boolean ground = mc.player.isOnGround();
        if (!ground) peakY = Math.max(peakY, mc.player.getY());
        if (wasOnGround && !ground && mc.player.getVelocity().y > 0 && onJump.get()) add(0.7);
        if (!wasOnGround && ground && onLand.get()) {
            double fall = Math.max(0, peakY - mc.player.getY());
            add(Math.min(2.5, 0.8 + fall * 0.25));
        }
        if (ground) peakY = mc.player.getY();
        wasOnGround = ground;
    }

    private void add(double scale) {
        rings.add(new Ring(Fx.pos(mc.player, 1).add(0, 0.02, 0), Fx.time(), scale));
    }

    private void star(Render3DEvent event, Vec3d c, double r, double turn, java.util.function.DoubleFunction<Color> col) {
        Vec3d prev = null, first = null;
        for (int k = 0; k <= 10; k++) {
            double a = turn + Math.PI * 2 * k / 10, rr = k % 2 == 0 ? r : r * 0.45;
            Vec3d p = c.add(Math.cos(a) * rr, 0, Math.sin(a) * rr);
            if (prev != null) event.depthRenderer.line(prev.x, prev.y, prev.z, p.x, p.y, p.z, col.apply(k / 10.0));
            if (k % 2 == 0 && k < 10 && on(sparksOn)) spark(event.depthRenderer, p.add(0, 0.05, 0), 0.15, col.apply(k / 10.0), true);
            prev = p;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double now = Fx.time(), life = duration.get();
        for (Iterator<Ring> it = rings.iterator(); it.hasNext(); ) {
            Ring ring = it.next();
            double f = (now - ring.start()) / life;
            if (f >= 1) {
                it.remove();
                continue;
            }
            double ease = 1 - Math.pow(1 - f, 3);
            double outer = 0.2 + size.get() * ring.scale() * ease, inner = Math.max(0, outer - 0.12 - 0.25 * (1 - f));
            int alpha = (int) (220 * (1 - f));
            double hueBase = now * 0.2;
            java.util.function.DoubleFunction<Color> col = u -> rainbow.get() ? Fx.hue(hueBase + u, alpha) : Fx.alpha(color.get(), (int) (color.get().a * (1 - f)));
            switch (shape.get()) {
                case Ring -> ring(event.depthRenderer, ring.pos(), inner, outer, 48, col);
                case Hex -> Fx.ring(event.depthRenderer, ring.pos(), inner, outer, 6, col, false);
                case Stars -> star(event, ring.pos(), outer, ring.start() * 3 + f * 2, col);
            }
            if (on(echoRing) && f > 0.15) {
                double g = (f - 0.15) / 0.85, o2 = 0.15 + size.get() * ring.scale() * 0.55 * (1 - Math.pow(1 - g, 2));
                ring(event.depthRenderer, ring.pos().add(0, 0.01, 0), Math.max(0, o2 - 0.07), o2, 36,
                    u -> rainbow.get() ? Fx.hue(hueBase + 0.5 + u, (int) (140 * (1 - g))) : Fx.alpha(color.get(), (int) (color.get().a * 0.6 * (1 - g))));
            }
            if (!on(sparksOn)) continue;
            int sparks = (int) (6 + 4 * ring.scale());
            for (int k = 0; k < sparks; k++) {
                double a = k * Math.PI * 2 / sparks + ring.start() * 7, d = outer * 1.05;
                Vec3d p = ring.pos().add(Math.cos(a) * d, 0.25 * Math.sin(f * Math.PI) * ring.scale(), Math.sin(a) * d);
                spark(event.depthRenderer, p, 0.12 * (1 - f), rainbow.get() ? Fx.hue(hueBase + (double) k / sparks, alpha) : Fx.alpha(color.get(), alpha), false);
            }
        }
    }
}
