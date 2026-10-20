package com.nexuscraft.nexusstargate;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Orientable;

/** Puts gates into a real world and reads them back. Nothing here decides anything; it only places and looks. */
final class GateBuilder {

    private final GateMaterials mats;

    GateBuilder(GateMaterials mats) {
        this.mats = mats;
    }

    /** Reads blocks from a world; chunks that are not loaded read as air so a look never loads or generates them. */
    static GateShape.Blocks reader(World world) {
        return (x, y, z) -> {
            if (!world.isChunkLoaded(x >> 4, z >> 4)) {
                return Material.AIR;
            }
            return world.getBlockAt(x, y, z).getType();
        };
    }

    GateShape.Status check(World world, Gate g) {
        return GateShape.check(reader(world), g.ax, g.ay, g.az, g.facing, mats);
    }

    /** Builds a complete gate: ring, chevrons, an empty opening. */
    void buildRing(World world, int ax, int ay, int az, BlockFace facing) {
        for (GateShape.Cell c : GateShape.RING) {
            int[] p = GateShape.pos(ax, ay, az, facing, c);
            Material m = GateShape.CHEVRONS.contains(c) ? mats.chevron : mats.ring;
            world.getBlockAt(p[0], p[1], p[2]).setType(m, false);
        }
        fill(world, ax, ay, az, facing, Material.AIR);
    }

    /** Fills the opening with one material: air (idle), the horizon (open) or the iris. */
    void fill(World world, int ax, int ay, int az, BlockFace facing, Material m) {
        BlockData data = null;
        if (m == Material.NETHER_PORTAL) {
            data = org.bukkit.Bukkit.createBlockData(Material.NETHER_PORTAL);
            if (data instanceof Orientable o) {
                o.setAxis(GateShape.alongX(facing) ? org.bukkit.Axis.X : org.bukkit.Axis.Z);
            }
        }
        for (int[] p : GateShape.insideWorld(ax, ay, az, facing)) {
            Block b = world.getBlockAt(p[0], p[1], p[2]);
            if (data != null) {
                b.setBlockData(data, false);
            } else {
                b.setType(m, false);
            }
        }
    }

    void fill(World world, Gate g, Material m) {
        fill(world, g.ax, g.ay, g.az, g.facing, m);
    }

    /** Lights the first n of the seven dialing chevrons, in {@link GateShape#LOCK_ORDER}; the other two stay dark until open. */
    void light(World world, Gate g, int locked, boolean allNine) {
        for (int i = 0; i < GateShape.CHEVRONS.size(); i++) {
            boolean on = allNine;
            if (!on) {
                for (int k = 0; k < Math.min(locked, GateShape.LOCK_ORDER.length); k++) {
                    if (GateShape.LOCK_ORDER[k] == i) {
                        on = true;
                    }
                }
            }
            int[] p = GateShape.pos(g.ax, g.ay, g.az, g.facing, GateShape.CHEVRONS.get(i));
            Block b = world.getBlockAt(p[0], p[1], p[2]);
            Material now = b.getType();
            if (mats.isChevron(now)) {
                Material want = on ? mats.lit : mats.chevron;
                if (now != want) {
                    b.setType(want, false);
                }
            }
        }
    }

    /** All chevrons dark again. */
    void dark(World world, Gate g) {
        light(world, g, 0, false);
    }

    /** The arrival platform of a planet gate: a disc in front of and around the gate, the gate itself, a dialing device. */
    void platform(World world, int cx, int groundY, int cz, int radius, Material floor, Material rim) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                double d = Math.sqrt((double) x * x + (double) z * z);
                if (d > radius + 0.4) {
                    continue;
                }
                Material m = d > radius - 1.0 ? rim : floor;
                world.getBlockAt(cx + x, groundY, cz + z).setType(m, false);
            }
        }
    }

    /** Clears a box above a platform so nothing grows into the gate's surroundings. */
    void clearAbove(World world, int cx, int fromY, int cz, int radius, int height) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z > (radius + 0.4) * (radius + 0.4)) {
                    continue;
                }
                for (int y = fromY; y < fromY + height; y++) {
                    Block b = world.getBlockAt(cx + x, y, cz + z);
                    if (!b.getType().isAir()) {
                        b.setType(Material.AIR, false);
                    }
                }
            }
        }
    }
}
