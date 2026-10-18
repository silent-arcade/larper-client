package com.example.addon.modules;

import com.example.addon.visuals.Fx;
import com.example.addon.visuals.FxTheme;
import com.example.addon.visuals.VisualModule;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

public class SkeletonESP extends VisualModule {
    public enum ColorBy { Theme, Health, Distance }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<ColorBy> colorBy = sg.add(new EnumSetting.Builder<ColorBy>()
        .name("color-by").description("Theme: flowing theme colours. Health: green when healthy, red when low. Distance: red close, green far.").defaultValue(ColorBy.Theme).build());

    private final Setting<FxTheme> theme = sg.add(new EnumSetting.Builder<FxTheme>()
        .name("theme").description("Colour theme. Custom uses the colour below.").defaultValue(FxTheme.Frost).visible(() -> colorBy.get() == ColorBy.Theme).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Custom theme colour.").defaultValue(new SettingColor(255, 255, 255, 255))
        .visible(() -> colorBy.get() == ColorBy.Theme && theme.get() == FxTheme.Custom).build());

    private final Setting<SettingColor> friendColor = sg.add(new ColorSetting.Builder()
        .name("friend-color").description("Friends are always this colour.").defaultValue(new SettingColor(80, 255, 120, 255)).build());

    private final Setting<Boolean> self = sg.add(new BoolSetting.Builder()
        .name("self").description("Also draw yours in third person.").defaultValue(false).build());

    private final Setting<Boolean> thick = sg.add(new BoolSetting.Builder()
        .name("thick").description("Draw every bone three times side by side so it reads from far away.").defaultValue(true).build());

    private final Setting<Boolean> bloomOn = addBloom();
    private final Setting<Boolean> joints = feature("joints", "Glowing dots on the shoulders, elbows, hips and knees.");
    private final Setting<Boolean> skull = feature("skull", "An outlined cube for the head that turns where they look.");
    private final Setting<Boolean> heartbeat = feature("heartbeat", "The skeleton pulses brighter when they take damage.");

    public SkeletonESP() {
        super("skeleton-esp", "A glowing stick-figure skeleton inside every player that moves with their arms, legs, sneaking and head, through walls.");
    }

    private Color colorFor(Player p, double u) {
        if (Friends.get().isFriend(p)) return friendColor.get();
        return switch (colorBy.get()) {
            case Theme -> theme.get().color(u + Fx.time() * 0.25, 255, color.get());
            case Health -> {
                float f = Mth.clamp(p.getHealth() / p.getMaxHealth(), 0, 1);
                yield new Color((int) (255 * (1 - f)), (int) (60 + 195 * f), 60, 255);
            }
            case Distance -> {
                float f = (float) Mth.clamp(mc.player.distanceTo(p) / 40.0, 0, 1);
                yield new Color((int) (255 * (1 - f)), (int) (60 + 195 * f), 80, 255);
            }
        };
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.level == null) return;
        float d = event.tickDelta;
        for (Player p : mc.level.players()) {
            if (p == mc.player && (!self.get() || mc.options.getCameraType().isFirstPerson()) || p.isInvisible()) continue;
            Vec3 base = Fx.pos(p, d);
            double yaw = Math.toRadians(Mth.rotLerp(d, p.yBodyRotO, p.yBodyRot));
            float pos = p.walkAnimation.position(d), amp = Math.min(1, p.walkAnimation.speed(d));
            double leg = Math.cos(pos * 0.6662) * 1.4 * amp, arm = Math.cos(pos * 0.6662 + Math.PI) * amp;
            boolean sneak = p.isCrouching();
            double drop = sneak ? 0.25 : 0, lean = sneak ? 0.22 : 0, hipY = 0.75 - drop * 0.4, shY = 1.4 - drop;
            float swing = p.getAttackAnim(d);
            int flash = on(heartbeat) && p.hurtTime > 0 ? p.hurtTime * 18 : 0;

            Vec3 hip = local(base, yaw, 0, hipY, 0), neck = local(base, yaw, 0, 1.5 - drop, lean);
            bone(event, p, hip, neck, 0.1, flash);
            bone(event, p, local(base, yaw, -0.375, shY, lean), local(base, yaw, 0.375, shY, lean), 0.3, flash);
            bone(event, p, local(base, yaw, -0.12, hipY, 0), local(base, yaw, 0.12, hipY, 0), 0.5, flash);
            for (int side = -1; side <= 1; side += 2) {
                // right arm also follows the attack swing
                double a = arm * side + (side == 1 ? -Math.sin(swing * Math.PI) * 1.4 : 0), l = leg * side;
                Vec3 shoulder = local(base, yaw, side * 0.375, shY, lean);
                Vec3 elbow = local(base, yaw, side * 0.375, shY - 0.33 * Math.cos(a), 0.33 * Math.sin(a) + lean);
                Vec3 hand = local(base, yaw, side * 0.375, shY - 0.65 * Math.cos(a), 0.65 * Math.sin(a) + lean);
                Vec3 legTop = local(base, yaw, side * 0.12, hipY, 0);
                Vec3 knee = local(base, yaw, side * 0.12, hipY - 0.38 * Math.cos(l), 0.38 * Math.sin(l));
                Vec3 foot = local(base, yaw, side * 0.12, hipY - 0.75 * Math.cos(l), 0.75 * Math.sin(l));
                bone(event, p, shoulder, elbow, 0.6, flash);
                bone(event, p, elbow, hand, 0.7, flash);
                bone(event, p, legTop, knee, 0.8, flash);
                bone(event, p, knee, foot, 0.9, flash);
                if (on(joints)) for (Vec3 j : new Vec3[] {shoulder, elbow, legTop, knee}) spark(event.renderer, j, 0.07, Fx.alpha(colorFor(p, 0.4), 230), true);
            }
            if (on(skull)) {
                double headYaw = Math.toRadians(Mth.rotLerp(d, p.yHeadRotO, p.yHeadRot)), pitch = Math.toRadians(p.getViewXRot(d));
                Vec3 head = neck.add(0, 0.25, 0);
                outline(event.renderer, head, 0.22, Fx.alpha(colorFor(p, 0), Math.min(255, 200 + flash)));
                Vec3 look = new Vec3(-Math.sin(headYaw) * Math.cos(pitch), -Math.sin(pitch), Math.cos(headYaw) * Math.cos(pitch));
                line(event, head, head.add(look.scale(0.45)), Fx.alpha(colorFor(p, 0.1), 255));
            }
        }
    }

    private void bone(Render3DEvent e, Player p, Vec3 a, Vec3 b, double u, int flash) {
        Color c = colorFor(p, u);
        if (flash > 0) c = new Color(Math.min(255, c.r + flash), Math.min(255, c.g + flash / 2), Math.min(255, c.b + flash / 2), 255);
        line(e, a, b, c);
        if (!thick.get()) return;
        Vec3 off = new Vec3(0.012, 0.012, 0.012);
        line(e, a.add(off), b.add(off), c);
        line(e, a.subtract(off), b.subtract(off), c);
    }

    // x = to the player's right, z = forward, rotated by their body yaw
    private static Vec3 local(Vec3 base, double yaw, double x, double y, double z) {
        double s = Math.sin(yaw), co = Math.cos(yaw);
        return base.add(-x * co - z * s, y, -x * s + z * co);
    }

    private static void line(Render3DEvent e, Vec3 a, Vec3 b, Color c) {
        e.renderer.line(a.x, a.y, a.z, b.x, b.y, b.z, c);
    }
}
