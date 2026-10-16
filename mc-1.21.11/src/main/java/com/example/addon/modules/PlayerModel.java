package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.playermodel.FiguraModel;
import com.example.addon.util.FileChoices;
import com.example.addon.util.FilePicker;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

public class PlayerModel extends Module {
    private static final String[] EXTENSIONS = {".zip", ".bbmodel"};
    private static final String[] BUNDLED = {};

    private final SettingGroup sg = settings.getDefaultGroup();

    private final Setting<String> model = sg.add(new ProvidedStringSetting.Builder()
        .name("model").description("Figura avatar (.zip) or Blockbench model (.bbmodel) from .minecraft/larper-client/models.")
        .defaultValue(FileChoices.NONE)
        .supplier(() -> FileChoices.list(folder(), currentModel(), EXTENSIONS))
        .onChanged(v -> load())
        .build());

    private final Setting<Boolean> hidePlayer = sg.add(new BoolSetting.Builder()
        .name("hide-player").description("Hide your normal player model and show only the avatar.").defaultValue(true).build());

    private final Setting<Boolean> hideArmor = sg.add(new BoolSetting.Builder()
        .name("hide-armor").description("Hide your armor.").defaultValue(false).build());

    private final Setting<Boolean> firstPersonArm = sg.add(new BoolSetting.Builder()
        .name("first-person-arm").description("Show the avatar's arm in first person instead of the normal Minecraft arm.").defaultValue(true).build());

    private volatile FiguraModel loaded;
    private int generation;

    public PlayerModel() {
        super(AddonTemplate.VISUAL, "player-model", "Wear a Figura avatar (.zip). Only you see it.");
    }

    @Override
    public WWidget getWidget(GuiTheme theme) {
        return FilePicker.buttons(theme, "Choose a Figura avatar", folder(), model, EXTENSIONS);
    }

    @Override
    public void onActivate() {
        load();
    }

    @Override
    public void onDeactivate() {
        generation++;
        if (loaded != null) mc.execute(loaded::close);
        loaded = null;
    }

    public FiguraModel avatar() {
        return isActive() ? loaded : null;
    }

    public FiguraModel firstPersonAvatar() {
        return firstPersonArm.get() ? avatar() : null;
    }

    public boolean hidesPlayer() {
        return isActive() && loaded != null && hidePlayer.get();
    }

    public boolean hidesArmor() {
        return isActive() && hideArmor.get();
    }

    private void load() {
        if (!isActive()) return;
        int gen = ++generation;
        if (!FileChoices.isFile(model.get())) {
            replace(null);
            return;
        }
        Path file = folder().resolve(model.get()).normalize();
        if (!file.startsWith(folder()) || !Files.isRegularFile(file)) {
            ChatUtils.warning("Avatar %s not found.", model.get());
            return;
        }
        CompletableFuture.supplyAsync(() -> {
            try {
                return FiguraModel.load(file);
            } catch (Exception e) {
                AddonTemplate.LOG.warn("Couldn't load avatar {}", file, e);
                mc.execute(() -> ChatUtils.warning("Couldn't load avatar %s: %s", model.get(), e.getMessage()));
                return null;
            }
        }).thenAccept(m -> mc.execute(() -> {
            if (gen != generation || !isActive() || m == null) return;
            replace(m);
            ChatUtils.info("Avatar loaded: %s (%d faces).", file.getFileName(), m.faceCount);
        }));
    }

    private void replace(FiguraModel m) {
        if (loaded != null) loaded.close();
        loaded = m;
    }

    private Path folder() {
        Path dir = mc.runDirectory.toPath().resolve("larper-client").resolve("models").toAbsolutePath().normalize();
        try {
            Files.createDirectories(dir);
            for (String name : BUNDLED) {
                Path target = dir.resolve(name);
                if (Files.exists(target)) continue;
                try (InputStream in = PlayerModel.class.getResourceAsStream("/assets/larper-client/playermodels/" + name)) {
                    if (in != null) Files.copy(in, target);
                }
            }
        } catch (IOException e) {
            ChatUtils.warning("Couldn't set up %s", dir);
        }
        return dir;
    }

    private String currentModel() {
        return model == null ? null : model.get();
    }
}
