package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.entity.player.AttackEntityEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.registry.tag.ItemTags;

public class ShieldBreaker extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> swapBack = sg.add(new BoolSetting.Builder()
        .name("swap-back").description("Go back to the item you had after the hit.").defaultValue(true).build());

    private final Setting<Boolean> autoHit = sg.add(new BoolSetting.Builder()
        .name("auto-break").description("When your crosshair is on someone holding up a shield, swaps to your axe and hits for you to knock it down.").defaultValue(false).build());

    private int back = -1;

    public ShieldBreaker() {
        super(AddonTemplate.CATEGORY, "shield-breaker", "Swaps to an axe when you hit a player who is blocking so the hit disables their shield, and can break shields for you automatically.");
    }

    @EventHandler
    private void onAttack(AttackEntityEvent event) {
        if (!(event.entity instanceof PlayerEntity p) || !p.isBlocking()) return;
        FindItemResult axe = InvUtils.findInHotbar(s -> s.isIn(ItemTags.AXES));
        if (!axe.found() || axe.isMainHand()) return;
        InvUtils.swap(axe.slot(), swapBack.get());
        if (swapBack.get()) back = 2;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (back >= 0 && --back == 0) InvUtils.swapBack();
        if (!autoHit.get() || back > 0 || mc.player == null || mc.currentScreen != null) return;
        if (!(mc.crosshairTarget instanceof net.minecraft.util.hit.EntityHitResult hit) || !(hit.getEntity() instanceof PlayerEntity p) || !p.isBlocking()
            || !meteordevelopment.meteorclient.systems.friends.Friends.get().shouldAttack(p) || mc.player.getAttackCooldownProgress(0.5f) < 1) return;
        if (InvUtils.findInHotbar(s -> s.isIn(ItemTags.AXES)).found()) ((meteordevelopment.meteorclient.mixin.MinecraftClientAccessor) mc).meteor$leftClick();
    }
}
