package com.example.addon.knife;

public final class Anim {
    public static final int CHANNELS = 10;

    private final float[] times;
    private final float[][] values;

    private Anim(float[] times, float[][] values) {
        this.times = times;
        this.values = values;
    }

    public float duration() {
        return times[times.length - 1];
    }

    public void sample(float t, float[] out) {
        int n = times.length;
        if (t <= times[0]) { System.arraycopy(values[0], 0, out, 0, CHANNELS); return; }
        if (t >= times[n - 1]) { System.arraycopy(values[n - 1], 0, out, 0, CHANNELS); return; }

        int i = 0;
        while (t > times[i + 1]) i++;
        float t0 = times[i], t1 = times[i + 1], h = t1 - t0, u = (t - t0) / h;

        float u2 = u * u, u3 = u2 * u;
        float h00 = 2 * u3 - 3 * u2 + 1, h10 = u3 - 2 * u2 + u, h01 = -2 * u3 + 3 * u2, h11 = u3 - u2;

        for (int c = 0; c < CHANNELS; c++) {
            float m0 = tangent(i, c), m1 = tangent(i + 1, c);
            out[c] = h00 * values[i][c] + h10 * h * m0 + h01 * values[i + 1][c] + h11 * h * m1;
        }
    }

    private float tangent(int k, int c) {
        if (k == 0 || k == times.length - 1) return 0;
        float d0 = (values[k][c] - values[k - 1][c]) / (times[k] - times[k - 1]);
        float d1 = (values[k + 1][c] - values[k][c]) / (times[k + 1] - times[k]);
        if (d0 * d1 <= 0) return 0;
        float m = (values[k + 1][c] - values[k - 1][c]) / (times[k + 1] - times[k - 1]);
        float limit = 3 * Math.min(Math.abs(d0), Math.abs(d1));
        return Math.signum(m) * Math.min(Math.abs(m), limit);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static final class Builder {
        private final java.util.List<Float> times = new java.util.ArrayList<>();
        private final java.util.List<float[]> values = new java.util.ArrayList<>();

        public Builder key(float t, float... v) {
            if (!times.isEmpty() && t <= times.get(times.size() - 1)) throw new IllegalArgumentException("keys must be in time order");
            if (v.length > CHANNELS) throw new IllegalArgumentException("too many channels");
            float[] full = new float[CHANNELS];
            System.arraycopy(v, 0, full, 0, v.length);
            times.add(t);
            values.add(full);
            return this;
        }

        public Anim build() {
            if (times.size() < 2) throw new IllegalStateException("need at least two keys");
            float[] t = new float[times.size()];
            for (int i = 0; i < t.length; i++) t[i] = times.get(i);
            return new Anim(t, values.toArray(new float[0][]));
        }
    }
}
