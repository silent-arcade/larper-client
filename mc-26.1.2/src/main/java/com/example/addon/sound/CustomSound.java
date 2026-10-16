package com.example.addon.sound;

import com.example.addon.AddonTemplate;
import javazoom.jl.decoder.Bitstream;
import javazoom.jl.decoder.Decoder;
import javazoom.jl.decoder.Header;
import javazoom.jl.decoder.SampleBuffer;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Plays the user's own sound files (MP3 or WAV) outside Minecraft's sound engine, which only reads OGG.
 * Files are decoded once to 16-bit PCM and cached until they change on disk. Decoding and playback run on
 * background threads so a hit never stutters the game.
 */
public final class CustomSound {
    public static final String[] EXTENSIONS = {".mp3", ".wav"};

    private record Pcm(short[] samples, float sampleRate, int channels, long modified) {}

    private static final Map<Path, Pcm> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService PLAYER = Executors.newCachedThreadPool(r -> {
        Thread t = new Thread(r, "Larper hit sound");
        t.setDaemon(true);
        return t;
    });

    private CustomSound() {
    }

    public static boolean isSupported(Path file) {
        String name = file.getFileName().toString().toLowerCase();
        for (String ext : EXTENSIONS) if (name.endsWith(ext)) return true;
        return false;
    }

    /**
     * Length of the file in seconds, or -1 while it's still being decoded (decoding starts in the background on
     * the first call, so this never stalls the game).
     */
    public static double duration(Path file) {
        try {
            Pcm pcm = CACHE.get(file);
            if (pcm != null && pcm.modified == Files.getLastModifiedTime(file).toMillis()) {
                return pcm.samples.length / (double) pcm.channels / pcm.sampleRate;
            }
        } catch (Exception e) {
            return -1;
        }
        PLAYER.execute(() -> {
            try {
                load(file);
            } catch (Exception ignored) {
                // play() reports problems
            }
        });
        return -1;
    }

    /**
     * Plays file at volume (0..1) and pitch (playback speed, 1 = normal), starting startSeconds in and playing
     * at most lengthSeconds (0 = to the end).
     */
    public static void play(Path file, float volume, float pitch, double startSeconds, double lengthSeconds) {
        PLAYER.execute(() -> {
            try {
                Pcm pcm = load(file);
                int frames = pcm.samples.length / pcm.channels;
                int from = (int) Math.min(frames, Math.max(0, Math.round(startSeconds * pcm.sampleRate)));
                int to = lengthSeconds > 0 ? (int) Math.min(frames, from + Math.round(lengthSeconds * pcm.sampleRate)) : frames;
                int first = from * pcm.channels, count = (to - from) * pcm.channels;
                if (count <= 0) return;
                byte[] bytes = new byte[count * 2];
                for (int i = 0; i < count; i++) {
                    int s = Math.round(pcm.samples[first + i] * volume);
                    s = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, s));
                    bytes[i * 2] = (byte) s;
                    bytes[i * 2 + 1] = (byte) (s >> 8);
                }
                AudioFormat format = new AudioFormat(pcm.sampleRate * pitch, 16, pcm.channels, true, false);
                try (SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
                    line.open(format);
                    line.start();
                    line.write(bytes, 0, bytes.length);
                    line.drain();
                }
            } catch (Exception e) {
                AddonTemplate.LOG.warn("Couldn't play hit sound {}: {}", file, e.toString());
            }
        });
    }

    private static Pcm load(Path file) throws Exception {
        long modified = Files.getLastModifiedTime(file).toMillis();
        Pcm cached = CACHE.get(file);
        if (cached != null && cached.modified == modified) return cached;
        Pcm pcm = file.toString().toLowerCase().endsWith(".mp3") ? decodeMp3(file, modified) : decodeWav(file, modified);
        CACHE.put(file, pcm);
        return pcm;
    }

    private static Pcm decodeMp3(Path file, long modified) throws Exception {
        try (InputStream in = new BufferedInputStream(Files.newInputStream(file))) {
            Bitstream bitstream = new Bitstream(in);
            Decoder decoder = new Decoder();
            ShortList out = new ShortList();
            Header header;
            while ((header = bitstream.readFrame()) != null) {
                SampleBuffer buffer = (SampleBuffer) decoder.decodeFrame(header, bitstream);
                out.add(buffer.getBuffer(), buffer.getBufferLength());
                bitstream.closeFrame();
            }
            if (out.size == 0) throw new IllegalArgumentException("no MP3 audio found");
            return new Pcm(out.toArray(), decoder.getOutputFrequency(), decoder.getOutputChannels(), modified);
        }
    }

    private static Pcm decodeWav(Path file, long modified) throws Exception {
        try (AudioInputStream source = AudioSystem.getAudioInputStream(file.toFile())) {
            AudioFormat src = source.getFormat();
            AudioFormat pcm16 = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED, src.getSampleRate(), 16, src.getChannels(), src.getChannels() * 2, src.getSampleRate(), false);
            try (AudioInputStream converted = AudioSystem.getAudioInputStream(pcm16, source)) {
                ByteArrayOutputStream bytes = new ByteArrayOutputStream();
                converted.transferTo(bytes);
                byte[] b = bytes.toByteArray();
                short[] samples = new short[b.length / 2];
                for (int i = 0; i < samples.length; i++) samples[i] = (short) ((b[i * 2] & 0xFF) | (b[i * 2 + 1] << 8));
                return new Pcm(samples, src.getSampleRate(), src.getChannels(), modified);
            }
        }
    }

    private static final class ShortList {
        short[] data = new short[1 << 16];
        int size;

        void add(short[] src, int len) {
            if (size + len > data.length) data = java.util.Arrays.copyOf(data, Math.max(data.length * 2, size + len));
            System.arraycopy(src, 0, data, size, len);
            size += len;
        }

        short[] toArray() {
            return java.util.Arrays.copyOf(data, size);
        }
    }
}
