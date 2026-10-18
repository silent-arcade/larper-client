package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;

public class Aspect extends Module {
    public enum Preset {
        Stretched4x3(4 / 3.0), Stretched5x4(5 / 4.0), Stretched16x10(16 / 10.0), Square(1), Ultrawide21x9(21 / 9.0), Custom(0);

        final double ratio;

        Preset(double ratio) {
            this.ratio = ratio;
        }
    }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Preset> preset = sg.add(new EnumSetting.Builder<Preset>()
        .name("preset").description("The look you want. 4:3 stretched is what most PvPers use; Ultrawide squishes things instead.").defaultValue(Preset.Stretched4x3).build());

    private final Setting<Double> ratio = sg.add(new DoubleSetting.Builder()
        .name("ratio").description("Your own aspect ratio (width / height).").defaultValue(1.33).range(0.5, 3).sliderRange(1, 2.5)
        .visible(() -> preset.get() == Preset.Custom).build());

    private final Setting<Double> smooth = sg.add(new DoubleSetting.Builder()
        .name("transition").description("Seconds to smoothly stretch in when you turn it on (0 = instant).").defaultValue(0.4).range(0, 3).sliderRange(0, 1.5).build());

    private long since;

    public Aspect() {
        super(AddonTemplate.VISUAL, "aspect", "Stretched resolution presets (4:3, 5:4, 16:10, square, ultrawide) that ease in smoothly. Your HUD stays normal.");
    }

    @Override
    public void onActivate() {
        since = System.nanoTime();
    }

    // how much to stretch the world sideways
    public float stretch() {
        float window = (float) mc.getWindow().getFramebufferWidth() / Math.max(1, mc.getWindow().getFramebufferHeight());
        double want = preset.get() == Preset.Custom ? ratio.get() : preset.get().ratio;
        double full = window / want;
        double k = smooth.get() <= 0 ? 1 : Math.min(1, (System.nanoTime() - since) / 1e9 / smooth.get());
        k = k * k * (3 - 2 * k);
        return (float) (1 + (full - 1) * k);
    }
}
