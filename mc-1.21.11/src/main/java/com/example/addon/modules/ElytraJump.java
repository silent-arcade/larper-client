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
import net.minecraft.item.Items;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.util.Hand;

public class ElytraJump extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<Boolean> atTop = sg.add(new BoolSetting.Builder()
        .name("open-at-top").description("Opens the elytra at the top of your jump instead of straight away.").defaultValue(true).build());

    private final Setting<Boolean> holdJump = sg.add(new BoolSetting.Builder()
        .name("only-while-holding-jump").description("Only opens if you're still holding jump when it's time to open, so tapping space is a normal jump and holding it takes off.").defaultValue(true).build());

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
        if (mc.player == null || mc.getNetworkHandler() == null) return;
        if (rocketIn >= 0 && rocketIn-- == 0 && mc.player.isGliding()) {
            FindItemResult rocket = InvUtils.findInHotbar(Items.FIREWORK_ROCKET);
            if (rocket.found()) {
                InvUtils.swap(rocket.slot(), true);
                mc.interactionManager.interactItem(mc.player, Hand.MAIN_HAND);
                InvUtils.swapBack();
            }
        }
        if (mc.player.isOnGround() || mc.player.isGliding()) {
            jumped = mc.player.isOnGround() && mc.options.jumpKey.isPressed() && (!sprintJump.get() || mc.player.isSprinting());
            return;
        }
        if (!jumped || atTop.get() && mc.player.getVelocity().y > 0) return;
        jumped = false;
        if (holdJump.get() && !mc.options.jumpKey.isPressed()) return;
        // same as the vanilla mid-air jump press
        if (mc.player.checkGliding()) {
            mc.getNetworkHandler().sendPacket(new ClientCommandC2SPacket(mc.player, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
            if (firework.get()) rocketIn = fireworkDelay.get();
        }
    }
}
