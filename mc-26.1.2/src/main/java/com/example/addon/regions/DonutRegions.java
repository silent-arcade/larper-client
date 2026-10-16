package com.example.addon.regions;

import net.minecraft.util.Mth;

public final class DonutRegions {
    public static final int GRID = 9;
    public static final double SIZE = 50_000, OFFSET = 225_000;
    public static final String[] CONTINENTS = {"EU Central", "EU West", "NA East", "NA West", "Asia", "Oceania"};

    private static final int[][] LAYOUT = {
        {82, 5}, {100, 3}, {101, 3}, {102, 3}, {103, 2}, {104, 2}, {105, 2}, {106, 2}, {91, 2},
        {83, 5}, {44, 3}, {75, 3}, {42, 3}, {41, 2}, {40, 2}, {39, 2}, {38, 2}, {92, 2},
        {84, 5}, {45, 3}, {14, 3}, {13, 3}, {12, 2}, {11, 2}, {10, 2}, {37, 2}, {93, 2},
        {85, 5}, {46, 5}, {74, 5}, {3, 3}, {2, 2}, {1, 2}, {25, 2}, {36, 2}, {94, 2},
        {86, 4}, {47, 4}, {72, 4}, {71, 4}, {5, 2}, {4, 2}, {24, 2}, {35, 2}, {95, 2},
        {87, 4}, {51, 1}, {17, 1}, {9, 0}, {8, 0}, {7, 0}, {23, 0}, {34, 0}, {96, 2},
        {88, 4}, {54, 1}, {18, 1}, {61, 0}, {62, 0}, {21, 0}, {22, 0}, {33, 0}, {97, 0},
        {89, 0}, {26, 1}, {27, 0}, {28, 0}, {29, 0}, {30, 0}, {59, 0}, {32, 0}, {98, 0},
        {90, 0}, {107, 1}, {108, 1}, {109, 1}, {110, 1}, {111, 1}, {112, 1}, {113, 1}, {99, 0}
    };

    private DonutRegions() {
    }

    public static int regionAt(double x, double z) {
        int col = (int) Math.floor((x + OFFSET) / SIZE), row = (int) Math.floor((z + OFFSET) / SIZE);
        if (col < 0 || row < 0 || col >= GRID || row >= GRID) return -1;
        return LAYOUT[row * GRID + col][0];
    }

    public static double[] center(int region) {
        for (int i = 0; i < LAYOUT.length; i++) {
            if (LAYOUT[i][0] != region) continue;
            int row = i / GRID, col = i % GRID;
            return new double[]{col * SIZE - OFFSET + SIZE / 2, row * SIZE - OFFSET + SIZE / 2};
        }
        return null;
    }

    public static String continent(int region) {
        for (int[] cell : LAYOUT) if (cell[0] == region) return CONTINENTS[cell[1]];
        return "?";
    }

    public static float yawTo(double x, double z, double tx, double tz) {
        return (float) Math.toDegrees(Math.atan2(-(tx - x), tz - z));
    }

    public static String compass(float yaw) {
        String[] names = {"S", "SW", "W", "NW", "N", "NE", "E", "SE"};
        int i = Math.floorMod(Math.round(Mth.wrapDegrees(yaw) / 45f), 8);
        return names[i];
    }

    public static String turn(float from, float to) {
        float d = Mth.wrapDegrees(to - from);
        if (Math.abs(d) < 3) return "straight ahead";
        return String.format("turn %.0f° %s", Math.abs(d), d > 0 ? "right" : "left");
    }

    public static String axes(double dx, double dz) {
        return String.format("%,d %s, %,d %s", Math.round(Math.abs(dx)), dx >= 0 ? "east" : "west",
            Math.round(Math.abs(dz)), dz >= 0 ? "south" : "north");
    }

    public static String nether(double x, double z) {
        return String.format("X %,d, Z %,d", Math.round(x / 8), Math.round(z / 8));
    }

    static {
        assert regionAt(-57501, 130197) == 28 : "region grid is off";
        assert "EU Central".equals(continent(28));
        double[] c = center(28);
        assert c != null && regionAt(c[0], c[1]) == 28;
        assert axes(-100, 50).equals("100 west, 50 south") && nether(-800, 1600).equals("X -100, Z 200");
    }
}
