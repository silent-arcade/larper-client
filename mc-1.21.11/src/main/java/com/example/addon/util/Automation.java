package com.example.addon.util;

import meteordevelopment.meteorclient.systems.friends.Friends;
import meteordevelopment.meteorclient.utils.misc.input.Input;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.GameOptions;
import net.minecraft.entity.player.PlayerEntity;

import java.util.List;

public final class Automation {
    public static final long MOVE_GRACE_MS = 2000;
    private static long lastMove = Long.MIN_VALUE / 2;

    private Automation() {
    }

    public static boolean youAreMoving() {
        GameOptions o = MinecraftClient.getInstance().options;
        if (Input.isPressed(o.forwardKey) || Input.isPressed(o.backKey) || Input.isPressed(o.leftKey) || Input.isPressed(o.rightKey) || Input.isPressed(o.jumpKey)) {
            lastMove = System.currentTimeMillis();
        }
        return System.currentTimeMillis() - lastMove < MOVE_GRACE_MS;
    }

    public static String pauseReason(boolean onMove, boolean nearPlayers, int range, boolean lowHealth, double minHearts) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (onMove && youAreMoving()) return "you're moving (carries on 2s after you stop)";
        if (lowHealth && mc.player.getHealth() < minHearts * 2) return "low health";
        if (nearPlayers) {
            double r2 = range * range;
            for (PlayerEntity p : mc.world.getPlayers()) {
                if (p != mc.player && !Friends.get().isFriend(p) && p.squaredDistanceTo(mc.player) <= r2) return p.getName().getString() + " is nearby";
            }
        }
        return null;
    }

    public static String time(long ms) {
        long s = Math.max(0, ms / 1000);
        if (s >= 3600) return String.format("%dh %02dm", s / 3600, s / 60 % 60);
        if (s >= 60) return String.format("%dm %02ds", s / 60, s % 60);
        return s + "s";
    }

    public static String timing(long startedMs, int done, int total) {
        long elapsed = System.currentTimeMillis() - startedMs;
        String t = time(elapsed);
        if (done >= 5 && total > done) t += ", ~" + time(elapsed * (total - done) / done) + " left";
        return t;
    }

    private static final java.util.Map<String, long[]> boxes = new java.util.LinkedHashMap<>();

    public static void status(DrawContext ctx, String key, List<String> lines, int headColor) {
        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();
        boxes.put(key, new long[] {lines.size(), now});
        int y = 7;
        for (java.util.Map.Entry<String, long[]> e : boxes.entrySet()) {
            if (e.getKey().equals(key)) break;
            if (now - e.getValue()[1] < 250) y += (int) e.getValue()[0] * 11 + 8;
        }
        int w = 0;
        for (String l : lines) w = Math.max(w, mc.textRenderer.getWidth(l));
        int x = (mc.getWindow().getScaledWidth() - w) / 2;
        ctx.fill(x - 5, y - 3, x + w + 5, y + lines.size() * 11 + 1, 0xA0101018);
        for (int i = 0; i < lines.size(); i++) {
            String l = lines.get(i);
            int col = i == 0 ? headColor : l.trim().startsWith("PAUSED") ? 0xFFFFA040 : 0xFFE0E0E0;
            ctx.drawTextWithShadow(mc.textRenderer, l, x, y + i * 11, col);
        }
    }
}
