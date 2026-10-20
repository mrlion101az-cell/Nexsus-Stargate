package com.nexuscraft.nexusstargate;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Random;

public final class StargatePlugin extends JavaPlugin {

    private final StargateConfig cfg = new StargateConfig();
    private final PlanetBook book = new PlanetBook();
    private GateStore store;
    private GateService gates;
    private WormholeService wormholes;
    private PlanetService planetService;
    private Items items;
    private BukkitTask clock;
    private BukkitTask saver;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        saveResource("custom-planets.yml", false);
        cfg.load(getConfig());
        loadPlanets();
        File dir = getDataFolder();
        store = new GateStore(new File(dir, "gates.tsv"), new File(dir, "dhds.tsv"), new File(dir, "known.tsv"),
                new File(dir, "planets.tsv"), getLogger()::warning);
        store.load();
        items = new Items(cfg);
        gates = new GateService(cfg, store, book, new GateBuilder(cfg.materials), items, new Random(), System::currentTimeMillis);
        wormholes = new WormholeService(this, gates);
        planetService = new PlanetService(gates, getLogger()::warning);
        DialControl control = new DialControl(gates, wormholes);
        getServer().getServicesManager().register(StargateApi.class, new StargateApiImpl(gates, wormholes), this, ServicePriority.Normal);
        getServer().getPluginManager().registerEvents(new StargateListener(gates, wormholes, control), this);
        StargateCommand command = new StargateCommand(gates, wormholes, control, planetService, this::reload);
        getCommand("stargate").setExecutor(command);
        getCommand("stargate").setTabCompleter(command);
        items.registerRecipe();
        planetService.syncAll();
        clock = getServer().getScheduler().runTaskTimer(this, wormholes::tick, WormholeService.STEP, WormholeService.STEP);
        saver = getServer().getScheduler().runTaskTimer(this, store::flush, 6000L, 6000L);
        getLogger().info("NexusStargate enabled: " + store.gates().size() + " gates, " + book.size() + " planets.");
        warnAll();
    }

    @Override
    public void onDisable() {
        if (clock != null) {
            clock.cancel();
        }
        if (saver != null) {
            saver.cancel();
        }
        if (wormholes != null) {
            wormholes.closeAll();
        }
        if (items != null) {
            items.unregisterRecipe();
        }
        getServer().getServicesManager().unregisterAll(this);
        if (store != null) {
            store.flush();
        }
    }

    private void warnAll() {
        for (String w : book.warnings()) {
            getLogger().warning(w);
        }
        for (String w : cfg.warnings) {
            getLogger().warning(w);
        }
    }

    private void reload() {
        reloadConfig();
        cfg.load(getConfig());
        loadPlanets();
        items.registerRecipe();
        planetService.syncAll();
        warnAll();
    }

    /** The planets in the jar, then the server's own custom-planets.yml on top. */
    private void loadPlanets() {
        book.clear();
        try (InputStream in = getResource("planets.yml")) {
            if (in != null) {
                book.load(YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8)), "planets.yml");
            }
        } catch (java.io.IOException ex) {
            getLogger().warning("Could not read the built-in planets: " + ex.getMessage());
        }
        File custom = new File(getDataFolder(), "custom-planets.yml");
        if (custom.exists()) {
            book.load(YamlConfiguration.loadConfiguration(custom), "custom-planets.yml");
        }
    }
}
