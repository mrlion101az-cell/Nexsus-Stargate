package com.nexuscraft.nexusstargate;

import org.bukkit.Material;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What a kind of gate world looks like: its blocks, its hills, its weather and the things standing on it.
 * Six are built in; planets.yml picks one by id for each planet.
 */
final class Style {

    enum Kind {
        PYRAMID(15, true), OBELISK(4, true), COLUMN_RING(11, true), DOME(11, true), FORTRESS(12, true), RUIN_WALL(8, true),
        SPIKE(3, false), SPRUCE(4, false), JUNGLE_TREE(5, false), DEAD_TREE(3, false);

        /** How far from its centre it reaches (blocks). */
        final int radius;
        /** Whether the ground under it is levelled first. */
        final boolean flattens;

        Kind(int radius, boolean flattens) {
            this.radius = radius;
            this.flattens = flattens;
        }
    }

    /** One kind of thing placed on a grid: one candidate per grid square, kept with this chance. */
    record Feature(Kind kind, int grid, double chance, int salt) {
    }

    /** The blocks the structures of a world are built from. */
    record Palette(Material wall, Material alt, Material cracked, Material floor, Material pillar, Material glow, Material accent) {
    }

    final String id;
    final String biome;
    final long time;
    final boolean storm;
    final Material surface;
    final Material subsurface;
    final Material deep;
    /** What lies at the water's edge. */
    final Material shore;
    /** An occasional patch on the surface (null for none). */
    final Material patch;
    final double patchThreshold;
    final int baseY;
    final double amplitude;
    final double scale;
    final int octaves;
    final boolean ridged;
    final double duneAmp;
    final double duneWave;
    /** Absolute height of the liquid (only used when {@link #liquid} is not null). */
    final int liquidLevel;
    final Material liquid;
    final Palette palette;
    final Material log;
    final Material leaves;
    final List<Feature> features;

    private Style(Builder b) {
        this.id = b.id;
        this.biome = b.biome;
        this.time = b.time;
        this.storm = b.storm;
        this.surface = b.surface;
        this.subsurface = b.subsurface;
        this.deep = b.deep;
        this.shore = b.shore == null ? b.surface : b.shore;
        this.patch = b.patch;
        this.patchThreshold = b.patchThreshold;
        this.baseY = b.baseY;
        this.amplitude = b.amplitude;
        this.scale = b.scale;
        this.octaves = b.octaves;
        this.ridged = b.ridged;
        this.duneAmp = b.duneAmp;
        this.duneWave = b.duneWave;
        this.liquidLevel = b.liquidLevel;
        this.liquid = b.liquid;
        this.palette = b.palette;
        this.log = b.log;
        this.leaves = b.leaves;
        this.features = List.copyOf(b.features);
    }

    private static final class Builder {
        String id;
        String biome = "PLAINS";
        long time = 6000;
        boolean storm;
        Material surface = Material.GRASS_BLOCK;
        Material subsurface = Material.DIRT;
        Material deep = Material.STONE;
        Material shore;
        Material patch;
        double patchThreshold = 0.5;
        int baseY = 66;
        double amplitude = 10;
        double scale = 80;
        int octaves = 4;
        boolean ridged;
        double duneAmp;
        double duneWave = 30;
        int liquidLevel = Integer.MIN_VALUE;
        Material liquid;
        Palette palette = new Palette(Material.STONE_BRICKS, Material.COBBLESTONE, Material.CRACKED_STONE_BRICKS, Material.SMOOTH_STONE,
                Material.CHISELED_STONE_BRICKS, Material.SEA_LANTERN, Material.CHISELED_STONE_BRICKS);
        Material log = Material.SPRUCE_LOG;
        Material leaves = Material.SPRUCE_LEAVES;
        final java.util.ArrayList<Feature> features = new java.util.ArrayList<>();

        Builder(String id) {
            this.id = id;
        }

        Builder with(Kind k, int grid, double chance) {
            features.add(new Feature(k, grid, chance, features.size() * 7919 + 13));
            return this;
        }

        Style build() {
            return new Style(this);
        }
    }

    /** The built-in styles by id, in a stable order. */
    static final Map<String, Style> BUILT_IN = new LinkedHashMap<>();

    static {
        Builder desert = new Builder("desert");
        desert.biome = "DESERT";
        desert.time = 6000;
        desert.surface = Material.SAND;
        desert.subsurface = Material.SAND;
        desert.deep = Material.SANDSTONE;
        desert.baseY = 66;
        desert.amplitude = 7;
        desert.scale = 90;
        desert.octaves = 3;
        desert.duneAmp = 5;
        desert.duneWave = 34;
        desert.palette = new Palette(Material.SMOOTH_SANDSTONE, Material.SANDSTONE, Material.CUT_SANDSTONE, Material.CUT_SANDSTONE,
                Material.CHISELED_SANDSTONE, Material.SEA_LANTERN, Material.GOLD_BLOCK);
        desert.log = Material.DARK_OAK_LOG;
        desert.with(Kind.PYRAMID, 170, 0.6).with(Kind.OBELISK, 96, 0.4).with(Kind.COLUMN_RING, 140, 0.35).with(Kind.DEAD_TREE, 14, 0.12);
        BUILT_IN.put("desert", desert.build());

        Builder forest = new Builder("forest");
        forest.biome = "TAIGA";
        forest.time = 3000;
        forest.surface = Material.GRASS_BLOCK;
        forest.subsurface = Material.DIRT;
        forest.shore = Material.GRAVEL;
        forest.baseY = 70;
        forest.amplitude = 14;
        forest.scale = 70;
        forest.octaves = 4;
        forest.liquidLevel = 62;
        forest.liquid = Material.WATER;
        forest.palette = new Palette(Material.STONE_BRICKS, Material.MOSSY_STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.POLISHED_ANDESITE,
                Material.CHISELED_STONE_BRICKS, Material.SEA_LANTERN, Material.CHISELED_STONE_BRICKS);
        forest.with(Kind.FORTRESS, 180, 0.55).with(Kind.COLUMN_RING, 130, 0.4).with(Kind.OBELISK, 150, 0.25).with(Kind.SPRUCE, 7, 0.55);
        BUILT_IN.put("forest", forest.build());

        Builder moor = new Builder("moor");
        moor.biome = "WINDSWEPT_HILLS";
        moor.time = 12800;
        moor.surface = Material.GRASS_BLOCK;
        moor.subsurface = Material.DIRT;
        moor.patch = Material.COARSE_DIRT;
        moor.patchThreshold = 0.25;
        moor.shore = Material.GRAVEL;
        moor.baseY = 68;
        moor.amplitude = 18;
        moor.scale = 80;
        moor.octaves = 4;
        moor.liquidLevel = 60;
        moor.liquid = Material.WATER;
        moor.palette = new Palette(Material.COBBLESTONE, Material.MOSSY_COBBLESTONE, Material.ANDESITE, Material.COBBLESTONE,
                Material.POLISHED_ANDESITE, Material.SEA_LANTERN, Material.POLISHED_ANDESITE);
        moor.log = Material.DARK_OAK_LOG;
        moor.with(Kind.COLUMN_RING, 96, 0.6).with(Kind.RUIN_WALL, 110, 0.45).with(Kind.OBELISK, 150, 0.3).with(Kind.DEAD_TREE, 16, 0.25);
        BUILT_IN.put("moor", moor.build());

        Builder ice = new Builder("ice");
        ice.biome = "SNOWY_PLAINS";
        ice.time = 6000;
        ice.storm = true;
        ice.surface = Material.SNOW_BLOCK;
        ice.subsurface = Material.SNOW_BLOCK;
        ice.deep = Material.PACKED_ICE;
        ice.patch = Material.BLUE_ICE;
        ice.patchThreshold = 0.35;
        ice.baseY = 72;
        ice.amplitude = 22;
        ice.scale = 110;
        ice.octaves = 4;
        ice.ridged = true;
        ice.palette = new Palette(Material.PACKED_ICE, Material.BLUE_ICE, Material.PACKED_ICE, Material.SMOOTH_QUARTZ,
                Material.QUARTZ_PILLAR, Material.SEA_LANTERN, Material.CHISELED_QUARTZ_BLOCK);
        ice.with(Kind.DOME, 200, 0.6).with(Kind.SPIKE, 24, 0.3).with(Kind.OBELISK, 160, 0.2);
        BUILT_IN.put("ice", ice.build());

        Builder jungle = new Builder("jungle");
        jungle.biome = "JUNGLE";
        jungle.time = 5000;
        jungle.surface = Material.GRASS_BLOCK;
        jungle.subsurface = Material.DIRT;
        jungle.patch = Material.MOSS_BLOCK;
        jungle.patchThreshold = 0.2;
        jungle.shore = Material.MUD;
        jungle.baseY = 68;
        jungle.amplitude = 16;
        jungle.scale = 60;
        jungle.octaves = 4;
        jungle.liquidLevel = 63;
        jungle.liquid = Material.WATER;
        jungle.palette = new Palette(Material.MOSSY_STONE_BRICKS, Material.STONE_BRICKS, Material.CRACKED_STONE_BRICKS, Material.MOSSY_COBBLESTONE,
                Material.CHISELED_STONE_BRICKS, Material.SEA_LANTERN, Material.CHISELED_STONE_BRICKS);
        jungle.log = Material.JUNGLE_LOG;
        jungle.leaves = Material.JUNGLE_LEAVES;
        jungle.with(Kind.PYRAMID, 190, 0.5).with(Kind.RUIN_WALL, 90, 0.5).with(Kind.COLUMN_RING, 120, 0.35).with(Kind.JUNGLE_TREE, 6, 0.6);
        BUILT_IN.put("jungle", jungle.build());

        Builder ash = new Builder("ash");
        ash.biome = "BASALT_DELTAS";
        ash.time = 14000;
        ash.surface = Material.BASALT;
        ash.subsurface = Material.BLACKSTONE;
        ash.deep = Material.BLACKSTONE;
        ash.patch = Material.MAGMA_BLOCK;
        ash.patchThreshold = 0.45;
        ash.shore = Material.BASALT;
        ash.baseY = 66;
        ash.amplitude = 20;
        ash.scale = 55;
        ash.octaves = 4;
        ash.ridged = true;
        ash.liquidLevel = 54;
        ash.liquid = Material.LAVA;
        ash.palette = new Palette(Material.POLISHED_BLACKSTONE_BRICKS, Material.BLACKSTONE, Material.POLISHED_BLACKSTONE, Material.POLISHED_BASALT,
                Material.CHISELED_POLISHED_BLACKSTONE, Material.SHROOMLIGHT, Material.CRYING_OBSIDIAN);
        ash.with(Kind.FORTRESS, 170, 0.6).with(Kind.OBELISK, 110, 0.4).with(Kind.SPIKE, 20, 0.35);
        BUILT_IN.put("ash", ash.build());
    }

    static Style get(String id) {
        return id == null ? null : BUILT_IN.get(id.toLowerCase(java.util.Locale.ROOT));
    }
}
