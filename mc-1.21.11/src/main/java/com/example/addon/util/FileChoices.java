package com.example.addon.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

/**
 * Options for a file-picker dropdown. Meteor's dropdown crashes if the current value isn't one of the options
 * (or is null), so the list always starts with {@link #NONE} and always contains the current value.
 */
public final class FileChoices {
    public static final String NONE = "None";

    private FileChoices() {
    }

    public static String[] list(Path folder, String current, String... extensions) {
        List<String> out = new ArrayList<>();
        out.add(NONE);
        try (Stream<Path> files = Files.list(folder)) {
            files.filter(Files::isRegularFile).map(f -> f.getFileName().toString())
                .filter(n -> matches(n, extensions)).sorted().forEach(out::add);
        } catch (IOException ignored) {
            // folder missing or unreadable: just offer None
        }
        if (current != null && !current.isBlank() && !out.contains(current)) out.add(current);
        return out.toArray(String[]::new);
    }

    /** True if value names a file (not None/blank). */
    public static boolean isFile(String value) {
        return value != null && !value.isBlank() && !value.equals(NONE);
    }

    private static boolean matches(String name, String[] extensions) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String ext : extensions) if (n.endsWith(ext)) return true;
        return false;
    }
}
