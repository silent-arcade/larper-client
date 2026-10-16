package com.example.addon.knife;

import net.minecraft.core.component.DataComponents;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Knife models, their parts, pivots and animations.
 * Pivots are in the upright design frame of tools/gen-knife-models.js (pixels), which tilts every model by -45°
 * around (8, 8). Keep the tilt and the pivots in sync with that script.
 *
 * The butterfly is a real linkage: you hold the safe handle (A); the blade swings around A's pin, and the bite
 * handle (B) swings around the blade's other pin. Blade 180 + bite 180 = closed, with the blade between the handles.
 */
public enum KnifeType {
    Butterfly("butterfly", new float[]{7.5f, 3.6f}, new float[]{7.5f, 7.2f}, new float[]{8.5f, 7.2f},
        butterflyDeploy(), butterflyInspect(),
        new Part("butterfly_handle_a", Link.ROOT, false), new Part("butterfly_blade", Link.BLADE, true), new Part("butterfly_handle_b", Link.BITE, false)),
    // First person shows the CS:GO karambit viewmodel; this model is the inventory icon and third-person knife
    Karambit("karambit", new float[]{8.4f, 0.35f}, null, null, bayonetDeploy(), bayonetInspect(), new Part("karambit", Link.ROOT, true)),
    Bayonet("bayonet", new float[]{8, 3.8f}, null, null, bayonetDeploy(), bayonetInspect(), new Part("bayonet", Link.ROOT, true));

    public static final String NAMESPACE = "larper-client";

    // Holder class: enum constants are built before the enum's own static fields are initialized
    private static final class Geo {
        static final float TILT = (float) Math.toRadians(-45);
        // The blade's long axis in model space ("up" after the tilt)
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

    /** The whole knife (inventory icon, third person) with the given finish. */
    public ItemStack stack(Finish finish) {
        return Stacks.get(finish.apply(model));
    }

    /** A display-only stack showing one of this addon's item models (e.g. "halo"). */
    public static ItemStack modelStack(String model) {
        return Stacks.get(model);
    }

    public static boolean isKnife(ItemStack stack) {
        Identifier model = stack.get(DataComponents.ITEM_MODEL);
        return model != null && model.getNamespace().equals(NAMESPACE);
    }

    /** Model-space transform of one part for the given pose (see {@link Anim} for channels). */
    public Matrix4f partMatrix(Part part, float[] pose) {
        Matrix4f m = new Matrix4f();
        rotateAround(m, pivot, Geo.LONG_AXIS, pose[6]);
        rotateAround(m, pivot, null, pose[7]);
        if (part.link != Link.ROOT) rotateAround(m, pinA, null, pose[8]);
        if (part.link == Link.BITE) rotateAround(m, pinB, null, pose[9]);
        return m;
    }

    // axis null = model z (the blade's plane)
    private static void rotateAround(Matrix4f m, Vector3f p, Vector3f axis, float degrees) {
        if (degrees == 0) return;
        float r = (float) Math.toRadians(degrees);
        m.translate(p);
        if (axis == null) m.rotateZ(r);
        else m.rotate(r, axis);
        m.translate(-p.x, -p.y, -p.z);
    }

    // Display-only stacks, one per model id, made on first use
    private static final class Stacks {
        static final java.util.Map<String, ItemStack> CACHE = new java.util.HashMap<>();

        static ItemStack get(String model) {
            return CACHE.computeIfAbsent(model, m -> {
                ItemStack s = new ItemStack(Items.STICK);
                s.set(DataComponents.ITEM_MODEL, Identifier.fromNamespaceAndPath(NAMESPACE, m));
                return s;
            });
        }
    }

    /** Upright design-frame pixels -> model units (what the item renderer uses after the display transform). */
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

    // ---- Animations ----
    // key(time, tx, ty, tz, rotX, rotY, rotZ, roll, spin, blade, bite)
    //   tx/ty/tz: hand-space offset in blocks (x right, y up, z toward the camera)
    //   roll: around the blade's long axis, spin: in the blade's plane around the pivot
    //   blade/bite: butterfly linkage angles, multiples of 360 = open
    // The last key must be equivalent to the rest pose (whole turns) so the hand-off back to idle is seamless.

    // Like a real balisong, blade and bite only ever move between 0 (open) and 180 (folded): the blade swings into
    // the safe handle's channel and stops against its back. The big "fan" motions come from the wrist spinning
    // the whole knife (spin channel), not from the blade going round.

    private static Anim butterflyDeploy() {
        return Anim.builder()
            .key(0f, 0.02f, -0.42f, 0.04f, -40, 0, -10, 0, 0, 180, 180) // closed, below the screen
            .key(0.2f, 0, -0.1f, 0.02f, -12, 0, -4, 0, 0, 180, 180)
            .key(0.4f, 0, -0.02f, 0, 0, 0, 0, 0, -15, 0, 180)          // flick: blade swings out, bite still folded on it
            .key(0.6f, 0, 0, 0, 0, 0, 0, 0, 8, 0, 0)                    // bite handle swings down
            .key(0.75f, 0, 0, 0, 0, 0, 0, 0, -3, 0, 0)                  // catch
            .key(1.15f)
            .build();
    }

    private static Anim butterflyInspect() {
        // Raised toward the middle of the screen so the flips are in full view
        float x = -0.22f, y = 0.16f, z = 0, ry = 20, rz = 8;
        return Anim.builder()
            .key(0f)
            .key(0.35f, -0.18f, 0.13f, 0, 0, ry, rz)
            // flip it closed: bite folds onto the blade, then both swing down into the safe handle
            .key(0.55f, x, y, z, 0, ry, rz, 0, -10, 0, 180)
            .key(0.8f, x, y, z, 0, ry, rz, 0, 15, 180, 180)
            // and open again
            .key(1.05f, x, y, z, 0, ry, rz, 0, -10, 0, 180)
            .key(1.25f, x, y, z, 0, ry, rz, 0, 0, 0, 0)
            // rollover: the wrist spins the knife a full turn, the bite handle swings out and comes back
            .key(1.55f, x, y, z, 0, ry, rz, 0, -180, 0, 110)
            .key(1.85f, x, y, z, 0, ry, rz, 0, -360, 0, 0)
            // a faster close/open
            .key(2.02f, x, y, z, 0, ry, rz, 0, -370, 0, 180)
            .key(2.2f, x, y, z, 0, ry, rz, 0, -350, 180, 180)
            .key(2.38f, x, y, z, 0, ry, rz, 0, -370, 0, 180)
            .key(2.56f, x, y, z, 0, ry, rz, 0, -360, 0, 0)
            // catch it open and admire the blade, then the other side
            .key(3.0f, -0.28f, 0.17f, 0.05f, 6, 60, 12, 0, -360)
            .key(3.4f, -0.29f, 0.175f, 0.05f, 4, 66, 13, 0, -360)
            .key(3.85f, -0.24f, 0.15f, 0.04f, 0, 50, 8, 180, -360)
            .key(4.5f, 0, 0, 0, 0, 0, 0, 360, -360)
            .build();
    }

    private static Anim bayonetDeploy() {
        return Anim.builder()
            .key(0f, 0, -0.42f, 0.05f, -45, 0, 0, -360) // tossed up with a flip
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
            .key(1.85f, -0.26f, 0.17f, 0.06f, 0, 45, 12, 100) // quick flip in the fingers
            .key(2.1f, -0.26f, 0.15f, 0.06f, -5, 40, 10, 180)
            .key(2.25f, -0.26f, 0.15f, 0.06f, -5, 40, 10, 188)
            .key(2.4f, -0.26f, 0.15f, 0.06f, -5, 40, 10, 180)
            .key(2.9f, -0.22f, 0.13f, 0.05f, -10, 28, 6, 180) // look along the spine
            .key(3.3f, -0.07f, 0.04f, 0.02f, 0, 8, 2, 330)
            .key(3.8f, 0, 0, 0, 0, 0, 0, 360)
            .build();
    }
}
