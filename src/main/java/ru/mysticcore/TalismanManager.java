package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashSet;
import java.util.Set;

/** Талисманы работают, пока лежат в инвентаре (или во второй руке). Каждый тип учитывается один раз. */
public final class TalismanManager {

    private static final String[] IDS = {"t_life", "t_power", "t_wind", "t_vision", "t_luck", "t_guard"};

    private final MysticCore plugin;
    private BukkitTask task;

    public TalismanManager(MysticCore plugin) {
        this.plugin = plugin;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) clear(p);
    }

    public void clear(Player p) {
        for (String id : IDS) apply(p, id, false);
    }

    private void tick() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            Set<String> have = new HashSet<>();
            for (ItemStack it : p.getInventory().getContents()) {
                String id = plugin.getCatalog().idOf(it);
                if (id != null && id.startsWith("t_")) have.add(id);
            }
            for (String id : IDS) apply(p, id, have.contains(id));
        }
    }

    private void apply(Player p, String id, boolean on) {
        AttributeModifier.Operation add = AttributeModifier.Operation.ADD_NUMBER;
        switch (id) {
            case "t_life" -> AttrUtil.apply(plugin, p, "tal_life", Attribute.MAX_HEALTH, 4, add, on);
            case "t_power" -> AttrUtil.apply(plugin, p, "tal_power", Attribute.ATTACK_DAMAGE, 2, add, on);
            case "t_wind" -> AttrUtil.apply(plugin, p, "tal_wind", Attribute.MOVEMENT_SPEED, 0.10,
                    AttributeModifier.Operation.ADD_SCALAR, on);
            case "t_vision" -> {
                if (on) p.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 400, 0, true, false, false));
            }
            case "t_luck" -> AttrUtil.apply(plugin, p, "tal_luck", Attribute.LUCK, 2, add, on);
            case "t_guard" -> {
                AttrUtil.apply(plugin, p, "tal_guard_armor", Attribute.ARMOR, 3, add, on);
                AttrUtil.apply(plugin, p, "tal_guard_kb", Attribute.KNOCKBACK_RESISTANCE, 0.2, add, on);
            }
            default -> {}
        }
    }
}
