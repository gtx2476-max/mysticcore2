package ru.mysticcore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

/**
 * Живая экономика + Скупщик. Цены на ресурсы плавают (спрос/предложение): продажа роняет цену,
 * со временем цена возвращается к норме, раз в 15 минут — рыночное событие. Платит монетками MysticCore.
 */
public final class Exchange {

    private static final class Sale {
        long coins;
        int items;
        final Map<Material, Integer> counts = new LinkedHashMap<>();
    }

    private final MysticCore plugin;
    private final Random random = new Random();
    private final Map<Material, Double> base = new LinkedHashMap<>();
    private final Map<Material, Double> mult = new HashMap<>();
    private final Map<Material, Double> trend = new HashMap<>();
    private BukkitTask task;
    private int minutes = 0;

    public Exchange(MysticCore plugin) {
        this.plugin = plugin;
    }

    private File file() { return new File(plugin.getDataFolder(), "exchange.yml"); }

    private double cfg(String path, double def) { return plugin.getConfig().getDouble("exchange." + path, def); }

    public void load() {
        base.clear();
        ConfigurationSection s = plugin.getConfig().getConfigurationSection("exchange.prices");
        if (s != null) {
            for (String k : s.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null && m.isItem()) base.put(m, s.getDouble(k));
            }
        }
        YamlConfiguration y = file().exists() ? YamlConfiguration.loadConfiguration(file()) : new YamlConfiguration();
        for (Material m : base.keySet()) {
            mult.put(m, y.getDouble("mult." + m.name(), 1.0));
            trend.put(m, 0.0);
        }
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::minuteTick, 1200L, 1200L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        save();
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<Material, Double> e : mult.entrySet()) y.set("mult." + e.getKey().name(), e.getValue());
        try {
            y.save(file());
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить exchange.yml: " + ex.getMessage());
        }
    }

    // ---------- динамика цен ----------

    private void minuteTick() {
        minutes++;
        double min = cfg("min-multiplier", 0.4);
        double max = cfg("max-multiplier", 2.2);
        for (Material m : base.keySet()) {
            double old = mult.getOrDefault(m, 1.0);
            double now = old + (1.0 - old) * 0.04 + (random.nextDouble() - 0.5) * 0.04;
            now = Math.max(min, Math.min(max, now));
            mult.put(m, now);
            trend.put(m, now - old);
        }
        if (minutes % 15 == 0 && !base.isEmpty()) marketEvent(min, max);
        if (minutes % 5 == 0) save();
    }

    private void marketEvent(double min, double max) {
        List<Material> list = new ArrayList<>(base.keySet());
        Material m = list.get(random.nextInt(list.size()));
        boolean up = random.nextInt(100) < 60;
        double old = mult.getOrDefault(m, 1.0);
        double now = Math.max(min, Math.min(max, old * (up ? 1.6 : 0.6)));
        mult.put(m, now);
        trend.put(m, now - old);
        Component msg = U.c("&6&l[БИРЖА] ").append(U.c(up ? "&fСкупщик ищет " : "&fРынок перенасыщен: "))
                .append(U.mat(m).color(net.kyori.adventure.text.format.NamedTextColor.YELLOW))
                .append(U.c(up ? "&f! Цена выросла до &e" + fmt(unit(m)) + " &fмонетки за штуку."
                        : "&f. Цена упала до &e" + fmt(unit(m)) + " &fмонетки за штуку."));
        Bukkit.broadcast(msg);
    }

    // ---------- цены ----------

    public boolean tradeable(ItemStack it) {
        return it != null && it.getType() != Material.AIR && base.containsKey(it.getType()) && !it.hasItemMeta();
    }

    public double unit(Material m) {
        return base.getOrDefault(m, 0.0) * mult.getOrDefault(m, 1.0);
    }

    private String fmt(double v) {
        return v >= 100 ? String.valueOf(Math.round(v)) : String.format(java.util.Locale.US, "%.1f", v);
    }

    public long estimate(Player p) {
        double sum = 0;
        for (ItemStack it : p.getInventory().getStorageContents()) {
            if (tradeable(it)) sum += unit(it.getType()) * it.getAmount();
        }
        return Math.round(sum * (1 - cfg("fee-percent", 0) / 100.0));
    }

    // ---------- продажа ----------

    private Sale sell(Player p, Predicate<ItemStack> filter, boolean onlyHand) {
        Sale sale = new Sale();
        PlayerInventory inv = p.getInventory();
        ItemStack[] cont = inv.getStorageContents();
        int held = inv.getHeldItemSlot();
        double impact = cfg("impact-per-item", 0.0015);
        double min = cfg("min-multiplier", 0.4);
        double revenue = 0;
        for (int i = 0; i < cont.length; i++) {
            if (onlyHand && i != held) continue;
            ItemStack it = cont[i];
            if (!tradeable(it) || !filter.test(it)) continue;
            Material m = it.getType();
            int n = it.getAmount();
            double mu = mult.getOrDefault(m, 1.0);
            double avg = Math.max(0.3, mu - impact * (n - 1) / 2.0);
            revenue += base.get(m) * avg * n;
            double nowMu = Math.max(min, mu - impact * n);
            mult.put(m, nowMu);
            trend.put(m, nowMu - mu);
            sale.items += n;
            sale.counts.merge(m, n, Integer::sum);
            cont[i] = null;
        }
        inv.setStorageContents(cont);
        revenue *= (1 - cfg("fee-percent", 0) / 100.0);
        sale.coins = Math.round(revenue);
        if (sale.items > 0) {
            plugin.getEconomy().addCoins(p.getUniqueId(), sale.coins);
            plugin.quests().progress(p, QuestManager.Type.SELL_ITEMS, sale.items);
        }
        return sale;
    }

    private void report(Player p, Sale sale) {
        if (sale.items == 0) {
            U.msg(p, "&6&l[СКУПЩИК] &cНечего продавать: нужны обычные ресурсы (без названий и чар).");
            return;
        }
        U.msg(p, "&6&l[СКУПЩИК] &fПродано &e" + sale.items + " &fпредметов за &e" + U.money(sale.coins)
                + " &fмонеток. Баланс: &e" + U.money(plugin.getEconomy().coins(p.getUniqueId())));
        List<Map.Entry<Material, Integer>> list = new ArrayList<>(sale.counts.entrySet());
        list.sort(Comparator.comparingInt((Map.Entry<Material, Integer> e) -> e.getValue()).reversed());
        for (int i = 0; i < Math.min(4, list.size()); i++) {
            p.sendMessage(U.c("&7  ▸ ").append(U.mat(list.get(i).getKey())).append(U.c(" &fx" + list.get(i).getValue())));
        }
        p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
    }

    public void sellAll(Player p) { report(p, sell(p, it -> true, false)); }

    public void sellHand(Player p) { report(p, sell(p, it -> true, true)); }

    public void sellMaterial(Player p, Material m) { report(p, sell(p, it -> it.getType() == m, false)); }

    // ---------- меню ----------

    public void openBuyer(Player p) {
        Gui g = new Gui(4, "&6&lСкупщик").fill(Material.BLACK_STAINED_GLASS_PANE);
        long est = estimate(p);
        g.set(11, U.item(Material.GOLD_BLOCK, "&e&lПродать всё",
                "&7Продаёт все ресурсы из инвентаря", "&7по текущим ценам биржи.", "",
                "&7Примерно: &e" + U.money(est) + " монеток", "", "&aНажми, чтобы продать"),
                e -> { sellAll(p); openBuyer(p); });
        g.set(13, U.item(Material.HOPPER, "&e&lПродать предмет в руке",
                "&7Продаёт стопку из основной руки.", "", "&aНажми, чтобы продать"),
                e -> { sellHand(p); openBuyer(p); });
        g.set(15, U.item(Material.WRITTEN_BOOK, "&b&lТаблица цен (Биржа)",
                "&7Текущие цены, спрос и тренды.", "&7Клик по предмету — продать такие.", "", "&aНажми, чтобы открыть"),
                e -> openBoard(p, 0));
        g.set(31, U.item(Material.GOLD_NUGGET, "&6Ваши монетки: &e" + U.money(plugin.getEconomy().coins(p.getUniqueId())),
                "&7Цена падает, когда продают много —", "&7и восстанавливается со временем."));
        g.open(p);
    }

    public void openBoard(Player p, int page) {
        List<Material> sorted = new ArrayList<>(base.keySet());
        sorted.sort(Comparator.comparingDouble((Material m) -> -unit(m)));
        int perPage = 36;
        int pages = Math.max(1, (sorted.size() + perPage - 1) / perPage);
        int pg = Math.max(0, Math.min(pages - 1, page));
        Gui g = new Gui(6, "&b&lБиржа — страница " + (pg + 1) + "/" + pages).fill(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < perPage; i++) g.set(i, null);
        for (int i = 0; i < perPage; i++) {
            int idx = pg * perPage + i;
            if (idx >= sorted.size()) break;
            final Material m = sorted.get(idx);
            double mu = mult.getOrDefault(m, 1.0);
            double tr = trend.getOrDefault(m, 0.0);
            String arrow = tr > 0.002 ? "&a▲" : (tr < -0.002 ? "&c▼" : "&7■");
            String demand = mu >= 1.3 ? "&aВысокий спрос" : (mu <= 0.8 ? "&cНизкий спрос" : "&7Обычный спрос");
            ItemStack shown = U.withLore(new ItemStack(m), "", "&7Цена за 1 шт: &e" + fmt(unit(m)) + " монеток " + arrow,
                    "&7Спрос: " + demand + " &8(" + Math.round(mu * 100) + "%)", "", "&aНажми — продать все такие");
            g.set(i, shown, e -> { sellMaterial(p, m); openBoard(p, pg); });
        }
        if (pg > 0) g.set(45, U.item(Material.ARROW, "&e← Назад"), e -> openBoard(p, pg - 1));
        if (pg < pages - 1) g.set(53, U.item(Material.ARROW, "&eВперёд →"), e -> openBoard(p, pg + 1));
        g.set(49, U.item(Material.BARRIER, "&cНазад к скупщику"), e -> openBuyer(p));
        g.open(p);
    }
}
