package com.nexuscraft.nexusstargate;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The dialing device as a plain chest menu (so Java and Bedrock players get the same thing): every address the player
 * may dial, a page at a time, with the gate's own controls along the bottom. Nothing in it can be taken out.
 */
final class DialMenu implements InventoryHolder {

    enum Kind { DIAL, PREV, NEXT, IRIS, CLOSE, INFO }

    /** What a slot does; {@code target} is the gate id for DIAL. */
    record Button(Kind kind, String target) {
    }

    static final int SIZE = 54;
    static final int PER_PAGE = 45;
    static final int PREV_SLOT = 45;
    static final int IRIS_SLOT = 47;
    static final int INFO_SLOT = 49;
    static final int CLOSE_SLOT = 51;
    static final int NEXT_SLOT = 53;
    static final int WRAP = 38;

    final String gateId;
    final int page;
    private final Map<Integer, Button> bySlot = new HashMap<>();
    private Inventory inventory;

    private DialMenu(String gateId, int page) {
        this.gateId = gateId;
        this.page = page;
    }

    @Override
    public Inventory getInventory() {
        return inventory;
    }

    Button at(int slot) {
        return bySlot.get(slot);
    }

    static int pages(int gateCount) {
        return Math.max(1, (gateCount + PER_PAGE - 1) / PER_PAGE);
    }

    static DialMenu build(GateService gates, WormholeService wormholes, Player viewer, Gate from, int requestedPage) {
        List<Gate> list = gates.dialable(viewer, from);
        int pages = pages(list.size());
        int page = Math.max(0, Math.min(requestedPage, pages - 1));
        DialMenu m = new DialMenu(from.id, page);
        m.inventory = Bukkit.createInventory(m, SIZE, ColorText.translate("&8Dialing device"));
        for (int i = 0; i < PER_PAGE; i++) {
            int idx = page * PER_PAGE + i;
            if (idx >= list.size()) {
                break;
            }
            Gate g = list.get(idx);
            List<String> lore = new ArrayList<>();
            lore.add("&7" + g.address.names());
            if (g.isPlanet()) {
                Planet pl = gates.planets().get(g.planet);
                if (pl != null) {
                    lore.add("&7" + pl.designation());
                }
            } else {
                lore.add("&7" + g.world);
            }
            lore.add(wormholes.busy(g) ? "&cBusy right now" : "&aTap to dial");
            m.put(i, iconOf(gates, g), "&b" + g.name, lore, new Button(Kind.DIAL, g.id));
        }
        if (page > 0) {
            m.put(PREV_SLOT, Material.ARROW, "&6Previous page", List.of("&7Page " + page + " of " + pages), new Button(Kind.PREV, ""));
        }
        if (page < pages - 1) {
            m.put(NEXT_SLOT, Material.ARROW, "&6Next page", List.of("&7Page " + (page + 2) + " of " + pages), new Button(Kind.NEXT, ""));
        }
        WormholeService.Wormhole wh = wormholes.of(from);
        List<String> info = new ArrayList<>();
        info.add("&7" + from.address.names());
        info.add("&7Address: &f" + from.address.code());
        info.add(wh == null ? "&7Idle" : wh.phase == WormholeService.Phase.OPEN ? "&bWormhole open" : "&eDialing…");
        info.add("&7Iris: " + (from.iris ? "&cclosed" : "&aopen"));
        m.put(INFO_SLOT, Material.BOOK, "&6" + from.name, info, new Button(Kind.INFO, ""));
        if (gates.owns(viewer, from) || gates.isAdmin(viewer)) {
            m.put(IRIS_SLOT, gates.cfg().materials.iris, from.iris ? "&aOpen the iris" : "&cClose the iris",
                    List.of("&7Closed, nothing arrives through this gate."), new Button(Kind.IRIS, ""));
        }
        if (wh != null) {
            m.put(CLOSE_SLOT, Material.BARRIER, "&cClose the wormhole", List.of("&7Shut the gate down now."), new Button(Kind.CLOSE, ""));
        }
        return m;
    }

    static Material iconOf(GateService gates, Gate g) {
        if (g.isPlanet()) {
            Planet pl = gates.planets().get(g.planet);
            Style s = pl == null ? null : Style.get(pl.style());
            if (s != null) {
                return s.surface;
            }
        }
        return Glyphs.icon(g.address.glyph(0));
    }

    private void put(int slot, Material icon, String name, List<String> lore, Button button) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ColorText.translate(name));
        List<String> out = new ArrayList<>();
        for (String line : lore) {
            for (String piece : wrap(line, WRAP)) {
                out.add(ColorText.translate(piece));
            }
        }
        meta.setLore(out);
        item.setItemMeta(meta);
        inventory.setItem(slot, item);
        bySlot.put(slot, button);
    }

    /** Splits a long line at spaces; every piece keeps the line's leading colour codes. */
    static List<String> wrap(String line, int width) {
        List<String> out = new ArrayList<>();
        if (line == null) {
            return out;
        }
        int start = 0;
        while (start + 1 < line.length() && line.charAt(start) == '&') {
            start += 2;
        }
        String codes = line.substring(0, start);
        String rest = line.substring(start);
        if (rest.length() <= width) {
            out.add(line);
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : rest.split(" ")) {
            if (cur.length() > 0 && cur.length() + 1 + word.length() > width) {
                out.add(codes + cur);
                cur.setLength(0);
            }
            if (cur.length() > 0) {
                cur.append(' ');
            }
            cur.append(word);
        }
        out.add(codes + cur);
        return out;
    }
}
