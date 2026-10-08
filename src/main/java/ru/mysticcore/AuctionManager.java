package ru.mysticcore;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Аукцион за монетки. /ah — открыть, /ah sell <цена> — выставить предмет из руки. ПКМ по своему лоту — снять. */
public final class AuctionManager {

    private static final class Lot {
        final UUID seller;
        final String sellerName;
        final long price;
        final ItemStack item;

        Lot(UUID seller, String sellerName, long price, ItemStack item) {
            this.seller = seller;
            this.sellerName = sellerName;
            this.price = price;
            this.item = item;
        }
    }

    private final MysticCore plugin;
    private final List<Lot> lots = new ArrayList<>();

    public AuctionManager(MysticCore plugin) {
        this.plugin = plugin;
    }

    private File file() { return new File(plugin.getDataFolder(), "auctions_core.yml"); }

    public void load() {
        if (!file().exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(file());
        ConfigurationSection s = y.getConfigurationSection("lots");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            ConfigurationSection l = s.getConfigurationSection(k);
            if (l == null) continue;
            ItemStack it = l.getItemStack("item");
            String seller = l.getString("seller");
            if (it == null || seller == null) continue;
            try {
                lots.add(new Lot(UUID.fromString(seller), l.getString("seller-name", "?"), l.getLong("price"), it));
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (int i = 0; i < lots.size(); i++) {
            Lot l = lots.get(i);
            y.set("lots." + i + ".item", l.item);
            y.set("lots." + i + ".seller", l.seller.toString());
            y.set("lots." + i + ".seller-name", l.sellerName);
            y.set("lots." + i + ".price", l.price);
        }
        try {
            y.save(file());
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить аукцион: " + ex.getMessage());
        }
    }

    public void sell(Player p, long price) {
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            U.msg(p, "&cВозьмите предмет в руку.");
            return;
        }
        if (price < 1 || price > 1_000_000_000L) {
            U.msg(p, "&cНекорректная цена.");
            return;
        }
        int max = plugin.getConfig().getInt("auction.max-lots-per-player", 5);
        long mine = lots.stream().filter(l -> l.seller.equals(p.getUniqueId())).count();
        if (mine >= max) {
            U.msg(p, "&cВы уже выставили максимум лотов (" + max + ").");
            return;
        }
        if (lots.size() >= 45) {
            U.msg(p, "&cАукцион переполнен.");
            return;
        }
        lots.add(new Lot(p.getUniqueId(), p.getName(), price, hand.clone()));
        p.getInventory().setItemInMainHand(null);
        save();
        U.msg(p, "&aЛот выставлен за &e" + U.money(price) + " &aмонеток.");
    }

    public void open(Player p) {
        Gui g = new Gui(6, "&8Аукцион").fill(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 45; i++) g.set(i, null);
        for (int i = 0; i < lots.size() && i < 45; i++) {
            final Lot lot = lots.get(i);
            boolean own = lot.seller.equals(p.getUniqueId());
            ItemStack shown = U.withLore(lot.item, "", "&7Продавец: &f" + lot.sellerName,
                    "&7Цена: &e" + U.money(lot.price) + " монеток", "",
                    own ? "&eПКМ — снять с продажи" : "&aЛКМ — купить");
            g.set(i, shown, e -> {
                boolean right = e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT;
                if (own && right) cancel(p, lot);
                else if (!own) buy(p, lot);
                else U.msg(p, "&cНельзя купить собственный лот. ПКМ — снять с продажи.");
                open(p);
            });
        }
        g.set(49, U.item(Material.GOLD_INGOT, "&6Ваши монетки: &e" + U.money(plugin.getEconomy().coins(p.getUniqueId())),
                "&7Выставить предмет: &f/ah sell <цена>"));
        g.set(53, U.item(Material.ARROW, "&cНазад"), e -> plugin.menus().openHub(p));
        g.open(p);
    }

    private void buy(Player p, Lot lot) {
        if (!lots.contains(lot)) {
            U.msg(p, "&cЛот уже продан.");
            return;
        }
        Economy ec = plugin.getEconomy();
        if (!ec.takeCoins(p.getUniqueId(), lot.price)) {
            U.msg(p, "&cНедостаточно монеток! Нужно &e" + U.money(lot.price));
            return;
        }
        lots.remove(lot);
        ec.addCoins(lot.seller, lot.price);
        U.give(p, lot.item.clone());
        save();
        U.msg(p, "&aВы купили лот за &e" + U.money(lot.price) + " &aмонеток.");
        Player seller = plugin.getServer().getPlayer(lot.seller);
        if (seller != null) U.msg(seller, "&aВаш лот купил &f" + p.getName() + "&a! +&e" + U.money(lot.price) + " &aмонеток.");
    }

    private void cancel(Player p, Lot lot) {
        if (!lots.remove(lot)) return;
        U.give(p, lot.item.clone());
        save();
        U.msg(p, "&7Лот снят с продажи.");
    }
}
