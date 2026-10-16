package com.example.addon.visuals;

import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;

/** Drawing helpers shared by the visual modules (everything is drawn only on your screen). */
public final class Fx {
    private Fx() {
    }

    /** Hue (0..1, wraps) at full saturation, with alpha (0..255). */
    public static Color hue(double h, int alpha) {
        int rgb = java.awt.Color.HSBtoRGB((float) (h - Math.floor(h)), 0.85f, 1f);
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.r, c.g, c.b, MathHelper.clamp(alpha, 0, 255));
    }

    /** Seconds since start, smooth (for animations). */
    public static double time() {
        return System.nanoTime() / 1e9;
    }

    /** Where an entity is this frame (between ticks), at its feet. */
    public static Vec3d pos(Entity e, float tickDelta) {
        return e.getLerpedPos(tickDelta);
    }

    /**
     * A soft glowing ball: a disc that always faces the camera, bright in the middle and fading to clear at the
     * edge, with a small white-hot core.
     */
    public static void glow(Renderer3D r, Vec3d c, double radius, Color color) {
        Camera cam = MinecraftClient.getInstance().gameRenderer.getCamera();
        Vec3d forward = Vec3d.fromPolar(cam.getPitch(), cam.getYaw());
        Vec3d right = forward.crossProduct(new Vec3d(0, 1, 0)).normalize();
        if (right.lengthSquared() < 1e-6) right = new Vec3d(1, 0, 0);
        Vec3d up = right.crossProduct(forward).normalize();
        disc(r, c, right, up, radius, alpha(color, color.a), alpha(color, 0), 18);
        Color core = new Color(255, 255, 255, Math.min(255, color.a + 40));
        disc(r, c, right, up, radius * 0.32, core, alpha(color, color.a), 12);
    }

    // A filled disc in the plane spanned by a/b, as a fan of quads (each is a triangle: the centre counted twice)
    private static void disc(Renderer3D r, Vec3d c, Vec3d a, Vec3d b, double radius, Color centre, Color rim, int segments) {
        for (int i = 0; i < segments; i++) {
            double t0 = Math.PI * 2 * i / segments, t1 = Math.PI * 2 * (i + 1) / segments;
            Vec3d p0 = c.add(a.multiply(Math.cos(t0) * radius)).add(b.multiply(Math.sin(t0) * radius));
            Vec3d p1 = c.add(a.multiply(Math.cos(t1) * radius)).add(b.multiply(Math.sin(t1) * radius));
            r.quad(c.x, c.y, c.z, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, c.x, c.y, c.z, centre, rim, rim, centre);
        }
    }

    /** A flat ring on the ground (between two radii), coloured around its length. */
    public static void ring(Renderer3D r, Vec3d c, double inner, double outer, int segments, java.util.function.DoubleFunction<Color> colorAt) {
        for (int i = 0; i < segments; i++) {
            double f0 = (double) i / segments, f1 = (double) (i + 1) / segments;
            double t0 = Math.PI * 2 * f0, t1 = Math.PI * 2 * f1;
            Color c0 = colorAt.apply(f0), c1 = colorAt.apply(f1);
            r.quad(c.x + Math.cos(t0) * inner, c.y, c.z + Math.sin(t0) * inner,
                c.x + Math.cos(t0) * outer, c.y, c.z + Math.sin(t0) * outer,
                c.x + Math.cos(t1) * outer, c.y, c.z + Math.sin(t1) * outer,
                c.x + Math.cos(t1) * inner, c.y, c.z + Math.sin(t1) * inner,
                c0, c0, c1, c1);
        }
    }
}
