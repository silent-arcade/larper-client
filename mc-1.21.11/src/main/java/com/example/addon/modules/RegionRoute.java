package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.regions.DonutRegions;
import meteordevelopment.meteorclient.events.render.Render2DEvent;
import meteordevelopment.meteorclient.settings.ColorSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.waypoints.Waypoint;
import meteordevelopment.meteorclient.systems.waypoints.Waypoints;
import meteordevelopment.meteorclient.utils.world.Dimension;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;

public class RegionRoute extends Module {
    private static final String WAYPOINT = "Region route";

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> target = sg.add(new IntSetting.Builder()
        .name("to-region").description("The region number you want to go to (as on the Region Map).")
        .defaultValue(5).range(1, 113).sliderRange(1, 113)
        .onChanged(v -> placeWaypoint())
        .build());

    private final Setting<Boolean> waypoint = sg.add(new BoolSetting.Builder()
        .name("waypoint").description("Put a Meteor waypoint at the region's centre.")
        .defaultValue(true)
        .onChanged(v -> placeWaypoint())
        .build());

    private final SettingGroup sgText = settings.createGroup("Position");

    private final Setting<Double> posX = sgText.add(new DoubleSetting.Builder()
        .name("x").description("Across the screen: 0 = left edge, 50 = middle, 100 = right edge.")
        .defaultValue(50).range(0, 100).sliderRange(0, 100).build());

    private final Setting<Double> posY = sgText.add(new DoubleSetting.Builder()
        .name("y").description("Down the screen: 0 = top, 100 = bottom.")
        .defaultValue(2).range(0, 100).sliderRange(0, 100).build());

    private final Setting<Double> scale = sgText.add(new DoubleSetting.Builder()
        .name("scale").description("Text size.").defaultValue(1).range(0.5, 3).sliderRange(0.5, 2).build());

    private final Setting<SettingColor> color = sgText.add(new ColorSetting.Builder()
        .name("color").description("Text colour.").defaultValue(new SettingColor(255, 216, 102)).build());

    public RegionRoute() {
        super(AddonTemplate.CATEGORY, "region-route", "DonutSMP: which way and how far to another region.");
    }

    @Override
    public void onActivate() {
        placeWaypoint();
    }

    @Override
    public void onDeactivate() {
        removeWaypoint();
    }

    @EventHandler
    private void onRender(Render2DEvent event) {
        if (mc.options.hudHidden) return;
        String[] lines = lines();
        if (lines == null) return;
        var ctx = event.drawContext;
        float s = scale.get().floatValue();
        int w = 0, lineH = mc.textRenderer.fontHeight + 2;
        for (String l : lines) w = Math.max(w, mc.textRenderer.getWidth(l));
        double sw = ctx.getScaledWindowWidth() / s, sh = ctx.getScaledWindowHeight() / s;
        int bx = (int) Math.max(0, Math.min(sw - w, sw * posX.get() / 100 - w / 2.0));
        int by = (int) Math.max(0, Math.min(sh - lineH * lines.length, sh * posY.get() / 100));
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(s, s);
        for (int i = 0; i < lines.length; i++) {
            int lw = mc.textRenderer.getWidth(lines[i]);
            ctx.drawTextWithShadow(mc.textRenderer, lines[i], bx + (w - lw) / 2, by + i * lineH, color.get().getPacked());
        }
        ctx.getMatrices().popMatrix();
    }

    private String[] lines() {
        if (mc.player == null || mc.world == null) return null;
        int to = target.get();
        double[] c = DonutRegions.center(to);
        String[] lines;
        if (c == null) lines = new String[]{"Region " + to + " isn't on the DonutSMP map"};
        else {
            double scale = mc.world.getRegistryKey() == World.NETHER ? 8 : 1;
            double x = mc.player.getX() * scale, z = mc.player.getZ() * scale;
            int here = DonutRegions.regionAt(x, z);
            double dist = Math.hypot(c[0] - x, c[1] - z);
            float yaw = DonutRegions.yawTo(x, z, c[0], c[1]);
            lines = here == to
                ? new String[]{String.format("You're in region %d (%s)", to, DonutRegions.continent(to))}
                : new String[]{
                    String.format("Region %d (%s): %,d blocks %s, %s", to, DonutRegions.continent(to), Math.round(dist / scale),
                        DonutRegions.compass(yaw), DonutRegions.turn(mc.player.getYaw(), yaw)),
                    DonutRegions.axes((c[0] - x) / scale, (c[1] - z) / scale),
                    String.format("Overworld X %,d, Z %,d  |  Nether %s", Math.round(c[0]), Math.round(c[1]), DonutRegions.nether(c[0], c[1]))
                };
        }
        return lines;
    }

    public void placeWaypoint() {
        removeWaypoint();
        if (!isActive() || !waypoint.get() || mc.player == null) return;
        double[] c = DonutRegions.center(target.get());
        if (c == null) return;
        Waypoints.get().add(new Waypoint.Builder().name(WAYPOINT).pos(BlockPos.ofFloored(c[0], mc.player.getY(), c[1]))
            .dimension(Dimension.Overworld).build());
    }

    private void removeWaypoint() {
        Waypoint old = Waypoints.get().get(WAYPOINT);
        if (old != null) Waypoints.get().remove(old);
    }

    public void setTarget(int region) {
        target.set(region);
    }
}
