package com.example.addon.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

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
        }
        if (current != null && !current.isBlank() && !out.contains(current)) out.add(current);
        return out.toArray(String[]::new);
    }

    public static boolean isFile(String value) {
        return value != null && !value.isBlank() && !value.equals(NONE);
    }

    private static boolean matches(String name, String[] extensions) {
        String n = name.toLowerCase(Locale.ROOT);
        for (String ext : extensions) if (n.endsWith(ext)) return true;
        return false;
    }
}
