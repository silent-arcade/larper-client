package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Items;

public class ElytraJump extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> atTop = sg.add(new BoolSetting.Builder()
        .name("open-at-top").description("Opens the elytra at the top of your jump instead of straight away.").defaultValue(true).build());

    private final Setting<Boolean> firework = sg.add(new BoolSetting.Builder()
        .name("auto-firework").description("Fires a rocket from your hotbar as soon as the elytra opens, so you take off from a standing jump.").defaultValue(false).build());

    private final Setting<Integer> fireworkDelay = sg.add(new IntSetting.Builder()
        .name("firework-delay").description("Ticks after opening before the rocket.").defaultValue(2).range(0, 20).sliderRange(0, 10).visible(firework::get).build());

    private final Setting<Boolean> sprintJump = sg.add(new BoolSetting.Builder()
        .name("only-sprint-jumps").description("Only for jumps while sprinting, so normal hops don't open it.").defaultValue(false).build());

    private boolean jumped;
    private int rocketIn = -1;

    public ElytraJump() {
        super(AddonTemplate.CATEGORY, "elytra-jump", "Opens your elytra by itself when you jump and can fire a rocket to take off straight from the ground.");
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.getConnection() == null) return;
        if (rocketIn >= 0 && rocketIn-- == 0 && mc.player.isFallFlying()) {
            FindItemResult rocket = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
            if (rocket.found()) {
                InvUtils.swap(rocket.slot(), true);
                mc.gameMode.useItem(mc.player, InteractionHand.MAIN_HAND);
                InvUtils.swapBack();
            }
        }
        if (mc.player.onGround() || mc.player.isFallFlying()) {
            jumped = mc.player.onGround() && mc.options.keyJump.isDown() && (!sprintJump.get() || mc.player.isSprinting());
            return;
        }
        if (!jumped || atTop.get() && mc.player.getDeltaMovement().y > 0) return;
        jumped = false;
        // same as the vanilla mid-air jump press
        if (mc.player.tryToStartFallFlying()) {
            mc.getConnection().send(new ServerboundPlayerCommandPacket(mc.player, ServerboundPlayerCommandPacket.Action.START_FALL_FLYING));
            if (firework.get()) rocketIn = fireworkDelay.get();
        }
    }
}
