package ru.mysticcore;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.HeightMap;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

/** /rtp: случайная телепортация по карте выживания. */
public final class Rtp {

    private final MysticCore plugin;
    private final Random random = new Random();
    private final Map<UUID, Long> cooldowns = new HashMap<>();

    public Rtp(MysticCore plugin) {
        this.plugin = plugin;
    }

    /** ignoreCooldown = true для свитка телепорта. Возвращает true, если телепортация состоялась. */
    public boolean teleport(Player p, boolean ignoreCooldown) {
        World w = plugin.survivalWorld();
        if (w == null) {
            U.msg(p, "&cМир выживания не найден.");
            return false;
        }
        if (p.getWorld().equals(plugin.lobby().world())) {
            U.msg(p, "&cВ лобби RTP недоступен. Выберите режим у NPC.");
            return false;
        }
        long now = System.currentTimeMillis();
        long cd = plugin.getConfig().getLong("rtp.cooldown-seconds", 30) * 1000L;
        Long last = cooldowns.get(p.getUniqueId());
        if (!ignoreCooldown && last != null && now - last < cd && !p.hasPermission("mystic.admin")) {
            U.msg(p, "&cПодождите ещё &f" + ((cd - (now - last)) / 1000 + 1) + " &cсек.");
            return false;
        }
        Location target = find(w);
        if (target == null) {
            U.msg(p, "&cНе удалось найти безопасное место. Попробуйте ещё раз.");
            return false;
        }
        cooldowns.put(p.getUniqueId(), now);
        p.teleport(target);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        U.msg(p, "&aВы телепортированы: &fX: " + target.getBlockX() + " Y: " + target.getBlockY() + " Z: " + target.getBlockZ());
        return true;
    }

    private Location find(World w) {
        double min = plugin.getConfig().getDouble("rtp.min-radius", 200);
        double max = plugin.getConfig().getDouble("rtp.radius", 3000);
        if (min > max) { double t = min; min = max; max = t; }
        Location c = w.getSpawnLocation();
        for (int i = 0; i < 20; i++) {
            double ang = random.nextDouble() * Math.PI * 2;
            double dist = min + random.nextDouble() * (max - min);
            int x = (int) Math.round(c.getX() + Math.cos(ang) * dist);
            int z = (int) Math.round(c.getZ() + Math.sin(ang) * dist);
            if (!w.getWorldBorder().isInside(new Location(w, x, 64, z))) continue;
            int y = w.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
            Block ground = w.getBlockAt(x, y, z);
            Material m = ground.getType();
            if (ground.isLiquid() || m == Material.AIR || m == Material.LAVA || m == Material.MAGMA_BLOCK
                    || m == Material.CACTUS || m == Material.FIRE) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }
}
