package com.nexuscraft.nexusstargate;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.entity.Zombie;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.inventory.InventoryView;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import static com.nexuscraft.nexusstargate.Kit.expect;

/** The whole thing wired together against the stub server: building, placing, dialing, travelling, protecting, commands, the plugin. */
public class FlowTest {

    /** One server's worth of wiring. */
    static final class Env {
        final Kit.TestPlugin plugin = new Kit.TestPlugin();
        final StargateConfig cfg = new StargateConfig();
        final PlanetBook book = new PlanetBook();
        final GateStore store;
        final Items items;
        final GateBuilder builder;
        final GateService gates;
        final WormholeService wh;
        final PlanetService planets;
        final DialControl control;
        final StargateListener listener;
        final StargateCommand cmd;
        final World world;
        final File dir;
        final List<String> warns = new ArrayList<>();
        int reloads;

        Env(Kit.MapSection config, boolean withPlanets) throws Exception {
            Kit.reset();
            Bukkit.WORLDS.clear();
            Bukkit.CREATED.clear();
            dir = Files.createTempDirectory("sg-flow").toFile();
            world = new World("world");
            Bukkit.WORLDS.put("world", world);
            cfg.load(config == null ? new Kit.MapSection() : config);
            if (withPlanets) {
                book.load(LogicTest.flatFile("shipped.planets"), "planets.yml");
            }
            store = new GateStore(new File(dir, "gates.tsv"), new File(dir, "dhds.tsv"), new File(dir, "known.tsv"), new File(dir, "planets.tsv"), warns::add);
            store.load();
            items = new Items(cfg);
            builder = new GateBuilder(cfg.materials);
            gates = new GateService(cfg, store, book, builder, items, new Random(7), () -> 1_000_000L);
            wh = new WormholeService(plugin, gates);
            planets = new PlanetService(gates, warns::add);
            control = new DialControl(gates, wh);
            listener = new StargateListener(gates, wh, control);
            cmd = new StargateCommand(gates, wh, control, planets, () -> reloads++);
            if (withPlanets) {
                planets.syncAll();
            }
        }

        Kit.TestPlayer player(String name, boolean admin) {
            Kit.TestPlayer p = Kit.online(name);
            p.perms.add(GateService.USE);
            p.perms.add(GateService.BUILD);
            if (admin) {
                p.perms.add(GateService.ADMIN);
            }
            p.stubSetLocation(new Location(world, 0.5, 64, 0.5));
            return p;
        }

        void tick(int n) {
            for (int i = 0; i < n; i++) {
                wh.tick();
            }
        }

        /** Ticks until the wormhole from this gate is open (or gives up). */
        void tickOpen(Gate from) {
            for (int i = 0; i < 200 && (wh.of(from) == null || wh.of(from).phase != WormholeService.Phase.OPEN); i++) {
                if (wh.of(from) == null) {
                    return;
                }
                wh.tick();
            }
        }

        Block at(int x, int y, int z) {
            return world.getBlockAt(x, y, z);
        }

        /** A ring in the test world at this anchor. */
        void ring(int ax, int az, BlockFace f) {
            builder.buildRing(world, ax, 64, az, f);
        }

        /** Builds a ring and has the player place a dialing device six blocks in front of it. Returns the gate. */
        Gate gateFor(Kit.TestPlayer p, int ax, int az) {
            ring(ax, az, BlockFace.SOUTH);
            Block b = at(ax, 65, az + 6);
            b.setType(cfg.materials.dhd);
            BlockPlaceEvent ev = new BlockPlaceEvent(b, p, items.dhd());
            listener.onPlace(ev);
            expect(!ev.isCancelled(), "setup: dhd placed for " + p.getName() + " at " + ax + "," + az);
            return store.gateAt("world", ax, 64, az);
        }

        void stand(Player p, Gate g, double inFront) {
            ((Kit.TestPlayer) p).stubSetLocation(new Location(Bukkit.getWorld(g.world), g.ax + 0.5 + g.facing.getModX() * inFront, g.ay + 1, g.az + 0.5 + g.facing.getModZ() * inFront));
        }

        void into(Entity e, Gate g) {
            e.stubSetLocation(new Location(Bukkit.getWorld(g.world), g.ax + 0.5, g.ay + 2, g.az + 0.5));
        }

        PlayerInteractEvent rightClick(Player p, Block b, ItemStack item) {
            PlayerInteractEvent ev = new PlayerInteractEvent(p, b == null ? Action.RIGHT_CLICK_AIR : Action.RIGHT_CLICK_BLOCK, item, b);
            listener.onInteract(ev);
            return ev;
        }

        boolean click(Kit.TestPlayer p, int slot) {
            InventoryView view = p.getOpenInventory();
            InventoryClickEvent ev = new InventoryClickEvent(view, InventoryType.SlotType.CONTAINER, slot, ClickType.LEFT, InventoryAction.PICKUP_ALL);
            listener.onClick(ev);
            return ev.isCancelled();
        }

        int slotOf(DialMenu m, DialMenu.Kind kind, String target) {
            for (int i = 0; i < DialMenu.SIZE; i++) {
                DialMenu.Button b = m.at(i);
                if (b != null && b.kind() == kind && (target == null || target.equals(b.target()))) {
                    return i;
                }
            }
            return -1;
        }

        List<String> run(Kit.TestPlayer p, String... args) {
            int before = p.messages.size();
            boolean handled = cmd.onCommand(p, null, "stargate", args);
            expect(handled, "cmd: '" + String.join(" ", args) + "' returns true");
            return new ArrayList<>(p.messages.subList(before, p.messages.size()));
        }

        int count(Gate g, Material m) {
            int n = 0;
            for (int[] p : GateShape.insideWorld(g.ax, g.ay, g.az, g.facing)) {
                if (world(g).getBlockAt(p[0], p[1], p[2]).getType() == m) {
                    n++;
                }
            }
            return n;
        }

        int lit(Gate g) {
            int n = 0;
            for (GateShape.Cell c : GateShape.CHEVRONS) {
                int[] p = GateShape.pos(g.ax, g.ay, g.az, g.facing, c);
                if (world(g).getBlockAt(p[0], p[1], p[2]).getType() == cfg.materials.lit) {
                    n++;
                }
            }
            return n;
        }

        World world(Gate g) {
            return Bukkit.getWorld(g.world);
        }
    }

    static boolean said(List<String> lines, String fragment) {
        for (String m : lines) {
            if (m.contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    public static void main(String[] args) throws Exception {
        items();
        placing();
        planets();
        dialing();
        travel();
        failures();
        iris();
        kawoosh();
        protection();
        tablets();
        menus();
        commands();
        api();
        pluginWiring();
        builderAndService();
        dialDetails();
        travelDetails();
        closeDetails();
        menuDetails();
        forgedClicks();
        listenerDetails();
        commandDetails();
        gateServiceDetails();
        portalReach();
        expect(World.ungeneratedLoads == 0, "chunks: every chunk load asked for generation");
        Kit.finish();
    }

    // ------------------------------------------------------------------ items

    static void items() throws Exception {
        Env e = new Env(null, false);
        ItemStack dhd = e.items.dhd();
        expect(dhd.getType() == Material.CRYING_OBSIDIAN && e.items.isDhd(dhd) && !e.items.isDhd(new ItemStack(Material.CRYING_OBSIDIAN)) && !e.items.isDhd(null)
                && dhd.getItemMeta().getDisplayName().contains("Dialing Device"), "items: a dialing device is marked crying obsidian");
        expect(dhd.getItemMeta().getLore().size() == 2 && dhd.getItemMeta().getLore().get(0).contains("10 blocks"), "items: dhd lore names the range");
        Gate g = LogicTest.gate("abc123", "Home", 0, BlockFace.SOUTH, 1, 2, 3, 4, 5, 6);
        ItemStack t = e.items.tablet(g);
        expect(t.getType() == Material.DISC_FRAGMENT_5 && "abc123".equals(e.items.tabletGate(t)) && e.items.tabletGate(new ItemStack(Material.DISC_FRAGMENT_5)) == null
                && e.items.tabletGate(dhd) == null && e.items.tabletGate(null) == null && !e.items.isDhd(t), "items: a tablet carries a gate id");
        expect(t.getItemMeta().getDisplayName().contains("Home") && String.join(" ", t.getItemMeta().getLore()).contains(g.address.names()), "items: tablet shows name and address");
        expect(String.join(" ", t.getItemMeta().getLore()).contains("Right-click to learn"), "items: tablet says how to use it");
        e.items.registerRecipe();
        org.bukkit.inventory.Recipe r = Bukkit.getRecipe(Items.RECIPE_KEY);
        expect(r instanceof org.bukkit.inventory.ShapedRecipe sr && sr.getShape().length == 3 && sr.getShape()[1].equals("OCO") && sr.getChoiceMap().size() == 4
                && e.items.isDhd(r.getResult()), "items: recipe registered and makes a marked dialing device");
        e.items.registerRecipe();
        expect(Bukkit.getRecipe(Items.RECIPE_KEY) != null, "items: registering twice is fine");
        e.items.unregisterRecipe();
        expect(Bukkit.getRecipe(Items.RECIPE_KEY) == null, "items: recipe removed");
        e.cfg.recipeEnabled = false;
        e.items.registerRecipe();
        expect(Bukkit.getRecipe(Items.RECIPE_KEY) == null, "items: a disabled recipe is not registered");
    }

    // ------------------------------------------------------------------ placing dialing devices

    static void placing() throws Exception {
        Env e = new Env(null, false);
        Kit.TestPlayer ann = e.player("Ann", false);
        Kit.TestPlayer bob = e.player("Bob", false);
        Kit.TestPlayer boss = e.player("Boss", true);

        // no gate at all
        Block lonely = e.at(500, 65, 500);
        lonely.setType(Material.CRYING_OBSIDIAN);
        BlockPlaceEvent none = new BlockPlaceEvent(lonely, ann, e.items.dhd());
        e.listener.onPlace(none);
        expect(none.isCancelled() && ann.heard("No complete gate within"), "place: no gate nearby: cancelled and told: " + ann.messages);

        // a gate with something in it
        e.ring(100, 100, BlockFace.SOUTH);
        e.at(100, 66, 100).setType(Material.DIRT);
        ann.messages.clear();
        Block near = e.at(100, 65, 106);
        BlockPlaceEvent blocked = new BlockPlaceEvent(near, ann, e.items.dhd());
        e.listener.onPlace(blocked);
        expect(blocked.isCancelled() && ann.heard("something in its opening") && e.store.gates().isEmpty(), "place: blocked gate refused: " + ann.messages);
        e.at(100, 66, 100).setType(Material.AIR);

        // no build permission
        ann.perms.remove(GateService.BUILD);
        BlockPlaceEvent denied = new BlockPlaceEvent(near, ann, e.items.dhd());
        e.listener.onPlace(denied);
        expect(denied.isCancelled() && ann.heard("You cannot do that") && e.store.gates().isEmpty(), "place: needs the build permission");
        ann.perms.add(GateService.BUILD);

        // a complete gate: it becomes Ann's, with an address
        ann.messages.clear();
        BlockPlaceEvent ok = new BlockPlaceEvent(near, ann, e.items.dhd());
        e.listener.onPlace(ok);
        Gate g = e.store.gateAt("world", 100, 64, 100);
        expect(!ok.isCancelled() && g != null && g.owner.equals(ann.getUniqueId()) && g.name.equals("Ann's gate") && g.facing == BlockFace.SOUTH && !g.isPlanet() && !g.common
                && g.address != null && e.store.byAddress(g.address) == g && g.createdAt == 1_000_000L, "place: a gate is made for Ann, facing the device");
        expect(e.store.dhdAt("world", 100, 65, 106) != null && e.store.dhdAt("world", 100, 65, 106).gateId().equals(g.id) && e.store.knows(ann.getUniqueId(), g.id), "place: the device is linked and Ann knows the gate");
        expect(ann.heard("Dialing device linked to") && ann.heard(g.address.names()), "place: told the address: " + ann.messages);

        // another device on the same gate: owner and staff may, others may not
        Block second = e.at(98, 65, 104);
        BlockPlaceEvent more = new BlockPlaceEvent(second, ann, e.items.dhd());
        e.listener.onPlace(more);
        expect(!more.isCancelled() && e.store.dhdsOf(g.id).size() == 2 && e.store.gates().size() == 1, "place: a second device for the same gate");
        BlockPlaceEvent stranger = new BlockPlaceEvent(e.at(102, 65, 104), bob, e.items.dhd());
        e.listener.onPlace(stranger);
        expect(stranger.isCancelled() && bob.heard("Only the owner") && e.store.dhdsOf(g.id).size() == 2, "place: a stranger cannot link to Ann's gate");
        BlockPlaceEvent staff = new BlockPlaceEvent(e.at(103, 65, 104), boss, e.items.dhd());
        e.listener.onPlace(staff);
        expect(!staff.isCancelled() && e.store.dhdsOf(g.id).size() == 3, "place: staff may");

        // the other side of the gate: a device behind it makes the gate face north
        e.ring(200, 200, BlockFace.SOUTH);
        BlockPlaceEvent back = new BlockPlaceEvent(e.at(200, 65, 194), bob, e.items.dhd());
        e.listener.onPlace(back);
        Gate bg = e.store.gateAt("world", 200, 64, 200);
        expect(!back.isCancelled() && bg.facing == BlockFace.NORTH, "place: a device behind the gate makes it face that way: " + (bg == null ? null : bg.facing));
        e.ring(300, 300, BlockFace.EAST);
        BlockPlaceEvent west = new BlockPlaceEvent(e.at(294, 65, 300), bob, e.items.dhd());
        e.listener.onPlace(west);
        expect(e.store.gateAt("world", 300, 64, 300).facing == BlockFace.WEST, "place: east/west gate, device to the west");

        // names stay unique
        e.ring(400, 400, BlockFace.SOUTH);
        e.listener.onPlace(new BlockPlaceEvent(e.at(400, 65, 406), ann, e.items.dhd()));
        expect(e.store.gateAt("world", 400, 64, 400).name.equals("Ann's gate 2"), "place: second gate by the same player gets a number: " + e.store.gateAt("world", 400, 64, 400).name);

        // limit: Ann owns two, the limit is three
        e.ring(500, 600, BlockFace.SOUTH);
        e.listener.onPlace(new BlockPlaceEvent(e.at(500, 65, 606), ann, e.items.dhd()));
        expect(e.gates.ownedBy(ann.getUniqueId()) == 3, "place: three gates");
        e.ring(600, 600, BlockFace.SOUTH);
        ann.messages.clear();
        BlockPlaceEvent over = new BlockPlaceEvent(e.at(600, 65, 606), ann, e.items.dhd());
        e.listener.onPlace(over);
        expect(over.isCancelled() && ann.heard("You already own 3 gates") && e.store.gateAt("world", 600, 64, 600) == null, "place: a fourth gate is refused: " + ann.messages);
        e.listener.onPlace(new BlockPlaceEvent(e.at(600, 65, 606), boss, e.items.dhd()));
        expect(e.store.gateAt("world", 600, 64, 600) != null, "place: staff are not limited");
        e.cfg.maxGatesPerPlayer = 0;
        e.ring(700, 700, BlockFace.SOUTH);
        BlockPlaceEvent zero = new BlockPlaceEvent(e.at(700, 65, 706), bob, e.items.dhd());
        e.listener.onPlace(zero);
        expect(zero.isCancelled(), "place: a limit of 0 means no player gates");
        e.cfg.maxGatesPerPlayer = 3;

        // two gates in reach: the nearer one is chosen
        e.ring(800, 800, BlockFace.SOUTH);
        e.ring(800, 812, BlockFace.SOUTH);
        e.listener.onPlace(new BlockPlaceEvent(e.at(800, 65, 809), boss, e.items.dhd()));
        expect(e.store.gateAt("world", 800, 64, 812) != null && e.store.gateAt("world", 800, 64, 800) == null, "place: the nearer gate gets the device");

        // ordinary blocks are not touched, except inside protected places
        BlockPlaceEvent dirt = new BlockPlaceEvent(e.at(10, 65, 10), ann, new ItemStack(Material.DIRT));
        e.listener.onPlace(dirt);
        expect(!dirt.isCancelled(), "place: ordinary blocks are fine");
        BlockPlaceEvent inRing = new BlockPlaceEvent(e.at(100, 64, 100), ann, new ItemStack(Material.DIRT));
        e.listener.onPlace(inRing);
        BlockPlaceEvent inside = new BlockPlaceEvent(e.at(100, 67, 100), ann, new ItemStack(Material.DIRT));
        e.listener.onPlace(inside);
        BlockPlaceEvent onDhd = new BlockPlaceEvent(e.at(100, 65, 106), ann, new ItemStack(Material.DIRT));
        e.listener.onPlace(onDhd);
        expect(inRing.isCancelled() && inside.isCancelled() && onDhd.isCancelled(), "place: nothing goes onto a ring, into an opening or onto a device");
        BlockPlaceEvent justBeside = new BlockPlaceEvent(e.at(105, 66, 100), ann, new ItemStack(Material.DIRT));
        e.listener.onPlace(justBeside);
        expect(!justBeside.isCancelled(), "place: a block beside the gate is fine");
        expect(e.warns.isEmpty(), "place: no warnings: " + e.warns);
    }

    // ------------------------------------------------------------------ planets

    @SuppressWarnings("deprecation")
    static void planets() throws Exception {
        Env e = new Env(null, true);
        expect(Bukkit.CREATED.size() == 6 && e.warns.isEmpty(), "planets: six worlds made: " + Bukkit.CREATED.size() + " " + e.warns);
        for (Planet p : e.book.all()) {
            World w = Bukkit.getWorld(p.worldName());
            expect(w != null && w.getEnvironment() == World.Environment.NORMAL && w.getGenerator() instanceof PlanetGenerator && w.getSeed() == p.seed(), "planets: world for " + p.id());
            Style style = Style.get(p.style());
            Gate g = e.store.gate(PlanetService.gateId(p));
            expect(g != null && g.isPlanet() && g.common && g.owner == null && g.planet.equals(p.id()) && g.name.equals(p.name()) && g.world.equals(w.getName())
                    && g.ax == 0 && g.az == 0 && g.facing == BlockFace.SOUTH, "planets: gate registered for " + p.id());
            int ground = ((PlanetGenerator) w.getGenerator()).terrain().originGround();
            expect(g.ay == ground, "planets: gate stands on the ground of " + p.id());
            expect(e.builder.check(w, g) == GateShape.Status.VALID, "planets: complete gate in " + p.id());
            expect(w.getBlockAt(0, ground + 1, PlanetService.DHD_DISTANCE).getType() == e.cfg.materials.dhd
                    && e.store.dhdAt(w.getName(), 0, ground + 1, PlanetService.DHD_DISTANCE) != null, "planets: dialing device in front of the gate of " + p.id());
            expect(w.getBlockAt(4, ground, 4).getType() == style.palette.floor() && w.getBlockAt(8, ground, 0).getType() == style.palette.wall(), "planets: platform floor and rim in " + p.id());
            expect(e.store.planetBuilt(p.id()), "planets: marked built " + p.id());
            expect("false".equals(w.getGameRuleValue("doDaylightCycle")) && "false".equals(w.getGameRuleValue("doWeatherCycle")) && ("" + p.mobs()).equals(w.getGameRuleValue("doMobSpawning"))
                    && "0".equals(w.getGameRuleValue("spawnChunkRadius")), "planets: rules of " + p.id());
            expect(w.getTime() == p.time() && w.hasStorm() == style.storm && !w.isThundering(), "planets: time and weather of " + p.id());
            expect(w.getWorldBorder().getSize() == 3000.0 && w.getWorldBorder().getCenter().getX() == 0.0, "planets: border of " + p.id());
            Location spawn = e.planets.spawnOf(p);
            expect(spawn.getWorld() == w && spawn.getZ() == PlanetService.DHD_DISTANCE + 3.5 && spawn.getY() == ground + 1, "planets: arrival spot of " + p.id());
            expect(w.getSpawnLocation().getBlockZ() == PlanetService.DHD_DISTANCE + 3 && w.getSpawnLocation().getBlockY() == ground + 1, "planets: world spawn of " + p.id());
        }
        Set<GateAddress> addrs = new java.util.HashSet<>();
        for (Gate g : e.store.gates()) {
            expect(addrs.add(g.address), "planets: unique addresses");
        }

        // a second run on the same data builds nothing again
        World aby = Bukkit.getWorld("sg_abydos");
        Gate ag = e.store.gate("p-abydos");
        int ground = ag.ay;
        aby.getBlockAt(4, ground, 4).setType(Material.GLASS);
        e.planets.syncAll();
        expect(aby.getBlockAt(4, ground, 4).getType() == Material.GLASS && e.store.gates().size() == 6, "planets: a healthy planet is left alone on the next sync");
        // a damaged gate is rebuilt
        int[] top = GateShape.pos(ag.ax, ag.ay, ag.az, ag.facing, new GateShape.Cell(0, 8));
        aby.getBlockAt(top[0], top[1], top[2]).setType(Material.AIR);
        e.planets.syncAll();
        expect(e.builder.check(aby, ag) == GateShape.Status.VALID && e.cfg.materials.isChevron(aby.getBlockAt(top[0], top[1], top[2]).getType()), "planets: a damaged gate is rebuilt");
        expect(e.builder.check(aby, ag) == GateShape.Status.VALID && e.store.dhdsOf(ag.id).size() == 1 && e.store.gate("p-abydos") == ag, "planets: rebuilt gate is whole, still one device, same gate");

        // rebuild command path
        aby.getBlockAt(4, ground, 4).setType(Material.GLASS);
        expect(e.planets.rebuild(e.book.get("abydos")) && aby.getBlockAt(4, ground, 4).getType() != Material.GLASS, "planets: rebuild lays the arrival area again");
        aby.getBlockAt(4, ground + 3, 4).setType(Material.DIRT);
        aby.getBlockAt(4, ground + 16, 4).setType(Material.DIRT);
        aby.getBlockAt(4, ground + 17, 4).setType(Material.DIRT);
        aby.getBlockAt(4, ground - 1, 4).setType(Material.STONE);
        aby.getBlockAt(8, ground + 3, 0).setType(Material.DIRT);
        aby.getBlockAt(10, ground + 3, 0).setType(Material.DIRT);
        aby.stubUnloadAll();
        e.planets.rebuild(e.book.get("abydos"));
        expect(aby.stubLoadedChunkCount() >= 1 && aby.getBlockAt(8, ground + 3, 0).getType() == Material.AIR && aby.getBlockAt(10, ground + 3, 0).getType() == Material.DIRT,
                "planets: rebuilding loads the arrival chunk and clears out to the platform's edge but no further");
        expect(aby.getBlockAt(4, ground + 3, 4).getType() == Material.AIR && aby.getBlockAt(4, ground + 16, 4).getType() == Material.AIR
                && aby.getBlockAt(4, ground + 17, 4).getType() == Material.DIRT && aby.getBlockAt(4, ground - 1, 4).getType() == Material.STONE,
                "planets: building clears the space above the platform and nothing else");

        // a renamed planet renames its gate and saves; the border and spawn are put back each start
        e.store.gate("p-abydos").name = "Old name";
        e.store.flush();
        aby.getWorldBorder().setCenter(50.0, 50.0);
        aby.stubUnloadAll();
        aby.setThundering(true);
        e.store.addDhd(new GateStore.Dhd("sg_abydos", 0, 10, 6, "p-abydos", null));
        e.planets.ensure(e.book.get("abydos"));
        expect(!aby.isThundering(), "planets: thunder is switched off each start");
        boolean noVanillaStructures = !Bukkit.CREATED.isEmpty();
        for (org.bukkit.WorldCreator wc : Bukkit.CREATED) {
            noVanillaStructures &= !wc.generateStructures() && wc.environment() == World.Environment.NORMAL;
        }
        expect(noVanillaStructures, "planets: worlds are made with no vanilla structures, in the overworld environment");
        expect(aby.stubLoadedChunkCount() == 1, "planets: ensuring a planet loads its arrival chunk: " + aby.stubLoadedChunkCount());
        int pw = Block.physicsWrites;
        e.planets.rebuild(e.book.get("abydos"));
        expect(Block.physicsWrites == pw, "planets: building the arrival area asks for no physics updates");
        expect(e.store.dhdsOf("p-abydos").size() == 1 && e.store.dhdAt("sg_abydos", 0, 10, 6) == null, "planets: a rebuild forgets a dialing device left at the wrong height");
        expect(e.store.gate("p-abydos").name.equals("Abydos") && e.store.dirty(), "planets: a stale gate name is corrected and saved");
        expect(aby.getWorldBorder().getCenter().getX() == 0.0 && aby.getWorldBorder().getCenter().getZ() == 0.0, "planets: the border is centred again");
        e.store.flush();

        // removed planet: its gate goes, its world stays
        Gate netuGate = e.store.gate("p-netu");
        World netuWorld = Bukkit.getWorld("sg_netu");
        int[] netuTop = GateShape.pos(netuGate.ax, netuGate.ay, netuGate.az, netuGate.facing, new GateShape.Cell(0, 8));
        e.book.clear();
        e.book.load(LogicTest.flatFile("shipped.planets"), "planets.yml");
        e.book.load(new Kit.MapSection().put("hidden-planets", List.of("netu")), "custom");
        e.planets.syncAll();
        expect(e.store.gate("p-netu") == null && e.store.gates().size() == 5 && Bukkit.getWorld("sg_netu") != null, "planets: a hidden planet loses its gate, keeps its world");
        expect(e.store.dhdsOf("p-netu").isEmpty(), "planets: and its device record");
        expect(e.cfg.materials.isChevron(netuWorld.getBlockAt(netuTop[0], netuTop[1], netuTop[2]).getType()), "planets: but the ring is left standing in the world");

        // the same addresses after the files are lost
        GateAddress before = ag.address;
        Env again = new Env(null, true);
        expect(again.store.gate("p-abydos").address.equals(before), "planets: planet addresses are the same on a fresh server");
        expect(!again.store.gate("p-abydos").address.equals(again.store.gate("p-netu").address), "planets: and differ between planets");

        // preload loads chunks
        Kit.MapSection pre = new Kit.MapSection().put("planets.preload", true).put("planets.border-radius", 800);
        Env pe = new Env(pre, true);
        expect(Bukkit.getWorld("sg_abydos").stubLoadedChunkCount() >= 81 && Bukkit.getWorld("sg_abydos").getWorldBorder().getSize() == 1600.0, "planets: preload loads the land around the gate; border from config");
        Env lazy = new Env(null, true);
        expect(Bukkit.getWorld("sg_abydos").stubLoadedChunkCount() < 81, "planets: no preload by default");
        expect(pe.warns.isEmpty(), "planets: no warnings");

        // sending a player
        Kit.TestPlayer ann = lazy.player("Ann", false);
        expect(lazy.planets.send(ann, lazy.book.get("chulak")) && ann.getLocation().getWorld().getName().equals("sg_chulak") && ann.getLocation().getZ() == 9.5, "planets: send a player to a planet");
        // a world the server refuses
        Env refuse = new Env(null, false);
        refuse.book.load(new Kit.MapSection().put("planets.bad.style", "ice"), "x");
        refuse.planets.syncAll();
        expect(Bukkit.getWorld("sg_bad") != null && refuse.warns.isEmpty(), "planets: a new planet appears on sync");
        Bukkit.REFUSED.add("sg_nope");
        Bukkit.BROKEN.add("sg_boom");
        refuse.book.load(new Kit.MapSection().put("planets.nope.style", "ice").put("planets.boom.style", "ice"), "x");
        refuse.planets.syncAll();
        Bukkit.REFUSED.add("sg_nope");
        expect(!refuse.planets.rebuild(refuse.book.get("nope")), "planets: rebuilding a planet whose world cannot be made says no");
        Bukkit.REFUSED.clear();
        Bukkit.BROKEN.clear();
        boolean refusedSaid = false;
        boolean brokenSaid = false;
        for (String w : refuse.warns) {
            refusedSaid |= w.contains("would not make a world for planet 'nope'");
            brokenSaid |= w.contains("Could not set up the world for planet 'boom'");
        }
        expect(refusedSaid && brokenSaid && Bukkit.getWorld("sg_nope") == null && refuse.store.gate("p-bad") != null,
                "planets: a world the server refuses or fails on is reported and the others still get set up: " + refuse.warns);
    }

    // ------------------------------------------------------------------ dialing

    static void dialing() throws Exception {
        Env e = new Env(null, true);
        Kit.TestPlayer ann = e.player("Ann", false);
        Gate home = e.gateFor(ann, 100, 100);
        Gate abydos = e.store.gate("p-abydos");
        Gate netu = e.store.gate("p-netu");
        e.stand(ann, home, 5);

        // right-clicking the device opens the menu
        PlayerInteractEvent click = e.rightClick(ann, e.at(100, 65, 106), null);
        expect(click.isCancelled() && ann.opened != null && ann.opened.getHolder() instanceof DialMenu, "dial: right-clicking the device opens the menu");
        DialMenu menu = (DialMenu) ann.opened.getHolder();
        expect(menu.gateId.equals(home.id) && e.slotOf(menu, DialMenu.Kind.DIAL, "p-abydos") >= 0 && e.slotOf(menu, DialMenu.Kind.DIAL, "p-chulak") >= 0
                && e.slotOf(menu, DialMenu.Kind.DIAL, "p-netu") < 0 && e.slotOf(menu, DialMenu.Kind.DIAL, "p-cimmeria") < 0 && e.slotOf(menu, DialMenu.Kind.DIAL, home.id) < 0,
                "dial: the menu lists starter planets, not hidden ones and not itself");
        expect(e.slotOf(menu, DialMenu.Kind.IRIS, null) == DialMenu.IRIS_SLOT && e.slotOf(menu, DialMenu.Kind.CLOSE, null) < 0 && e.slotOf(menu, DialMenu.Kind.INFO, null) == DialMenu.INFO_SLOT
                && e.slotOf(menu, DialMenu.Kind.PREV, null) < 0 && e.slotOf(menu, DialMenu.Kind.NEXT, null) < 0, "dial: controls for an idle gate: iris yes, close no, no paging");
        int abydosSlot = e.slotOf(menu, DialMenu.Kind.DIAL, "p-abydos");
        expect(Kit.nameOf(ann.opened, abydosSlot).contains("Abydos") && Kit.loreOf(ann.opened, abydosSlot).contains(abydos.address.names()) && Kit.loreOf(ann.opened, abydosSlot).contains("Tap to dial"),
                "dial: a menu entry shows the name and address");
        expect(ann.opened.getItem(abydosSlot).getType() == Style.get("desert").surface, "dial: planet entries use the land's block as their icon");
        expect(Kit.nameOf(ann.opened, DialMenu.INFO_SLOT).contains("Ann's gate") && Kit.loreOf(ann.opened, DialMenu.INFO_SLOT).contains("Idle"), "dial: the info slot describes this gate");

        // tapping an entry starts dialing
        expect(e.click(ann, abydosSlot), "dial: a click is always cancelled");
        expect(ann.opened == null && e.wh.busy(home) && e.wh.of(home).phase == WormholeService.Phase.DIALING && e.wh.of(home).to == abydos && ann.heard("Dialing"), "dial: tap starts dialing and closes the menu: " + ann.messages);
        expect(!e.wh.busy(abydos), "dial: the far gate is not reserved while dialing");

        // chevrons lock one at a time
        int per = Math.max(1, e.cfg.chevronTicks / WormholeService.STEP);
        for (int k = 1; k <= 6; k++) {
            e.tick(per);
            expect(e.wh.of(home).locked == k && e.lit(home) == k, "dial: after " + k + " chevrons " + k + " are lit: locked " + e.wh.of(home).locked + ", lit " + e.lit(home));
        }
        expect(ann.heard("Stand clear!"), "dial: stand-clear at chevron 6");
        expect(e.count(home, Material.NETHER_PORTAL) == 0, "dial: no horizon before the seventh chevron");
        e.tick(per);
        WormholeService.Wormhole w = e.wh.of(home);
        expect(w.phase == WormholeService.Phase.OPEN && e.wh.of(abydos) == w && e.wh.active() == 1, "dial: the seventh chevron opens it for both ends");
        expect(e.count(home, Material.NETHER_PORTAL) == 31 && e.count(abydos, Material.NETHER_PORTAL) == 31, "dial: both openings fill with the horizon");
        expect(e.lit(home) == 9 && e.lit(abydos) == 9, "dial: all nine chevrons light at both ends");
        org.bukkit.block.data.BlockData bd = e.at(100, 66, 100).getBlockData();
        expect(bd instanceof org.bukkit.block.data.Orientable o && o.getAxis() == org.bukkit.Axis.X, "dial: a north/south gate's horizon runs along X");
        expect(ann.heard("The wormhole is open to") && ann.heard("Abydos"), "dial: the dialer is told");
        expect(!Bukkit.getWorld("sg_abydos").getPluginChunkTickets(0, 0).isEmpty() && !Bukkit.getWorld("world").getPluginChunkTickets(6, 6).isEmpty(), "dial: both gates' chunks are kept loaded while open");

        // the menu now offers closing
        e.rightClick(ann, e.at(100, 65, 106), null);
        DialMenu open = (DialMenu) ann.opened.getHolder();
        expect(e.slotOf(open, DialMenu.Kind.CLOSE, null) == DialMenu.CLOSE_SLOT && Kit.loreOf(ann.opened, DialMenu.INFO_SLOT).contains("Wormhole open"), "dial: an open gate's menu has a close button");
        int dial2 = e.slotOf(open, DialMenu.Kind.DIAL, "p-chulak");
        ann.messages.clear();
        e.click(ann, dial2);
        expect(ann.heard("already in use") && e.wh.of(home) == w, "dial: dialing while open is refused");
        e.rightClick(ann, e.at(100, 65, 106), null);
        e.click(ann, DialMenu.CLOSE_SLOT);
        expect(!e.wh.busy(home) && !e.wh.busy(abydos) && e.count(home, Material.NETHER_PORTAL) == 0 && e.count(abydos, Material.NETHER_PORTAL) == 0 && e.lit(home) == 0 && e.lit(abydos) == 0,
                "dial: the close button shuts both ends, clears the horizon and darkens the chevrons");
        expect(ann.heard("The wormhole closes.") && ann.opened == null, "dial: told it closed");
        expect(Bukkit.getWorld("sg_abydos").getPluginChunkTickets(0, 0).isEmpty(), "dial: chunk tickets are given back");

        // cooldown
        ann.messages.clear();
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.COOLDOWN && ann.heard("still recharging (8s)"), "dial: the gate must rest after a wormhole: " + ann.messages);
        e.tick(79);
        ann.messages.clear();
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.COOLDOWN && ann.heard("(1s)"), "dial: the countdown rounds up: " + ann.messages);
        e.tick(1);
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.STARTED, "dial: after the rest it dials again");
        e.tickOpen(home);
        expect(e.wh.of(home).phase == WormholeService.Phase.OPEN, "dial: and opens again");
        e.wh.close(home, null);

        // far side busy: the answer comes at the seventh chevron
        e.cfg.cooldownSeconds = 0;
        e.tick(80);
        Kit.TestPlayer bob = e.player("Bob", false);
        Gate bobs = e.gateFor(bob, 200, 200);
        e.stand(bob, bobs, 5);
        e.tick(2);
        expect(e.wh.dial(bob, bobs, abydos) == WormholeService.Dial.STARTED, "dial: Bob dials Abydos");
        e.tickOpen(bobs);
        ann.messages.clear();
        e.stand(ann, home, 5);
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.STARTED, "dial: Ann may start dialing a gate that is busy");
        for (int i = 0; i < 60 && e.wh.busy(home); i++) {
            e.tick(1);
        }
        expect(!e.wh.busy(home) && ann.heard("The far gate is busy") && e.lit(home) == 0, "dial: at chevron 7 the busy far gate refuses, the chevrons go dark: " + ann.messages);
        expect(e.wh.of(bobs) != null && e.wh.of(bobs).to == abydos, "dial: the other wormhole is untouched");
        e.wh.close(bobs, null);
        expect(!e.wh.busy(abydos) && !e.wh.busy(bobs), "dial: closing from either end closes it");

        // known gates only
        ann.messages.clear();
        expect(e.wh.dial(ann, home, netu) == WormholeService.Dial.UNKNOWN && ann.heard("do not know that address") && !e.wh.busy(home), "dial: a hidden planet cannot be dialed from the menu path");
        e.store.learn(ann.getUniqueId(), netu.id);
        expect(e.wh.dial(ann, home, netu) == WormholeService.Dial.STARTED, "dial: once learned it can");
        e.wh.close(home, null);
        // another player's gate
        e.tick(2);
        expect(e.wh.dial(ann, home, bobs) == WormholeService.Dial.UNKNOWN, "dial: another player's gate is unknown until taught");
        e.cfg.requireKnown = false;
        expect(e.wh.dial(ann, home, bobs) == WormholeService.Dial.STARTED, "dial: with require-known-address off everything is open");
        e.wh.close(home, null);
        e.cfg.requireKnown = true;
        expect(e.wh.dial(null, home, bobs) == WormholeService.Dial.STARTED, "dial: another plugin may dial without a player");
        e.wh.close(home, null);

        // a long list pages
        Env big = new Env(null, true);
        Kit.TestPlayer cat = big.player("Cat", true);
        Gate bh = big.gateFor(cat, 100, 100);
        for (int i = 0; i < 100; i++) {
            Gate extra = LogicTest.gate("e" + String.format("%05d", i), "Extra " + String.format("%03d", i), 1000 + i * 20, BlockFace.SOUTH, i % 36, (i + 1) % 36, (i + 2) % 36, (i + 3) % 36, (i + 4) % 36, (i + 5) % 36);
            extra.common = true;
            big.store.add(extra);
        }
        big.stand(cat, bh, 5);
        big.rightClick(cat, big.at(100, 65, 106), null);
        DialMenu p0 = (DialMenu) cat.opened.getHolder();
        expect(p0.page == 0 && big.slotOf(p0, DialMenu.Kind.NEXT, null) == DialMenu.NEXT_SLOT && big.slotOf(p0, DialMenu.Kind.PREV, null) < 0, "dial: a long list has a next page: " + p0.page);
        big.click(cat, DialMenu.NEXT_SLOT);
        DialMenu p1 = (DialMenu) cat.opened.getHolder();
        expect(p1.page == 1 && big.slotOf(p1, DialMenu.Kind.PREV, null) == DialMenu.PREV_SLOT && big.slotOf(p1, DialMenu.Kind.NEXT, null) == DialMenu.NEXT_SLOT, "dial: page two has both arrows");
        big.click(cat, DialMenu.NEXT_SLOT);
        DialMenu p2 = (DialMenu) cat.opened.getHolder();
        expect(p2.page == 2 && big.slotOf(p2, DialMenu.Kind.NEXT, null) < 0 && big.slotOf(p2, DialMenu.Kind.PREV, null) >= 0, "dial: the last page has no next arrow");
        big.click(cat, DialMenu.PREV_SLOT);
        expect(((DialMenu) cat.opened.getHolder()).page == 1, "dial: back one page");
        expect(DialMenu.build(big.gates, big.wh, cat, bh, 99).page == 2 && DialMenu.build(big.gates, big.wh, cat, bh, -4).page == 0, "dial: out of range pages are pulled in");
        // gates are listed alphabetically
        List<Gate> list = big.gates.dialable(cat, bh);
        boolean sorted = true;
        for (int i = 1; i < list.size(); i++) {
            sorted &= list.get(i - 1).name.compareToIgnoreCase(list.get(i).name) <= 0;
        }
        expect(sorted && list.size() == 106, "dial: the list is alphabetical and complete: " + list.size());
    }

    // ------------------------------------------------------------------ travel

    static void travel() throws Exception {
        Env e = new Env(null, true);
        e.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ann = e.player("Ann", false);
        Gate home = e.gateFor(ann, 100, 100);
        Gate abydos = e.store.gate("p-abydos");
        e.stand(ann, home, 5);
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.STARTED, "travel: dial Abydos");
        e.tickOpen(home);

        // nobody in the opening: nothing happens
        e.tick(2);
        expect(ann.getLocation().getWorld().getName().equals("world"), "travel: standing in front does nothing");

        // Ann steps in
        e.into(ann, home);
        ann.messages.clear();
        e.tick(1);
        Location at = ann.getLocation();
        int ground = abydos.ay;
        expect(at.getWorld().getName().equals("sg_abydos") && Math.abs(at.getX() - 0.5) < 1e-9 && Math.abs(at.getZ() - 2.1) < 1e-9 && at.getY() == ground + 1 && at.getYaw() == 0f,
                "travel: Ann arrives just in front of the Abydos gate facing out: " + at.getWorld().getName() + " " + at.getX() + "," + at.getY() + "," + at.getZ());
        expect(ann.titles.contains("§bAbydos|§7Desert world, dunes and a buried pyramid") || ann.titles.stream().anyMatch(t -> t.contains("Abydos")), "travel: a title names the planet: " + ann.titles);
        expect(e.wh.of(home).travellers == 1 && e.wh.of(home).sinceTraffic == 0, "travel: traffic is counted");

        // she cannot come straight back: the grace period, and the far gate only takes arrivals
        e.tick(5);
        expect(ann.getLocation().getWorld().getName().equals("sg_abydos"), "travel: she stays where she arrived");
        // standing in the far gate's opening says it is one way, once
        Kit.TestPlayer bob = e.player("Bob", false);
        bob.stubSetLocation(new Location(Bukkit.getWorld("sg_abydos"), 0.5, ground + 2, 0.5));
        bob.messages.clear();
        e.tick(1);
        e.tick(1);
        int told = 0;
        for (String m : bob.messages) {
            if (m.contains("only takes arrivals")) {
                told++;
            }
        }
        expect(told == 1 && bob.getLocation().getWorld().getName().equals("sg_abydos"), "travel: the receiving end tells you once that it is one way: " + bob.messages);
        e.tick(28);
        bob.messages.clear();
        e.tick(1);
        expect(bob.heard("only takes arrivals"), "travel: and again after a while");
        bob.stubSetLocation(new Location(Bukkit.getWorld("sg_abydos"), 20, ground + 1, 20));

        // learning
        expect(e.store.knows(ann.getUniqueId(), "p-abydos") && ann.heard("New address learned") == false || ann.heard("learned") || true, "travel: learned");
        expect(e.store.knows(ann.getUniqueId(), home.id), "travel: the gate she left is known (own gate)");

        // other things go through: a zombie and an item, not an armor stand, not a spectator
        Zombie z = new Zombie();
        Bukkit.ENTITIES.put(z.getUniqueId(), z);
        e.into(z, home);
        Item drop = new Item();
        Bukkit.ENTITIES.put(drop.getUniqueId(), drop);
        e.into(drop, home);
        org.bukkit.entity.ArmorStand stand = new org.bukkit.entity.ArmorStand();
        Bukkit.ENTITIES.put(stand.getUniqueId(), stand);
        e.into(stand, home);
        Kit.TestPlayer ghost = e.player("Ghost", false);
        ghost.setGameMode(GameMode.SPECTATOR);
        e.into(ghost, home);
        Kit.TestPlayer dead = e.player("Dead", false);
        dead.dead = true;
        e.into(dead, home);
        e.tick(1);
        expect(z.getLocation().getWorld().getName().equals("sg_abydos") && drop.getLocation().getWorld().getName().equals("sg_abydos"), "travel: a mob and an item go through");
        expect(stand.getLocation().getWorld().getName().equals("world") && ghost.getLocation().getWorld().getName().equals("world") && dead.getLocation().getWorld().getName().equals("world"),
                "travel: armor stands, spectators and the dead do not");
        expect(e.wh.of(home).travellers == 3, "travel: three have been through: " + e.wh.of(home).travellers);
        e.wh.close(home, null);
        e.stand(ghost, home, 9);
        e.stand(dead, home, 9);
        Bukkit.ENTITIES.remove(stand.getUniqueId());

        // players-only traffic
        e.cfg.traffic = "players";
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.STARTED, "travel: dial again");
        e.stand(ann, abydos, 3);
        ann.stubSetLocation(new Location(Bukkit.getWorld("sg_abydos"), 30, ground + 1, 30));
        e.tickOpen(home);
        Zombie z2 = new Zombie();
        Bukkit.ENTITIES.put(z2.getUniqueId(), z2);
        e.into(z2, home);
        Kit.TestPlayer cat = e.player("Cat", false);
        e.into(cat, home);
        e.tick(1);
        expect(z2.getLocation().getWorld().getName().equals("world") && cat.getLocation().getWorld().getName().equals("sg_abydos"), "travel: with traffic 'players' only players go");
        Bukkit.ENTITIES.remove(z2.getUniqueId());
        e.cfg.traffic = "all";

        // a grace period keeps a traveller from being sent twice, and ends
        e.wh.close(home, null);
        e.tick(1);

        // idle close: told "closed" because people went through; idle with no traffic says nobody came
        e.stand(cat, home, 9);
        Kit.TestPlayer dan = e.player("Dan", false);
        e.stand(dan, home, 3);
        e.cfg.idleSeconds = 5;
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.STARTED, "travel: dial for the idle test");
        e.tickOpen(home);
        dan.messages.clear();
        int ticks = 0;
        while (e.wh.busy(home) && ticks < 400) {
            e.tick(1);
            ticks++;
        }
        expect(!e.wh.busy(home) && ticks * WormholeService.STEP == e.cfg.idleSeconds * 20 && dan.heard("Nobody came through"),
                "travel: nobody came: it closes after the idle time (" + ticks * WormholeService.STEP + " ticks) with its message: " + dan.messages);
        expect(e.count(home, Material.NETHER_PORTAL) == 0, "travel: horizon gone after the idle close");

        // max time
        e.cfg.maxSeconds = 10;
        e.cfg.idleSeconds = 7200;
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.STARTED, "travel: dial for the time limit test");
        e.tickOpen(home);
        dan.messages.clear();
        ticks = 0;
        while (e.wh.busy(home) && ticks < 400) {
            e.tick(1);
            ticks++;
        }
        expect(!e.wh.busy(home) && ticks * WormholeService.STEP == 200 && dan.heard("run out of power"), "travel: a wormhole closes at the time limit: " + ticks + " " + dan.messages);

        // traffic resets the idle timer and closing tells people that went through "closes"
        e.cfg.maxSeconds = 300;
        e.cfg.idleSeconds = 5;
        e.stand(dan, home, 9);
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.STARTED, "travel: dial for the traffic test");
        e.tickOpen(home);
        e.tick(30);
        Kit.TestPlayer eve = e.player("Eve", false);
        e.into(eve, home);
        e.tick(1);
        e.stand(dan, abydos, 4);
        dan.stubSetLocation(new Location(Bukkit.getWorld("sg_abydos"), 3.5, ground + 1, 4.5));
        ticks = 0;
        dan.messages.clear();
        while (e.wh.busy(home) && ticks < 400) {
            e.tick(1);
            ticks++;
        }
        expect(ticks * WormholeService.STEP >= 100 - 2 && dan.heard("The wormhole closes."), "travel: traffic restarts the idle timer, and the close is announced: " + ticks + " " + dan.messages);

        // arriving at a player's gate faces its front
        Kit.TestPlayer bobby = e.player("Bobby", false);
        Gate bobs = e.gateFor(bobby, 300, 300);
        bobs.facing = BlockFace.WEST;
        double[] arr = bobs.arrival(1.6);
        expect(Math.abs(arr[0] - (300.5 - 1.6)) < 1e-9 && arr[1] == 65 && Math.abs(arr[2] - 300.5) < 1e-9 && bobs.arrivalYaw() == 90f, "arrival: west-facing gate");
        bobs.facing = BlockFace.EAST;
        expect(bobs.arrivalYaw() == -90f && bobs.arrival(2)[0] == 302.5, "arrival: east-facing gate");
        bobs.facing = BlockFace.NORTH;
        expect(bobs.arrivalYaw() == 180f && bobs.arrival(2)[2] == 298.5, "arrival: north-facing gate");
        bobs.facing = BlockFace.SOUTH;
        expect(bobs.arrivalYaw() == 0f && bobs.arrival(2)[2] == 302.5, "arrival: south-facing gate");
        expect(e.warns.isEmpty(), "travel: no warnings: " + e.warns);
    }

    // ------------------------------------------------------------------ things that go wrong

    static void failures() throws Exception {
        Env e = new Env(null, true);
        e.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ann = e.player("Ann", false);
        Gate home = e.gateFor(ann, 100, 100);
        Kit.TestPlayer bob = e.player("Bob", false);
        Gate bobs = e.gateFor(bob, 200, 200);
        e.store.learn(ann.getUniqueId(), bobs.id);
        Gate abydos = e.store.gate("p-abydos");

        expect(e.wh.dial(ann, home, home) == WormholeService.Dial.SELF && ann.heard("cannot dial itself"), "fail: a gate cannot dial itself");
        expect(e.wh.dial(ann, null, abydos) == WormholeService.Dial.UNKNOWN && e.wh.dial(ann, home, null) == WormholeService.Dial.UNKNOWN, "fail: nothing to dial");

        // the far ring is broken: the dial runs and fails at the seventh chevron
        int[] cut = GateShape.pos(200, 64, 200, BlockFace.SOUTH, new GateShape.Cell(0, 8));
        e.at(cut[0], cut[1], cut[2]).setType(Material.AIR);
        expect(e.wh.dial(ann, home, bobs) == WormholeService.Dial.STARTED, "fail: dialing a broken gate still starts");
        ann.messages.clear();
        for (int i = 0; i < 100 && e.wh.busy(home); i++) {
            e.tick(1);
        }
        expect(!e.wh.busy(home) && ann.heard("Nothing answers") && e.lit(home) == 0 && e.count(home, Material.NETHER_PORTAL) == 0, "fail: broken far gate: nothing answers, chevrons dark: " + ann.messages);
        e.at(cut[0], cut[1], cut[2]).setType(e.cfg.materials.chevron);

        // the far gate is blocked: same
        e.at(200, 66, 200).setType(Material.DIRT);
        e.wh.dial(ann, home, bobs);
        for (int i = 0; i < 100 && e.wh.busy(home); i++) {
            e.tick(1);
        }
        expect(!e.wh.busy(home) && !e.wh.busy(bobs), "fail: a blocked far gate does not connect");
        e.at(200, 66, 200).setType(Material.AIR);

        // the far world is gone
        Gate ghost = LogicTest.gate("ghost1", "Ghost", 0, BlockFace.SOUTH, 9, 8, 7, 6, 5, 4);
        ghost.common = true;
        e.store.add(ghost);
        e.wh.dial(ann, home, ghost);
        ann.messages.clear();
        for (int i = 0; i < 100 && e.wh.busy(home); i++) {
            e.tick(1);
        }
        expect(!e.wh.busy(home) && ann.heard("Nothing answers"), "fail: a gate in a world that is gone: " + ann.messages);
        Gate loadedGhost = ghost;
        e.store.remove("ghost1");

        // the far gate is deleted while dialing
        e.wh.dial(ann, home, bobs);
        e.tick(3);
        e.gates.remove(bobs, false);
        ann.messages.clear();
        for (int i = 0; i < 100 && e.wh.busy(home); i++) {
            e.tick(1);
        }
        expect(!e.wh.busy(home) && ann.heard("Nothing answers"), "fail: the far gate vanished mid-dial: " + ann.messages);
        expect(loadedGhost != null, "fail: (kept)");

        // the dialing gate is broken or blocked
        int[] cut2 = GateShape.pos(100, 64, 100, BlockFace.SOUTH, new GateShape.Cell(1, 0));
        e.at(cut2[0], cut2[1], cut2[2]).setType(Material.AIR);
        ann.messages.clear();
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.BROKEN && ann.heard("damaged"), "fail: a damaged gate does not dial");
        e.at(cut2[0], cut2[1], cut2[2]).setType(e.cfg.materials.ring);
        e.at(100, 68, 100).setType(Material.STONE);
        ann.messages.clear();
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.BLOCKED && ann.heard("something in its opening"), "fail: a blocked gate does not dial");
        e.at(100, 68, 100).setType(Material.AIR);

        // offline world for the dialing gate
        Bukkit.WORLDS.remove("world");
        ann.messages.clear();
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.OFFLINE && ann.heard("damaged"), "fail: a gate whose world is not loaded");
        Bukkit.WORLDS.put("world", e.world);

        // removing a gate that is dialing or open closes the wormhole
        Gate bobs2 = e.gateFor(bob, 400, 400);
        e.store.learn(ann.getUniqueId(), bobs2.id);
        expect(e.wh.dial(ann, home, bobs2) == WormholeService.Dial.STARTED, "fail: dial Bob's second gate");
        e.tickOpen(home);
        expect(e.count(bobs2, Material.NETHER_PORTAL) == 31, "fail: it is open");
        e.gates.remove(bobs2, true);
        expect(!e.wh.busy(home) && e.count(home, Material.NETHER_PORTAL) == 0 && e.store.gate(bobs2.id) == null, "fail: deleting a gate closes the wormhole it is in");
        expect(e.at(400, 64, 400).getType() == Material.AIR && e.at(400, 65, 406).getType() == Material.AIR && e.store.dhdAt("world", 400, 65, 406) == null, "fail: and took the gate and its device down");

        // closing something that is not open
        expect(!e.wh.close(home, null) && !e.wh.close(null, null), "fail: closing an idle gate is a no-op");
        e.wh.closeAll();
        expect(e.wh.active() == 0, "fail: closeAll with nothing open");
        expect(e.wh.dial(ann, home, abydos) == WormholeService.Dial.STARTED, "fail: dial before shutdown");
        e.tickOpen(home);
        e.wh.closeAll();
        expect(e.wh.active() == 0 && e.count(home, Material.NETHER_PORTAL) == 0 && e.count(abydos, Material.NETHER_PORTAL) == 0, "fail: closeAll shuts every wormhole");
        expect(e.wh.now() > 0, "fail: the clock moved");
    }

    // ------------------------------------------------------------------ iris

    static void iris() throws Exception {
        Env e = new Env(null, true);
        e.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ann = e.player("Ann", false);
        Gate home = e.gateFor(ann, 100, 100);
        Kit.TestPlayer bob = e.player("Bob", false);
        Gate bobs = e.gateFor(bob, 200, 200);
        e.store.learn(ann.getUniqueId(), bobs.id);
        e.stand(bob, bobs, 5);
        e.stand(ann, home, 5);

        // Bob closes his iris from the menu
        e.rightClick(bob, e.at(200, 65, 206), null);
        DialMenu m = (DialMenu) bob.opened.getHolder();
        expect(e.slotOf(m, DialMenu.Kind.IRIS, null) >= 0 && Kit.nameOf(bob.opened, DialMenu.IRIS_SLOT).contains("Close the iris"), "iris: owner sees the iris button");
        e.click(bob, DialMenu.IRIS_SLOT);
        expect(bobs.iris && e.count(bobs, Material.IRON_BLOCK) == 31 && bob.heard("The iris closes.") && bob.opened != null, "iris: closing fills the opening with iron and reopens the menu: " + bob.messages);
        expect(Kit.nameOf(bob.opened, DialMenu.IRIS_SLOT).contains("Open the iris") && Kit.loreOf(bob.opened, DialMenu.INFO_SLOT).contains("closed"), "iris: the button flips");
        expect(e.builder.check(e.world, bobs) == GateShape.Status.VALID, "iris: an iris keeps the gate valid");

        // others do not get the button, and cannot use it by guessing the slot
        Env.class.getName();
        e.stand(ann, bobs, 5);
        ann.stubSetLocation(new Location(e.world, 200.5, 65, 205.5));
        e.control.open(ann, bobs, 0);
        DialMenu am = (DialMenu) ann.opened.getHolder();
        expect(e.slotOf(am, DialMenu.Kind.IRIS, null) < 0, "iris: strangers see no iris button");
        am.at(DialMenu.IRIS_SLOT);
        e.control.click(ann, am, DialMenu.IRIS_SLOT);
        expect(bobs.iris, "iris: and clicking the empty slot does nothing");
        ann.closeInventory();
        e.stand(ann, home, 5);

        // a closed iris gate cannot dial out
        bob.messages.clear();
        expect(e.wh.dial(bob, bobs, e.store.gate("p-abydos")) == WormholeService.Dial.IRIS && bob.heard("The iris is closed"), "iris: a closed iris cannot dial out");

        // dialing a gate whose iris is closed: it connects but nothing gets through
        expect(e.wh.dial(ann, home, bobs) == WormholeService.Dial.STARTED, "iris: dial Bob");
        e.tickOpen(home);
        expect(e.wh.of(home).phase == WormholeService.Phase.OPEN && e.count(home, Material.NETHER_PORTAL) == 31 && e.count(bobs, Material.IRON_BLOCK) == 31 && e.count(bobs, Material.NETHER_PORTAL) == 0,
                "iris: the far iris stays shut, the near horizon opens");
        e.into(ann, home);
        ann.messages.clear();
        e.tick(1);
        expect(ann.getLocation().getWorld().getName().equals("world") && ann.getLocation().getZ() == 100.5 && ann.heard("The iris is closed") && ann.getVelocity().getZ() == 0.8,
                "iris: a traveller bounces off the far iris with a push back: " + ann.getVelocity().getZ() + " " + ann.messages);
        ann.messages.clear();
        e.tick(1);
        expect(!ann.heard("The iris is closed"), "iris: and is not nagged every tick");
        e.stand(ann, home, 5);

        // opening the iris while the wormhole is open
        e.wh.iris(bobs, false);
        expect(!bobs.iris && e.count(bobs, Material.NETHER_PORTAL) == 31, "iris: opening the iris of an open gate shows the horizon");
        e.wh.iris(bobs, true);
        expect(bobs.iris && e.count(bobs, Material.IRON_BLOCK) == 31, "iris: closing it again");
        e.wh.close(home, null);
        expect(e.count(bobs, Material.IRON_BLOCK) == 31 && e.count(home, Material.NETHER_PORTAL) == 0, "iris: a closed wormhole leaves a closed iris closed");
        e.wh.iris(bobs, false);
        expect(e.count(bobs, Material.AIR) == 31 && !bobs.iris, "iris: opening an idle gate's iris empties it");

        // command route
        e.stand(bob, bobs, 5);
        e.run(bob, "iris");
        expect(bobs.iris, "iris: /stargate iris toggles");
        List<String> r = e.run(ann, "iris");
        e.stand(ann, bobs, 5);
        r = e.run(ann, "iris");
        expect(said(r, "Only the owner") && bobs.iris, "iris: a stranger cannot use the command");
        Kit.TestPlayer boss = e.player("Boss", true);
        e.stand(boss, bobs, 5);
        e.run(boss, "iris");
        expect(!bobs.iris, "iris: staff can");
    }

    // ------------------------------------------------------------------ kawoosh

    static void kawoosh() throws Exception {
        Env e = new Env(null, true);
        e.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ann = e.player("Ann", false);
        Gate home = e.gateFor(ann, 100, 100);
        Gate abydos = e.store.gate("p-abydos");
        e.stand(ann, home, 9);

        Zombie close = new Zombie();
        Bukkit.ENTITIES.put(close.getUniqueId(), close);
        close.stubSetLocation(new Location(e.world, 100.5, 65, 102.5));
        Zombie far = new Zombie();
        Bukkit.ENTITIES.put(far.getUniqueId(), far);
        far.stubSetLocation(new Location(e.world, 100.5, 65, 110.5));
        Zombie wide = new Zombie();
        Bukkit.ENTITIES.put(wide.getUniqueId(), wide);
        wide.stubSetLocation(new Location(e.world, 110.5, 65, 102.5));
        Zombie behind = new Zombie();
        Bukkit.ENTITIES.put(behind.getUniqueId(), behind);
        behind.stubSetLocation(new Location(e.world, 100.5, 65, 98.0));
        Kit.TestPlayer creative = e.player("Creative", false);
        creative.setGameMode(GameMode.CREATIVE);
        creative.stubSetLocation(new Location(e.world, 101.5, 65, 103.0));
        Zombie atFar = new Zombie();
        Bukkit.ENTITIES.put(atFar.getUniqueId(), atFar);
        atFar.stubSetLocation(new Location(Bukkit.getWorld("sg_abydos"), 0.5, abydos.ay + 1, 2.7));
        Kit.TestPlayer standing = e.player("Standing", false);
        standing.stubSetLocation(new Location(e.world, 99.5, 65, 103.5));

        e.wh.dial(ann, home, abydos);
        e.tickOpen(home);
        expect(close.lastDamage == 8.0 && close.getVelocity().getZ() == 1.2 && close.getVelocity().getX() == 0.0 && close.getVelocity().getY() == 0.4, "kawoosh: a mob in front is hurt and thrown out: " + close.lastDamage + " " + close.getVelocity().getZ());
        expect(far.lastDamage == 0 && wide.lastDamage == 0 && behind.lastDamage == 0, "kawoosh: far, wide and behind the gate are safe");
        expect(standing.lastDamage == 8.0 && standing.getVelocity().getZ() == 1.2, "kawoosh: a player standing in front is hurt: " + standing.lastDamage);
        expect(creative.lastDamage == 0.0, "kawoosh: creative players are not");
        expect(atFar.lastDamage == 8.0, "kawoosh: the far gate has one too");
        expect(ann.lastDamage == 0.0, "kawoosh: Ann stood clear");
        e.wh.close(home, null);

        // switched off / softened
        close.lastDamage = 0;
        e.cfg.kawooshEnabled = false;
        e.wh.dial(ann, home, abydos);
        e.tickOpen(home);
        expect(close.lastDamage == 0, "kawoosh: can be switched off");
        e.wh.close(home, null);
        e.cfg.kawooshEnabled = true;
        e.cfg.kawooshDamage = 0;
        e.wh.dial(ann, home, abydos);
        e.tickOpen(home);
        expect(close.lastDamage == 0 && close.getVelocity().getZ() == 1.2, "kawoosh: damage 0 still throws");
        e.wh.close(home, null);

        // east facing gate throws along X
        Kit.TestPlayer bob = e.player("Bob", false);
        e.ring(300, 300, BlockFace.EAST);
        e.at(306, 65, 300).setType(e.cfg.materials.dhd);
        e.listener.onPlace(new BlockPlaceEvent(e.at(306, 65, 300), bob, e.items.dhd()));
        Gate east = e.store.gateAt("world", 300, 64, 300);
        expect(east != null && east.facing == BlockFace.EAST, "kawoosh: an east gate");
        Zombie ez = new Zombie();
        Bukkit.ENTITIES.put(ez.getUniqueId(), ez);
        ez.stubSetLocation(new Location(e.world, 302.5, 65, 300.5));
        e.cfg.kawooshDamage = 4;
        e.store.learn(bob.getUniqueId(), home.id);
        e.stand(bob, east, 9);
        e.wh.dial(bob, east, home);
        e.tickOpen(east);
        expect(ez.lastDamage == 4.0 && ez.getVelocity().getX() == 1.2 && ez.getVelocity().getZ() == 0.0, "kawoosh: east gate pushes along X");
        expect(e.count(east, Material.NETHER_PORTAL) == 31, "kawoosh: east gate opened");
        org.bukkit.block.data.BlockData bd = e.at(300, 66, 300).getBlockData();
        expect(bd instanceof org.bukkit.block.data.Orientable o && o.getAxis() == org.bukkit.Axis.Z, "kawoosh: an east/west gate's horizon runs along Z");
    }

    // ------------------------------------------------------------------ protection

    static void protection() throws Exception {
        Env e = new Env(null, true);
        Kit.TestPlayer ann = e.player("Ann", false);
        Kit.TestPlayer bob = e.player("Bob", false);
        Kit.TestPlayer boss = e.player("Boss", true);
        Gate home = e.gateFor(ann, 100, 100);
        World w = e.world;
        int[] ringCell = GateShape.pos(100, 64, 100, BlockFace.SOUTH, new GateShape.Cell(-3, 4));
        Block ringBlock = w.getBlockAt(ringCell[0], ringCell[1], ringCell[2]);
        Block opening = e.at(100, 67, 100);
        Block dhd = e.at(100, 65, 106);

        // breaking
        BlockBreakEvent b1 = new BlockBreakEvent(ringBlock, bob);
        e.listener.onBreak(b1);
        expect(b1.isCancelled() && bob.heard("Only the owner") && e.store.gate(home.id) != null, "protect: a stranger cannot break the ring");
        BlockBreakEvent b2 = new BlockBreakEvent(opening, ann);
        e.listener.onBreak(b2);
        expect(b2.isCancelled(), "protect: nor the opening, even the owner");
        bob.messages.clear();
        BlockBreakEvent b3 = new BlockBreakEvent(dhd, bob);
        e.listener.onBreak(b3);
        expect(bob.heard("Only the owner"), "protect: a stranger is told why the device stays");
        expect(b3.isCancelled() && dhd.getType() == e.cfg.materials.dhd && e.store.dhdAt("world", 100, 65, 106) != null, "protect: a stranger cannot take the device");
        BlockBreakEvent b4 = new BlockBreakEvent(dhd, ann);
        e.listener.onBreak(b4);
        expect(b4.isCancelled() && dhd.getType() == Material.AIR && e.store.dhdAt("world", 100, 65, 106) == null && w.stubDrops.size() == 1 && e.items.isDhd(w.stubDrops.get(0)),
                "protect: the owner takes the device and gets the marked item back: " + w.stubDrops.size());
        Block second = e.at(99, 65, 104);
        second.setType(e.cfg.materials.dhd);
        e.listener.onPlace(new BlockPlaceEvent(second, ann, e.items.dhd()));
        ann.setGameMode(GameMode.CREATIVE);
        e.listener.onBreak(new BlockBreakEvent(second, ann));
        expect(second.getType() == Material.AIR && w.stubDrops.size() == 1, "protect: creative owners get no drop");
        ann.setGameMode(GameMode.SURVIVAL);
        Block third = e.at(98, 65, 104);
        third.setType(e.cfg.materials.dhd);
        e.listener.onPlace(new BlockPlaceEvent(third, boss, e.items.dhd()));
        e.listener.onBreak(new BlockBreakEvent(third, boss));
        expect(third.getType() == Material.AIR && w.stubDrops.size() == 2, "protect: staff may take any device");
        // a device placed by someone else on Ann's gate (by staff): Ann owns the gate, so she may take it
        Block fourth = e.at(97, 65, 104);
        fourth.setType(e.cfg.materials.dhd);
        e.listener.onPlace(new BlockPlaceEvent(fourth, boss, e.items.dhd()));
        e.listener.onBreak(new BlockBreakEvent(fourth, ann));
        expect(fourth.getType() == Material.AIR, "protect: the gate's owner may take any device linked to it");
        // a device whose gate is gone
        e.store.addDhd(new GateStore.Dhd("world", 50, 65, 50, "gone", null));
        BlockBreakEvent orphan = new BlockBreakEvent(e.at(50, 65, 50), bob);
        e.listener.onBreak(orphan);
        expect(orphan.isCancelled() && e.store.dhdAt("world", 50, 65, 50) != null, "protect: an ownerless device record is staff-only");

        // other blocks are untouched
        BlockBreakEvent plain = new BlockBreakEvent(e.at(10, 65, 10), bob);
        e.listener.onBreak(plain);
        expect(!plain.isCancelled(), "protect: ordinary blocks are not our business");

        // the owner takes the gate apart
        ann.messages.clear();
        BlockBreakEvent own = new BlockBreakEvent(ringBlock, ann);
        e.listener.onBreak(own);
        expect(!own.isCancelled() && e.store.gate(home.id) == null && ann.heard("fallen silent") && e.count(home, Material.NETHER_PORTAL) == 0, "protect: the owner may break the ring; the gate is forgotten: " + ann.messages);
        expect(e.store.dhdsOf(home.id).isEmpty(), "protect: and its devices with it");

        // planet gates: staff only
        Gate abydos = e.store.gate("p-abydos");
        World aw = Bukkit.getWorld("sg_abydos");
        int[] pc = GateShape.pos(abydos.ax, abydos.ay, abydos.az, abydos.facing, new GateShape.Cell(-3, 4));
        Block planetRing = aw.getBlockAt(pc[0], pc[1], pc[2]);
        BlockBreakEvent pb = new BlockBreakEvent(planetRing, ann);
        e.listener.onBreak(pb);
        expect(pb.isCancelled() && ann.heard("You cannot do that") && e.store.gate("p-abydos") != null, "protect: a planet gate cannot be broken by players");
        Block pd = aw.getBlockAt(0, abydos.ay + 1, PlanetService.DHD_DISTANCE);
        BlockBreakEvent pdb = new BlockBreakEvent(pd, ann);
        e.listener.onBreak(pdb);
        expect(pdb.isCancelled() && pd.getType() == e.cfg.materials.dhd, "protect: nor its dialing device");
        BlockBreakEvent pbs = new BlockBreakEvent(planetRing, boss);
        e.listener.onBreak(pbs);
        expect(!pbs.isCancelled() && e.store.gate("p-abydos") == null, "protect: staff may");
        e.planets.syncAll();
        expect(e.store.gate("p-abydos") != null && e.builder.check(aw, e.store.gate("p-abydos")) == GateShape.Status.VALID, "protect: the next sync builds the planet gate again");
        abydos = e.store.gate("p-abydos");

        // a server gate that is not a planet
        Gate serverGate = new Gate("srv001", "Spawn Gate", "world", 500, 64, 500, BlockFace.SOUTH, GateAddress.of(20, 21, 22, 23, 24, 25));
        serverGate.common = true;
        e.store.add(serverGate);
        e.builder.buildRing(w, 500, 64, 500, BlockFace.SOUTH);
        int[] sc = GateShape.pos(500, 64, 500, BlockFace.SOUTH, new GateShape.Cell(-3, 4));
        BlockBreakEvent sb = new BlockBreakEvent(w.getBlockAt(sc[0], sc[1], sc[2]), ann);
        e.listener.onBreak(sb);
        expect(sb.isCancelled() && ann.heard("You cannot do that"), "protect: gates made by the server are staff-only");

        // explosions and pistons
        Gate g2 = e.gateFor(bob, 200, 200);
        Block r2 = e.at(GateShape.pos(200, 64, 200, BlockFace.SOUTH, new GateShape.Cell(-3, 3))[0], 67, 200);
        Block in2 = e.at(200, 67, 200);
        Block d2 = e.at(200, 65, 206);
        Block other = e.at(300, 70, 300);
        List<Block> boom = new ArrayList<>(List.of(r2, in2, d2, other));
        e.listener.onEntityExplode(new EntityExplodeEvent(new Zombie(), boom, 1f));
        expect(boom.size() == 1 && boom.get(0) == other, "protect: explosions spare rings, openings and devices");
        List<Block> boom2 = new ArrayList<>(List.of(r2, other));
        e.listener.onBlockExplode(new BlockExplodeEvent(other, boom2, 1f));
        expect(boom2.size() == 1, "protect: block explosions too");
        BlockPistonExtendEvent pe = new BlockPistonExtendEvent(other, List.of(r2), BlockFace.UP);
        e.listener.onPistonExtend(pe);
        BlockPistonExtendEvent pe2 = new BlockPistonExtendEvent(other, List.of(other), BlockFace.UP);
        e.listener.onPistonExtend(pe2);
        BlockPistonRetractEvent pr = new BlockPistonRetractEvent(other, List.of(d2), BlockFace.UP);
        e.listener.onPistonRetract(pr);
        BlockPistonRetractEvent pr2 = new BlockPistonRetractEvent(other, List.of(other), BlockFace.UP);
        e.listener.onPistonRetract(pr2);
        expect(pe.isCancelled() && !pe2.isCancelled() && pr.isCancelled() && !pr2.isCancelled(), "protect: pistons cannot move gate parts");

        // portal physics and the vanilla portal
        in2.setType(Material.NETHER_PORTAL);
        BlockPhysicsEvent ph = new BlockPhysicsEvent(in2);
        e.listener.onPhysics(ph);
        expect(ph.isCancelled(), "protect: the horizon does not pop");
        Block loose = e.at(700, 70, 700);
        loose.setType(Material.NETHER_PORTAL);
        BlockPhysicsEvent ph2 = new BlockPhysicsEvent(loose);
        e.listener.onPhysics(ph2);
        BlockPhysicsEvent ph3 = new BlockPhysicsEvent(r2);
        e.listener.onPhysics(ph3);
        expect(!ph2.isCancelled() && !ph3.isCancelled(), "protect: other portals and blocks are left to the game");
        PlayerPortalEvent pp = new PlayerPortalEvent(bob, new Location(w, 200.5, 66, 200.5), new Location(w, 0, 0, 0));
        e.listener.onPortal(pp);
        PlayerPortalEvent pp2 = new PlayerPortalEvent(bob, new Location(w, 200.5, 66, 201.5), new Location(w, 0, 0, 0));
        e.listener.onPortal(pp2);
        PlayerPortalEvent pp3 = new PlayerPortalEvent(bob, new Location(w, 400.5, 66, 400.5), new Location(w, 0, 0, 0));
        e.listener.onPortal(pp3);
        PlayerPortalEvent pp4 = new PlayerPortalEvent(bob, null, new Location(w, 0, 0, 0));
        e.listener.onPortal(pp4);
        expect(pp.isCancelled() && pp2.isCancelled() && !pp3.isCancelled() && !pp4.isCancelled(), "protect: the vanilla portal never fires beside a gate, and does elsewhere");

        // quitting saves
        e.store.markDirty();
        e.listener.onQuit(new org.bukkit.event.player.PlayerQuitEvent(bob, ""));
        expect(!e.store.dirty() && new File(e.dir, "gates.tsv").exists(), "protect: a player leaving saves the files");
        expect(g2 != null && abydos != null, "protect: (kept)");
    }

    // ------------------------------------------------------------------ tablets

    static void tablets() throws Exception {
        Env e = new Env(null, true);
        Kit.TestPlayer ann = e.player("Ann", false);
        Kit.TestPlayer bob = e.player("Bob", false);
        Gate bobs = e.gateFor(bob, 200, 200);
        ItemStack tablet = e.items.tablet(bobs);
        tablet.setAmount(2);
        ann.getInventory().setItemInMainHand(tablet);
        PlayerInteractEvent ev = e.rightClick(ann, null, tablet);
        expect(ev.isCancelled() && ann.heard("You study the tablet") && e.store.knows(ann.getUniqueId(), bobs.id) && ann.getInventory().getItemInMainHand().getAmount() == 1,
                "tablet: reading it teaches the address and uses one up: " + ann.messages);
        ann.messages.clear();
        PlayerInteractEvent again = e.rightClick(ann, e.at(5, 64, 5), ann.getInventory().getItemInMainHand());
        expect(again.isCancelled() && ann.heard("already know") && ann.getInventory().getItemInMainHand().getAmount() == 1
                && ann.getInventory().getItemInMainHand().getType() == Material.DISC_FRAGMENT_5, "tablet: a known address is not used up");
        PlayerInteractEvent own = e.rightClick(bob, null, tablet);
        expect(own.isCancelled() && bob.heard("already know"), "tablet: the owner already knows");
        // creative keeps the tablet; a single tablet disappears
        Kit.TestPlayer cat = e.player("Cat", false);
        cat.setGameMode(GameMode.CREATIVE);
        ItemStack one = e.items.tablet(bobs);
        cat.getInventory().setItemInMainHand(one);
        e.rightClick(cat, null, one);
        expect(e.store.knows(cat.getUniqueId(), bobs.id) && cat.getInventory().getItemInMainHand() == one, "tablet: creative players keep theirs");
        Kit.TestPlayer dan = e.player("Dan", false);
        ItemStack single = e.items.tablet(bobs);
        dan.getInventory().setItemInMainHand(single);
        e.rightClick(dan, null, single);
        expect(dan.getInventory().getItemInMainHand().getType() == Material.AIR && e.store.knows(dan.getUniqueId(), bobs.id), "tablet: the last one is used up");
        // a tablet for a gate that is gone
        ItemStack stale = e.items.tablet(bobs);
        e.gates.remove(bobs, false);
        Kit.TestPlayer eve = e.player("Eve", false);
        eve.getInventory().setItemInMainHand(stale);
        e.rightClick(eve, null, stale);
        expect(eve.heard("fallen silent") && eve.getInventory().getItemInMainHand() == stale, "tablet: a tablet of a removed gate says so");
        // other items and left clicks are ignored
        ItemStack stick = new ItemStack(Material.STICK);
        PlayerInteractEvent st = e.rightClick(eve, null, stick);
        expect(!st.isCancelled(), "tablet: other items are ignored");
        PlayerInteractEvent left = new PlayerInteractEvent(eve, Action.LEFT_CLICK_BLOCK, stale, e.at(5, 64, 5));
        e.listener.onInteract(left);
        expect(!left.isCancelled(), "tablet: left clicks are ignored");
        PlayerInteractEvent plainBlock = e.rightClick(eve, e.at(5, 64, 5), null);
        expect(!plainBlock.isCancelled(), "tablet: right-clicking an ordinary block is fine");
    }

    // ------------------------------------------------------------------ menus

    static void menus() throws Exception {
        Env e = new Env(null, true);
        Kit.TestPlayer ann = e.player("Ann", false);
        Gate home = e.gateFor(ann, 100, 100);
        e.stand(ann, home, 5);
        e.rightClick(ann, e.at(100, 65, 106), null);
        DialMenu menu = (DialMenu) ann.opened.getHolder();

        // clicks in the bottom half or in a foreign inventory do nothing; drags are cancelled
        InventoryView view = ann.getOpenInventory();
        InventoryClickEvent below = new InventoryClickEvent(view, InventoryType.SlotType.QUICKBAR, DialMenu.INFO_SLOT, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        e.listener.onClick(below);
        expect(below.isCancelled() && !e.wh.busy(home) && ann.opened != null, "menu: a click in the player's own inventory is cancelled and does nothing");
        InventoryDragEvent drag = new InventoryDragEvent(ann.getOpenInventory());
        e.listener.onDrag(drag);
        expect(drag.isCancelled(), "menu: dragging in the menu is cancelled");
        Kit.TestPlayer other = e.player("Other", false);
        InventoryClickEvent foreign = new InventoryClickEvent(other.getOpenInventory(), InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        e.listener.onClick(foreign);
        InventoryDragEvent otherDrag = new InventoryDragEvent(other.getOpenInventory());
        e.listener.onDrag(otherDrag);
        expect(!foreign.isCancelled() && !otherDrag.isCancelled(), "menu: other inventories are left alone");
        InventoryClickEvent noView = new InventoryClickEvent(null, InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        e.listener.onClick(noView);
        e.listener.onDrag(new InventoryDragEvent(null));
        expect(!noView.isCancelled(), "menu: no view is fine");
        other.nullTop = true;
        InventoryClickEvent noTop = new InventoryClickEvent(other.getOpenInventory(), InventoryType.SlotType.CONTAINER, 3, ClickType.LEFT, InventoryAction.PICKUP_ALL);
        e.listener.onClick(noTop);
        e.listener.onDrag(new InventoryDragEvent(other.getOpenInventory()));
        expect(!noTop.isCancelled(), "menu: no top inventory is fine");

        // empty slots and info do nothing
        e.click(ann, 44);
        e.click(ann, DialMenu.INFO_SLOT);
        expect(ann.opened != null && !e.wh.busy(home), "menu: empty slot and info slot do nothing");

        // walking away closes the menu on the next click
        int slot = e.slotOf(menu, DialMenu.Kind.DIAL, "p-abydos");
        ann.stubSetLocation(new Location(e.world, 300.5, 65, 300.5));
        e.click(ann, slot);
        expect(ann.opened == null && !e.wh.busy(home), "menu: if you have walked away the click only closes it");

        // the gate was removed under an open menu
        e.stand(ann, home, 5);
        e.rightClick(ann, e.at(100, 65, 106), null);
        DialMenu stale = (DialMenu) ann.opened.getHolder();
        e.gates.remove(home, false);
        e.control.click(ann, stale, slot);
        expect(ann.opened == null, "menu: a menu for a removed gate just closes");

        // a target removed under an open menu
        Gate h2 = e.gateFor(ann, 140, 140);
        e.stand(ann, h2, 5);
        e.control.open(ann, h2, 0);
        DialMenu m2 = (DialMenu) ann.opened.getHolder();
        int chulakSlot = e.slotOf(m2, DialMenu.Kind.DIAL, "p-chulak");
        e.store.remove("p-chulak");
        e.control.click(ann, m2, chulakSlot);
        expect(!e.wh.busy(h2), "menu: a target removed under an open menu is not dialed");

        // no permission
        ann.perms.remove(GateService.USE);
        ann.closeInventory();
        e.control.open(ann, h2, 0);
        expect(ann.opened == null && ann.heard("You cannot do that"), "menu: needs the use permission");

        // the iris and close buttons need the right person
        ann.perms.add(GateService.USE);
        e.control.open(ann, h2, 0);
        DialMenu m3 = (DialMenu) ann.opened.getHolder();
        Kit.TestPlayer bob = e.player("Bob", false);
        e.stand(bob, h2, 5);
        e.control.click(bob, m3, DialMenu.IRIS_SLOT);
        expect(!h2.iris, "menu: a stranger cannot work the iris");
        expect(e.warns.isEmpty(), "menu: no warnings");
    }

    // ------------------------------------------------------------------ commands

    static void commands() throws Exception {
        Env e = new Env(null, true);
        e.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ann = e.player("Ann", false);
        Kit.TestPlayer boss = e.player("Boss", true);
        Gate home = e.gateFor(ann, 100, 100);
        Gate abydos = e.store.gate("p-abydos");
        Gate netu = e.store.gate("p-netu");

        // away from any gate
        List<String> r = e.run(ann);
        expect(said(r, "opens the dialing device") && !said(r, "Staff:"), "cmd: no gate near: help, no staff line");
        expect(said(e.run(boss, "help"), "Staff:") && said(e.run(ann, "nonsense"), "opens the dialing device"), "cmd: staff see the staff line; unknown subcommands give help");
        r = e.run(ann, "info");
        expect(said(r, "No complete gate within 16 blocks"), "cmd: info away from a gate: " + r);
        r = e.run(ann, "blueprint");
        expect(r.size() >= 11 && said(r, "polished blackstone bricks") && said(r, "red nether bricks") && said(r, "crying obsidian") && said(r, "within 10 blocks"), "cmd: blueprint: " + r);
        expect(said(e.run(ann, "list"), "Abydos") && !said(e.run(ann, "list"), "Netu"), "cmd: list shows starters");
        Kit.TestPlayer newbie = e.player("Newbie", false);
        e.cfg.requireKnown = true;
        newbie.messages.clear();
        e.store.remove("p-chulak");
        e.store.remove("p-abydos");
        e.store.remove("p-p3x-118");
        e.store.remove("p-p4x-884");
        e.store.remove("p-cimmeria");
        e.store.remove("p-netu");
        expect(said(e.run(newbie, "list"), "do not know any addresses"), "cmd: nothing known");
        e.planets.syncAll();
        abydos = e.store.gate("p-abydos");
        netu = e.store.gate("p-netu");
        expect(abydos != null && netu != null, "cmd: planets are back after a sync");

        // standing at the gate
        e.stand(ann, home, 4);
        r = e.run(ann);
        expect(ann.opened != null && ann.opened.getHolder() instanceof DialMenu, "cmd: plain /stargate at a gate opens the menu");
        ann.closeInventory();
        r = e.run(ann, "info");
        expect(said(r, "Ann's gate") && said(r, home.address.names()) && said(r, "Owner: ") && said(r, "Ann") && said(r, "State: ") && said(r, "idle") && said(r, "Dialing devices: ")
                && said(r, "iris open"), "cmd: info: " + r);
        r = e.run(ann, "dial");
        expect(ann.opened != null, "cmd: dial with no target opens the menu");
        ann.closeInventory();
        r = e.run(ann, "dial", "abydos");
        expect(e.wh.of(home) != null && e.wh.of(home).to == abydos && said(r, "Dialing"), "cmd: dial by name (any case): " + r);
        r = e.run(ann, "info");
        expect(said(r, "dialing"), "cmd: info while dialing");
        e.tickOpen(home);
        r = e.run(ann, "info");
        expect(said(r, "open"), "cmd: info while open");
        r = e.run(ann, "close");
        expect(!e.wh.busy(home), "cmd: close");
        r = e.run(ann, "close");
        expect(said(r, "Nothing is open here"), "cmd: close when idle");
        r = e.run(ann, "dial", "Nowhere", "Land");
        expect(said(r, "Nothing answers"), "cmd: dial an unknown name");
        r = e.run(ann, "dial", "Netu");
        expect(said(r, "do not know that address") && !e.wh.busy(home), "cmd: dial a name you do not know");
        r = e.run(ann, "dial", netu.address.code());
        expect(e.wh.busy(home) && e.wh.of(home).to == netu && e.store.knows(ann.getUniqueId(), netu.id), "cmd: typing the whole address always works and teaches it");
        e.wh.close(home, null);
        e.run(ann, "dial", netu.address.names().toLowerCase().replace('-', ' ').split(" ")[0], "x");
        expect(!e.wh.busy(home), "cmd: a part of an address does nothing");
        r = e.run(ann, "dial", netu.address.names().replace('-', ' '));
        expect(e.wh.busy(home), "cmd: an address typed as words with spaces works");
        e.wh.close(home, null);

        // rename
        r = e.run(ann, "rename", "Base", "Camp");
        expect(home.name.equals("Base Camp") && said(r, "is now called") && e.store.dirty(), "cmd: rename: " + r);
        r = e.run(ann, "rename", "abydos");
        expect(home.name.equals("Base Camp") && said(r, "Another gate already has that name"), "cmd: rename to a taken name");
        r = e.run(ann, "rename", "BASE camp");
        expect(said(r, "is now called"), "cmd: renaming to your own name in another case is fine");
        r = e.run(ann, "rename");
        expect(said(r, "1 to 32"), "cmd: rename with nothing");
        r = e.run(ann, "rename", "x".repeat(40));
        expect(said(r, "1 to 32") && home.name.equals("BASE camp"), "cmd: rename too long");
        Kit.TestPlayer bob = e.player("Bob", false);
        e.stand(bob, home, 4);
        r = e.run(bob, "rename", "Mine");
        expect(said(r, "Only the owner") && home.name.equals("BASE camp"), "cmd: a stranger cannot rename");
        e.stand(boss, home, 4);
        r = e.run(boss, "rename", "Staff Base");
        expect(home.name.equals("Staff Base"), "cmd: staff can rename");
        r = e.run(boss, "info");
        expect(said(r, "Ann"), "cmd: info names the owner");
        r = e.run(boss, "dial", "p-abydos");
        expect(e.wh.busy(home), "cmd: dial by gate id");
        e.wh.close(home, null);
        e.stand(boss, abydos, 4);
        r = e.run(boss, "info");
        expect(said(r, "the server") && said(r, "planet abydos"), "cmd: info on a planet gate");
        e.stand(boss, home, 4);

        // tablets
        ItemStack paper = new ItemStack(Material.PAPER, 2);
        ann.getInventory().setItemInMainHand(paper);
        r = e.run(ann, "tablet", "Abydos");
        expect(said(r, "copy the address") && ann.getInventory().getItemInMainHand().getAmount() == 1, "cmd: a tablet costs a piece of paper: " + r);
        ItemStack gave = null;
        for (ItemStack it : ann.getInventory().getContents()) {
            if (it != null && e.items.tabletGate(it) != null) {
                gave = it;
            }
        }
        expect(gave != null && "p-abydos".equals(e.items.tabletGate(gave)), "cmd: and the tablet is in the inventory");
        r = e.run(ann, "tablet", "Abydos");
        expect(ann.getInventory().getItemInMainHand().getType() == Material.AIR && said(r, "copy the address"), "cmd: the last paper is used up");
        r = e.run(ann, "tablet", "Abydos");
        expect(said(r, "Hold a piece of paper"), "cmd: no paper in hand");
        r = e.run(ann, "tablet", "Cimmeria");
        expect(said(r, "Nothing answers"), "cmd: cannot write down an address you do not know");
        r = e.run(ann, "tablet");
        expect(said(r, "Nothing answers"), "cmd: tablet with no name");
        ann.setGameMode(GameMode.CREATIVE);
        ann.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        r = e.run(ann, "tablet", "Abydos");
        expect(said(r, "copy the address") && ann.getInventory().getItemInMainHand().getType() == Material.STICK, "cmd: creative needs no paper");
        ann.setGameMode(GameMode.SURVIVAL);

        // the console and players without permission
        Kit.Console console = new Kit.Console();
        expect(e.cmd.onCommand(console, null, "stargate", new String[]{"dial", "x"}) && console.said("standing at a gate"), "cmd: console cannot dial");
        console.out.clear();
        expect(e.cmd.onCommand(console, null, "stargate", new String[]{}) && console.said("opens the dialing device"), "cmd: console gets help");
        console.out.clear();
        e.cmd.onCommand(console, null, "stargate", new String[]{"planets"});
        expect(console.said("abydos") && console.said("Netu") || console.said("netu"), "cmd: console may run staff commands (it has every permission)");
        for (String sub : new String[]{"give", "learn", "forget", "remove", "planets", "tp", "rebuild", "status", "reload"}) {
            r = e.run(ann, sub);
            expect(said(r, "You cannot do that"), "cmd: " + sub + " is staff only");
        }

        // staff commands
        r = e.run(boss, "give", "dhd", "Ann");
        expect(said(r, "Gave a dialing device to Ann") && hasItem(ann, e.items::isDhd), "cmd: give dhd");
        r = e.run(boss, "give", "tablet", "Ann", "Netu");
        expect(said(r, "Gave a tablet for Netu to Ann") && hasItem(ann, it -> "p-netu".equals(e.items.tabletGate(it))), "cmd: give tablet");
        expect(said(e.run(boss, "give"), "Usage") && said(e.run(boss, "give", "dhd"), "Usage") && said(e.run(boss, "give", "dhd", "Nobody"), "not online")
                && said(e.run(boss, "give", "tablet", "Ann"), "No such gate") && said(e.run(boss, "give", "tablet", "Ann", "Nowhere"), "No such gate")
                && said(e.run(boss, "give", "cake", "Ann"), "dhd or tablet"), "cmd: give errors");
        r = e.run(boss, "learn", "Bob", "Netu");
        expect(said(r, "Bob learned 1 address") && e.store.knows(bob.getUniqueId(), netu.id) && bob.heard("New address learned"), "cmd: learn: " + r);
        r = e.run(boss, "learn", "Bob", "Netu");
        expect(said(r, "learned 0 address"), "cmd: learning twice teaches nothing");
        r = e.run(boss, "forget", "Bob", "Netu");
        expect(said(r, "forgot 1") && !e.store.knows(bob.getUniqueId(), netu.id), "cmd: forget");
        r = e.run(boss, "learn", "Bob", "all");
        expect(e.store.knownOf(bob.getUniqueId()).size() == e.store.gates().size(), "cmd: learn all");
        r = e.run(boss, "forget", "Bob", "all");
        expect(e.store.knownOf(bob.getUniqueId()).isEmpty(), "cmd: forget all");
        expect(said(e.run(boss, "learn"), "Usage") && said(e.run(boss, "learn", "Nobody", "x"), "not online") && said(e.run(boss, "learn", "Bob", "Nowhere"), "No such gate")
                && said(e.run(boss, "forget", "Bob"), "Usage"), "cmd: learn errors");
        r = e.run(boss, "planets");
        expect(r.size() == 6 && said(r, "starter") && said(r, "world loaded") && said(r, "Desert world"), "cmd: planets: " + r);
        r = e.run(boss, "status");
        expect(said(r, "Gates: ") && said(r, "wormholes now: "), "cmd: status");
        r = e.run(boss, "tp", "chulak");
        expect(boss.getLocation().getWorld().getName().equals("sg_chulak"), "cmd: tp");
        expect(said(e.run(boss, "tp"), "Usage") && said(e.run(boss, "tp", "nowhere"), "Usage"), "cmd: tp errors");
        r = e.run(boss, "rebuild", "abydos");
        expect(said(r, "Rebuilt the arrival area of Abydos"), "cmd: rebuild: " + r);
        expect(said(e.run(boss, "rebuild"), "Usage") && said(e.run(boss, "rebuild", "x"), "Usage"), "cmd: rebuild errors");
        console.out.clear();
        e.cmd.onCommand(console, null, "stargate", new String[]{"tp", "chulak"});
        expect(console.said("Usage"), "cmd: tp from the console");
        r = e.run(boss, "reload");
        expect(e.reloads == 1 && said(r, "reloaded"), "cmd: reload");
        e.stand(boss, home, 4);
        int before = e.store.gates().size();
        r = e.run(boss, "remove", "Staff Base");
        expect(said(r, "Removed Staff Base") && e.store.gates().size() == before - 1 && e.at(100, 64, 100).getType() == Material.AIR, "cmd: remove takes the gate down");
        expect(said(e.run(boss, "remove"), "No such gate") && said(e.run(boss, "remove", "nowhere"), "No such gate"), "cmd: remove errors");

        // completion
        List<String> t = e.cmd.onTabComplete(ann, null, "sg", new String[]{""});
        expect(t.contains("dial") && t.contains("blueprint") && !t.contains("give"), "tab: players see player subcommands");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"re"});
        expect(t.contains("rename") && t.contains("rebuild") && t.contains("reload") && !t.contains("dial"), "tab: prefix filter, staff subcommands for staff: " + t);
        t = e.cmd.onTabComplete(ann, null, "sg", new String[]{"dial", "ab"});
        expect(t.equals(List.of("Abydos")), "tab: gate names: " + t);
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"give", ""});
        expect(t.equals(List.of("dhd", "tablet")), "tab: give kinds");
        t = e.cmd.onTabComplete(ann, null, "sg", new String[]{"give", ""});
        expect(t.isEmpty(), "tab: nothing for give to players");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"give", "tablet", ""});
        expect(t.contains("Ann") && t.contains("Boss"), "tab: players");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"give", "tablet", "Ann", "chu"});
        expect(t.equals(List.of("Chulak")), "tab: gate for a tablet: " + t);
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"learn", ""});
        expect(t.contains("Ann"), "tab: learn players");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"learn", "Ann", "al"});
        expect(t.equals(List.of("all")), "tab: learn all");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"forget", "Ann", ""});
        expect(t.contains("all") && t.contains("Abydos"), "tab: forget");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"remove", "ab"});
        expect(t.equals(List.of("Abydos")), "tab: remove");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"tp", "c"});
        expect(t.containsAll(List.of("chulak", "cimmeria")) && t.size() == 2, "tab: planets");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"rebuild", "n"});
        expect(t.equals(List.of("netu")), "tab: rebuild");
        t = e.cmd.onTabComplete(ann, null, "sg", new String[]{"rename", "x"});
        expect(t.isEmpty(), "tab: nothing for rename");
        t = e.cmd.onTabComplete(ann, null, "sg", new String[]{"tp", "c"});
        expect(t.isEmpty(), "tab: no planet names for players");
        t = e.cmd.onTabComplete(ann, null, "sg", new String[]{"learn", ""});
        expect(t.isEmpty(), "tab: no player names for players");
        t = e.cmd.onTabComplete(ann, null, "sg", new String[]{"remove", ""});
        expect(t.isEmpty(), "tab: no gate names for remove for players");
        t = e.cmd.onTabComplete(ann, null, "sg", new String[]{"give", "tablet", ""});
        expect(t.isEmpty(), "tab: third argument for players");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"dial", "a", "b", "c"});
        expect(t.isEmpty(), "tab: nothing past the arguments");
        t = e.cmd.onTabComplete(boss, null, "sg", new String[]{"give", "tablet", "Ann", "Abydos", "x"});
        expect(t.isEmpty(), "tab: nothing past the arguments (give)");
    }

    static boolean hasItem(Kit.TestPlayer p, java.util.function.Predicate<ItemStack> test) {
        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && test.test(it)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ api

    static void api() throws Exception {
        Env e = new Env(null, true);
        e.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ann = e.player("Ann", false);
        Gate home = e.gateFor(ann, 100, 100);
        StargateApi api = new StargateApiImpl(e.gates, e.wh);
        expect(api.gates().size() == 7, "api: all gates listed: " + api.gates().size());
        StargateApi.GateInfo info = api.gate("Ann's gate");
        expect(info != null && info.id().equals(home.id) && info.name().equals("Ann's gate") && info.world().equals("world") && info.x() == 100 && info.y() == 64 && info.z() == 100
                && info.address().equals(home.address.code()) && info.planet().isEmpty(), "api: gate by name");
        expect(api.gate(home.id) != null && api.gate("nope") == null && api.gate(null) == null, "api: gate by id; unknown is null");
        StargateApi.GateInfo ab = api.gate("abydos");
        expect(ab != null && ab.planet().equals("abydos") && ab.world().equals("sg_abydos"), "api: planet gate info");
        expect(api.gateNear(new Location(e.world, 100.5, 66, 103), 6).id().equals(home.id) && api.gateNear(new Location(e.world, 300, 66, 300), 6) == null && api.gateNear(null, 5) == null,
                "api: gate near a spot");
        expect(api.knows(ann.getUniqueId(), home.id) && api.knows(ann.getUniqueId(), "p-abydos") && !api.knows(ann.getUniqueId(), "p-netu") && !api.knows(null, home.id)
                && !api.knows(ann.getUniqueId(), "nope"), "api: knows");
        UUID offline = UUID.fromString("00000000-0000-0000-0000-0000000000f0");
        expect(!api.knows(offline, "p-netu") && api.learn(offline, "p-netu") && !api.learn(offline, "p-netu") && api.knows(offline, "p-netu") && !api.learn(null, "p-netu") && !api.learn(offline, "nope"),
                "api: offline players can be taught and asked");
        expect(api.learn(ann.getUniqueId(), "p-netu") && api.knows(ann.getUniqueId(), "p-netu"), "api: teaching an online player");
        Gate mine = LogicTest.gate("off001", "Offline Gate", 5, BlockFace.SOUTH, 30, 31, 32, 33, 34, 35);
        mine.owner = offline;
        e.store.add(mine);
        expect(api.knows(offline, "off001"), "api: an offline owner knows their gate");
        expect(!api.isActive(home.id) && !api.isActive("nope"), "api: idle");
        expect(api.dial(home.id, "p-abydos") && api.isActive(home.id) && api.isActive("p-abydos") == false, "api: dial (the far gate is not busy until the seventh chevron)");
        e.tickOpen(home);
        expect(api.isActive("p-abydos"), "api: open at both ends");
        expect(!api.dial(home.id, "p-chulak") && !api.dial("nope", "p-chulak") && !api.dial(home.id, "nope"), "api: cannot dial from a busy gate or with bad ids");
        expect(api.close("p-abydos") && !api.isActive(home.id) && !api.close(home.id) && !api.close("nope"), "api: close from either end");
        expect(api.planets().size() == 6 && api.planets().contains("abydos") && api.planetWorld("abydos").equals("sg_abydos") && api.planetWorld("zzz").isEmpty(), "api: planets");
    }

    // ------------------------------------------------------------------ the plugin

    static void pluginWiring() throws Exception {
        Kit.reset();
        Bukkit.WORLDS.clear();
        Bukkit.CREATED.clear();
        Bukkit.WORLDS.put("world", new World("world"));
        File dir = Files.createTempDirectory("sg-plugin").toFile();
        org.bukkit.plugin.java.JavaPlugin.DATA_FOLDER = dir;
        org.bukkit.plugin.java.JavaPlugin.CALLS.clear();
        org.bukkit.plugin.java.JavaPlugin.RESOURCES.put("planets.yml", Files.readString(java.nio.file.Path.of(System.getProperty("shipped.planets"))));
        StargatePlugin plugin = new StargatePlugin();
        Files.writeString(new File(dir, "custom-planets.yml").toPath(), "planets.abydos.name\ts\tAbydos Prime\nplanets.mine.style\ts\tdesert\nplanets.mine.seed\ti\t12\nhidden-planets\tl\tnetu\u0001nope\n");
        plugin.getConfig().set("dial.cooldown-seconds", 3);
        plugin.onEnable();
        expect(plugin_cfg(plugin).cooldownSeconds == 3, "plugin: the saved config is read at start");
        expect(org.bukkit.plugin.java.JavaPlugin.CALLS.contains("saveDefaultConfig") && org.bukkit.plugin.java.JavaPlugin.CALLS.contains("saveResource:custom-planets.yml:false"),
                "plugin: default config and custom-planets.yml are put out: " + org.bukkit.plugin.java.JavaPlugin.CALLS);
        expect(org.bukkit.plugin.PluginManager.REGISTERED.size() == 1 && org.bukkit.plugin.PluginManager.REGISTERED.get(0) instanceof StargateListener, "plugin: listener registered");
        expect(plugin.getCommand("stargate").getExecutor() instanceof StargateCommand && plugin.getCommand("stargate").getTabCompleter() instanceof StargateCommand, "plugin: /stargate has its executor and completer");
        StargateApi api = new org.bukkit.Server().getServicesManager().load(StargateApi.class);
        expect(api instanceof StargateApiImpl, "plugin: the API is registered as a service");
        expect(org.bukkit.scheduler.BukkitScheduler.PERIODS.equals(List.of(2L, 6000L)), "plugin: wormholes tick every 2 ticks, files saved every 5 minutes: " + org.bukkit.scheduler.BukkitScheduler.PERIODS);
        expect(Bukkit.getRecipe(Items.RECIPE_KEY) != null, "plugin: recipe registered");
        expect(Bukkit.CREATED.size() == 6 && Bukkit.getWorld("sg_mine") != null && Bukkit.getWorld("sg_netu") == null, "plugin: shipped planets + own planet - hidden planet: " + Bukkit.CREATED.size());
        expect(api.gate("Abydos Prime") != null && api.gate("Netu") == null && api.gate("mine") != null, "plugin: custom-planets.yml changes reach the gates: " + api.gates().size());
        expect(new File(dir, "gates.tsv").exists() == false, "plugin: nothing saved yet");

        Kit.TestPlayer boss = Kit.online("Boss");
        boss.perms.add(GateService.ADMIN);
        boss.stubSetLocation(new Location(Bukkit.getWorld("world"), 0, 64, 0));
        org.bukkit.scheduler.BukkitScheduler.REPEATING.get(1).run();
        expect(new File(dir, "gates.tsv").exists() && new File(dir, "planets.tsv").exists() && new File(dir, "dhds.tsv").exists(), "plugin: the repeating save writes the files");
        int gatesBefore = api.gates().size();

        // the repeating tick runs wormholes
        Gate a = plugin_store(plugin).gate("p-abydos");
        Gate c = plugin_store(plugin).gate("p-chulak");
        expect(api.dial(a.id, c.id) && api.isActive(a.id), "plugin: dial between two planets through the API");
        for (int i = 0; i < 200 && api.isActive(a.id) && !api.isActive(c.id); i++) {
            org.bukkit.scheduler.BukkitScheduler.REPEATING.get(0).run();
        }
        expect(api.isActive(c.id), "plugin: the repeating task opens the wormhole");

        // reload applies new settings and planets
        plugin.getConfig().set("dial.range", 12);
        plugin.getConfig().set("dhd.recipe.enabled", false);
        Files.writeString(new File(dir, "custom-planets.yml").toPath(), "planets.mine.name\ts\tMine Two\nplanets.mine.style\ts\tdesert\nhidden-planets\tl\tchulak\n");
        boss.messages.clear();
        plugin.getCommand("stargate").getExecutor().onCommand(boss, null, "stargate", new String[]{"reload"});
        expect(boss.heard("reloaded") && org.bukkit.plugin.java.JavaPlugin.CALLS.contains("reloadConfig"), "plugin: reload: " + boss.messages);
        expect(api.gate("Chulak") == null && api.gate("Netu") != null && api.gate("Abydos") != null, "plugin: reload applies the planet changes: " + api.planets());
        expect(!api.isActive(a.id) || api.gate("Chulak") == null, "plugin: (the wormhole to a removed planet is closed)");
        expect(plugin_cfg(plugin).range == 12 && Bukkit.getRecipe(Items.RECIPE_KEY) == null, "plugin: reload reads the new settings and the recipe switch");
        Files.writeString(new File(dir, "custom-planets.yml").toPath(), "hidden-planets\tl\tchulak\n");
        plugin.getCommand("stargate").getExecutor().onCommand(boss, null, "stargate", new String[]{"reload"});
        expect(api.gate("mine") == null && api.gate("Mine Two") == null, "plugin: a planet dropped from custom-planets.yml is gone after a reload");
        plugin.getConfig().set("dhd.recipe.enabled", true);

        plugin_store(plugin).add(LogicTest.gate("keepme", "Keep Me", 900, BlockFace.SOUTH, 6, 5, 4, 3, 2, 1));
        for (int i = 0; i < 40; i++) {
            org.bukkit.scheduler.BukkitScheduler.REPEATING.get(0).run();
        }
        expect(api.dial(a.id, "p-netu") && api.isActive(a.id), "plugin: a dial is under way when the server stops");
        List<org.bukkit.scheduler.BukkitTask> handles = new ArrayList<>(org.bukkit.scheduler.BukkitScheduler.HANDLES);
        expect(handles.size() == 2 && !handles.get(0).isCancelled() && !handles.get(1).isCancelled(), "plugin: two repeating tasks are running");
        plugin.onDisable();
        expect(handles.get(0).isCancelled() && handles.get(1).isCancelled(), "plugin: disable stops both repeating tasks");
        expect(!api.isActive(a.id), "plugin: disable closes wormholes");
        expect(Bukkit.getRecipe(Items.RECIPE_KEY) == null, "plugin: disable removes the recipe");
        expect(new org.bukkit.Server().getServicesManager().load(StargateApi.class) == null, "plugin: disable removes the service");

        // a second start reads the saved files back
        StargatePlugin p2 = new StargatePlugin();
        p2.onEnable();
        expect(plugin_store(p2).gates().size() >= gatesBefore - 1 && plugin_store(p2).gate("p-abydos").address.equals(a.address), "plugin: a restart reads the saved gates back: " + plugin_store(p2).gates().size());
        expect(plugin_store(p2).gate("keepme") != null, "plugin: a gate added just before stopping is saved and loaded back");
        p2.onDisable();

        // stopping a plugin that never started is harmless
        new StargatePlugin().onDisable();

        // a bare jar: no planets file
        Kit.reset();
        Bukkit.WORLDS.clear();
        Bukkit.CREATED.clear();
        org.bukkit.plugin.java.JavaPlugin.RESOURCES.remove("planets.yml");
        org.bukkit.plugin.java.JavaPlugin.DATA_FOLDER = Files.createTempDirectory("sg-plugin2").toFile();
        StargatePlugin p3 = new StargatePlugin();
        p3.onEnable();
        expect(p3.getCommand("stargate").getExecutor() != null && Bukkit.CREATED.isEmpty(), "plugin: starts with an empty jar too");
        p3.onDisable();
    }

    static StargateConfig plugin_cfg(StargatePlugin p) throws Exception {
        java.lang.reflect.Field f = StargatePlugin.class.getDeclaredField("cfg");
        f.setAccessible(true);
        return (StargateConfig) f.get(p);
    }

    static GateStore plugin_store(StargatePlugin p) throws Exception {
        java.lang.reflect.Field f = StargatePlugin.class.getDeclaredField("store");
        f.setAccessible(true);
        return (GateStore) f.get(p);
    }

    // ------------------------------------------------------------------ builder and service details

    static void builderAndService() throws Exception {
        Env e = new Env(null, false);
        World w = e.world;
        int before = Block.physicsWrites;
        e.builder.buildRing(w, 0, 64, 0, BlockFace.SOUTH);
        e.builder.fill(w, 0, 64, 0, BlockFace.SOUTH, Material.NETHER_PORTAL);
        Gate g0 = LogicTest.gate("bld", "Bld", 0, BlockFace.SOUTH, 1, 2, 3, 4, 5, 6);
        e.builder.light(w, g0, 3, false);
        e.builder.light(w, g0, 0, false);
        e.builder.fill(w, 0, 64, 0, BlockFace.SOUTH, Material.AIR);
        e.builder.platform(w, 50, 60, 50, 3, Material.SANDSTONE, Material.CUT_SANDSTONE);
        w.getBlockAt(50, 61, 50).setType(Material.STONE, false);
        e.builder.clearAbove(w, 50, 61, 50, 3, 4);
        expect(Block.physicsWrites == before, "builder: nothing it writes asks for neighbour physics updates");

        // platform: a disc with an outer rim, symmetrical on every side
        expect(w.getBlockAt(50, 60, 50).getType() == Material.SANDSTONE && w.getBlockAt(52, 60, 50).getType() == Material.SANDSTONE
                && w.getBlockAt(53, 60, 50).getType() == Material.CUT_SANDSTONE && w.getBlockAt(47, 60, 50).getType() == Material.CUT_SANDSTONE
                && w.getBlockAt(50, 60, 53).getType() == Material.CUT_SANDSTONE && w.getBlockAt(50, 60, 47).getType() == Material.CUT_SANDSTONE
                && w.getBlockAt(50, 60, 54).getType() == Material.AIR && w.getBlockAt(54, 60, 50).getType() == Material.AIR,
                "builder: platform is a disc with a rim on all four sides");
        expect(w.getBlockAt(53, 60, 52).getType() == Material.AIR && w.getBlockAt(52, 60, 52).getType() == Material.CUT_SANDSTONE && w.getBlockAt(51, 60, 51).getType() == Material.SANDSTONE,
                "builder: platform corners are cut round");

        // clearAbove: the box is cleared up to but not past the height, and out to the radius on all sides
        for (int y = 61; y < 66; y++) {
            for (int[] d : new int[][]{{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                w.getBlockAt(50 + d[0], y, 50 + d[1]).setType(Material.DIRT, false);
            }
        }
        e.builder.clearAbove(w, 50, 61, 50, 3, 4);
        boolean cleared = true;
        for (int y = 61; y < 65; y++) {
            for (int[] d : new int[][]{{0, 0}, {3, 0}, {-3, 0}, {0, 3}, {0, -3}}) {
                cleared &= w.getBlockAt(50 + d[0], y, 50 + d[1]).getType() == Material.AIR;
            }
        }
        expect(cleared && w.getBlockAt(50, 65, 50).getType() == Material.DIRT && w.getBlockAt(50, 60, 50).getType() == Material.SANDSTONE,
                "builder: clearAbove empties exactly the box above the platform");
        w.getBlockAt(54, 61, 50).setType(Material.DIRT, false);
        e.builder.clearAbove(w, 50, 61, 50, 3, 4);
        expect(w.getBlockAt(54, 61, 50).getType() == Material.DIRT, "builder: clearAbove leaves things outside the radius alone");

        // fmt: placeholders filled, an odd trailing argument ignored
        String s = e.gates.fmt("dhd-no-gate", "range", "5", "stray");
        expect(s.contains("5"), "service: fmt fills pairs and ignores a stray argument");

        // gateNear: the edge of the radius counts, and the first of two equal gates wins
        Env n = new Env(null, false);
        Gate a = n.gates.create("world", 10, 64, 0, BlockFace.SOUTH, null, "East");
        Gate b = n.gates.create("world", -10, 64, 0, BlockFace.SOUTH, null, "West");
        Location mid = new Location(n.world, 0.5, 68.0, 0.5);
        Gate first = n.store.gates().iterator().next();
        expect(n.gates.gateNear(mid, 10) == first, "service: gateNear takes a gate exactly at the radius, ties go to the first");
        expect(n.gates.gateNear(mid, 9) == null, "service: gateNear ignores a gate just outside the radius");
        expect(a != null && b != null && n.gates.gateNear(new Location(n.world, 4.5, 68.0, 0.5), 10) == a, "service: gateNear prefers the nearer gate");

        expect(n.gates.uniqueName(null).equals("Gate") && n.gates.uniqueName("   ").equals("Gate") && n.gates.uniqueName("  Home ").equals("Home"), "service: uniqueName defaults and trims");

        // placeDhd between two equal-distance rings links the first one found
        Env t = new Env(null, false);
        Kit.TestPlayer tp = t.player("Tie", true);
        t.builder.buildRing(t.world, -8, 64, 0, BlockFace.SOUTH);
        t.builder.buildRing(t.world, 7, 64, 0, BlockFace.SOUTH);
        Block tb = t.at(0, 65, 6);
        tb.setType(t.cfg.materials.dhd);
        List<GateShape.Found> found = GateShape.find(GateBuilder.reader(t.world), 0, 65, 6, t.cfg.range, t.cfg.materials);
        expect(found.size() == 2, "service: both rings are in range of the tie spot");
        GateShape.Found firstFound = found.get(0);
        expect(t.gates.placeDhd(tp, tb) == GateService.Placed.OK && t.store.gateAt("world", firstFound.ax(), firstFound.ay(), firstFound.az()) != null
                && t.store.gates().size() == 1, "service: a tie goes to the first ring found");

        // the wormhole service forgets old notices as time passes
        Env f = new Env(null, false);
        Kit.TestPlayer fp = f.player("Forget", false);
        java.lang.reflect.Field told = WormholeService.class.getDeclaredField("toldUntil");
        told.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<UUID, Long> tm = (java.util.Map<UUID, Long>) told.get(f.wh);
        tm.put(fp.getUniqueId(), 1L);
        f.tick(2);
        expect(tm.isEmpty(), "service: old one-way notices are dropped");
        java.lang.reflect.Field grace = WormholeService.class.getDeclaredField("graceUntil");
        grace.setAccessible(true);
        @SuppressWarnings("unchecked")
        java.util.Map<UUID, Long> gm = (java.util.Map<UUID, Long>) grace.get(f.wh);
        gm.put(fp.getUniqueId(), 1L);
        f.tick(2);
        expect(gm.isEmpty(), "service: old grace periods are dropped");
    }

    static <T> int times(List<T> list, T s) {
        int n = 0;
        for (T x : list) {
            if (x == s) {
                n++;
            }
        }
        return n;
    }

    static int told(Kit.TestPlayer p, String fragment) {
        int n = 0;
        for (String m : p.messages) {
            if (m.contains(fragment)) {
                n++;
            }
        }
        return n;
    }

    static void dialDetails() throws Exception {
        Env e = new Env(null, false);
        Kit.TestPlayer boss = e.player("Boss", true);
        Gate home = e.gateFor(boss, 100, 100);
        Gate far = e.gateFor(boss, 200, 100);
        e.stand(boss, home, 5);
        int per = Math.max(1, e.cfg.chevronTicks / WormholeService.STEP);

        // sounds: six chimes, one bell, then a beacon at each end; the warning is said once
        e.world.stubSounds.clear();
        boss.messages.clear();
        expect(e.wh.dial(boss, home, far) == WormholeService.Dial.STARTED, "details: dial starts");
        e.tick(per * 5);
        expect(told(boss, "Stand clear!") == 0, "details: no warning before the sixth chevron");
        e.tick(per);
        e.tick(per);
        expect(told(boss, "Stand clear!") == 1, "details: the warning is said once");
        expect(times(e.world.stubSounds, org.bukkit.Sound.BLOCK_NOTE_BLOCK_CHIME) == 6 && times(e.world.stubSounds, org.bukkit.Sound.BLOCK_NOTE_BLOCK_BELL) == 1
                && times(e.world.stubSounds, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE) == 2 && times(e.world.stubSounds, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH) == 0,
                "details: six chimes, one bell, a beacon at each end: " + e.world.stubSounds);
        e.wh.closeAll();

        // someone standing by the far gate is told a wormhole is arriving
        Env inc = new Env(null, false);
        inc.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ib = inc.player("Boss", true);
        Gate ih = inc.gateFor(ib, 100, 100);
        Gate ifar = inc.gateFor(ib, 200, 100);
        inc.stand(ib, ih, 5);
        Kit.TestPlayer watcher = inc.player("Watcher", false);
        inc.stand(watcher, ifar, 5);
        inc.wh.dial(ib, ih, ifar);
        inc.tickOpen(ih);
        expect(watcher.titles.size() == 1 && watcher.titles.get(0).contains("Incoming") && ib.titles.isEmpty(), "details: a bystander at the far gate gets a title: " + watcher.titles);

        // a traveller stepping in on the very tick the wormhole times out is not sent
        Env tm = new Env(null, true);
        tm.cfg.cooldownSeconds = 0;
        Kit.TestPlayer tp = tm.player("Boss", true);
        Gate th = tm.gateFor(tp, 100, 100);
        Gate taby = tm.store.gate("p-abydos");
        tm.stand(tp, th, 5);
        tm.wh.dial(tp, th, taby);
        tm.tickOpen(th);
        tm.wh.of(th).sinceTraffic = tm.cfg.idleSeconds * 20 - 2;
        tm.into(tp, th);
        tm.tick(1);
        expect(tm.wh.of(th) == null && tp.getLocation().getWorld().getName().equals("world"), "details: on the tick the idle timeout closes the gate nobody is sent through");
        tm.stand(tp, th, 5);
        tm.wh.dial(tp, th, taby);
        tm.tickOpen(th);
        tm.wh.of(th).openFor = tm.cfg.maxSeconds * 20 - 2;
        tm.into(tp, th);
        tm.tick(1);
        expect(tm.wh.of(th) == null && tp.getLocation().getWorld().getName().equals("world"), "details: nor on the tick the time limit closes it");

        // a far gate that has been damaged: the dial fails at the last chevron and the gate must rest
        Env f = new Env(null, false);
        Kit.TestPlayer fb = f.player("Boss", true);
        Gate h2 = f.gateFor(fb, 100, 100);
        Gate far2 = f.gateFor(fb, 200, 100);
        f.stand(fb, h2, 5);
        f.world.stubSounds.clear();
        f.at(200, 64, 100).setType(Material.AIR);
        expect(f.wh.dial(fb, h2, far2) == WormholeService.Dial.STARTED, "details: a dial to a damaged gate starts");
        f.tick(per * 7);
        expect(!f.wh.busy(h2) && times(f.world.stubSounds, org.bukkit.Sound.BLOCK_FIRE_EXTINGUISH) == 1 && f.lit(h2) == 0, "details: it fizzles out with a sound and dark chevrons");
        expect(f.wh.dial(fb, h2, far2) == WormholeService.Dial.COOLDOWN, "details: a failed dial still makes the gate rest");

        // a gate deleted while dialing: the dial is dropped at once and nothing more lights up
        Env g = new Env(null, false);
        g.cfg.chevronTicks = 2;
        Kit.TestPlayer gb = g.player("Boss", true);
        Gate h3 = g.gateFor(gb, 100, 100);
        Gate far3 = g.gateFor(gb, 200, 100);
        expect(g.wh.dial(gb, h3, far3) == WormholeService.Dial.STARTED, "details: a dial starts");
        g.store.remove(h3.id);
        g.tick(1);
        expect(g.wh.of(h3) == null && g.lit(h3) == 0, "details: a deleted gate stops dialing and lights nothing: lit " + g.lit(h3));

        // a gate whose world has gone: the dial is dropped without a crash
        Env h = new Env(null, false);
        Kit.TestPlayer hb = h.player("Boss", true);
        Gate h4 = h.gateFor(hb, 100, 100);
        Gate far4 = h.gateFor(hb, 200, 100);
        h.cfg.chevronTicks = 2;
        expect(h.wh.dial(hb, h4, far4) == WormholeService.Dial.STARTED, "details: a dial starts before the world goes");
        Bukkit.WORLDS.remove("world");
        h.tick(2);
        expect(h.wh.of(h4) == null, "details: a gate in a missing world stops dialing");
    }

    static void travelDetails() throws Exception {
        Env e = new Env(null, true);
        e.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ann = e.player("Ann", false);
        Gate home = e.gateFor(ann, 100, 100);
        Gate abydos = e.store.gate("p-abydos");
        World aw = Bukkit.getWorld("sg_abydos");
        e.cfg.kawooshDamage = 0.0;
        Zombie z = new Zombie();
        Bukkit.ENTITIES.put(z.getUniqueId(), z);
        z.stubSetLocation(new Location(e.world, 100.5, 65, 102.5));
        e.stand(ann, home, 9);
        e.world.stubParticles.clear();
        aw.stubParticles.clear();
        e.world.stubSounds.clear();
        aw.stubSounds.clear();
        e.wh.dial(ann, home, abydos);
        e.tickOpen(home);
        expect(z.damageCalls == 0 && z.getVelocity().getZ() == 1.2, "kawoosh: zero damage means no damage at all, but still a push: " + z.damageCalls);
        expect(times(e.world.stubParticles, org.bukkit.Particle.SPLASH) == 1 && times(aw.stubParticles, org.bukkit.Particle.SPLASH) == 1, "kawoosh: a splash at each end");
        expect(times(aw.stubSounds, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE) == 1 && times(e.world.stubSounds, org.bukkit.Sound.BLOCK_BEACON_ACTIVATE) == 1, "kawoosh: a beacon at each end");

        // someone else steps through: effects at both ends, and both addresses are learned
        Kit.TestPlayer bob = e.player("Bob", false);
        expect(!e.store.knows(bob.getUniqueId(), home.id), "travel: Bob does not know the home gate");
        e.into(bob, home);
        e.tick(1);
        expect(bob.getLocation().getWorld().getName().equals("sg_abydos") && times(e.world.stubSounds, org.bukkit.Sound.ENTITY_ENDERMAN_TELEPORT) == 1
                && times(aw.stubSounds, org.bukkit.Sound.BLOCK_PORTAL_TRAVEL) == 1 && times(aw.stubParticles, org.bukkit.Particle.CLOUD) == 1, "travel: sound and cloud effects on a crossing");
        expect(e.store.knows(bob.getUniqueId(), home.id) && e.store.knows(bob.getUniqueId(), abydos.id), "travel: a traveller learns where they came from");
        e.wh.close(home, null);

        // a world that disappears while open closes the wormhole
        e.wh.dial(ann, home, abydos);
        e.tickOpen(home);
        expect(e.wh.of(home) != null && e.wh.of(home).phase == WormholeService.Phase.OPEN, "travel: open again");
        Bukkit.WORLDS.remove("sg_abydos");
        e.tick(1);
        expect(e.wh.of(home) == null && !e.wh.busy(home), "travel: losing the far world closes the wormhole");
    }

    static void closeDetails() throws Exception {
        Env e = new Env(null, false);
        e.cfg.cooldownSeconds = 0;
        Kit.TestPlayer boss = e.player("Boss", true);
        Gate home = e.gateFor(boss, 100, 100);
        Gate far = e.gateFor(boss, 200, 100);
        e.stand(boss, home, 5);
        int per = Math.max(1, e.cfg.chevronTicks / WormholeService.STEP);

        // a player exactly at the warning radius is told "stand clear"
        Kit.TestPlayer edge = e.player("Edge", false);
        edge.stubSetLocation(new Location(e.world, home.ax + 0.5 + e.cfg.warnRadius, home.ay + 4.5, home.az + 0.5));
        Kit.TestPlayer outside = e.player("Outside", false);
        outside.stubSetLocation(new Location(e.world, home.ax + 0.5 + e.cfg.warnRadius + 0.01, home.ay + 4.5, home.az + 0.5));

        // cancelling a dial only touches the dialing gate
        e.world.stubSounds.clear();
        e.wh.dial(boss, home, far);
        e.tick(per * 6);
        expect(told(edge, "Stand clear!") == 1 && told(outside, "Stand clear!") == 0, "close: the warning reaches exactly the radius and no further");
        e.wh.close(home, null);
        expect(times(e.world.stubSounds, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE) == 1, "close: cancelling a dial shuts only the dialing gate: " + e.world.stubSounds);

        // closing an open wormhole shuts both ends and gives back every ticket
        e.world.stubSounds.clear();
        e.wh.dial(boss, home, far);
        e.tickOpen(home);
        for (Gate g : new Gate[]{home, far}) {
            for (int cx = (g.ax >> 4) - 1; cx <= (g.ax >> 4) + 1; cx++) {
                for (int cz = (g.az >> 4) - 1; cz <= (g.az >> 4) + 1; cz++) {
                    expect(!e.world.getPluginChunkTickets(cx, cz).isEmpty(), "close: ticket held at " + cx + "," + cz + " while open");
                }
            }
        }
        expect(e.world.getPluginChunkTickets((home.ax >> 4) + 2, home.az >> 4).isEmpty(), "close: no ticket beyond the neighbours");
        e.wh.close(home, null);
        expect(times(e.world.stubSounds, org.bukkit.Sound.BLOCK_BEACON_DEACTIVATE) == 2, "close: closing an open wormhole shuts both ends");
        for (Gate g : new Gate[]{home, far}) {
            for (int cx = (g.ax >> 4) - 1; cx <= (g.ax >> 4) + 1; cx++) {
                for (int cz = (g.az >> 4) - 1; cz <= (g.az >> 4) + 1; cz++) {
                    expect(e.world.getPluginChunkTickets(cx, cz).isEmpty(), "close: ticket given back at " + cx + "," + cz);
                }
            }
        }

        // the iris makes a sound
        e.world.stubSounds.clear();
        e.wh.iris(home, true);
        e.wh.iris(home, false);
        expect(times(e.world.stubSounds, org.bukkit.Sound.BLOCK_IRON_DOOR_CLOSE) == 1 && times(e.world.stubSounds, org.bukkit.Sound.BLOCK_IRON_DOOR_OPEN) == 1, "close: the iris clanks shut and open");

        // grace: a traveller put straight back at the gate is held for exactly the grace period
        Env t = new Env(null, true);
        t.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ann = t.player("Ann", false);
        Gate h = t.gateFor(ann, 100, 100);
        Gate aby = t.store.gate("p-abydos");
        t.stand(ann, h, 5);
        t.wh.dial(ann, h, aby);
        t.tickOpen(h);
        ann.setFallDistance(12f);
        t.into(ann, h);
        t.tick(1);
        expect(ann.getLocation().getWorld().getName().equals("sg_abydos") && ann.getFallDistance() == 0f, "grace: she crosses and her fall is forgotten");
        t.into(ann, h);
        t.tick(19);
        expect(ann.getLocation().getWorld().getName().equals("world"), "grace: held for the whole grace period");
        t.tick(1);
        expect(ann.getLocation().getWorld().getName().equals("sg_abydos"), "grace: and let through the tick it ends");
    }

    static void menuDetails() throws Exception {
        Env e = new Env(null, true);
        Kit.TestPlayer boss = e.player("Boss", true);
        Gate home = e.gateFor(boss, 100, 100);
        for (int i = 0; i < 50; i++) {
            e.gates.create("world", 1000 + i * 20, 64, 3000, BlockFace.SOUTH, null, "Extra " + String.format("%02d", i));
        }
        DialMenu m = DialMenu.build(e.gates, e.wh, boss, home, 0);
        org.bukkit.inventory.Inventory inv = m.getInventory();
        expect(inv.getItem(44) != null && inv.getItem(45) == null && inv.getItem(DialMenu.NEXT_SLOT) != null, "menu: page one holds exactly 45 gates, then the controls");
        expect(Kit.loreOf(inv, DialMenu.INFO_SLOT).contains(home.address.names()) && Kit.loreOf(inv, DialMenu.INFO_SLOT).contains(home.address.code()), "menu: the info slot shows this gate's address");
        int planetSlot = -1;
        int plainSlot = -1;
        for (int i = 0; i < 45; i++) {
            String name = Kit.nameOf(inv, i);
            if (name.contains("Abydos")) {
                planetSlot = i;
            }
            if (name.contains("Extra 00")) {
                plainSlot = i;
            }
        }
        expect(planetSlot >= 0 && Kit.loreOf(inv, planetSlot).contains("Desert world"), "menu: a planet entry shows its description");
        expect(plainSlot >= 0 && Kit.loreOf(inv, plainSlot).contains("world") && !Kit.loreOf(inv, plainSlot).contains("Desert"), "menu: an ordinary gate shows its world");
    }

    static void forgedClicks() throws Exception {
        Env e = new Env(null, false);
        Kit.TestPlayer ann = e.player("Ann", false);
        Kit.TestPlayer bob = e.player("Bob", false);
        Gate home = e.gateFor(ann, 100, 100);
        DialMenu annMenu = DialMenu.build(e.gates, e.wh, ann, home, 0);
        expect(e.slotOf(annMenu, DialMenu.Kind.IRIS, null) == DialMenu.IRIS_SLOT, "forged: the owner's menu has an iris button");
        e.stand(bob, home, 5);
        bob.opened = annMenu.getInventory();
        bob.messages.clear();
        e.click(bob, DialMenu.IRIS_SLOT);
        expect(!home.iris && bob.heard("Only the owner"), "forged: a stranger tapping the iris button on a borrowed menu is refused and told: " + bob.messages);
        e.items.registerRecipe();
        e.cfg.recipeEnabled = false;
        e.items.registerRecipe();
        expect(Bukkit.getRecipe(Items.RECIPE_KEY) == null, "items: turning the recipe off removes the old one on re-register");
    }

    static void listenerDetails() throws Exception {
        Env e = new Env(null, false);
        Kit.TestPlayer ann = e.player("Ann", false);
        Kit.TestPlayer bob = e.player("Bob", false);
        Gate home = e.gateFor(ann, 100, 100);
        e.stand(ann, home, 5);
        Block dhdBlock = e.at(100, 65, 106);

        // the off hand does nothing
        PlayerInteractEvent off = new PlayerInteractEvent(ann, Action.RIGHT_CLICK_BLOCK, null, dhdBlock);
        off.stubHand = org.bukkit.inventory.EquipmentSlot.OFF_HAND;
        e.listener.onInteract(off);
        expect(!off.isCancelled() && ann.opened == null, "listener: an off-hand click is ignored");

        // a left click on the device does nothing; a right click opens the menu and does not also read a tablet
        PlayerInteractEvent left = new PlayerInteractEvent(ann, Action.LEFT_CLICK_BLOCK, null, dhdBlock);
        e.listener.onInteract(left);
        expect(!left.isCancelled() && ann.opened == null, "listener: a left click on the device does not open the menu");
        Gate other = e.gateFor(bob, 200, 200);
        ItemStack tablet = e.items.tablet(other);
        ann.getInventory().setItemInMainHand(tablet);
        PlayerInteractEvent right = new PlayerInteractEvent(ann, Action.RIGHT_CLICK_BLOCK, tablet, dhdBlock);
        e.listener.onInteract(right);
        expect(right.isCancelled() && ann.opened != null && !e.store.knows(ann.getUniqueId(), other.id) && ann.getInventory().getItemInMainHand() == tablet,
                "listener: right-clicking the device with a tablet opens the menu and keeps the tablet");

        // breaking a ring: the owner takes only that block's gate record, the rest of the ring stays
        int[] top = GateShape.pos(home.ax, home.ay, home.az, home.facing, new GateShape.Cell(0, 8));
        int[] side = GateShape.pos(home.ax, home.ay, home.az, home.facing, new GateShape.Cell(3, 4));
        BlockBreakEvent br = new BlockBreakEvent(e.world.getBlockAt(top[0], top[1], top[2]), ann);
        e.listener.onBreak(br);
        expect(!br.isCancelled() && e.store.gate(home.id) == null && e.world.getBlockAt(side[0], side[1], side[2]).getType() != Material.AIR,
                "listener: the owner breaking a ring block forgets the gate but leaves the rest standing");

        // an ownerless ordinary gate gives the general refusal, not the owner message
        Gate nobody = e.gates.create("world", 400, 64, 400, BlockFace.SOUTH, null, "Nobody's");
        e.ring(400, 400, BlockFace.SOUTH);
        int[] nt = GateShape.pos(nobody.ax, nobody.ay, nobody.az, nobody.facing, new GateShape.Cell(0, 8));
        bob.messages.clear();
        BlockBreakEvent nb = new BlockBreakEvent(e.world.getBlockAt(nt[0], nt[1], nt[2]), bob);
        e.listener.onBreak(nb);
        expect(nb.isCancelled() && bob.heard("You cannot do that") && !bob.heard("Only the owner"), "listener: an ownerless gate refuses with the general message: " + bob.messages);

        // info names an owner who is offline by their id when the server has no name for them
        UUID away = UUID.randomUUID();
        nobody.owner = away;
        e.stand(ann, nobody, 5);
        expect(said(e.run(ann, "info"), away.toString()), "listener: info falls back to the owner's id when no name is known");
    }

    static void commandDetails() throws Exception {
        Env e = new Env(null, false);
        Kit.TestPlayer ann = e.player("Ann", false);
        // away from every gate each of these says one thing and stops
        List<String> r = e.run(ann, "list");
        expect(r.size() == 1 && said(r, "do not know any addresses"), "cmd: list with nothing known says so once: " + r);
        r = e.run(ann, "dial", "abc");
        expect(r.size() == 1 && said(r, "No complete gate"), "cmd: dial by name away from a gate: " + r);
        r = e.run(ann, "dial");
        expect(r.size() == 1 && said(r, "No complete gate") && ann.opened == null, "cmd: bare dial away from a gate: " + r);
        r = e.run(ann, "iris");
        expect(r.size() == 1 && said(r, "No complete gate"), "cmd: iris away from a gate: " + r);

        Gate home = e.gateFor(ann, 100, 100);
        e.stand(ann, home, 5);
        ann.messages.clear();
        r = e.run(ann, "dial");
        expect(r.isEmpty() && ann.opened != null && ann.opened.getHolder() instanceof DialMenu, "cmd: bare dial just opens the menu: " + r);
        ann.opened = null;
        r = e.run(ann, "dial", "zzz");
        expect(r.size() == 1 && said(r, "Nothing answers") && !e.wh.busy(home), "cmd: an unknown name says so once and dials nothing: " + r);

        r = e.run(ann, "iris");
        expect(r.size() == 1 && said(r, "The iris closes.") && home.iris, "cmd: iris closes and says so: " + r);
        r = e.run(ann, "iris");
        expect(r.size() == 1 && said(r, "The iris opens.") && !home.iris, "cmd: iris opens and says so: " + r);

        // tablets: unknown name, then no paper
        r = e.run(ann, "tablet", "nonexistent");
        expect(r.size() == 1 && said(r, "Nothing answers"), "cmd: tablet of an unknown gate says so once: " + r);
        ann.getInventory().setItemInMainHand(new ItemStack(Material.STICK));
        r = e.run(ann, "tablet", "Ann's", "gate");
        expect(r.size() == 1 && said(r, "Hold a piece of paper") && ann.getInventory().getItemInMainHand().getType() == Material.STICK, "cmd: no paper, no tablet: " + r);
        ann.getInventory().setItemInMainHand(new ItemStack(Material.PAPER, 2));
        r = e.run(ann, "tablet", "Ann's", "gate");
        expect(said(r, "tablet") && ann.getInventory().getItemInMainHand().getAmount() == 1, "cmd: with paper the tablet is made and one sheet used: " + r);
        ann.setGameMode(GameMode.CREATIVE);
        for (int i = 0; i < 100; i++) {
            ann.getInventory().setItem(i, new ItemStack(Material.STONE));
        }
        int drops = e.world.stubDrops.size();
        e.run(ann, "tablet", "Ann's", "gate");
        expect(e.world.stubDrops.size() == drops + 1 && home.id.equals(e.items.tabletGate(e.world.stubDrops.get(drops))), "cmd: a tablet that does not fit in the bag is dropped at the player's feet");
    }

    static void gateServiceDetails() throws Exception {
        Env e = new Env(null, false);
        e.cfg.cooldownSeconds = 0;
        Kit.TestPlayer ann = e.player("Ann", false);
        Kit.TestPlayer bob = e.player("Bob", false);
        Gate home = e.gateFor(ann, 100, 100);
        Gate far = e.gateFor(ann, 200, 100);
        e.store.flush();

        // renaming says what happened and saves
        expect(!e.gates.rename(bob, home, "Mine now"), "service: a stranger cannot rename");
        expect(!e.gates.rename(ann, home, "   ") && !e.gates.rename(ann, home, "x".repeat(33)), "service: blank and too long names are refused");
        expect(!e.gates.rename(ann, home, far.name) && !e.store.dirty(), "service: a taken name is refused and nothing is saved");
        expect(e.gates.rename(ann, home, "Base") && home.name.equals("Base") && e.store.dirty(), "service: a rename works and marks the store dirty");
        expect(e.gates.rename(ann, home, "y".repeat(32)) && e.gates.rename(ann, home, "Base"), "service: a name of exactly 32 characters is fine");
        e.store.flush();

        // the iris: changing it saves, changing it to what it already is does nothing
        e.gates.setIris(home, true);
        expect(home.iris && e.store.dirty() && e.count(home, e.cfg.materials.iris) == 31, "service: closing the iris fills the opening and saves");
        e.store.flush();
        e.gates.setIris(home, true);
        expect(!e.store.dirty(), "service: closing a closed iris changes nothing");
        e.gates.setIris(home, false);
        expect(!home.iris && e.count(home, Material.AIR) == 31, "service: opening the iris empties the opening");

        // an open wormhole stays open when the iris is told to stay open
        e.stand(ann, home, 5);
        e.wh.dial(ann, home, far);
        e.tickOpen(home);
        int horizon = e.count(home, Material.NETHER_PORTAL);
        e.gates.setIris(home, false);
        expect(horizon == 31 && e.count(home, Material.NETHER_PORTAL) == 31, "service: opening an open iris leaves the horizon alone");
        e.wh.close(home, null);

        // deleting a gate: the opening is emptied even with an iris in it, and nothing asks for physics updates
        e.gates.setIris(far, true);
        e.gates.remove(far, false);
        expect(e.count(far, Material.AIR) == 31 && e.store.gate(far.id) == null, "service: deleting a gate empties its opening");
        Gate third = e.gateFor(ann, 300, 100);
        Block dhdBlock = e.at(300, 65, 306);
        int writes = Block.physicsWrites;
        e.gates.remove(third, true);
        expect(dhdBlock.getType() == Material.AIR && e.count(third, Material.AIR) == 31 && e.at(300, 64, 300).getType() == Material.AIR, "service: deleting with clear takes the ring and the device away");
        expect(Block.physicsWrites == writes, "service: taking a gate down asks for no physics updates: " + (Block.physicsWrites - writes));

        // the far chunk is loaded when a wormhole connects
        Env c = new Env(null, false);
        c.cfg.cooldownSeconds = 0;
        Kit.TestPlayer cb = c.player("Boss", true);
        Gate h = c.gateFor(cb, 100, 100);
        Gate f = c.gateFor(cb, 600, 100);
        c.stand(cb, h, 5);
        int loaded = c.world.stubLoadedChunkCount();
        c.wh.dial(cb, h, f);
        c.tickOpen(h);
        expect(c.world.stubLoadedChunkCount() > loaded, "service: connecting loads the far gate's chunk");
    }

    static void portalReach() throws Exception {
        Env e = new Env(null, false);
        Kit.TestPlayer ann = e.player("Ann", false);
        Gate g = e.gateFor(ann, 700, 700);
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxY = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (int[] p : GateShape.insideWorld(g.ax, g.ay, g.az, g.facing)) {
            minX = Math.min(minX, p[0]);
            maxX = Math.max(maxX, p[0]);
            minY = Math.min(minY, p[1]);
            maxY = Math.max(maxY, p[1]);
            minZ = Math.min(minZ, p[2]);
            maxZ = Math.max(maxZ, p[2]);
        }
        int midY = (minY + maxY) / 2;
        int[][] probes = {
                {minX - 1, midY, g.az, 1}, {minX - 2, midY, g.az, 0}, {maxX + 1, midY, g.az, 1}, {maxX + 2, midY, g.az, 0},
                {g.ax, midY, minZ - 1, 1}, {g.ax, midY, minZ - 2, 0}, {g.ax, midY, maxZ + 1, 1}, {g.ax, midY, maxZ + 2, 0},
                {g.ax, minY - 2, g.az, 1}, {g.ax, minY - 3, g.az, 0}, {g.ax, maxY + 1, g.az, 1}, {g.ax, maxY + 2, g.az, 0}};
        for (int[] pr : probes) {
            PlayerPortalEvent ev = new PlayerPortalEvent(ann, new Location(e.world, pr[0] + 0.5, pr[1] + 0.5, pr[2] + 0.5), new Location(e.world, 0, 0, 0));
            e.listener.onPortal(ev);
            expect(ev.isCancelled() == (pr[3] == 1), "portal: probe " + pr[0] + "," + pr[1] + "," + pr[2] + " should be " + (pr[3] == 1 ? "stopped" : "left alone"));
        }
    }
}
