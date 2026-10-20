package com.nexuscraft.nexusstargate;

import org.bukkit.entity.Player;

/** What happens when a player uses a dialing device: opening its menu and running its buttons. */
final class DialControl {

    private final GateService gates;
    private final WormholeService wormholes;

    DialControl(GateService gates, WormholeService wormholes) {
        this.gates = gates;
        this.wormholes = wormholes;
    }

    /** Opens the dialing menu of a gate for a player. */
    void open(Player p, Gate gate, int page) {
        if (!p.hasPermission(GateService.USE)) {
            gates.tell(p, "no-permission");
            return;
        }
        p.openInventory(DialMenu.build(gates, wormholes, p, gate, page).getInventory());
    }

    void click(Player p, DialMenu menu, int slot) {
        DialMenu.Button b = menu.at(slot);
        Gate gate = gates.store().gate(menu.gateId);
        if (b == null) {
            return;
        }
        if (gate == null || gates.gateNear(p.getLocation(), gates.cfg().range + 6) != gate) {
            p.closeInventory();
            return;
        }
        switch (b.kind()) {
            case DIAL -> {
                p.closeInventory();
                Gate target = gates.store().gate(b.target());
                if (target != null) {
                    wormholes.dial(p, gate, target);
                }
            }
            case PREV -> open(p, gate, menu.page - 1);
            case NEXT -> open(p, gate, menu.page + 1);
            case IRIS -> {
                if (gates.owns(p, gate) || gates.isAdmin(p)) {
                    wormholes.iris(gate, !gate.iris);
                    gates.tell(p, gate.iris ? "iris-on" : "iris-off");
                    open(p, gate, menu.page);
                } else {
                    gates.tell(p, "not-owner");
                }
            }
            case CLOSE -> {
                wormholes.close(gate, "wormhole-closed");
                p.closeInventory();
            }
            case INFO -> {
            }
        }
    }
}
