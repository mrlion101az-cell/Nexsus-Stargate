package com.nexuscraft.nexusstargate;

import org.bukkit.block.BlockFace;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Every gate, dialing device and "who knows which address", kept in four small tab-separated files. */
final class GateStore {

    /** A dialing device standing in the world. */
    record Dhd(String world, int x, int y, int z, String gateId, UUID owner) {
    }

    private final File gatesFile;
    private final File dhdsFile;
    private final File knownFile;
    private final File planetsFile;
    private final Consumer<String> warn;

    private final Map<String, Gate> gates = new LinkedHashMap<>();
    private final Map<String, Dhd> dhds = new LinkedHashMap<>();
    private final Map<UUID, Set<String>> known = new LinkedHashMap<>();
    private final Map<String, Boolean> planetsBuilt = new LinkedHashMap<>();
    private Map<String, String> blockIndex = new HashMap<>();
    private Map<String, String> insideIndex = new HashMap<>();
    private boolean dirty;

    GateStore(File gatesFile, File dhdsFile, File knownFile, File planetsFile, Consumer<String> warn) {
        this.gatesFile = gatesFile;
        this.dhdsFile = dhdsFile;
        this.knownFile = knownFile;
        this.planetsFile = planetsFile;
        this.warn = warn;
    }

    // ------------------------------------------------------------------ loading

    void load() {
        gates.clear();
        dhds.clear();
        known.clear();
        planetsBuilt.clear();
        dirty = false;
        int bad = 0;
        for (String line : lines(gatesFile)) {
            String[] f = line.split("\t", -1);
            try {
                if (f.length != 12) {
                    throw new IllegalArgumentException("columns");
                }
                GateAddress a = GateAddress.parse(f[7]);
                BlockFace face = BlockFace.valueOf(f[6].toUpperCase(Locale.ROOT));
                if (a == null || f[0].isBlank() || !GateShape.horizontal(face) || gates.containsKey(f[0])) {
                    throw new IllegalArgumentException("gate");
                }
                Gate g = new Gate(f[0], f[1], f[2], Integer.parseInt(f[3]), Integer.parseInt(f[4]), Integer.parseInt(f[5]), face, a);
                g.owner = f[8].isEmpty() ? null : UUID.fromString(f[8]);
                g.planet = f[9];
                g.common = f[10].contains("common");
                g.iris = f[10].contains("iris");
                g.createdAt = Long.parseLong(f[11]);
                gates.put(g.id, g);
            } catch (IllegalArgumentException ex) {
                bad++;
            }
        }
        for (String line : lines(dhdsFile)) {
            String[] f = line.split("\t", -1);
            try {
                if (f.length != 6 || !gates.containsKey(f[4])) {
                    throw new IllegalArgumentException("dhd");
                }
                Dhd d = new Dhd(f[0], Integer.parseInt(f[1]), Integer.parseInt(f[2]), Integer.parseInt(f[3]), f[4],
                        f[5].isEmpty() ? null : UUID.fromString(f[5]));
                dhds.put(posKey(d.world(), d.x(), d.y(), d.z()), d);
            } catch (IllegalArgumentException ex) {
                bad++;
            }
        }
        for (String line : lines(knownFile)) {
            String[] f = line.split("\t", -1);
            try {
                if (f.length != 2 || !gates.containsKey(f[1])) {
                    throw new IllegalArgumentException("known");
                }
                known.computeIfAbsent(UUID.fromString(f[0]), k -> new LinkedHashSet<>()).add(f[1]);
            } catch (IllegalArgumentException ex) {
                bad++;
            }
        }
        for (String line : lines(planetsFile)) {
            String[] f = line.split("\t", -1);
            if (f.length != 2 || f[0].isBlank()) {
                bad++;
                continue;
            }
            planetsBuilt.put(f[0], f[1].equals("1"));
        }
        if (bad > 0) {
            warn.accept(bad + " unreadable line(s) in the stargate files were skipped.");
        }
        reindex();
    }

    private List<String> lines(File file) {
        List<String> out = new ArrayList<>();
        if (!file.exists()) {
            return out;
        }
        try {
            for (String line : Files.readAllLines(file.toPath(), StandardCharsets.UTF_8)) {
                if (!line.isBlank() && !line.startsWith("#")) {
                    out.add(line);
                }
            }
        } catch (IOException ex) {
            warn.accept("Could not read " + file.getName() + ": " + ex.getMessage());
        }
        return out;
    }

    // ------------------------------------------------------------------ gates

    static String posKey(String world, int x, int y, int z) {
        return world + ":" + x + ":" + y + ":" + z;
    }

    Gate gate(String id) {
        return id == null ? null : gates.get(id);
    }

    Collection<Gate> gates() {
        return Collections.unmodifiableCollection(gates.values());
    }

    Gate byAddress(GateAddress address) {
        if (address == null) {
            return null;
        }
        for (Gate g : gates.values()) {
            if (g.address.equals(address)) {
                return g;
            }
        }
        return null;
    }

    /** By exact id or by name (any case); null when nothing matches. */
    Gate find(String idOrName) {
        if (idOrName == null) {
            return null;
        }
        String q = idOrName.trim();
        Gate g = gates.get(q);
        if (g != null) {
            return g;
        }
        for (Gate other : gates.values()) {
            if (other.name.equalsIgnoreCase(q)) {
                return other;
            }
        }
        return null;
    }

    boolean nameTaken(String name, Gate except) {
        for (Gate g : gates.values()) {
            if (g != except && g.name.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    /** A fresh six-character id nobody has. */
    String newId(Random rng) {
        String id;
        do {
            id = Integer.toHexString(0x100000 + rng.nextInt(0xEFFFFF)).substring(0, 6);
        } while (gates.containsKey(id));
        return id;
    }

    GateAddress newAddress(Random rng) {
        GateAddress a;
        do {
            a = GateAddress.random(rng, x -> byAddress(x) != null);
        } while (a == null);
        return a;
    }

    void add(Gate g) {
        gates.put(g.id, g);
        dirty = true;
        reindex();
    }

    /** Removes a gate with its dialing devices and every "known" entry. */
    boolean remove(String id) {
        Gate g = gates.remove(id);
        if (g == null) {
            return false;
        }
        dhds.values().removeIf(d -> d.gateId().equals(id));
        for (Set<String> s : known.values()) {
            s.remove(id);
        }
        dirty = true;
        reindex();
        return true;
    }

    /** The gate whose ring (chevrons included) includes this block; null if none. */
    Gate gateAt(String world, int x, int y, int z) {
        return gates.get(blockIndex.get(posKey(world, x, y, z)));
    }

    /** True when the block is a ring or chevron block of some gate. */
    boolean isRing(String world, int x, int y, int z) {
        return blockIndex.containsKey(posKey(world, x, y, z));
    }

    /** The gate whose opening includes this block; null if none. */
    Gate gateInside(String world, int x, int y, int z) {
        return gates.get(insideIndex.get(posKey(world, x, y, z)));
    }

    private void reindex() {
        Map<String, String> idx = new HashMap<>();
        Map<String, String> inside = new HashMap<>();
        for (Gate g : gates.values()) {
            for (GateShape.Cell c : GateShape.RING) {
                int[] p = GateShape.pos(g.ax, g.ay, g.az, g.facing, c);
                idx.put(posKey(g.world, p[0], p[1], p[2]), g.id);
            }
            for (GateShape.Cell c : GateShape.INSIDE) {
                int[] p = GateShape.pos(g.ax, g.ay, g.az, g.facing, c);
                inside.put(posKey(g.world, p[0], p[1], p[2]), g.id);
            }
        }
        blockIndex = idx;
        insideIndex = inside;
    }

    // ------------------------------------------------------------------ dialing devices

    void addDhd(Dhd d) {
        dhds.put(posKey(d.world(), d.x(), d.y(), d.z()), d);
        dirty = true;
    }

    Dhd dhdAt(String world, int x, int y, int z) {
        return dhds.get(posKey(world, x, y, z));
    }

    boolean removeDhd(String world, int x, int y, int z) {
        boolean had = dhds.remove(posKey(world, x, y, z)) != null;
        if (had) {
            dirty = true;
        }
        return had;
    }

    List<Dhd> dhdsOf(String gateId) {
        List<Dhd> out = new ArrayList<>();
        for (Dhd d : dhds.values()) {
            if (d.gateId().equals(gateId)) {
                out.add(d);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ who knows what

    /** Remembers that a player knows a gate's address; true when that was news to them. */
    boolean learn(UUID player, String gateId) {
        if (!gates.containsKey(gateId)) {
            return false;
        }
        boolean added = known.computeIfAbsent(player, k -> new LinkedHashSet<>()).add(gateId);
        if (added) {
            dirty = true;
        }
        return added;
    }

    boolean forget(UUID player, String gateId) {
        Set<String> s = known.get(player);
        boolean had = s != null && s.remove(gateId);
        if (had) {
            dirty = true;
        }
        return had;
    }

    boolean knows(UUID player, String gateId) {
        Set<String> s = known.get(player);
        return s != null && s.contains(gateId);
    }

    /** The gates a player has learned, in the order learned (gates that no longer exist are gone). */
    List<Gate> knownOf(UUID player) {
        List<Gate> out = new ArrayList<>();
        for (String id : known.getOrDefault(player, Set.of())) {
            Gate g = gates.get(id);
            if (g != null) {
                out.add(g);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ planets

    boolean planetBuilt(String planetId) {
        return planetsBuilt.getOrDefault(planetId, false);
    }

    void setPlanetBuilt(String planetId, boolean built) {
        planetsBuilt.put(planetId, built);
        dirty = true;
    }

    // ------------------------------------------------------------------ saving

    void markDirty() {
        dirty = true;
    }

    boolean dirty() {
        return dirty;
    }

    void flush() {
        if (!dirty) {
            return;
        }
        List<String> g = new ArrayList<>();
        g.add("# NexusStargate gates: id, name, world, x, y, z, facing, address, owner, planet, flags, createdAt");
        for (Gate x : gates.values()) {
            String flags = (x.common ? "common" : "") + (x.iris ? ",iris" : "");
            g.add(x.id + "\t" + flat(x.name) + "\t" + flat(x.world) + "\t" + x.ax + "\t" + x.ay + "\t" + x.az + "\t" + x.facing.name()
                    + "\t" + x.address.code() + "\t" + (x.owner == null ? "" : x.owner) + "\t" + flat(x.planet) + "\t" + flags + "\t" + x.createdAt);
        }
        List<String> d = new ArrayList<>();
        d.add("# NexusStargate dialing devices: world, x, y, z, gate, owner");
        for (Dhd x : dhds.values()) {
            d.add(flat(x.world()) + "\t" + x.x() + "\t" + x.y() + "\t" + x.z() + "\t" + x.gateId() + "\t" + (x.owner() == null ? "" : x.owner()));
        }
        List<String> k = new ArrayList<>();
        k.add("# NexusStargate known addresses: player, gate");
        for (Map.Entry<UUID, Set<String>> e : known.entrySet()) {
            for (String id : e.getValue()) {
                k.add(e.getKey() + "\t" + id);
            }
        }
        List<String> p = new ArrayList<>();
        p.add("# NexusStargate planets: planet, built");
        for (Map.Entry<String, Boolean> e : planetsBuilt.entrySet()) {
            p.add(flat(e.getKey()) + "\t" + (e.getValue() ? "1" : "0"));
        }
        boolean a = write(gatesFile, g);
        boolean b = write(dhdsFile, d);
        boolean c = write(knownFile, k);
        boolean e = write(planetsFile, p);
        if (a && b && c && e) {
            dirty = false;
        }
    }

    static String flat(String s) {
        return s == null ? "" : s.replace('\t', ' ').replace('\n', ' ').replace('\r', ' ');
    }

    private boolean write(File file, List<String> lines) {
        try {
            File dir = file.getAbsoluteFile().getParentFile();
            if (dir != null) {
                dir.mkdirs();
            }
            File tmp = new File(file.getPath() + ".tmp");
            Files.write(tmp.toPath(), lines, StandardCharsets.UTF_8);
            Files.move(tmp.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (IOException ex) {
            warn.accept("Could not save " + file.getName() + ": " + ex.getMessage());
            return false;
        }
    }
}
