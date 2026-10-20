package com.nexuscraft.nexusstargate;

import org.bukkit.Material;

/** The blocks a gate is made of (from config.yml, checked). */
final class GateMaterials {

    Material ring = Material.POLISHED_BLACKSTONE_BRICKS;
    Material chevron = Material.RED_NETHER_BRICKS;
    Material lit = Material.SHROOMLIGHT;
    /** The shimmering surface of an open gate. */
    Material horizon = Material.NETHER_PORTAL;
    /** What fills the gate while its iris is closed. */
    Material iris = Material.IRON_BLOCK;
    /** The dialing device. */
    Material dhd = Material.CRYING_OBSIDIAN;

    /** True for any block the inside of a gate may hold. */
    boolean clear(Material m) {
        return m == null || m.isAir() || m == horizon || m == iris || m == Material.NETHER_PORTAL;
    }

    boolean isChevron(Material m) {
        return m == chevron || m == lit;
    }
}
