package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.visuals.Fx;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;

public class SpinBot extends Module {
    public enum Mode { Spin, Jitter, Random, Backwards, Sway, Helicopter }
    public enum Pitch { Keep, Down, Up, Nod, Random }
    public enum Side { Client, Server }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Side> side = sg.add(new EnumSetting.Builder<Side>()
        .name("side").description("Client: only your own model spins on your screen (see it in F5), nothing is sent. Server: your head really spins for everyone; your own view stays still.").defaultValue(Side.Client).build());

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Spin: turns round and round. Jitter: quick wiggle left and right. Random: wanders around smoothly. Backwards: your body faces behind you. Sway: swings side to side. Helicopter: very fast spin with your head down.").defaultValue(Mode.Spin).build());

    private final Setting<Integer> speed = sg.add(new IntSetting.Builder()
        .name("speed").description("How fast (degrees per tick for Spin, swing size for Jitter and Sway).").defaultValue(30).range(1, 180).sliderRange(5, 90).build());

    private final Setting<Pitch> pitch = sg.add(new EnumSetting.Builder<Pitch>()
        .name("pitch").description("Where your head points up and down.").defaultValue(Pitch.Keep).build());

    public SpinBot() {
        super(AddonTemplate.CATEGORY, "spin-bot", "Spins, jitters or sways your head. Client: only your own model on your screen (see it in F5). Server: your head really spins for everyone, while your view stays still.");
    }

    // Server side: send the spin as your rotation (lowest priority, so auras and placing still aim where they need)
    @meteordevelopment.orbit.EventHandler
    private void onTick(meteordevelopment.meteorclient.events.world.TickEvent.Pre event) {
        if (side.get() != Side.Server || mc.player == null) return;
        float[] a = angles(mc.player.getYRot(), mc.player.getXRot());
        meteordevelopment.meteorclient.utils.player.Rotations.rotate(a[0], a[1], -100);
    }

    // called when your player is about to be drawn; only changes how it looks on your screen
    public void apply(LivingEntityRenderState state) {
        if (side.get() != Side.Client) return;
        float[] a = angles(state.bodyRot, state.xRot);
        state.bodyRot = a[0];
        state.yRot = 0;
        state.xRot = a[1];
    }

    private float[] angles(float baseYaw, float basePitch) {
        double t = Fx.time() * 20, s = speed.get();
        float y = switch (mode.get()) {
            case Spin -> (float) (t * s % 360);
            case Helicopter -> (float) (t * Math.max(s, 60) % 360);
            // eased wiggle instead of snapping, so it stays smooth
            case Jitter -> baseYaw + (float) (Math.sin(t * 1.4) * s);
            // a few slow waves mixed together wander around smoothly but never repeat in an obvious way
            case Random -> baseYaw + (float) ((Math.sin(t * 0.11) + Math.sin(t * 0.173 + 1.3) + Math.sin(t * 0.071 + 2.1)) * 60);
            case Backwards -> baseYaw + 180;
            case Sway -> baseYaw + (float) (Math.sin(t * 0.25) * s);
        };
        float p = mode.get() == Mode.Helicopter ? 90 : switch (pitch.get()) {
            case Keep -> basePitch;
            case Down -> 90;
            case Up -> -90;
            case Nod -> (float) (Math.sin(t * 0.5) * 90);
            case Random -> (float) ((Math.sin(t * 0.13 + 0.7) + Math.sin(t * 0.21)) * 40);
        };
        return new float[] {y, p};
    }
}
