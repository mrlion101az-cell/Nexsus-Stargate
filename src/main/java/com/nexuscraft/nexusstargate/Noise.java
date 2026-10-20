package com.nexuscraft.nexusstargate;

/** Small, fast, seeded value noise. Everything here is a pure function of its inputs, so a chunk always generates the same. */
final class Noise {

    private Noise() {
    }

    /** A well-mixed 64-bit hash of a seed and three coordinates. */
    static long hash(long seed, long a, long b, long c) {
        long h = seed * 0x9E3779B97F4A7C15L + a * 0xC2B2AE3D27D4EB4FL + b * 0x165667B19E3779F9L + c * 0x27D4EB2F165667C5L;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        h *= 0xC4CEB9FE1A85EC53L;
        h ^= h >>> 33;
        return h;
    }

    /** 0.0 (inclusive) to 1.0 (exclusive) from a hash. */
    static double unit(long h) {
        return (h >>> 11) * (1.0 / (1L << 53));
    }

    static double unit(long seed, long a, long b, long c) {
        return unit(hash(seed, a, b, c));
    }

    private static double lattice(long seed, int x, int z) {
        return unit(hash(seed, x, z, 0x51)) * 2.0 - 1.0;
    }

    private static double smooth(double t) {
        return t * t * (3.0 - 2.0 * t);
    }

    /** One octave of smooth value noise, -1 to 1. */
    static double value(long seed, double x, double z) {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = lattice(seed, x0, z0);
        double b = lattice(seed, x0 + 1, z0);
        double c = lattice(seed, x0, z0 + 1);
        double d = lattice(seed, x0 + 1, z0 + 1);
        double top = a + (b - a) * fx;
        double bottom = c + (d - c) * fx;
        return top + (bottom - top) * fz;
    }

    /** Several octaves added up and scaled back to about -1 to 1. */
    static double fbm(long seed, double x, double z, int octaves) {
        double sum = 0;
        double amp = 1;
        double norm = 0;
        double f = 1;
        for (int i = 0; i < octaves; i++) {
            sum += value(seed + i * 1013L, x * f, z * f) * amp;
            norm += amp;
            amp *= 0.5;
            f *= 2.0;
        }
        return norm == 0 ? 0 : sum / norm;
    }
}
