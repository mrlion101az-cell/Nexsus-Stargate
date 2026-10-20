package com.nexuscraft.nexusstargate;

import org.bukkit.Location;

import java.util.List;
import java.util.UUID;

/**
 * For other plugins. Get it with {@code Bukkit.getServicesManager().load(StargateApi.class)}. Everything is null-safe:
 * unknown ids and offline players just give empty or false answers.
 */
public interface StargateApi {

    /** A gate as other plugins see it. {@code planet} is empty for gates players built. */
    record GateInfo(String id, String name, String world, int x, int y, int z, String address, String planet) {
    }

    /** Every gate on the server. */
    List<GateInfo> gates();

    /** The gate with this id or name, or null. */
    GateInfo gate(String idOrName);

    /** The gate nearest to a spot within the radius, or null. */
    GateInfo gateNear(Location at, int radius);

    /** True if the player may dial that gate (owns it, was taught it, or it is open to everyone). */
    boolean knows(UUID player, String gateId);

    /** Teaches a player a gate's address. True if that was new to them. */
    boolean learn(UUID player, String gateId);

    /** True while a gate is dialing or has a wormhole open. */
    boolean isActive(String gateId);

    /** Starts a dial between two gates (no player needed, and no address knowledge asked). True if dialing started. */
    boolean dial(String fromGateId, String toGateId);

    /** Closes the wormhole a gate is part of. True if one was open or dialing. */
    boolean close(String gateId);

    /** The ids of the planets (gate worlds) on this server. */
    List<String> planets();

    /** The name of the world of a planet, or an empty string. */
    String planetWorld(String planetId);
}
