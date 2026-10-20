package com.nexuscraft.nexusstargate;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Everything in config.yml, checked: a bad value falls back to something safe and adds a warning. */
final class StargateConfig {

    /** Every message the plugin says, with its built-in text (config.yml ships the same text). */
    static final Map<String, String> DEFAULT_MESSAGES = new LinkedHashMap<>();

    static {
        Map<String, String> m = DEFAULT_MESSAGES;
        m.put("dhd-placed", "&6Dialing device linked to &e{name}&6. This gate's address is &f{address}&6.");
        m.put("dhd-no-gate", "&cNo complete gate within {range} blocks. &7/stargate blueprint shows how to build one.");
        m.put("dhd-blocked", "&cThe gate nearby has something in its opening. Clear it first.");
        m.put("gate-limit", "&cYou already own {n} gates.");
        m.put("gate-broken", "&cThis gate is damaged and no longer works.");
        m.put("gate-removed", "&7The gate {name} has fallen silent for good.");
        m.put("dial-busy", "&cThis gate is already in use.");
        m.put("dial-far-busy", "&cChevron 7 will not lock. The far gate is busy.");
        m.put("dial-unknown", "&cChevron 7 will not lock. Nothing answers at that address.");
        m.put("dial-not-known", "&cYou do not know that address.");
        m.put("dial-self", "&cA gate cannot dial itself.");
        m.put("dial-cooldown", "&cThe gate is still recharging ({n}s).");
        m.put("dial-failed", "&cThe far gate could not be reached.");
        m.put("dial-start", "&7Dialing {address}…");
        m.put("wormhole-open", "&bThe wormhole is open to &f{name}&b.");
        m.put("wormhole-incoming", "&bIncoming wormhole");
        m.put("wormhole-incoming-sub", "&7Stand clear of the gate");
        m.put("wormhole-closed", "&7The wormhole closes.");
        m.put("wormhole-timeout", "&7The wormhole has run out of power.");
        m.put("wormhole-idle", "&7Nobody came through. The wormhole closes.");
        m.put("stand-clear", "&cStand clear!");
        m.put("iris-closed", "&cThe iris is closed. You cannot get through.");
        m.put("one-way", "&7This end of the wormhole only takes arrivals.");
        m.put("iris-on", "&7The iris closes.");
        m.put("iris-off", "&7The iris opens.");
        m.put("learned", "&6New address learned: &e{name} &7({address})");
        m.put("tablet-learned", "&6You study the tablet and learn the address of &e{name}&6.");
        m.put("tablet-known", "&7You already know that address.");
        m.put("tablet-given", "&6You copy the address of &e{name}&6 onto a tablet.");
        m.put("not-owner", "&cOnly the owner of this gate can do that.");
        m.put("no-permission", "&cYou cannot do that.");
        m.put("renamed", "&6This gate is now called &e{name}&6.");
        m.put("name-taken", "&cAnother gate already has that name.");
        m.put("bad-name", "&cA gate name is 1 to 32 characters.");
    }

    // dialing and wormholes
    int chevronTicks = 14;
    int cooldownSeconds = 8;
    int range = 10;
    boolean requireKnown = true;
    int maxSeconds = 300;
    int idleSeconds = 90;
    double arrivalDistance = 1.6;
    boolean kawooshEnabled = true;
    double kawooshDamage = 8.0;
    double kawooshKnockback = 1.2;
    int warnRadius = 8;
    /** players | all */
    String traffic = "all";
    int maxGatesPerPlayer = 3;

    // planets
    int borderRadius = 1500;
    boolean preload;

    final GateMaterials materials = new GateMaterials();

    // the dialing device item and recipe
    String dhdName = "&6Dialing Device";
    boolean recipeEnabled = true;
    List<String> recipeShape = new ArrayList<>(List.of("AEA", "OCO", "OOO"));
    Map<Character, Material> recipeIngredients = new LinkedHashMap<>();

    final Map<String, String> messages = new LinkedHashMap<>();
    final List<String> warnings = new ArrayList<>();

    StargateConfig() {
        resetRecipe();
    }

    private void resetRecipe() {
        recipeShape = new ArrayList<>(List.of("AEA", "OCO", "OOO"));
        recipeIngredients.clear();
        recipeIngredients.put('A', Material.AMETHYST_SHARD);
        recipeIngredients.put('E', Material.ENDER_EYE);
        recipeIngredients.put('O', Material.OBSIDIAN);
        recipeIngredients.put('C', Material.CRYING_OBSIDIAN);
    }

    String msg(String key) {
        String own = messages.get(key);
        return own != null ? own : DEFAULT_MESSAGES.getOrDefault(key, key);
    }

    static int clamp(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    void load(ConfigurationSection c) {
        warnings.clear();
        messages.clear();
        chevronTicks = clamp(c.getInt("dial.chevron-ticks", 14), 4, 100);
        cooldownSeconds = clamp(c.getInt("dial.cooldown-seconds", 8), 0, 3600);
        range = clamp(c.getInt("dial.range", 10), 3, 16);
        requireKnown = c.getBoolean("dial.require-known-address", true);
        maxSeconds = clamp(c.getInt("wormhole.max-seconds", 300), 10, 7200);
        idleSeconds = clamp(c.getInt("wormhole.idle-seconds", 90), 5, 7200);
        arrivalDistance = clamp(c.getDouble("wormhole.arrival-distance", 1.6), 1.0, 4.0);
        String t = c.getString("wormhole.traffic", "all").toLowerCase(Locale.ROOT);
        if (t.equals("players") || t.equals("all")) {
            traffic = t;
        } else {
            traffic = "all";
            warnings.add("wormhole.traffic must be 'players' or 'all', not '" + t + "'; using all.");
        }
        kawooshEnabled = c.getBoolean("kawoosh.enabled", true);
        kawooshDamage = clamp(c.getDouble("kawoosh.damage", 8.0), 0.0, 1000.0);
        kawooshKnockback = clamp(c.getDouble("kawoosh.knockback", 1.2), 0.0, 5.0);
        warnRadius = clamp(c.getInt("kawoosh.warn-radius", 8), 0, 64);
        maxGatesPerPlayer = clamp(c.getInt("gates.max-per-player", 3), 0, 1000);
        borderRadius = clamp(c.getInt("planets.border-radius", 1500), 100, 29_000_000);
        preload = c.getBoolean("planets.preload", false);

        GateMaterials d = new GateMaterials();
        materials.ring = block(c, "gate.ring", d.ring);
        materials.chevron = block(c, "gate.chevron", d.chevron);
        materials.lit = block(c, "gate.chevron-lit", d.lit);
        materials.horizon = block(c, "gate.horizon", d.horizon);
        materials.iris = block(c, "gate.iris", d.iris);
        materials.dhd = block(c, "gate.dhd", d.dhd);
        if (materials.ring == materials.chevron || materials.lit == materials.chevron || materials.ring == materials.lit) {
            warnings.add("gate.ring, gate.chevron and gate.chevron-lit must be three different blocks; using the defaults.");
            materials.ring = d.ring;
            materials.chevron = d.chevron;
            materials.lit = d.lit;
        }

        dhdName = c.getString("dhd.name", "&6Dialing Device");
        recipeEnabled = c.getBoolean("dhd.recipe.enabled", true);
        loadRecipe(c);

        ConfigurationSection msgs = c.getConfigurationSection("messages");
        if (msgs != null) {
            for (String key : msgs.getKeys(false)) {
                String v = msgs.getString(key);
                if (v == null) {
                    continue;
                }
                if (DEFAULT_MESSAGES.containsKey(key)) {
                    messages.put(key, v);
                } else {
                    warnings.add("messages." + key + " is not a message this plugin says; ignored.");
                }
            }
        }
    }

    private Material block(ConfigurationSection c, String path, Material fallback) {
        String name = c.getString(path, fallback.name());
        Material m = Material.matchMaterial(name);
        if (m == null || m.isAir()) {
            warnings.add(path + " '" + name + "' is not a block; using " + fallback.name() + ".");
            return fallback;
        }
        return m;
    }

    private void loadRecipe(ConfigurationSection c) {
        resetRecipe();
        List<String> shape = c.getStringList("dhd.recipe.shape");
        ConfigurationSection ing = c.getConfigurationSection("dhd.recipe.ingredients");
        if (shape.isEmpty() && ing == null) {
            return;
        }
        boolean ok = !shape.isEmpty() && shape.size() <= 3 && ing != null;
        Map<Character, Material> found = new LinkedHashMap<>();
        if (ok) {
            for (String row : shape) {
                if (row == null || row.isEmpty() || row.length() > 3) {
                    ok = false;
                }
            }
        }
        if (ok) {
            for (String key : ing.getKeys(false)) {
                String mat = ing.getString(key);
                Material m = mat == null ? null : Material.matchMaterial(mat);
                if (key.length() != 1 || m == null || m.isAir()) {
                    ok = false;
                    break;
                }
                found.put(key.charAt(0), m);
            }
        }
        if (ok) {
            for (String row : shape) {
                for (char ch : row.toCharArray()) {
                    if (ch != ' ' && !found.containsKey(ch)) {
                        ok = false;
                    }
                }
            }
        }
        if (!ok) {
            warnings.add("dhd.recipe is not a valid recipe (shape of 1 to 3 rows of up to 3 letters, and an item for each letter); using the default.");
            return;
        }
        recipeShape = new ArrayList<>(shape);
        recipeIngredients.clear();
        recipeIngredients.putAll(found);
    }
}
