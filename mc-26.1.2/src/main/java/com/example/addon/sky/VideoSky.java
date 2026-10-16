package com.example.addon.sky;

import com.example.addon.AddonTemplate;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.FloatControl;
import javax.sound.sampled.SourceDataLine;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundSource;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class VideoSky {
    public record Source(String video, String audio, String title, double fps) {}

    private final String ffmpeg;
    private final Source source;
    private final int fps, srcW, srcH, outW, outH;
    private final int[] lookup;
    private final List<Identifier> ids;
    private final boolean sound, loop;
    private volatile float volume;

    private static final int RING = 4;
    private final Object lock = new Object();
    private final ByteBuffer[] slots = new ByteBuffer[RING];
    private final long[] slotTime = new long[RING];
    private final boolean[] slotFull = new boolean[RING];
    private long clockStart, pausedAt, pausedTotal;
    private double from;
    private final Map<Identifier, DynamicTexture> live = new HashMap<>();

    private Process videoProc, audioProc;
    private volatile int run;
    private volatile double position;
    private volatile boolean paused, ended;
    private volatile long resumedAt, lastFrameAt;
    private volatile String lastError;

    public VideoSky(String ffmpeg, Source source, int fps, int faceSize, int srcW, int srcH, int[] lookup, List<Identifier> ids, boolean sound, boolean loop, float volume) {
        this.ffmpeg = ffmpeg;
        this.source = source;
        this.fps = fps;
        this.srcW = srcW;
        this.srcH = srcH;
        this.outW = faceSize * 3;
        this.outH = faceSize * 2;
        this.lookup = lookup;
        this.ids = ids;
        this.sound = sound;
        this.loop = loop;
        this.volume = volume;
        for (int i = 0; i < RING; i++) slots[i] = ByteBuffer.allocateDirect(outW * outH * 4).order(ByteOrder.LITTLE_ENDIAN);
    }

    public static Source resolve(String ytdlp, String deno, String link, int maxHeight) throws IOException, InterruptedException {
        if (deno == null) maxHeight = Math.min(maxHeight, 480);
        List<String> cmd = new ArrayList<>(List.of(ytdlp, "--no-playlist", "--no-warnings"));
        if (deno != null) cmd.addAll(List.of("--js-runtimes", "deno:" + deno));
        cmd.addAll(List.of("-f",
            "bv*[height<=" + maxHeight + "][vcodec^=avc1]+ba[ext=m4a]/bv*[height<=" + maxHeight + "]+ba/b[height<=" + maxHeight + "]/b",
            "--print", "%(fps)s", "--print", "%(title)s", "-g", "--", link));
        Process p = new ProcessBuilder(cmd).redirectErrorStream(false).start();
        p.getOutputStream().close();
        String out = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        String err = new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(90, TimeUnit.SECONDS) || p.exitValue() != 0) {
            p.destroyForcibly();
            String line = err.lines().filter(l -> l.contains("ERROR")).findFirst().orElse(err.lines().reduce((a, b) -> b).orElse("unknown error"));
            throw new IOException("yt-dlp: " + line.replace("ERROR: ", ""));
        }
        List<String> lines = out.lines().map(String::trim).filter(l -> !l.isEmpty()).toList();
        List<String> urls = lines.stream().filter(l -> l.startsWith("https://")).toList();
        if (lines.size() < 3 || urls.isEmpty()) throw new IOException("yt-dlp didn't return a playable stream");
        double fps = 30;
        try {
            fps = Double.parseDouble(lines.get(0));
        } catch (NumberFormatException ignored) {
        }
        return new Source(urls.get(0), urls.size() > 1 ? urls.get(1) : urls.get(0), lines.get(1), fps);
    }

    public String title() {
        return source.title();
    }

    public boolean paused() {
        return paused;
    }

    public boolean ended() {
        return ended;
    }

    public double stalledFor() {
        return paused || ended ? 0 : (System.nanoTime() - lastFrameAt) / 1e9;
    }

    public String lastError() {
        return lastError;
    }

    public double position() {
        return position;
    }

    public void volume(float v) {
        volume = v;
    }

    public void play(double from) {
        stopProcesses();
        paused = false;
        ended = false;
        position = from;
        this.from = from;
        lastFrameAt = System.nanoTime();
        lastError = null;
        int myRun = ++run;
        synchronized (lock) {
            java.util.Arrays.fill(slotFull, false);
            clockStart = 0;
            pausedTotal = 0;
            lock.notifyAll();
        }
        try {
            videoProc = start(List.of("-ss", fmt(from), "-i", source.video(), "-an", "-vf", "fps=" + fps + ",scale=" + srcW + ":" + srcH + ":flags=bilinear",
                "-f", "rawvideo", "-pix_fmt", "rgba", "pipe:1"));
            if (sound) audioProc = start(List.of("-ss", fmt(from), "-i", source.audio(), "-vn", "-f", "s16le", "-ac", "2", "-ar", "48000", "pipe:1"));
        } catch (IOException e) {
            AddonTemplate.LOG.warn("Video sky: couldn't start ffmpeg", e);
            ended = true;
            return;
        }
        Process v = videoProc, a = audioProc;
        thread("Larper video sky", () -> readVideo(v, myRun));
        if (a != null) thread("Larper video sky sound", () -> playAudio(a, myRun));
    }

    public void pause() {
        if (ended || paused) return;
        synchronized (lock) {
            pausedAt = System.nanoTime();
            paused = true;
        }
    }

    public void resume() {
        if (ended) {
            play(0);
            return;
        }
        if (!paused) return;
        synchronized (lock) {
            if (clockStart != 0) pausedTotal += System.nanoTime() - pausedAt;
            resumedAt = System.nanoTime();
            lastFrameAt = resumedAt;
            paused = false;
            lock.notifyAll();
        }
    }

    public void stop() {
        run++;
        synchronized (lock) {
            lock.notifyAll();
        }
        stopProcesses();
        live.clear();
    }

    private Process start(List<String> args) throws IOException {
        List<String> cmd = new ArrayList<>();
        cmd.add(ffmpeg);
        cmd.add("-hide_banner");
        cmd.add("-loglevel");
        cmd.add("error");
        cmd.add("-nostdin");
        cmd.addAll(args);
        Process p = new ProcessBuilder(cmd).start();
        p.getOutputStream().close();
        thread("Larper video sky log", () -> {
            try (var err = new java.io.BufferedReader(new java.io.InputStreamReader(p.getErrorStream(), StandardCharsets.UTF_8))) {
                for (String line; (line = err.readLine()) != null; ) if (!line.isBlank()) lastError = line.trim();
            } catch (IOException ignored) {
            }
        });
        return p;
    }

    private void stopProcesses() {
        if (videoProc != null) videoProc.destroyForcibly();
        if (audioProc != null) audioProc.destroyForcibly();
        videoProc = audioProc = null;
    }

    private static void thread(String name, Runnable r) {
        Thread t = new Thread(r, name);
        t.setDaemon(true);
        t.start();
    }

    private static String fmt(double seconds) {
        return String.format(java.util.Locale.ROOT, "%.3f", Math.max(0, seconds));
    }

    private void readVideo(Process p, int myRun) {
        ByteBuffer frame = ByteBuffer.allocateDirect(srcW * srcH * 4).order(ByteOrder.LITTLE_ENDIAN);
        long frames = 0, frameNanos = 1_000_000_000L / fps;
        try (ReadableByteChannel in = Channels.newChannel(p.getInputStream())) {
            while (run == myRun) {
                frame.clear();
                while (frame.hasRemaining()) if (in.read(frame) < 0) throw new java.io.EOFException();
                int slot = (int) (frames % RING);
                synchronized (lock) {
                    while (slotFull[slot] && run == myRun) lock.wait(20);
                }
                if (run != myRun) return;
                convert(frame, slots[slot]);
                synchronized (lock) {
                    slotTime[slot] = frames * frameNanos;
                    slotFull[slot] = true;
                }
                frames++;
            }
        } catch (IOException e) {
            if (run != myRun) return;
            if (frames == 0) {
                ended = true;
                return;
            }
            boolean justResumed = System.nanoTime() - resumedAt < 5_000_000_000L;
            double at = position;
            if (justResumed) Minecraft.getInstance().execute(() -> { if (run == myRun) play(at); });
            else if (loop) Minecraft.getInstance().execute(() -> { if (run == myRun) play(0); });
            else ended = true;
        } catch (InterruptedException ignored) {
        }
    }

    private int[] srcPx, outPx;

    private void convert(ByteBuffer frame, ByteBuffer out) {
        if (srcPx == null) {
            srcPx = new int[srcW * srcH];
            outPx = new int[lookup.length];
        }
        frame.position(0);
        frame.asIntBuffer().get(srcPx);
        int[] src = srcPx, dst = outPx, map = lookup;
        int ground = average(src, srcH - 1), top = average(src, 0);
        int n = map.length, parts = 4, chunk = (n + parts - 1) / parts;
        java.util.stream.IntStream.range(0, parts).parallel().forEach(part -> {
            for (int i = part * chunk, end = Math.min(n, i + chunk); i < end; i++) {
                int k = map[i];
                if (k >= 0) dst[i] = src[k] | 0xFF000000;
                else {
                    int code = -k - 1;
                    double f = (code % 1024) / 1000.0;
                    dst[i] = (code / 1024 == SkyboxifyCube.GROUND ? scale(ground, f) : lighten(top, f)) | 0xFF000000;
                }
            }
        });
        out.position(0);
        out.asIntBuffer().put(dst);
    }

    private int average(int[] src, int row) {
        long r = 0, g = 0, b = 0;
        int n = 0;
        for (int x = 0; x < srcW; x += 8, n++) {
            int c = src[row * srcW + x];
            r += c & 255;
            g += c >> 8 & 255;
            b += c >> 16 & 255;
        }
        return (int) (r / n) | (int) (g / n) << 8 | (int) (b / n) << 16;
    }

    private static int scale(int c, double k) {
        return (int) ((c & 255) * k) | (int) ((c >> 8 & 255) * k) << 8 | (int) ((c >> 16 & 255) * k) << 16;
    }

    private static int lighten(int c, double k) {
        int r = c & 255, g = c >> 8 & 255, b = c >> 16 & 255;
        return r + (int) ((255 - r) * k) | g + (int) ((255 - g) * k) << 8 | b + (int) ((255 - b) * k) << 16;
    }

    private void playAudio(Process p, int myRun) {
        AudioFormat format = new AudioFormat(48000, 16, 2, true, false);
        try (InputStream in = p.getInputStream(); SourceDataLine line = AudioSystem.getSourceDataLine(format)) {
            line.open(format, 48000);
            line.start();
            byte[] buf = new byte[4096];
            float applied = -1;
            int n;
            while (run == myRun) {
                if (paused) {
                    line.stop();
                    while (paused && run == myRun) Thread.sleep(10);
                    line.start();
                    continue;
                }
                if ((n = in.read(buf)) <= 0) break;
                float want = volume * Minecraft.getInstance().options.getFinalSoundSourceVolume(SoundSource.MASTER);
                if (want != applied && line.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
                    FloatControl gain = (FloatControl) line.getControl(FloatControl.Type.MASTER_GAIN);
                    gain.setValue(Math.max(gain.getMinimum(), Math.min(gain.getMaximum(), want <= 0.0001f ? gain.getMinimum() : (float) (20 * Math.log10(want)))));
                    applied = want;
                }
                line.write(buf, 0, n);
            }
            if (run != myRun) line.flush();
            line.stop();
        } catch (Exception e) {
            if (run == myRun) AddonTemplate.LOG.warn("Video sky sound stopped", e);
        }
    }

    public void tick() {
        var tm = Minecraft.getInstance().getTextureManager();
        boolean reattached = false;
        for (Identifier id : ids) {
            DynamicTexture tex = live.get(id);
            AbstractTexture current = tm.getTexture(id);
            if (tex == null || current != tex) {
                tex = new DynamicTexture(() -> "larper video sky", new NativeImage(outW, outH, false));
                tm.register(id, tex);
                live.put(id, tex);
                reattached = true;
            }
        }
        synchronized (lock) {
            long now = System.nanoTime();
            int pick = -1;
            for (int i = 0; i < RING; i++) if (slotFull[i] && (pick < 0 || slotTime[i] < slotTime[pick])) pick = i;
            if (pick < 0) return;
            if (clockStart == 0) clockStart = now - slotTime[pick];
            long videoNow = (paused ? pausedAt : now) - clockStart - pausedTotal;
            int show = -1;
            for (int i = 0; i < RING; i++) if (slotFull[i] && slotTime[i] <= videoNow && (show < 0 || slotTime[i] > slotTime[show])) show = i;
            if (show < 0 && !reattached) return;
            if (show < 0) show = pick;
            long shownTime = slotTime[show];
            for (int i = 0; i < RING; i++) if (slotFull[i] && slotTime[i] <= shownTime) slotFull[i] = false;
            position = from + shownTime / 1e9;
            lastFrameAt = now;
            lock.notifyAll();
            var encoder = RenderSystem.getDevice().createCommandEncoder();
            for (DynamicTexture tex : live.values()) {
                slots[show].position(0);
                encoder.writeToTexture(tex.getTexture(), slots[show], NativeImage.Format.RGBA, 0, 0, 0, 0, outW, outH);
            }
        }
    }
}
