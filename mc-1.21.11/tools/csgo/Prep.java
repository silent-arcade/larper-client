import javax.imageio.*; import javax.imageio.stream.*; import java.awt.*; import java.awt.image.BufferedImage; import java.io.*; import java.nio.file.*; import java.util.*; import java.util.concurrent.*;
// Crops each CS:GO Hands frame to its visible pixels, scales it, and writes an index of offsets.
public class Prep { public static void main(String[] a) throws Exception {
  File src = new File(a[0]); File out = new File(a[1]); double s = Double.parseDouble(a[2]);
  String[] anims = System.getProperty("anims", "takeout1,takeout2,idle,swipeleft,swiperight,attackrecovery,inspect1,inspect2,inspect3").split(",");
  ExecutorService ex = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());
  StringBuilder json = new StringBuilder("{\"scale\":" + s + ",\"anims\":{");
  for (int ai = 0; ai < anims.length; ai++) {
    String an = anims[ai]; int n = 0; while (new File(src, an + "/" + an + "_frame" + (n + 1) + ".png").exists()) n++;
    new File(out, an).mkdirs();
    java.util.List<Future<int[]>> fs = new ArrayList<>();
    for (int i = 1; i <= n; i++) { final int fi = i; fs.add(ex.submit(() -> {
      BufferedImage im = ImageIO.read(new File(src, an + "/" + an + "_frame" + fi + ".png"));
      int w = im.getWidth(), h = im.getHeight(); int[] px = im.getRGB(0, 0, w, h, null, 0, w);
      clean(px, w, h);
      int x0 = w, y0 = h, x1 = -1, y1 = -1;
      for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) if ((px[y * w + x] >>> 24) > 4) { x0 = Math.min(x0, x); x1 = Math.max(x1, x); y0 = Math.min(y0, y); y1 = Math.max(y1, y); }
      if (x1 < 0) { x0 = y0 = 0; x1 = y1 = 0; }
      int cw = x1 - x0 + 1, ch = y1 - y0 + 1;
      BufferedImage small = shrink(px, w, x0, y0, cw, ch, s);
      if (System.getProperty("jpg") != null) writeJpgAndMask(small, new File(out, an + "/" + fi));
      else writePng(quantize(small), new File(out, an + "/" + fi + ".png"));
      return new int[]{x0, y0, cw, ch};
    })); }
    json.append(ai == 0 ? "" : ",").append('"').append(an).append("\":[");
    for (int i = 0; i < fs.size(); i++) { int[] r = fs.get(i).get(); json.append(i == 0 ? "" : ",").append("[").append(r[0]).append(",").append(r[1]).append(",").append(r[2]).append(",").append(r[3]).append("]"); }
    json.append("]"); System.out.println(an + " " + n);
  }
  json.append("}}"); Files.writeString(new File(out, "frames.json").toPath(), json); ex.shutdown();
}
  static boolean clear(int[] px, int w, int h, int x, int y) { return x < 0 || y < 0 || x >= w || y >= h || (px[y * w + x] >>> 24) == 0; }

  // Green-screen cleanup: drop lone specks, strip green spill from the cut edges (the gloves and sleeves are olive,
  // where green is about equal to red, so only clearly green pixels change), and soften the hard 1px cut.
  static void clean(int[] px, int w, int h) {
    int[] in = px.clone();
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) {
      int p = in[y * w + x]; if ((p >>> 24) == 0) continue;
      int n = 0, edge = 0;
      for (int dy = -1; dy <= 1; dy++) for (int dx = -1; dx <= 1; dx++) if ((dx | dy) != 0) { if (clear(in, w, h, x + dx, y + dy)) edge++; else n++; }
      if (n < 3) { px[y * w + x] = 0; continue; }
      int near = edge;
      if (near == 0) for (int dy = -2; dy <= 2 && near == 0; dy++) for (int dx = -2; dx <= 2; dx++) if (clear(in, w, h, x + dx, y + dy)) { near = 1; break; }
      int r = p >> 16 & 255, g = p >> 8 & 255, b = p & 255, m = Math.max(r, b);
      if (near > 0) {
        if (g > m + 40) { px[y * w + x] = 0; continue; } // pure key colour left on the cut
        g = Math.min(g, m);
      } else if (g > m + 25) g = m + (g - m) / 4;
      int alpha = edge > 0 ? 170 : 255;
      px[y * w + x] = alpha << 24 | r << 16 | g << 8 | b;
    }
  }

  // Box-filter downscale weighted by alpha, so the (black) colour of transparent pixels never bleeds into the edges
  static BufferedImage shrink(int[] px, int w, int x0, int y0, int cw, int ch, double s) {
    int ow = Math.max(1, (int) Math.round(cw * s)), oh = Math.max(1, (int) Math.round(ch * s));
    BufferedImage o = new BufferedImage(ow, oh, BufferedImage.TYPE_INT_ARGB);
    for (int oy = 0; oy < oh; oy++) for (int ox = 0; ox < ow; ox++) {
      int sx0 = x0 + (int) (ox / s), sx1 = Math.max(sx0 + 1, x0 + (int) ((ox + 1) / s)), sy0 = y0 + (int) (oy / s), sy1 = Math.max(sy0 + 1, y0 + (int) ((oy + 1) / s));
      long A = 0, R = 0, G = 0, B = 0; int cnt = 0;
      for (int y = sy0; y < Math.min(sy1, y0 + ch); y++) for (int x = sx0; x < Math.min(sx1, x0 + cw); x++) {
        int p = px[y * w + x], a = p >>> 24; A += a; R += (long) (p >> 16 & 255) * a; G += (long) (p >> 8 & 255) * a; B += (long) (p & 255) * a; cnt++; }
      if (A == 0 || cnt == 0) continue;
      o.setRGB(ox, oy, (int) (A / cnt) << 24 | (int) (R / A) << 16 | (int) (G / A) << 8 | (int) (B / A));
    }
    return o;
  }

  // 256-colour palette per frame (median cut over a 5-bit-per-channel histogram, alpha included). Frames are dark and
  // low in colour, so this is close to lossless to the eye and makes the PNGs several times smaller.
  static BufferedImage quantize(BufferedImage img) {
    int w = img.getWidth(), h = img.getHeight(); int[] px = img.getRGB(0, 0, w, h, null, 0, w);
    java.util.Map<Integer, int[]> hist = new java.util.HashMap<>(); // key -> {count, sumA, sumR, sumG, sumB}
    for (int p : px) { int a = p >>> 24; if (a == 0) continue; int k = key(p);
      int[] e = hist.computeIfAbsent(k, z -> new int[5]); e[0]++; e[1] += a; e[2] += p >> 16 & 255; e[3] += p >> 8 & 255; e[4] += p & 255; }
    java.util.List<java.util.List<int[]>> boxes = new java.util.ArrayList<>();
    java.util.List<int[]> all = new java.util.ArrayList<>();
    for (var en : hist.entrySet()) { int[] e = en.getValue(); all.add(new int[]{e[1] / e[0], e[2] / e[0], e[3] / e[0], e[4] / e[0], e[0], en.getKey()}); }
    if (!all.isEmpty()) boxes.add(all);
    while (boxes.size() < Integer.getInteger("colors", 255)) {
      int bi = -1, bc = -1, br = 0;
      for (int i = 0; i < boxes.size(); i++) { var b = boxes.get(i); if (b.size() < 2) continue;
        for (int c = 0; c < 4; c++) { int lo = 999, hi = -1; for (int[] v : b) { lo = Math.min(lo, v[c]); hi = Math.max(hi, v[c]); }
          long pop = 0; for (int[] v : b) pop += v[4]; long score = (long) (hi - lo) * (long) Math.sqrt(pop);
          if (score > br) { br = (int) Math.min(Integer.MAX_VALUE, score); bi = i; bc = c; } } }
      if (bi < 0) break;
      var b = boxes.remove(bi); final int c = bc; b.sort((x, y) -> x[c] - y[c]);
      long total = 0; for (int[] v : b) total += v[4]; long acc = 0; int cut = 1;
      for (int i = 0; i < b.size() - 1; i++) { acc += b.get(i)[4]; if (acc >= total / 2) { cut = i + 1; break; } }
      boxes.add(new java.util.ArrayList<>(b.subList(0, cut))); boxes.add(new java.util.ArrayList<>(b.subList(cut, b.size())));
    }
    int n = boxes.size() + 1; byte[] R = new byte[n], G = new byte[n], B = new byte[n], A = new byte[n]; // index 0 = transparent
    java.util.Map<Integer, Integer> map = new java.util.HashMap<>();
    for (int i = 0; i < boxes.size(); i++) { long sa = 0, sr = 0, sg = 0, sb = 0, sp = 0;
      for (int[] v : boxes.get(i)) { sa += (long) v[0] * v[4]; sr += (long) v[1] * v[4]; sg += (long) v[2] * v[4]; sb += (long) v[3] * v[4]; sp += v[4]; map.put(v[5], i + 1); }
      A[i + 1] = (byte) (sa / sp); R[i + 1] = (byte) (sr / sp); G[i + 1] = (byte) (sg / sp); B[i + 1] = (byte) (sb / sp); }
    java.awt.image.IndexColorModel cm = new java.awt.image.IndexColorModel(8, n, R, G, B, A);
    BufferedImage o = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_INDEXED, cm);
    byte[] data = ((java.awt.image.DataBufferByte) o.getRaster().getDataBuffer()).getData();
    for (int i = 0; i < px.length; i++) data[i] = (byte) ((px[i] >>> 24) == 0 ? 0 : map.get(key(px[i])));
    return o;
  }

  static int key(int p) { return (p >>> 29) << 15 | (p >> 19 & 31) << 10 | (p >> 11 & 31) << 5 | (p >> 3 & 31); }

  static void writePng(BufferedImage img, File f) throws IOException {
    ImageWriter wr = ImageIO.getImageWritersByFormatName("png").next(); ImageWriteParam p = wr.getDefaultWriteParam();
    p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); p.setCompressionQuality(0f); // 0 = smallest file
    try (ImageOutputStream o = ImageIO.createImageOutputStream(f)) { f.delete(); wr.setOutput(o); wr.write(null, new IIOImage(img, null, null), p); } finally { wr.dispose(); }
  }

  // Colour as a JPEG (great for these smooth, dark frames) + the see-through shape as a small grey PNG
  static void writeJpgAndMask(BufferedImage img, File base) throws IOException {
    int w = img.getWidth(), h = img.getHeight();
    BufferedImage rgb = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB), mask = new BufferedImage(w, h, BufferedImage.TYPE_BYTE_GRAY);
    for (int y = 0; y < h; y++) for (int x = 0; x < w; x++) { int p = img.getRGB(x, y); rgb.setRGB(x, y, p & 0xFFFFFF); mask.getRaster().setSample(x, y, 0, p >>> 24); }
    ImageWriter jw = ImageIO.getImageWritersByFormatName("jpeg").next(); ImageWriteParam p = jw.getDefaultWriteParam();
    p.setCompressionMode(ImageWriteParam.MODE_EXPLICIT); p.setCompressionQuality(Float.parseFloat(System.getProperty("jpg")));
    File jf = new File(base.getPath() + ".jpg"); jf.delete();
    try (ImageOutputStream o = ImageIO.createImageOutputStream(jf)) { jw.setOutput(o); jw.write(null, new IIOImage(rgb, null, null), p); } finally { jw.dispose(); }
    writePng(mask, new File(base.getPath() + ".a.png"));
  }
}
