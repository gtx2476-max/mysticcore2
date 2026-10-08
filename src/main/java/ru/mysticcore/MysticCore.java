package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.command.PluginCommand;
import org.bukkit.inventory.RecipeChoice;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import ru.mysticcore.spheres.SphereItems;
import ru.mysticcore.spheres.SphereListener;
import ru.mysticcore.spheres.SphereMenu;
import ru.mysticcore.spheres.SphereType;
import ru.mysticcore.spheres.SpheresCommand;
import ru.mysticcore.spheres.SpheresManager;

import java.util.ArrayList;
import java.util.List;

/**
 * MysticCore — всё в одном: мистические сундуки (аирдропы), сферы, талисманы, кейсы, Чёрный рынок,
 * Донат маркет (токены), магазин и аукцион (монетки), Мама Фугу, лобби, шахта, RTP, награды.
 */
public final class MysticCore extends JavaPlugin {

    private static MysticCore instance;

    private Economy economy;
    private Catalog catalog;
    private SphereItems sphereItems;
    private SpheresManager spheresManager;
    private SphereMenu sphereMenu;
    private TalismanManager talismans;
    private DropManager drops;
    private BlackMarket blackMarket;
    private AuctionManager auction;
    private BossManager boss;
    private LobbyManager lobby;
    private RewardManager rewards;
    private Rtp rtp;
    private Menus menus;
    private Exchange exchange;
    private QuestManager quests;
    private SeasonPass season;
    private BukkitTask saveTask;
    private final List<NamespacedKey> recipeKeys = new ArrayList<>();

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        economy = new Economy(this);
        economy.load();
        catalog = new Catalog(this);
        sphereItems = new SphereItems(this);
        registerRecipes();

        spheresManager = new SpheresManager(this, sphereItems);
        sphereMenu = new SphereMenu(this, sphereItems);
        talismans = new TalismanManager(this);
        drops = new DropManager(this);
        blackMarket = new BlackMarket(this);
        auction = new AuctionManager(this);
        auction.load();
        boss = new BossManager(this);
        lobby = new LobbyManager(this);
        rewards = new RewardManager(this);
        rtp = new Rtp(this);
        menus = new Menus(this);
        exchange = new Exchange(this);
        exchange.load();
        quests = new QuestManager(this);
        season = new SeasonPass(this);

        var pm = getServer().getPluginManager();
        pm.registerEvents(new GuiListener(), this);
        pm.registerEvents(new SphereListener(this, sphereItems, spheresManager), this);
        pm.registerEvents(sphereMenu, this);
        pm.registerEvents(new ItemListener(this), this);
        pm.registerEvents(new DropListener(drops), this);
        pm.registerEvents(blackMarket, this);
        pm.registerEvents(boss, this);
        pm.registerEvents(lobby, this);
        pm.registerEvents(rewards, this);
        pm.registerEvents(quests, this);

        spheresManager.start();
        talismans.start();
        lobby.start();
        drops.start();
        blackMarket.start();
        boss.start();
        rewards.start();
        exchange.start();
        quests.start();
        season.start();

        Commands cmds = new Commands(this);
        for (String name : new String[]{"menu", "donate", "shop", "mystic", "lobby", "mine", "ah", "coins", "rtp",
                "airdrops", "blackmarket", "quests", "season", "sell", "bourse"}) {
            PluginCommand pc = getCommand(name);
            if (pc != null) {
                pc.setExecutor(cmds);
                pc.setTabCompleter(cmds);
            }
        }
        PluginCommand sc = getCommand("spheres");
        if (sc != null) {
            SpheresCommand h = new SpheresCommand(this, sphereItems, sphereMenu);
            sc.setExecutor(h);
            sc.setTabCompleter(h);
        }

        saveTask = Bukkit.getScheduler().runTaskTimer(this, () -> economy.save(), 2400L, 2400L);
        getLogger().info("MysticCore включен. Старые MysticChests/AirDrops/Spheres нужно удалить из plugins/.");
    }

    @Override
    public void onDisable() {
        if (saveTask != null) saveTask.cancel();
        if (boss != null) boss.shutdown();
        if (blackMarket != null) blackMarket.shutdown();
        if (drops != null) drops.shutdown();
        if (spheresManager != null) spheresManager.shutdown();
        if (talismans != null) talismans.shutdown();
        if (lobby != null) lobby.shutdown();
        if (rewards != null) rewards.shutdown();
        if (exchange != null) exchange.shutdown();
        if (quests != null) quests.shutdown();
        if (season != null) season.shutdown();
        if (auction != null) auction.save();
        if (economy != null) economy.save();
        for (NamespacedKey k : recipeKeys) Bukkit.removeRecipe(k);
        recipeKeys.clear();
    }

    private void registerRecipes() {
        for (SphereType t : SphereType.values()) {
            NamespacedKey k = new NamespacedKey(this, "recipe_" + t.id);
            ShapedRecipe r = new ShapedRecipe(k, sphereItems.create(t));
            if (t == SphereType.EMPTY) {
                r.shape("HHH", "HHH", "HHH");
                r.setIngredient('H', new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD));
            } else {
                r.shape("CEC", "ESE", "CEC");
                r.setIngredient('C', new RecipeChoice.MaterialChoice(t.corner));
                r.setIngredient('E', new RecipeChoice.MaterialChoice(t.edge));
                r.setIngredient('S', new RecipeChoice.MaterialChoice(Material.PLAYER_HEAD));
            }
            Bukkit.addRecipe(r);
            recipeKeys.add(k);
        }
    }

    public World survivalWorld() {
        World w = Bukkit.getWorld(getConfig().getString("survival-world", "world"));
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        return w;
    }

    public long sphereCoinPrice(SphereType t) {
        return getConfig().getLong("shop.sphere-prices." + t.name(), 5000);
    }

    public long sphereTokenPrice(SphereType t) {
        return getConfig().getLong("market.sphere-prices." + t.name(), 50000);
    }

    public static MysticCore getInstance() { return instance; }
    public Economy getEconomy() { return economy; }
    public Catalog getCatalog() { return catalog; }
    public SphereItems getSphereItems() { return sphereItems; }
    public SphereMenu getSphereMenu() { return sphereMenu; }
    public List<NamespacedKey> getRecipeKeys() { return recipeKeys; }
    public DropManager drops() { return drops; }
    public BlackMarket blackMarket() { return blackMarket; }
    public AuctionManager auction() { return auction; }
    public BossManager boss() { return boss; }
    public LobbyManager lobby() { return lobby; }
    public Rtp getRtp() { return rtp; }
    public Menus menus() { return menus; }
    public Exchange exchange() { return exchange; }
    public QuestManager quests() { return quests; }
    public SeasonPass season() { return season; }
}
