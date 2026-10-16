package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.KeybindSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.item.Items;
import net.minecraft.util.Hand;

/** Throws a wind charge from your hotbar with one key, then switches back to the item you were holding. */
public class KeyWindCharge extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> throwKey = sgGeneral.add(new KeybindSetting.Builder()
        .name("throw-key").description("Key that throws a wind charge.")
        .defaultValue(Keybind.none())
        .action(this::throwCharge)
        .build());

    private final Setting<Integer> swapDelay = sgGeneral.add(new IntSetting.Builder()
        .name("swap-delay").description("Ticks to hold the wind charge before throwing, and after before swapping back (20 = 1 second). 0 = instant.")
        .defaultValue(0).range(0, 20).sliderRange(0, 20)
        .build());

    // -1 idle; otherwise ticks left in the current step
    private int timer = -1;
    private boolean thrown;

    public KeyWindCharge() {
        super(AddonTemplate.CATEGORY, "key-wind-charge", "Throws a wind charge when you press a key.");
    }

    @Override
    public void onDeactivate() {
        if (timer >= 0) InvUtils.swapBack();
        timer = -1;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (timer < 0 || timer-- > 0) return;
        if (!thrown) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            thrown = true;
            timer = swapDelay.get();
        } else {
            InvUtils.swapBack();
            timer = -1;
        }
    }

    private void throwCharge() {
        if (mc.player == null || mc.interactionManager == null || mc.currentScreen != null || timer >= 0) return;
        // Hotbar only (slots 1-9), never the offhand or main inventory
        int slot = -1;
        for (int i = 0; i < 9; i++) {
            if (mc.player.getInventory().getStack(i).isOf(Items.WIND_CHARGE)) {
                slot = i;
                break;
            }
        }
        if (slot < 0) {
            ChatUtils.warning("No wind charge in your hotbar.");
            return;
        }
        // Swap to it, throw it, then back to the item you were holding
        InvUtils.swap(slot, true);
        if (swapDelay.get() == 0) {
            mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
            InvUtils.swapBack();
            return;
        }
        thrown = false;
        timer = swapDelay.get();
    }
}
