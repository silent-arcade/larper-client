package com.example.addon.sky;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.w3c.dom.NamedNodeMap;
import org.w3c.dom.Node;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class AnimatedSky {
    public record Gif(List<BufferedImage> frames, int[] delaysMs) {}

    private static final int MAX_BYTES = 40 << 20, MAX_FRAMES = 150;
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NORMAL)
        .connectTimeout(Duration.ofSeconds(10)).build();
    private static final Pattern META = Pattern.compile("<meta[^>]+(?:property|name)=[\"'](?:og:image|og:image:url|og:video|og:video:url|twitter:image)[\"'][^>]*>", Pattern.CASE_INSENSITIVE);
    private static final Pattern CONTENT = Pattern.compile("content=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE);
    private static final Pattern ANY_GIF = Pattern.compile("https?://[^\"'\\s<>]+?\\.gif(?:\\?[^\"'\\s<>]*)?", Pattern.CASE_INSENSITIVE);

    private static int[] delays;
    private static int width, height, shown = -1;
    private static long startMs;
    private static List<Identifier> ids = List.of();
    private static final Map<Identifier, NativeImageBackedTexture> live = new java.util.HashMap<>();

    private AnimatedSky() {
    }

    public static byte[] download(String link) throws IOException, InterruptedException {
        URI uri = URI.create(link.trim());
        if (!"https".equalsIgnoreCase(uri.getScheme()) && !"http".equalsIgnoreCase(uri.getScheme())) throw new IOException("the link must start with https://");
        HttpResponse<byte[]> r = get(uri);
        String type = r.headers().firstValue("content-type").orElse("").toLowerCase();
        if (type.startsWith("image/")) return r.body();
        String page = new String(r.body(), StandardCharsets.UTF_8);
        if (r.statusCode() == 403 || r.headers().firstValue("cf-mitigated").isPresent() || page.contains("Just a moment..."))
            throw new IOException("that site blocks downloads (bot check). Open the GIF in your browser, right-click it, \"Copy image address\", and paste that link");
        if (r.statusCode() != 200) throw new IOException("the site answered " + r.statusCode());
        List<String> candidates = new ArrayList<>();
        Matcher m = META.matcher(page);
        while (m.find()) {
            Matcher c = CONTENT.matcher(m.group());
            if (c.find()) candidates.add(c.group(1).replace("&amp;", "&"));
        }
        Matcher g = ANY_GIF.matcher(page);
        while (g.find()) candidates.add(g.group().replace("&amp;", "&"));
        candidates.sort((a, b) -> Boolean.compare(!a.toLowerCase().contains(".gif"), !b.toLowerCase().contains(".gif")));
        for (String c : candidates) {
            try {
                HttpResponse<byte[]> img = get(uri.resolve(c));
                if (img.statusCode() == 200 && img.headers().firstValue("content-type").orElse("").toLowerCase().startsWith("image/")) return img.body();
            } catch (IOException ignored) {
            }
        }
        throw new IOException("no GIF found on that page. Paste the GIF's own link (right-click it, \"Copy image address\")");
    }

    private static HttpResponse<byte[]> get(URI uri) throws IOException, InterruptedException {
        HttpResponse<InputStream> r = HTTP.send(HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(20))
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) LarperClient").GET().build(), HttpResponse.BodyHandlers.ofInputStream());
        try (InputStream in = r.body()) {
            byte[] body = in.readNBytes(MAX_BYTES + 1);
            if (body.length > MAX_BYTES) throw new IOException("the file is too big (over 40 MB)");
            return new HttpResponse<>() {
                public int statusCode() { return r.statusCode(); }
                public HttpRequest request() { return r.request(); }
                public java.util.Optional<HttpResponse<byte[]>> previousResponse() { return java.util.Optional.empty(); }
                public java.net.http.HttpHeaders headers() { return r.headers(); }
                public byte[] body() { return body; }
                public java.util.Optional<javax.net.ssl.SSLSession> sslSession() { return r.sslSession(); }
                public URI uri() { return r.uri(); }
                public HttpClient.Version version() { return r.version(); }
            };
        }
    }

    public static Gif decode(byte[] bytes) throws IOException {
        try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            var readers = ImageIO.getImageReaders(in);
            if (!readers.hasNext()) throw new IOException("not an image ImageIO can read (GIF, PNG or JPG; WebP/MP4 links don't work)");
            ImageReader reader = readers.next();
            reader.setInput(in);
            if (!reader.getFormatName().equalsIgnoreCase("gif")) {
                BufferedImage still = reader.read(0);
                return new Gif(List.of(still), new int[]{100});
            }
            int count = Math.min(MAX_FRAMES, reader.getNumImages(true));
            int w = 0, h = 0;
            Node screen = child(reader.getStreamMetadata() == null ? null : reader.getStreamMetadata().getAsTree("javax_imageio_gif_stream_1.0"), "LogicalScreenDescriptor");
            if (screen != null) {
                w = attr(screen, "logicalScreenWidth");
                h = attr(screen, "logicalScreenHeight");
            }
            List<BufferedImage> frames = new ArrayList<>();
            int[] delays = new int[count];
            BufferedImage canvas = null;
            for (int i = 0; i < count; i++) {
                BufferedImage part = reader.read(i);
                IIOMetadata meta = reader.getImageMetadata(i);
                Node root = meta.getAsTree("javax_imageio_gif_image_1.0");
                Node desc = child(root, "ImageDescriptor"), gce = child(root, "GraphicControlExtension");
                int x = desc == null ? 0 : attr(desc, "imageLeftPosition"), y = desc == null ? 0 : attr(desc, "imageTopPosition");
                if (canvas == null) canvas = new BufferedImage(Math.max(w, x + part.getWidth()), Math.max(h, y + part.getHeight()), BufferedImage.TYPE_INT_ARGB);
                BufferedImage before = null;
                String disposal = gce == null ? "none" : gce.getAttributes().getNamedItem("disposalMethod").getNodeValue();
                if (disposal.equals("restoreToPrevious")) before = copy(canvas);
                Graphics2D g = canvas.createGraphics();
                g.drawImage(part, x, y, null);
                g.dispose();
                frames.add(copy(canvas));
                int d = gce == null ? 10 : attr(gce, "delayTime");
                delays[i] = (d <= 1 ? 10 : d) * 10;
                if (disposal.equals("restoreToBackgroundColor")) {
                    Graphics2D c = canvas.createGraphics();
                    c.setComposite(java.awt.AlphaComposite.Clear);
                    c.fillRect(x, y, part.getWidth(), part.getHeight());
                    c.dispose();
                } else if (before != null) canvas = before;
            }
            reader.dispose();
            if (frames.isEmpty()) throw new IOException("the GIF has no frames");
            return new Gif(frames, java.util.Arrays.copyOf(delays, frames.size()));
        }
    }

    private static BufferedImage copy(BufferedImage src) {
        BufferedImage out = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.drawImage(src, 0, 0, null);
        g.dispose();
        return out;
    }

    private static Node child(Node node, String name) {
        if (node == null) return null;
        for (Node n = node.getFirstChild(); n != null; n = n.getNextSibling()) if (n.getNodeName().equals(name)) return n;
        return null;
    }

    private static int attr(Node node, String name) {
        NamedNodeMap a = node.getAttributes();
        Node v = a == null ? null : a.getNamedItem(name);
        return v == null ? 0 : Integer.parseInt(v.getNodeValue());
    }

    private static final long CACHE_BYTES = 192L << 20;
    private static final java.util.concurrent.ExecutorService WORKER = java.util.concurrent.Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "Larper sky frames");
        t.setDaemon(true);
        return t;
    });

    private static List<BufferedImage> sources;
    private static java.util.function.Function<BufferedImage, BufferedImage> convert;
    private static NativeImage[] cache;
    private static final Map<Integer, java.util.concurrent.CompletableFuture<NativeImage>> pending = new java.util.HashMap<>();
    private static int cacheable, generation;

    public static void play(List<BufferedImage> frames, int[] delaysMs, java.util.function.Function<BufferedImage, BufferedImage> toCube,
                            int cubeWidth, int cubeHeight, List<Identifier> textureIds) {
        stop();
        sources = frames;
        convert = toCube;
        delays = delaysMs;
        ids = textureIds;
        width = cubeWidth;
        height = cubeHeight;
        cache = new NativeImage[frames.size()];
        cacheable = (int) Math.min(frames.size(), CACHE_BYTES / ((long) width * height * 4));
        startMs = System.currentTimeMillis();
        shown = -1;
    }

    public static void stop() {
        generation++;
        sources = null;
        if (cache != null) for (NativeImage img : cache) if (img != null) img.close();
        cache = null;
        for (var f : pending.values()) f.thenAccept(img -> { if (img != null) img.close(); });
        pending.clear();
        shown = -1;
        live.clear();
    }

    public static boolean playing() {
        return sources != null;
    }

    private static java.util.concurrent.CompletableFuture<NativeImage> request(int i) {
        return pending.computeIfAbsent(i, k -> {
            int gen = generation;
            BufferedImage src = sources.get(k);
            var toCube = convert;
            int w = width, h = height;
            return java.util.concurrent.CompletableFuture.supplyAsync(() -> {
                if (gen != generation) return null;
                int[] px = toCube.apply(src).getRGB(0, 0, w, h, null, 0, w);
                NativeImage img = new NativeImage(w, h, false);
                for (int y = 0, j = 0; y < h; y++) for (int x = 0; x < w; x++, j++) img.setColorArgb(x, y, px[j]);
                return img;
            }, WORKER);
        });
    }

    private static NativeImage ready(int i) {
        if (cache[i] != null) return cache[i];
        var f = request(i);
        if (!f.isDone()) return null;
        pending.remove(i);
        NativeImage img = f.join();
        if (img != null && i < cacheable) cache[i] = img;
        return img;
    }

    public static void tick() {
        if (sources == null) return;
        int total = 0;
        for (int d : delays) total += d;
        long t = (System.currentTimeMillis() - startMs) % Math.max(1, total);
        int index = 0;
        while (index < delays.length - 1 && t >= delays[index]) t -= delays[index++];

        var tm = MinecraftClient.getInstance().getTextureManager();
        boolean reattached = false;
        for (Identifier id : ids) {
            NativeImageBackedTexture tex = live.get(id);
            AbstractTexture current = tm.getTexture(id);
            if (tex == null || current != tex) {
                tex = new NativeImageBackedTexture(() -> "larper animated sky", new NativeImage(width, height, false));
                tm.registerTexture(id, tex);
                live.put(id, tex);
                reattached = true;
                shown = -1;
            }
        }
        request((index + 1) % sources.size());
        if (index == shown && !reattached) return;
        NativeImage frame = ready(index);
        if (frame == null) return;
        for (NativeImageBackedTexture tex : live.values()) {
            NativeImage img = tex.getImage();
            if (img == null) continue;
            img.copyFrom(frame);
            tex.upload();
        }
        shown = index;
        if (index >= cacheable) frame.close();
    }
}
