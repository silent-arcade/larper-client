package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.hit.EntityHitResult;

/** Your own crosshair: cross, dot, circle or chevron, that spreads while you move and shows your attack cooldown. */
public class CustomCrosshair extends Module {
    public enum Style { Cross, Dot, Circle, Chevron }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Style> style = sg.add(new EnumSetting.Builder<Style>()
        .name("style").description("Crosshair shape.").defaultValue(Style.Cross).build());

    private final Setting<Integer> size = sg.add(new IntSetting.Builder()
        .name("size").description("Arm length / radius (pixels).").defaultValue(5).range(1, 30).sliderRange(2, 15).build());

    private final Setting<Integer> thickness = sg.add(new IntSetting.Builder()
        .name("thickness").description("Line thickness (pixels).").defaultValue(1).range(1, 6).sliderRange(1, 4).build());

    private final Setting<Integer> gap = sg.add(new IntSetting.Builder()
        .name("gap").description("Empty space in the middle (pixels).").defaultValue(2).range(0, 20).sliderRange(0, 10).build());

    private final Setting<Boolean> dynamic = sg.add(new BoolSetting.Builder()
        .name("dynamic").description("Spreads out while you move or fall.").defaultValue(true).build());

    private final Setting<Boolean> centerDot = sg.add(new BoolSetting.Builder()
        .name("center-dot").description("A dot in the middle as well.").defaultValue(false).build());

    private final Setting<Boolean> outline = sg.add(new BoolSetting.Builder()
        .name("outline").description("Dark outline so it shows on bright backgrounds.").defaultValue(true).build());

    private final Setting<Boolean> cooldown = sg.add(new BoolSetting.Builder()
        .name("cooldown-bar").description("A small bar under it while your attack recharges.").defaultValue(true).build());

    private final Setting<SettingColor> color = sg.add(new ColorSetting.Builder()
        .name("color").description("Crosshair colour.").defaultValue(new SettingColor(120, 255, 200)).build());

    private final Setting<SettingColor> targetColor = sg.add(new ColorSetting.Builder()
        .name("on-target-color").description("Colour while it's on a mob or player.").defaultValue(new SettingColor(255, 80, 80)).build());

    private double spread;

    public CustomCrosshair() {
        super(AddonTemplate.CATEGORY, "custom-crosshair", "Your own crosshair style, colour and size. Only you see it.");
    }

    /** The vanilla crosshair is hidden while this is on (see InGameHudMixin). */
    public boolean replacesVanilla() {
        return isActive();
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.player == null || mc.options.hudHidden || !mc.options.getPerspective().isFirstPerson() || mc.currentScreen != null) return;
        DrawContext ctx = event.drawContext;
        int cx = ctx.getScaledWindowWidth() / 2, cy = ctx.getScaledWindowHeight() / 2;
        double speed = mc.player.getVelocity().horizontalLength() * 20 + Math.abs(Math.min(0, mc.player.getVelocity().y)) * 10;
        spread += ((dynamic.get() ? Math.min(6, speed * 0.8) : 0) - spread) * 0.2; // ease toward the target spread
        int g = gap.get() + (int) Math.round(spread), s = size.get(), t = thickness.get();
        boolean onTarget = mc.crosshairTarget instanceof EntityHitResult;
        int c = (onTarget ? targetColor.get() : color.get()).getPacked();

        switch (style.get()) {
            case Cross -> {
                rect(ctx, cx - g - s, cy - t / 2, s, t, c);
                rect(ctx, cx + g + 1, cy - t / 2, s, t, c);
                rect(ctx, cx - t / 2, cy - g - s, t, s, c);
                rect(ctx, cx - t / 2, cy + g + 1, t, s, c);
            }
            case Dot -> rect(ctx, cx - (s + 1) / 2, cy - (s + 1) / 2, s + 1, s + 1, c);
            case Circle -> {
                int r = s + g, n = Math.max(24, r * 6);
                for (int i = 0; i < n; i++) {
                    double a = Math.PI * 2 * i / n;
                    rect(ctx, (int) Math.round(cx + Math.cos(a) * r - t / 2.0), (int) Math.round(cy + Math.sin(a) * r - t / 2.0), t, t, c);
                }
            }
            case Chevron -> {
                for (int i = 0; i < s; i++) {
                    rect(ctx, cx - i - t / 2, cy + g + i, t, t, c);
                    rect(ctx, cx + i - t / 2, cy + g + i, t, t, c);
                }
            }
        }
        if (centerDot.get() && style.get() != Style.Dot) rect(ctx, cx - t / 2, cy - t / 2, t, t, c);

        float charge = mc.player.getAttackCooldownProgress(0);
        if (cooldown.get() && charge < 1) {
            int w = 16, y = cy + g + s + 4;
            ctx.fill(cx - w / 2, y, cx + w / 2, y + 2, 0x90000000);
            ctx.fill(cx - w / 2, y, cx - w / 2 + Math.round(w * charge), y + 2, c);
        }
    }

    // A filled rectangle, with a 1px dark outline when enabled
    private void rect(DrawContext ctx, int x, int y, int w, int h, int color) {
        if (outline.get()) ctx.fill(x - 1, y - 1, x + w + 1, y + h + 1, 0xB0000000);
        ctx.fill(x, y, x + w, y + h, color);
    }
}
