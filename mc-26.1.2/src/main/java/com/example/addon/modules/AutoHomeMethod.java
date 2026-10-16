package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import meteordevelopment.meteorclient.events.game.ReceiveMessageEvent;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.settings.StringSetting;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayDeque;
import java.util.Deque;

public class AutoHomeMethod extends Module {
    private final SettingGroup sg = settings.getDefaultGroup();
    private final SettingGroup sgAuto = settings.createGroup("Auto");

    private final Setting<Integer> slot = sg.add(new IntSetting.Builder()
        .name("home-slot").description("Which home number to use (your server rank decides how many you have).").defaultValue(1).range(1, 10).sliderRange(1, 10).build());

    private final Setting<Boolean> overwrite = sg.add(new BoolSetting.Builder()
        .name("overwrite").description("Delete that home first, so the new one saves even if the slot was used.").defaultValue(true).build());

    private final Setting<String> rtpCommand = sg.add(new StringSetting.Builder()
        .name("rtp-command").description("The random teleport command, without the slash (e.g. \"rtp\" or \"rtp overworld\").").defaultValue("rtp").build());

    private final Setting<Integer> gap = sg.add(new IntSetting.Builder()
        .name("command-delay").description("Ticks between commands (20 = 1 second).").defaultValue(10).range(1, 100).sliderRange(2, 40).build());

    private final Setting<Integer> timeout = sg.add(new IntSetting.Builder()
        .name("rtp-timeout").description("Seconds to wait for the random teleport before going home anyway.").defaultValue(30).range(5, 120).sliderRange(5, 60).build());

    private final Setting<Boolean> auto = sgAuto.add(new BoolSetting.Builder()
        .name("auto").description("Stay on and run whenever you go below the Y level. Off = run once when turned on, then turn off.").defaultValue(false).build());

    private final Setting<Integer> yLevel = sgAuto.add(new IntSetting.Builder()
        .name("y-level").description("Auto: run when you go below this Y.").defaultValue(0).range(-64, 320).sliderRange(-64, 128).visible(auto::get).build());

    private final Setting<Integer> cooldown = sgAuto.add(new IntSetting.Builder()
        .name("cooldown").description("Auto: seconds to wait after a run before it can run again.").defaultValue(30).range(0, 600).sliderRange(0, 120).visible(auto::get).build());

    private final Deque<Runnable> steps = new ArrayDeque<>();
    private int wait, rtpTicks;
    private boolean waitingRtp, teleported, wasBelow;
    private Vec3 rtpFrom;
    private long readyAt;

    public AutoHomeMethod() {
        super(AddonTemplate.CATEGORY, "auto-home-method", "Sets a home, /rtp's away, then /home's back. Pick home slots 1-10; auto mode runs below a Y level.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        WButton now = theme.button("Run now");
        now.action = () -> {
            if (!isActive()) toggle();
            else start();
        };
        return now;
    }

    @Override
    public void onActivate() {
        steps.clear();
        waitingRtp = false;
        readyAt = 0;
        wasBelow = mc.player != null && mc.player.getY() < yLevel.get();
        if (!auto.get()) start();
    }

    private boolean running() {
        return !steps.isEmpty() || waitingRtp;
    }

    private void start() {
        if (mc.player == null || running()) return;
        int n = slot.get();
        info("Home method: home %d, then /%s, then back.", n, rtpCommand.get());
        if (overwrite.get()) steps.add(() -> send("delhome " + n));
        steps.add(() -> send("sethome " + n));
        steps.add(() -> {
            send(rtpCommand.get());
            rtpFrom = mc.player.position();
            rtpTicks = 0;
            teleported = false;
            waitingRtp = true;
        });
        steps.add(() -> send("home " + n));
        steps.add(this::finish);
        wait = 0;
    }

    private void finish() {
        readyAt = System.currentTimeMillis() + cooldown.get() * 1000L;
        if (!auto.get()) toggle();
    }

    private void send(String command) {
        if (mc.getConnection() != null) mc.getConnection().sendCommand(command);
    }

    @EventHandler
    private void onTick(TickEvent.Post event) {
        if (mc.player == null) return;
        if (waitingRtp) {
            rtpTicks++;
            boolean moved = mc.player.position().distanceToSqr(rtpFrom) > 64;
            if (!teleported && !moved && rtpTicks < timeout.get() * 20) return;
            waitingRtp = false;
            wait = gap.get() + 20;
            return;
        }
        if (!steps.isEmpty()) {
            if (wait > 0) {
                wait--;
                return;
            }
            steps.poll().run();
            wait = gap.get();
            return;
        }
        if (!auto.get()) return;
        boolean below = mc.player.getY() < yLevel.get();
        if (below && !wasBelow && System.currentTimeMillis() >= readyAt) start();
        wasBelow = below;
    }

    @EventHandler
    private void onMessage(ReceiveMessageEvent event) {
        if (!waitingRtp) return;
        String m = event.getMessage().getString().toLowerCase();
        if (m.contains("teleported") || m.contains("random location")) teleported = true;
    }
}
