package com.nexuscraft.nexusstargate;

import org.bukkit.Material;

/**
 * The things standing on a gate world, each described as "what block is at this offset from my centre". Because it is
 * a pure function of the offset, every chunk that a structure touches draws its own piece and the pieces always agree.
 * dx and dz are measured from the centre; dy is 1 for the first block above the (levelled) ground.
 */
final class Structures {

    private Structures() {
    }

    /** The tallest any structure goes above the ground (so the generator knows how far up to look). */
    static int height(Style.Kind kind) {
        return switch (kind) {
            case PYRAMID -> 8;
            case OBELISK -> 15;
            case COLUMN_RING -> 7;
            case DOME -> 10;
            case FORTRESS -> 11;
            case RUIN_WALL -> 5;
            case SPIKE -> 12;
            case SPRUCE -> 12;
            case JUNGLE_TREE -> 17;
            case DEAD_TREE -> 8;
        };
    }

    /** The block at an offset from the centre, or null for "leave whatever is there". */
    static Material blockAt(Style style, Style.Kind kind, long h, int dx, int dy, int dz) {
        Style.Palette p = style.palette;
        return switch (kind) {
            case PYRAMID -> pyramid(p, h, dx, dy, dz);
            case OBELISK -> obelisk(p, dx, dy, dz);
            case COLUMN_RING -> columnRing(p, h, dx, dy, dz);
            case DOME -> dome(p, h, dx, dy, dz);
            case FORTRESS -> fortress(p, h, dx, dy, dz);
            case RUIN_WALL -> ruinWall(p, h, dx, dy, dz);
            case SPIKE -> spike(p, h, dx, dy, dz);
            case SPRUCE -> spruce(style, h, dx, dy, dz);
            case JUNGLE_TREE -> jungleTree(style, h, dx, dy, dz);
            case DEAD_TREE -> deadTree(style, h, dx, dy, dz);
        };
    }

    private static Material weathered(Style.Palette p, long h, int dx, int dy, int dz, Material base) {
        double v = Noise.unit(h, dx, dy * 31, dz);
        if (v < 0.18) {
            return p.alt();
        }
        if (v < 0.30) {
            return p.cracked();
        }
        return base;
    }

    // ------------------------------------------------------------------ pyramid

    private static Material pyramid(Style.Palette p, long h, int dx, int dy, int dz) {
        if (dy == 8) {
            return dx == 0 && dz == 0 ? p.accent() : null;
        }
        int half = 13 - 2 * (dy - 1);
        if (half < 1 || Math.abs(dx) > half || Math.abs(dz) > half) {
            return null;
        }
        // the way in, from the south, and the chamber it leads to
        if (Math.abs(dx) <= 1 && dz >= 4 && dy <= 3) {
            return null;
        }
        if (Math.abs(dx) <= 4 && Math.abs(dz) <= 4 && dy <= 5) {
            return null;
        }
        boolean edge = Math.abs(dx) == half || Math.abs(dz) == half;
        if (edge && dy % 3 == 0) {
            return p.pillar();
        }
        return edge ? weathered(p, h, dx, dy, dz, p.wall()) : p.alt();
    }

    // ------------------------------------------------------------------ obelisk

    private static Material obelisk(Style.Palette p, int dx, int dy, int dz) {
        if (dy <= 2) {
            return Math.abs(dx) <= 1 && Math.abs(dz) <= 1 ? p.wall() : null;
        }
        if (dx != 0 || dz != 0) {
            return null;
        }
        if (dy <= 14) {
            return dy % 4 == 0 ? p.pillar() : p.wall();
        }
        return dy == 15 ? p.glow() : null;
    }

    // ------------------------------------------------------------------ standing stones

    private static final int[][] STONES = {{6, 0}, {0, 6}, {-6, 0}, {0, -6}, {4, 4}, {4, -4}, {-4, 4}, {-4, -4}};

    private static Material columnRing(Style.Palette p, long h, int dx, int dy, int dz) {
        double d2 = dx * dx + dz * dz;
        if (dy == 1) {
            if (d2 > 8.5 * 8.5) {
                return null;
            }
            return Noise.unit(h, dx, 77, dz) < 0.2 ? p.alt() : p.floor();
        }
        if (dx == 0 && dz == 0 && dy == 2) {
            return p.accent();
        }
        for (int[] s : STONES) {
            if (s[0] == dx && s[1] == dz) {
                int top = 1 + 3 + (int) (Noise.unit(h, s[0], 5, s[1]) * 4);
                return dy <= top ? (dy == top ? p.pillar() : p.wall()) : null;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ dome (an outpost)

    private static Material dome(Style.Palette p, long h, int dx, int dy, int dz) {
        int flat2 = dx * dx + dz * dz;
        if (dy == 1) {
            if (flat2 > 64) {
                return null;
            }
            boolean lamp = Math.abs(dx) == 4 && Math.abs(dz) == 4;
            return lamp ? p.glow() : p.floor();
        }
        if (dx == 0 && dz == -3 && dy == 2) {
            return p.accent();
        }
        int r2 = flat2 + (dy - 1) * (dy - 1);
        if (r2 > 64 || r2 < 49) {
            return null;
        }
        if (dz > 0 && Math.abs(dx) <= 1 && dy <= 4) {
            return null;
        }
        return Noise.unit(h, dx, dy, dz) < 0.07 ? p.glow() : p.wall();
    }

    // ------------------------------------------------------------------ fortress

    private static Material fortress(Style.Palette p, long h, int dx, int dy, int dz) {
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (ax >= 7 && az >= 7 && ax <= 9 && az <= 9) {
            if (dy <= 10) {
                return weathered(p, h, dx, dy, dz, p.wall());
            }
            return dy == 11 && ax == 8 && az == 8 ? p.glow() : null;
        }
        if (ax > 8 || az > 8) {
            return null;
        }
        boolean wall = ax == 8 || az == 8;
        if (wall) {
            if (dz == 8 && ax <= 1 && dy <= 4) {
                return null;
            }
            if (dy <= 6) {
                return weathered(p, h, dx, dy, dz, p.wall());
            }
            return dy == 7 && (dx + dz) % 2 == 0 ? p.wall() : null;
        }
        if (dy == 1) {
            return Noise.unit(h, dx, 9, dz) < 0.7 ? p.floor() : null;
        }
        return null;
    }

    // ------------------------------------------------------------------ broken wall

    private static Material ruinWall(Style.Palette p, long h, int dx, int dy, int dz) {
        boolean a = dz == 0 && dx >= -6 && dx <= 6;
        boolean b = dx == -6 && dz >= -6 && dz <= 0;
        if (!a && !b) {
            return null;
        }
        if (Noise.unit(h, dx, 9, dz) < 0.2) {
            return null;
        }
        int top = 1 + (int) (Noise.unit(h, dx, 0, dz) * 5);
        if (dy > top) {
            return null;
        }
        return weathered(p, h, dx, dy, dz, p.wall());
    }

    // ------------------------------------------------------------------ spike

    private static Material spike(Style.Palette p, long h, int dx, int dy, int dz) {
        int tall = 5 + (int) (Noise.unit(h, 1, 2, 3) * 7);
        if (dy > tall) {
            return null;
        }
        double r = 2.0 * (1.0 - (dy - 1.0) / tall);
        double d2 = dx * dx + dz * dz;
        return d2 <= Math.max(0.0, r) * Math.max(0.0, r) + 0.01 ? p.wall() : null;
    }

    // ------------------------------------------------------------------ trees

    private static Material spruce(Style style, long h, int dx, int dy, int dz) {
        int tall = 7 + (int) (Noise.unit(h, 3, 4, 5) * 5);
        if (dx == 0 && dz == 0 && dy <= tall) {
            return style.log;
        }
        if (dy < 3 || dy > tall + 1) {
            return null;
        }
        int r = Math.min(3, (tall + 2 - dy) / 2);
        if (dy == tall + 1) {
            r = 0;
        }
        return dx * dx + dz * dz <= r * r + (r > 1 ? 1 : 0) ? style.leaves : null;
    }

    private static Material jungleTree(Style style, long h, int dx, int dy, int dz) {
        int tall = 10 + (int) (Noise.unit(h, 6, 7, 8) * 6);
        if (dx == 0 && dz == 0 && dy <= tall) {
            return style.log;
        }
        double cy = tall - 1;
        double e = (dx * dx + dz * dz) / 9.0 + ((dy - cy) * (dy - cy)) / 4.0;
        return e <= 1.0 ? style.leaves : null;
    }

    private static Material deadTree(Style style, long h, int dx, int dy, int dz) {
        int tall = 3 + (int) (Noise.unit(h, 9, 1, 2) * 4);
        if (dx == 0 && dz == 0 && dy <= tall) {
            return style.log;
        }
        int dir = (int) (Noise.unit(h, 4, 4, 4) * 4);
        int bx = dir == 0 ? 1 : dir == 1 ? -1 : 0;
        int bz = dir == 2 ? 1 : dir == 3 ? -1 : 0;
        if (dy == tall - 1 && dx == bx && dz == bz) {
            return style.log;
        }
        if (dy == tall && dx == bx && dz == bz) {
            return style.log;
        }
        return null;
    }
}
