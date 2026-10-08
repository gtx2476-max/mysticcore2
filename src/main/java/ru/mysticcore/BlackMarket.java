package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Display;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;
import ru.mysticcore.spheres.SphereType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/**
 * Чёрный рынок: подпольный торговец появляется в случайной точке, координаты видят все.
 * Цены меняются каждые 30 сек, через 10 минут рынок закрывается. Оплата — монетки.
 */
public final class BlackMarket implements Listener {

    private static final class Offer {
        final ItemStack proto;
        final long base;
        double mult;
        double prev;
        int stock;

        Offer(ItemStack proto, long base, int stock, double mult) {
            this.proto = proto;
            this.base = base;
            this.stock = stock;
            this.mult = mult;
            this.prev = mult;
        }

        long price() { return Math.max(1, Math.round(base * mult)); }
    }

    private record Rare(Material m, int amount, long price) {}

    private static final List<Rare> RARES = List.of(
            new Rare(Material.TOTEM_OF_UNDYING, 1, 1500), new Rare(Material.ENCHANTED_GOLDEN_APPLE, 2, 3000),
            new Rare(Material.NETHERITE_INGOT, 1, 4500), new Rare(Material.ELYTRA, 1, 14000),
            new Rare(Material.NETHER_STAR, 1, 6000), new Rare(Material.SHULKER_SHELL, 2, 2500),
            new Rare(Material.DIAMOND_BLOCK, 3, 2800), new Rare(Material.EXPERIENCE_BOTTLE, 32, 1200),
            new Rare(Material.BEACON, 1, 7000), new Rare(Material.HEART_OF_THE_SEA, 1, 3500),
            new Rare(Material.ECHO_SHARD, 4, 2000), new Rare(Material.ANCIENT_DEBRIS, 6, 3000));

    private final MysticCore plugin;
    private final Random random = new Random();
    private final NamespacedKey dealerKey;
    private final Gui gui = new Gui(6, "&8&l\u2620 Чёрный рынок \u2620");
    private final List<Offer> offers = new ArrayList<>();

    private boolean open = false;
    private Location loc;
    private long closeAt;
    private Villager dealer;
    private TextDisplay holo;
    private BukkitTask task;
    private int secondCounter = 0;
    private int minutesToNext;

    public BlackMarket(MysticCore plugin) {
        this.plugin = plugin;
        this.dealerKey = new NamespacedKey(plugin, "bm_dealer");
    }

    public void start() {
        minutesToNext = Math.max(1, plugin.getConfig().getInt("black-market.interval-minutes", 40));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        if (open) close(false);
    }

    public boolean isOpen() { return open; }

    // ---------- открытие / закрытие ----------

    public boolean open(Location where) {
        if (open) return false;
        Location l = where != null ? where : plugin.drops().pickLocation();
        if (l == null) return false;
        loc = l.clone();
        World w = loc.getWorld();
        loc.getChunk().addPluginChunkTicket(plugin);
        buildOffers();
        closeAt = System.currentTimeMillis() + plugin.getConfig().getInt("black-market.duration-minutes", 10) * 60_000L;
        open = true;
        secondCounter = 0;

        dealer = w.spawn(loc.clone().add(0.5, 0, 0.5), Villager.class, v -> {
            v.setAI(false);
            v.setInvulnerable(true);
            v.setSilent(true);
            v.setPersistent(false);
            v.setRemoveWhenFarAway(false);
            v.setCollidable(false);
            v.setProfession(Villager.Profession.NITWIT);
            v.customName(U.c("&8&lТорговец-подпольщик"));
            v.setCustomNameVisible(true);
            v.getPersistentDataContainer().set(dealerKey, PersistentDataType.BYTE, (byte) 1);
        });
        holo = w.spawn(loc.clone().add(0.5, 2.9, 0.5), TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setPersistent(false);
            td.setShadowed(true);
            td.setViewRange(2.5f);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        });
        w.strikeLightningEffect(loc);
        w.playSound(loc, Sound.ENTITY_WITHER_SPAWN, 1.0f, 1.4f);
        rebuildGui();
        updateHolo();

        long mins = Math.max(1, (closeAt - System.currentTimeMillis()) / 60_000L);
        Bukkit.broadcast(U.c(""));
        Bukkit.broadcast(U.c("&8&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        Bukkit.broadcast(U.c("&8&l[ЧЁРНЫЙ РЫНОК] &fОткрылся подпольный рынок!"));
        Bukkit.broadcast(U.c("&fКоординаты: &aX: &e" + loc.getBlockX() + "&f, &aY: &e" + loc.getBlockY()
                + "&f, &aZ: &e" + loc.getBlockZ() + " &7(" + w.getName() + ")"));
        Bukkit.broadcast(U.c("&fРедкие предметы, сферы, талисманы, кейсы. Цены меняются каждые &e30 сек&f!"));
        Bukkit.broadcast(U.c("&fЗакроется через: &c" + mins + " мин. &7Команда: &f/bm"));
        Bukkit.broadcast(U.c("&8&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬"));
        Bukkit.broadcast(U.c(""));
        return true;
    }

    public void close(boolean announce) {
        if (!open) return;
        open = false;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() == gui) p.closeInventory();
        }
        if (dealer != null && dealer.isValid()) dealer.remove();
        if (holo != null && holo.isValid()) holo.remove();
        if (loc != null) loc.getChunk().removePluginChunkTicket(plugin);
        dealer = null;
        holo = null;
        if (announce) Bukkit.broadcast(U.c("&8&l[ЧЁРНЫЙ РЫНОК] &7Рынок закрылся. Торговец скрылся в тени..."));
    }

    // ---------- товары ----------

    private void buildOffers() {
        offers.clear();
        Catalog cat = plugin.getCatalog();

        List<Catalog.Entry> uniques = new ArrayList<>(cat.byCat(Catalog.Cat.UNIQUE));
        Collections.shuffle(uniques, random);
        for (int i = 0; i < Math.min(2, uniques.size()); i++) {
            Catalog.Entry e = uniques.get(i);
            add(cat.create(e.id()), e.price(), 1);
        }

        List<SphereType> spheres = new ArrayList<>(List.of(SphereType.values()));
        Collections.shuffle(spheres, random);
        for (int i = 0; i < 3; i++) {
            SphereType t = spheres.get(i);
            add(plugin.getSphereItems().create(t), plugin.sphereCoinPrice(t), 3);
        }

        addRandom(cat.byCat(Catalog.Cat.TALISMAN), 3, 3);
        addRandom(cat.byCat(Catalog.Cat.CASE), 3, 5);
        addRandom(cat.byCat(Catalog.Cat.CONSUMABLE), 3, 8);

        List<Rare> rares = new ArrayList<>(RARES);
        Collections.shuffle(rares, random);
        for (int i = 0; i < 4; i++) {
            Rare r = rares.get(i);
            add(new ItemStack(r.m(), r.amount()), r.price(), 4);
        }
    }

    private void addRandom(List<Catalog.Entry> pool, int n, int stock) {
        List<Catalog.Entry> list = new ArrayList<>(pool);
        Collections.shuffle(list, random);
        for (int i = 0; i < Math.min(n, list.size()); i++) {
            Catalog.Entry e = list.get(i);
            add(plugin.getCatalog().create(e.id()), e.price(), stock);
        }
    }

    private void add(ItemStack proto, long base, int stock) {
        offers.add(new Offer(proto, base, stock, 0.7 + random.nextDouble() * 0.8));
    }

    private void updatePrices() {
        for (Offer o : offers) {
            o.prev = o.mult;
            o.mult = Math.max(0.5, Math.min(2.5, o.mult * (0.75 + random.nextDouble() * 0.5)));
        }
        rebuildGui();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getOpenInventory().getTopInventory().getHolder() == gui) {
                p.sendActionBar(U.c("&e⟳ Цены на Чёрном рынке обновились!"));
            }
        }
    }

    private void rebuildGui() {
        gui.clear();
        gui.fill(Material.BLACK_STAINED_GLASS_PANE);
        int idx = 0;
        for (int r = 1; r <= 4 && idx < offers.size(); r++) {
            for (int c = 1; c <= 7 && idx < offers.size(); c++) {
                Offer o = offers.get(idx++);
                int slot = r * 9 + c;
                if (o.stock <= 0) {
                    gui.set(slot, U.item(Material.BARRIER, "&c&lРАСПРОДАНО", "&7Этот товар закончился."));
                    continue;
                }
                String arrow = o.mult > o.prev + 1e-9 ? " &c▲" : (o.mult < o.prev - 1e-9 ? " &a▼" : "");
                ItemStack shown = U.withLore(o.proto, "", "&7Цена: &e" + U.money(o.price()) + " монеток" + arrow,
                        "&7В наличии: &f" + o.stock, "", "&aНажми, чтобы купить");
                shown.setAmount(Math.max(1, o.proto.getAmount()));
                gui.set(slot, shown, e -> {
                    if (e.getWhoClicked() instanceof Player p) buy(p, o);
                });
            }
        }
        updateInfoSlot();
    }

    private void updateInfoSlot() {
        long left = Math.max(0, (closeAt - System.currentTimeMillis()) / 1000);
        gui.set(49, U.item(Material.CLOCK, "&e&lДо закрытия рынка: &f" + U.time(left),
                "&7Цены меняются каждые 30 секунд.", "&7Оплата — монетки."));
    }

    private void buy(Player p, Offer o) {
        if (!open) return;
        if (o.stock <= 0) {
            U.msg(p, "&cТовар распродан.");
            return;
        }
        long price = o.price();
        if (!plugin.getEconomy().takeCoins(p.getUniqueId(), price)) {
            U.msg(p, "&cНедостаточно монеток! Нужно &f" + U.money(price) + "&c, у вас &f"
                    + U.money(plugin.getEconomy().coins(p.getUniqueId())));
            return;
        }
        o.stock--;
        plugin.quests().progress(p, QuestManager.Type.BM_BUY, 1);
        U.give(p, o.proto.clone());
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
        U.msg(p, "&aВы купили товар за &e" + U.money(price) + " &aмонеток. Баланс: &e"
                + U.money(plugin.getEconomy().coins(p.getUniqueId())));
        rebuildGui();
    }

    // ---------- тик ----------

    private void tick() {
        if (!open) {
            if (Bukkit.getOnlinePlayers().isEmpty()) return;
            // раз в минуту уменьшаем счётчик до следующего авто-открытия
            if (++secondCounter >= 60) {
                secondCounter = 0;
                if (--minutesToNext <= 0) {
                    open(null);
                    minutesToNext = Math.max(1, plugin.getConfig().getInt("black-market.interval-minutes", 40));
                }
            }
            return;
        }
        long leftSec = (closeAt - System.currentTimeMillis()) / 1000;
        if (leftSec <= 0) {
            close(true);
            minutesToNext = Math.max(1, plugin.getConfig().getInt("black-market.interval-minutes", 40));
            secondCounter = 0;
            return;
        }
        secondCounter++;
        if (secondCounter % 30 == 0) updatePrices();
        if (leftSec == 300 || leftSec == 60 || leftSec == 10) {
            Bukkit.broadcast(U.c("&8&l[ЧЁРНЫЙ РЫНОК] &fЗакроется через &c" + U.time(leftSec) + "&f! &7X: "
                    + loc.getBlockX() + " Z: " + loc.getBlockZ()));
        }
        if (dealer == null || !dealer.isValid()) {
            // торговца могли убрать выгрузкой чанка — вернём
            if (loc.getChunk().isLoaded()) {
                dealer = loc.getWorld().spawn(loc.clone().add(0.5, 0, 0.5), Villager.class, v -> {
                    v.setAI(false);
                    v.setInvulnerable(true);
                    v.setSilent(true);
                    v.setPersistent(false);
                    v.setProfession(Villager.Profession.NITWIT);
                    v.customName(U.c("&8&lТорговец-подпольщик"));
                    v.setCustomNameVisible(true);
                    v.getPersistentDataContainer().set(dealerKey, PersistentDataType.BYTE, (byte) 1);
                });
            }
        }
        loc.getWorld().spawnParticle(Particle.SOUL_FIRE_FLAME, loc.clone().add(0.5, 0.2, 0.5), 12, 0.8, 0.1, 0.8, 0.02);
        loc.getWorld().spawnParticle(Particle.PORTAL, loc.clone().add(0.5, 1.5, 0.5), 30, 0.6, 1.2, 0.6, 0.3);
        updateHolo();
        updateInfoSlot();
    }

    private void updateHolo() {
        if (holo == null || !holo.isValid()) return;
        long left = Math.max(0, (closeAt - System.currentTimeMillis()) / 1000);
        holo.text(U.c("&8&l☠ ЧЁРНЫЙ РЫНОК ☠\n&fЗакроется через &c" + U.time(left)));
    }

    // ---------- взаимодействие ----------

    public boolean isNear(Player p) {
        return open && loc != null && p.getWorld().equals(loc.getWorld())
                && p.getLocation().distanceSquared(loc) <= 12 * 12;
    }

    public void openGui(Player p) {
        if (!open) {
            U.msg(p, "&8[ЧЁРНЫЙ РЫНОК] &7Сейчас рынок закрыт.");
            return;
        }
        updateInfoSlot();
        gui.open(p);
    }

    /** /bm: информация и координаты; рядом с торговцем — открывает меню. */
    public void info(CommandSender s) {
        if (!open) {
            s.sendMessage(U.c("&8[ЧЁРНЫЙ РЫНОК] &7Рынок закрыт. Он открывается каждые "
                    + plugin.getConfig().getInt("black-market.interval-minutes", 40) + " мин. на 10 минут."));
            return;
        }
        long left = Math.max(0, (closeAt - System.currentTimeMillis()) / 1000);
        s.sendMessage(U.c("&8&l[ЧЁРНЫЙ РЫНОК] &fОткрыт! Закроется через &c" + U.time(left)));
        s.sendMessage(U.c("&fКоординаты: &aX: &e" + loc.getBlockX() + "&f, &aY: &e" + loc.getBlockY()
                + "&f, &aZ: &e" + loc.getBlockZ() + " &7(" + loc.getWorld().getName() + ")"));
        if (s instanceof Player p) {
            if (isNear(p)) openGui(p);
            else s.sendMessage(U.c("&7Подойдите к торговцу (в пределах 12 блоков) и снова введите &f/bm &7или нажмите на него."));
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDealerClick(PlayerInteractEntityEvent e) {
        if (!e.getRightClicked().getPersistentDataContainer().has(dealerKey, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        if (e.getHand() != EquipmentSlot.HAND) return;
        openGui(e.getPlayer());
    }

    @EventHandler
    public void onDealerDamage(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(dealerKey, PersistentDataType.BYTE)) e.setCancelled(true);
    }
}
