package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.util.CombatTargets;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.mixin.MinecraftAccessor;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.EntityHitResult;
import java.util.Random;

public class Triggerbot extends Module {
    public enum Crits { Off, Smart, Only }

    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgTargets = settings.createGroup("Targets");

    private final Setting<Boolean> fullCharge = sg.add(new BoolSetting.Builder()
        .name("wait-for-charge").description("Only hits once your attack is fully charged (full damage).").defaultValue(true).build());

    private final Setting<Crits> crits = sg.add(new EnumSetting.Builder<Crits>()
        .name("crits").description("Smart: if you're jumping it waits until you fall so the hit crits; on the ground it hits normally. Only: only ever hits while falling.").defaultValue(Crits.Smart).build());

    private final Setting<Integer> minDelay = sg.add(new IntSetting.Builder()
        .name("reaction-min").description("Shortest reaction time before hitting (ticks).").defaultValue(1).range(0, 20).sliderRange(0, 8).build());

    private final Setting<Integer> maxDelay = sg.add(new IntSetting.Builder()
        .name("reaction-max").description("Longest reaction time before hitting (ticks). A random time in between is used each time.").defaultValue(3).range(0, 20).sliderRange(0, 10).build());

    private final Setting<Boolean> weaponOnly = sg.add(new BoolSetting.Builder()
        .name("only-with-weapon").description("Only while holding a sword, axe, mace or spear.").defaultValue(true).build());

    private final Setting<Boolean> notWhileShielded = sg.add(new BoolSetting.Builder()
        .name("skip-shields").description("Don't waste hits on players blocking with a shield (unless you hold an axe).").defaultValue(true).build());

    private final Setting<Boolean> players = sgTargets.add(new BoolSetting.Builder()
        .name("players").description("Hit players (never friends).").defaultValue(true).build());

    private final Setting<Boolean> mobs = sgTargets.add(new BoolSetting.Builder()
        .name("mobs").description("Hit hostile mobs.").defaultValue(true).build());

    private final Random rng = new Random();
    private int wait = -1;

    public Triggerbot() {
        super(AddonTemplate.CATEGORY, "triggerbot", "Clicks for you when your crosshair is on a target: full-charge hits, smart crits, random reaction time, skips shields.");
    }

    private boolean critReady() {
        boolean falling = mc.player.fallDistance > 0 && !mc.player.onGround() && mc.player.getDeltaMovement().y < 0 && !mc.player.onClimbable() && !mc.player.isInWater();
        return switch (crits.get()) {
            case Off -> true;
            case Only -> falling;
            case Smart -> mc.player.onGround() || falling || mc.player.isInWater() || mc.player.onClimbable();
        };
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.screen != null || mc.player.isUsingItem()) return;
        var held = mc.player.getMainHandItem();
        boolean axe = held.is(ItemTags.AXES);
        if (weaponOnly.get() && !(held.is(ItemTags.SWORDS) || axe || held.is(ItemTags.SPEARS) || held.is(net.minecraft.world.item.Items.MACE))) return;
        if (!(mc.hitResult instanceof EntityHitResult hit) || !CombatTargets.valid(hit.getEntity(), players.get(), mobs.get())) {
            wait = -1;
            return;
        }
        if (notWhileShielded.get() && !axe && hit.getEntity() instanceof Player p && p.isBlocking()) return;
        if (fullCharge.get() && mc.player.getAttackStrengthScale(0.5f) < 1) return;
        if (!critReady()) return;
        if (wait < 0) {
            int lo = Math.min(minDelay.get(), maxDelay.get()), hi = Math.max(minDelay.get(), maxDelay.get());
            wait = lo + rng.nextInt(hi - lo + 1);
        }
        if (wait-- > 0) return;
        // the same code path as pressing left click
        ((MinecraftAccessor) mc).meteor$leftClick();
        wait = -1;
    }
}
