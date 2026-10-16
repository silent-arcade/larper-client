package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.Vec3d;

/** Glowing orbs circling around you, each with a fading trail. Only you see them. */
public class OrbitOrbs extends Module {
    public enum Style { Ring, Atom, Helix }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Ring: one flat circle. Atom: each orb on its own tilted orbit. Helix: orbs spiral up and down.")
        .defaultValue(Style.Atom).build());

    private final Setting<Integer> count = sg.add(new IntSetting.Builder()
        .name("orbs").description("How many orbs.").defaultValue(3).range(1, 12).sliderRange(1, 8).build());

    private final Setting<Double> radius = sg.add(new DoubleSetting.Builder()
        .name("radius").description("How far from you they orbit.").defaultValue(0.9).range(0.3, 4).sliderRange(0.4, 2.5).build());

    private final Setting<Double> speed = sg.add(new DoubleSetting.Builder()
        .name("speed").description("Turns per second.").defaultValue(0.6).range(0.05, 4).sliderRange(0.1, 2).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("size").description("Orb size.").defaultValue(0.12).range(0.03, 0.6).sliderRange(0.05, 0.3).build());

    private final Setting<Integer> trail = sg.add(new IntSetting.Builder()
        .name("trail").description("Trail length (0 = none).").defaultValue(14).range(0, 40).sliderRange(0, 30).build());

    private final Setting<Boolean> rainbow = sg.add(new BoolSetting.Builder()
        .name("rainbow").description("Each orb a different, slowly shifting colour.").defaultValue(true).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Orb colour when rainbow is off.").defaultValue(new SettingColor(120, 200, 255, 210))
        .visible(() -> !rainbow.get()).build());

    private final Setting<Boolean> firstPerson = sg.add(new BoolSetting.Builder()
        .name("first-person").description("Also show them in first person (they swing past your view).").defaultValue(true).build());

    public OrbitOrbs() {
        super(AddonTemplate.CATEGORY, "orbit-orbs", "Glowing orbs orbiting around you. Only you see them.");
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || (!firstPerson.get() && mc.options.getPerspective().isFirstPerson())) return;
        Vec3d base = Fx.pos(mc.player, event.tickDelta).add(0, mc.player.getHeight() * 0.55, 0);
        double t = Fx.time();
        int n = count.get();
        for (int i = 0; i < n; i++) {
            Color c = rainbow.get() ? Fx.hue(t * 0.08 + (double) i / n, 220) : color.get();
            // trail first (oldest, faintest), then the orb on top
            for (int k = trail.get(); k > 0; k--) {
                Vec3d a = orbit(i, n, t - k * 0.02), b = orbit(i, n, t - (k - 1) * 0.02);
                int alpha = (int) (c.a * (1 - (double) k / (trail.get() + 1)) * 0.8);
                event.depthRenderer.line(base.x + a.x, base.y + a.y, base.z + a.z, base.x + b.x, base.y + b.y, base.z + b.z, Fx.alpha(c, alpha));
            }
            Fx.glow(event.depthRenderer, base.add(orbit(i, n, t)), size.get(), c);
        }
    }

    // Offset of orb i (of n) from your centre at time t
    private Vec3d orbit(int i, int n, double t) {
        double r = radius.get(), phase = (double) i / n * Math.PI * 2, a = t * speed.get() * Math.PI * 2 + phase;
        return switch (style.get()) {
            case Ring -> new Vec3d(Math.cos(a) * r, Math.sin(t * 2 + phase) * 0.06, Math.sin(a) * r);
            case Helix -> new Vec3d(Math.cos(a) * r, Math.sin(a * 0.5 + phase) * 0.8, Math.sin(a) * r);
            case Atom -> {
                // a circle tilted by a different amount for each orb, its tilt slowly turning
                double tilt = Math.PI / n * i + 0.5, turn = phase + t * 0.2;
                double x = Math.cos(a) * r, z = Math.sin(a) * r;
                double y = z * Math.sin(tilt);
                z *= Math.cos(tilt);
                yield new Vec3d(x * Math.cos(turn) - z * Math.sin(turn), y, x * Math.sin(turn) + z * Math.cos(turn));
            }
        };
    }
}
