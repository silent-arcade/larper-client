package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

public class RenderMethod extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> low = sg.add(new IntSetting.Builder()
        .name("low-distance").description("Render distance to switch to.").defaultValue(2).range(2, 32).sliderRange(2, 8).build());

    private final Setting<Integer> holdMs = sg.add(new IntSetting.Builder()
        .name("hold-ms").description("How long to stay at the low distance before switching back (1000 = 1 second).").defaultValue(1000).range(50, 10000).sliderRange(100, 3000).build());

    private final Setting<Boolean> auto = sg.add(new BoolSetting.Builder()
        .name("auto").description("Stay on and trigger whenever you go below the Y level. Off = trigger once when turned on, then turn off.").defaultValue(false).build());

    private final Setting<Integer> yLevel = sg.add(new IntSetting.Builder()
        .name("y-level").description("Auto: trigger when you go below this Y.").defaultValue(0).range(-64, 320).sliderRange(-64, 128).visible(auto::get).build());

    private final Setting<Boolean> notify = sg.add(new BoolSetting.Builder()
        .name("chat-info").description("Say in chat when it switches.").defaultValue(true).build());

    private int saved = -1;
    private long restoreAt;
    private boolean wasBelow;

    public RenderMethod() {
        super(AddonTemplate.CATEGORY, "render-method", "Drops your render distance to 2 for a moment, then back to normal. Auto mode triggers below a Y level.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton now = theme.button("Trigger now");
        now.action = () -> {
            if (!isActive()) toggle();
            else trigger();
        };
        return now;
    }

    @Override
    public void onActivate() {
        wasBelow = mc.player != null && mc.player.getY() < yLevel.get();
        if (!auto.get()) trigger();
    }

    @Override
    public void onDeactivate() {
        restore();
    }

    private void trigger() {
        if (mc.options == null || saved != -1) return;
        saved = mc.options.getViewDistance().getValue();
        mc.options.getViewDistance().setValue(low.get());
        restoreAt = System.currentTimeMillis() + holdMs.get();
        if (notify.get()) info("Render distance %d -> %d", saved, low.get());
    }

    private void restore() {
        if (saved == -1 || mc.options == null) return;
        mc.options.getViewDistance().setValue(saved);
        if (notify.get()) info("Render distance back to %d", saved);
        saved = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (saved != -1 && System.currentTimeMillis() >= restoreAt) {
            restore();
            if (!auto.get()) toggle();
            return;
        }
        if (!auto.get() || mc.player == null) return;
        boolean below = mc.player.getY() < yLevel.get();
        if (below && !wasBelow) trigger();
        wasBelow = below;
    }
}
