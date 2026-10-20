package com.nexuscraft.nexusstargate;

import org.bukkit.block.BlockFace;

import java.util.UUID;

/** One gate: where it stands, what it is called, how it is dialed. Mutable, owned by {@link GateStore}. */
final class Gate {

    final String id;
    String name;
    final String world;
    final int ax;
    final int ay;
    final int az;
    /** Which way the front faces (where travellers arrive). */
    BlockFace facing;
    GateAddress address;
    /** Null for gates made by the server itself. */
    UUID owner;
    /** The planet this gate belongs to, or empty. */
    String planet = "";
    /** Planet gates and gates the server built: everyone may use their address. */
    boolean common;
    /** Closed to arrivals (and travellers just bounce). */
    boolean iris;
    long createdAt;

    Gate(String id, String name, String world, int ax, int ay, int az, BlockFace facing, GateAddress address) {
        this.id = id;
        this.name = name;
        this.world = world;
        this.ax = ax;
        this.ay = ay;
        this.az = az;
        this.facing = facing;
        this.address = address;
    }

    boolean isPlanet() {
        return !planet.isEmpty();
    }

    /** The spot a traveller arrives at: just in front, centred, standing on the floor of the opening. */
    double[] arrival(double distance) {
        return new double[]{
                ax + 0.5 + facing.getModX() * distance,
                ay + 1,
                az + 0.5 + facing.getModZ() * distance};
    }

    /** Which way travellers look on arrival (yaw degrees as Minecraft counts them). */
    float arrivalYaw() {
        return switch (facing) {
            case SOUTH -> 0f;
            case WEST -> 90f;
            case NORTH -> 180f;
            default -> -90f;
        };
    }
}
