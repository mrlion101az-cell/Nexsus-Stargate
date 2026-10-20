package com.nexuscraft.nexusstargate;

import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The public API on top of the services. */
final class StargateApiImpl implements StargateApi {

    private final GateService gates;
    private final WormholeService wormholes;

    StargateApiImpl(GateService gates, WormholeService wormholes) {
        this.gates = gates;
        this.wormholes = wormholes;
    }

    private static GateInfo info(Gate g) {
        return g == null ? null : new GateInfo(g.id, g.name, g.world, g.ax, g.ay, g.az, g.address.code(), g.planet);
    }

    @Override
    public List<GateInfo> gates() {
        List<GateInfo> out = new ArrayList<>();
        for (Gate g : gates.store().gates()) {
            out.add(info(g));
        }
        return out;
    }

    @Override
    public GateInfo gate(String idOrName) {
        return info(gates.store().find(idOrName));
    }

    @Override
    public GateInfo gateNear(Location at, int radius) {
        return info(gates.gateNear(at, radius));
    }

    @Override
    public boolean knows(UUID player, String gateId) {
        Gate g = gates.store().gate(gateId);
        if (player == null || g == null) {
            return false;
        }
        org.bukkit.entity.Player p = org.bukkit.Bukkit.getPlayer(player);
        if (p != null) {
            return gates.knows(p, g);
        }
        return gates.store().knows(player, gateId) || (g.owner != null && g.owner.equals(player));
    }

    @Override
    public boolean learn(UUID player, String gateId) {
        return player != null && gates.store().learn(player, gateId);
    }

    @Override
    public boolean isActive(String gateId) {
        return wormholes.busy(gates.store().gate(gateId));
    }

    @Override
    public boolean dial(String fromGateId, String toGateId) {
        Gate from = gates.store().gate(fromGateId);
        Gate to = gates.store().gate(toGateId);
        return from != null && to != null && wormholes.dial(null, from, to) == WormholeService.Dial.STARTED;
    }

    @Override
    public boolean close(String gateId) {
        Gate g = gates.store().gate(gateId);
        return g != null && wormholes.close(g, "wormhole-closed");
    }

    @Override
    public List<String> planets() {
        List<String> out = new ArrayList<>();
        for (Planet p : gates.planets().all()) {
            out.add(p.id());
        }
        return out;
    }

    @Override
    public String planetWorld(String planetId) {
        Planet p = gates.planets().get(planetId);
        return p == null ? "" : p.worldName();
    }
}
