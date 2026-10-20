package com.nexuscraft.nexusstargate;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;

import java.util.List;
import java.util.Map;
import java.util.Random;

/** The chunk generator for a gate world: everything comes from {@link Terrain}. */
final class PlanetGenerator extends ChunkGenerator {

    private static final Map<String, Biome> BIOMES = Map.ofEntries(
            Map.entry("DESERT", Biome.DESERT),
            Map.entry("TAIGA", Biome.TAIGA),
            Map.entry("WINDSWEPT_HILLS", Biome.WINDSWEPT_HILLS),
            Map.entry("SNOWY_PLAINS", Biome.SNOWY_PLAINS),
            Map.entry("JUNGLE", Biome.JUNGLE),
            Map.entry("BASALT_DELTAS", Biome.BASALT_DELTAS),
            Map.entry("PLAINS", Biome.PLAINS),
            Map.entry("FOREST", Biome.FOREST),
            Map.entry("SWAMP", Biome.SWAMP));

    private final Style style;
    private final Terrain terrain;

    PlanetGenerator(Style style, long seed) {
        this.style = style;
        this.terrain = new Terrain(style, seed);
    }

    static Biome biomeOf(String name) {
        Biome b = name == null ? null : BIOMES.get(name);
        return b == null ? Biome.PLAINS : b;
    }

    Terrain terrain() {
        return terrain;
    }

    @Override
    public void generateNoise(WorldInfo worldInfo, Random random, int chunkX, int chunkZ, ChunkData chunkData) {
        terrain.fill(chunkData, chunkX, chunkZ);
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(WorldInfo worldInfo) {
        return new PlanetBiomes(biomeOf(style.biome));
    }

    @Override
    public Location getFixedSpawnLocation(World world, Random random) {
        return new Location(world, 0.5, terrain.originGround() + 1, 9.5);
    }

    /** One biome everywhere. */
    static final class PlanetBiomes extends BiomeProvider {
        private final Biome biome;

        PlanetBiomes(Biome biome) {
            this.biome = biome;
        }

        @Override
        public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) {
            return biome;
        }

        @Override
        public List<Biome> getBiomes(WorldInfo worldInfo) {
            return List.of(biome);
        }
    }
}
