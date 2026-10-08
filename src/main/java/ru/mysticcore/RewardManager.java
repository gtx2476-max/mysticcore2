package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Награды за онлайн: 1ч / 3ч / 5ч — токены, каждый час — монетки, раз в сутки — ежедневный бонус. */
public final class RewardManager implements Listener {

    private static final int[] MILESTONES = {3600, 10800, 18000};

    private final MysticCore plugin;
    private final Map<UUID, Long> seconds = new HashMap<>();
    private final Map<UUID, List<Integer>> claimed = new HashMap<>();
    private final Map<UUID, Long> lastDaily = new HashMap<>();
    private BukkitTask task;

    public RewardManager(MysticCore plugin) {
        this.plugin = plugin;
    }

    private File file() { return new File(plugin.getDataFolder(), "rewards.yml"); }

    public void start() {
        if (file().exists()) {
            ConfigurationSection s = YamlConfiguration.loadConfiguration(file()).getConfigurationSection("players");
            if (s != null) {
                for (String k : s.getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(k);
                        seconds.put(id, s.getLong(k + ".time"));
                        claimed.put(id, new ArrayList<>(s.getIntegerList(k + ".claimed")));
                        lastDaily.put(id, s.getLong(k + ".daily"));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }
        }
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::minuteTick, 1200L, 1200L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        save();
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (UUID id : seconds.keySet()) {
            y.set("players." + id + ".time", seconds.get(id));
            y.set("players." + id + ".claimed", claimed.getOrDefault(id, new ArrayList<>()));
            y.set("players." + id + ".daily", lastDaily.getOrDefault(id, 0L));
        }
        try {
            y.save(file());
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить rewards.yml: " + ex.getMessage());
        }
    }

    private void minuteTick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            UUID id = p.getUniqueId();
            long old = seconds.getOrDefault(id, 0L);
            long now = old + 60;
            seconds.put(id, now);
            plugin.quests().progress(p, QuestManager.Type.PLAY_MINUTES, 1);
            List<Integer> done = claimed.computeIfAbsent(id, k -> new ArrayList<>());
            int[] rewards = {
                    plugin.getConfig().getInt("rewards.tokens-1h", 50),
                    plugin.getConfig().getInt("rewards.tokens-3h", 200),
                    plugin.getConfig().getInt("rewards.tokens-5h", 400)};
            for (int i = 0; i < MILESTONES.length; i++) {
                if (now >= MILESTONES[i] && !done.contains(MILESTONES[i])) {
                    done.add(MILESTONES[i]);
                    plugin.getEconomy().addTokens(id, rewards[i]);
                    U.msg(p, "&6&l[НАГРАДА] &fЗа &e" + (MILESTONES[i] / 3600) + " ч. &fонлайна: &d+" + rewards[i] + " токенов&f!");
                }
            }
            if (old / 3600 < now / 3600) {
                long coins = plugin.getConfig().getLong("rewards.coins-per-hour", 100);
                plugin.getEconomy().addCoins(id, coins);
                plugin.season().addXp(p, 20);
                U.msg(p, "&6&l[НАГРАДА] &fЧасовой бонус: &e+" + U.money(coins) + " монеток&f!");
            }
        }
        save();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            long now = System.currentTimeMillis();
            long last = lastDaily.getOrDefault(p.getUniqueId(), 0L);
            if (now - last >= 24L * 3600_000L) {
                lastDaily.put(p.getUniqueId(), now);
                long t = plugin.getConfig().getLong("rewards.daily-tokens", 150);
                long c = plugin.getConfig().getLong("rewards.daily-coins", 300);
                plugin.getEconomy().addTokens(p.getUniqueId(), t);
                plugin.getEconomy().addCoins(p.getUniqueId(), c);
                U.msg(p, "&6&l[ЕЖЕДНЕВНЫЙ БОНУС] &d+" + t + " токенов &fи &e+" + c + " монеток&f!");
            }
        }, 60L);
    }
}
