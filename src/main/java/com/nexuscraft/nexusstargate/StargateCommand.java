package com.nexuscraft.nexusstargate;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/** /stargate: dial, look at, rename and close the gate beside you; for staff also hand out items, teach addresses, visit planets. */
final class StargateCommand implements CommandExecutor, TabCompleter {

    private static final List<String> PLAYER_SUBS = List.of("dial", "list", "info", "close", "iris", "rename", "tablet", "blueprint", "help");
    private static final List<String> ADMIN_SUBS = List.of("give", "learn", "forget", "remove", "planets", "tp", "rebuild", "status", "reload");

    private final GateService gates;
    private final WormholeService wormholes;
    private final DialControl control;
    private final PlanetService planetService;
    private final Runnable reload;

    StargateCommand(GateService gates, WormholeService wormholes, DialControl control, PlanetService planetService, Runnable reload) {
        this.gates = gates;
        this.wormholes = wormholes;
        this.control = control;
        this.planetService = planetService;
        this.reload = reload;
    }

    private GateStore store() {
        return gates.store();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        String[] rest = args.length <= 1 ? new String[0] : Arrays.copyOfRange(args, 1, args.length);
        switch (sub) {
            case "":
                if (sender instanceof Player p && nearest(p) != null) {
                    control.open(p, nearest(p), 0);
                    return true;
                }
                return help(sender);
            case "help":
                return help(sender);
            case "blueprint":
                return blueprint(sender);
            case "list":
                return player(sender, this::list);
            case "info":
                return player(sender, this::info);
            case "dial":
                return player(sender, p -> dial(p, rest));
            case "close":
                return player(sender, this::close);
            case "iris":
                return player(sender, this::iris);
            case "rename":
                return player(sender, p -> rename(p, rest));
            case "tablet":
                return player(sender, p -> tablet(p, rest));
            case "give":
            case "learn":
            case "forget":
            case "remove":
            case "planets":
            case "tp":
            case "rebuild":
            case "status":
            case "reload":
                if (!gates.isAdmin(sender)) {
                    sender.sendMessage(gates.fmt("no-permission"));
                    return true;
                }
                return staff(sender, sub, rest);
            default:
                return help(sender);
        }
    }

    private static boolean player(CommandSender sender, Consumer<Player> action) {
        if (sender instanceof Player p) {
            action.accept(p);
        } else {
            sender.sendMessage("That needs a player standing at a gate.");
        }
        return true;
    }

    private Gate nearest(Player p) {
        return gates.gateNear(p.getLocation(), gates.cfg().range + 6);
    }

    private Gate nearOrTell(Player p) {
        Gate g = nearest(p);
        if (g == null) {
            gates.tell(p, "dhd-no-gate", "range", String.valueOf(gates.cfg().range + 6));
        }
        return g;
    }

    private static void say(CommandSender to, String text) {
        to.sendMessage(ColorText.translate(text));
    }

    // ------------------------------------------------------------------ everyone

    private boolean help(CommandSender sender) {
        say(sender, "&6Stargate: &f/stargate &7opens the dialing device of the gate beside you.");
        say(sender, "&6Also: &f/stargate dial <name or address>&7, &flist&7, &finfo&7, &fclose&7, &firis&7, &frename <name>&7, &ftablet <name>&7, &fblueprint");
        if (gates.isAdmin(sender)) {
            say(sender, "&6Staff: &f/stargate give <dhd|tablet> <player> [gate]&7, &flearn <player> <gate|all>&7, &fforget <player> <gate>&7, &fremove <gate>&7, &fplanets&7, &ftp <planet>&7, &frebuild <planet>&7, &fstatus&7, &freload");
        }
        return true;
    }

    private boolean blueprint(CommandSender sender) {
        StargateConfig cfg = gates.cfg();
        say(sender, "&6A gate is 7 wide and 9 tall, standing upright. &fR&7 = " + name(cfg.materials.ring)
                + ", &fC&7 = " + name(cfg.materials.chevron) + ", &f.&7 = empty:");
        for (String line : GateShape.blueprint()) {
            say(sender, "&f" + line);
        }
        say(sender, "&7Then place a dialing device (" + name(cfg.materials.dhd) + " named " + ColorText.strip(ColorText.translate(cfg.dhdName))
                + ") within " + cfg.range + " blocks of it and right-click it.");
        return true;
    }

    private static String name(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private void list(Player p) {
        List<Gate> list = gates.dialable(p, null);
        if (list.isEmpty()) {
            say(p, "&7You do not know any addresses yet.");
            return;
        }
        say(p, "&6Addresses you know (" + list.size() + "):");
        for (Gate g : list) {
            say(p, "&e" + g.name + " &7- " + g.address.names());
        }
    }

    private void info(Player p) {
        Gate g = nearOrTell(p);
        if (g == null) {
            return;
        }
        say(p, "&6" + g.name + " &7(" + g.id + ")");
        say(p, "&7Address: &f" + g.address.names() + " &7(" + g.address.code() + ")");
        say(p, "&7Owner: &f" + ownerName(g) + (g.isPlanet() ? "&7, planet " + g.planet : ""));
        WormholeService.Wormhole wh = wormholes.of(g);
        say(p, "&7State: &f" + (wh == null ? "idle" : wh.phase == WormholeService.Phase.OPEN ? "open" : "dialing") + "&7, iris " + (g.iris ? "closed" : "open"));
        say(p, "&7Dialing devices: &f" + store().dhdsOf(g.id).size());
    }

    private String ownerName(Gate g) {
        if (g.owner == null) {
            return "the server";
        }
        Player online = Bukkit.getPlayer(g.owner);
        if (online != null) {
            return online.getName();
        }
        String n = Bukkit.getOfflinePlayer(g.owner).getName();
        return n == null ? g.owner.toString() : n;
    }

    private void dial(Player p, String[] rest) {
        Gate from = nearOrTell(p);
        if (from == null) {
            return;
        }
        if (rest.length == 0) {
            control.open(p, from, 0);
            return;
        }
        String text = String.join(" ", rest);
        Gate to = store().find(text);
        if (to == null) {
            GateAddress a = GateAddress.parse(text);
            to = a == null ? null : store().byAddress(a);
            if (to != null) {
                // knowing all six symbols is knowing the address
                store().learn(p.getUniqueId(), to.id);
            }
        }
        if (to == null) {
            gates.tell(p, "dial-unknown");
            return;
        }
        wormholes.dial(p, from, to);
    }

    private void close(Player p) {
        Gate g = nearOrTell(p);
        if (g != null && !wormholes.close(g, "wormhole-closed")) {
            say(p, "&7Nothing is open here.");
        }
    }

    private void iris(Player p) {
        Gate g = nearOrTell(p);
        if (g == null) {
            return;
        }
        if (!gates.owns(p, g) && !gates.isAdmin(p)) {
            gates.tell(p, "not-owner");
            return;
        }
        wormholes.iris(g, !g.iris);
        gates.tell(p, g.iris ? "iris-on" : "iris-off");
    }

    private void rename(Player p, String[] rest) {
        Gate g = nearOrTell(p);
        if (g != null) {
            gates.rename(p, g, String.join(" ", rest));
        }
    }

    private void tablet(Player p, String[] rest) {
        Gate g = rest.length == 0 ? null : store().find(String.join(" ", rest));
        if (g == null || !gates.knows(p, g)) {
            gates.tell(p, "dial-unknown");
            return;
        }
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (!creative && (hand == null || hand.getType() != Material.PAPER)) {
            say(p, "&7Hold a piece of paper to copy the address onto.");
            return;
        }
        if (!creative) {
            if (hand.getAmount() <= 1) {
                p.getInventory().setItemInMainHand(new ItemStack(Material.AIR));
            } else {
                hand.setAmount(hand.getAmount() - 1);
            }
        }
        give(p, gates.items().tablet(g));
        gates.tell(p, "tablet-given", "name", g.name);
    }

    private static void give(Player p, ItemStack item) {
        for (ItemStack left : p.getInventory().addItem(item).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), left);
        }
    }

    // ------------------------------------------------------------------ staff

    private boolean staff(CommandSender sender, String sub, String[] a) {
        switch (sub) {
            case "give":
                return giveItem(sender, a);
            case "learn":
                return learn(sender, a, true);
            case "forget":
                return learn(sender, a, false);
            case "remove": {
                Gate g = a.length == 0 ? null : store().find(String.join(" ", a));
                if (g == null) {
                    say(sender, "&cNo such gate.");
                    return true;
                }
                gates.remove(g, true);
                say(sender, "&6Removed " + g.name + " and took it down.");
                return true;
            }
            case "planets": {
                for (Planet pl : gates.planets().all()) {
                    boolean loaded = Bukkit.getWorld(pl.worldName()) != null;
                    say(sender, "&e" + pl.id() + " &7- " + pl.name() + " (" + pl.designation() + "), style " + pl.style()
                            + (pl.starter() ? ", starter" : "") + (loaded ? ", world loaded" : ", world not loaded"));
                }
                return true;
            }
            case "tp": {
                Planet pl = a.length == 0 ? null : gates.planets().get(a[0]);
                if (!(sender instanceof Player p) || pl == null) {
                    say(sender, "&cUsage (as a player): /stargate tp <planet id>");
                    return true;
                }
                if (!planetService.send(p, pl)) {
                    say(sender, "&cThat world could not be made.");
                }
                return true;
            }
            case "rebuild": {
                Planet pl = a.length == 0 ? null : gates.planets().get(a[0]);
                if (pl == null) {
                    say(sender, "&cUsage: /stargate rebuild <planet id>");
                    return true;
                }
                say(sender, planetService.rebuild(pl) ? "&6Rebuilt the arrival area of " + pl.name() + "." : "&cThat world could not be made.");
                return true;
            }
            case "status":
                say(sender, "&6Gates: &f" + store().gates().size() + "&6, planets: &f" + gates.planets().size()
                        + "&6, wormholes now: &f" + wormholes.active());
                return true;
            default:
                reload.run();
                say(sender, "&6NexusStargate reloaded.");
                return true;
        }
    }

    private boolean giveItem(CommandSender sender, String[] a) {
        if (a.length < 2) {
            say(sender, "&cUsage: /stargate give <dhd|tablet> <player> [gate]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[1]);
        if (target == null) {
            say(sender, "&cThat player is not online.");
            return true;
        }
        if (a[0].equalsIgnoreCase("dhd")) {
            give(target, gates.items().dhd());
            say(sender, "&6Gave a dialing device to " + target.getName() + ".");
            return true;
        }
        if (a[0].equalsIgnoreCase("tablet")) {
            Gate g = a.length < 3 ? null : store().find(String.join(" ", Arrays.copyOfRange(a, 2, a.length)));
            if (g == null) {
                say(sender, "&cNo such gate.");
                return true;
            }
            give(target, gates.items().tablet(g));
            say(sender, "&6Gave a tablet for " + g.name + " to " + target.getName() + ".");
            return true;
        }
        say(sender, "&cGive what? dhd or tablet.");
        return true;
    }

    private boolean learn(CommandSender sender, String[] a, boolean learn) {
        if (a.length < 2) {
            say(sender, "&cUsage: /stargate " + (learn ? "learn" : "forget") + " <player> <gate|all>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target == null) {
            say(sender, "&cThat player is not online.");
            return true;
        }
        String what = String.join(" ", Arrays.copyOfRange(a, 1, a.length));
        List<Gate> which = new ArrayList<>();
        if (what.equalsIgnoreCase("all")) {
            which.addAll(store().gates());
        } else {
            Gate g = store().find(what);
            if (g == null) {
                say(sender, "&cNo such gate.");
                return true;
            }
            which.add(g);
        }
        int n = 0;
        for (Gate g : which) {
            boolean changed = learn ? store().learn(target.getUniqueId(), g.id) : store().forget(target.getUniqueId(), g.id);
            if (changed) {
                n++;
                if (learn) {
                    gates.tell(target, "learned", "name", g.name, "address", g.address.names());
                }
            }
        }
        say(sender, "&6" + target.getName() + (learn ? " learned " : " forgot ") + n + " address(es).");
        return true;
    }

    // ------------------------------------------------------------------ completion

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            out.addAll(PLAYER_SUBS);
            if (gates.isAdmin(sender)) {
                out.addAll(ADMIN_SUBS);
            }
            return filter(out, args[0]);
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        boolean admin = gates.isAdmin(sender);
        if (args.length == 2) {
            switch (sub) {
                case "dial", "tablet" -> out.addAll(gateNames());
                case "give" -> {
                    if (admin) {
                        out.addAll(List.of("dhd", "tablet"));
                    }
                }
                case "learn", "forget" -> {
                    if (admin) {
                        out.addAll(playerNames());
                    }
                }
                case "remove" -> {
                    if (admin) {
                        out.addAll(gateNames());
                    }
                }
                case "tp", "rebuild" -> {
                    if (admin) {
                        gates.planets().all().forEach(pl -> out.add(pl.id()));
                    }
                }
                default -> {
                }
            }
        } else if (args.length == 3 && admin) {
            switch (sub) {
                case "give" -> out.addAll(playerNames());
                case "learn", "forget" -> {
                    out.addAll(gateNames());
                    out.add("all");
                }
                default -> {
                }
            }
        } else if (args.length == 4 && admin && sub.equals("give") && args[1].equalsIgnoreCase("tablet")) {
            out.addAll(gateNames());
        }
        return filter(out, args[args.length - 1]);
    }

    private List<String> gateNames() {
        List<String> out = new ArrayList<>();
        for (Gate g : store().gates()) {
            out.add(g.name);
        }
        return out;
    }

    private static List<String> playerNames() {
        List<String> out = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) {
            out.add(p.getName());
        }
        return out;
    }

    private static List<String> filter(List<String> all, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (String s : all) {
            if (s.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(s);
            }
        }
        return out;
    }
}
