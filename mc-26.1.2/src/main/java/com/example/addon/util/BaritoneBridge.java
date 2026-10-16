package com.example.addon.util;

import com.example.addon.AddonTemplate;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;

public final class BaritoneBridge {
    private BaritoneBridge() {
    }

    private static Object primary() {
        try {
            Object provider = Class.forName("baritone.api.BaritoneAPI").getMethod("getProvider").invoke(null);
            return provider.getClass().getMethod("getPrimaryBaritone").invoke(provider);
        } catch (ReflectiveOperationException | LinkageError e) {
            return null;
        }
    }

    public static boolean available() {
        return primary() != null;
    }

    private static void path(String goalClass, Class<?>[] types, Object... args) {
        Object b = primary();
        if (b == null) return;
        try {
            Class<?> goalType = Class.forName("baritone.api.pathing.goals.Goal");
            Object goal = Class.forName("baritone.api.pathing.goals." + goalClass).getConstructor(types).newInstance(args);
            Object process = b.getClass().getMethod("getCustomGoalProcess").invoke(b);
            process.getClass().getMethod("setGoalAndPath", goalType).invoke(process, goal);
        } catch (ReflectiveOperationException e) {
            AddonTemplate.LOG.warn("Baritone goal {} failed", goalClass, e);
        }
    }

    public static void goTo(BlockPos feet) {
        path("GoalBlock", new Class<?>[] {BlockPos.class}, feet);
    }

    public static void goNextTo(BlockPos block) {
        path("GoalGetToBlock", new Class<?>[] {BlockPos.class}, block);
    }

    public static boolean busy() {
        Object b = primary();
        if (b == null) return false;
        try {
            Object process = b.getClass().getMethod("getCustomGoalProcess").invoke(b);
            if ((boolean) process.getClass().getMethod("isActive").invoke(process)) return true;
            Object behavior = b.getClass().getMethod("getPathingBehavior").invoke(b);
            return (boolean) behavior.getClass().getMethod("isPathing").invoke(behavior);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static void command(String cmd) {
        Object b = primary();
        if (b == null) return;
        try {
            Object manager = b.getClass().getMethod("getCommandManager").invoke(b);
            manager.getClass().getMethod("execute", String.class).invoke(manager, cmd);
        } catch (ReflectiveOperationException e) {
            AddonTemplate.LOG.warn("Baritone command '{}' failed", cmd, e);
        }
    }

    public static boolean mining() {
        Object b = primary();
        if (b == null) return false;
        try {
            Object process = b.getClass().getMethod("getMineProcess").invoke(b);
            return (boolean) process.getClass().getMethod("isActive").invoke(process);
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    public static Map<String, Object> humanWalking() {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("allowParkour", false);
        s.put("allowParkourPlace", false);
        s.put("freeLook", false);
        s.put("smoothLook", true);
        s.put("smoothLookTicks", 10);
        s.put("randomLooking", 0.03);
        s.put("antiCheatCompatibility", true);
        return s;
    }

    public static void stop() {
        Object b = primary();
        if (b == null) return;
        try {
            Object behavior = b.getClass().getMethod("getPathingBehavior").invoke(b);
            behavior.getClass().getMethod("cancelEverything").invoke(behavior);
        } catch (ReflectiveOperationException e) {
            AddonTemplate.LOG.warn("Baritone stop failed", e);
        }
    }

    public static Map<String, Object> apply(Map<String, Object> values) {
        Map<String, Object> old = new LinkedHashMap<>();
        try {
            Object settings = Class.forName("baritone.api.BaritoneAPI").getMethod("getSettings").invoke(null);
            for (Map.Entry<String, Object> e : values.entrySet()) {
                try {
                    Object setting = settings.getClass().getField(e.getKey()).get(settings);
                    var field = setting.getClass().getField("value");
                    old.put(e.getKey(), field.get(setting));
                    field.set(setting, e.getValue());
                } catch (NoSuchFieldException ignored) {
                }
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            return old;
        }
        return old;
    }

    public static void restore(Map<String, Object> old) {
        if (old != null && !old.isEmpty()) apply(old);
    }
}
