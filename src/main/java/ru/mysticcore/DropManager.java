package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Random;

/** Сундуки (аирдропы): автоспавн волнами, покупка за токены, список активных. */
public final class DropManager {

    private final MysticCore plugin;
    private final Random random = new Random();
    private final Map<String, Tier> tiers = new LinkedHashMap<>();
    private final Map<Integer, Drop> drops = new LinkedHashMap<>();
    private BukkitTask task;
    private int nextId = 1;
    private int secondsUntilWave;

    private static final Map<String, String> DESC = Map.of(
            "poor", "&7Базовые ресурсы и еда.",
            "solid", "&7Ресурсы получше, шанс на сферу, тотем и талисман.",
            "rich", "&7Алмазы, изумруды, золото, шанс на сферы и талисманы.",
            "elite", "&7Гарантированно ценные предметы и шанс на сферу.",
            "crusher", "&7Самый редкий: сферы, талисманы, вещи Крушителя.");

    public DropManager(MysticCore plugin) {
        this.plugin = plugin;
        loadTiers();
    }

    public void loadTiers() {
        tiers.clear();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("tiers");
        if (sec == null) return;
        for (String key : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(key);
            if (s == null) continue;
            Material block = Material.matchMaterial(s.getString("block", "CHEST"));
            if (block == null || !block.isBlock()) block = Material.CHEST;
            Material icon = Material.matchMaterial(s.getString("icon", "CHEST"));
            if (icon == null) icon = Material.CHEST;
            tiers.put(key, new Tier(key, s.getString("name", key), s.getLong("price", 100),
                    Math.max(5, s.getInt("unlock-seconds", 60)), block, icon, DESC.getOrDefault(key, "")));
        }
    }

    public void start() {
        resetWave();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Drop d : new ArrayList<>(drops.values())) d.remove(Drop.RemoveReason.SHUTDOWN);
    }

    private void resetWave() {
        secondsUntilWave = Math.max(10, plugin.getConfig().getInt("drops.interval-minutes", 3) * 60);
    }

    private void tick() {
        for (Drop d : new ArrayList<>(drops.values())) d.tick();
        secondsUntilWave--;
        if (secondsUntilWave <= 0) {
            boolean skip = plugin.getConfig().getBoolean("drops.only-if-players-online", true)
                    && Bukkit.getOnlinePlayers().isEmpty();
            if (!skip) spawnWave();
            resetWave();
        }
    }

    public void spawnWave() {
        int count = Math.max(1, plugin.getConfig().getInt("drops.per-wave", 3));
        for (int i = 0; i < count; i++) spawnRandom(rollTier());
    }

    public Drop spawnRandom(Tier tier) {
        Location loc = pickLocation();
        return loc == null ? null : spawn(tier, loc);
    }

    public Drop spawn(Tier tier, Location loc) {
        if (tier == null || loc == null || loc.getWorld() == null) return null;
        Drop drop = new Drop(plugin, this, nextId++, tier, loc, random);
        drops.put(drop.getId(), drop);
        drop.place();
        Location l = drop.getLocation();
        String[] lines = {
                "",
                "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                "&6&l[МИСТИЧЕСКИЙ СУНДУК] &fПоявился " + tier.name() + "&f!",
                "&fКоординаты: &aX: &e" + l.getBlockX() + "&f, &aY: &e" + l.getBlockY() + "&f, &aZ: &e" + l.getBlockZ()
                        + " &7(" + l.getWorld().getName() + ")",
                "&fОткроется через: &c" + U.time(tier.unlockSeconds()),
                "&e&l▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬",
                ""};
        for (String line : lines) Bukkit.broadcast(U.c(line));
        return drop;
    }

    public Tier rollTier() {
        ConfigurationSection w = plugin.getConfig().getConfigurationSection("drops.tier-weights");
        int total = 0;
        for (String k : tiers.keySet()) total += w == null ? 1 : Math.max(0, w.getInt(k, 1));
        if (total <= 0) return tiers.values().iterator().next();
        int r = random.nextInt(total);
        for (Tier t : tiers.values()) {
            r -= w == null ? 1 : Math.max(0, w.getInt(t.key(), 1));
            if (r < 0) return t;
        }
        return tiers.values().iterator().next();
    }

    public Tier tier(String key) { return tiers.get(key); }

    public Collection<Tier> tiers() { return tiers.values(); }

    /** Случайная точка над землёй в кольце вокруг спавна мира выживания. */
    public Location pickLocation() {
        FileConfiguration cfg = plugin.getConfig();
        World w = plugin.survivalWorld();
        if (w == null) return null;
        double min = cfg.getDouble("drops.min-radius", 300);
        double max = cfg.getDouble("drops.max-radius", 3000);
        if (min > max) { double t = min; min = max; max = t; }
        Location c = w.getSpawnLocation();
        for (int i = 0; i < 25; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double dist = min + random.nextDouble() * (max - min);
            int x = (int) Math.round(c.getX() + Math.cos(ang) * dist);
            int z = (int) Math.round(c.getZ() + Math.sin(ang) * dist);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) continue;
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = w.getBlockAt(x, y, z);
            if (ground.isLiquid() || ground.getType() == Material.AIR) continue;
            return new Location(w, x, y + 1, z);
        }
        return null;
    }

    void unregister(Drop d) { drops.remove(d.getId()); }

    public Drop getByBlock(Block block) {
        for (Drop d : drops.values()) if (d.isAt(block)) return d;
        return null;
    }

    public Collection<Drop> getDrops() { return Collections.unmodifiableCollection(drops.values()); }

    public int removeAll() {
        int n = drops.size();
        for (Drop d : new ArrayList<>(drops.values())) d.remove(Drop.RemoveReason.ADMIN);
        return n;
    }

    public int getSecondsUntilWave() { return secondsUntilWave; }

    void broadcastReady(Drop d) {
        Location l = d.getLocation();
        Bukkit.broadcast(U.c("&6&l[СУНДУК] &a" + d.getTier().name() + " &aоткрыт! &7X: " + l.getBlockX()
                + " Y: " + l.getBlockY() + " Z: " + l.getBlockZ()));
    }

    void broadcastRemoved(Drop d, Drop.RemoveReason reason) {
        if (reason == Drop.RemoveReason.LOOTED) {
            Bukkit.broadcast(U.c("&6&l[СУНДУК] &7Сундук #" + d.getId() + " полностью залутан."));
        } else if (reason == Drop.RemoveReason.EXPIRED) {
            Bukkit.broadcast(U.c("&6&l[СУНДУК] &7Сундук #" + d.getId() + " исчез."));
        }
    }
}
