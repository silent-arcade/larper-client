package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;

import java.util.Random;

public class SpinBot extends Module {
    public enum Mode { Spin, Jitter, Random, Backwards, Sway, Helicopter }
    public enum Pitch { Keep, Down, Up, Nod, Random }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Spin: turns round and round. Jitter: snaps left and right. Random: a new direction every tick. Backwards: always facing behind you. Sway: swings side to side. Helicopter: very fast spin with your head down.").defaultValue(Mode.Spin).build());

    private final Setting<Integer> speed = sg.add(new IntSetting.Builder()
        .name("speed").description("How fast (degrees per tick for Spin, swing size for Jitter and Sway).").defaultValue(30).range(1, 180).sliderRange(5, 90).build());

    private final Setting<Pitch> pitch = sg.add(new EnumSetting.Builder<Pitch>()
        .name("pitch").description("Where your head points up and down.").defaultValue(Pitch.Keep).build());

    private final Setting<Boolean> pauseOnUse = sg.add(new BoolSetting.Builder()
        .name("pause-when-using").description("Stops while you eat, drink, place, mine or attack, so those still aim where you look.").defaultValue(true).build());

    private final Random rng = new Random();
    private float yaw;
    private int t;

    public SpinBot() {
        super(AddonTemplate.CATEGORY, "spin-bot", "Spins, jitters or sways your head for other players with six modes. Your own view doesn't move.");
    }

    @Override
    public void onActivate() {
        if (mc.player != null) yaw = mc.player.getYRot();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null) return;
        if (pauseOnUse.get() && (mc.player.isUsingItem() || mc.options.keyAttack.isDown() || mc.options.keyUse.isDown())) return;
        t++;
        float look = mc.player.getYRot(), s = speed.get();
        float y = switch (mode.get()) {
            case Spin -> yaw = (yaw + s) % 360;
            case Helicopter -> yaw = (yaw + Math.max(s, 60)) % 360;
            case Jitter -> look + (t % 2 == 0 ? s : -s);
            case Random -> rng.nextFloat() * 360;
            case Backwards -> look + 180;
            case Sway -> look + (float) Math.sin(t * 0.25) * s;
        };
        float p = mode.get() == Mode.Helicopter ? 90 : switch (pitch.get()) {
            case Keep -> mc.player.getXRot();
            case Down -> 90;
            case Up -> -90;
            case Nod -> (float) (Math.sin(t * 0.5) * 90);
            case Random -> rng.nextFloat() * 180 - 90;
        };
        // lowest priority, so anything that really needs to aim (auras, placing) wins
        Rotations.rotate(y, p, -100);
    }
}
