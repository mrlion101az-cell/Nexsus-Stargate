package com.nexuscraft.nexusstargate;

import org.bukkit.Material;
import org.bukkit.block.Biome;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.generator.ChunkGenerator;

import java.io.File;
import java.io.StringReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.nexuscraft.nexusstargate.Kit.expect;

/** The parts that need no server: glyphs, addresses, gate shape, store, config, planets, terrain, structures. */
public class LogicTest {

    static final UUID A = UUID.fromString("00000000-0000-0000-0000-00000000000a");
    static final UUID B = UUID.fromString("00000000-0000-0000-0000-00000000000b");

    public static void main(String[] args) throws Exception {
        colorText();
        glyphs();
        addresses();
        noise();
        shape();
        store();
        storeFiles();
        config();
        shippedConfig();
        planetBook();
        shippedPlanets();
        styles();
        terrain();
        terrainInvariants();
        terrainColumns();
        structuresOnGround();
        generator();
        menuWrap();
        expect(FakeChunk.outside == 0, "chunks: nothing was ever written outside its chunk: " + FakeChunk.outside);
        Kit.finish();
    }

    static void colorText() {
        expect(ColorText.translate("&6Gold &lbold &Zno").equals("\u00a76Gold \u00a7lbold &Zno") && ColorText.translate(null).isEmpty() && ColorText.translate("plain").equals("plain"),
                "colour: & codes become section signs; other & stay");
        expect(ColorText.translate("&AUpper").equals("\u00a7AUpper"), "colour: upper-case codes too");
        expect(ColorText.strip("&6Gold \u00a7lbold &Zno").equals("Gold bold &Zno") && ColorText.strip(null).isEmpty() && ColorText.strip("plain").equals("plain"),
                "colour: strip takes out both forms of colour code");
    }

    // ------------------------------------------------------------------ glyphs and addresses

    static void glyphs() {
        expect(Glyphs.COUNT == 36 && Glyphs.LENGTH == 6, "glyphs: 36 glyphs, 6 to an address");
        expect(Glyphs.NAMES.size() == 36 && new HashSet<>(Glyphs.NAMES).size() == 36, "glyphs: 36 distinct names");
        expect(Glyphs.ICONS.size() == 36 && new HashSet<>(Glyphs.ICONS).size() == 36, "glyphs: 36 distinct icons");
        for (int i = 0; i < Glyphs.COUNT; i++) {
            expect(Glyphs.indexOf(Glyphs.name(i)) == i, "glyphs: name -> index round trip " + i);
            expect(Glyphs.indexOf(Glyphs.name(i).toUpperCase()) == i, "glyphs: lookup ignores case " + i);
            expect(Glyphs.icon(i) != null && !Glyphs.icon(i).isAir(), "glyphs: icon is an item " + i);
        }
        expect(Glyphs.indexOf("nonsense") == -1 && Glyphs.indexOf(null) == -1, "glyphs: unknown name is -1");
    }

    static void addresses() {
        GateAddress a = GateAddress.of(3, 17, 22, 0, 5, 9);
        expect(a != null && a.code().equals("3.17.22.0.5.9"), "address: code");
        expect(a.names().equals(Glyphs.name(3) + "-" + Glyphs.name(17) + "-" + Glyphs.name(22) + "-" + Glyphs.name(0) + "-" + Glyphs.name(5) + "-" + Glyphs.name(9)),
                "address: names joined with dashes: " + a.names());
        expect(a.glyph(0) == 3 && a.glyph(5) == 9, "address: glyph(i)");
        expect(GateAddress.of(1, 2, 3, 4, 5) == null, "address: five glyphs is not an address");
        expect(GateAddress.of(1, 2, 3, 4, 5, 6, 7) == null, "address: seven glyphs is not an address");
        expect(GateAddress.of(1, 1, 3, 4, 5, 6) == null, "address: repeated glyph refused");
        expect(GateAddress.of(1, 2, 3, 4, 5, 36) == null && GateAddress.of(-1, 2, 3, 4, 5, 6) == null, "address: glyph out of range refused");
        expect(GateAddress.parse("3.17.22.0.5.9").equals(a), "address: parse code");
        expect(GateAddress.parse("3 17 22 0 5 9").equals(a) && GateAddress.parse("3,17,22,0,5,9").equals(a), "address: parse spaces and commas");
        expect(GateAddress.parse(a.names()).equals(a), "address: parse names");
        expect(GateAddress.parse(a.names().toLowerCase().replace('-', ' ')).equals(a), "address: parse lower-case names with spaces");
        expect(GateAddress.parse("3.17.22.0.5") == null && GateAddress.parse("") == null && GateAddress.parse(null) == null, "address: parse rejects short and empty");
        expect(GateAddress.parse("3.17.22.0.5.x") == null && GateAddress.parse("a b c d e f") == null, "address: parse rejects nonsense");
        expect(a.equals(GateAddress.of(3, 17, 22, 0, 5, 9)) && a.hashCode() == GateAddress.of(3, 17, 22, 0, 5, 9).hashCode()
                && !a.equals(GateAddress.of(9, 5, 0, 22, 17, 3)) && !a.equals(null) && !a.equals("x"), "address: equality is by glyph and order");
        expect(GateAddress.parse("003.017.022.000.005.009").equals(a), "address: leading zeros are fine (up to three digits)");
        expect(GateAddress.parse("0003.017.022.000.005.009") == null, "address: four digits is not a glyph number");
        int[] tries = {0};
        expect(GateAddress.random(new Random(1), x -> {
            tries[0]++;
            return true;
        }) == null && tries[0] == 200, "address: gives up after exactly 200 tries: " + tries[0]);
        Random r1 = new Random(5);
        Random r2 = new Random(5);
        expect(GateAddress.random(r1, x -> false).equals(GateAddress.random(r2, x -> false)), "address: random is repeatable for one seed");
        Set<GateAddress> seen = new HashSet<>();
        Random rr = new Random(1);
        for (int i = 0; i < 300; i++) {
            GateAddress g = GateAddress.random(rr, seen::contains);
            expect(GateAddress.of(g.glyph(0), g.glyph(1), g.glyph(2), g.glyph(3), g.glyph(4), g.glyph(5)) != null, "address: random is valid");
            expect(seen.add(g), "address: random avoids taken ones");
        }
    }

    static void noise() {
        expect(Noise.hash(1, 2, 3, 4) == Noise.hash(1, 2, 3, 4), "noise: hash repeatable");
        expect(Noise.hash(1, 2, 3, 4) != Noise.hash(2, 2, 3, 4) && Noise.hash(1, 2, 3, 4) != Noise.hash(1, 3, 3, 4)
                && Noise.hash(1, 2, 3, 4) != Noise.hash(1, 2, 4, 4) && Noise.hash(1, 2, 3, 4) != Noise.hash(1, 2, 3, 5), "noise: every input matters");
        double lo = 1;
        double hi = 0;
        for (int i = 0; i < 2000; i++) {
            double u = Noise.unit(Noise.hash(9, i, i * 3, 7));
            lo = Math.min(lo, u);
            hi = Math.max(hi, u);
        }
        expect(lo >= 0 && hi < 1 && lo < 0.05 && hi > 0.95, "noise: unit is in [0,1) and spread: " + lo + " " + hi);
        double vmin = 9;
        double vmax = -9;
        for (int i = 0; i < 3000; i++) {
            double v = Noise.fbm(3, i * 0.37, i * 0.11, 4);
            vmin = Math.min(vmin, v);
            vmax = Math.max(vmax, v);
        }
        expect(vmin >= -1.0001 && vmax <= 1.0001 && vmax - vmin > 0.5, "noise: fbm in [-1,1] and moves: " + vmin + " " + vmax);
        expect(Noise.fbm(3, 5.5, 6.5, 4) == Noise.fbm(3, 5.5, 6.5, 4) && Noise.fbm(3, 5.5, 6.5, 4) != Noise.fbm(4, 5.5, 6.5, 4), "noise: fbm repeatable and seeded");
        double diff = Math.abs(Noise.fbm(3, 10.0, 10.0, 4) - Noise.fbm(3, 10.01, 10.0, 4));
        expect(diff < 0.05, "noise: fbm is smooth: " + diff);
    }

    // ------------------------------------------------------------------ shape

    /** A map of blocks for shape tests. */
    static final class Grid implements GateShape.Blocks {
        final Map<String, Material> m = new HashMap<>();

        @Override
        public Material at(int x, int y, int z) {
            return m.getOrDefault(x + "," + y + "," + z, Material.AIR);
        }

        void set(int x, int y, int z, Material mat) {
            m.put(x + "," + y + "," + z, mat);
        }

        void gate(int ax, int ay, int az, BlockFace f, GateMaterials mats) {
            for (GateShape.Cell c : GateShape.RING) {
                int[] p = GateShape.pos(ax, ay, az, f, c);
                set(p[0], p[1], p[2], GateShape.CHEVRONS.contains(c) ? mats.chevron : mats.ring);
            }
        }
    }

    static void shape() {
        GateMaterials mats = new GateMaterials();
        expect(GateShape.RING.size() == 20, "shape: 20 ring cells: " + GateShape.RING.size());
        expect(GateShape.CHEVRONS.size() == 9 && GateShape.RING.containsAll(GateShape.CHEVRONS), "shape: nine chevrons, all on the ring");
        expect(GateShape.INSIDE.size() == 31, "shape: 31 cells inside: " + GateShape.INSIDE.size());
        for (GateShape.Cell c : GateShape.INSIDE) {
            expect(!GateShape.RING.contains(c), "shape: inside cell not on the ring " + c);
        }
        Set<GateShape.Cell> all = new HashSet<>(GateShape.RING);
        all.addAll(GateShape.INSIDE);
        expect(all.size() == 51, "shape: ring and inside do not overlap");
        for (GateShape.Cell c : GateShape.RING) {
            expect(GateShape.RING.contains(new GateShape.Cell(-c.u(), c.v())), "shape: ring is mirror-symmetric " + c);
        }
        for (GateShape.Cell c : GateShape.INSIDE) {
            expect(GateShape.INSIDE.contains(new GateShape.Cell(-c.u(), c.v())), "shape: inside is mirror-symmetric " + c);
            expect(c.v() >= 1 && c.v() <= 7 && Math.abs(c.u()) <= 2, "shape: inside is within the ring " + c);
        }
        expect(GateShape.LOCK_ORDER.length == 7, "shape: seven dialing chevrons");
        Set<Integer> order = new HashSet<>();
        for (int i : GateShape.LOCK_ORDER) {
            order.add(i);
        }
        expect(order.size() == 7 && !order.contains(7) && !order.contains(8), "shape: seven distinct, the two bottom ones never dial");

        Grid g = new Grid();
        g.gate(10, 64, 20, BlockFace.SOUTH, mats);
        expect(GateShape.check(g, 10, 64, 20, BlockFace.SOUTH, mats) == GateShape.Status.VALID, "shape: complete gate is valid");
        expect(GateShape.check(g, 10, 64, 20, BlockFace.NORTH, mats) == GateShape.Status.VALID, "shape: north/south share the same blocks");
        expect(GateShape.check(g, 10, 64, 20, BlockFace.EAST, mats) == GateShape.Status.RING_BROKEN, "shape: the other orientation does not match");
        expect(GateShape.check(g, 11, 64, 20, BlockFace.SOUTH, mats) == GateShape.Status.RING_BROKEN, "shape: off by one is not a gate");
        expect(GateShape.check(g, 10, 63, 20, BlockFace.SOUTH, mats) == GateShape.Status.RING_BROKEN, "shape: off by one up is not a gate");
        for (GateShape.Cell c : GateShape.RING) {
            int[] p = GateShape.pos(10, 64, 20, BlockFace.SOUTH, c);
            Material was = g.at(p[0], p[1], p[2]);
            g.set(p[0], p[1], p[2], Material.STONE);
            expect(GateShape.check(g, 10, 64, 20, BlockFace.SOUTH, mats) == GateShape.Status.RING_BROKEN, "shape: broken at " + c);
            g.set(p[0], p[1], p[2], was);
        }
        // chevron cells accept lit chevrons; a ring cell does not accept a chevron block
        int[] top = GateShape.pos(10, 64, 20, BlockFace.SOUTH, new GateShape.Cell(0, 8));
        g.set(top[0], top[1], top[2], mats.lit);
        expect(GateShape.check(g, 10, 64, 20, BlockFace.SOUTH, mats) == GateShape.Status.VALID, "shape: a lit chevron is still a chevron");
        int[] ringCell = GateShape.pos(10, 64, 20, BlockFace.SOUTH, new GateShape.Cell(1, 8));
        g.set(ringCell[0], ringCell[1], ringCell[2], mats.chevron);
        expect(GateShape.check(g, 10, 64, 20, BlockFace.SOUTH, mats) == GateShape.Status.RING_BROKEN, "shape: a chevron block on a plain ring cell is wrong");
        g.set(ringCell[0], ringCell[1], ringCell[2], mats.ring);
        for (GateShape.Cell c : GateShape.INSIDE) {
            int[] p = GateShape.pos(10, 64, 20, BlockFace.SOUTH, c);
            g.set(p[0], p[1], p[2], Material.STONE);
            expect(GateShape.check(g, 10, 64, 20, BlockFace.SOUTH, mats) == GateShape.Status.BLOCKED, "shape: blocked at " + c);
            g.set(p[0], p[1], p[2], mats.horizon);
            expect(GateShape.check(g, 10, 64, 20, BlockFace.SOUTH, mats) == GateShape.Status.VALID, "shape: horizon in the opening is fine " + c);
            g.set(p[0], p[1], p[2], mats.iris);
            expect(GateShape.check(g, 10, 64, 20, BlockFace.SOUTH, mats) == GateShape.Status.VALID, "shape: iris in the opening is fine " + c);
            g.set(p[0], p[1], p[2], Material.AIR);
        }

        // facing east/west lies along Z
        Grid e = new Grid();
        e.gate(5, 70, 5, BlockFace.EAST, mats);
        expect(GateShape.check(e, 5, 70, 5, BlockFace.EAST, mats) == GateShape.Status.VALID && GateShape.check(e, 5, 70, 5, BlockFace.WEST, mats) == GateShape.Status.VALID,
                "shape: east/west gate");
        int[] eastTop = GateShape.pos(5, 70, 5, BlockFace.EAST, new GateShape.Cell(2, 7));
        expect(eastTop[0] == 5 && eastTop[1] == 77 && eastTop[2] == 7, "shape: east gate spreads along Z: " + java.util.Arrays.toString(eastTop));
        int[] southTop = GateShape.pos(5, 70, 5, BlockFace.SOUTH, new GateShape.Cell(2, 7));
        expect(southTop[0] == 7 && southTop[1] == 77 && southTop[2] == 5, "shape: south gate spreads along X");

        // finding
        for (BlockFace f : new BlockFace[]{BlockFace.SOUTH, BlockFace.EAST}) {
            Grid fg = new Grid();
            fg.gate(100, 64, 100, f, mats);
            List<GateShape.Found> found = GateShape.find(fg, 100, 66, 102, 10, mats);
            expect(found.size() == 1 && found.get(0).ax() == 100 && found.get(0).ay() == 64 && found.get(0).az() == 100
                    && found.get(0).facing() == f && found.get(0).status() == GateShape.Status.VALID, "find: one gate found, " + f + ": " + found);
            expect(GateShape.find(fg, 100, 66, 102, 2, mats).isEmpty() || GateShape.find(fg, 100, 66, 102, 2, mats).get(0).status() == GateShape.Status.VALID,
                    "find: small radius is fine");
            expect(GateShape.find(fg, 300, 66, 300, 10, mats).isEmpty(), "find: nothing far away");
        }
        Grid zero = new Grid();
        zero.gate(0, 64, 0, BlockFace.SOUTH, mats);
        expect(GateShape.find(zero, 0, 72, 0, 0, mats).size() == 1 && GateShape.find(zero, 0, 71, 0, 0, mats).isEmpty(),
                "find: radius 0 looks at exactly the one block");
        expect(GateShape.find(zero, 3, 67, 0, 0, mats).size() == 1 && GateShape.find(zero, 3, 67, 1, 0, mats).isEmpty() && GateShape.find(zero, 4, 67, 0, 0, mats).isEmpty(),
                "find: radius 0 on a side chevron, and not beside it");
        Grid blocked = new Grid();
        blocked.gate(0, 64, 0, BlockFace.SOUTH, mats);
        blocked.set(0, 66, 0, Material.DIRT);
        List<GateShape.Found> bf = GateShape.find(blocked, 0, 66, 3, 10, mats);
        expect(bf.size() >= 1 && bf.get(0).status() == GateShape.Status.BLOCKED, "find: a blocked gate is reported as blocked: " + bf);
        Grid two = new Grid();
        two.gate(0, 64, 0, BlockFace.SOUTH, mats);
        two.gate(0, 64, 12, BlockFace.SOUTH, mats);
        List<GateShape.Found> tf = GateShape.find(two, 0, 66, 6, 12, mats);
        expect(tf.size() == 2, "find: two gates in reach: " + tf.size());
        Grid broken = new Grid();
        broken.gate(0, 64, 0, BlockFace.SOUTH, mats);
        int[] cut = GateShape.pos(0, 64, 0, BlockFace.SOUTH, new GateShape.Cell(0, 0));
        broken.set(cut[0], cut[1], cut[2], Material.AIR);
        expect(GateShape.find(broken, 0, 66, 2, 10, mats).isEmpty(), "find: a broken ring is not a gate");

        // sides
        expect(GateShape.sideOf(0, 0, BlockFace.SOUTH, 0, 5) == BlockFace.SOUTH && GateShape.sideOf(0, 0, BlockFace.SOUTH, 0, -5) == BlockFace.NORTH
                && GateShape.sideOf(0, 0, BlockFace.SOUTH, 0, 0) == BlockFace.SOUTH, "sideOf: south gate");
        expect(GateShape.sideOf(0, 0, BlockFace.EAST, 5, 0) == BlockFace.EAST && GateShape.sideOf(0, 0, BlockFace.EAST, -5, 0) == BlockFace.WEST
                && GateShape.sideOf(0, 0, BlockFace.EAST, 0, 3) == BlockFace.EAST, "sideOf: east gate");
        expect(GateShape.horizontal(BlockFace.NORTH) && GateShape.horizontal(BlockFace.WEST) && !GateShape.horizontal(BlockFace.UP), "horizontal faces");

        // blueprint
        List<String> bp = GateShape.blueprint();
        int c = 0;
        int r = 0;
        int d = 0;
        for (String line : bp) {
            for (char ch : line.toCharArray()) {
                if (ch == 'C') {
                    c++;
                } else if (ch == 'R') {
                    r++;
                } else if (ch == '.') {
                    d++;
                }
            }
        }
        expect(bp.size() == 9 && c == 9 && r == 11 && d == 31, "blueprint: 9 rows, 9 chevrons, 11 ring, 31 open: " + bp.size() + " " + c + " " + r + " " + d);
        expect(bp.get(0).equals("    R C R") && bp.get(1).equals("  C . . . C") && bp.get(4).equals("R . . . . . R") && bp.get(8).equals("    R R R"),
                "blueprint: exact rows: " + bp);
        expect(GateShape.insideWorld(10, 64, 20, BlockFace.SOUTH).size() == 31, "insideWorld: 31 positions");
    }

    // ------------------------------------------------------------------ store

    static Gate gate(String id, String name, int x, BlockFace f, int... glyphs) {
        Gate g = new Gate(id, name, "world", x, 64, 0, f, GateAddress.of(glyphs));
        g.createdAt = 12345L;
        return g;
    }

    static File tmp(String prefix) throws Exception {
        return Files.createTempDirectory(prefix).toFile();
    }

    static GateStore storeIn(File dir, List<String> warns) {
        return new GateStore(new File(dir, "gates.tsv"), new File(dir, "dhds.tsv"), new File(dir, "known.tsv"), new File(dir, "planets.tsv"), warns::add);
    }

    static void store() throws Exception {
        List<String> warns = new ArrayList<>();
        GateStore s = storeIn(tmp("sg-store"), warns);
        s.load();
        expect(s.gates().isEmpty() && !s.dirty(), "store: starts empty and clean");
        Gate g1 = gate("aaaaaa", "Home", 0, BlockFace.SOUTH, 1, 2, 3, 4, 5, 6);
        Gate g2 = gate("bbbbbb", "Mine", 100, BlockFace.EAST, 6, 5, 4, 3, 2, 1);
        s.add(g1);
        s.add(g2);
        expect(s.dirty() && s.gates().size() == 2, "store: add marks dirty");
        expect(s.gate("aaaaaa") == g1 && s.gate("zzzzzz") == null && s.gate(null) == null, "store: gate by id");
        expect(s.find("home") == g1 && s.find("  MINE ") == g2 && s.find("aaaaaa") == g1 && s.find("nope") == null && s.find(null) == null, "store: find by name or id, any case");
        expect(s.byAddress(GateAddress.of(1, 2, 3, 4, 5, 6)) == g1 && s.byAddress(GateAddress.of(1, 2, 3, 4, 5, 7)) == null && s.byAddress(null) == null, "store: by address");
        expect(s.nameTaken("home", null) && !s.nameTaken("home", g1) && !s.nameTaken("other", null), "store: names are unique, case-blind, except for itself");
        String id = s.newId(new Random(1));
        expect(id.matches("[0-9a-f]{6}") && s.gate(id) == null, "store: new ids look right: " + id);
        GateAddress fresh = s.newAddress(new Random(2));
        expect(s.byAddress(fresh) == null, "store: new addresses are unused");

        // block lookups
        int[] bottom = GateShape.pos(0, 64, 0, BlockFace.SOUTH, new GateShape.Cell(0, 0));
        int[] mid = GateShape.pos(0, 64, 0, BlockFace.SOUTH, new GateShape.Cell(0, 4));
        expect(s.gateAt("world", bottom[0], bottom[1], bottom[2]) == g1 && s.isRing("world", bottom[0], bottom[1], bottom[2]), "store: ring block belongs to its gate");
        expect(s.gateAt("world", mid[0], mid[1], mid[2]) == null && !s.isRing("world", mid[0], mid[1], mid[2]), "store: opening is not ring");
        expect(s.gateInside("world", mid[0], mid[1], mid[2]) == g1 && s.gateInside("world", bottom[0], bottom[1], bottom[2]) == null, "store: opening belongs to its gate");
        expect(s.gateAt("elsewhere", bottom[0], bottom[1], bottom[2]) == null, "store: other world has no such gate");
        int[] eastBottom = GateShape.pos(100, 64, 0, BlockFace.EAST, new GateShape.Cell(2, 7));
        expect(s.gateAt("world", eastBottom[0], eastBottom[1], eastBottom[2]) == g2, "store: east gate ring block");

        // dhds
        s.addDhd(new GateStore.Dhd("world", 5, 65, 6, "aaaaaa", A));
        s.addDhd(new GateStore.Dhd("world", 6, 65, 6, "aaaaaa", null));
        expect(s.dhdAt("world", 5, 65, 6) != null && s.dhdAt("world", 5, 65, 6).owner().equals(A) && s.dhdAt("world", 9, 9, 9) == null, "store: dhd lookup");
        expect(s.dhdsOf("aaaaaa").size() == 2 && s.dhdsOf("bbbbbb").isEmpty(), "store: dhds of a gate");
        expect(s.removeDhd("world", 6, 65, 6) && !s.removeDhd("world", 6, 65, 6) && s.dhdsOf("aaaaaa").size() == 1, "store: remove a dhd once");

        // knowing
        expect(s.learn(A, "bbbbbb") && !s.learn(A, "bbbbbb") && !s.learn(A, "nope") && s.knows(A, "bbbbbb") && !s.knows(B, "bbbbbb"), "store: learn is news once; unknown gate cannot be learned");
        s.learn(A, "aaaaaa");
        expect(s.knownOf(A).size() == 2 && s.knownOf(A).get(0) == g2 && s.knownOf(A).get(1) == g1 && s.knownOf(B).isEmpty(), "store: known in the order learned");
        expect(s.forget(A, "aaaaaa") && !s.forget(A, "aaaaaa") && !s.forget(B, "aaaaaa") && s.knownOf(A).size() == 1, "store: forget");

        // every change marks the store dirty, and a save clears it
        s.flush();
        expect(!s.dirty(), "store: clean after flush");
        s.addDhd(new GateStore.Dhd("world", 7, 65, 7, "aaaaaa", null));
        expect(s.dirty(), "store: adding a device marks dirty");
        s.flush();
        s.removeDhd("world", 7, 65, 7);
        expect(s.dirty(), "store: removing a device marks dirty");
        s.flush();
        s.removeDhd("world", 7, 65, 7);
        expect(!s.dirty(), "store: removing nothing does not");
        s.learn(B, "bbbbbb");
        expect(s.dirty(), "store: learning marks dirty");
        s.flush();
        s.learn(B, "bbbbbb");
        expect(!s.dirty(), "store: learning something known does not");
        s.forget(B, "bbbbbb");
        expect(s.dirty(), "store: forgetting marks dirty");
        s.flush();
        s.forget(B, "bbbbbb");
        expect(!s.dirty(), "store: forgetting something unknown does not");
        s.setPlanetBuilt("zed", true);
        expect(s.dirty(), "store: planet flags mark dirty");
        s.flush();
        s.markDirty();
        expect(s.dirty(), "store: markDirty");
        s.flush();

        // planets
        expect(!s.planetBuilt("abydos"), "store: planet not built yet");
        s.setPlanetBuilt("abydos", true);
        s.setPlanetBuilt("netu", false);
        expect(s.planetBuilt("abydos") && !s.planetBuilt("netu"), "store: planet built flags");

        // removing a gate takes its dhds and knowledge with it
        s.learn(B, "aaaaaa");
        expect(s.remove("aaaaaa") && !s.remove("aaaaaa") && s.gate("aaaaaa") == null, "store: remove a gate once");
        expect(s.dhdsOf("aaaaaa").isEmpty() && s.dhdAt("world", 5, 65, 6) == null && !s.knows(B, "aaaaaa"), "store: removing a gate removes its dhds and who knew it");
        expect(s.gateAt("world", bottom[0], bottom[1], bottom[2]) == null && s.gateInside("world", mid[0], mid[1], mid[2]) == null, "store: removed gate's blocks are no longer indexed");
        expect(warns.isEmpty(), "store: no warnings: " + warns);
    }

    static void storeFiles() throws Exception {
        File dir = tmp("sg-files");
        List<String> warns = new ArrayList<>();
        GateStore s = storeIn(dir, warns);
        s.load();
        Gate g1 = gate("aaaaaa", "Home\tbase", 0, BlockFace.SOUTH, 1, 2, 3, 4, 5, 6);
        g1.owner = A;
        g1.iris = true;
        Gate g2 = gate("p-abydos", "Abydos", 100, BlockFace.EAST, 6, 5, 4, 3, 2, 1);
        g2.planet = "abydos";
        g2.common = true;
        s.add(g1);
        s.add(g2);
        s.addDhd(new GateStore.Dhd("world", 5, 65, 6, "aaaaaa", A));
        s.addDhd(new GateStore.Dhd("sg_abydos", 0, 65, 6, "p-abydos", null));
        s.learn(A, "p-abydos");
        s.learn(B, "aaaaaa");
        s.setPlanetBuilt("abydos", true);
        s.flush();
        expect(!s.dirty(), "files: flush clears dirty");
        for (String n : new String[]{"gates.tsv", "dhds.tsv", "known.tsv", "planets.tsv"}) {
            expect(new File(dir, n).exists(), "files: wrote " + n);
        }
        expect(dir.list().length == 4, "files: no temp files left: " + String.join(",", dir.list()));

        GateStore t = storeIn(dir, warns);
        t.load();
        expect(t.gates().size() == 2 && !t.dirty(), "files: reload finds both gates, clean");
        Gate h = t.gate("aaaaaa");
        expect(h != null && h.name.equals("Home base") && h.owner.equals(A) && h.iris && !h.common && h.planet.isEmpty() && h.facing == BlockFace.SOUTH
                && h.ax == 0 && h.ay == 64 && h.az == 0 && h.world.equals("world") && h.createdAt == 12345L && h.address.equals(GateAddress.of(1, 2, 3, 4, 5, 6)),
                "files: gate fields survive (tab in the name flattened): " + (h == null ? null : h.name));
        Gate p = t.gate("p-abydos");
        expect(p != null && p.owner == null && p.common && !p.iris && p.planet.equals("abydos") && p.facing == BlockFace.EAST && p.isPlanet(), "files: planet gate fields survive");
        expect(t.dhdAt("world", 5, 65, 6).owner().equals(A) && t.dhdAt("sg_abydos", 0, 65, 6).owner() == null, "files: dhds survive with and without owner");
        expect(t.knows(A, "p-abydos") && t.knows(B, "aaaaaa") && !t.knows(A, "aaaaaa"), "files: knowledge survives");
        expect(t.planetBuilt("abydos") && !t.planetBuilt("netu"), "files: planet flags survive");
        expect(t.gateAt("world", GateShape.pos(0, 64, 0, BlockFace.SOUTH, new GateShape.Cell(0, 0))[0], 64, 0) == h, "files: block index rebuilt on load");
        expect(warns.isEmpty(), "files: no warnings on good files: " + warns);
        for (String[] hd : new String[][]{{"gates.tsv", "# NexusStargate gates"}, {"dhds.tsv", "# NexusStargate dialing devices"},
                {"known.tsv", "# NexusStargate known addresses"}, {"planets.tsv", "# NexusStargate planets"}}) {
            expect(Files.readAllLines(new File(dir, hd[0]).toPath()).get(0).startsWith(hd[1]), "files: " + hd[0] + " starts with its header");
        }
        new File(dir, "known.tsv").delete();
        s.flush();
        expect(!new File(dir, "known.tsv").exists(), "files: a clean store writes nothing on flush");
        s.markDirty();
        s.flush();
        expect(new File(dir, "known.tsv").exists() && !s.dirty(), "files: a dirty store writes everything");
        GateStore dirt = storeIn(tmp("sg-dirty"), warns);
        dirt.add(gate("eeeeee", "E", 0, BlockFace.SOUTH, 1, 2, 3, 4, 5, 6));
        dirt.flush();
        dirt.addDhd(new GateStore.Dhd("world", 1, 65, 1, "eeeeee", null));
        expect(dirt.dirty(), "files: adding a device marks the store dirty");
        dirt.flush();
        expect(dirt.removeDhd("world", 1, 65, 1) && dirt.dirty(), "files: removing a device marks the store dirty");
        dirt.flush();
        expect(dirt.learn(A, "eeeeee") && dirt.dirty(), "files: learning marks the store dirty");
        dirt.flush();
        expect(dirt.forget(A, "eeeeee") && dirt.dirty(), "files: forgetting marks the store dirty");
        dirt.flush();
        dirt.setPlanetBuilt("zed", true);
        expect(dirt.dirty(), "files: planet flags mark the store dirty");
        dirt.flush();
        expect(dirt.remove("eeeeee") && dirt.dirty(), "files: removing a gate marks the store dirty");

        // damage: bad lines are skipped and counted, good ones kept
        Files.writeString(new File(dir, "gates.tsv").toPath(),
                "# comment\n\n"
                        + "aaaaaa\tHome\tworld\t0\t64\t0\tSOUTH\t1.2.3.4.5.6\t\t\t\t1\n"
                        + "short\tline\n"
                        + "bbbbbb\tB\tworld\t0\t64\t0\tUP\t1.2.3.4.5.7\t\t\t\t1\n"
                        + "cccccc\tC\tworld\t0\t64\t0\tNORTH\t1.1.3.4.5.7\t\t\t\t1\n"
                        + "dddddd\tD\tworld\tx\t64\t0\tNORTH\t1.2.3.4.5.8\t\t\t\t1\n"
                        + "eeeeee\tE\tworld\t0\t64\t0\tNORTH\t1.2.3.4.5.9\tnot-a-uuid\t\t\t1\n"
                        + "aaaaaa\tDup\tworld\t0\t64\t0\tNORTH\t1.2.3.4.5.9\t\t\t\t1\n"
                        + "ffffff\tF\tworld\t0\t64\t0\tNORTH\t1.2.3.4.6.9\t\t\tcommon,iris\t7\n");
        Files.writeString(new File(dir, "dhds.tsv").toPath(), "world\t1\t2\t3\taaaaaa\t\nworld\t1\t2\t4\tmissing\t\nbad\n");
        Files.writeString(new File(dir, "known.tsv").toPath(), A + "\taaaaaa\n" + A + "\tmissing\nnot-a-uuid\taaaaaa\n" + B + "\taaaaaa\textra\n");
        Files.writeString(new File(dir, "planets.tsv").toPath(), "abydos\t1\n\t1\nnetu\t0\nextra\t1\tx\n");
        List<String> w2 = new ArrayList<>();
        GateStore u = storeIn(dir, w2);
        u.load();
        expect(u.gates().size() == 2 && u.gate("aaaaaa") != null && u.gate("ffffff") != null, "files: only the good gate lines are kept: " + u.gates().size());
        expect(u.gate("ffffff").common && u.gate("ffffff").iris && u.gate("ffffff").createdAt == 7, "files: flags and time parsed");
        expect(u.dhdAt("world", 1, 2, 3) != null && u.dhdAt("world", 1, 2, 4) == null, "files: a dhd for a missing gate is dropped");
        expect(u.knows(A, "aaaaaa") && u.knownOf(A).size() == 1, "files: knowledge of a missing gate is dropped");
        expect(u.planetBuilt("abydos") && !u.planetBuilt("netu"), "files: planet lines");
        expect(!u.knows(B, "aaaaaa") && !u.planetBuilt("extra"), "files: lines with too many columns are skipped");
        expect(w2.size() == 1 && w2.get(0).contains("13 unreadable"), "files: one warning that counts what was skipped: " + w2);

        // loading again forgets what was only in memory
        GateStore mem = storeIn(dir, new ArrayList<>());
        mem.load();
        mem.add(gate("zzzzzz", "Memory", 5, BlockFace.SOUTH, 1, 2, 3, 4, 5, 8));
        mem.addDhd(new GateStore.Dhd("world", 9, 9, 9, "zzzzzz", null));
        mem.learn(A, "zzzzzz");
        mem.setPlanetBuilt("memory-only", true);
        mem.load();
        expect(mem.gate("zzzzzz") == null && mem.dhdAt("world", 9, 9, 9) == null && !mem.knows(A, "zzzzzz") && !mem.planetBuilt("memory-only") && !mem.dirty(),
                "files: load replaces everything held in memory");

        // a save creates the data folder if it is missing
        File deep = new File(tmp("sg-deep"), "a/b");
        GateStore dstore = storeIn(deep, new ArrayList<>());
        dstore.load();
        dstore.add(gate("aaaaaa", "A", 0, BlockFace.SOUTH, 1, 2, 3, 4, 5, 6));
        dstore.flush();
        expect(new File(deep, "gates.tsv").exists() && !dstore.dirty(), "files: the data folder is created on the first save");

        // unreadable directory entry: a directory where a file should be
        File dir2 = tmp("sg-files2");
        new File(dir2, "gates.tsv").mkdir();
        List<String> w3 = new ArrayList<>();
        GateStore v = storeIn(dir2, w3);
        v.load();
        expect(v.gates().isEmpty() && !w3.isEmpty() && w3.get(0).contains("gates.tsv"), "files: an unreadable file warns by name: " + w3);

        // a failed save keeps the store dirty so the next flush tries again
        File dir3 = tmp("sg-files3");
        List<String> w4 = new ArrayList<>();
        GateStore x = storeIn(dir3, w4);
        x.load();
        x.add(gate("aaaaaa", "A", 0, BlockFace.SOUTH, 1, 2, 3, 4, 5, 6));
        new File(dir3, "gates.tsv").mkdir();
        new File(new File(dir3, "gates.tsv"), "block").createNewFile();
        x.flush();
        expect(x.dirty() && !w4.isEmpty(), "files: a failed save stays dirty and warns: " + w4);
        expect(GateStore.flat("a\tb\nc\r") .equals("a b c "), "flat: tabs and newlines become spaces: '" + GateStore.flat("a\tb\nc\r") + "'");
    }

    // ------------------------------------------------------------------ config

    static void config() {
        StargateConfig d = new StargateConfig();
        d.load(new Kit.MapSection());
        expect(d.warnings.isEmpty(), "config: defaults load without warnings: " + d.warnings);
        expect(d.chevronTicks == 14 && d.cooldownSeconds == 8 && d.range == 10 && d.requireKnown && d.maxSeconds == 300 && d.idleSeconds == 90
                && d.arrivalDistance == 1.6 && d.traffic.equals("all") && d.kawooshEnabled && d.kawooshDamage == 8.0 && d.kawooshKnockback == 1.2
                && d.warnRadius == 8 && d.maxGatesPerPlayer == 3 && d.borderRadius == 1500 && !d.preload, "config: defaults");
        expect(d.materials.ring == Material.POLISHED_BLACKSTONE_BRICKS && d.materials.chevron == Material.RED_NETHER_BRICKS && d.materials.lit == Material.SHROOMLIGHT
                && d.materials.horizon == Material.NETHER_PORTAL && d.materials.iris == Material.IRON_BLOCK && d.materials.dhd == Material.CRYING_OBSIDIAN, "config: default blocks");
        expect(d.dhdName.equals("&6Dialing Device") && d.recipeEnabled && d.recipeShape.equals(List.of("AEA", "OCO", "OOO"))
                && d.recipeIngredients.get('A') == Material.AMETHYST_SHARD && d.recipeIngredients.get('E') == Material.ENDER_EYE
                && d.recipeIngredients.get('O') == Material.OBSIDIAN && d.recipeIngredients.get('C') == Material.CRYING_OBSIDIAN && d.recipeIngredients.size() == 4, "config: default recipe");
        expect(d.msg("dial-busy").equals(StargateConfig.DEFAULT_MESSAGES.get("dial-busy")) && d.msg("no-such-key").equals("no-such-key"), "config: msg falls back to the default, then the key");

        StargateConfig c = new StargateConfig();
        c.load(new Kit.MapSection().put("dial.chevron-ticks", 1).put("dial.cooldown-seconds", -5).put("dial.range", 99).put("wormhole.max-seconds", 1)
                .put("wormhole.idle-seconds", 99999).put("wormhole.arrival-distance", 9.0).put("kawoosh.damage", -3.0).put("kawoosh.knockback", 77.0)
                .put("kawoosh.warn-radius", 500).put("gates.max-per-player", -2).put("planets.border-radius", 5));
        expect(c.chevronTicks == 4 && c.cooldownSeconds == 0 && c.range == 16 && c.maxSeconds == 10 && c.idleSeconds == 7200 && c.arrivalDistance == 4.0
                && c.kawooshDamage == 0.0 && c.kawooshKnockback == 5.0 && c.warnRadius == 64 && c.maxGatesPerPlayer == 0 && c.borderRadius == 100,
                "config: numbers are clamped");
        c.load(new Kit.MapSection().put("dial.chevron-ticks", 5000).put("dial.range", 1));
        expect(c.chevronTicks == 100 && c.range == 3, "config: clamped at the top and bottom");
        c.load(new Kit.MapSection().put("wormhole.traffic", "Players").put("dial.require-known-address", false).put("kawoosh.enabled", false).put("planets.preload", true));
        expect(c.traffic.equals("players") && !c.requireKnown && !c.kawooshEnabled && c.preload && c.warnings.isEmpty(), "config: switches");
        c.load(new Kit.MapSection().put("wormhole.traffic", "cows"));
        expect(c.traffic.equals("all") && c.warnings.size() == 1, "config: bad traffic warns: " + c.warnings);

        c.load(new Kit.MapSection().put("gate.ring", "STONE").put("gate.chevron", "gold_block").put("gate.chevron-lit", "glowstone").put("gate.iris", "iron_block")
                .put("gate.dhd", "ENCHANTING_TABLE"));
        expect(c.warnings.isEmpty() && c.materials.ring == Material.STONE && c.materials.chevron == Material.GOLD_BLOCK && c.materials.lit == Material.GLOWSTONE
                && c.materials.dhd == Material.ENCHANTING_TABLE, "config: blocks by name, any case: " + c.warnings);
        c.load(new Kit.MapSection().put("gate.ring", "NOT_A_BLOCK").put("gate.dhd", "AIR"));
        expect(c.warnings.size() == 2 && c.materials.ring == Material.POLISHED_BLACKSTONE_BRICKS && c.materials.dhd == Material.CRYING_OBSIDIAN,
                "config: bad or air blocks warn and fall back: " + c.warnings);
        c.load(new Kit.MapSection().put("gate.ring", "STONE").put("gate.chevron", "STONE"));
        expect(c.warnings.size() == 1 && c.materials.ring == Material.POLISHED_BLACKSTONE_BRICKS && c.materials.chevron == Material.RED_NETHER_BRICKS && c.materials.lit == Material.SHROOMLIGHT,
                "config: ring and chevron the same block resets all three: " + c.warnings);

        c.load(new Kit.MapSection().put("dhd.name", "&bDHD").put("dhd.recipe.enabled", false));
        expect(c.dhdName.equals("&bDHD") && !c.recipeEnabled, "config: dhd name and switch");
        c.load(new Kit.MapSection().put("dhd.recipe.shape", List.of("XX", "X"))
                .put("dhd.recipe.ingredients.X", "DIAMOND"));
        expect(c.warnings.isEmpty() && c.recipeShape.equals(List.of("XX", "X")) && c.recipeIngredients.size() == 1 && c.recipeIngredients.get('X') == Material.DIAMOND,
                "config: custom recipe: " + c.warnings);
        for (Object[] bad : new Object[][]{
                {List.of("XXXX"), "DIAMOND"}, {List.of("XX", "XX", "XX", "XX"), "DIAMOND"}, {List.of("XY"), "DIAMOND"}, {List.of(""), "DIAMOND"}, {List.of("XX"), "NOT_AN_ITEM"},
                {List.of("XX"), "AIR"}}) {
            c.load(new Kit.MapSection().put("dhd.recipe.shape", bad[0]).put("dhd.recipe.ingredients.X", bad[1]));
            expect(c.warnings.size() == 1 && c.recipeShape.equals(List.of("AEA", "OCO", "OOO")) && c.recipeIngredients.size() == 4, "config: bad recipe " + bad[0] + " falls back: " + c.warnings);
        }
        c.load(new Kit.MapSection().put("dhd.recipe.shape", List.of("X")).put("dhd.recipe.ingredients.X", "DIAMOND").put("dhd.recipe.ingredients.Q", "NOT_AN_ITEM"));
        expect(c.warnings.size() == 1 && c.recipeIngredients.size() == 4, "config: a bad ingredient the shape does not even use still spoils the recipe: " + c.warnings);
        c.load(new Kit.MapSection().put("gate.ring", "STONE").put("gate.chevron-lit", "STONE"));
        expect(c.warnings.size() == 1 && c.materials.ring == Material.POLISHED_BLACKSTONE_BRICKS, "config: a ring and lit chevron of one block fall back to the defaults: " + c.warnings);
        c.load(new Kit.MapSection().put("messages.dial-busy", "Busy!").put("messages.group.inner", "x"));
        expect(c.warnings.isEmpty() && c.messages.size() == 1, "config: a section inside messages is ignored quietly: " + c.warnings);
        c.load(new Kit.MapSection().put("dhd.recipe.shape", List.of("X")));
        expect(c.warnings.size() == 1, "config: shape with no ingredients warns");
        c.load(new Kit.MapSection().put("dhd.recipe.ingredients.X", "DIAMOND").put("dhd.recipe.ingredients.XY", "DIAMOND"));
        expect(c.warnings.size() == 1, "config: ingredients with no shape warns");

        c.load(new Kit.MapSection().put("messages.dial-busy", "Busy!").put("messages.made-up", "x"));
        expect(c.msg("dial-busy").equals("Busy!") && c.msg("dial-self").equals(StargateConfig.DEFAULT_MESSAGES.get("dial-self")) && c.warnings.size() == 1
                && c.warnings.get(0).contains("made-up"), "config: messages override, unknown keys warn: " + c.warnings);
        c.load(new Kit.MapSection());
        expect(c.messages.isEmpty() && c.warnings.isEmpty(), "config: reloading forgets old messages and warnings");
        expect(StargateConfig.clamp(5, 1, 3) == 3 && StargateConfig.clamp(0, 1, 3) == 1 && StargateConfig.clamp(2, 1, 3) == 2
                && StargateConfig.clamp(5.0, 1.0, 3.0) == 3.0 && StargateConfig.clamp(0.5, 1.0, 3.0) == 1.0, "config: clamp");
    }

    static Kit.MapSection flatFile(String property) throws Exception {
        Kit.MapSection flat = new Kit.MapSection();
        for (String line : Files.readAllLines(Path.of(System.getProperty(property)))) {
            String[] p = line.split("\t", 3);
            if (p.length < 3) {
                continue;
            }
            flat.put(p[0], switch (p[1]) {
                case "b" -> Boolean.parseBoolean(p[2]);
                case "i" -> Integer.parseInt(p[2]);
                case "d" -> Double.parseDouble(p[2]);
                case "l" -> p[2].isEmpty() ? new ArrayList<String>() : new ArrayList<>(List.of(p[2].split("\u0001", -1)));
                default -> p[2];
            });
        }
        return flat;
    }

    static void shippedConfig() throws Exception {
        Kit.MapSection flat = flatFile("shipped.config");
        StargateConfig c = new StargateConfig();
        c.load(flat);
        StargateConfig d = new StargateConfig();
        d.load(new Kit.MapSection());
        expect(c.warnings.isEmpty(), "shipped config: no warnings: " + c.warnings);
        expect(c.chevronTicks == d.chevronTicks && c.cooldownSeconds == d.cooldownSeconds && c.range == d.range && c.requireKnown == d.requireKnown
                && c.maxSeconds == d.maxSeconds && c.idleSeconds == d.idleSeconds && c.arrivalDistance == d.arrivalDistance && c.traffic.equals(d.traffic)
                && c.kawooshEnabled == d.kawooshEnabled && c.kawooshDamage == d.kawooshDamage && c.kawooshKnockback == d.kawooshKnockback
                && c.warnRadius == d.warnRadius && c.maxGatesPerPlayer == d.maxGatesPerPlayer && c.borderRadius == d.borderRadius && c.preload == d.preload,
                "shipped config: every number equals the built-in default");
        expect(c.materials.ring == d.materials.ring && c.materials.chevron == d.materials.chevron && c.materials.lit == d.materials.lit
                && c.materials.horizon == d.materials.horizon && c.materials.iris == d.materials.iris && c.materials.dhd == d.materials.dhd, "shipped config: blocks equal the defaults");
        expect(c.dhdName.equals(d.dhdName) && c.recipeEnabled == d.recipeEnabled && c.recipeShape.equals(d.recipeShape) && c.recipeIngredients.equals(d.recipeIngredients),
                "shipped config: dhd and recipe equal the defaults");
        expect(c.messages.keySet().equals(StargateConfig.DEFAULT_MESSAGES.keySet()), "shipped config: every message key is shipped and none is extra: " + c.messages.keySet());
        for (Map.Entry<String, String> e : StargateConfig.DEFAULT_MESSAGES.entrySet()) {
            expect(e.getValue().equals(c.messages.get(e.getKey())), "shipped config: message '" + e.getKey() + "' equals the built-in text");
        }
        for (String v : StargateConfig.DEFAULT_MESSAGES.values()) {
            expect(!v.isBlank(), "messages: none blank");
        }
    }

    // ------------------------------------------------------------------ planets

    static Kit.MapSection planet(Kit.MapSection m, String id, Object... kv) {
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put("planets." + id + "." + kv[i], kv[i + 1]);
        }
        return m;
    }

    static void planetBook() {
        PlanetBook b = new PlanetBook();
        Kit.MapSection m = new Kit.MapSection();
        planet(m, "sandy", "name", "Sandy", "designation", "P1X-1", "style", "desert", "seed", 42, "starter", true, "time", 6000, "mobs", false, "blurb", "Hot.");
        planet(m, "plain", "style", "jungle");
        b.load(m, "test");
        expect(b.size() == 2 && b.warnings().isEmpty(), "planets: two planets load: " + b.warnings());
        PlanetBook blank = new PlanetBook();
        Kit.MapSection bm = new Kit.MapSection();
        planet(bm, "blanky", "name", "   ", "style", "jungle");
        blank.load(bm, "test");
        expect(blank.get("blanky") != null && blank.get("blanky").name().equals("blanky"), "planets: a blank name falls back to the id");
        PlanetBook gone = new PlanetBook();
        gone.load(m, "test");
        gone.clear();
        expect(gone.size() == 0 && gone.get("sandy") == null && gone.all().isEmpty() && gone.warnings().isEmpty(), "planets: clear empties the book");
        Planet s = b.get("sandy");
        expect(s.id().equals("sandy") && s.name().equals("Sandy") && s.designation().equals("P1X-1") && s.style().equals("desert") && s.seed() == 42 && s.starter()
                && s.time() == 6000 && !s.mobs() && s.blurb().equals("Hot.") && s.worldName().equals("sg_sandy"), "planets: fields");
        Planet p = b.get("PLAIN");
        expect(p != null && p.name().equals("plain") && p.designation().isEmpty() && !p.starter() && p.mobs() && p.time() == Style.get("jungle").time && p.blurb().isEmpty()
                && p.seed() == "plain".hashCode() * 31L + 17, "planets: defaults for a bare planet");
        expect(b.byWorld("sg_sandy") == s && b.byWorld("world") == null && b.get(null) == null && b.get("zz") == null, "planets: lookups");
        expect(PlanetBook.key("  AbC ") .equals("abc") && PlanetBook.key(null).isEmpty(), "planets: key");

        // merging, field by field
        Kit.MapSection custom = new Kit.MapSection();
        planet(custom, "sandy", "name", "Dunes", "starter", false);
        planet(custom, "newone", "style", "ice", "time", -1000);
        b.load(custom, "custom");
        Planet s2 = b.get("sandy");
        expect(s2.name().equals("Dunes") && !s2.starter() && s2.designation().equals("P1X-1") && s2.seed() == 42 && s2.style().equals("desert") && !s2.mobs() && s2.time() == 6000,
                "planets: custom changes only the fields it names");
        expect(b.get("newone").time() == 23000, "planets: negative time wraps to the day: " + b.get("newone").time());
        expect(b.size() == 3, "planets: custom adds");
        Kit.MapSection hide = new Kit.MapSection().put("hidden-planets", List.of("PLAIN", "ghost"));
        b.load(hide, "custom");
        expect(b.get("plain") == null && b.size() == 2 && b.warnings().size() == 1 && b.warnings().get(0).contains("ghost"), "planets: hidden planets go; unknown ones warn: " + b.warnings());

        PlanetBook bad = new PlanetBook();
        Kit.MapSection mm = new Kit.MapSection();
        planet(mm, "no-style", "name", "x");
        planet(mm, "bad-style", "style", "lava");
        planet(mm, "Bad Id!", "style", "ice");
        planet(mm, "waytoolongidwaytoolongidwaytoolongid", "style", "ice");
        mm.put("planets.notasection", "text");
        bad.load(mm, "test");
        expect(bad.size() == 0 && bad.warnings().size() == 5, "planets: bad entries are skipped with a warning each: " + bad.warnings());
        bad.load(null, "x");
        expect(bad.size() == 0, "planets: null root is fine");
        bad.clear();
        expect(bad.warnings().isEmpty(), "planets: clear");
    }

    static void shippedPlanets() throws Exception {
        PlanetBook b = new PlanetBook();
        b.load(flatFile("shipped.planets"), "planets.yml");
        expect(b.warnings().isEmpty(), "shipped.planets: load without warnings: " + b.warnings());
        expect(b.size() == 6, "shipped.planets: six: " + b.size());
        Set<Long> seeds = new HashSet<>();
        Set<String> names = new HashSet<>();
        Set<String> styles = new HashSet<>();
        int starters = 0;
        for (Planet p : b.all()) {
            expect(seeds.add(p.seed()), "shipped.planets: unique seed " + p.id());
            expect(names.add(p.name()), "shipped.planets: unique name " + p.id());
            styles.add(p.style());
            expect(!p.designation().isBlank() && !p.blurb().isBlank(), "shipped.planets: described " + p.id());
            expect(p.time() >= 0 && p.time() < 24000, "shipped.planets: time of day " + p.id());
            expect(p.worldName().length() <= 27, "shipped.planets: world name is sane " + p.id());
            if (p.starter()) {
                starters++;
            }
        }
        expect(styles.equals(Style.BUILT_IN.keySet()), "shipped.planets: every style is used once or more: " + styles);
        expect(starters == 4, "shipped.planets: four starters: " + starters);
        expect(b.get("abydos").style().equals("desert") && b.get("abydos").starter() && !b.get("abydos").mobs(), "shipped.planets: abydos is a safe desert starter");
        expect(!b.get("netu").starter() && !b.get("cimmeria").starter() && b.get("netu").style().equals("ash") && b.get("cimmeria").style().equals("ice"), "shipped.planets: netu and cimmeria are hidden away");
        expect(b.get("chulak").style().equals("forest") && b.get("p3x-118").style().equals("jungle") && b.get("p4x-884").style().equals("moor"), "shipped.planets: styles");
    }

    // ------------------------------------------------------------------ styles and terrain

    static void styles() {
        expect(Style.BUILT_IN.keySet().equals(Set.of("desert", "forest", "moor", "ice", "jungle", "ash")), "styles: six built in");
        expect(Style.get("DESERT") == Style.get("desert") && Style.get("nope") == null && Style.get(null) == null, "styles: lookup ignores case");
        for (Style s : Style.BUILT_IN.values()) {
            expect(s.features.size() >= 3, "styles: " + s.id + " has things standing on it");
            expect(s.surface != null && s.subsurface != null && s.deep != null && s.shore != null && s.palette != null, "styles: " + s.id + " has its blocks");
            expect(s.time >= 0 && s.time < 24000, "styles: " + s.id + " time of day");
            expect(PlanetGenerator.biomeOf(s.biome) != null, "styles: " + s.id + " biome maps");
            Set<Integer> salts = new HashSet<>();
            for (Style.Feature f : s.features) {
                expect(salts.add(f.salt()), "styles: " + s.id + " feature salts are distinct");
                expect(f.chance() > 0 && f.chance() <= 1, "styles: " + s.id + " chance in range");
                expect(f.grid() > 2 * (f.kind().flattens ? f.kind().radius + Terrain.LEVEL_BLEND : 1), "styles: " + s.id + " " + f.kind() + " grid leaves room");
            }
        }
        expect(Style.get("ice").storm && !Style.get("desert").storm, "styles: only the ice world has a storm");
        expect(Style.get("ash").liquid == Material.LAVA && Style.get("forest").liquid == Material.WATER && Style.get("desert").liquid == null, "styles: liquids");
        expect(PlanetGenerator.biomeOf("DESERT") == Biome.DESERT && PlanetGenerator.biomeOf("BASALT_DELTAS") == Biome.BASALT_DELTAS && PlanetGenerator.biomeOf("???") == Biome.PLAINS
                && PlanetGenerator.biomeOf(null) == Biome.PLAINS, "biome map");
    }

    /** A chunk held in memory. */
    static final class FakeChunk implements ChunkGenerator.ChunkData {
        static int outside;
        static final int MIN = -64;
        static final int MAX = 320;
        final Material[][][] blocks = new Material[16][MAX - MIN][16];

        @Override public int getMinHeight() { return MIN; }
        @Override public int getMaxHeight() { return MAX; }

        @Override
        public void setBlock(int x, int y, int z, Material m) {
            if (x >= 0 && x < 16 && z >= 0 && z < 16 && y >= MIN && y < MAX) {
                blocks[x][y - MIN][z] = m;
            } else {
                outside++;
            }
        }

        @Override
        public void setRegion(int x0, int y0, int z0, int x1, int y1, int z1, Material m) {
            for (int x = x0; x < x1; x++) {
                for (int y = y0; y < y1; y++) {
                    for (int z = z0; z < z1; z++) {
                        setBlock(x, y, z, m);
                    }
                }
            }
        }

        @Override
        public Material getType(int x, int y, int z) {
            Material m = blocks[x][y - MIN][z];
            return m == null ? Material.AIR : m;
        }

        int hash() {
            int h = 1;
            for (int x = 0; x < 16; x++) {
                for (int y = 0; y < MAX - MIN; y++) {
                    for (int z = 0; z < 16; z++) {
                        h = h * 31 + (blocks[x][y][z] == null ? 0 : blocks[x][y][z].ordinal() + 1);
                    }
                }
            }
            return h;
        }
    }

    /** A block of the world made of chunks, filled on demand. */
    static final class Land {
        final Terrain t;
        final Map<Long, FakeChunk> chunks = new HashMap<>();

        Land(Terrain t) {
            this.t = t;
        }

        FakeChunk chunk(int cx, int cz) {
            return chunks.computeIfAbsent(((long) cx << 32) ^ (cz & 0xffffffffL), k -> {
                FakeChunk c = new FakeChunk();
                t.fill(c, cx, cz);
                return c;
            });
        }

        Material at(int x, int y, int z) {
            return chunk(Math.floorDiv(x, 16), Math.floorDiv(z, 16)).getType(Math.floorMod(x, 16), y, Math.floorMod(z, 16));
        }

        int top(int x, int z) {
            for (int y = FakeChunk.MAX - 1; y >= FakeChunk.MIN; y--) {
                Material m = at(x, y, z);
                if (m != Material.AIR && m != Material.WATER && m != Material.LAVA) {
                    return y;
                }
            }
            return FakeChunk.MIN;
        }
    }

    static void terrain() {
        for (Style style : Style.BUILT_IN.values()) {
            String id = style.id;
            Terrain a = new Terrain(style, 1234);
            Terrain b = new Terrain(style, 1234);
            Terrain other = new Terrain(style, 9999);
            FakeChunk c1 = new FakeChunk();
            FakeChunk c2 = new FakeChunk();
            FakeChunk c3 = new FakeChunk();
            a.fill(c1, 3, -2);
            b.fill(c2, 3, -2);
            other.fill(c3, 3, -2);
            expect(c1.hash() == c2.hash(), "terrain " + id + ": same seed, same chunk");
            expect(c1.hash() != c3.hash(), "terrain " + id + ": another seed, another chunk");
            expect(c1.getType(5, FakeChunk.MIN, 5) == Material.BEDROCK, "terrain " + id + ": bedrock at the bottom");

            // order does not matter
            Land fwd = new Land(a);
            Land back = new Land(new Terrain(style, 1234));
            List<int[]> cells = new ArrayList<>();
            for (int cx = -3; cx <= 3; cx++) {
                for (int cz = -3; cz <= 3; cz++) {
                    cells.add(new int[]{cx, cz});
                }
            }
            for (int[] c : cells) {
                fwd.chunk(c[0], c[1]);
            }
            for (int i = cells.size() - 1; i >= 0; i--) {
                back.chunk(cells.get(i)[0], cells.get(i)[1]);
            }
            boolean same = true;
            for (int[] c : cells) {
                same &= fwd.chunk(c[0], c[1]).hash() == back.chunk(c[0], c[1]).hash();
            }
            expect(same, "terrain " + id + ": the order chunks are made in does not change them");

            // the arrival area is flat, open and solid
            int ground = a.originGround();
            expect(a.height(0, 0) == ground, "terrain " + id + ": height at the origin is the ground: " + a.height(0, 0) + " vs " + ground);
            for (int x = -Terrain.PLATEAU; x <= Terrain.PLATEAU; x += 3) {
                for (int z = -Terrain.PLATEAU; z <= Terrain.PLATEAU; z += 3) {
                    if (x * x + z * z <= Terrain.PLATEAU * Terrain.PLATEAU) {
                        expect(a.height(x, z) == ground, "terrain " + id + ": flat plateau at " + x + "," + z + ": " + a.height(x, z) + " vs " + ground);
                    }
                }
            }
            expect(fwd.top(0, 0) == ground && fwd.at(0, ground, 0) == a.surfaceAt(0, 0, ground), "terrain " + id + ": the block at the origin is the surface");
            expect(fwd.at(0, ground - 1, 0) == style.subsurface, "terrain " + id + ": subsurface under the surface");
            boolean clear = true;
            for (int x = -9; x <= 9; x++) {
                for (int z = -9; z <= 9; z++) {
                    for (int y = ground + 1; y <= ground + 12; y++) {
                        clear &= fwd.at(x, y, z) == Material.AIR;
                    }
                }
            }
            expect(clear, "terrain " + id + ": nothing stands within nine blocks of the arrival gate");
            expect(fwd.at(0, ground, 0) != Material.WATER && fwd.at(0, ground, 0) != Material.LAVA && fwd.at(0, ground + 1, 0) == Material.AIR, "terrain " + id + ": dry land at the origin");

            // land rises and falls, but never leaves the world
            int lo = 999;
            int hi = -999;
            for (int[] c : cells) {
                for (int i = 0; i < 16; i += 5) {
                    int h = a.height(c[0] * 16 + i, c[1] * 16 + i);
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
            }
            expect(lo > FakeChunk.MIN + 8 && hi < FakeChunk.MAX - 40, "terrain " + id + ": land inside the world: " + lo + ".." + hi);
            expect(hi - lo >= 3, "terrain " + id + ": the land is not flat: " + (hi - lo));
        }
        Terrain d = new Terrain(Style.get("desert"), 5);
        expect(d.style() == Style.get("desert"), "terrain: style accessor");
        PlanetGenerator g = new PlanetGenerator(Style.get("desert"), 5);
        expect(g.terrain().originGround() == d.originGround(), "generator: same terrain for same style and seed");
    }

    static final Map<Style.Kind, Integer> SEEN = new HashMap<>();

    /** Every levelled structure the generator places looks exactly like the pure function says, even across chunk borders. */
    static void structuresOnGround() {
        for (Style style : Style.BUILT_IN.values()) {
            Terrain t = new Terrain(style, 31337);
            Land land = new Land(t);
            int box = 224;
            List<Terrain.Instance> list = t.instancesIn(-box, -box, box, box);
            int flat = 0;
            for (Terrain.Instance in : list) {
                Style.Kind kind = in.kind();
                SEEN.merge(kind, 1, Integer::sum);
                if (!kind.flattens) {
                    continue;
                }
                flat++;
                int tall = Structures.height(kind);
                int r = kind.radius;
                int solid = 0;
                int wrong = 0;
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        for (int dy = 1; dy <= tall; dy++) {
                            Material want = Structures.blockAt(style, kind, in.hash(), dx, dy, dz);
                            if (want == null) {
                                continue;
                            }
                            solid++;
                            if (land.at(in.x() + dx, in.baseY() + dy, in.z() + dz) != want) {
                                wrong++;
                            }
                        }
                    }
                }
                expect(solid > 20, "structures " + style.id + " " + kind + ": it has blocks: " + solid);
                expect(wrong == 0, "structures " + style.id + " " + kind + " at " + in.x() + "," + in.z() + ": " + wrong + " of " + solid + " blocks differ from the plan");
                expect(t.height(in.x(), in.z()) == in.baseY(), "structures " + style.id + " " + kind + ": ground under it is levelled to its base");
            }
            expect(flat >= 2, "structures " + style.id + ": at least two big structures in a 450 block square: " + flat);
        }
        for (Style.Kind k : Style.Kind.values()) {
            boolean used = false;
            for (Style s : Style.BUILT_IN.values()) {
                for (Style.Feature f : s.features) {
                    used |= f.kind() == k;
                }
            }
            expect(used, "structures: kind " + k + " is used by some style");
            expect(Structures.height(k) > 0, "structures: kind " + k + " has a height");
        }
        for (Style.Kind k : new Style.Kind[]{Style.Kind.PYRAMID, Style.Kind.OBELISK, Style.Kind.COLUMN_RING, Style.Kind.FORTRESS, Style.Kind.DOME, Style.Kind.RUIN_WALL}) {
            expect(SEEN.getOrDefault(k, 0) > 0, "structures: kind " + k + " appeared somewhere in the test lands: " + SEEN);
        }
    }

    static void generator() {
        Style desert = Style.get("desert");
        PlanetGenerator g = new PlanetGenerator(desert, 77);
        FakeChunk c = new FakeChunk();
        g.generateNoise(null, new Random(), 0, 0, c);
        expect(c.getType(0, g.terrain().originGround(), 0) == desert.surface, "generator: generateNoise fills the chunk");
        org.bukkit.World w = new org.bukkit.World("sg_x");
        org.bukkit.Location spawn = g.getFixedSpawnLocation(w, new Random());
        expect(spawn.getWorld() == w && spawn.getX() == 0.5 && spawn.getZ() == 9.5 && spawn.getY() == g.terrain().originGround() + 1, "generator: fixed spawn is just beyond the dialing device");
        org.bukkit.generator.BiomeProvider bp = g.getDefaultBiomeProvider(null);
        expect(bp.getBiome(null, 5, 70, 5) == Biome.DESERT && bp.getBiome(null, -9000, 3, 12) == Biome.DESERT && bp.getBiomes(null).equals(List.of(Biome.DESERT)), "generator: one biome everywhere");
        expect(!g.shouldGenerateNoise() && !g.shouldGenerateSurface() && !g.shouldGenerateCaves() && !g.shouldGenerateDecorations() && !g.shouldGenerateMobs() && !g.shouldGenerateStructures(),
                "generator: asks the server for none of the vanilla generation");
    }

    /** Properties that must hold for every cell, for every style: no levelled structure in a liquid, none crowding a stronger one, caching is stable. */
    static void terrainInvariants() throws Exception {
        int checked = 0;
        boolean remembered = true;
        for (Style style : Style.BUILT_IN.values()) {
            for (long seed = 1; seed <= 3; seed++) {
                Terrain t = new Terrain(style, seed * 7919L);
                for (int fi = 0; fi < style.features.size(); fi++) {
                    Style.Feature f = style.features.get(fi);
                    if (!f.kind().flattens) {
                        continue;
                    }
                    for (int cx = -40; cx <= 40; cx++) {
                        for (int cz = -40; cz <= 40; cz++) {
                            Terrain.Instance in = t.instance(f, cx, cz);
                            if (in == null) {
                                continue;
                            }
                            checked++;
                            if (style.liquid != null && in.baseY() <= style.liquidLevel + 1) {
                                expect(false, "terrain: " + style.id + " has a structure standing in liquid at " + in.x() + "," + in.z());
                            }
                            for (int oi = 0; oi < fi; oi++) {
                                Style.Feature other = style.features.get(oi);
                                if (!other.kind().flattens) {
                                    continue;
                                }
                                int reach = f.kind().radius + other.kind().radius + 2 * Terrain.LEVEL_BLEND;
                                int g = other.grid();
                                for (int ox = Math.floorDiv(in.x() - reach, g); ox <= Math.floorDiv(in.x() + reach, g); ox++) {
                                    for (int oz = Math.floorDiv(in.z() - reach, g); oz <= Math.floorDiv(in.z() + reach, g); oz++) {
                                        Terrain.Instance o = t.instance(other, ox, oz);
                                        if (o != null) {
                                            long dx = o.x() - in.x();
                                            long dz = o.z() - in.z();
                                            if (dx * dx + dz * dz < (long) reach * reach) {
                                                expect(false, "terrain: " + style.id + " has crowded structures at " + in.x() + "," + in.z());
                                            }
                                        }
                                    }
                                }
                            }
                            remembered &= t.instance(f, cx, cz) == in;
                        }
                    }
                }
            }
        }
        // the search box is inclusive on every side: touching counts, one block further does not
        int probed = 0;
        boolean edgesRight = true;
        for (Style style : Style.BUILT_IN.values()) {
            Terrain t = new Terrain(style, 7919L);
            for (Style.Feature f : style.features) {
                if (!f.kind().flattens) {
                    continue;
                }
                int taken = 0;
                for (int cx = -30; cx <= 30 && taken < 3; cx++) {
                    for (int cz = -30; cz <= 30 && taken < 3; cz++) {
                        Terrain.Instance in = t.instance(f, cx, cz);
                        if (in == null) {
                            continue;
                        }
                        taken++;
                        probed++;
                        int r = f.kind().radius;
                        edgesRight &= t.instancesIn(in.x() + r, in.z(), in.x() + r, in.z()).contains(in);
                        edgesRight &= t.instancesIn(in.x() - r, in.z(), in.x() - r, in.z()).contains(in);
                        edgesRight &= t.instancesIn(in.x(), in.z() + r, in.x(), in.z() + r).contains(in);
                        edgesRight &= t.instancesIn(in.x(), in.z() - r, in.x(), in.z() - r).contains(in);
                        edgesRight &= !t.instancesIn(in.x() + r + 1, in.z(), in.x() + r + 1, in.z()).contains(in);
                        edgesRight &= !t.instancesIn(in.x() - r - 1, in.z(), in.x() - r - 1, in.z()).contains(in);
                        edgesRight &= !t.instancesIn(in.x(), in.z() + r + 1, in.x(), in.z() + r + 1).contains(in);
                        edgesRight &= !t.instancesIn(in.x(), in.z() - r - 1, in.x(), in.z() - r - 1).contains(in);
                        edgesRight &= t.instancesIn(in.x() - 40 * r, in.z() - 40 * r, in.x() - 39 * r, in.z() - 39 * r).contains(in) == false;
                    }
                }
            }
        }
        expect(probed > 10 && edgesRight, "terrain: the search box edges are inclusive (" + probed + " structures probed)");
        // trees and spikes never stand in liquid or on its very edge
        int wet = 0;
        boolean dry = true;
        for (Style style : Style.BUILT_IN.values()) {
            if (style.liquid == null) {
                continue;
            }
            for (long seed = 1; seed <= 4; seed++) {
                Terrain t = new Terrain(style, seed * 104729L);
                for (Terrain.Instance in : t.instancesIn(-900, -900, 900, 900)) {
                    if (!in.feature().kind().flattens) {
                        wet++;
                        dry &= t.height(in.x(), in.z()) > style.liquidLevel + 1;
                    }
                }
            }
        }
        expect(wet > 20 && dry, "terrain: none of the " + wet + " trees or spikes stands at or below the waterline");
        expect(remembered, "terrain: an instance is made once and remembered");
        expect(checked > 100, "terrain: the invariants looked at " + checked + " structures");
        Terrain big = new Terrain(Style.get("desert"), 5);
        Style.Feature any = Style.get("desert").features.get(0);
        for (int i = 0; i < 41_000; i++) {
            big.instance(any, i, 1);
        }
        java.lang.reflect.Field memo = Terrain.class.getDeclaredField("memo");
        memo.setAccessible(true);
        expect(((java.util.Map<?, ?>) memo.get(big)).size() < 41_000, "terrain: the memory of made things is kept bounded");
    }

    /** Column by column: bedrock, deep stone, subsurface, surface, liquid, for chunks with nothing built in them. */
    static void terrainColumns() {
        boolean right = true;
        String why = "";
        int columns = 0;
        int atWaterline = 0;
        int shoreCols = 0;
        int patched = 0;
        for (Style style : Style.BUILT_IN.values()) {
            Terrain t = new Terrain(style, 31337L);
            int chunksDone = 0;
            for (int cx = -12; cx <= 12 && chunksDone < 40; cx++) {
                for (int cz = -12; cz <= 12 && chunksDone < 40; cz++) {
                    if (!t.instancesIn(cx * 16, cz * 16, cx * 16 + 15, cz * 16 + 15).isEmpty()) {
                        continue;
                    }
                    chunksDone++;
                    FakeChunk c = new FakeChunk();
                    t.fill(c, cx, cz);
                    for (int lx = 0; lx < 16; lx++) {
                        for (int lz = 0; lz < 16; lz++) {
                            int x = cx * 16 + lx;
                            int z = cz * 16 + lz;
                            int h = Math.max(FakeChunk.MIN + 4, Math.min(319 - 24, t.height(x, z)));
                            columns++;
                            boolean ok = c.getType(lx, FakeChunk.MIN, lz) == Material.BEDROCK
                                    && c.getType(lx, FakeChunk.MIN + 1, lz) == style.deep
                                    && c.getType(lx, h - 4, lz) == style.deep
                                    && c.getType(lx, h - 3, lz) == style.subsurface
                                    && c.getType(lx, h - 1, lz) == style.subsurface
                                    && c.getType(lx, h, lz) == t.surfaceAt(x, z, h);
                            if (style.liquid != null) {
                                Material above = c.getType(lx, h + 1, lz);
                                if (h < style.liquidLevel) {
                                    ok &= above == style.liquid && c.getType(lx, style.liquidLevel, lz) == style.liquid && c.getType(lx, style.liquidLevel + 1, lz) != style.liquid;
                                } else {
                                    ok &= above != style.liquid;
                                }
                                if (h == style.liquidLevel) {
                                    atWaterline++;
                                }
                            }
                            if (!ok && why.isEmpty()) {
                                why = style.id + " at " + x + "," + z;
                            }
                            right &= ok;
                            Material top = t.surfaceAt(x, z, h);
                            if (style.liquid != null && h <= style.liquidLevel + 1) {
                                right &= top == style.shore;
                                shoreCols++;
                            } else {
                                right &= top == style.surface || top == style.patch;
                                if (top == style.patch && style.patch != null) {
                                    patched++;
                                }
                            }
                            right &= top != null;
                        }
                    }
                }
            }
            // the surface rules at the waterline, straight from the function
            if (style.liquid != null) {
                right &= t.surfaceAt(3, 3, style.liquidLevel + 1) == style.shore && t.surfaceAt(3, 3, style.liquidLevel - 5) == style.shore;
                Material dry = t.surfaceAt(3, 3, style.liquidLevel + 2);
                right &= dry == style.surface || dry == style.patch;
            } else {
                Material low = t.surfaceAt(3, 3, -50);
                right &= low == style.surface || low == style.patch;
            }
        }
        expect(columns > 5000 && right, "terrain: every column is bedrock, deep stone, subsurface, surface and (low ground) liquid, in order: " + why + " over " + columns + " columns");
        expect(atWaterline > 0 && shoreCols > 0 && patched > 0, "terrain: the sample included waterline columns (" + atWaterline + "), shore (" + shoreCols + ") and patches (" + patched + ")");
    }

    static void menuWrap() {
        expect(DialMenu.wrap("&7short", 38).equals(List.of("&7short")), "wrap: short stays whole");
        List<String> w = DialMenu.wrap("&7" + "word ".repeat(20).trim(), 20);
        expect(w.size() > 1, "wrap: long line splits: " + w);
        for (String line : w) {
            expect(line.startsWith("&7") && line.length() <= 22, "wrap: pieces keep the colour and the width: " + line);
        }
        expect(DialMenu.wrap(null, 5).isEmpty(), "wrap: null");
        expect(DialMenu.wrap("&", 5).equals(List.of("&")), "wrap: a lone ampersand is left alone");
        expect(DialMenu.wrap("&7" + "x".repeat(20), 5).equals(List.of("&7" + "x".repeat(20))), "wrap: one word longer than the width is a single piece, not an empty one first");
        expect(DialMenu.wrap("&7aaaa bbbbb", 9).equals(List.of("&7aaaa", "&7bbbbb")), "wrap: a word that would overflow by one starts a new piece: " + DialMenu.wrap("&7aaaa bbbbb", 9));
        expect(DialMenu.wrap("&7aaaa bbbb cccc", 9).equals(List.of("&7aaaa bbbb", "&7cccc")), "wrap: a piece that fits the width exactly is kept whole: " + DialMenu.wrap("&7aaaa bbbb cccc", 9));
        expect(DialMenu.pages(0) == 1 && DialMenu.pages(45) == 1 && DialMenu.pages(46) == 2 && DialMenu.pages(90) == 2 && DialMenu.pages(91) == 3, "menu: page count");
    }
}
