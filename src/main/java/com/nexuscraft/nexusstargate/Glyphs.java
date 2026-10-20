package com.nexuscraft.nexusstargate;

import org.bukkit.Material;

import java.util.List;
import java.util.Locale;

/**
 * The 36 glyphs on the dialing menu. They are named after real constellations (public names, nothing borrowed),
 * and each one has its own item so the menu is readable without a resource pack.
 */
final class Glyphs {

    static final int COUNT = 36;
    /** How many glyphs make up an address before the point of origin. */
    static final int LENGTH = 6;

    static final List<String> NAMES = List.of(
            "Aquarius", "Aries", "Auriga", "Bootes", "Cancer", "Capricornus", "Centaurus", "Cetus", "Corvus",
            "Crater", "Cygnus", "Draco", "Eridanus", "Gemini", "Hydra", "Leo", "Libra", "Lynx",
            "Lyra", "Monoceros", "Orion", "Pegasus", "Perseus", "Pisces", "Sagittarius", "Scorpius", "Serpens",
            "Taurus", "Virgo", "Aquila", "Delphinus", "Lacerta", "Norma", "Pyxis", "Scutum", "Sextans");

    /** One distinct, common item per glyph (index matches {@link #NAMES}). */
    static final List<Material> ICONS = List.of(
            Material.FEATHER, Material.CLOCK, Material.COMPASS, Material.PAPER, Material.BOOK, Material.GOLD_INGOT,
            Material.IRON_INGOT, Material.DIAMOND, Material.EMERALD, Material.REDSTONE, Material.LAPIS_LAZULI,
            Material.QUARTZ, Material.AMETHYST_SHARD, Material.COPPER_INGOT, Material.NETHERITE_SCRAP,
            Material.PRISMARINE_SHARD, Material.PRISMARINE_CRYSTALS, Material.GLOWSTONE_DUST, Material.BLAZE_POWDER,
            Material.GHAST_TEAR, Material.ENDER_EYE, Material.ENDER_PEARL, Material.NAUTILUS_SHELL,
            Material.HEART_OF_THE_SEA, Material.ECHO_SHARD, Material.DISC_FRAGMENT_5, Material.SPIDER_EYE,
            Material.SLIME_BALL, Material.MAGMA_CREAM, Material.FIRE_CHARGE, Material.SNOWBALL, Material.CLAY_BALL,
            Material.BRICK, Material.NETHER_BRICK, Material.FLINT, Material.BONE);

    private Glyphs() {
    }

    static String name(int glyph) {
        return glyph >= 0 && glyph < COUNT ? NAMES.get(glyph) : "?";
    }

    static Material icon(int glyph) {
        return glyph >= 0 && glyph < COUNT ? ICONS.get(glyph) : Material.PAPER;
    }

    /** The glyph for a name (any case), or -1. */
    static int indexOf(String name) {
        if (name == null) {
            return -1;
        }
        String n = name.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < COUNT; i++) {
            if (NAMES.get(i).toLowerCase(Locale.ROOT).equals(n)) {
                return i;
            }
        }
        return -1;
    }

    /** The point-of-origin glyph for a world: the same world always shows the same one. */
    static int origin(String worldName) {
        int h = 17;
        String n = worldName == null ? "" : worldName;
        for (int i = 0; i < n.length(); i++) {
            h = h * 31 + n.charAt(i);
        }
        return Math.floorMod(h, COUNT);
    }
}
