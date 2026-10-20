package com.nexuscraft.nexusstargate;

import java.util.Arrays;
import java.util.Random;
import java.util.function.Predicate;

/** Six distinct glyphs, in order. Immutable. */
final class GateAddress {

    private final int[] glyphs;

    private GateAddress(int[] glyphs) {
        this.glyphs = glyphs;
    }

    /** An address from six glyph numbers; null when they are not six distinct valid glyphs. */
    static GateAddress of(int... g) {
        if (g == null || g.length != Glyphs.LENGTH) {
            return null;
        }
        boolean[] seen = new boolean[Glyphs.COUNT];
        for (int v : g) {
            if (v < 0 || v >= Glyphs.COUNT || seen[v]) {
                return null;
            }
            seen[v] = true;
        }
        return new GateAddress(g.clone());
    }

    /** "3.17.22.0.5.9" (the saved form), or glyph names separated by dashes, spaces or commas. */
    static GateAddress parse(String text) {
        if (text == null) {
            return null;
        }
        String t = text.trim();
        if (t.isEmpty()) {
            return null;
        }
        String[] parts = t.split("[\\s,.\\-]+");
        if (parts.length != Glyphs.LENGTH) {
            return null;
        }
        int[] g = new int[Glyphs.LENGTH];
        for (int i = 0; i < g.length; i++) {
            if (parts[i].chars().allMatch(Character::isDigit) && !parts[i].isEmpty() && parts[i].length() <= 3) {
                g[i] = Integer.parseInt(parts[i]);
            } else {
                g[i] = Glyphs.indexOf(parts[i]);
            }
        }
        return of(g);
    }

    /** A random address that is not taken. Gives up (null) only if 200 tries all collide. */
    static GateAddress random(Random rng, Predicate<GateAddress> taken) {
        for (int attempt = 0; attempt < 200; attempt++) {
            int[] g = new int[Glyphs.LENGTH];
            boolean[] used = new boolean[Glyphs.COUNT];
            for (int i = 0; i < g.length; i++) {
                int v;
                do {
                    v = rng.nextInt(Glyphs.COUNT);
                } while (used[v]);
                used[v] = true;
                g[i] = v;
            }
            GateAddress a = new GateAddress(g);
            if (!taken.test(a)) {
                return a;
            }
        }
        return null;
    }

    int glyph(int i) {
        return glyphs[i];
    }

    int[] glyphs() {
        return glyphs.clone();
    }

    /** The saved form, e.g. "3.17.22.0.5.9". */
    String code() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glyphs.length; i++) {
            if (i > 0) {
                sb.append('.');
            }
            sb.append(glyphs[i]);
        }
        return sb.toString();
    }

    /** The readable form: "Taurus-Orion-Hydra-...". */
    String names() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < glyphs.length; i++) {
            if (i > 0) {
                sb.append('-');
            }
            sb.append(Glyphs.name(glyphs[i]));
        }
        return sb.toString();
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof GateAddress a && Arrays.equals(glyphs, a.glyphs);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(glyphs);
    }

    @Override
    public String toString() {
        return names();
    }
}
