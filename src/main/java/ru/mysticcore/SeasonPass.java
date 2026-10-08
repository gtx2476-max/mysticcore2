package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import ru.mysticcore.spheres.SphereType;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Сезонный пропуск: 50 уровней, бесплатная и премиум ветки. XP — за задания, босса и онлайн. */
public final class SeasonPass {

    private static final int MAX_LEVEL = 50;

    private static final class PData {
        long xp;
        boolean premium;
        final Set<Integer> free = new HashSet<>();
        final Set<Integer> prem = new HashSet<>();
    }

    private record Reward(String desc, Material icon, Consumer<Player> give) {}

    private final MysticCore plugin;
    private final Random random = new Random();
    private final Map<UUID, PData> players = new HashMap<>();
    private int season = 1;
    private long startDay = -1;
    private BukkitTask task;

    public SeasonPass(MysticCore plugin) {
        this.plugin = plugin;
    }

    private File file() { return new File(plugin.getDataFolder(), "season.yml"); }

    private long today() {
        long off = plugin.getConfig().getLong("quests.timezone-offset-hours", 3) * 3_600_000L;
        return Math.floorDiv(System.currentTimeMillis() + off, 86_400_000L);
    }

    private int xpPerLevel() { return Math.max(10, plugin.getConfig().getInt("season.xp-per-level", 120)); }

    public int level(long xp) { return (int) Math.min(MAX_LEVEL, xp / xpPerLevel()); }

    // ---------- хранение ----------

    public void start() {
        if (file().exists()) {
            YamlConfiguration y = YamlConfiguration.loadConfiguration(file());
            season = y.getInt("season", 1);
            startDay = y.getLong("start", -1);
            ConfigurationSection s = y.getConfigurationSection("players");
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    try {
                        PData d = new PData();
                        d.xp = s.getLong(k + ".xp");
                        d.premium = s.getBoolean(k + ".premium");
                        d.free.addAll(s.getIntegerList(k + ".free"));
                        d.prem.addAll(s.getIntegerList(k + ".prem"));
                        players.put(UUID.fromString(k), d);
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        if (startDay < 0) startDay = today();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::checkSeason, 1200L, 1200L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        save();
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();
        y.set("season", season);
        y.set("start", startDay);
        for (Map.Entry<UUID, PData> e : players.entrySet()) {
            String k = "players." + e.getKey();
            y.set(k + ".xp", e.getValue().xp);
            y.set(k + ".premium", e.getValue().premium);
            y.set(k + ".free", new ArrayList<>(e.getValue().free));
            y.set(k + ".prem", new ArrayList<>(e.getValue().prem));
        }
        try {
            y.save(file());
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить season.yml: " + ex.getMessage());
        }
    }

    private void checkSeason() {
        int days = Math.max(1, plugin.getConfig().getInt("season.days", 30));
        if (today() >= startDay + days) {
            season++;
            startDay = today();
            players.clear();
            save();
            Bukkit.broadcast(U.c("&d&l[СЕЗОН] &fНачался сезон &e#" + season + "&f! Прогресс пропуска сброшен. Откройте &e/season"));
        }
    }

    private PData pd(UUID id) { return players.computeIfAbsent(id, k -> new PData()); }

    // ---------- XP ----------

    public void addXp(Player p, long amount) {
        if (amount <= 0) return;
        PData d = pd(p.getUniqueId());
        int old = level(d.xp);
        d.xp += amount;
        int now = level(d.xp);
        if (now > old) {
            U.msg(p, "&d&l[ПРОПУСК] &fНовый уровень сезонного пропуска: &e" + now + "&f! Откройте &e/season &fдля наград.");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        }
    }

    // ---------- награды ----------

    private void giveCatalog(Player p, String id, int n) {
        U.give(p, plugin.getCatalog().create(id, n));
    }

    private void giveTalisman(Player p) {
        var list = plugin.getCatalog().byCat(Catalog.Cat.TALISMAN);
        U.give(p, plugin.getCatalog().create(list.get(random.nextInt(list.size())).id()));
    }

    private void giveSphere(Player p, boolean allowEmpty) {
        SphereType[] all = SphereType.values();
        SphereType t;
        do {
            t = all[random.nextInt(all.length)];
        } while (!allowEmpty && t == SphereType.EMPTY);
        U.give(p, plugin.getSphereItems().create(t));
    }

    private Reward free(int L) {
        long coins = 50L + L * 10L;
        List<String> parts = new ArrayList<>();
        List<Consumer<Player>> acts = new ArrayList<>();
        Material icon = Material.GOLD_NUGGET;
        parts.add("&e" + coins + " монеток");
        acts.add(p -> plugin.getEconomy().addCoins(p.getUniqueId(), coins));
        if (L == MAX_LEVEL) {
            parts.add("&fПустая сфера");
            acts.add(p -> U.give(p, plugin.getSphereItems().create(SphereType.EMPTY)));
            parts.add("&e3000 монеток");
            acts.add(p -> plugin.getEconomy().addCoins(p.getUniqueId(), 3000));
            icon = Material.PLAYER_HEAD;
        } else if (L == 25) {
            parts.add("&fСлучайный талисман");
            acts.add(this::giveTalisman);
            icon = Material.HEART_OF_THE_SEA;
        } else if (L % 10 == 0) {
            parts.add("&9Редкий кейс");
            acts.add(p -> giveCatalog(p, "case_rare", 1));
            icon = Material.ENDER_CHEST;
        } else if (L % 5 == 0) {
            parts.add("&7Обычный кейс");
            acts.add(p -> giveCatalog(p, "case_common", 1));
            icon = Material.BARREL;
        }
        return new Reward(String.join("&7 + ", parts), icon, p -> { for (Consumer<Player> a : acts) a.accept(p); });
    }

    private Reward premium(int L) {
        long tokens = 20L + L * 2L;
        List<String> parts = new ArrayList<>();
        List<Consumer<Player>> acts = new ArrayList<>();
        Material icon = Material.NETHER_STAR;
        parts.add("&d" + tokens + " токенов");
        acts.add(p -> plugin.getEconomy().addTokens(p.getUniqueId(), tokens));
        if (L == MAX_LEVEL) {
            parts.add("&6Мифический кейс x3");
            acts.add(p -> giveCatalog(p, "case_mythic", 3));
            parts.add("&6Уникальный предмет");
            acts.add(p -> {
                var list = plugin.getCatalog().byCat(Catalog.Cat.UNIQUE);
                U.give(p, plugin.getCatalog().create(list.get(random.nextInt(list.size())).id()));
            });
            icon = Material.NETHERITE_SWORD;
        } else if (L == 15 || L == 30 || L == 45) {
            parts.add("&bСлучайная сфера");
            acts.add(p -> giveSphere(p, false));
            icon = Material.PLAYER_HEAD;
        } else if (L % 10 == 0) {
            parts.add("&6Мифический кейс");
            acts.add(p -> giveCatalog(p, "case_mythic", 1));
            parts.add("&fСлучайный талисман");
            acts.add(this::giveTalisman);
            icon = Material.PURPLE_SHULKER_BOX;
        } else if (L % 5 == 0) {
            parts.add("&9Редкий кейс");
            acts.add(p -> giveCatalog(p, "case_rare", 1));
            icon = Material.ENDER_CHEST;
        }
        return new Reward(String.join("&7 + ", parts), icon, p -> { for (Consumer<Player> a : acts) a.accept(p); });
    }

    // ---------- меню ----------

    public void open(Player p, int page) {
        PData d = pd(p.getUniqueId());
        int lvl = level(d.xp);
        int pages = (MAX_LEVEL + 8) / 9;
        int pg = Math.max(0, Math.min(pages - 1, page));
        long perLevel = xpPerLevel();
        long into = d.xp - (long) lvl * perLevel;
        Gui g = new Gui(6, "&d&lСезонный пропуск #" + season).fill(Material.BLACK_STAINED_GLASS_PANE);

        g.set(0, U.item(Material.PAPER, "&a&lБесплатная ветка", "&7Награды для всех игроков."));
        g.set(18, U.item(Material.NETHER_STAR, "&6&lПремиум ветка", "&7Токены, кейсы, сферы и уникальные предметы."));

        if (d.premium) {
            g.set(4, U.item(Material.NETHER_STAR, "&6&lПремиум активен", "&7Спасибо за поддержку!"));
        } else {
            long price = plugin.getConfig().getLong("season.premium-price-tokens", 25000);
            g.set(4, U.item(Material.NETHER_STAR, "&6&lКупить Премиум пропуск",
                    "&7Цена: &d" + U.money(price) + " токенов", "&7Откроет премиум ветку.", "", "&aНажми, чтобы купить"),
                    e -> {
                        if (plugin.getEconomy().takeTokens(p.getUniqueId(), price)) {
                            pd(p.getUniqueId()).premium = true;
                            U.msg(p, "&6&l[ПРОПУСК] &aПремиум активирован!");
                        } else {
                            U.msg(p, "&cНедостаточно токенов! Нужно &d" + U.money(price));
                        }
                        open(p, pg);
                    });
        }

        for (int i = 0; i < 9; i++) {
            int L = pg * 9 + i + 1;
            if (L > MAX_LEVEL) break;
            // бесплатная ветка (ряд 1)
            Reward fr = free(L);
            boolean fDone = d.free.contains(L);
            boolean fCan = lvl >= L && !fDone;
            ItemStack fi;
            if (fDone) fi = U.item(Material.BLACK_STAINED_GLASS_PANE, "&8✔ Уровень " + L + " — получено");
            else if (fCan) fi = U.item(fr.icon(), "&a&lУровень " + L, "&7Награда: " + fr.desc(), "", "&eНажми, чтобы забрать");
            else fi = U.item(Material.RED_STAINED_GLASS_PANE, "&cУровень " + L, "&7Награда: " + fr.desc(), "&8Нужно достичь уровня " + L);
            fi.setAmount(fDone || fCan ? 1 : Math.max(1, Math.min(64, L)));
            final int fl = L;
            g.set(9 + i, fi, e -> {
                if (!fCan) return;
                PData cur = pd(p.getUniqueId());
                if (cur.free.add(fl)) {
                    fr.give().accept(p);
                    U.msg(p, "&a[ПРОПУСК] Награда уровня " + fl + " получена.");
                }
                open(p, pg);
            });
            // премиум ветка (ряд 3)
            Reward pr = premium(L);
            boolean pDone = d.prem.contains(L);
            boolean pCan = d.premium && lvl >= L && !pDone;
            ItemStack pi;
            if (pDone) pi = U.item(Material.BLACK_STAINED_GLASS_PANE, "&8✔ Премиум " + L + " — получено");
            else if (pCan) pi = U.item(pr.icon(), "&6&lПремиум " + L, "&7Награда: " + pr.desc(), "", "&eНажми, чтобы забрать");
            else if (!d.premium) pi = U.item(Material.PURPLE_STAINED_GLASS_PANE, "&5Премиум " + L, "&7Награда: " + pr.desc(), "&8Нужен премиум пропуск");
            else pi = U.item(Material.RED_STAINED_GLASS_PANE, "&cПремиум " + L, "&7Награда: " + pr.desc(), "&8Нужно достичь уровня " + L);
            pi.setAmount(pDone || pCan ? 1 : Math.max(1, Math.min(64, L)));
            g.set(27 + i, pi, e -> {
                if (!pCan) return;
                PData cur = pd(p.getUniqueId());
                if (cur.premium && cur.prem.add(fl)) {
                    pr.give().accept(p);
                    U.msg(p, "&6[ПРОПУСК] Премиум награда уровня " + fl + " получена.");
                }
                open(p, pg);
            });
        }

        int days = Math.max(1, plugin.getConfig().getInt("season.days", 30));
        long left = Math.max(0, startDay + days - today());
        g.set(49, U.item(Material.EXPERIENCE_BOTTLE, "&d&lУровень: &f" + lvl + "&7/" + MAX_LEVEL,
                lvl >= MAX_LEVEL ? "&aМаксимальный уровень!" : "&7XP до следующего: &f" + into + "&7/&f" + perLevel,
                "&7До конца сезона: &f" + left + " дн.", "", "&7XP даёт: задания, босс, время онлайн."));
        if (pg > 0) g.set(45, U.item(Material.ARROW, "&e← Назад"), e -> open(p, pg - 1));
        if (pg < pages - 1) g.set(53, U.item(Material.ARROW, "&eВперёд →"), e -> open(p, pg + 1));
        g.set(47, U.item(Material.BOOK, "&6Задания"), e -> plugin.quests().open(p));
        g.set(51, U.item(Material.BARRIER, "&cМеню"), e -> plugin.menus().openHub(p));
        g.open(p);
    }
}
