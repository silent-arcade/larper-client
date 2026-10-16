package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;

public class NoFog extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> overworld = sg.add(new BoolSetting.Builder()
        .name("overworld").description("No fog in the Overworld.").defaultValue(true).build());

    private final Setting<Boolean> nether = sg.add(new BoolSetting.Builder()
        .name("nether").description("No fog in the Nether.").defaultValue(true).build());

    private final Setting<Boolean> end = sg.add(new BoolSetting.Builder()
        .name("end").description("No fog in the End.").defaultValue(true).build());

    private final Setting<Boolean> distance = sg.add(new BoolSetting.Builder()
        .name("distance-fog").description("Also remove the fade at the edge of your render distance (you'll see the hard edge of loaded chunks).")
        .defaultValue(false).build());

    public NoFog() {
        super(AddonTemplate.VISUAL, "no-fog", "Removes fog in the Overworld, Nether and End (each can be turned on or off).");
    }

    public boolean clearWorldFog() {
        if (!isActive() || mc.level == null || mc.player == null) return false;
        if (mc.gameRenderer.getMainCamera().getFluidInCamera() != FogType.NONE) return false;
        if (mc.player.hasEffect(MobEffects.BLINDNESS) || mc.player.hasEffect(MobEffects.DARKNESS)) return false;
        var dim = mc.level.dimension();
        if (dim == Level.NETHER) return nether.get();
        if (dim == Level.END) return end.get();
        return overworld.get();
    }

    public boolean clearDistanceFog() {
        return distance.get() && clearWorldFog();
    }
}
