package com.nexuscraft.nexusstargate;

/** One gate world as written in planets.yml. */
record Planet(String id, String name, String designation, String style, long seed, boolean starter, long time, boolean mobs, String blurb) {

    /** The name of the Bukkit world made for this planet. */
    String worldName() {
        return "sg_" + id;
    }
}
