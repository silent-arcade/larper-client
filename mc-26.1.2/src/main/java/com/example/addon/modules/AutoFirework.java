package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.EntityAddedEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.core.component.DataComponents;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.Fireworks;

public class AutoFirework extends Module {
    public enum Mode { Auto, Timed }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Integer> height = sg.add(new IntSetting.Builder()
        .name("height").description("Y level to fly at.").defaultValue(120).range(-64, 2000).sliderRange(0, 320).build());

    private final Setting<Mode> mode = sg.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Auto: next rocket when the last boost ends. Timed: a rocket every few seconds.")
        .defaultValue(Mode.Auto).build());

    private final Setting<Double> delay = sg.add(new DoubleSetting.Builder()
        .name("delay").description("Timed mode: seconds between rockets.").defaultValue(1.5).range(0.1, 20).sliderRange(0.25, 6)
        .visible(() -> mode.get() == Mode.Timed).build());

    private final Setting<Boolean> steer = sg.add(new BoolSetting.Builder()
        .name("hold-height").description("Steer your pitch up or down to stay at the height (you still steer left and right).")
        .defaultValue(true).build());

    private final Setting<Integer> maxPitch = sg.add(new IntSetting.Builder()
        .name("max-climb").description("Steepest it will climb or dive (degrees).").defaultValue(40).range(5, 90).sliderRange(10, 70)
        .visible(steer::get).build());

    private final Setting<Boolean> humanize = sg.add(new BoolSetting.Builder()
        .name("humanize").description("Fly like a person: a short random reaction before each rocket, slightly varied timing, and smooth, gently wobbling steering.")
        .defaultValue(true).build());

    private final java.util.Random rng = new java.util.Random();
    private FireworkRocketEntity rocket;
    private int reaction = -1, nextDelay;
    private int heldBefore = -1;
    private int sinceFired = 1000, rocketLife;
    private boolean watching;

    public AutoFirework() {
        super(AddonTemplate.AUTOMATION, "auto-firework", "Elytra autopilot: fireworks from your hotbar, holding a set height.");
    }

    @Override
    public void onDeactivate() {
        restoreSlot();
    }

    @Override
    public void onActivate() {
        rocket = null;
        watching = false;
        sinceFired = 1000;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || !mc.player.isFallFlying()) {
            rocket = null;
            restoreSlot();
            return;
        }
        sinceFired++;
        double y = mc.player.getY(), target = height.get();

        if (steer.get()) {
            float want = (float) Mth.clamp((y - target) * 2.5, -maxPitch.get(), maxPitch.get() * 0.6);
            float step = (want - mc.player.getXRot()) * 0.15f;
            if (humanize.get()) {
                step = Mth.clamp(step, -4, 4) + (float) Math.sin(sinceFired * 0.21 + rng.nextGaussian() * 0.1) * 0.15f;
            }
            mc.player.setXRot(mc.player.getXRot() + step);
        }

        if (y > target + 2) return;
        if (!ready()) {
            reaction = -1;
            return;
        }
        if (humanize.get() && mode.get() == Mode.Auto) {
            if (reaction < 0) reaction = 2 + rng.nextInt(6);
            if (reaction-- > 0) return;
        }

        FindItemResult rockets = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!rockets.found()) return;
        rocketLife = maxLife(mc.player.getInventory().getItem(rockets.slot()).get(DataComponents.FIREWORKS));
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected != rockets.slot()) {
            if (heldBefore < 0) heldBefore = selected;
            InvUtils.swap(rockets.slot(), false);
        }
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        sinceFired = 0;
        rocket = null;
        watching = true;
        reaction = -1;
        double d = delay.get() * 20;
        nextDelay = (int) Math.round(humanize.get() ? d * (0.85 + rng.nextDouble() * 0.3) : d);
    }

    private boolean ready() {
        if (mode.get() == Mode.Timed) return sinceFired >= Math.max(1, nextDelay);
        if (sinceFired < 3) return false;
        if (rocket != null) return rocket.isRemoved();
        return !watching || sinceFired > rocketLife;
    }

    private void restoreSlot() {
        if (heldBefore >= 0 && mc.player != null) InvUtils.swap(heldBefore, false);
        heldBefore = -1;
    }

    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!watching || mc.player == null || sinceFired > 10 || !(event.entity instanceof FireworkRocketEntity r)) return;
        if (r.distanceToSqr(mc.player) > 9) return;
        rocket = r;
        watching = false;
    }

    private static int maxLife(Fireworks fireworks) {
        int duration = fireworks == null ? 1 : fireworks.flightDuration();
        return 10 * (duration + 1) + 11;
    }
}
