package com.nexuscraft.nexusstargate;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.function.Consumer;

/** Makes the gate worlds, keeps them set up the same way every start, and puts a gate and a dialing device on each. */
final class PlanetService {

    static final int PLATFORM_RADIUS = 8;
    static final int DHD_DISTANCE = 6;

    private final GateService gates;
    private final StargateConfig cfg;
    private final GateStore store;
    private final PlanetBook book;
    private final Consumer<String> warn;

    PlanetService(GateService gates, Consumer<String> warn) {
        this.gates = gates;
        this.cfg = gates.cfg();
        this.store = gates.store();
        this.book = gates.planets();
        this.warn = warn;
    }

    static String gateId(Planet p) {
        return "p-" + p.id();
    }

    /** Creates (or loads) every planet's world and makes sure each has its gate; gates of planets that are gone are removed. */
    void syncAll() {
        List<Gate> stale = new ArrayList<>();
        for (Gate g : store.gates()) {
            if (g.isPlanet() && book.get(g.planet) == null) {
                stale.add(g);
            }
        }
        for (Gate g : stale) {
            gates.remove(g, false);
        }
        for (Planet p : book.all()) {
            try {
                ensure(p);
            } catch (RuntimeException ex) {
                warn.accept("Could not set up the world for planet '" + p.id() + "': " + ex);
            }
        }
    }

    /** The planet's world, created if needed. */
    World world(Planet p) {
        World w = Bukkit.getWorld(p.worldName());
        if (w != null) {
            return w;
        }
        Style style = Style.get(p.style());
        PlanetGenerator generator = new PlanetGenerator(style, p.seed());
        w = new WorldCreator(p.worldName())
                .environment(World.Environment.NORMAL)
                .generator(generator)
                .seed(p.seed())
                .generateStructures(false)
                .createWorld();
        return w;
    }

    World ensure(Planet p) {
        World w = world(p);
        if (w == null) {
            warn.accept("The server would not make a world for planet '" + p.id() + "'.");
            return null;
        }
        Style style = Style.get(p.style());
        rules(w, p, style);
        Terrain terrain = terrainOf(w, p, style);
        int ground = terrain.originGround();
        w.setSpawnLocation(0, ground + 1, DHD_DISTANCE + 3);
        w.loadChunk(0, 0, true);
        Gate g = store.gate(gateId(p));
        if (g != null && !g.name.equals(p.name())) {
            g.name = p.name();
            store.markDirty();
        }
        boolean fine = g != null && store.planetBuilt(p.id()) && gates.builder().check(w, g) == GateShape.Status.VALID;
        if (!fine) {
            build(w, p, style, ground);
        }
        if (cfg.preload) {
            for (int cx = -4; cx <= 4; cx++) {
                for (int cz = -4; cz <= 4; cz++) {
                    w.loadChunk(cx, cz, true);
                }
            }
        }
        return w;
    }

    private Terrain terrainOf(World w, Planet p, Style style) {
        if (w.getGenerator() instanceof PlanetGenerator pg) {
            return pg.terrain();
        }
        return new Terrain(style, p.seed());
    }

    /** The string game rule calls are deprecated but are the only ones that take both old and new rule names. */
    @SuppressWarnings("deprecation")
    private void rules(World w, Planet p, Style style) {
        for (String rule : new String[]{"doDaylightCycle", "daylight_cycle", "doWeatherCycle", "weather_cycle"}) {
            w.setGameRuleValue(rule, "false");
        }
        for (String rule : new String[]{"doMobSpawning", "mob_spawning"}) {
            w.setGameRuleValue(rule, String.valueOf(p.mobs()));
        }
        w.setGameRuleValue("spawnChunkRadius", "0");
        w.setTime(p.time());
        w.setStorm(style.storm);
        w.setThundering(false);
        w.getWorldBorder().setCenter(0.0, 0.0);
        w.getWorldBorder().setSize(cfg.borderRadius * 2.0);
    }

    /** Lays the arrival platform, the gate and the dialing device, and registers them. */
    void build(World w, Planet p, Style style, int ground) {
        GateBuilder b = gates.builder();
        b.clearAbove(w, 0, ground + 1, 0, PLATFORM_RADIUS + 1, 16);
        b.platform(w, 0, ground, 0, PLATFORM_RADIUS, style.palette.floor(), style.palette.wall());
        b.buildRing(w, 0, ground, 0, BlockFace.SOUTH);
        Gate g = store.gate(gateId(p));
        if (g == null) {
            Random rng = new Random(p.seed() * 31L + 7L);
            g = new Gate(gateId(p), p.name(), w.getName(), 0, ground, 0, BlockFace.SOUTH,
                    GateAddress.random(rng, a -> store.byAddress(a) != null));
            g.planet = p.id();
            g.common = true;
            g.createdAt = System.currentTimeMillis();
            store.add(g);
        }
        for (GateStore.Dhd d : store.dhdsOf(g.id)) {
            store.removeDhd(d.world(), d.x(), d.y(), d.z());
        }
        w.getBlockAt(0, ground + 1, DHD_DISTANCE).setType(cfg.materials.dhd, false);
        gates.addDhd(g, 0, ground + 1, DHD_DISTANCE);
        store.setPlanetBuilt(p.id(), true);
    }

    /** Throws the arrival area away and builds it again (after an admin edit or a changed seed). */
    boolean rebuild(Planet p) {
        World w = world(p);
        if (w == null) {
            return false;
        }
        Style style = Style.get(p.style());
        w.loadChunk(0, 0, true);
        build(w, p, style, terrainOf(w, p, style).originGround());
        return true;
    }

    /** Where a player appears when sent to a planet: in front of the dialing device, facing the gate. */
    Location spawnOf(Planet p) {
        World w = ensure(p);
        if (w == null) {
            return null;
        }
        Style style = Style.get(p.style());
        int ground = terrainOf(w, p, style).originGround();
        return new Location(w, 0.5, ground + 1, DHD_DISTANCE + 3.5, 180f, 0f);
    }

    boolean send(Player player, Planet p) {
        Location at = spawnOf(p);
        return at != null && player.teleport(at);
    }
}
