package com.example.addon.visuals;

import meteordevelopment.meteorclient.renderer.Renderer3D;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import java.util.function.DoubleFunction;

public final class Fx {
    private Fx() {
    }

    public static Color hue(double h, int alpha) {
        int rgb = java.awt.Color.HSBtoRGB((float) (h - Math.floor(h)), 0.85f, 1f);
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
    }

    public static Color alpha(Color c, int alpha) {
        return new Color(c.r, c.g, c.b, Mth.clamp(alpha, 0, 255));
    }

    public static double time() {
        return System.nanoTime() / 1e9;
    }

    public static Vec3 pos(Entity e, float tickDelta) {
        return e.getPosition(tickDelta);
    }

    private static Vec3[] facing() {
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 forward = Vec3.directionFromRotation(cam.xRot(), cam.yRot());
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        right = right.lengthSqr() < 1e-6 ? new Vec3(1, 0, 0) : right.normalize();
        return new Vec3[] {right, right.cross(forward).normalize()};
    }

    public static void glow(Renderer3D r, Vec3 c, double radius, Color color) {
        glow(r, c, radius, color, false);
    }

    public static void glow(Renderer3D r, Vec3 c, double radius, Color color, boolean low) {
        glow(r, c, radius, color, !low, low);
    }

    public static void glow(Renderer3D r, Vec3 c, double radius, Color color, boolean bloom, boolean cheap) {
        Vec3[] f = facing();
        if (bloom) disc(r, c, f[0], f[1], radius * 2.3, alpha(color, color.a / 5), alpha(color, 0), cheap ? 10 : 20);
        disc(r, c, f[0], f[1], radius, alpha(color, color.a), alpha(color, 0), cheap ? 8 : 18);
        Color core = new Color(255, 255, 255, Math.min(255, color.a + 40));
        disc(r, c, f[0], f[1], radius * 0.32, core, alpha(color, color.a), cheap ? 6 : 12);
    }

    public static void spark(Renderer3D r, Vec3 c, double size, Color color, boolean low) {
        Vec3[] f = facing();
        Color clear = alpha(color, 0);
        for (int k = 0; k < 2; k++) {
            Vec3 along = f[k].scale(size), across = f[1 - k].scale(size * 0.12);
            Vec3 a = c.subtract(along), b = c.add(along), s = c.add(across), t = c.subtract(across);
            r.quad(a.x, a.y, a.z, s.x, s.y, s.z, b.x, b.y, b.z, t.x, t.y, t.z, clear, color, clear, color);
        }
        if (!low) glow(r, c, size * 0.35, color, true);
    }

    public static void beam(Renderer3D r, Vec3 base, double radius, double height, Color color, boolean low) {
        int segs = low ? 6 : 14;
        Color top = alpha(color, 0);
        for (int i = 0; i < segs; i++) {
            double a0 = Math.PI * 2 * i / segs, a1 = Math.PI * 2 * (i + 1) / segs;
            double x0 = base.x + Math.cos(a0) * radius, z0 = base.z + Math.sin(a0) * radius;
            double x1 = base.x + Math.cos(a1) * radius, z1 = base.z + Math.sin(a1) * radius;
            r.quad(x0, base.y, z0, x1, base.y, z1, x1, base.y + height, z1, x0, base.y + height, z0, color, color, top, top);
        }
    }

    private static void disc(Renderer3D r, Vec3 c, Vec3 a, Vec3 b, double radius, Color centre, Color rim, int segments) {
        for (int i = 0; i < segments; i++) {
            double t0 = Math.PI * 2 * i / segments, t1 = Math.PI * 2 * (i + 1) / segments;
            Vec3 p0 = c.add(a.scale(Math.cos(t0) * radius)).add(b.scale(Math.sin(t0) * radius));
            Vec3 p1 = c.add(a.scale(Math.cos(t1) * radius)).add(b.scale(Math.sin(t1) * radius));
            r.quad(c.x, c.y, c.z, p0.x, p0.y, p0.z, p1.x, p1.y, p1.z, c.x, c.y, c.z, centre, rim, rim, centre);
        }
    }

    public static void ring(Renderer3D r, Vec3 c, double inner, double outer, int segments, DoubleFunction<Color> colorAt) {
        ring(r, c, inner, outer, segments, colorAt, false);
    }

    public static void ring(Renderer3D r, Vec3 c, double inner, double outer, int segments, DoubleFunction<Color> colorAt, boolean low) {
        ring(r, c, inner, outer, segments, colorAt, !low, low);
    }

    public static void ring(Renderer3D r, Vec3 c, double inner, double outer, int segments, DoubleFunction<Color> colorAt, boolean soft, boolean cheap) {
        if (cheap) segments = Math.max(12, segments / 3);
        double feather = Math.max(0.04, (outer - inner) * 0.9);
        for (int i = 0; i < segments; i++) {
            double f0 = (double) i / segments, f1 = (double) (i + 1) / segments;
            double t0 = Math.PI * 2 * f0, t1 = Math.PI * 2 * f1;
            double cos0 = Math.cos(t0), sin0 = Math.sin(t0), cos1 = Math.cos(t1), sin1 = Math.sin(t1);
            Color c0 = colorAt.apply(f0), c1 = colorAt.apply(f1);
            band(r, c, cos0, sin0, cos1, sin1, inner, outer, c0, c0, c1, c1);
            if (!soft) continue;
            Color s0 = alpha(c0, c0.a / 2), s1 = alpha(c1, c1.a / 2), z0 = alpha(c0, 0), z1 = alpha(c1, 0);
            band(r, c, cos0, sin0, cos1, sin1, outer, outer + feather, s0, z0, z1, s1);
            if (inner > 0) band(r, c, cos0, sin0, cos1, sin1, Math.max(0, inner - feather), inner, z0, s0, s1, z1);
        }
    }

    private static void band(Renderer3D r, Vec3 c, double cos0, double sin0, double cos1, double sin1, double r0, double r1, Color a, Color b, Color d, Color e) {
        r.quad(c.x + cos0 * r0, c.y, c.z + sin0 * r0, c.x + cos0 * r1, c.y, c.z + sin0 * r1,
            c.x + cos1 * r1, c.y, c.z + sin1 * r1, c.x + cos1 * r0, c.y, c.z + sin1 * r0, a, b, d, e);
    }

    public static void shockwave(Renderer3D r, Vec3 at, double f, double size, Color color) {
        shockwave(r, at, f, size, color, false);
    }

    public static void shockwave(Renderer3D r, Vec3 at, double f, double size, Color color, boolean low) {
        shockwave(r, at, f, size, color, !low, low);
    }

    public static void shockwave(Renderer3D r, Vec3 at, double f, double size, Color color, boolean bloom, boolean cheap) {
        double ease = 1 - Math.pow(1 - f, 3);
        double outer = 0.3 + size * ease, inner = Math.max(0, outer - 0.15 - 0.4 * (1 - f));
        int a = (int) (color.a * (1 - f));
        Vec3 ground = at.add(0, 0.05, 0);
        ring(r, ground, inner, outer, 56, u -> alpha(color, a), bloom, cheap);
        if (!cheap && f > 0.12) {
            double g = (f - 0.12) / 0.88, o2 = 0.2 + size * 0.6 * (1 - Math.pow(1 - g, 2));
            ring(r, ground.add(0, 0.01, 0), Math.max(0, o2 - 0.08), o2, 40, u -> alpha(color, (int) (color.a * 0.6 * (1 - g))), bloom, false);
        }
        if (!cheap && f < 0.5) beam(r, ground, 0.35 + 0.3 * f, 3.5 * (1 - f), alpha(color, (int) (color.a * 0.5 * (1 - f / 0.5))), false);
        if (f < 0.35) glow(r, at.add(0, 0.6, 0), 0.6 + size * 0.5 * f, alpha(color, (int) (230 * (1 - f / 0.35))), bloom, cheap);
    }
}
