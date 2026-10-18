package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.option.KeyBinding;

public class SnapTap extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> forwardBack = sg.add(new BoolSetting.Builder()
        .name("forward-back").description("Also for W and S, not only A and D.").defaultValue(true).build());

    // per axis: which key was pressed last (true = the first key of the pair)
    private boolean lastA, lastW, prevA, prevD, prevW, prevS;

    public SnapTap() {
        super(AddonTemplate.CATEGORY, "snap-tap", "When you hold both A and D (or W and S), the one you pressed last wins instead of stopping you.");
    }

    @Override
    public void onDeactivate() {
        for (KeyBinding k : new KeyBinding[] {mc.options.leftKey, mc.options.rightKey, mc.options.forwardKey, mc.options.backKey}) k.setPressed(Input.isPressed(k));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.currentScreen != null) return;
        boolean a = Input.isPressed(mc.options.leftKey), d = Input.isPressed(mc.options.rightKey);
        if (a && !prevA) lastA = true;
        if (d && !prevD) lastA = false;
        prevA = a;
        prevD = d;
        mc.options.leftKey.setPressed(a && (!d || lastA));
        mc.options.rightKey.setPressed(d && (!a || !lastA));
        if (!forwardBack.get()) return;
        boolean w = Input.isPressed(mc.options.forwardKey), s = Input.isPressed(mc.options.backKey);
        if (w && !prevW) lastW = true;
        if (s && !prevS) lastW = false;
        prevW = w;
        prevS = s;
        mc.options.forwardKey.setPressed(w && (!s || lastW));
        mc.options.backKey.setPressed(s && (!w || !lastW));
    }
}
