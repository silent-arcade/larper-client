package com.example.addon.modules;

import com.example.addon.AddonTemplate;
import com.example.addon.sound.CustomSound;
import com.example.addon.util.FileChoices;
import meteordevelopment.meteorclient.events.packets.PacketEvent;
import meteordevelopment.meteorclient.settings.DoubleSetting;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.ProvidedStringSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.player.ChatUtils;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.network.packet.s2c.play.EntityDamageS2CPacket;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.sound.SoundEvents;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Supplier;

/** Plays a sound when one of your hits lands (the server confirms the damage). Only you hear it. */
public class HitSounds extends Module {
    public enum Sound {
        Exp(() -> SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP),
        Anvil(() -> SoundEvents.BLOCK_ANVIL_LAND),
        ArrowDing(() -> SoundEvents.ENTITY_ARROW_HIT_PLAYER),
        Bell(() -> SoundEvents.BLOCK_NOTE_BLOCK_BELL.value()),
        Pling(() -> SoundEvents.BLOCK_NOTE_BLOCK_PLING.value()),
        Bit(() -> SoundEvents.BLOCK_NOTE_BLOCK_BIT.value()),
        Hat(() -> SoundEvents.BLOCK_NOTE_BLOCK_HAT.value()),
        Click(() -> SoundEvents.UI_BUTTON_CLICK.value()),
        Chime(() -> SoundEvents.BLOCK_AMETHYST_BLOCK_CHIME),
        Crit(() -> SoundEvents.ENTITY_PLAYER_ATTACK_CRIT),
        Custom(null);

        final Supplier<SoundEvent> event;

        Sound(Supplier<SoundEvent> event) {
            this.event = event;
        }
    }

    private final SettingGroup sgGeneral = settings.getDefaultGroup();

    private final Setting<Sound> sound = sgGeneral.add(new EnumSetting.Builder<Sound>()
        .name("sound").description("Sound to play. Custom plays your own file from the hitsounds folder.")
        .defaultValue(Sound.Exp)
        .onChanged(s -> { if (s == Sound.Custom) folder(); })
        .build());

    private final Setting<String> customFile = sgGeneral.add(new ProvidedStringSetting.Builder()
        .name("custom-file").description("An .mp3 or .wav file in .minecraft/larper-client/hitsounds.")
        .defaultValue(FileChoices.NONE)
        .supplier(() -> FileChoices.list(folder(), customFile(), CustomSound.EXTENSIONS))
        .visible(() -> sound.get() == Sound.Custom)
        .build());

    private final Setting<Double> volume = sgGeneral.add(new DoubleSetting.Builder()
        .name("volume").description("Hit sound volume.").defaultValue(1).range(0, 1).sliderRange(0, 1)
        .build());

    private final Setting<Double> pitch = sgGeneral.add(new DoubleSetting.Builder()
        .name("pitch").description("Hit sound pitch.").defaultValue(1).range(0.5, 2).sliderRange(0.5, 2)
        .build());

    private final Setting<Double> startTime = sgGeneral.add(new DoubleSetting.Builder()
        .name("start-time").description("Seconds into your file to start playing from. Type a value for long files.")
        .defaultValue(0).range(0, 3600).sliderRange(0, 30).decimalPlaces(2)
        .visible(() -> sound.get() == Sound.Custom)
        .build());

    private final Setting<Double> playLength = sgGeneral.add(new DoubleSetting.Builder()
        .name("play-length").description("Seconds to play from the start time. 0 = to the end of the file.")
        .defaultValue(0).range(0, 600).sliderRange(0, 5).decimalPlaces(2)
        .visible(() -> sound.get() == Sound.Custom)
        .build());

    public HitSounds() {
        super(AddonTemplate.CATEGORY, "hit-sounds", "Plays a sound when your hits land. Only you hear it.");
    }

    @Override
    public meteordevelopment.meteorclient.gui.widgets.WWidget getWidget(meteordevelopment.meteorclient.gui.GuiTheme theme) {
        var list = theme.verticalList();
        list.add(com.example.addon.util.FilePicker.buttons(theme, "Choose a hit sound", folder(), customFile, CustomSound.EXTENSIONS));
        if (FileChoices.isFile(customFile.get()) && Files.isRegularFile(folder().resolve(customFile.get()))) {
            double len = CustomSound.duration(folder().resolve(customFile.get()));
            list.add(theme.label(len < 0 ? "Length: reading file... (reopen to see it)"
                : String.format("Length of %s: %.2f seconds", customFile.get(), len)));
        }
        return list;
    }

    @Override
    public void onActivate() {
        folder();
    }

    // Packets arrive on the network thread; play on the game thread
    @EventHandler
    private void onPacket(PacketEvent.Receive event) {
        if (!(event.packet instanceof EntityDamageS2CPacket packet)) return;
        mc.execute(() -> {
            if (mc.player == null) return;
            if (packet.sourceCauseId() == mc.player.getId() && packet.entityId() != mc.player.getId()) play();
        });
    }

    private void play() {
        float vol = volume.get().floatValue(), p = pitch.get().floatValue();
        if (sound.get() != Sound.Custom) {
            mc.getSoundManager().play(PositionedSoundInstance.master(sound.get().event.get(), p, vol));
            return;
        }
        if (!FileChoices.isFile(customFile.get())) return;
        Path file = folder().resolve(customFile.get());
        if (!Files.isRegularFile(file) || !CustomSound.isSupported(file)) return;
        // Outside Minecraft's engine, so apply the game's master volume ourselves
        CustomSound.play(file, vol * mc.options.getSoundVolume(SoundCategory.MASTER), p, startTime.get(), playLength.get());
    }

    private Path folder() {
        Path dir = mc.runDirectory.toPath().resolve("larper-client").resolve("hitsounds");
        try {
            Files.createDirectories(dir);
        } catch (IOException e) {
            ChatUtils.warning("Couldn't create %s", dir);
        }
        return dir;
    }

    // The setting field isn't assigned yet while its own builder runs, so read it lazily
    private String customFile() {
        return customFile == null ? null : customFile.get();
    }
}
