package com.nexuscraft.nexusstargate;

import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Dialing and wormholes. One timer calls {@link #tick()} every {@link #STEP} ticks; everything here counts in ticks
 * from that, so a test can run a whole wormhole by calling tick() itself.
 */
final class WormholeService {

    /** Server ticks between two calls to {@link #tick()}. */
    static final int STEP = 2;
    static final int CHEVRONS = 7;
    /** Ticks an entity that just travelled is left alone, so it cannot be sent straight back. */
    static final int GRACE = 40;

    enum Dial { STARTED, SELF, UNKNOWN, BUSY, COOLDOWN, BROKEN, BLOCKED, IRIS, OFFLINE }

    enum Phase { DIALING, OPEN }

    /** One wormhole, from the dialing gate to the far gate. */
    static final class Wormhole {
        final Gate from;
        final Gate to;
        final UUID dialer;
        Phase phase = Phase.DIALING;
        int locked;
        int ticks;
        int sinceTraffic;
        int openFor;
        int travellers;
        boolean warned;

        Wormhole(Gate from, Gate to, UUID dialer) {
            this.from = from;
            this.to = to;
            this.dialer = dialer;
        }
    }

    private final Plugin plugin;
    private final GateService gates;
    private final StargateConfig cfg;
    private final GateStore store;
    private final GateBuilder builder;

    private final Map<String, Wormhole> byGate = new HashMap<>();
    private final Map<String, Long> cooldownUntil = new HashMap<>();
    private final Map<UUID, Long> graceUntil = new HashMap<>();
    private final Map<UUID, Long> toldUntil = new HashMap<>();
    private long now;

    WormholeService(Plugin plugin, GateService gates) {
        this.plugin = plugin;
        this.gates = gates;
        this.cfg = gates.cfg();
        this.store = gates.store();
        this.builder = gates.builder();
        gates.beforeRemove = this::closeFor;
    }

    // ------------------------------------------------------------------ asking

    Wormhole of(Gate g) {
        return g == null ? null : byGate.get(g.id);
    }

    boolean busy(Gate g) {
        return of(g) != null;
    }

    int active() {
        return new LinkedHashSet<>(byGate.values()).size();
    }

    long now() {
        return now;
    }

    // ------------------------------------------------------------------ dialing

    /**
     * Starts dialing {@code to} from {@code from}. The player is told if it cannot start (null for dialing by another
     * plugin). Things only the far end can answer, such as a busy or missing gate, come out at the last chevron.
     */
    Dial dial(Player p, Gate from, Gate to) {
        if (from == null || to == null) {
            return Dial.UNKNOWN;
        }
        if (from == to) {
            gates.tell(p, "dial-self");
            return Dial.SELF;
        }
        if (p != null && !gates.knows(p, to)) {
            gates.tell(p, "dial-not-known");
            return Dial.UNKNOWN;
        }
        if (busy(from)) {
            gates.tell(p, "dial-busy");
            return Dial.BUSY;
        }
        long wait = cooldownUntil.getOrDefault(from.id, 0L) - now;
        if (wait > 0) {
            gates.tell(p, "dial-cooldown", "n", String.valueOf((wait + 19) / 20));
            return Dial.COOLDOWN;
        }
        World w = gates.worldOf(from);
        if (w == null) {
            gates.tell(p, "gate-broken");
            return Dial.OFFLINE;
        }
        if (from.iris) {
            gates.tell(p, "iris-closed");
            return Dial.IRIS;
        }
        load(w, from);
        GateShape.Status st = builder.check(w, from);
        if (st == GateShape.Status.RING_BROKEN) {
            gates.tell(p, "gate-broken");
            return Dial.BROKEN;
        }
        if (st == GateShape.Status.BLOCKED) {
            gates.tell(p, "dhd-blocked");
            return Dial.BLOCKED;
        }
        Wormhole wh = new Wormhole(from, to, p == null ? null : p.getUniqueId());
        byGate.put(from.id, wh);
        gates.tell(p, "dial-start", "address", to.address.names());
        return Dial.STARTED;
    }

    private void load(World w, Gate g) {
        w.loadChunk(g.ax >> 4, g.az >> 4, true);
    }

    // ------------------------------------------------------------------ the clock

    void tick() {
        now += STEP;
        for (Wormhole wh : new LinkedHashSet<>(byGate.values())) {
            if (wh.phase == Phase.DIALING) {
                dialing(wh);
            } else {
                open(wh);
            }
        }
        graceUntil.values().removeIf(t -> t < now);
        toldUntil.values().removeIf(t -> t < now);
    }

    private void dialing(Wormhole wh) {
        World w = gates.worldOf(wh.from);
        if (w == null || store.gate(wh.from.id) != wh.from) {
            byGate.remove(wh.from.id);
            return;
        }
        wh.ticks += STEP;
        if (wh.ticks < cfg.chevronTicks) {
            return;
        }
        wh.ticks = 0;
        wh.locked++;
        builder.light(w, wh.from, wh.locked, false);
        Location at = centre(wh.from, w);
        w.playSound(at, wh.locked == CHEVRONS ? Sound.BLOCK_NOTE_BLOCK_BELL : Sound.BLOCK_NOTE_BLOCK_CHIME, 1.0f, 0.6f + 0.1f * wh.locked);
        if (wh.locked == CHEVRONS - 1 && !wh.warned) {
            wh.warned = true;
            for (Player near : playersNear(wh.from, cfg.warnRadius)) {
                gates.tell(near, "stand-clear");
            }
        }
        if (wh.locked >= CHEVRONS) {
            connect(wh, w);
        }
    }

    /** The seventh chevron: either the far gate answers or the dial fails. */
    private void connect(Wormhole wh, World w) {
        Gate to = wh.to;
        Player dialer = wh.dialer == null ? null : org.bukkit.Bukkit.getPlayer(wh.dialer);
        World dw = store.gate(to.id) == to ? gates.worldOf(to) : null;
        String fail = null;
        if (dw == null) {
            fail = "dial-unknown";
        } else if (busy(to)) {
            fail = "dial-far-busy";
        } else {
            load(dw, to);
            if (builder.check(dw, to) != GateShape.Status.VALID) {
                fail = "dial-unknown";
            }
        }
        if (fail != null) {
            byGate.remove(wh.from.id);
            builder.dark(w, wh.from);
            cooldownUntil.put(wh.from.id, now + cfg.cooldownSeconds * 20L);
            w.playSound(centre(wh.from, w), Sound.BLOCK_FIRE_EXTINGUISH, 1.0f, 0.8f);
            gates.tell(dialer, fail);
            return;
        }
        wh.phase = Phase.OPEN;
        wh.sinceTraffic = 0;
        wh.openFor = 0;
        byGate.put(to.id, wh);
        ticket(w, wh.from, true);
        ticket(dw, to, true);
        builder.light(w, wh.from, 0, true);
        builder.light(dw, to, 0, true);
        builder.fill(w, wh.from, cfg.materials.horizon);
        if (!to.iris) {
            builder.fill(dw, to, cfg.materials.horizon);
        }
        kawoosh(wh.from, w);
        kawoosh(to, dw);
        w.playSound(centre(wh.from, w), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        dw.playSound(centre(to, dw), Sound.BLOCK_BEACON_ACTIVATE, 1.0f, 1.4f);
        gates.tell(dialer, "wormhole-open", "name", to.name);
        for (Player near : playersNear(to, cfg.warnRadius)) {
            near.sendTitle(ColorText.translate(cfg.msg("wormhole-incoming")), ColorText.translate(cfg.msg("wormhole-incoming-sub")), 5, 40, 10);
        }
    }

    private void open(Wormhole wh) {
        World w = gates.worldOf(wh.from);
        World dw = gates.worldOf(wh.to);
        if (w == null || dw == null) {
            close(wh, null);
            return;
        }
        wh.openFor += STEP;
        wh.sinceTraffic += STEP;
        if (wh.openFor >= cfg.maxSeconds * 20) {
            close(wh, "wormhole-timeout");
            return;
        }
        if (wh.sinceTraffic >= cfg.idleSeconds * 20) {
            close(wh, wh.travellers == 0 ? "wormhole-idle" : "wormhole-closed");
            return;
        }
        for (Entity e : near(wh.from, w)) {
            if (!eligible(e) || graced(e)) {
                continue;
            }
            if (wh.to.iris) {
                bounce(e, wh.from);
                continue;
            }
            send(e, wh, w, dw);
        }
        for (Entity e : near(wh.to, dw)) {
            if (e instanceof Player p && !graced(e) && !told(p)) {
                gates.tell(p, "one-way");
            }
        }
    }

    // ------------------------------------------------------------------ travel

    private List<Entity> near(Gate g, World w) {
        boolean x = GateShape.alongX(g.facing);
        Location centre = new Location(w, g.ax + 0.5, g.ay + 4.5, g.az + 0.5);
        double along = 2.6;
        double thin = 0.6;
        return new ArrayList<>(w.getNearbyEntities(centre, x ? along : thin, 3.6, x ? thin : along));
    }

    private boolean eligible(Entity e) {
        if (e instanceof Player p) {
            return p.getGameMode() != GameMode.SPECTATOR && !p.isDead();
        }
        if (cfg.traffic.equals("players")) {
            return false;
        }
        if (e instanceof ArmorStand) {
            return false;
        }
        return e instanceof LivingEntity || e instanceof Item;
    }

    private boolean graced(Entity e) {
        return graceUntil.getOrDefault(e.getUniqueId(), 0L) > now;
    }

    /** True the first time within a short while; keeps messages from repeating every tick. */
    private boolean told(Player p) {
        boolean again = toldUntil.getOrDefault(p.getUniqueId(), 0L) > now;
        if (!again) {
            toldUntil.put(p.getUniqueId(), now + 60);
        }
        return again;
    }

    private void bounce(Entity e, Gate g) {
        e.setVelocity(new Vector(g.facing.getModX() * 0.8, 0.25, g.facing.getModZ() * 0.8));
        if (e instanceof Player p && !told(p)) {
            gates.tell(p, "iris-closed");
        }
    }

    private void send(Entity e, Wormhole wh, World w, World dw) {
        Gate to = wh.to;
        double[] a = to.arrival(cfg.arrivalDistance);
        load(dw, to);
        Location dest = new Location(dw, a[0], a[1], a[2], to.arrivalYaw(), 0f);
        graceUntil.put(e.getUniqueId(), now + GRACE);
        Location was = e.getLocation();
        e.teleport(dest);
        e.setFallDistance(0f);
        wh.travellers++;
        wh.sinceTraffic = 0;
        w.playSound(was, Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 0.6f);
        dw.playSound(dest, Sound.BLOCK_PORTAL_TRAVEL, 0.25f, 1.6f);
        dw.spawnParticle(Particle.CLOUD, dest, 12, 0.4, 0.8, 0.4);
        if (e instanceof Player p) {
            gates.learn(p, to);
            gates.learn(p, wh.from);
            if (to.isPlanet()) {
                Planet pl = gates.planets().get(to.planet);
                if (pl != null) {
                    p.sendTitle(ColorText.translate("&b" + pl.name()), ColorText.translate("&7" + pl.designation()), 10, 50, 20);
                }
            }
        }
    }

    // ------------------------------------------------------------------ effects

    private void kawoosh(Gate g, World w) {
        if (!cfg.kawooshEnabled) {
            return;
        }
        boolean x = GateShape.alongX(g.facing);
        Location centre = new Location(w, g.ax + 0.5 + g.facing.getModX() * 2.2, g.ay + 4.5, g.az + 0.5 + g.facing.getModZ() * 2.2);
        double lateral = 3.2;
        double deep = 1.8;
        w.spawnParticle(Particle.SPLASH, centre, 40, 1.2, 2.0, 1.2);
        for (Entity e : w.getNearbyEntities(centre, x ? lateral : deep, 4.5, x ? deep : lateral)) {
            if (!(e instanceof LivingEntity living) || e instanceof ArmorStand) {
                continue;
            }
            if (e instanceof Player p && (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR)) {
                continue;
            }
            if (cfg.kawooshDamage > 0) {
                living.damage(cfg.kawooshDamage, null);
            }
            e.setVelocity(new Vector(g.facing.getModX() * cfg.kawooshKnockback, 0.4, g.facing.getModZ() * cfg.kawooshKnockback));
        }
    }

    private Location centre(Gate g, World w) {
        return new Location(w, g.ax + 0.5, g.ay + 4.5, g.az + 0.5);
    }

    private List<Player> playersNear(Gate g, double radius) {
        List<Player> out = new ArrayList<>();
        World w = gates.worldOf(g);
        if (w == null) {
            return out;
        }
        for (Player p : w.getPlayers()) {
            Location at = p.getLocation();
            double dx = at.getX() - (g.ax + 0.5);
            double dy = at.getY() - (g.ay + 4.5);
            double dz = at.getZ() - (g.az + 0.5);
            if (dx * dx + dy * dy + dz * dz <= radius * radius) {
                out.add(p);
            }
        }
        return out;
    }

    private void ticket(World w, Gate g, boolean on) {
        for (int cx = (g.ax >> 4) - 1; cx <= (g.ax >> 4) + 1; cx++) {
            for (int cz = (g.az >> 4) - 1; cz <= (g.az >> 4) + 1; cz++) {
                if (on) {
                    w.addPluginChunkTicket(cx, cz, plugin);
                } else {
                    w.removePluginChunkTicket(cx, cz, plugin);
                }
            }
        }
    }

    // ------------------------------------------------------------------ iris

    /** Closes or opens a gate's iris, keeping an open wormhole's surface right. */
    void iris(Gate g, boolean closed) {
        gates.setIris(g, closed);
        Wormhole wh = of(g);
        World w = gates.worldOf(g);
        if (!closed && wh != null && wh.phase == Phase.OPEN && w != null) {
            builder.fill(w, g, cfg.materials.horizon);
        }
        if (w != null) {
            w.playSound(centre(g, w), closed ? Sound.BLOCK_IRON_DOOR_CLOSE : Sound.BLOCK_IRON_DOOR_OPEN, 1.0f, 0.8f);
        }
    }

    // ------------------------------------------------------------------ closing

    /** Closes the wormhole this gate is part of, if any. */
    boolean close(Gate g, String messageKey) {
        Wormhole wh = of(g);
        if (wh == null) {
            return false;
        }
        close(wh, messageKey);
        return true;
    }

    void closeFor(Gate g) {
        close(g, null);
    }

    void closeAll() {
        for (Wormhole wh : new LinkedHashSet<>(byGate.values())) {
            close(wh, null);
        }
    }

    private void close(Wormhole wh, String messageKey) {
        byGate.remove(wh.from.id, wh);
        byGate.remove(wh.to.id, wh);
        World w = gates.worldOf(wh.from);
        World dw = gates.worldOf(wh.to);
        Set<Player> told = new LinkedHashSet<>();
        if (w != null) {
            end(wh.from, w);
            if (wh.phase == Phase.OPEN) {
                ticket(w, wh.from, false);
            }
            told.addAll(playersNear(wh.from, cfg.warnRadius));
        }
        if (dw != null && wh.phase == Phase.OPEN) {
            end(wh.to, dw);
            ticket(dw, wh.to, false);
            told.addAll(playersNear(wh.to, cfg.warnRadius));
        }
        cooldownUntil.put(wh.from.id, now + cfg.cooldownSeconds * 20L);
        if (messageKey != null) {
            for (Player p : told) {
                gates.tell(p, messageKey);
            }
        }
    }

    private void end(Gate g, World w) {
        if (store.gate(g.id) == g) {
            builder.fill(w, g, g.iris ? cfg.materials.iris : Material.AIR);
            builder.dark(w, g);
        }
        w.playSound(centre(g, w), Sound.BLOCK_BEACON_DEACTIVATE, 1.0f, 1.2f);
    }
}
