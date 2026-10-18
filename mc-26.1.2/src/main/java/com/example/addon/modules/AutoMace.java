package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.util.CombatTargets;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.MinecraftAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.phys.EntityHitResult;

public class AutoMace extends Module {
    public enum Prefer { Density, Breach, Any }

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Double> minFall = sg.add(new DoubleSetting.Builder()
        .name("min-fall").description("Only swaps once you've fallen at least this far (blocks). The mace smash needs 1.5.").defaultValue(1.5).range(0, 20).sliderRange(1.5, 10).build());

    private final Setting<Boolean> autoHit = sg.add(new BoolSetting.Builder()
        .name("auto-hit").description("Also hits for you when you're falling and your crosshair is on a target.").defaultValue(false).build());

    private final Setting<Prefer> prefer = sg.add(new EnumSetting.Builder<Prefer>()
        .name("prefer").description("With several maces in your hotbar: Density hits harder the further you fall, Breach goes through armour.").defaultValue(Prefer.Density).build());

    private final Setting<Boolean> onlyPlayers = sg.add(new BoolSetting.Builder()
        .name("only-players").description("Only swap for hits on players, not mobs.").defaultValue(false).build());

    private final Setting<Boolean> swapBack = sg.add(new BoolSetting.Builder()
        .name("swap-back").description("Go back to the item you had after the hit.").defaultValue(true).build());

    private int back = -1;

    public AutoMace() {
        super(AddonTemplate.CATEGORY, "auto-mace", "Swaps to your mace for the hit when you fall onto someone, so you get the smash damage.");
    }

    private boolean falling() {
        return mc.player.fallDistance >= minFall.get() && mc.player.getDeltaMovement().y < 0 && !mc.player.onGround();
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!(event.entity instanceof LivingEntity) || !falling() || onlyPlayers.get() && !(event.entity instanceof net.minecraft.world.entity.player.Player)) return;
        int slot = bestMace();
        if (slot < 0 || slot == mc.player.getInventory().getSelectedSlot()) return;
        InvUtils.swap(slot, swapBack.get());
        if (swapBack.get()) back = 2;
    }

    private int bestMace() {
        int best = -1, bestScore = -1;
        var key = prefer.get() == Prefer.Breach ? Enchantments.BREACH : Enchantments.DENSITY;
        for (int i = 0; i < 9; i++) {
            var s = mc.player.getInventory().getItem(i);
            if (!s.is(Items.MACE)) continue;
            int score = prefer.get() == Prefer.Any ? 0 : Utils.getEnchantmentLevel(s, key);
            if (score > bestScore) {
                bestScore = score;
                best = i;
            }
        }
        return best;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (back >= 0 && --back == 0) InvUtils.swapBack();
        if (!autoHit.get() || mc.player == null || mc.screen != null || back > 0 || !falling()) return;
        if (mc.hitResult instanceof EntityHitResult hit && CombatTargets.valid(hit.getEntity(), true, !onlyPlayers.get()) && bestMace() >= 0)
            ((MinecraftAccessor) mc).meteor$leftClick();
    }
}
