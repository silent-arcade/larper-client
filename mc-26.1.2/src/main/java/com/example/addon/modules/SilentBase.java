package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public abstract class SilentBase extends Module {
    public enum Mode { Safe, Blatant, Fast }

    protected final SettingGroup sg = settings.getDefaultGroup();

    protected final Setting<Keybind> key = sg.add(new KeybindSetting.Builder()
        .name("macro-key").description("Hold this to run the macro on what you're looking at.").defaultValue(Keybind.fromButton(GLFW.GLFW_MOUSE_BUTTON_RIGHT)).build());

    protected final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Safe: never faster than 2 ticks, randomised timing, only exactly what you look at. Blatant: your delays as set, plus silent aim at things near your crosshair. Fast: no delays at all, everything as soon as possible.")
        .defaultValue(Mode.Safe).build());

    protected final Setting<Integer> jitter = sg.add(new IntSetting.Builder()
        .name("random-delay").description("Safe mode: up to this many extra random ticks on every delay.").defaultValue(2).range(0, 10).sliderRange(0, 6)
        .visible(() -> mode.get() == Mode.Safe).build());

    protected final Setting<Boolean> humanAim = sg.add(new BoolSetting.Builder()
        .name("human-aim").description("Safe mode: your view glides onto targets near your crosshair like a real flick, with a little wobble, and only acts once you're really on them.")
        .defaultValue(true).visible(() -> mode.get() == Mode.Safe).build());

    protected final Setting<Double> turnSpeed = sg.add(new DoubleSetting.Builder()
        .name("turn-speed").description("Human aim: most degrees your view turns per tick. Lower looks more human.").defaultValue(14).range(2, 60).sliderRange(4, 30)
        .visible(() -> mode.get() == Mode.Safe && humanAim.get()).build());

    protected final Setting<Double> aimRadius = sg.add(new DoubleSetting.Builder()
        .name("aim-radius").description("Human aim: how far (blocks) from where you look it will pull your view to a target.").defaultValue(2.5).range(0.5, 6).sliderRange(1, 4)
        .visible(() -> mode.get() == Mode.Safe && humanAim.get()).build());

    protected final Setting<Double> maxSelf = sg.add(new DoubleSetting.Builder()
        .name("max-self-damage").description("Don't blow up anything doing more than this to you.").defaultValue(10).range(0, 36).sliderRange(0, 20).build());

    protected final Setting<Boolean> antiSuicide = sg.add(new BoolSetting.Builder()
        .name("anti-suicide").description("Never blow up something that would kill or pop you.").defaultValue(true).build());

    private final Random rng = new Random();

    protected SilentBase(String name, String desc) {
        super(AddonTemplate.CATEGORY, name, desc);
    }

    protected boolean safe() {
        return mode.get() == Mode.Safe;
    }

    protected boolean held() {
        return mc.player != null && mc.level != null && mc.screen == null && !mc.player.isUsingItem() && key.get().isPressed();
    }

    protected boolean fast() {
        return mode.get() == Mode.Fast;
    }

    protected int delay(int base) {
        if (fast()) return 0;
        return safe() ? Math.max(2, base) + rng.nextInt(jitter.get() + 1) : base;
    }

    protected void turnTowards(Vec3 target) {
        Vec3 d = target.subtract(mc.player.getEyePosition());
        float yaw = (float) Math.toDegrees(Math.atan2(-d.x, d.z));
        float pitch = (float) Mth.clamp(-Math.toDegrees(Math.atan2(d.y, Math.hypot(d.x, d.z))), -90, 90);
        float dy = Mth.wrapDegrees(yaw - mc.player.getYRot()), dp = pitch - mc.player.getXRot();
        float max = turnSpeed.get().floatValue();
        float stepY = Mth.clamp(dy * (0.3f + rng.nextFloat() * 0.25f), -max, max) + (float) rng.nextGaussian() * 0.3f;
        float stepP = Mth.clamp(dp * (0.3f + rng.nextFloat() * 0.25f), -max, max) + (float) rng.nextGaussian() * 0.2f;
        mc.player.setYRot(mc.player.getYRot() + stepY);
        mc.player.setXRot(Mth.clamp(mc.player.getXRot() + stepP, -90, 90));
    }

    protected boolean selfOk(double self) {
        if (self > maxSelf.get()) return false;
        return !antiSuicide.get() || self < mc.player.getHealth() + mc.player.getAbsorptionAmount() - 1;
    }

    protected void use(int slot, BlockHitResult hit) {
        InvUtils.swap(slot, true);
        BlockUtils.interact(hit, InteractionHand.MAIN_HAND, true);
        InvUtils.swapBack();
    }
}
