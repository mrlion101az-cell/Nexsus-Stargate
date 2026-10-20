package com.nexuscraft.nexusstargate;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/** Placing and using dialing devices, protecting gates, and keeping the vanilla portal rules away from the horizon. */
final class StargateListener implements Listener {

    private final GateService gates;
    private final WormholeService wormholes;
    private final DialControl control;

    StargateListener(GateService gates, WormholeService wormholes, DialControl control) {
        this.gates = gates;
        this.wormholes = wormholes;
        this.control = control;
    }

    private GateStore store() {
        return gates.store();
    }

    private boolean protectedBlock(Block b) {
        String w = b.getWorld().getName();
        return store().isRing(w, b.getX(), b.getY(), b.getZ())
                || store().gateInside(w, b.getX(), b.getY(), b.getZ()) != null
                || store().dhdAt(w, b.getX(), b.getY(), b.getZ()) != null;
    }

    // ------------------------------------------------------------------ dialing devices

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player p = event.getPlayer();
        Block block = event.getBlockPlaced();
        if (gates.items().isDhd(event.getItemInHand())) {
            if (!p.hasPermission(GateService.BUILD)) {
                gates.tell(p, "no-permission");
                event.setCancelled(true);
                return;
            }
            if (gates.placeDhd(p, block) != GateService.Placed.OK) {
                event.setCancelled(true);
            }
            return;
        }
        if (protectedBlock(block)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Block b = event.getBlock();
        Player p = event.getPlayer();
        String w = b.getWorld().getName();
        GateStore.Dhd dhd = store().dhdAt(w, b.getX(), b.getY(), b.getZ());
        if (dhd != null) {
            Gate g = store().gate(dhd.gateId());
            boolean mine = dhd.owner() != null && dhd.owner().equals(p.getUniqueId());
            if (!mine && !gates.isAdmin(p) && !(g != null && gates.owns(p, g))) {
                gates.tell(p, "not-owner");
                event.setCancelled(true);
                return;
            }
            event.setCancelled(true);
            store().removeDhd(w, b.getX(), b.getY(), b.getZ());
            b.setType(Material.AIR);
            if (!p.getGameMode().equals(org.bukkit.GameMode.CREATIVE)) {
                b.getWorld().dropItemNaturally(b.getLocation(), gates.items().dhd());
            }
            return;
        }
        Gate ring = store().gateAt(w, b.getX(), b.getY(), b.getZ());
        if (ring != null) {
            boolean may = gates.isAdmin(p) || (!ring.isPlanet() && gates.owns(p, ring));
            if (!may) {
                gates.tell(p, ring.isPlanet() || ring.owner == null ? "no-permission" : "not-owner");
                event.setCancelled(true);
                return;
            }
            // the gate is being taken apart: forget it and let the block break
            gates.tell(p, "gate-removed", "name", ring.name);
            gates.remove(ring, false);
            return;
        }
        if (store().gateInside(w, b.getX(), b.getY(), b.getZ()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }
        Player p = event.getPlayer();
        Action a = event.getAction();
        if (a == Action.RIGHT_CLICK_BLOCK && event.getClickedBlock() != null) {
            Block b = event.getClickedBlock();
            Gate g = gates.gateOfDhd(b);
            if (g != null) {
                event.setCancelled(true);
                control.open(p, g, 0);
                return;
            }
        }
        if (a == Action.RIGHT_CLICK_BLOCK || a == Action.RIGHT_CLICK_AIR) {
            String id = gates.items().tabletGate(event.getItem());
            if (id != null) {
                event.setCancelled(true);
                useTablet(p, id);
            }
        }
    }

    private void useTablet(Player p, String id) {
        Gate g = store().gate(id);
        if (g == null) {
            gates.tell(p, "gate-removed", "name", "?");
            return;
        }
        if (store().knows(p.getUniqueId(), id) || gates.owns(p, g)) {
            gates.tell(p, "tablet-known");
            return;
        }
        store().learn(p.getUniqueId(), id);
        gates.tell(p, "tablet-learned", "name", g.name);
        if (p.getGameMode() != org.bukkit.GameMode.CREATIVE) {
            ItemStack hand = p.getInventory().getItemInMainHand();
            if (hand.getAmount() <= 1) {
                p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                hand.setAmount(hand.getAmount() - 1);
            }
        }
    }

    // ------------------------------------------------------------------ the menu

    @EventHandler(priority = EventPriority.HIGH)
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof DialMenu menu)) {
            return;
        }
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player p)) {
            return;
        }
        if (event.getClickedInventory() == top) {
            control.click(p, menu, event.getRawSlot());
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDrag(InventoryDragEvent event) {
        Inventory top = event.getView() == null ? null : event.getView().getTopInventory();
        if (top != null && top.getHolder() instanceof DialMenu) {
            event.setCancelled(true);
        }
    }

    // ------------------------------------------------------------------ keeping gates whole

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPhysics(BlockPhysicsEvent event) {
        Block b = event.getBlock();
        if (b.getType() == Material.NETHER_PORTAL
                && store().gateInside(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()) != null) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent event) {
        org.bukkit.Location from = event.getFrom();
        if (from != null && from.getWorld() != null && nearHorizon(from)) {
            event.setCancelled(true);
        }
    }

    private boolean nearHorizon(org.bukkit.Location at) {
        String w = at.getWorld().getName();
        int x = at.getBlockX();
        int z = at.getBlockZ();
        for (int y = at.getBlockY() - 1; y <= at.getBlockY() + 2; y++) {
            for (int dx = -1; dx <= 1; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    if (store().gateInside(w, x + dx, y, z + dz) != null) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::protectedBlock);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (anyProtected(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (anyProtected(event.getBlocks())) {
            event.setCancelled(true);
        }
    }

    private boolean anyProtected(List<Block> blocks) {
        for (Block b : blocks) {
            if (protectedBlock(b)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        store().flush();
    }
}
