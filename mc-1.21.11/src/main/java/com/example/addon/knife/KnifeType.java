package com.example.addon.knife;

import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public enum KnifeType {
    Butterfly("butterfly", new float[]{7.5f, 3.6f}, new float[]{7.5f, 7.2f}, new float[]{8.5f, 7.2f},
        butterflyDeploy(), butterflyInspect(),
        new Part("butterfly_handle_a", Link.ROOT, false), new Part("butterfly_blade", Link.BLADE, true), new Part("butterfly_handle_b", Link.BITE, false)),
    Karambit("karambit", new float[]{8.4f, 0.35f}, null, null, bayonetDeploy(), bayonetInspect(), new Part("karambit", Link.ROOT, true)),
    Bayonet("bayonet", new float[]{8, 3.8f}, null, null, bayonetDeploy(), bayonetInspect(), new Part("bayonet", Link.ROOT, true));

    public static final String NAMESPACE = "larper-client";

    private static final class Geo {
        static final float TILT = (float) Math.toRadians(-45);
        static final Vector3f LONG_AXIS = new Vector3f((float) -Math.sin(TILT), (float) Math.cos(TILT), 0).normalize();
    }

    enum Link { ROOT, BLADE, BITE }

    private final String model;
    public final Part[] parts;
    public final Anim deploy, inspect;
    private final Vector3f pivot, pinA, pinB;

    KnifeType(String model, float[] pivot, float[] pinA, float[] pinB, Anim deploy, Anim inspect, Part... parts) {
        this.model = model;
        this.pivot = toModel(pivot);
        this.pinA = pinA == null ? null : toModel(pinA);
        this.pinB = pinB == null ? null : toModel(pinB);
        this.deploy = deploy;
        this.inspect = inspect;
        this.parts = parts;
    }

    public ItemStack stack(Finish finish) {
        return Stacks.get(finish.apply(model));
    }

    public static ItemStack modelStack(String model) {
        return Stacks.get(model);
    }

    public static boolean isKnife(ItemStack stack) {
        Identifier model = stack.get(DataComponentTypes.ITEM_MODEL);
        return model != null && model.getNamespace().equals(NAMESPACE);
    }

    public Matrix4f partMatrix(Part part, float[] pose) {
        Matrix4f m = new Matrix4f();
        rotateAround(m, pivot, Geo.LONG_AXIS, pose[6]);
        rotateAround(m, pivot, null, pose[7]);
        if (part.link != Link.ROOT) rotateAround(m, pinA, null, pose[8]);
        if (part.link == Link.BITE) rotateAround(m, pinB, null, pose[9]);
        return m;
    }

    private static void rotateAround(Matrix4f m, Vector3f p, Vector3f axis, float degrees) {
        if (degrees == 0) return;
        float r = (float) Math.toRadians(degrees);
        m.translate(p);
        if (axis == null) m.rotateZ(r);
        else m.rotate(r, axis);
        m.translate(-p.x, -p.y, -p.z);
    }

    private static final class Stacks {
        static final java.util.Map<String, ItemStack> CACHE = new java.util.HashMap<>();

        static ItemStack get(String model) {
            return CACHE.computeIfAbsent(model, m -> {
                ItemStack s = new ItemStack(Items.STICK);
                s.set(DataComponentTypes.ITEM_MODEL, Identifier.of(NAMESPACE, m));
                return s;
            });
        }
    }

    private static Vector3f toModel(float[] p) {
        float dx = p[0] - 8, dy = p[1] - 8, c = (float) Math.cos(Geo.TILT), s = (float) Math.sin(Geo.TILT);
        return new Vector3f((8 + dx * c - dy * s) / 16f, (8 + dx * s + dy * c) / 16f, 0.5f);
    }

    public static final class Part {
        private final String model;
        private final boolean hasFinish;
        final Link link;

        Part(String model, Link link, boolean hasFinish) {
            this.model = model;
            this.link = link;
            this.hasFinish = hasFinish;
        }

        public ItemStack stack(Finish finish) {
            return Stacks.get(hasFinish ? finish.apply(model) : model);
        }
    }

    private static Anim butterflyDeploy() {
        return Anim.builder()
            .key(0f, 0.02f, -0.42f, 0.04f, -40, 0, -10, 0, 0, 180, 180)
            .key(0.2f, 0, -0.1f, 0.02f, -12, 0, -4, 0, 0, 180, 180)
            .key(0.4f, 0, -0.02f, 0, 0, 0, 0, 0, -15, 0, 180)
            .key(0.6f, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0)
            .key(0.75f, 0, 0, 0, 0, 0, 0, 0, -3, 0, 0)
            .key(1.15f)
            .build();
    }

    private static Anim butterflyInspect() {
        float x = -0.22f, y = 0.16f, z = 0, ry = 20, rz = 8;
        return Anim.builder()
            .key(0f)
            .key(0.35f, -0.18f, 0.13f, 0, 0, ry, rz)
            .key(0.55f, x, y, z, 0, ry, rz, 0, -10, 0, 180)
            .key(0.8f, x, y, z, 0, ry, rz, 0, 15, 180, 180)
            .key(1.05f, x, y, z, 0, ry, rz, 0, -10, 0, 180)
            .key(1.25f, x, y, z, 0, ry, rz, 0, 0, 0, 0)
            .key(1.55f, x, y, z, 0, ry, rz, 0, -180, 0, 110)
            .key(1.85f, x, y, z, 0, ry, rz, 0, -360, 0, 0)
            .key(2.02f, x, y, z, 0, ry, rz, 0, -370, 0, 180)
            .key(2.2f, x, y, z, 0, ry, rz, 0, -350, 180, 180)
            .key(2.38f, x, y, z, 0, ry, rz, 0, -370, 0, 180)
            .key(2.56f, x, y, z, 0, ry, rz, 0, -360, 0, 0)
            .key(3.0f, -0.28f, 0.17f, 0.05f, 6, 60, 12, 0, -360)
            .key(3.4f, -0.29f, 0.175f, 0.05f, 4, 66, 13, 0, -360)
            .key(3.85f, -0.24f, 0.15f, 0.04f, 0, 50, 8, 180, -360)
            .key(4.5f, 0, 0, 0, 0, 0, 0, 360, -360)
            .build();
    }

    private static Anim bayonetDeploy() {
        return Anim.builder()
            .key(0f, 0, -0.42f, 0.05f, -45, 0, 0, -360)
            .key(0.4f, 0, -0.02f, 0.02f, 6, 0, 0, -20)
            .key(0.55f, 0, 0, 0, -2, 0, 0, 6)
            .key(0.9f)
            .build();
    }

    private static Anim bayonetInspect() {
        return Anim.builder()
            .key(0f)
            .key(0.45f, -0.18f, 0.1f, 0.04f, 0, 35, 12)
            .key(1.2f, -0.26f, 0.15f, 0.06f, 8, 50, 16, -10)
            .key(1.6f, -0.27f, 0.155f, 0.06f, 6, 55, 17, -12)
            .key(1.85f, -0.26f, 0.17f, 0.06f, 0, 45, 12, 100)
            .key(2.1f, -0.26f, 0.15f, 0.06f, -5, 40, 10, 180)
            .key(2.25f, -0.26f, 0.15f, 0.06f, -5, 40, 10, 188)
            .key(2.4f, -0.26f, 0.15f, 0.06f, -5, 40, 10, 180)
            .key(2.9f, -0.22f, 0.13f, 0.05f, -10, 28, 6, 180)
            .key(3.3f, -0.07f, 0.04f, 0.02f, 0, 8, 2, 330)
            .key(3.8f, 0, 0, 0, 0, 0, 0, 360)
            .build();
    }
}
