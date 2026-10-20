package com.nexuscraft.nexusstargate;

import org.bukkit.configuration.ConfigurationSection;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Every gate world the server has. Loaded from the built-in planets.yml, then custom-planets.yml on top (change a
 * planet field by field, add planets, hide planets), in file order.
 */
final class PlanetBook {

    private final Map<String, Planet> planets = new LinkedHashMap<>();
    private final List<String> warnings = new ArrayList<>();

    static String key(String id) {
        return id == null ? "" : id.trim().toLowerCase(Locale.ROOT);
    }

    void clear() {
        planets.clear();
        warnings.clear();
    }

    Collection<Planet> all() {
        return Collections.unmodifiableCollection(planets.values());
    }

    Planet get(String id) {
        return planets.get(key(id));
    }

    Planet byWorld(String worldName) {
        for (Planet p : planets.values()) {
            if (p.worldName().equals(worldName)) {
                return p;
            }
        }
        return null;
    }

    int size() {
        return planets.size();
    }

    List<String> warnings() {
        return Collections.unmodifiableList(warnings);
    }

    void load(ConfigurationSection root, String source) {
        if (root == null) {
            return;
        }
        ConfigurationSection sec = root.getConfigurationSection("planets");
        if (sec != null) {
            for (String id : sec.getKeys(false)) {
                ConfigurationSection s = sec.getConfigurationSection(id);
                if (s == null) {
                    warnings.add(source + ": planet '" + id + "' is not a section -- skipped.");
                    continue;
                }
                put(key(id), s, source);
            }
        }
        for (String id : root.getStringList("hidden-planets")) {
            if (planets.remove(key(id)) == null) {
                warnings.add(source + ": hidden-planets names '" + id + "', which is not a planet.");
            }
        }
    }

    private void put(String id, ConfigurationSection s, String source) {
        if (!id.matches("[a-z0-9_\\-]{1,24}")) {
            warnings.add(source + ": planet id '" + id + "' must be 1 to 24 letters, digits, - or _ -- skipped.");
            return;
        }
        Planet old = planets.get(id);
        String style = key(s.getString("style", old == null ? "" : old.style()));
        if (Style.get(style) == null) {
            warnings.add(source + ": planet '" + id + "' has style '" + style + "', which is not one of " + Style.BUILT_IN.keySet() + " -- skipped.");
            return;
        }
        String name = s.getString("name", old == null ? id : old.name());
        long seed = s.getLong("seed", old == null ? id.hashCode() * 31L + 17 : old.seed());
        long time = s.getLong("time", old == null ? Style.get(style).time : old.time());
        planets.put(id, new Planet(id,
                name == null || name.isBlank() ? id : name,
                s.getString("designation", old == null ? "" : old.designation()),
                style,
                seed,
                s.getBoolean("starter", old != null && old.starter()),
                Math.floorMod(time, 24000L),
                s.getBoolean("mobs", old == null || old.mobs()),
                s.getString("blurb", old == null ? "" : old.blurb())));
    }
}
