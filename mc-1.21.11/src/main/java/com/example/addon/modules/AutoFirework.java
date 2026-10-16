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
import net.minecraft.component.DataComponentTypes;
import net.minecraft.component.type.FireworksComponent;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;
import net.minecraft.util.math.MathHelper;

/**
 * Elytra flight on autopilot: holds the height you pick by steering your pitch, and boosts with firework rockets
 * from your hotbar (it switches to them while you fly and back to your item when you land). Auto mode fires the next rocket as soon as the last one's
 * boost has ended; Timed mode fires on a fixed delay. Above your height it stops boosting and glides down to it.
 */
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
    private FireworkRocketEntity rocket; // the boost we're riding (Auto mode)
    private int reaction = -1, nextDelay;
    private int heldBefore = -1; // your slot before we switched to the rockets, restored when you land
    private int sinceFired = 1000, rocketLife;
    private boolean watching;

    public AutoFirework() {
        super(AddonTemplate.CATEGORY, "auto-firework", "Elytra autopilot: fireworks from your hotbar, holding a set height.");
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
        if (mc.player == null || !mc.player.isGliding()) {
            rocket = null;
            restoreSlot(); // landed: back to what you were holding
            return;
        }
        sinceFired++;
        double y = mc.player.getY(), target = height.get();

        if (steer.get()) {
            // Climb when below, dive gently when above, level out near the height. Negative pitch = nose up.
            float want = (float) MathHelper.clamp((y - target) * 2.5, -maxPitch.get(), maxPitch.get() * 0.6);
            float step = (want - mc.player.getPitch()) * 0.15f;
            if (humanize.get()) {
                step = MathHelper.clamp(step, -4, 4) + (float) Math.sin(sinceFired * 0.21 + rng.nextGaussian() * 0.1) * 0.15f;
            }
            mc.player.setPitch(mc.player.getPitch() + step);
        }

        if (y > target + 2) return; // above the height: glide down to it, no boost
        if (!ready()) {
            reaction = -1;
            return;
        }
        // A person reacts a moment after the boost runs out, not on the exact tick
        if (humanize.get() && mode.get() == Mode.Auto) {
            if (reaction < 0) reaction = 2 + rng.nextInt(6);
            if (reaction-- > 0) return;
        }

        FindItemResult rockets = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
        if (!rockets.found()) return;
        rocketLife = maxLife(mc.player.getInventory().getStack(rockets.slot()).get(DataComponentTypes.FIREWORKS));
        // Switch to the rockets once and stay on them while flying (no swapping back and forth every rocket)
        int selected = mc.player.getInventory().getSelectedSlot();
        if (selected != rockets.slot()) {
            if (heldBefore < 0) heldBefore = selected;
            InvUtils.swap(rockets.slot(), false);
        }
        mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
        sinceFired = 0;
        rocket = null;
        watching = true;
        reaction = -1;
        double d = delay.get() * 20;
        nextDelay = (int) Math.round(humanize.get() ? d * (0.85 + rng.nextDouble() * 0.3) : d);
    }

    private boolean ready() {
        if (mode.get() == Mode.Timed) return sinceFired >= Math.max(1, nextDelay);
        if (sinceFired < 3) return false; // give the rocket a moment to show up
        if (rocket != null) return rocket.isRemoved(); // its boost has ended
        // Never saw our rocket (e.g. the server hid it): fall back to the longest it can last
        return !watching || sinceFired > rocketLife;
    }

    private void restoreSlot() {
        if (heldBefore >= 0 && mc.player != null) InvUtils.swap(heldBefore, false);
        heldBefore = -1;
    }

    // The rocket that appears on you right after firing is the one boosting you
    @EventHandler
    private void onEntityAdded(EntityAddedEvent event) {
        if (!watching || mc.player == null || sinceFired > 10 || !(event.entity instanceof FireworkRocketEntity r)) return;
        if (r.squaredDistanceTo(mc.player) > 9) return;
        rocket = r;
        watching = false;
    }

    // Longest a rocket boosts for: 10 * (duration + 1) ticks plus up to 11 random ticks
    private static int maxLife(FireworksComponent fireworks) {
        int duration = fireworks == null ? 1 : fireworks.flightDuration();
        return 10 * (duration + 1) + 11;
    }
}
