package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.TimeChanger;

public class TimePresets extends Module {
    public enum Preset {
        Sunrise(-700), Day(6000), Sunset(12700), Night(18000);

        final double time;

        Preset(double time) {
            this.time = time;
        }
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Preset> preset = sg.add(new EnumSetting.Builder<Preset>()
        .name("preset").description("The time of day you see. Only on your screen - the server's time doesn't change.")
        .defaultValue(Preset.Night).onChanged(p -> apply()).build());

    private boolean turnedOn;

    public TimePresets() {
        super(AddonTemplate.VISUAL, "time-presets", "Pick Sunrise, Day, Sunset or Night for your own screen (uses Meteor's Time Changer).");
    }

    @Override
    public void onActivate() {
        apply();
    }

    @Override
    public void onDeactivate() {
        TimeChanger tc = Modules.get().get(TimeChanger.class);
        if (turnedOn && tc.isActive()) tc.toggle();
        turnedOn = false;
    }

    @SuppressWarnings("unchecked")
    private void apply() {
        if (!isActive()) return;
        TimeChanger tc = Modules.get().get(TimeChanger.class);
        ((Setting<Double>) tc.settings.get("time")).set(preset.get().time);
        if (!tc.isActive()) {
            tc.toggle();
            turnedOn = true;
        }
    }
}
