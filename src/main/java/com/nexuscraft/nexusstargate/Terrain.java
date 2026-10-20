package com.nexuscraft.nexusstargate;

import org.bukkit.Material;
import org.bukkit.generator.ChunkGenerator.ChunkData;

import java.util.ArrayList;
import java.util.List;

/**
 * The ground of one gate world: height, surface blocks, and the structures and trees on it. All of it is a pure
 * function of the style, the seed and the coordinates, so the same chunk always generates the same.
 */
final class Terrain {

    /** Flat ground around the arrival gate. */
    static final int PLATEAU = 12;
    static final int PLATEAU_BLEND = 14;
    /** Nothing big stands this close to the arrival gate. */
    static final int EXCLUDE = 48;
    static final int LEVEL_BLEND = 6;

    /** A placed thing: its kind, centre, the ground level it stands on, and a hash for its small variations. */
    record Instance(Style.Feature feature, int x, int z, int baseY, long hash) {
        Style.Kind kind() {
            return feature.kind();
        }
    }

    private final Style style;
    private final long seed;

    Terrain(Style style, long seed) {
        this.style = style;
        this.seed = seed;
    }

    Style style() {
        return style;
    }

    // ------------------------------------------------------------------ height

    /** The ground height with no levelling anywhere (hills, dunes only). */
    int natural(int x, int z) {
        double n = Noise.fbm(seed, x / style.scale, z / style.scale, style.octaves);
        if (style.ridged) {
            n = 1.0 - 2.0 * Math.abs(n);
        }
        double h = style.baseY + n * style.amplitude;
        if (style.duneAmp > 0) {
            double wave = Math.sin((x * 0.6 + z * 0.8) / style.duneWave * Math.PI * 2 + n * 3.0);
            h += style.duneAmp * (0.5 + 0.5 * wave);
        }
        return (int) Math.round(h);
    }

    /** The ground level the arrival gate stands on. */
    int originGround() {
        int g = natural(0, 0);
        return style.liquid == null ? g : Math.max(g, style.liquidLevel + 2);
    }

    /** The finished ground height: hills, the flat arrival area, and levelled spots under structures. */
    int height(int x, int z) {
        double h = natural(x, z);
        double d = Math.sqrt((double) x * x + (double) z * z);
        if (d < PLATEAU + PLATEAU_BLEND) {
            double t = d <= PLATEAU ? 1.0 : 1.0 - (d - PLATEAU) / PLATEAU_BLEND;
            h = h + (originGround() - h) * smooth(t);
        }
        Instance flat = flattener(x, z);
        if (flat != null) {
            double fd = Math.sqrt((double) (x - flat.x()) * (x - flat.x()) + (double) (z - flat.z()) * (z - flat.z()));
            int r = flat.kind().radius;
            double t = fd <= r ? 1.0 : 1.0 - (fd - r) / LEVEL_BLEND;
            h = h + (flat.baseY() - h) * smooth(Math.max(0.0, t));
        }
        return (int) Math.round(h);
    }

    private static double smooth(double t) {
        double c = Math.max(0.0, Math.min(1.0, t));
        return c * c * (3.0 - 2.0 * c);
    }

    /** The structure whose levelled area (plus its blend) covers this column, if any. */
    private Instance flattener(int x, int z) {
        for (Style.Feature f : style.features) {
            if (!f.kind().flattens) {
                continue;
            }
            int cellX = Math.floorDiv(x, f.grid());
            int cellZ = Math.floorDiv(z, f.grid());
            // the structure sits inside its own square (by margin), so only this square can reach this column
            Instance in = instance(f, cellX, cellZ);
            if (in != null) {
                int reach = f.kind().radius + LEVEL_BLEND;
                int ddx = x - in.x();
                int ddz = z - in.z();
                if (ddx * ddx + ddz * ddz <= reach * reach) {
                    return in;
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ placing things

    private static final Instance NONE = new Instance(null, 0, 0, 0, 0);
    private static final int MEMO_LIMIT = 40_000;
    private final java.util.concurrent.ConcurrentHashMap<String, Instance> memo = new java.util.concurrent.ConcurrentHashMap<>();

    /** The thing in one grid square for one feature, or null when there is none (or it would be in the way). */
    Instance instance(Style.Feature f, int cellX, int cellZ) {
        String key = f.salt() + ":" + cellX + ":" + cellZ;
        Instance cached = memo.get(key);
        if (cached != null) {
            return cached == NONE ? null : cached;
        }
        Instance made = makeInstance(f, cellX, cellZ);
        if (memo.size() > MEMO_LIMIT) {
            memo.clear();
        }
        memo.put(key, made == null ? NONE : made);
        return made;
    }

    private Instance makeInstance(Style.Feature f, int cellX, int cellZ) {
        long h = Noise.hash(seed, cellX, cellZ, f.salt());
        if (Noise.unit(h) >= f.chance()) {
            return null;
        }
        int margin = f.kind().flattens ? f.kind().radius + LEVEL_BLEND : 1;
        int room = f.grid() - 2 * margin;
        if (room < 1) {
            return null;
        }
        int x = cellX * f.grid() + margin + (int) (Noise.unit(h, 1, 0, 0) * room);
        int z = cellZ * f.grid() + margin + (int) (Noise.unit(h, 2, 0, 0) * room);
        double fromOrigin = Math.sqrt((double) x * x + (double) z * z);
        if (fromOrigin < (f.kind().flattens ? EXCLUDE : PLATEAU + PLATEAU_BLEND + 2)) {
            return null;
        }
        int base = natural(x, z);
        if (f.kind().flattens) {
            // a levelled structure must not stand in water or lava, and must not crowd a stronger one
            if (style.liquid != null && base <= style.liquidLevel + 1) {
                return null;
            }
            for (Style.Feature other : style.features) {
                if (other == f) {
                    break;
                }
                if (other.kind().flattens && crowds(f, x, z, other)) {
                    return null;
                }
            }
        }
        return new Instance(f, x, z, base, Noise.hash(h, x, z, 99));
    }

    /** True if an earlier (stronger) feature has a thing within touching distance of this spot. */
    private boolean crowds(Style.Feature f, int x, int z, Style.Feature other) {
        int reach = f.kind().radius + other.kind().radius + 2 * LEVEL_BLEND;
        int g = other.grid();
        int x0 = Math.floorDiv(x - reach, g);
        int x1 = Math.floorDiv(x + reach, g);
        int z0 = Math.floorDiv(z - reach, g);
        int z1 = Math.floorDiv(z + reach, g);
        for (int cx = x0; cx <= x1; cx++) {
            for (int cz = z0; cz <= z1; cz++) {
                Instance o = instance(other, cx, cz);
                if (o != null) {
                    long dx = o.x() - x;
                    long dz = o.z() - z;
                    if (dx * dx + dz * dz < (long) reach * reach) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** Everything that reaches into the box (inclusive), in a fixed order. */
    List<Instance> instancesIn(int minX, int minZ, int maxX, int maxZ) {
        List<Instance> out = new ArrayList<>();
        for (Style.Feature f : style.features) {
            int reach = f.kind().radius;
            int g = f.grid();
            for (int cx = Math.floorDiv(minX - reach, g); cx <= Math.floorDiv(maxX + reach, g); cx++) {
                for (int cz = Math.floorDiv(minZ - reach, g); cz <= Math.floorDiv(maxZ + reach, g); cz++) {
                    Instance in = instance(f, cx, cz);
                    if (in == null) {
                        continue;
                    }
                    if (in.x() + reach < minX || in.x() - reach > maxX || in.z() + reach < minZ || in.z() - reach > maxZ) {
                        continue;
                    }
                    if (!f.kind().flattens && !treeStands(in)) {
                        continue;
                    }
                    out.add(in);
                }
            }
        }
        return out;
    }

    /** A tree or spike stands only on dry ground clear of structures. */
    private boolean treeStands(Instance in) {
        int ground = height(in.x(), in.z());
        if (style.liquid != null && ground <= style.liquidLevel + 1) {
            return false;
        }
        return flattener(in.x(), in.z()) == null;
    }

    // ------------------------------------------------------------------ surface

    /** The block on top of a column of this height. */
    Material surfaceAt(int x, int z, int ground) {
        if (style.liquid != null && ground <= style.liquidLevel + 1) {
            return style.shore;
        }
        if (style.patch != null && Noise.value(seed + 77, x / 17.0, z / 17.0) > style.patchThreshold) {
            return style.patch;
        }
        return style.surface;
    }

    // ------------------------------------------------------------------ filling a chunk

    /** Fills one chunk: stone, soil, surface, liquid, then every structure and tree that reaches into it. */
    void fill(ChunkData data, int chunkX, int chunkZ) {
        int minY = data.getMinHeight();
        int maxY = data.getMaxHeight();
        int baseX = chunkX * 16;
        int baseZ = chunkZ * 16;
        int top = Math.min(maxY - 1, 319);
        for (int lx = 0; lx < 16; lx++) {
            for (int lz = 0; lz < 16; lz++) {
                int x = baseX + lx;
                int z = baseZ + lz;
                int h = Math.max(minY + 4, Math.min(top - 24, height(x, z)));
                data.setBlock(lx, minY, lz, Material.BEDROCK);
                data.setRegion(lx, minY + 1, lz, lx + 1, h - 3, lz + 1, style.deep);
                data.setRegion(lx, h - 3, lz, lx + 1, h, lz + 1, style.subsurface);
                data.setBlock(lx, h, lz, surfaceAt(x, z, h));
                if (style.liquid != null && h < style.liquidLevel) {
                    data.setRegion(lx, h + 1, lz, lx + 1, Math.min(top, style.liquidLevel) + 1, lz + 1, style.liquid);
                }
            }
        }
        for (Instance in : instancesIn(baseX, baseZ, baseX + 15, baseZ + 15)) {
            draw(data, in, baseX, baseZ, minY, maxY);
        }
    }

    private void draw(ChunkData data, Instance in, int baseX, int baseZ, int minY, int maxY) {
        Style.Kind kind = in.kind();
        int ground = kind.flattens ? in.baseY() : height(in.x(), in.z());
        int r = kind.radius;
        int tall = Structures.height(kind);
        int x0 = Math.max(baseX, in.x() - r);
        int x1 = Math.min(baseX + 15, in.x() + r);
        int z0 = Math.max(baseZ, in.z() - r);
        int z1 = Math.min(baseZ + 15, in.z() + r);
        for (int x = x0; x <= x1; x++) {
            for (int z = z0; z <= z1; z++) {
                for (int dy = 1; dy <= tall; dy++) {
                    int y = ground + dy;
                    if (y >= maxY || y < minY) {
                        continue;
                    }
                    Material m = Structures.blockAt(style, kind, in.hash(), x - in.x(), dy, z - in.z());
                    if (m != null) {
                        data.setBlock(x - baseX, y, z - baseZ, m);
                    }
                }
            }
        }
    }
}
