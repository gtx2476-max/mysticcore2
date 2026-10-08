package ru.mysticcore;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import ru.mysticcore.spheres.SphereType;

import java.util.ArrayList;
import java.util.List;

/** Все меню: главное, магазин сундуков (токены), Донат маркет (токены), магазин (монетки), режимы, обмен фугу. */
public final class Menus {

    public record Product(ItemStack item, long price, boolean tokens) {}

    private final MysticCore plugin;

    public Menus(MysticCore plugin) {
        this.plugin = plugin;
    }

    private String bal(Player p) {
        Economy ec = plugin.getEconomy();
        return "&7Токены: &d" + U.money(ec.tokens(p.getUniqueId())) + " &8| &7Монетки: &e" + U.money(ec.coins(p.getUniqueId()));
    }

    private Gui base(int rows, String title) {
        return new Gui(rows, title).fill(Material.BLACK_STAINED_GLASS_PANE);
    }

    // ================= главное меню =================
    public void openHub(Player p) {
        Gui g = base(5, "&8Меню сервера");
        g.set(10, U.item(Material.ENDER_CHEST, "&6&lМистические сундуки",
                "&7Вызови сундук с лутом за токены.", "", "&eНажми, чтобы открыть"), e -> openChestShop(p));
        g.set(12, U.item(Material.NETHER_STAR, "&d&l✦ Донат маркет",
                "&7Премиум аирдропы и сферы за токены.", "&7Только высокие цены.", "", "&eНажми, чтобы открыть"), e -> openDonateMain(p));
        g.set(14, U.item(Material.PLAYER_HEAD, "&b&lСферы",
                "&7Рецепты крафта всех сфер.", "", "&eНажми, чтобы открыть"), e -> plugin.getSphereMenu().openMain(p));
        g.set(16, U.item(Material.EMERALD, "&a&lМагазин (монетки)",
                "&7Сферы, талисманы, кейсы, расходники.", "", "&eНажми, чтобы открыть"), e -> openShopMain(p));
        g.set(28, U.item(Material.GOLD_INGOT, "&e&lАукцион",
                "&7Покупай и продавай предметы игроков.", "&7/ah sell <цена> — выставить лот.", "", "&eНажми, чтобы открыть"),
                e -> plugin.auction().open(p));
        g.set(30, U.item(Material.WITHER_SKELETON_SKULL, "&8&lЧёрный рынок",
                "&7Подпольный торговец: редкие вещи,", "&7цены меняются каждые 30 секунд.", "", "&eНажми — координаты и статус"),
                e -> { p.closeInventory(); plugin.blackMarket().info(p); });
        g.set(32, U.item(Material.DIAMOND_PICKAXE, "&b&lАвтошахта",
                "&7Руда обновляется каждые 3 минуты.", "", "&eНажми, чтобы телепортироваться"),
                e -> { p.closeInventory(); plugin.lobby().teleportMine(p); });
        g.set(34, U.item(Material.BEACON, "&f&lЛобби", "&7Вернуться в лобби.", "", "&eНажми, чтобы телепортироваться"),
                e -> { p.closeInventory(); plugin.lobby().teleportLobby(p); });
        g.set(22, U.item(Material.GRASS_BLOCK, "&a&lСлучайная телепортация (RTP)",
                "&7Телепорт в случайную точку карты.", "", "&eНажми"),
                e -> { p.closeInventory(); plugin.getRtp().teleport(p, false); });
        g.set(19, U.item(Material.BOOK, "&e&lЗадания", "&7Ежедневные и недельные задания", "&7за монетки и XP пропуска.", "", "&eНажми, чтобы открыть"),
                e -> plugin.quests().open(p));
        g.set(21, U.item(Material.EXPERIENCE_BOTTLE, "&d&lСезонный пропуск", "&750 уровней наград.", "", "&eНажми, чтобы открыть"),
                e -> plugin.season().open(p, 0));
        g.set(23, U.item(Material.EMERALD_BLOCK, "&6&lСкупщик", "&7Продай ресурсы за монетки.", "&7Цены меняются в зависимости от спроса.", "", "&eНажми, чтобы открыть"),
                e -> plugin.exchange().openBuyer(p));
        g.set(25, U.item(Material.WRITTEN_BOOK, "&b&lБиржа", "&7Таблица цен, спрос и тренды.", "", "&eНажми, чтобы открыть"),
                e -> plugin.exchange().openBoard(p, 0));
        g.set(40, U.item(Material.GOLD_NUGGET, "&6Ваш баланс", bal(p)));
        g.open(p);
    }

    // ================= сундуки за токены =================
    public void openChestShop(Player p) {
        Gui g = base(3, "&8Мистические сундуки");
        int slot = 11;
        for (Tier t : plugin.drops().tiers()) {
            final Tier tier = t;
            g.set(slot++, U.item(t.icon(), t.name(), t.desc(), "", "&7Цена: &d" + U.money(t.price()) + " токенов",
                    "&7Откроется через: &f" + U.time(t.unlockSeconds()), "", bal(p), "", "&eНажми, чтобы вызвать"),
                    e -> { buyChest(p, tier, t.price()); openChestShop(p); });
        }
        g.set(22, U.item(Material.ARROW, "&cНазад"), e -> openHub(p));
        g.open(p);
    }

    private void buyChest(Player p, Tier tier, long price) {
        Economy ec = plugin.getEconomy();
        if (ec.tokens(p.getUniqueId()) < price) {
            U.msg(p, "&cНедостаточно токенов! Нужно &d" + U.money(price) + "&c, у вас &d" + U.money(ec.tokens(p.getUniqueId())));
            return;
        }
        Drop d = plugin.drops().spawnRandom(tier);
        if (d == null) {
            U.msg(p, "&cНе удалось найти место для сундука. Токены не списаны.");
            return;
        }
        ec.takeTokens(p.getUniqueId(), price);
        U.msg(p, "&aВы вызвали " + tier.name() + "&a! Сундук появился в случайной точке мира.");
    }

    // ================= Донат маркет (токены, высокие цены) =================
    public void openDonateMain(Player p) {
        Gui g = base(3, "&6&l✦ Донат маркет ✦");
        g.set(11, U.item(Material.NETHER_STAR, "&6&lПремиум аирдропы",
                "&7Вызови сундук нужного уровня", "&7в случайной точке мира.", "", "&eНажми, чтобы открыть"), e -> openDonateAirdrops(p));
        g.set(15, U.item(Material.PLAYER_HEAD, "&b&lСферы",
                "&7Готовые сферы без крафта.", "", "&eНажми, чтобы открыть"), e -> openDonateSpheres(p));
        g.set(4, U.item(Material.GOLD_NUGGET, "&6Ваш баланс", bal(p)));
        g.set(22, U.item(Material.ARROW, "&cНазад"), e -> openHub(p));
        g.open(p);
    }

    public void openDonateAirdrops(Player p) {
        Gui g = base(3, "&6&lДонат: аирдропы");
        int slot = 11;
        for (Tier t : plugin.drops().tiers()) {
            final Tier tier = t;
            long price = plugin.getConfig().getLong("market.airdrop-prices." + t.key(), t.price() * 50);
            g.set(slot++, U.item(t.icon(), t.name(), t.desc(), "", "&7Цена: &d" + U.money(price) + " токенов",
                    "&7Откроется через: &f" + U.time(t.unlockSeconds()), "", bal(p), "", "&eНажми, чтобы купить"),
                    e -> { buyChest(p, tier, price); openDonateAirdrops(p); });
        }
        g.set(22, U.item(Material.ARROW, "&cНазад"), e -> openDonateMain(p));
        g.open(p);
    }

    public void openDonateSpheres(Player p) {
        List<Product> list = new ArrayList<>();
        for (SphereType t : SphereType.values()) {
            list.add(new Product(plugin.getSphereItems().create(t), plugin.sphereTokenPrice(t), true));
        }
        openProducts(p, "&6&lДонат: сферы", list, () -> openDonateMain(p));
    }

    // ================= магазин за монетки =================
    public void openShopMain(Player p) {
        Gui g = base(3, "&a&lМагазин (монетки)");
        g.set(10, U.item(Material.PLAYER_HEAD, "&b&lСферы", "&7Все сферы за монетки.", "", "&eНажми"),
                e -> {
                    List<Product> l = new ArrayList<>();
                    for (SphereType t : SphereType.values()) l.add(new Product(plugin.getSphereItems().create(t), plugin.sphereCoinPrice(t), false));
                    openProducts(p, "&b&lСферы", l, () -> openShopMain(p));
                });
        g.set(12, U.item(Material.HEART_OF_THE_SEA, "&d&lТалисманы", "&7Бонусы, пока лежат в инвентаре.", "", "&eНажми"),
                e -> openCatalog(p, Catalog.Cat.TALISMAN, "&d&lТалисманы"));
        g.set(14, U.item(Material.ENDER_CHEST, "&6&lКейсы", "&7Открывай и получай призы.", "", "&eНажми"),
                e -> openCatalog(p, Catalog.Cat.CASE, "&6&lКейсы"));
        g.set(16, U.item(Material.RED_DYE, "&c&lРасходники", "&7Эликсиры и свитки.", "", "&eНажми"),
                e -> openCatalog(p, Catalog.Cat.CONSUMABLE, "&c&lРасходники"));
        g.set(4, U.item(Material.GOLD_NUGGET, "&6Ваш баланс", bal(p)));
        g.set(22, U.item(Material.ARROW, "&cНазад"), e -> openHub(p));
        g.open(p);
    }

    private void openCatalog(Player p, Catalog.Cat cat, String title) {
        List<Product> l = new ArrayList<>();
        for (Catalog.Entry en : plugin.getCatalog().byCat(cat)) {
            l.add(new Product(plugin.getCatalog().create(en.id()), en.price(), false));
        }
        openProducts(p, title, l, () -> openShopMain(p));
    }

    /** Универсальная витрина товаров (токены или монетки). */
    public void openProducts(Player p, String title, List<Product> products, Runnable back) {
        Gui g = base(5, title);
        int idx = 0;
        for (int r = 1; r <= 3 && idx < products.size(); r++) {
            for (int c = 1; c <= 7 && idx < products.size(); c++) {
                final Product pr = products.get(idx++);
                String cur = pr.tokens() ? "&dтокенов" : "&eмонеток";
                ItemStack shown = U.withLore(pr.item(), "", "&7Цена: " + (pr.tokens() ? "&d" : "&e") + U.money(pr.price()) + " " + cur.substring(2),
                        "", bal(p), "", "&aНажми, чтобы купить");
                g.set(r * 9 + c, shown, e -> {
                    Economy ec = plugin.getEconomy();
                    boolean ok = pr.tokens() ? ec.takeTokens(p.getUniqueId(), pr.price()) : ec.takeCoins(p.getUniqueId(), pr.price());
                    if (!ok) {
                        U.msg(p, "&cНедостаточно " + (pr.tokens() ? "токенов" : "монеток") + "! Нужно &f" + U.money(pr.price()));
                        return;
                    }
                    U.give(p, pr.item().clone());
                    U.msg(p, "&aПокупка совершена за &f" + U.money(pr.price()) + "&a. " + bal(p));
                    openProducts(p, title, products, back);
                });
            }
        }
        g.set(40, U.item(Material.ARROW, "&cНазад"), e -> back.run());
        g.open(p);
    }

    // ================= лобби: режимы и обмен =================
    public void openModes(Player p) {
        Gui g = base(3, "&8Выбор режима");
        g.set(11, U.item(Material.GRASS_BLOCK, "&a&lВыживание",
                "&7Основной мир, мистические сундуки,", "&7Мама Фугу, Чёрный рынок и RTP.", "", "&aНажми, чтобы играть"),
                e -> { p.closeInventory(); plugin.lobby().sendToSurvival(p); });
        g.set(13, U.item(Material.IRON_SWORD, "&7Скоро...", "&8Этот режим ещё в разработке."));
        g.set(15, U.item(Material.ELYTRA, "&7Скоро...", "&8Этот режим ещё в разработке."));
        g.open(p);
    }

    public void openExchange(Player p) {
        long per = plugin.getConfig().getLong("trade.tokens-per-five", 250);
        Gui g = base(3, "&2Фугафаг: обмен");
        g.set(13, U.item(Material.PUFFERFISH, "&a&lСдать 5 фугу",
                "&7Получите &d" + per + " токенов &7за каждые 5 штук.", "&7Фугу падает с Мамы Фугу.", "", "&eНажми, чтобы обменять"),
                e -> plugin.boss().trade(p));
        g.set(22, U.item(Material.GOLD_NUGGET, "&6Ваш баланс", bal(p)));
        g.open(p);
    }
}
