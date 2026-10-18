package com.example.addon.modules;

import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import com.example.addon.visuals.VisualModule;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.utils.render.NametagUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.entity.vehicle.minecart.MinecartTNT;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;

public class TntTimer extends VisualModule {
    private static final double FULL_FUSE = 80;

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colour theme for the rings and outline. Custom uses the colour below.").defaultValue(FxTheme.Inferno).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Custom theme colour.").defaultValue(new SettingColor(255, 80, 40, 220)).visible(() -> theme.get() == FxTheme.Custom).build());

    private final Setting<Double> scale = sg.add(new DoubleSetting.Builder()
        .name("scale").description("Size of the countdown.").defaultValue(1.6).range(0.5, 4).sliderRange(0.8, 3).build());

    private final Setting<Boolean> minecarts = sg.add(new BoolSetting.Builder()
        .name("tnt-minecarts").description("Also time lit TNT minecarts.").defaultValue(true).build());

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> blastRing = feature("blast-ring", "A ring on the ground showing roughly how far the blast reaches; it fills up as the fuse burns and pulses faster near the end.");
    private final Setting<Boolean> outlineBox = feature("outline", "A pulsing outline round the TNT that flashes white in the last second.");
    private final Setting<Boolean> fuseSparks = feature("fuse-sparks", "Sparks spitting off the top like a real fuse.");
    private final Setting<Boolean> bar = feature("countdown-bar", "A bar under the number that drains as the fuse burns.");

    public TntTimer() {
        super("tnt-timer", "A big countdown over lit TNT with a blast-radius ring, a pulsing outline and fuse sparks. Only you see it.");
    }

    private static int fuse(Entity e, boolean carts) {
        return e instanceof PrimedTnt t ? t.getFuse() : carts && e instanceof MinecartTNT m ? m.getFuse() : -1;
    }

    private Color col(double t, int alpha) {
        return theme.get().color(t, alpha, color.get());
    }

    @EventHandler
    private void onRender3D(Render3DEvent event) {
        if (mc.level == null) return;
        double now = Fx.time();
        for (Entity e : mc.level.entitiesForRendering()) {
            int fuse = fuse(e, minecarts.get());
            if (fuse < 0) continue;
            double left = Mth.clamp((fuse - event.tickDelta) / FULL_FUSE, 0, 1), done = 1 - left;
            double pulse = 0.5 + 0.5 * Math.sin(now * (4 + done * 22));
            Vec3 p = Fx.pos(e, event.tickDelta);
            if (on(blastRing)) {
                double r = 4;
                ring(event.renderer, p.add(0, 0.04, 0), r - 0.16, r, detail(72, 20),
                    u -> u <= done ? Fx.alpha(col(u + now * 0.2, 255), (int) (150 + 100 * pulse)) : Fx.alpha(col(u, 255), 35));
                ring(event.renderer, p.add(0, 0.05, 0), 0, r * done, detail(48, 12), u -> Fx.alpha(col(u + now * 0.1, 255), (int) (18 + 30 * pulse * done)));
            }
            if (on(outlineBox)) {
                AABB box = e.getBoundingBox().move(p.subtract(e.position())).inflate(0.04 + 0.08 * pulse);
                Color c = fuse < 20 && (int) (now * 10) % 2 == 0 ? new Color(255, 255, 255, 255) : col(now * 0.3, 255);
                outline(event.renderer, box, c);
            }
            if (on(fuseSparks)) {
                for (int k = 0; k < 5; k++) {
                    double life = (now * 2.2 + k * 0.21 + e.getId() * 0.07) % 1, ang = k * 2.4 + e.getId();
                    Vec3 at = p.add(Math.cos(ang) * 0.25 * life, e.getBbHeight() + 0.1 + life * 0.6 - life * life * 0.4, Math.sin(ang) * 0.25 * life);
                    spark(event.renderer, at, 0.09 * (1 - life), Fx.alpha(theme.get().secondary(life, 255, color.get()), (int) (255 * (1 - life))), true);
                }
            }
        }
    }

    @EventHandler
    private void onRender2D(Render2DEvent event) {
        if (mc.level == null) return;
        for (Entity e : mc.level.entitiesForRendering()) {
            int fuse = fuse(e, minecarts.get());
            if (fuse < 0) continue;
            Vec3 p = Fx.pos(e, event.tickDelta);
            Vector3d v = new Vector3d(p.x, p.y + e.getBbHeight() + 0.9, p.z);
            double s = scale.get() * (fuse < 20 ? 1 + 0.25 * Math.abs(Math.sin(Fx.time() * 12)) : 1);
            if (!NametagUtils.to2D(v, s)) continue;
            double left = Mth.clamp(fuse / FULL_FUSE, 0, 1);
            String text = String.format("%.1f", fuse / 20.0);
            int textColor = 0xFF000000 | (int) (255 * (1 - left) + 80 * left) << 16 | (int) (255 * left + 60 * (1 - left)) << 8 | 70;
            NametagUtils.begin(v, event.graphics);
            int w = mc.font.width(text);
            event.graphics.fill(-w / 2 - 4, -3, w / 2 + 4, bar.get() ? 13 : 10, 0xA0000000);
            event.graphics.text(mc.font, text, -w / 2, 0, textColor);
            if (on(bar)) {
                int bw = Math.max(w + 4, 24);
                event.graphics.fill(-bw / 2, 10, bw / 2, 12, 0xFF202020);
                event.graphics.fill(-bw / 2, 10, -bw / 2 + (int) (bw * left), 12, col(Fx.time() * 0.3, 255).getPacked());
            }
            NametagUtils.end(event.graphics);
        }
    }
}
