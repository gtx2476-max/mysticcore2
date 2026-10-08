package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Ежедневные (3) и недельные (2) задания. Награда — монетки MysticCore + очки сезонного пропуска. */
public final class QuestManager implements Listener {

    public enum Type {
        KILL_MOBS("Убить монстров"), KILL_PLAYERS("Победить игроков"), MINE_ORE("Добыть руды"),
        OPEN_DROP("Открыть мистические сундуки"), BOSS_DAMAGE("Нанести урон Маме Фугу"),
        BOSS_KILL("Победить Маму Фугу"), SELL_ITEMS("Продать ресурсов скупщику"),
        BM_BUY("Купить на Чёрном рынке"), OPEN_CASE("Открыть кейсы"), PLAY_MINUTES("Провести минут онлайн");

        public final String title;

        Type(String title) { this.title = title; }
    }

    public record Def(String id, Type type, int target, long coins, int xp, boolean weekly) {
        public String name() { return type.title + ": " + target; }
    }

    private static final class Active {
        final String id;
        int progress;
        boolean done;

        Active(String id) { this.id = id; }
    }

    private static final class Data {
        long dayKey = -1;
        long weekKey = -1;
        List<Active> daily = new ArrayList<>();
        List<Active> weekly = new ArrayList<>();
    }

    private final MysticCore plugin;
    private final Random random = new Random();
    private final Map<String, Def> defs = new HashMap<>();
    private final List<Def> dailyPool = new ArrayList<>();
    private final List<Def> weeklyPool = new ArrayList<>();
    private final Map<UUID, Data> data = new HashMap<>();
    private BukkitTask saveTask;

    public QuestManager(MysticCore plugin) {
        this.plugin = plugin;
        d(Type.KILL_MOBS, 25, 150, 40);
        d(Type.KILL_MOBS, 50, 300, 60);
        d(Type.MINE_ORE, 40, 200, 40);
        d(Type.MINE_ORE, 80, 350, 60);
        d(Type.SELL_ITEMS, 300, 250, 40);
        d(Type.OPEN_DROP, 2, 300, 50);
        d(Type.PLAY_MINUTES, 30, 150, 30);
        d(Type.BOSS_DAMAGE, 100, 250, 50);
        d(Type.BM_BUY, 1, 200, 40);
        d(Type.OPEN_CASE, 1, 150, 30);
        w(Type.KILL_MOBS, 300, 1500, 150);
        w(Type.MINE_ORE, 600, 1800, 150);
        w(Type.BOSS_KILL, 3, 2500, 200);
        w(Type.OPEN_DROP, 15, 2000, 150);
        w(Type.SELL_ITEMS, 3000, 2000, 150);
        w(Type.PLAY_MINUTES, 300, 1500, 150);
        w(Type.KILL_PLAYERS, 5, 2500, 200);
    }

    private void d(Type t, int target, long coins, int xp) {
        Def def = new Def("d" + dailyPool.size(), t, target, coins, xp, false);
        dailyPool.add(def);
        defs.put(def.id(), def);
    }

    private void w(Type t, int target, long coins, int xp) {
        Def def = new Def("w" + weeklyPool.size(), t, target, coins, xp, true);
        weeklyPool.add(def);
        defs.put(def.id(), def);
    }

    // ---------- время ----------

    private long offsetMs() { return plugin.getConfig().getLong("quests.timezone-offset-hours", 3) * 3_600_000L; }

    private long dayKey() { return Math.floorDiv(System.currentTimeMillis() + offsetMs(), 86_400_000L); }

    private long weekKey() { return Math.floorDiv(dayKey() + 3, 7); }

    // ---------- данные ----------

    private File file() { return new File(plugin.getDataFolder(), "quests.yml"); }

    public void start() {
        if (file().exists()) {
            ConfigurationSection s = YamlConfiguration.loadConfiguration(file()).getConfigurationSection("players");
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(k);
                        Data d = new Data();
                        d.dayKey = s.getLong(k + ".dkey", -1);
                        d.weekKey = s.getLong(k + ".wkey", -1);
                        d.daily = parse(s.getStringList(k + ".daily"));
                        d.weekly = parse(s.getStringList(k + ".weekly"));
                        data.put(id, d);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        saveTask = Bukkit.getScheduler().runTaskTimer(plugin, this::save, 6000L, 6000L);
    }

    public void shutdown() {
        if (saveTask != null) saveTask.cancel();
        save();
    }

    private List<Active> parse(List<String> raw) {
        List<Active> list = new ArrayList<>();
        for (String r : raw) {
            String[] p = r.split(":");
            if (p.length < 3 || !defs.containsKey(p[0])) continue;
            Active a = new Active(p[0]);
            try {
                a.progress = Integer.parseInt(p[1]);
            } catch (NumberFormatException ex) {
                continue;
            }
            a.done = p[2].equals("1");
            list.add(a);
        }
        return list;
    }

    private List<String> ser(List<Active> list) {
        List<String> res = new ArrayList<>();
        for (Active a : list) res.add(a.id + ":" + a.progress + ":" + (a.done ? "1" : "0"));
        return res;
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Data> e : data.entrySet()) {
            String k = "players." + e.getKey();
            y.set(k + ".dkey", e.getValue().dayKey);
            y.set(k + ".wkey", e.getValue().weekKey);
            y.set(k + ".daily", ser(e.getValue().daily));
            y.set(k + ".weekly", ser(e.getValue().weekly));
        }
        try {
            y.save(file());
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить quests.yml: " + ex.getMessage());
        }
    }

    private List<Active> roll(List<Def> pool, int count) {
        List<Def> copy = new ArrayList<>(pool);
        Collections.shuffle(copy, random);
        Set<Type> used = new HashSet<>();
        List<Active> res = new ArrayList<>();
        for (Def def : copy) {
            if (res.size() >= count) break;
            if (!used.add(def.type())) continue;
            res.add(new Active(def.id()));
        }
        return res;
    }

    private Data data(UUID id) {
        Data d = data.computeIfAbsent(id, k -> new Data());
        if (d.dayKey != dayKey()) {
            d.dayKey = dayKey();
            d.daily = roll(dailyPool, 3);
        }
        if (d.weekKey != weekKey()) {
            d.weekKey = weekKey();
            d.weekly = roll(weeklyPool, 2);
        }
        return d;
    }

    // ---------- прогресс ----------

    public void progress(Player p, Type type, int amount) {
        if (amount <= 0) return;
        Data d = data(p.getUniqueId());
        List<Active> all = new ArrayList<>(d.daily);
        all.addAll(d.weekly);
        for (Active a : all) {
            Def def = defs.get(a.id);
            if (def == null || a.done || def.type() != type) continue;
            a.progress = Math.min(def.target(), a.progress + amount);
            if (a.progress >= def.target()) {
                a.done = true;
                complete(p, def);
            } else {
                p.sendActionBar(U.c("&7Задание: &f" + def.name() + " &8[&a" + a.progress + "&8/&a" + def.target() + "&8]"));
            }
        }
    }

    private void complete(Player p, Def def) {
        double mul = plugin.getConfig().getDouble("quests.coin-multiplier", 1.0);
        long coins = Math.round(def.coins() * mul);
        plugin.getEconomy().addCoins(p.getUniqueId(), coins);
        plugin.season().addXp(p, def.xp());
        U.msg(p, "&6&l[ЗАДАНИЕ] &fВыполнено: &a" + def.name() + "&f! &e+" + U.money(coins) + " монеток&f, &b+" + def.xp() + " XP пропуска&f.");
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }

    // ---------- меню ----------

    private String bar(int progress, int target) {
        int filled = Math.min(10, (int) Math.floor(10.0 * progress / Math.max(1, target)));
        return "&a" + "▮".repeat(filled) + "&7" + "▮".repeat(10 - filled);
    }

    private String resetIn(boolean weekly) {
        long now = System.currentTimeMillis() + offsetMs();
        long dayMs = 86_400_000L;
        long left = dayMs - Math.floorMod(now, dayMs);
        if (weekly) {
            long daysToMonday = 6 - Math.floorMod(Math.floorDiv(now, dayMs) + 3, 7);
            left += daysToMonday * dayMs;
        }
        long h = left / 3_600_000L;
        long m = (left / 60_000L) % 60;
        return h >= 24 ? (h / 24) + " д. " + (h % 24) + " ч." : h + " ч. " + m + " мин.";
    }

    public void open(Player p) {
        Data d = data(p.getUniqueId());
        Gui g = new Gui(4, "&6&lЗадания").fill(Material.BLACK_STAINED_GLASS_PANE);
        g.set(4, U.item(Material.CLOCK, "&e&lЕжедневные задания", "&7Обновятся через: &f" + resetIn(false),
                "&7Награда: монетки и XP сезонного пропуска."));
        int[] dailySlots = {10, 12, 14};
        for (int i = 0; i < d.daily.size() && i < 3; i++) g.set(dailySlots[i], questItem(d.daily.get(i)));
        g.set(22, U.item(Material.CLOCK, "&b&lНедельные задания", "&7Обновятся через: &f" + resetIn(true)));
        int[] weeklySlots = {20, 24};
        for (int i = 0; i < d.weekly.size() && i < 2; i++) g.set(weeklySlots[i], questItem(d.weekly.get(i)));
        g.set(27, U.item(Material.ARROW, "&cНазад"), e -> plugin.menus().openHub(p));
        g.set(35, U.item(Material.EXPERIENCE_BOTTLE, "&d&lСезонный пропуск", "&7Нажми, чтобы открыть."),
                e -> plugin.season().open(p, 0));
        g.open(p);
    }

    private org.bukkit.inventory.ItemStack questItem(Active a) {
        Def def = defs.get(a.id);
        String name = (a.done ? "&a✔ " : "&e") + def.name();
        return U.item(a.done ? Material.ENCHANTED_BOOK : Material.BOOK, name,
                "&7Прогресс: " + bar(a.progress, def.target()) + " &f" + a.progress + "/" + def.target(), "",
                "&7Награда: &e" + U.money(Math.round(def.coins() * plugin.getConfig().getDouble("quests.coin-multiplier", 1.0)))
                        + " монеток", "&7+ &b" + def.xp() + " XP пропуска", "", a.done ? "&aВыполнено!" : "&8В процессе...");
    }

    // ---------- события ----------

    @EventHandler(ignoreCancelled = true)
    public void onMobDeath(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && e.getEntity() instanceof Enemy) progress(killer, Type.KILL_MOBS, 1);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && !killer.equals(e.getEntity())) progress(killer, Type.KILL_PLAYERS, 1);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Block b = e.getBlock();
        String n = b.getType().name();
        if (n.endsWith("_ORE") || b.getType() == Material.ANCIENT_DEBRIS) progress(e.getPlayer(), Type.MINE_ORE, 1);
    }
}
