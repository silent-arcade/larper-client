package com.example.addon.playermodel;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.model.ModelPart;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayers;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class FiguraModel {
    enum Parent { NONE, HEAD, BODY, LEFT_ARM, RIGHT_ARM, LEFT_LEG, RIGHT_LEG, SKIP }

    private static final Object[][] KEYWORDS = {
        {Parent.NONE, "None", "NONE", "Model", "MODEL"},
        {Parent.HEAD, "Head", "HEAD"},
        {Parent.BODY, "Body", "BODY"},
        {Parent.LEFT_ARM, "LeftArm", "LEFT_ARM"},
        {Parent.RIGHT_ARM, "RightArm", "RIGHT_ARM"},
        {Parent.SKIP, "LeftLeggingPivot", "LEFT_LEGGING_PIVOT", "RightLeggingPivot", "RIGHT_LEGGING_PIVOT"},
        {Parent.LEFT_LEG, "LeftLeg", "LEFT_LEG"},
        {Parent.RIGHT_LEG, "RightLeg", "RIGHT_LEG"},
        {Parent.SKIP, "LeftElytraPivot", "LEFT_ELYTRA_PIVOT", "LeftWingPivot", "LEFT_WING_PIVOT", "RightElytraPivot", "RIGHT_ELYTRA_PIVOT", "RightWingPivot", "RIGHT_WING_PIVOT"},
        {Parent.BODY, "LeftElytra", "LEFT_ELYTRA", "LeftElytron", "LEFT_ELYTRON", "RightElytra", "RIGHT_ELYTRA", "RightElytron", "RIGHT_ELYTRON", "Cape", "CAPE"},
        {Parent.SKIP, "World", "WORLD", "Hud", "HUD", "HeadsUpDisplay", "Gui", "GUI", "GraphicalUserInterface", "Camera", "CAMERA", "Billboard", "BILLBOARD",
            "Skull", "SKULL", "☠", "Portrait", "PORTRAIT", "Arrow", "ARROW", "Trident", "TRIDENT", "Item", "ITEM",
            "LeftItemPivot", "LEFT_ITEM_PIVOT", "RightItemPivot", "RIGHT_ITEM_PIVOT", "LeftSpyglassPivot", "LEFT_SPYGLASS_PIVOT",
            "RightSpyglassPivot", "RIGHT_SPYGLASS_PIVOT", "LeftParrotPivot", "LEFT_PARROT_PIVOT", "RightParrotPivot", "RIGHT_PARROT_PIVOT",
            "HelmetItemPivot", "HELMET_ITEM_PIVOT", "HelmetPivot", "HELMET_PIVOT", "ChestplatePivot", "CHESTPLATE_PIVOT", "ChestplateBodyPivot",
            "LeftShoulderPivot", "LEFT_SHOULDER_PIVOT", "RightShoulderPivot", "RIGHT_SHOULDER_PIVOT", "LeggingsPivot", "LEGGINGS_PIVOT",
            "BeltPivot", "BELT_PIVOT", "LeftBootPivot", "LEFT_BOOT_PIVOT", "RightBootPivot", "RIGHT_BOOT_PIVOT"},
    };

    static Parent parentOf(String name) {
        if (name == null) return Parent.NONE;
        for (Object[] row : KEYWORDS) for (int i = 1; i < row.length; i++) if (name.startsWith((String) row[i])) return (Parent) row[0];
        return Parent.NONE;
    }

    private static final Map<Parent, Vector3f> DEFAULT_PIVOT = Map.of(
        Parent.HEAD, new Vector3f(), Parent.BODY, new Vector3f(),
        Parent.RIGHT_ARM, new Vector3f(-5, 2, 0), Parent.LEFT_ARM, new Vector3f(5, 2, 0),
        Parent.RIGHT_LEG, new Vector3f(-1.9f, 12, 0), Parent.LEFT_LEG, new Vector3f(1.9f, 12, 0));

    record Quad(int texture, float[][] pos, float[][] uv, Vector3f normal) {}

    static final class Group {
        Parent parent = Parent.NONE;
        Vector3f pivot = new Vector3f();
        Quaternionf rotation = new Quaternionf();
        final List<Group> children = new ArrayList<>();
        final List<Quad> quads = new ArrayList<>();
    }

    private static final long MAX_ENTRY = 32L << 20, MAX_TOTAL = 96L << 20;

    final List<Group> roots = new ArrayList<>();
    final List<BufferedImage> images = new ArrayList<>();
    private final List<Identifier> textures = new ArrayList<>();
    private static int counter;
    public final int faceCount;

    private FiguraModel(int faces) {
        faceCount = faces;
    }

    public static FiguraModel load(Path file) throws IOException {
        Map<String, byte[]> files = new LinkedHashMap<>();
        if (file.toString().toLowerCase(Locale.ROOT).endsWith(".zip")) {
            long total = 0;
            try (ZipInputStream zip = new ZipInputStream(Files.newInputStream(file))) {
                for (ZipEntry e; (e = zip.getNextEntry()) != null; ) {
                    String name = e.getName().replace('\\', '/');
                    String lower = name.toLowerCase(Locale.ROOT);
                    if (e.isDirectory() || !(lower.endsWith(".bbmodel") || lower.endsWith(".png") || lower.endsWith(".jpg"))) continue;
                    byte[] data = readCapped(zip);
                    total += data.length;
                    if (total > MAX_TOTAL) throw new IOException("the zip is too big (over 96 MB of models and textures)");
                    files.put(name, data);
                }
            }
        } else {
            files.put(file.getFileName().toString(), readCapped(Files.newInputStream(file)));
        }

        Builder b = new Builder(files);
        for (Map.Entry<String, byte[]> f : files.entrySet()) {
            if (f.getKey().toLowerCase(Locale.ROOT).endsWith(".bbmodel")) b.bbmodel(f.getKey(), new String(f.getValue(), StandardCharsets.UTF_8));
        }
        if (b.faces == 0) throw new IOException("no Blockbench model (.bbmodel) with faces found in it");
        FiguraModel m = new FiguraModel(b.faces);
        m.roots.addAll(b.roots);
        m.images.addAll(b.images);
        return m;
    }

    private static byte[] readCapped(InputStream in) throws IOException {
        byte[] data = in.readNBytes((int) MAX_ENTRY + 1);
        if (data.length > MAX_ENTRY) throw new IOException("a file in it is too big (over 32 MB)");
        return data;
    }

    private static final class Builder {
        final Map<String, byte[]> files;
        final List<Group> roots = new ArrayList<>();
        final List<BufferedImage> images = new ArrayList<>();
        int faces;
        int[] texId;
        float[] uvW, uvH;
        Map<String, Integer> texByUuid;
        Map<String, JsonObject> groupData;

        Builder(Map<String, byte[]> files) {
            this.files = files;
        }

        void bbmodel(String path, String json) throws IOException {
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            float resW = 16, resH = 16;
            if (root.has("resolution")) {
                resW = root.getAsJsonObject("resolution").get("width").getAsFloat();
                resH = root.getAsJsonObject("resolution").get("height").getAsFloat();
            }
            JsonArray texs = root.has("textures") ? root.getAsJsonArray("textures") : new JsonArray();
            texId = new int[texs.size()];
            uvW = new float[texs.size()];
            uvH = new float[texs.size()];
            texByUuid = new HashMap<>();
            String dir = path.contains("/") ? path.substring(0, path.lastIndexOf('/') + 1) : "";
            for (int i = 0; i < texs.size(); i++) {
                JsonObject t = texs.get(i).getAsJsonObject();
                BufferedImage img = image(t, dir);
                texId[i] = -1;
                if (img == null) continue;
                texId[i] = images.size();
                images.add(img);
                uvW[i] = t.has("uv_width") ? t.get("uv_width").getAsFloat() : resW;
                uvH[i] = t.has("uv_height") ? t.get("uv_height").getAsFloat() : resH;
                if (t.has("uuid")) texByUuid.put(t.get("uuid").getAsString(), i);
            }

            Map<String, JsonObject> elements = new LinkedHashMap<>();
            for (JsonElement e : root.getAsJsonArray("elements")) {
                JsonObject o = e.getAsJsonObject();
                elements.put(o.has("uuid") ? o.get("uuid").getAsString() : "#" + elements.size(), o);
            }
            groupData = new HashMap<>();
            if (root.has("groups")) for (JsonElement g : root.getAsJsonArray("groups")) {
                JsonObject o = g.getAsJsonObject();
                if (o.has("uuid")) groupData.put(o.get("uuid").getAsString(), o);
            }
            Group model = new Group();
            if (root.has("outliner")) {
                for (JsonElement child : root.getAsJsonArray("outliner")) node(child, model, elements);
            } else {
                for (JsonObject el : elements.values()) element(el, model);
            }
            roots.add(model);
        }

        BufferedImage image(JsonObject t, String dir) throws IOException {
            String source = t.has("source") ? t.get("source").getAsString() : "";
            if (source.startsWith("data:")) {
                byte[] png = Base64.getDecoder().decode(source.substring(source.indexOf(',') + 1));
                return ImageIO.read(new ByteArrayInputStream(png));
            }
            String name = t.has("name") ? t.get("name").getAsString() : "";
            if (!name.toLowerCase(Locale.ROOT).endsWith(".png")) name += ".png";
            for (String key : new String[]{dir + name, name}) {
                byte[] data = files.get(key);
                if (data != null) return ImageIO.read(new ByteArrayInputStream(data));
            }
            for (Map.Entry<String, byte[]> f : files.entrySet()) if (f.getKey().endsWith("/" + name)) return ImageIO.read(new ByteArrayInputStream(f.getValue()));
            return null;
        }

        void node(JsonElement node, Group parent, Map<String, JsonObject> elements) {
            if (node.isJsonPrimitive()) {
                JsonObject el = elements.get(node.getAsString());
                if (el != null) element(el, parent);
                return;
            }
            JsonObject outline = node.getAsJsonObject();
            JsonObject g = !outline.has("name") && outline.has("uuid") ? groupData.getOrDefault(outline.get("uuid").getAsString(), outline) : outline;
            if (g.has("visibility") && !g.get("visibility").getAsBoolean()) return;
            Group group = new Group();
            String name = g.has("name") ? g.get("name").getAsString() : "";
            group.parent = parentOf(name);
            if (group.parent == Parent.SKIP) return;
            group.pivot = toVanilla(vec(g, "origin"));
            group.rotation = rotation(vec(g, "rotation"));
            if (outline.has("children")) for (JsonElement c : outline.getAsJsonArray("children")) node(c, group, elements);
            parent.children.add(group);
        }

        void element(JsonObject el, Group into) {
            if (el.has("visibility") && !el.get("visibility").getAsBoolean()) return;
            String type = el.has("type") ? el.get("type").getAsString() : "cube";
            Vector3f origin = vec(el, "origin");
            Quaternionf rot = rotation(vec(el, "rotation"));
            Vector3f pivotV = toVanilla(origin);
            if (type.equals("mesh")) mesh(el, origin, rot, pivotV, into);
            else if (type.equals("cube")) cube(el, rot, pivotV, into);
        }

        static final float[][][] FACES = {
            {{1, 0, 0}, {0, 0, 0}, {0, 1, 0}, {1, 1, 0}, {0, 0, -1}},
            {{0, 0, 1}, {1, 0, 1}, {1, 1, 1}, {0, 1, 1}, {0, 0, 1}},
            {{1, 0, 1}, {1, 0, 0}, {1, 1, 0}, {1, 1, 1}, {1, 0, 0}},
            {{0, 0, 0}, {0, 0, 1}, {0, 1, 1}, {0, 1, 0}, {-1, 0, 0}},
            {{0, 1, 1}, {1, 1, 1}, {1, 1, 0}, {0, 1, 0}, {0, 1, 0}},
            {{0, 0, 0}, {1, 0, 0}, {1, 0, 1}, {0, 0, 1}, {0, -1, 0}},
        };
        static final String[] FACE_NAMES = {"north", "south", "east", "west", "up", "down"};
        static final float[][] UV_CORNERS = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};

        void cube(JsonObject el, Quaternionf rot, Vector3f pivotV, Group into) {
            Vector3f from = vec(el, "from"), to = vec(el, "to");
            float inflate = el.has("inflate") ? el.get("inflate").getAsFloat() : 0;
            from.sub(inflate, inflate, inflate);
            to.add(inflate, inflate, inflate);
            Vector3f size = new Vector3f(to).sub(from);
            JsonObject faceObjs = el.has("faces") ? el.getAsJsonObject("faces") : new JsonObject();
            for (int f = 0; f < 6; f++) {
                if (!faceObjs.has(FACE_NAMES[f])) continue;
                JsonObject face = faceObjs.getAsJsonObject(FACE_NAMES[f]);
                int tex = textureOf(face);
                if (tex < 0 || !face.has("uv")) continue;
                JsonArray uvA = face.getAsJsonArray("uv");
                float u0 = uvA.get(0).getAsFloat() / uvW[tex], v0 = uvA.get(1).getAsFloat() / uvH[tex];
                float u1 = uvA.get(2).getAsFloat() / uvW[tex], v1 = uvA.get(3).getAsFloat() / uvH[tex];
                int turn = face.has("rotation") ? (int) (face.get("rotation").getAsFloat() / 90) : 0;
                float[][] pos = new float[4][], uv = new float[4][];
                for (int i = 0; i < 4; i++) {
                    float[] k = FACES[f][i];
                    Vector3f p = toVanilla(new Vector3f(from.x + size.x * k[0], from.y + size.y * k[1], from.z + size.z * k[2]));
                    pos[i] = rotateAround(p, pivotV, rot);
                    float[] c = UV_CORNERS[(i + turn) % 4];
                    uv[i] = new float[]{u0 + (u1 - u0) * c[0], v0 + (v1 - v0) * c[1]};
                }
                float[] n = FACES[f][4];
                Vector3f normal = rot.transform(new Vector3f(-n[0], -n[1], n[2]));
                into.quads.add(new Quad(texId[tex], pos, uv, normal));
                faces++;
            }
        }

        void mesh(JsonObject el, Vector3f origin, Quaternionf rot, Vector3f pivotV, Group into) {
            JsonObject verts = el.getAsJsonObject("vertices");
            if (verts == null || !el.has("faces")) return;
            for (Map.Entry<String, JsonElement> fe : el.getAsJsonObject("faces").entrySet()) {
                JsonObject face = fe.getValue().getAsJsonObject();
                int tex = textureOf(face);
                if (tex < 0 || !face.has("vertices") || !face.has("uv")) continue;
                JsonArray keys = face.getAsJsonArray("vertices");
                int n = keys.size();
                if (n < 3 || n > 4) continue;
                float[][] pos = new float[4][], uv = new float[4][];
                Vector3f[] pts = new Vector3f[n];
                for (int i = 0; i < n; i++) {
                    String key = keys.get(i).getAsString();
                    JsonArray v = verts.getAsJsonArray(key);
                    pts[i] = toVanilla(new Vector3f(origin.x + v.get(0).getAsFloat(), origin.y + v.get(1).getAsFloat(), origin.z + v.get(2).getAsFloat()));
                    JsonArray t = face.getAsJsonObject("uv").getAsJsonArray(key);
                    uv[i] = new float[]{t.get(0).getAsFloat() / uvW[tex], t.get(1).getAsFloat() / uvH[tex]};
                    pos[i] = rotateAround(pts[i], pivotV, rot);
                }
                if (n == 4) sortQuad(pos, uv);
                else {
                    pos[3] = pos[2];
                    uv[3] = uv[2];
                }
                Vector3f a = new Vector3f(pos[1][0] - pos[0][0], pos[1][1] - pos[0][1], pos[1][2] - pos[0][2]);
                Vector3f b2 = new Vector3f(pos[2][0] - pos[0][0], pos[2][1] - pos[0][1], pos[2][2] - pos[0][2]);
                Vector3f normal = a.cross(b2);
                if (normal.lengthSquared() > 1e-9) normal.normalize();
                into.quads.add(new Quad(texId[tex], pos, uv, normal));
                faces++;
            }
        }

        int textureOf(JsonObject face) {
            if (!face.has("texture") || face.get("texture").isJsonNull()) return -1;
            JsonElement t = face.get("texture");
            int i = t.getAsJsonPrimitive().isNumber() ? t.getAsInt() : texByUuid.getOrDefault(t.getAsString(), -1);
            return i >= 0 && i < texId.length && texId[i] >= 0 ? i : -1;
        }
    }

    static void sortQuad(float[][] pos, float[][] uv) {
        Vector3f c = new Vector3f();
        for (float[] p : pos) c.add(p[0] / 4, p[1] / 4, p[2] / 4);
        Vector3f e1 = new Vector3f(pos[0][0] - c.x, pos[0][1] - c.y, pos[0][2] - c.z);
        Vector3f n = new Vector3f(pos[1][0] - pos[0][0], pos[1][1] - pos[0][1], pos[1][2] - pos[0][2]).cross(pos[2][0] - pos[0][0], pos[2][1] - pos[0][1], pos[2][2] - pos[0][2]);
        if (n.lengthSquared() < 1e-12) return;
        Vector3f e2 = new Vector3f(n).cross(e1);
        Integer[] order = {0, 1, 2, 3};
        double[] ang = new double[4];
        for (int i = 0; i < 4; i++) {
            Vector3f d = new Vector3f(pos[i][0] - c.x, pos[i][1] - c.y, pos[i][2] - c.z);
            ang[i] = Math.atan2(d.dot(e2), d.dot(e1));
        }
        java.util.Arrays.sort(order, (x, y) -> Double.compare(ang[x], ang[y]));
        float[][] p2 = new float[4][], u2 = new float[4][];
        for (int i = 0; i < 4; i++) {
            p2[i] = pos[order[i]];
            u2[i] = uv[order[i]];
        }
        System.arraycopy(p2, 0, pos, 0, 4);
        System.arraycopy(u2, 0, uv, 0, 4);
    }

    static Vector3f vec(JsonObject o, String key) {
        if (!o.has(key)) return new Vector3f();
        JsonArray a = o.getAsJsonArray(key);
        return new Vector3f(a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat());
    }

    static Vector3f toVanilla(Vector3f bb) {
        return new Vector3f(-bb.x, 24 - bb.y, bb.z);
    }

    static Quaternionf rotation(Vector3f deg) {
        return new Quaternionf().rotationZYX((float) Math.toRadians(deg.z), (float) Math.toRadians(-deg.y), (float) Math.toRadians(-deg.x));
    }

    static float[] rotateAround(Vector3f p, Vector3f pivot, Quaternionf rot) {
        Vector3f d = rot.transform(new Vector3f(p).sub(pivot)).add(pivot);
        return new float[]{d.x, d.y, d.z};
    }

    private void upload() {
        if (textures.size() == images.size()) return;
        var tm = MinecraftClient.getInstance().getTextureManager();
        int id = counter++;
        for (int i = textures.size(); i < images.size(); i++) {
            BufferedImage img = images.get(i);
            NativeImage nat = new NativeImage(img.getWidth(), img.getHeight(), false);
            for (int y = 0; y < img.getHeight(); y++) for (int x = 0; x < img.getWidth(); x++) nat.setColorArgb(x, y, img.getRGB(x, y));
            Identifier tid = Identifier.of("larper-client", "player_model/" + id + "_" + i);
            tm.registerTexture(tid, new NativeImageBackedTexture(() -> "larper player model", nat));
            textures.add(tid);
        }
    }

    public void close() {
        var tm = MinecraftClient.getInstance().getTextureManager();
        for (Identifier id : textures) tm.destroyTexture(id);
        textures.clear();
    }

    public void render(MatrixStack matrices, OrderedRenderCommandQueue queue, BipedEntityModel<?> model, int light) {
        upload();
        matrices.push();
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        Map<Integer, List<float[]>> byTexture = new HashMap<>();
        for (Group g : roots) collect(g, new Matrix4f(), model, byTexture);
        for (Map.Entry<Integer, List<float[]>> e : byTexture.entrySet()) {
            List<float[]> verts = e.getValue();
            queue.submitCustom(matrices, RenderLayers.entityTranslucent(textures.get(e.getKey()), true), (entry, vc) -> emit(entry, vc, verts, light));
        }
        matrices.pop();
    }

    public boolean renderArm(MatrixStack matrices, OrderedRenderCommandQueue queue, BipedEntityModel<?> model, boolean right, int light) {
        upload();
        Parent want = right ? Parent.RIGHT_ARM : Parent.LEFT_ARM;
        Map<Integer, List<float[]>> byTexture = new HashMap<>();
        for (Group g : roots) findArm(g, want, model, byTexture);
        if (byTexture.isEmpty()) return false;
        matrices.push();
        matrices.scale(1 / 16f, 1 / 16f, 1 / 16f);
        for (Map.Entry<Integer, List<float[]>> e : byTexture.entrySet()) {
            List<float[]> verts = e.getValue();
            queue.submitCustom(matrices, RenderLayers.entityTranslucent(textures.get(e.getKey()), true), (entry, vc) -> emit(entry, vc, verts, light));
        }
        matrices.pop();
        return true;
    }

    private void findArm(Group g, Parent want, BipedEntityModel<?> model, Map<Integer, List<float[]>> out) {
        if (g.parent == want) collect(g, new Matrix4f(), model, out);
        else for (Group c : g.children) findArm(c, want, model, out);
    }

    private void collect(Group g, Matrix4f parentMat, BipedEntityModel<?> model, Map<Integer, List<float[]>> out) {
        Matrix4f m = new Matrix4f(parentMat);
        ModelPart part = switch (g.parent) {
            case HEAD -> model.head;
            case BODY -> model.body;
            case LEFT_ARM -> model.leftArm;
            case RIGHT_ARM -> model.rightArm;
            case LEFT_LEG -> model.leftLeg;
            case RIGHT_LEG -> model.rightLeg;
            default -> null;
        };
        if (part != null && !part.visible) return;
        if (part != null) {
            Vector3f def = DEFAULT_PIVOT.get(g.parent);
            m.translate(part.originX - def.x, part.originY - def.y, part.originZ - def.z);
            m.translate(g.pivot).rotate(new Quaternionf().rotationZYX(part.roll, part.yaw, part.pitch)).rotate(g.rotation).translate(-g.pivot.x, -g.pivot.y, -g.pivot.z);
        } else {
            m.translate(g.pivot).rotate(g.rotation).translate(-g.pivot.x, -g.pivot.y, -g.pivot.z);
        }
        Matrix3f nm = new Matrix3f(m).normal();
        Vector3f p = new Vector3f(), n = new Vector3f();
        for (Quad q : g.quads) {
            List<float[]> list = out.computeIfAbsent(q.texture(), k -> new ArrayList<>());
            nm.transform(n.set(q.normal()));
            for (int i = 0; i < 4; i++) {
                m.transformPosition(p.set(q.pos()[i][0], q.pos()[i][1], q.pos()[i][2]));
                list.add(new float[]{p.x, p.y, p.z, q.uv()[i][0], q.uv()[i][1], n.x, n.y, n.z});
            }
        }
        for (Group c : g.children) collect(c, m, model, out);
    }

    private static void emit(MatrixStack.Entry entry, VertexConsumer vc, List<float[]> verts, int light) {
        for (float[] v : verts) {
            vc.vertex(entry, v[0], v[1], v[2]).color(0xFFFFFFFF).texture(v[3], v[4]).overlay(OverlayTexture.DEFAULT_UV).light(light).normal(entry, v[5], v[6], v[7]);
        }
    }
}
