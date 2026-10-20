package com.nexuscraft.nexusstargate;

import org.bukkit.Material;
import org.bukkit.block.BlockFace;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The shape of a gate: a 7 wide, 9 tall octagonal ring with a 31 block opening and nine chevron blocks.
 * Cells are (u, v): u across the ring from -3 to 3, v up from 0 at the bottom of the ring. {@link #blueprint()}
 * draws it.
 */
final class GateShape {

    static final int WIDTH = 7;
    static final int HEIGHT = 9;

    /** A cell of the template. */
    record Cell(int u, int v) {
    }

    /** All 20 ring cells, chevrons included. */
    static final List<Cell> RING = new ArrayList<>();
    /** The nine chevron cells (a subset of {@link #RING}). */
    static final List<Cell> CHEVRONS = new ArrayList<>();
    /** The 31 cells of the opening. */
    static final List<Cell> INSIDE = new ArrayList<>();
    /** The order the seven dialing chevrons light up in: indexes into {@link #CHEVRONS}. */
    static final int[] LOCK_ORDER;

    static {
        for (int u = -1; u <= 1; u++) {
            RING.add(new Cell(u, 8));
            RING.add(new Cell(u, 0));
        }
        for (int v = 2; v <= 6; v++) {
            RING.add(new Cell(-3, v));
            RING.add(new Cell(3, v));
        }
        RING.add(new Cell(-2, 7));
        RING.add(new Cell(2, 7));
        RING.add(new Cell(-2, 1));
        RING.add(new Cell(2, 1));

        // 0 top, 1-2 upper corners, 3-4 upper sides, 5-6 lower sides, 7-8 lower corners
        CHEVRONS.add(new Cell(0, 8));
        CHEVRONS.add(new Cell(-2, 7));
        CHEVRONS.add(new Cell(2, 7));
        CHEVRONS.add(new Cell(-3, 5));
        CHEVRONS.add(new Cell(3, 5));
        CHEVRONS.add(new Cell(-3, 3));
        CHEVRONS.add(new Cell(3, 3));
        CHEVRONS.add(new Cell(-2, 1));
        CHEVRONS.add(new Cell(2, 1));

        for (int u = -1; u <= 1; u++) {
            INSIDE.add(new Cell(u, 7));
            INSIDE.add(new Cell(u, 1));
        }
        for (int v = 2; v <= 6; v++) {
            for (int u = -2; u <= 2; u++) {
                INSIDE.add(new Cell(u, v));
            }
        }
        // left side down, right side down, then the top locks
        LOCK_ORDER = new int[]{1, 3, 5, 2, 4, 6, 0};
    }

    private GateShape() {
    }

    /** Where a cell is in the world: facing north/south is a gate lying along X, east/west along Z. */
    static int[] pos(int ax, int ay, int az, BlockFace facing, Cell c) {
        return alongX(facing) ? new int[]{ax + c.u(), ay + c.v(), az} : new int[]{ax, ay + c.v(), az + c.u()};
    }

    static boolean alongX(BlockFace facing) {
        return facing == BlockFace.NORTH || facing == BlockFace.SOUTH;
    }

    static boolean horizontal(BlockFace f) {
        return f == BlockFace.NORTH || f == BlockFace.SOUTH || f == BlockFace.EAST || f == BlockFace.WEST;
    }

    /** Reads blocks, so the shape can be checked against a real world or a test one. */
    interface Blocks {
        Material at(int x, int y, int z);
    }

    enum Status {
        VALID, RING_BROKEN, BLOCKED
    }

    /** Checks a gate at this anchor (the bottom middle ring block). */
    static Status check(Blocks b, int ax, int ay, int az, BlockFace facing, GateMaterials m) {
        for (Cell c : RING) {
            int[] p = pos(ax, ay, az, facing, c);
            Material at = b.at(p[0], p[1], p[2]);
            boolean chevronCell = CHEVRONS.contains(c);
            boolean good = chevronCell ? m.isChevron(at) : at == m.ring;
            if (!good) {
                return Status.RING_BROKEN;
            }
        }
        for (Cell c : INSIDE) {
            int[] p = pos(ax, ay, az, facing, c);
            if (!m.clear(b.at(p[0], p[1], p[2]))) {
                return Status.BLOCKED;
            }
        }
        return Status.VALID;
    }

    /** A gate found near a spot. The facing is one of the two possible fronts; the caller picks the side. */
    record Found(int ax, int ay, int az, BlockFace facing, Status status) {
    }

    /**
     * Looks for gates around a spot: every chevron block within the radius is tried as each of the nine chevron
     * cells, on each of the two orientations. Valid gates come first; at most a few partial ones follow so the
     * caller can say what is wrong.
     */
    static List<Found> find(Blocks b, int cx, int cy, int cz, int radius, GateMaterials m) {
        Set<String> seen = new LinkedHashSet<>();
        List<Found> valid = new ArrayList<>();
        List<Found> partial = new ArrayList<>();
        for (int x = cx - radius; x <= cx + radius; x++) {
            for (int y = cy - radius; y <= cy + radius; y++) {
                for (int z = cz - radius; z <= cz + radius; z++) {
                    if (!m.isChevron(b.at(x, y, z))) {
                        continue;
                    }
                    for (BlockFace f : new BlockFace[]{BlockFace.SOUTH, BlockFace.EAST}) {
                        for (Cell c : CHEVRONS) {
                            int ax = alongX(f) ? x - c.u() : x;
                            int az = alongX(f) ? z : z - c.u();
                            int ay = y - c.v();
                            if (!seen.add(ax + "," + ay + "," + az + "," + alongX(f))) {
                                continue;
                            }
                            Status s = check(b, ax, ay, az, f, m);
                            if (s == Status.VALID) {
                                valid.add(new Found(ax, ay, az, f, s));
                            } else if (s == Status.BLOCKED) {
                                partial.add(new Found(ax, ay, az, f, s));
                            }
                        }
                    }
                }
            }
        }
        List<Found> out = new ArrayList<>(valid);
        out.addAll(partial.subList(0, Math.min(2, partial.size())));
        return out;
    }

    /** The side of a gate (along its normal) a spot is on: the facing that points at it, default the given one. */
    static BlockFace sideOf(int ax, int az, BlockFace axisFacing, int x, int z) {
        if (alongX(axisFacing)) {
            if (z > az) {
                return BlockFace.SOUTH;
            }
            if (z < az) {
                return BlockFace.NORTH;
            }
            return BlockFace.SOUTH;
        }
        if (x > ax) {
            return BlockFace.EAST;
        }
        if (x < ax) {
            return BlockFace.WEST;
        }
        return BlockFace.EAST;
    }

    /** The text picture of a gate for chat: R ring, C chevron, . opening. */
    static List<String> blueprint() {
        List<String> lines = new ArrayList<>();
        for (int v = HEIGHT - 1; v >= 0; v--) {
            StringBuilder sb = new StringBuilder();
            for (int u = -3; u <= 3; u++) {
                Cell c = new Cell(u, v);
                if (CHEVRONS.contains(c)) {
                    sb.append("C ");
                } else if (RING.contains(c)) {
                    sb.append("R ");
                } else if (INSIDE.contains(c)) {
                    sb.append(". ");
                } else {
                    sb.append("  ");
                }
            }
            lines.add(sb.toString().stripTrailing());
        }
        return lines;
    }

    /** The cells the opening fills when open. */
    static List<int[]> insideWorld(int ax, int ay, int az, BlockFace facing) {
        List<int[]> out = new ArrayList<>(INSIDE.size());
        for (Cell c : INSIDE) {
            out.add(pos(ax, ay, az, facing, c));
        }
        return out;
    }
}
