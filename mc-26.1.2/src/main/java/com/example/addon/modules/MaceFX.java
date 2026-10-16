package com.example.addon.modules;

import com.example.addon.visuals.Fx;
import com.example.addon.visuals.VisualModule;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

public class MaceFX extends VisualModule {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> landing = sg.add(new BoolSetting.Builder()
        .name("landing-target").description("While falling with a mace: a ring where you'll land, growing and turning red the further you fall.").defaultValue(true).build());

    private final Setting<Boolean> preview = sg.add(new BoolSetting.Builder()
        .name("damage-preview").description("Shows the bonus smash damage you'd deal right now, under your crosshair.").defaultValue(true).build());

    private final Setting<Boolean> smash = sg.add(new BoolSetting.Builder()
        .name("smash-shockwave").description("A shockwave, ground cracks and a light pillar when you smash something.").defaultValue(true).build());

    private final Setting<Boolean> debris = sg.add(new BoolSetting.Builder()
        .name("debris").description("Chunks of the ground fly up on a smash.").defaultValue(true).build());

    private final Setting<SettingColor> lowColor = sg.add(new ColorSetting.Builder()
        .name("charge-color").description("Colour of a short fall.").defaultValue(new SettingColor(255, 220, 60, 220)).build());

    private final Setting<SettingColor> highColor = sg.add(new ColorSetting.Builder()
        .name("max-color").description("Colour of a big fall (and the smash).").defaultValue(new SettingColor(255, 40, 40, 235)).build());

    private record Smash(Vec3 pos, double start, double power, long seed) {}

    private final List<Smash> smashes = new ArrayList<>();
    private final Random rng = new Random();

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> chargeGlow = feature("charge-glow", "While falling: a guide line down to the target and a glow on you.");
    private final Setting<Boolean> cracks = feature("ground-cracks", "Smash: cracks racing out over the ground.");
    private final Setting<Boolean> pillarOn = feature("light-pillar", "Smash: a white pillar of light.");

    public MaceFX() {
        super("mace-fx", "Mace visuals: landing target, smash damage preview and a huge shockwave on the hit. Only you see it.");
    }

    @Override
    public void onDeactivate() {
        smashes.clear();
    }

    private boolean holdingMace() {
        return mc.player != null && mc.player.getMainHandItem().is(Items.MACE);
    }

    private boolean charging() {
        return holdingMace() && !mc.player.onGround() && mc.player.fallDistance > 1.5 && !mc.player.isFallFlying();
    }

    static double bonus(double fall) {
        if (fall <= 1.5) return 0;
        if (fall <= 3) return 4 * fall;
        if (fall <= 8) return 12 + 2 * (fall - 3);
        return 22 + (fall - 8);
    }

    private double charge() {
        return Math.min(1, mc.player.fallDistance / 20.0);
    }

    private Color chargeColor(double k) {
        Color a = lowColor.get(), b = highColor.get();
        return new Color((int) (a.r + (b.r - a.r) * k), (int) (a.g + (b.g - a.g) * k), (int) (a.b + (b.b - a.b) * k), (int) (a.a + (b.a - a.a) * k));
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!smash.get() || !holdingMace() || mc.player.fallDistance <= 1.5) return;
        double power = Math.min(1.6, 0.4 + mc.player.fallDistance / 12.0);
        Vec3 at = event.entity.position();
        smashes.add(new Smash(at, Fx.time(), power, rng.nextLong()));
        if (!debris.get()) return;
        BlockState ground = mc.level.getBlockState(BlockPos.containing(at).below());
        if (ground.isAir()) return;
        var effect = new BlockParticleOption(ParticleTypes.BLOCK, ground);
        for (int i = 0, n = detail((int) (30 * power), 8); i < n; i++) {
            double a = rng.nextDouble() * Math.PI * 2, s = 0.15 + rng.nextDouble() * 0.35 * power;
            mc.particleEngine.createParticle(effect, at.x, at.y + 0.1, at.z, Math.cos(a) * s, 0.25 + rng.nextDouble() * 0.45 * power, Math.sin(a) * s);
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.player == null || mc.level == null) return;
        double now = Fx.time();

        if (landing.get() && charging()) {
            Vec3 from = Fx.pos(mc.player, event.tickDelta);
            BlockHitResult hit = mc.level.clip(new ClipContext(from, from.add(0, -96, 0), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, mc.player));
            if (hit.getType() == HitResult.Type.BLOCK) {
                double k = charge(), r = 0.6 + 1.6 * k, spin = now * (2 + 4 * k);
                Color c = chargeColor(k);
                Vec3 g = hit.getLocation().add(0, 0.03, 0);
                double pulse = 0.85 + 0.15 * Math.sin(now * (6 + 10 * k));
                ring(event.renderer, g, r * 0.88 * pulse, r * pulse, 48, u -> c);
                ring(event.renderer, g, 0, r * 0.25, 24, u -> Fx.alpha(c, c.a / 2));
                for (int i = 0; i < 4; i++) {
                    double a = spin + i * Math.PI / 2;
                    Vec3 p0 = g.add(Math.cos(a) * r * 0.45, 0, Math.sin(a) * r * 0.45), p1 = g.add(Math.cos(a) * r * 0.75, 0, Math.sin(a) * r * 0.75);
                    event.renderer.line(p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, c);
                }
                if (on(chargeGlow)) {
                    event.renderer.line(from.x, from.y, from.z, g.x, g.y, g.z, Fx.alpha(c, 60), Fx.alpha(c, 160));
                    glow(event.renderer, from.add(0, mc.player.getBbHeight() * 0.55, 0), 0.25 + 0.35 * k, Fx.alpha(c, (int) (120 + 100 * k)));
                }
            }
        }

        smashes.removeIf(s -> now - s.start() > 0.9);
        for (Smash s : smashes) {
            double f = (now - s.start()) / 0.9;
            Color c = highColor.get();
            shockwave(event.renderer, s.pos(), f, 3 + 3 * s.power(), c);
            if (on(cracks)) {
                Random cr = new Random(s.seed());
                double reach = (1 - Math.pow(1 - Math.min(1, f * 2), 2)) * (2 + 2.5 * s.power());
                for (int i = 0; i < 9; i++) {
                    double a = cr.nextDouble() * Math.PI * 2, len = reach * (0.6 + cr.nextDouble() * 0.4);
                    Vec3 p = s.pos().add(0, 0.04, 0);
                    for (int k = 0; k < 4; k++) {
                        double seg = len / 4;
                        a += (cr.nextDouble() - 0.5) * 0.6;
                        Vec3 q = p.add(Math.cos(a) * seg, 0, Math.sin(a) * seg);
                        event.renderer.line(p.x, p.y, p.z, q.x, q.y, q.z, Fx.alpha(c, (int) (255 * (1 - f))));
                        p = q;
                    }
                }
            }
            if (on(pillarOn) && f < 0.25) beam(event.renderer, s.pos(), 0.25 + 0.4 * s.power(), 12 * s.power() * (1 - f * 2), Fx.alpha(Color.WHITE, (int) (200 * (1 - f / 0.25))));
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (!preview.get() || mc.options.hideGui || !charging()) return;
        String text = String.format("SMASH +%.0f", bonus(mc.player.fallDistance));
        Color c = chargeColor(charge());
        var ctx = event.graphics;
        int w = mc.font.width(text), x = ctx.guiWidth() / 2 - w / 2, y = ctx.guiHeight() / 2 + 14;
        ctx.fill(x - 3, y - 2, x + w + 3, y + 10, 0x90000000);
        ctx.text(mc.font, text, x, y, 0xFF000000 | c.r << 16 | c.g << 8 | c.b);
    }
}
