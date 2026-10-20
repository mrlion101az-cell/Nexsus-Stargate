package com.nexuscraft.nexusstargate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/** Gates and dialing devices: making them, finding them, who may use them. Wormholes live in {@link WormholeService}. */
final class GateService {

    static final String ADMIN = "nexusstargate.admin";
    static final String USE = "nexusstargate.use";
    static final String BUILD = "nexusstargate.build";

    enum Placed { OK, NO_GATE, BLOCKED, LIMIT, NOT_OWNER }

    private final StargateConfig cfg;
    private final GateStore store;
    private final PlanetBook planets;
    private final GateBuilder builder;
    private final Items items;
    private final Random rng;
    private final LongSupplier clock;

    /** Called just before a gate is deleted (the wormhole service closes any wormhole on it). */
    Consumer<Gate> beforeRemove = g -> { };

    GateService(StargateConfig cfg, GateStore store, PlanetBook planets, GateBuilder builder, Items items, Random rng, LongSupplier clock) {
        this.cfg = cfg;
        this.store = store;
        this.planets = planets;
        this.builder = builder;
        this.items = items;
        this.rng = rng;
        this.clock = clock;
    }

    StargateConfig cfg() {
        return cfg;
    }

    GateStore store() {
        return store;
    }

    PlanetBook planets() {
        return planets;
    }

    GateBuilder builder() {
        return builder;
    }

    Items items() {
        return items;
    }

    // ------------------------------------------------------------------ text

    /** A configured message with {name} style placeholders filled in, colours applied. */
    String fmt(String key, String... pairs) {
        String s = cfg.msg(key);
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            s = s.replace("{" + pairs[i] + "}", pairs[i + 1]);
        }
        return ColorText.translate(s);
    }

    void tell(CommandSender to, String key, String... pairs) {
        if (to != null) {
            to.sendMessage(fmt(key, pairs));
        }
    }

    // ------------------------------------------------------------------ finding

    World worldOf(Gate g) {
        return Bukkit.getWorld(g.world);
    }

    /** The registered gate nearest to a spot within the radius (distance to the middle of the opening), or null. */
    Gate gateNear(org.bukkit.Location at, int radius) {
        if (at == null || at.getWorld() == null) {
            return null;
        }
        Gate best = null;
        double bestD = Double.MAX_VALUE;
        double limit = (double) radius * radius;
        for (Gate g : store.gates()) {
            if (!g.world.equals(at.getWorld().getName())) {
                continue;
            }
            double dx = g.ax + 0.5 - at.getX();
            double dy = g.ay + 4.0 - at.getY();
            double dz = g.az + 0.5 - at.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d <= limit && d < bestD) {
                bestD = d;
                best = g;
            }
        }
        return best;
    }

    /** The gate a dialing device belongs to, or null. */
    Gate gateOfDhd(Block b) {
        GateStore.Dhd d = store.dhdAt(b.getWorld().getName(), b.getX(), b.getY(), b.getZ());
        return d == null ? null : store.gate(d.gateId());
    }

    // ------------------------------------------------------------------ who may do what

    boolean isAdmin(CommandSender s) {
        return s != null && s.hasPermission(ADMIN);
    }

    boolean owns(Player p, Gate g) {
        return g.owner != null && g.owner.equals(p.getUniqueId());
    }

    /** May this player dial this gate's address? */
    boolean knows(Player p, Gate g) {
        if (!cfg.requireKnown || isAdmin(p) || owns(p, g)) {
            return true;
        }
        if (g.isPlanet()) {
            Planet pl = planets.get(g.planet);
            if (pl != null && pl.starter()) {
                return true;
            }
        } else if (g.common) {
            return true;
        }
        return store.knows(p.getUniqueId(), g.id);
    }

    /** Every gate the player could dial from here, by name. */
    List<Gate> dialable(Player p, Gate from) {
        List<Gate> out = new ArrayList<>();
        for (Gate g : store.gates()) {
            if (g != from && knows(p, g)) {
                out.add(g);
            }
        }
        out.sort(Comparator.comparing((Gate g) -> g.name.toLowerCase(Locale.ROOT)));
        return out;
    }

    /** Tells the player a new address, once. */
    boolean learn(Player p, Gate g) {
        boolean fresh = store.learn(p.getUniqueId(), g.id);
        if (fresh) {
            tell(p, "learned", "name", g.name, "address", g.address.names());
        }
        return fresh;
    }

    int ownedBy(UUID owner) {
        int n = 0;
        for (Gate g : store.gates()) {
            if (owner.equals(g.owner) && !g.isPlanet()) {
                n++;
            }
        }
        return n;
    }

    // ------------------------------------------------------------------ making gates

    Gate create(String world, int ax, int ay, int az, BlockFace facing, UUID owner, String name) {
        Gate g = new Gate(store.newId(rng), uniqueName(name), world, ax, ay, az, facing, store.newAddress(rng));
        g.owner = owner;
        g.createdAt = clock.getAsLong();
        store.add(g);
        return g;
    }

    String uniqueName(String wanted) {
        String base = wanted == null || wanted.isBlank() ? "Gate" : wanted.trim();
        String name = base;
        int n = 2;
        while (store.nameTaken(name, null)) {
            name = base + " " + n++;
        }
        return name;
    }

    /** A player placed a dialing device: link it to the gate nearby, making the gate known to the server if it is new. */
    Placed placeDhd(Player p, Block block) {
        World w = block.getWorld();
        List<GateShape.Found> found = GateShape.find(GateBuilder.reader(w), block.getX(), block.getY(), block.getZ(), cfg.range, cfg.materials);
        GateShape.Found best = null;
        boolean blocked = false;
        double bestD = Double.MAX_VALUE;
        for (GateShape.Found f : found) {
            if (f.status() == GateShape.Status.BLOCKED) {
                blocked = true;
                continue;
            }
            double dx = f.ax() + 0.5 - block.getX();
            double dy = f.ay() + 4.0 - block.getY();
            double dz = f.az() + 0.5 - block.getZ();
            double d = dx * dx + dy * dy + dz * dz;
            if (d < bestD) {
                bestD = d;
                best = f;
            }
        }
        if (best == null) {
            tell(p, blocked ? "dhd-blocked" : "dhd-no-gate", "range", String.valueOf(cfg.range));
            return blocked ? Placed.BLOCKED : Placed.NO_GATE;
        }
        Gate gate = store.gateAt(w.getName(), best.ax(), best.ay(), best.az());
        if (gate == null) {
            if (!isAdmin(p) && ownedBy(p.getUniqueId()) >= cfg.maxGatesPerPlayer) {
                tell(p, "gate-limit", "n", String.valueOf(cfg.maxGatesPerPlayer));
                return Placed.LIMIT;
            }
            BlockFace front = GateShape.sideOf(best.ax(), best.az(), best.facing(), block.getX(), block.getZ());
            gate = create(w.getName(), best.ax(), best.ay(), best.az(), front, p.getUniqueId(), p.getName() + "'s gate");
        } else if (!owns(p, gate) && !isAdmin(p)) {
            tell(p, "not-owner");
            return Placed.NOT_OWNER;
        }
        store.addDhd(new GateStore.Dhd(w.getName(), block.getX(), block.getY(), block.getZ(), gate.id, p.getUniqueId()));
        store.learn(p.getUniqueId(), gate.id);
        tell(p, "dhd-placed", "name", gate.name, "address", gate.address.names());
        return Placed.OK;
    }

    /** Server-side: a dialing device for a gate with no player involved (planet arrival areas). */
    void addDhd(Gate g, int x, int y, int z) {
        store.addDhd(new GateStore.Dhd(g.world, x, y, z, g.id, null));
    }

    // ------------------------------------------------------------------ changing gates

    boolean rename(Player p, Gate g, String name) {
        if (!owns(p, g) && !isAdmin(p)) {
            tell(p, "not-owner");
            return false;
        }
        String clean = name == null ? "" : name.trim();
        if (clean.isEmpty() || clean.length() > 32) {
            tell(p, "bad-name");
            return false;
        }
        if (store.nameTaken(clean, g)) {
            tell(p, "name-taken");
            return false;
        }
        g.name = clean;
        store.markDirty();
        tell(p, "renamed", "name", clean);
        return true;
    }

    /** Closes or opens the iris of an idle or active gate. The opening is refilled by the caller's wormhole state. */
    void setIris(Gate g, boolean closed) {
        if (g.iris == closed) {
            return;
        }
        g.iris = closed;
        store.markDirty();
        World w = worldOf(g);
        if (w != null) {
            builder.fill(w, g, closed ? cfg.materials.iris : Material.AIR);
        }
    }

    /** Deletes a gate from the book; with {@code clear} also takes the gate itself and its dialing devices out of the world. */
    void remove(Gate g, boolean clear) {
        beforeRemove.accept(g);
        World w = worldOf(g);
        if (w != null) {
            builder.fill(w, g, Material.AIR);
            if (clear) {
                for (GateShape.Cell c : GateShape.RING) {
                    int[] p = GateShape.pos(g.ax, g.ay, g.az, g.facing, c);
                    w.getBlockAt(p[0], p[1], p[2]).setType(Material.AIR, false);
                }
                for (GateStore.Dhd d : store.dhdsOf(g.id)) {
                    w.getBlockAt(d.x(), d.y(), d.z()).setType(Material.AIR, false);
                }
            }
        }
        store.remove(g.id);
    }
}
