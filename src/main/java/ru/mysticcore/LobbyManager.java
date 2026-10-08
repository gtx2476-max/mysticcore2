package ru.mysticcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.block.Block;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.util.Random;

/**
 * Лобби (void-мир): платформа, NPC выбора режима, NPC Фугафаг, автошахта, защита и запрет команд.
 * Вход на сервер -> лобби. NPC «Выбор режима» -> меню -> Выживание -> спавн выживания -> /rtp.
 */
public final class LobbyManager implements Listener {

    static final int Y = 100;
    static final int MX1 = 22, MX2 = 36, MZ1 = -7, MZ2 = 7, MY1 = 93, MY2 = 100;

    public static final class VoidGenerator extends ChunkGenerator {
        @Override
        public Location getFixedSpawnLocation(World world, Random random) {
            return new Location(world, 0.5, Y + 1, 0.5);
        }
    }

    private final MysticCore plugin;
    private final Random random = new Random();
    private final NamespacedKey npcKey;
    private World world;
    private BukkitTask mineTask;
    private BukkitTask voidTask;

    public LobbyManager(MysticCore plugin) {
        this.plugin = plugin;
        this.npcKey = new NamespacedKey(plugin, "lobby_npc");
    }

    public World world() { return world; }

    public boolean isLobby(World w) { return world != null && w != null && w.equals(world); }

    // ---------- запуск ----------

    public void start() {
        String name = plugin.getConfig().getString("lobby-world", "mystic_lobby");
        world = Bukkit.getWorld(name);
        if (world == null) {
            WorldCreator wc = new WorldCreator(name);
            wc.environment(World.Environment.NORMAL);
            wc.generator(new VoidGenerator());
            world = wc.createWorld();
        }
        if (world == null) {
            plugin.getLogger().severe("Не удалось создать мир лобби!");
            return;
        }
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setTime(6000);
        world.setStorm(false);
        world.setSpawnLocation(0, Y + 1, 0);
        world.getWorldBorder().setCenter(0, 0);
        world.getWorldBorder().setSize(200);

        build();
        resetMine(false);
        spawnNpcs();

        long period = Math.max(1, plugin.getConfig().getInt("mine.reset-minutes", 3)) * 1200L;
        mineTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> resetMine(true), period, period);
        voidTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : world.getPlayers()) {
                if (p.getLocation().getY() < Y - 20) p.teleport(spawn());
            }
        }, 20L, 20L);
    }

    public void shutdown() {
        if (mineTask != null) mineTask.cancel();
        if (voidTask != null) voidTask.cancel();
    }

    public Location spawn() { return new Location(world, 0.5, Y + 1, 3.5, 180f, 0f); }

    public Location mineSpawn() { return new Location(world, 19.5, Y + 1, 0.5, -90f, 0f); }

    // ---------- постройка ----------

    private void set(int x, int y, int z, Material m) {
        world.getBlockAt(x, y, z).setType(m, false);
    }

    private void build() {
        int R = 14;
        for (int dx = -R; dx <= R; dx++) {
            for (int dz = -R; dz <= R; dz++) {
                int d2 = dx * dx + dz * dz;
                if (d2 > R * R) continue;
                Material top = d2 <= 9 ? Material.QUARTZ_BLOCK : (d2 >= 169 ? Material.POLISHED_BLACKSTONE : Material.SMOOTH_STONE);
                set(dx, Y, dz, top);
                set(dx, Y - 1, dz, Material.STONE_BRICKS);
                set(dx, Y - 2, dz, Material.DEEPSLATE_BRICKS);
            }
        }
        // мост к шахте
        for (int x = 14; x <= 21; x++) {
            for (int z = -1; z <= 1; z++) {
                set(x, Y, z, Material.STONE_BRICKS);
                set(x, Y - 1, z, Material.DEEPSLATE_BRICKS);
            }
        }
        // колонны со светом
        int[][] pillars = {{10, 10}, {-10, 10}, {10, -10}, {-10, -10}};
        for (int[] pc : pillars) {
            for (int h = 1; h <= 3; h++) set(pc[0], Y + h, pc[1], Material.QUARTZ_PILLAR);
            set(pc[0], Y + 4, pc[1], Material.SEA_LANTERN);
        }
    }

    private Material rollOre() {
        int r = random.nextInt(1000);
        if (r < 10) return Material.DIAMOND_ORE;
        if (r < 24) return Material.GOLD_ORE;
        if (r < 50) return Material.LAPIS_ORE;
        if (r < 90) return Material.IRON_ORE;
        if (r < 140) return Material.COAL_ORE;
        if (r < 180) return Material.COPPER_ORE;
        if (r < 200) return Material.REDSTONE_ORE;
        if (r < 204) return Material.EMERALD_ORE;
        return Material.STONE;
    }

    public boolean inMine(Block b) {
        return isLobby(b.getWorld()) && b.getX() >= MX1 && b.getX() <= MX2 && b.getZ() >= MZ1 && b.getZ() <= MZ2
                && b.getY() >= MY1 && b.getY() <= MY2;
    }

    public void resetMine(boolean announce) {
        if (world == null) return;
        for (Player p : world.getPlayers()) {
            Location l = p.getLocation();
            if (l.getX() >= MX1 - 1 && l.getX() <= MX2 + 2 && l.getZ() >= MZ1 - 1 && l.getZ() <= MZ2 + 2
                    && l.getY() >= MY1 - 1 && l.getY() <= MY2 + 3) {
                p.teleport(mineSpawn());
            }
        }
        for (int x = MX1; x <= MX2; x++) {
            for (int z = MZ1; z <= MZ2; z++) {
                for (int y = MY1; y <= MY2; y++) set(x, y, z, rollOre());
            }
        }
        if (announce) {
            for (Player p : world.getPlayers()) U.msg(p, "&6&l[ШАХТА] &fАвтошахта обновлена!");
        }
    }

    // ---------- NPC ----------

    private void spawnNpcs() {
        for (Entity e : world.getEntities()) {
            if (e.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) e.remove();
        }
        spawnNpc(new Location(world, 0.5, Y + 1, -6.5, 0f, 0f), "&a&lВыбор режима", "mode");
        spawnNpc(new Location(world, -6.5, Y + 1, -2.5, -90f, 0f), "&2&lФугафаг", "fugu");
        spawnNpc(new Location(world, 6.5, Y + 1, -2.5, 90f, 0f), "&6&lСкупщик", "buyer");
    }

    private void spawnNpc(Location loc, String name, String type) {
        world.spawn(loc, Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(true);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setProfession(Villager.Profession.CLERIC);
            v.customName(U.c(name));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, type);
        });
    }

    // ---------- телепорты ----------

    public void teleportLobby(Player p) {
        if (world == null) return;
        p.teleport(spawn());
        U.msg(p, "&7Вы в лобби.");
    }

    public void teleportMine(Player p) {
        if (world == null) return;
        p.teleport(mineSpawn());
        U.msg(p, "&bАвтошахта: ломайте руду, она обновляется каждые " + plugin.getConfig().getInt("mine.reset-minutes", 3) + " мин.");
    }

    public void sendToSurvival(Player p) {
        World w = plugin.survivalWorld();
        if (w == null) {
            U.msg(p, "&cМир выживания не найден.");
            return;
        }
        Location s = w.getSpawnLocation();
        int y = w.getHighestBlockYAt(s.getBlockX(), s.getBlockZ());
        p.teleport(new Location(w, s.getBlockX() + 0.5, y + 1, s.getBlockZ() + 0.5));
        p.setGameMode(GameMode.SURVIVAL);
        Component c = U.c("&a&lВы на спавне выживания! &fНажмите сюда или введите &e/rtp&f, чтобы попасть на карту.")
                .clickEvent(ClickEvent.runCommand("/rtp"));
        p.sendMessage(c);
    }

    // ---------- события ----------

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (world == null) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (p.isOnline()) {
                p.teleport(spawn());
                U.msg(p, "&6&lДобро пожаловать! &fНажмите на NPC &a«Выбор режима»&f, чтобы начать игру.");
            }
        }, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCommand(PlayerCommandPreprocessEvent e) {
        Player p = e.getPlayer();
        if (!isLobby(p.getWorld())) return;
        if (p.hasPermission("mystic.admin")) return;
        e.setCancelled(true);
        U.msg(p, "&cВ лобби команды отключены.");
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onNpcClick(PlayerInteractEntityEvent e) {
        String type = e.getRightClicked().getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (type == null) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (type.equals("mode")) plugin.menus().openModes(e.getPlayer());
        else if (type.equals("fugu")) plugin.menus().openExchange(e.getPlayer());
        else if (type.equals("buyer")) plugin.exchange().openBuyer(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (!isLobby(p.getWorld()) || p.hasPermission("mystic.admin")) return;
        Block b = e.getBlock();
        if (!inMine(b)) {
            e.setCancelled(true);
            return;
        }
        if (b.getType() == Material.STONE) return;
        long coins = plugin.getConfig().getLong("mine.coins-per-ore", 3);
        if (coins > 0) {
            plugin.getEconomy().addCoins(p.getUniqueId(), coins);
            p.sendActionBar(U.c("&e+" + coins + " монеток"));
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Player p = e.getPlayer();
        if (isLobby(p.getWorld()) && !p.hasPermission("mystic.admin")) e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageEvent e) {
        if (!isLobby(e.getEntity().getWorld())) return;
        if (e.getEntity() instanceof Player p) {
            e.setCancelled(true);
            if (e.getCause() == EntityDamageEvent.DamageCause.VOID) p.teleport(spawn());
        } else if (e.getEntity().getPersistentDataContainer().has(npcKey, PersistentDataType.STRING)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onFood(FoodLevelChangeEvent e) {
        if (isLobby(e.getEntity().getWorld())) e.setCancelled(true);
    }
}
