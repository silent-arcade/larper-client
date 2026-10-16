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
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Shockwave rings that spread across the ground when you jump and when you land. Only you see them. */
public class JumpRings extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

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

    public JumpRings() {
        super(AddonTemplate.CATEGORY, "jump-rings", "Shockwave rings when you jump and land. Only you see them.");
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
            add(Math.min(2.5, 0.8 + fall * 0.25)); // bigger falls, bigger shockwave
        }
        if (ground) peakY = mc.player.getY();
        wasOnGround = ground;
    }

    private void add(double scale) {
        rings.add(new Ring(mc.player.getEntityPos().add(0, 0.02, 0), Fx.time(), scale));
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
            double ease = 1 - Math.pow(1 - f, 3); // fast out, slowing down
            double outer = 0.2 + size.get() * ring.scale() * ease, inner = Math.max(0, outer - 0.12 - 0.25 * (1 - f));
            int alpha = (int) (220 * (1 - f));
            double hueBase = now * 0.2;
            Fx.ring(event.depthRenderer, ring.pos(), inner, outer, 48, u -> rainbow.get() ? Fx.hue(hueBase + u, alpha) : Fx.alpha(color.get(), (int) (color.get().a * (1 - f))));
        }
    }
}
