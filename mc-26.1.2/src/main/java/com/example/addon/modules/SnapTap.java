package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.KeyMapping;

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
        for (KeyMapping k : new KeyMapping[] {mc.options.keyLeft, mc.options.keyRight, mc.options.keyUp, mc.options.keyDown}) k.setDown(Input.isPressed(k));
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.screen != null) return;
        boolean a = Input.isPressed(mc.options.keyLeft), d = Input.isPressed(mc.options.keyRight);
        if (a && !prevA) lastA = true;
        if (d && !prevD) lastA = false;
        prevA = a;
        prevD = d;
        mc.options.keyLeft.setDown(a && (!d || lastA));
        mc.options.keyRight.setDown(d && (!a || !lastA));
        if (!forwardBack.get()) return;
        boolean w = Input.isPressed(mc.options.keyUp), s = Input.isPressed(mc.options.keyDown);
        if (w && !prevW) lastW = true;
        if (s && !prevS) lastW = false;
        prevW = w;
        prevS = s;
        mc.options.keyUp.setDown(w && (!s || lastW));
        mc.options.keyDown.setDown(s && (!w || !lastW));
    }
}
