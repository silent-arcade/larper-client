package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
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
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;

public class XpMacro extends Module {
    public enum Mode { Hold, Toggle }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Keybind> key = sgGeneral.add(new KeybindSetting.Builder()
        .name("key").description("Hold (or press, in Toggle mode) to throw XP bottles.")
        .defaultValue(Keybind.none())
        .action(this::pressed)
        .build());

    private final Setting<Mode> mode = sgGeneral.add(new EnumSetting.Builder<Mode>()
        .name("mode").description("Hold: throws while you hold the key. Toggle: press once to start, again to stop.")
        .defaultValue(Mode.Hold)
        .build());

    private final Setting<Integer> delay = sgGeneral.add(new IntSetting.Builder()
        .name("throw-delay").description("Ticks between bottles. 4 = the same speed as holding right-click; lower is faster but less legit.")
        .defaultValue(4).range(1, 20).sliderRange(1, 10)
        .build());

    private final Setting<Boolean> atFeet = sgGeneral.add(new BoolSetting.Builder()
        .name("throw-at-feet").description("Aim the bottles straight down so the XP lands on you (your camera doesn't move).")
        .defaultValue(true)
        .build());

    private final Setting<Boolean> stopWhenMended = sgGeneral.add(new BoolSetting.Builder()
        .name("stop-when-mended").description("Stop once all your Mending armour and held items are fully repaired.")
        .defaultValue(false)
        .build());

    private boolean running, warned;
    private int timer, homeSlot = -1;

    public XpMacro() {
        super(AddonTemplate.AUTOMATION, "xp-macro", "Switches to XP bottles in your hotbar and throws them while you hold a key.");
    }

    @Override
    public void onDeactivate() {
        running = false;
        swapBack();
    }

    private void pressed() {
        if (mode.get() == Mode.Toggle && isActive() && mc.screen == null) {
            running = !running;
            warned = false;
        }
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.gameMode == null) return;
        boolean want = mode.get() == Mode.Hold ? key.get().isPressed() && mc.screen == null : running;
        if (!want) {
            swapBack();
            timer = 0;
            warned = false;
            return;
        }
        if (stopWhenMended.get() && mended()) {
            if (!warned) ChatUtils.info("XP Macro: everything is mended.");
            warned = true;
            running = false;
            swapBack();
            return;
        }
        int xp = -1;
        for (int i = 0; i < 9; i++) if (mc.player.getInventory().getItem(i).is(Items.EXPERIENCE_BOTTLE)) {
            xp = i;
            break;
        }
        if (xp < 0) {
            if (!warned) ChatUtils.warning("XP Macro: no XP bottles in your hotbar.");
            warned = true;
            running = false;
            swapBack();
            return;
        }
        if (homeSlot < 0) homeSlot = mc.player.getInventory().getSelectedSlot();
        if (timer-- > 0) return;
        timer = delay.get() - 1;
        int slot = xp;
        if (atFeet.get()) Rotations.rotate(mc.player.getYRot(), 90, 50, () -> throwFrom(slot));
        else throwFrom(slot);
    }

    private void throwFrom(int slot) {
        if (mc.player == null || !mc.player.getInventory().getItem(slot).is(Items.EXPERIENCE_BOTTLE)) return;
        InvUtils.swap(slot, false);
        mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
    }

    private void swapBack() {
        if (homeSlot >= 0 && mc.player != null) InvUtils.swap(homeSlot, false);
        homeSlot = -1;
    }

    private boolean mended() {
        boolean any = false;
        for (EquipmentSlot s : EquipmentSlot.values()) {
            ItemStack st = mc.player.getItemBySlot(s);
            if (st.isEmpty() || !st.isDamageableItem() || !hasMending(st)) continue;
            any = true;
            if (st.isDamaged()) return false;
        }
        return any;
    }

    private boolean hasMending(ItemStack st) {
        var registry = mc.level.registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT);
        return net.minecraft.world.item.enchantment.EnchantmentHelper.getItemEnchantmentLevel(registry.getOrThrow(Enchantments.MENDING), st) > 0;
    }
}
