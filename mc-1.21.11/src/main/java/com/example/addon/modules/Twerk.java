package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;

/** Crouches and stands up over and over. */
public class Twerk extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Integer> crouchTicks = sgGeneral.add(new IntSetting.Builder()
        .name("crouch-delay").description("Ticks to stay crouched (20 ticks = 1 second).").defaultValue(2).range(1, 40).sliderRange(1, 20)
        .build());

    private final Setting<Integer> standTicks = sgGeneral.add(new IntSetting.Builder()
        .name("stand-delay").description("Ticks to stay standing (20 ticks = 1 second).").defaultValue(2).range(1, 40).sliderRange(1, 20)
        .build());

    private boolean crouching;
    private int timer;

    public Twerk() {
        super(AddonTemplate.CATEGORY, "twerk", "Crouches and uncrouches fast.");
    }

    @Override
    public void onActivate() {
        crouching = false;
        timer = 0;
    }

    @Override
    public void onDeactivate() {
        mc.options.sneakKey.setPressed(false);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (--timer <= 0) {
            crouching = !crouching;
            timer = crouching ? crouchTicks.get() : standTicks.get();
        }
        mc.options.sneakKey.setPressed(crouching);
    }
}
