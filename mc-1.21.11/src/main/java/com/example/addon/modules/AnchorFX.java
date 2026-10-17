package com.example.addon.modules;

import com.example.addon.visuals.VisualModule;
import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.RespawnAnchorBlock;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AnchorFX extends VisualModule {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> range = sg.add(new IntSetting.Builder()
        .name("range").description("Show anchors this many blocks around you.").defaultValue(12).range(4, 32).sliderRange(4, 24).build());

    private final Setting<Boolean> pips = sg.add(new BoolSetting.Builder()
        .name("charge-pips").description("Four small outlined cubes circling above each anchor; one lights up per charge (green to red).").defaultValue(true).build());

    private final Setting<Boolean> danger = sg.add(new BoolSetting.Builder()
        .name("danger-outline").description("A pulsing theme-coloured outline on charged anchors, faster the more charged.").defaultValue(true).build());

    private final Setting<Boolean> booms = sg.add(new BoolSetting.Builder()
        .name("explosion-shockwave").description("A shockwave ring and flash when a charged anchor blows up.").defaultValue(true).build());

    private final Setting<Double> size = sg.add(new DoubleSetting.Builder()
        .name("shockwave-size").description("How far the shockwave spreads (blocks).").defaultValue(4).range(0.5, 10).sliderRange(1, 7).build());

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colour theme: each one flows between its own colours. Custom uses the colour below.").defaultValue(FxTheme.Inferno).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Custom theme: glow and shockwave colour.").defaultValue(new SettingColor(255, 120, 40, 210)).visible(() -> theme.get() == FxTheme.Custom).build());

    private record Boom(Vec3d pos, double start) {}

    private static final double BOOM_SECONDS = 0.7;

    private final Map<BlockPos, Integer> anchors = new HashMap<>();
    private final List<Boom> active = new ArrayList<>();
    private final List<Boom> pulses = new ArrayList<>();

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> embers = feature("embers", "Embers rising off charged anchors.");
    private final Setting<Boolean> chargePulse = feature("charge-pulse", "A ring pulses out each time an anchor gets charged.");
    private final Setting<Boolean> fullBeam = feature("full-beam", "A warning beam above fully charged anchors.");
    private final Setting<Boolean> blastSparks = feature("explosion-sparks", "Sparks fly out when an anchor blows up.");
    private final Setting<Boolean> swirl = feature("charge-swirl", "Glowstone-coloured sparks spiral into charged anchors.");

    public AnchorFX() {
        super("anchor-fx", "Respawn anchors show their charge, glow when live and blow with a shockwave. Only you see it.");
    }

    @Override
    public void onDeactivate() {
        anchors.clear();
        active.clear();
        pulses.clear();
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null || mc.world == null) return;
        if (low() && mc.world.getTime() % 4 != 0) return;
        Map<BlockPos, Integer> now = new HashMap<>();
        int r = range.get();
        BlockPos me = mc.player.getBlockPos();
        for (BlockPos p : BlockPos.iterate(me.add(-r, -r, -r), me.add(r, r, r))) {
            BlockState s = mc.world.getBlockState(p);
            if (s.isOf(Blocks.RESPAWN_ANCHOR)) now.put(p.toImmutable(), s.get(RespawnAnchorBlock.CHARGES));
        }
        if (booms.get()) {
            anchors.forEach((p, charges) -> {
                if (charges > 0 && !now.containsKey(p) && p.isWithinDistance(me, r - 1)) active.add(new Boom(Vec3d.ofBottomCenter(p), Fx.time()));
            });
        }
        now.forEach((p, charges) -> {
            Integer before = anchors.get(p);
            if (before != null && charges > before) pulses.add(new Boom(Vec3d.ofCenter(p), Fx.time()));
        });
        anchors.clear();
        anchors.putAll(now);
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        double t = Fx.time();
        for (Map.Entry<BlockPos, Integer> a : anchors.entrySet()) {
            Vec3d mid = Vec3d.ofCenter(a.getKey());
            int charges = a.getValue();
            if (pips.get()) {
                for (int i = 0; i < 4; i++) {
                    double ang = t * 1.5 + i * Math.PI / 2;
                    Vec3d at = mid.add(Math.cos(ang) * 0.45, 0.85 + 0.05 * Math.sin(t * 3 + i), Math.sin(ang) * 0.45);
                    Color c = i < charges ? chargeColor(charges) : new Color(120, 120, 120, 70);
                    outline(event.renderer, at, i < charges ? 0.07 : 0.045, c);
                }
            }
            if (danger.get() && charges > 0) {
                double pulse = 0.5 + 0.5 * Math.sin(t * (3 + charges * 2));
                Color tc = col(t * 0.2 + a.getKey().hashCode() * 0.01);
                outline(event.renderer, new Box(a.getKey()).expand(0.01 + 0.04 * pulse), Fx.alpha(tc, (int) (150 + 105 * pulse)));
                if (on(fullBeam) && charges == 4) beam(event.renderer, Vec3d.ofBottomCenter(a.getKey()).add(0, 1, 0), 0.18, 8, Fx.alpha(col(t * 0.3), (int) (70 + 60 * pulse)));
                if (on(swirl)) {
                    for (int k = 0; k < charges * 3; k++) {
                        double life = (t * 0.6 + k * 0.173) % 1, ang = k * 2.1 + life * 5;
                        double rad = 1.1 * (1 - life);
                        Vec3d p = mid.add(Math.cos(ang) * rad, 0.9 * (1 - life) - 0.1, Math.sin(ang) * rad);
                        spark(event.renderer, p, 0.07 + 0.05 * life, Fx.alpha(theme.get().secondary(life * 0.5 + t * 0.1, 255, color.get()), (int) (220 * life)), true);
                    }
                }
                if (on(embers)) {
                    for (int k = 0; k < charges * 2; k++) {
                        double life = (t * 0.7 + k * 0.137 + a.getKey().hashCode() * 0.01) % 1;
                        double ang = k * 2.4 + a.getKey().getX();
                        Vec3d p = mid.add(Math.cos(ang) * 0.35, 0.5 + life * 1.4, Math.sin(ang) * 0.35);
                        spark(event.renderer, p, 0.08 * (1 - life), Fx.alpha(chargeColor(charges), (int) (230 * (1 - life))), false);
                    }
                }
            }
        }
        active.removeIf(b -> t - b.start() > Math.max(BOOM_SECONDS, 1.0));
        for (Boom b : active) {
            double age = t - b.start();
            if (age < BOOM_SECONDS) shockwave(event.renderer, b.pos(), age / BOOM_SECONDS, size.get(), col(b.start() * 0.3));
            if (on(blastSparks) && age < 1.0) {
                for (int k = 0; k < 24; k++) {
                    double yaw = k * 2.39996 + b.start() * 5, up = 0.3 + 0.7 * ((k * 0.618) % 1);
                    Vec3d dir = new Vec3d(Math.cos(yaw) * (1 - up * 0.5), up, Math.sin(yaw) * (1 - up * 0.5));
                    Vec3d at = b.pos().add(0, 0.5, 0).add(dir.multiply(age * 8)).add(0, -9 * age * age, 0);
                    Color c = k % 3 == 0 ? theme.get().secondary(b.start() * 0.3, 255, color.get()) : col(b.start() * 0.3 + k * 0.02);
                    spark(event.renderer, at, 0.25 * (1 - age), Fx.alpha(c, (int) (255 * (1 - age))), true);
                }
            }
        }
        pulses.removeIf(b -> t - b.start() > 0.5);
        if (on(chargePulse)) for (Boom b : pulses) {
            double f = (t - b.start()) / 0.5, rad = 0.4 + 1.6 * (1 - Math.pow(1 - f, 3));
            ring(event.renderer, b.pos().add(0, 0.52, 0), rad - 0.1, rad, detail(40, 12), u -> Fx.alpha(chargeColor(Math.max(1, anchors.getOrDefault(BlockPos.ofFloored(b.pos()), 1))), (int) (230 * (1 - f))));
        }
    }

    private Color col(double t) {
        return theme.get().color(t, color.get().a, color.get());
    }

    private static Color chargeColor(int charges) {
        double f = (charges - 1) / 3.0;
        return new Color((int) (80 + 175 * f), (int) (230 - 170 * f), 60, 230);
    }
}
