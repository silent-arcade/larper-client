package com.example.addon.sky;

import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.awt.image.BufferedImage;

/**
 * Turns a 360° panorama (equirectangular, 2:1) into the 3x2 cube texture Skyboxify reads for OptiFine skies.
 *
 * The face table mirrors Skyboxify's SkyPart: each face is the bottom square (y = -1, u along x, v along z)
 * rotated into place, and owns one third by one half of the texture. Directions are Minecraft world
 * directions (x east, y up, z south), so north is -z.
 */
public final class SkyboxifyCube {
    private record Face(Matrix4f rotation, float u0, float v0, float u1, float v1) {}

    private static final float THIRD = 1 / 3f, TWO_THIRDS = 2 / 3f;
    private static final Face[] FACES = {
        new Face(new Matrix4f().rotateY(rad(90)), 0, 0, THIRD, 0.5f),                                  // bottom
        new Face(new Matrix4f().rotateX(rad(180)).rotateY(rad(-90)), THIRD, 0, TWO_THIRDS, 0.5f),      // top
        new Face(new Matrix4f().rotateX(rad(90)).rotateZ(rad(90)), TWO_THIRDS, 0, 1, 0.5f),            // east
        new Face(new Matrix4f().rotateX(rad(90)).rotateZ(rad(180)), 0, 0.5f, THIRD, 1),                // south
        new Face(new Matrix4f().rotateX(rad(90)).rotateZ(rad(-90)), THIRD, 0.5f, TWO_THIRDS, 1),       // west
        new Face(new Matrix4f().rotateX(rad(90)), TWO_THIRDS, 0.5f, 1, 1),                             // north
    };

    private SkyboxifyCube() {
    }

    /** faceSize: pixels per cube face. yawDegrees turns the panorama around you. */
    public static BufferedImage convert(BufferedImage pano, int faceSize, double yawDegrees) {
        return convert(pano, faceSize, yawDegrees, false, 1);
    }

    /**
     * aboveHorizon: squeeze the whole image into the visible sky (horizon to straight up) instead of the full sphere;
     * below the horizon repeats the image's bottom row. repeat: how many times the image goes around you.
     */
    public static BufferedImage convert(BufferedImage pano, int faceSize, double yawDegrees, boolean aboveHorizon, int repeat) {
        int w = faceSize * 3, h = faceSize * 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Vector3f d = new Vector3f();
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float tu = (x + 0.5f) / w, tv = (y + 0.5f) / h;
                Face f = face(tu, tv);
                // Position inside the face (0..1), then on the base square, then rotated into the world
                float fu = (tu - f.u0) / (f.u1 - f.u0), fv = (tv - f.v0) / (f.v1 - f.v0);
                f.rotation.transformDirection(d.set(fu * 2 - 1, -1, fv * 2 - 1)).normalize();
                out.setRGB(x, y, sample(pano, d, yawDegrees, aboveHorizon, repeat));
            }
        }
        return out;
    }

    /**
     * The image laid flat on the cube: each wall shows the whole image above the horizon (2:1, no curving),
     * the ceiling continues the image's top colour, and below the horizon is a smooth fill in its bottom colour,
     * darkening toward the floor (repeating the edge rows there made long streaks).
     */
    public static BufferedImage convertFlat(BufferedImage img, int faceSize, double yawDegrees) {
        int w = faceSize * 3, h = faceSize * 2;
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Vector3f d = new Vector3f();
        // Same quarter-turn correction as sample(), so walls face the way the compass says
        double a = Math.toRadians(yawDegrees + 90), cos = Math.cos(a), sin = Math.sin(a);
        int ground = rowAverage(img, img.getHeight() - 1), top = rowAverage(img, 0);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                float tu = (x + 0.5f) / w, tv = (y + 0.5f) / h;
                Face f = face(tu, tv);
                float fu = (tu - f.u0) / (f.u1 - f.u0), fv = (tv - f.v0) / (f.v1 - f.v0);
                f.rotation.transformDirection(d.set(fu * 2 - 1, -1, fv * 2 - 1));
                double dx = d.x * cos - d.z * sin, dz = d.z * cos + d.x * sin, dy = d.y;
                out.setRGB(x, y, flat(img, ground, top, dx, dy, dz));
            }
        }
        return out;
    }

    // Direction (x east, y up, z south) -> color on the flat-walled cube
    static int flat(BufferedImage img, int ground, int top, double x, double y, double z) {
        double ax = Math.abs(x), ay = Math.abs(y), az = Math.abs(z);
        double u, v;
        if (ay >= ax && ay >= az) {
            if (y < 0) return shade(ground, 0.55);                // floor: the darkest ground colour
            // ceiling: the image's top-edge colour, a little brighter overhead, so it carries on from the walls
            double centre = 1 - Math.max(Math.abs(x), Math.abs(z)) / ay;
            return lighten(top, 0.12 * centre);
        } else {
            double m = Math.max(ax, az), t = y / m;              // t: -1 bottom of wall .. 1 top
            if (t < 0) return shade(ground, 1 + 0.45 * t);       // below the horizon: smooth, darker lower down
            // Horizontal position, left to right as seen when facing that wall
            double side = az >= ax ? (z < 0 ? x : -x) : (x > 0 ? z : -z);
            u = (side / m + 1) / 2;
            v = 1 - t;                                            // image fills the wall above the horizon
        }
        return pixel(img, u, v);
    }

    // Average colour of one row of the image
    private static int rowAverage(BufferedImage img, int y) {
        long r = 0, g = 0, b = 0;
        int w = img.getWidth();
        for (int x = 0; x < w; x++) {
            int p = img.getRGB(x, y);
            r += p >> 16 & 255;
            g += p >> 8 & 255;
            b += p & 255;
        }
        return 0xFF000000 | (int) (r / w) << 16 | (int) (g / w) << 8 | (int) (b / w);
    }

    private static int lighten(int rgb, double k) {
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255;
        r += (int) ((255 - r) * k);
        g += (int) ((255 - g) * k);
        b += (int) ((255 - b) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    private static int shade(int rgb, double k) {
        int r = (int) ((rgb >> 16 & 255) * k), g = (int) ((rgb >> 8 & 255) * k), b = (int) ((rgb & 255) * k);
        return 0xFF000000 | r << 16 | g << 8 | b;
    }

    // Bilinear: blends the four nearest pixels, so a small image scaled up onto the sky stays smooth instead of blocky
    private static int pixel(BufferedImage img, double u, double v) {
        int w = img.getWidth(), h = img.getHeight();
        double fx = Math.max(0, Math.min(w - 1, u * w - 0.5)), fy = Math.max(0, Math.min(h - 1, v * h - 0.5));
        int x0 = (int) fx, y0 = (int) fy, x1 = Math.min(w - 1, x0 + 1), y1 = Math.min(h - 1, y0 + 1);
        double tx = fx - x0, ty = fy - y0;
        int p00 = img.getRGB(x0, y0), p10 = img.getRGB(x1, y0), p01 = img.getRGB(x0, y1), p11 = img.getRGB(x1, y1);
        int out = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            double top = (p00 >> shift & 255) * (1 - tx) + (p10 >> shift & 255) * tx;
            double bottom = (p01 >> shift & 255) * (1 - tx) + (p11 >> shift & 255) * tx;
            out |= (int) Math.round(top * (1 - ty) + bottom * ty) << shift;
        }
        return out;
    }

    /** Panorama color in world direction d. The middle of the image faces north, left/right edges south. */
    static int sample(BufferedImage pano, Vector3f d, double yawDegrees) {
        return sample(pano, d, yawDegrees, false, 1);
    }

    static int sample(BufferedImage pano, Vector3f d, double yawDegrees, boolean aboveHorizon, int repeat) {
        // 0 = north, +90° = east. Skyboxify draws the cube turned a quarter turn further than its face table
        // says (checked in game: without the +90° the north label appeared when facing east), so add it here.
        double lon = Math.atan2(d.x, -d.z) + Math.toRadians(yawDegrees + 90);
        double lat = Math.asin(Math.max(-1, Math.min(1, d.y)));
        double u = (0.5 + lon / (2 * Math.PI)) * repeat;
        u -= Math.floor(u);
        double v = aboveHorizon ? Math.min(1, 1 - lat / (Math.PI / 2)) : 0.5 - lat / Math.PI;
        return pixel(pano, u, v);
    }

    private static Face face(float u, float v) {
        int col = Math.min(2, (int) (u * 3)), row = v < 0.5f ? 0 : 1;
        return FACES[row * 3 + col];
    }

    private static float rad(double degrees) {
        return (float) Math.toRadians(degrees);
    }
}
