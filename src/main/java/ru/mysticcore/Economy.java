package ru.mysticcore;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** Токены (донат-валюта) и монетки. Импортирует балансы из старого MysticChests (tokens.yml / coins.yml). */
public final class Economy {
    private final MysticCore plugin;
    private final Map<UUID, Long> tokens = new HashMap<>();
    private final Map<UUID, Long> coins = new HashMap<>();
    private boolean dirty = false;

    public Economy(MysticCore plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.getDataFolder().mkdirs();
        read(tokens, "tokens.yml");
        read(coins, "coins.yml");
    }

    private void read(Map<UUID, Long> map, String name) {
        File own = new File(plugin.getDataFolder(), name);
        File f = own;
        boolean imported = false;
        if (!own.exists()) {
            File old = new File(plugin.getDataFolder().getParentFile(), "MysticChests/" + name);
            if (!old.exists()) return;
            f = old;
            imported = true;
        }
        ConfigurationSection s = YamlConfiguration.loadConfiguration(f).getConfigurationSection("balances");
        if (s == null) return;
        for (String k : s.getKeys(false)) {
            try {
                map.put(UUID.fromString(k), s.getLong(k));
            } catch (IllegalArgumentException ignored) {
            }
        }
        if (imported) dirty = true;
    }

    public synchronized void save() {
        if (!dirty) return;
        write(tokens, "tokens.yml");
        write(coins, "coins.yml");
        dirty = false;
    }

    private void write(Map<UUID, Long> map, String name) {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID, Long> e : map.entrySet()) y.set("balances." + e.getKey(), e.getValue());
        try {
            y.save(new File(plugin.getDataFolder(), name));
        } catch (IOException ex) {
            plugin.getLogger().warning("Не удалось сохранить " + name + ": " + ex.getMessage());
        }
    }

    // ----- токены -----
    public long tokens(UUID id) {
        return tokens.computeIfAbsent(id, k -> {
            dirty = true;
            return plugin.getConfig().getLong("starting-tokens", 1000);
        });
    }

    public void setTokens(UUID id, long v) { tokens.put(id, Math.max(0, v)); dirty = true; }

    public void addTokens(UUID id, long v) { setTokens(id, tokens(id) + v); }

    public boolean takeTokens(UUID id, long v) {
        if (tokens(id) < v) return false;
        setTokens(id, tokens(id) - v);
        return true;
    }

    // ----- монетки -----
    public long coins(UUID id) { return coins.getOrDefault(id, 0L); }

    public void setCoins(UUID id, long v) { coins.put(id, Math.max(0, v)); dirty = true; }

    public void addCoins(UUID id, long v) { setCoins(id, coins(id) + v); }

    public boolean takeCoins(UUID id, long v) {
        if (coins(id) < v) return false;
        setCoins(id, coins(id) - v);
        return true;
    }
}
