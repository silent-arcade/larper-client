package com.example.addon.knife;

// Ported from the CS:GO Hands mod by StsThorn, MIT licence.

import com.example.addon.AddonTemplate;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

public final class CsgoViewmodel {
    private final String base;
    private static final float FPS = 20;
    private static final int PREFETCH = 6;
    private final Identifier texture;

    private record Frame(int x, int y, int w, int h) {}

    private final Map<String, Frame[]> anims = new HashMap<>();
    private boolean loaded;

    private String anim = "idle";
    private long start;
    private boolean swipeLeft;

    private final ExecutorService decoder = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Larper CS:GO viewmodel");
        t.setDaemon(true);
        return t;
    });
    private final Map<String, CompletableFuture<NativeImage>> pending = new HashMap<>();
    private String shownKey;
    private boolean showLeftArm = true;
    private Frame shown;
    private int texW, texH;

    public CsgoViewmodel(String knife) {
        base = "csgo/" + knife + "/";
        texture = Identifier.fromNamespaceAndPath(KnifeType.NAMESPACE, "csgo_viewmodel_" + knife);
    }

    public void takeOut() {
        play(randomAnim("takeout"));
    }

    public void inspect() {
        if (anim.startsWith("inspect")) return;
        play(randomAnim("inspect"));
    }

    private String randomAnim(String prefix) {
        load();
        List<String> names = anims.keySet().stream().filter(k -> k.startsWith(prefix)).sorted().toList();
        return names.isEmpty() ? "idle" : names.get((int) (Math.random() * names.size()));
    }

    public void attack() {
        swipeLeft = !swipeLeft;
        play(swipeLeft ? "swipeleft" : "swiperight");
    }

    private void play(String name) {
        anim = name;
        start = System.nanoTime();
    }

    private static String next(String finished) {
        return finished.startsWith("swipe") ? "attackrecovery" : "idle";
    }

    public void render(GuiGraphicsExtractor ctx, float size, float inspectSpeed, boolean leftArm) {
        if (!load()) return;
        if (leftArm != showLeftArm) {
            showLeftArm = leftArm;
            for (var f : pending.values()) f.thenAccept(img -> { if (img != null) img.close(); });
            pending.clear();
            shownKey = null;
        }
        int index = advance(anim.startsWith("inspect") ? inspectSpeed : 1);
        Frame[] frames = anims.get(anim);
        prefetch(anim, index, frames.length);
        upload(anim, index, frames[index]);
        if (shown == null) return;

        float k = ctx.guiWidth() / 1920f * size;
        float originX = ctx.guiWidth() - 1920 * k, originY = ctx.guiHeight() - 1080 * k;
        var m = ctx.pose();
        m.pushMatrix();
        m.translate(originX + shown.x * k, originY + shown.y * k);
        m.scale(shown.w * k / texW, shown.h * k / texH);
        ctx.blit(RenderPipelines.GUI_TEXTURED, texture, 0, 0, 0, 0, texW, texH, texW, texH);
        m.popMatrix();
    }

    private int advance(float speed) {
        int length = anims.get(anim).length;
        int index = (int) ((System.nanoTime() - start) / 1e9 * FPS * speed);
        if (index < length) return index;
        if (anim.equals("idle")) return index % length;
        play(next(anim));
        return 0;
    }

    private void prefetch(String name, int index, int length) {
        boolean loop = name.equals("idle");
        String then = next(name);
        for (int i = 0; i < PREFETCH; i++) {
            int f = index + i;
            if (loop) fetch(name + "/" + (f % length + 1));
            else if (f < length) fetch(name + "/" + (f + 1));
            else fetch(then + "/" + (f - length + 1));
        }
        for (Iterator<Map.Entry<String, CompletableFuture<NativeImage>>> it = pending.entrySet().iterator(); it.hasNext(); ) {
            var e = it.next();
            String a = e.getKey().substring(0, e.getKey().indexOf('/'));
            int f = Integer.parseInt(e.getKey().substring(e.getKey().indexOf('/') + 1)) - 1;
            boolean keep = a.equals(name) ? (loop ? (f - index + length) % length < PREFETCH : f >= index && f < index + PREFETCH)
                : !loop && a.equals(then) && f < index + PREFETCH - length;
            if (keep) continue;
            e.getValue().thenAccept(img -> { if (img != null) img.close(); });
            it.remove();
        }
    }

    private void fetch(String key) {
        if (key.equals(shownKey) || pending.containsKey(key)) return;
        boolean strip = !showLeftArm;
        String anim = key.substring(0, key.indexOf('/'));
        Frame frame = anims.get(anim)[Integer.parseInt(key.substring(key.indexOf('/') + 1)) - 1];
        pending.put(key, CompletableFuture.supplyAsync(() -> {
            NativeImage img = decode(key);
            if (img != null && strip) stripLeftArm(img, frame);
            return img;
        }, decoder));
    }

    private void upload(String name, int index, Frame frame) {
        String key = name + "/" + (index + 1);
        if (key.equals(shownKey)) return;
        CompletableFuture<NativeImage> f = pending.get(key);
        if (f == null || !f.isDone()) return;
        pending.remove(key);
        NativeImage img = f.getNow(null);
        if (img == null) return;
        texW = img.getWidth();
        texH = img.getHeight();
        Minecraft.getInstance().getTextureManager().register(texture, new DynamicTexture(() -> "larper csgo viewmodel", img) {
            {
                sampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
            }
        });
        shownKey = key;
        shown = frame;
    }

    static void stripLeftArm(NativeImage img, Frame f) {
        if (f.y() + f.h() < 1078) return;
        int w = img.getWidth(), h = img.getHeight();
        float k = f.w() / (float) w;
        int[] px = img.getPixels();
        int[] label = new int[w * h];
        int[] stack = new int[w * h];
        java.util.BitSet erase = new java.util.BitSet();
        int next = 0;
        for (int x = 0; x < w; x++) {
            int start = (h - 1) * w + x;
            if (label[start] != 0 || (px[start] >>> 24) == 0) continue;
            next++;
            int sp = 0, minB = x, maxB = x, top = h - 1, right = x;
            stack[sp++] = start;
            label[start] = next;
            while (sp > 0) {
                int i = stack[--sp], cx = i % w, cy = i / w;
                if (cy == h - 1) { minB = Math.min(minB, cx); maxB = Math.max(maxB, cx); }
                top = Math.min(top, cy);
                right = Math.max(right, cx);
                for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) {
                    int nx = cx + dx, ny = cy + dy;
                    if (nx < 0 || ny < 0 || nx >= w || ny >= h) continue;
                    int n = ny * w + nx;
                    if (label[n] != 0 || (px[n] >>> 24) == 0) continue;
                    label[n] = next;
                    stack[sp++] = n;
                }
            }
            boolean leftSide = f.x() + maxB * k < 1920 * 0.45f;
            boolean low = f.y() + top * k > 1080 * 0.35f;
            boolean leftHalf = f.x() + right * k < 1920 * 0.5f;
            if (leftSide && low && leftHalf) erase.set(next);
        }
        if (erase.isEmpty()) return;
        for (int i = 0; i < label.length; i++) if (label[i] != 0 && erase.get(label[i])) img.setPixel(i % w, i / w, 0);
    }

    private NativeImage decode(String key) {
        try (InputStream c = open(base + key + ".jpg"); InputStream a = open(base + key + ".a.png")) {
            java.awt.image.BufferedImage rgb = javax.imageio.ImageIO.read(c), mask = javax.imageio.ImageIO.read(a);
            int w = rgb.getWidth(), h = rgb.getHeight();
            int[] px = rgb.getRGB(0, 0, w, h, null, 0, w);
            java.awt.image.Raster alpha = mask.getRaster();
            NativeImage img = new NativeImage(w, h, false);
            for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) img.setPixel(x, y, alpha.getSample(x, y, 0) << 24 | (px[y * w + x] & 0xFFFFFF));
            return img;
        } catch (Exception e) {
            AddonTemplate.LOG.warn("Couldn't load CS:GO viewmodel frame {}: {}", key, e.toString());
            return null;
        }
    }

    private static InputStream open(String path) throws java.io.IOException {
        return Minecraft.getInstance().getResourceManager().getResourceOrThrow(Identifier.fromNamespaceAndPath(KnifeType.NAMESPACE, path)).open();
    }

    private boolean load() {
        if (loaded) return !anims.isEmpty();
        loaded = true;
        try (InputStream in = open(base + "frames.json")) {
            JsonObject json = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var e : json.getAsJsonObject("anims").entrySet()) {
                JsonArray arr = e.getValue().getAsJsonArray();
                Frame[] frames = new Frame[arr.size()];
                for (int i = 0; i < frames.length; i++) {
                    JsonArray r = arr.get(i).getAsJsonArray();
                    frames[i] = new Frame(r.get(0).getAsInt(), r.get(1).getAsInt(), r.get(2).getAsInt(), r.get(3).getAsInt());
                }
                anims.put(e.getKey(), frames);
            }
        } catch (Exception e) {
            AddonTemplate.LOG.warn("Couldn't load the CS:GO viewmodel: {}", e.toString());
        }
        return !anims.isEmpty();
    }
}
