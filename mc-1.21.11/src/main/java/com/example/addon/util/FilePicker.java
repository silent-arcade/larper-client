package com.example.addon.util;

import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import net.minecraft.util.Util;
import org.lwjgl.PointerBuffer;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.util.tinyfd.TinyFileDialogs;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class FilePicker {
    private FilePicker() {
    }

    public static WWidget buttons(GuiTheme theme, String title, Path folder, Setting<String> setting, String... extensions) {
        WHorizontalList list = theme.horizontalList();
        WButton select = list.add(theme.button("Select file...")).widget();
        select.action = () -> {
            Path picked = open(title, extensions);
            if (picked == null) return;
            try {
                Path dest = folder.resolve(picked.getFileName().toString());
                if (!picked.toAbsolutePath().equals(dest.toAbsolutePath())) Files.copy(picked, dest, StandardCopyOption.REPLACE_EXISTING);
                setting.set(dest.getFileName().toString());
            } catch (Exception e) {
                ChatUtils.warning("Couldn't use %s: %s", picked.getFileName(), e.getMessage());
            }
        };
        WButton open = list.add(theme.button("Open folder")).widget();
        open.action = () -> Util.getOperatingSystem().open(folder.toFile());
        return list;
    }

    private static Path open(String title, String[] extensions) {
        try (MemoryStack stack = MemoryStack.stackPush()) {
            PointerBuffer filters = stack.mallocPointer(extensions.length);
            for (String ext : extensions) filters.put(stack.UTF8("*" + ext));
            filters.flip();
            String result = TinyFileDialogs.tinyfd_openFileDialog(title, null, filters, String.join(", ", extensions), false);
            return result == null ? null : Path.of(result);
        }
    }
}
