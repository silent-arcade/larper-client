package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.player.Rotations;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;

public class PearlCatch extends Module {
    public enum Aim { WhereILook, AtMyFeet }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> key = sgGeneral.add(new KeybindSetting.Builder()
        .name("key").description("Press to throw an ender pearl and then a wind charge to catch it.")
        .defaultValue(Keybind.none())
        .action(this::start)
        .build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("wind-delay").description("Ticks between the pearl and the wind charge. 0 = same tick, which is what makes the pearl catch the wind charge; anything higher won't catch.")
        .defaultValue(0).range(0, 40).sliderRange(0, 20)
        .build());

    private final Setting<Aim> aim = sgGeneral.add(new EnumSetting.Builder<Aim>()
        .name("wind-charge-aim").description("WhereILook: throw the wind charge the same way as the pearl. AtMyFeet: throw it straight down to launch yourself (your camera doesn't move).")
        .defaultValue(Aim.WhereILook)
        .build());

    private final Setting<Integer> aimUp = sgGeneral.add(new IntSetting.Builder()
        .name("aim-up").description("If you aim flatter than this many degrees up, both throws go out at this angle (server-side, your camera doesn't move). Below about 20 degrees up the pearl can't catch the wind charge. 0 = always use your own aim.")
        .defaultValue(25).range(0, 89).sliderRange(0, 60)
        .build());

    private int timer = -1, windSlot, homeSlot;
    private float throwPitch;

    public PearlCatch() {
        super(AddonTemplate.AUTOMATION, "pearl-catch", "Throws an ender pearl and a wind charge in the same tick so the pearl catches the wind charge in mid-air (aim up).");
    }

    @Override
    public void onDeactivate() {
        timer = -1;
    }

    private void start() {
        if (!isActive() || mc.player == null || mc.gameMode == null || mc.screen != null || timer >= 0) return;
        int pearl = hotbar(Items.ENDER_PEARL), wind = hotbar(Items.WIND_CHARGE);
        if (pearl < 0 || wind < 0) {
            ChatUtils.warning("Pearl Catch: put %s in your hotbar.", pearl < 0 && wind < 0 ? "an ender pearl and a wind charge" : pearl < 0 ? "an ender pearl" : "a wind charge");
            return;
        }
        if (mc.player.getCooldowns().isOnCooldown(mc.player.getInventory().getItem(pearl))) return;
        homeSlot = mc.player.getInventory().getSelectedSlot();
        windSlot = wind;
        throwPitch = aimUp.get() > 0 && mc.player.getXRot() > -aimUp.get() ? -aimUp.get() : mc.player.getXRot();
        if (throwPitch != mc.player.getXRot()) Rotations.rotate(mc.player.getYRot(), throwPitch, 100, () -> throwPearl(pearl));
        else throwPearl(pearl);
    }

    private void throwPearl(int pearl) {
        use(pearl);
        timer = delay.get();
        if (timer == 0) throwWind();
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer > 0 && --timer == 0) throwWind();
    }

    private void throwWind() {
        timer = -1;
        if (mc.player == null || !mc.player.getInventory().getItem(windSlot).is(Items.WIND_CHARGE)) return;
        if (aim.get() == Aim.AtMyFeet) Rotations.rotate(mc.player.getYRot(), 90, 100, () -> use(windSlot));
        else if (delay.get() > 0 && throwPitch != mc.player.getXRot()) Rotations.rotate(mc.player.getYRot(), throwPitch, 100, () -> use(windSlot));
        else use(windSlot);
    }

    private void use(int slot) {
        InvUtils.swap(slot, false);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
        InvUtils.swap(homeSlot, false);
    }

    private int hotbar(Item item) {
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getItem(i).is(item)) return i;
        return -1;
    }
}
