package ru.mysticcore.spheres;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LargeFireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.PrepareItemCraftEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;
import ru.mysticcore.MysticCore;

import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

public final class SphereListener implements Listener {

    private final MysticCore plugin;
    private final SphereItems items;
    private final SpheresManager manager;
    private final Random random = new Random();
    private final NamespacedKey fireballKey;
    private final Map<UUID, Long> lightningImmune = new HashMap<>();
    private boolean busy = false; // защита от рекурсии при нашем дополнительном уроне

    public SphereListener(MysticCore plugin, SphereItems items, SpheresManager manager) {
        this.plugin = plugin;
        this.items = items;
        this.manager = manager;
        this.fireballKey = new NamespacedKey(plugin, "sphere_fireball");
    }

    private boolean roll(String path, double def) {
        return random.nextDouble() * 100.0 < plugin.getConfig().getDouble(path, def);
    }

    private void actionBar(Player p, String key) {
        p.sendActionBar(SphereItems.c(plugin.getConfig().getString("messages." + key, "")));
    }

    // ---------- бой ----------

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (busy) return;
        Entity damager = e.getDamager();

        if (damager.getPersistentDataContainer().has(fireballKey, PersistentDataType.BYTE)) {
            if (damager instanceof Projectile pr && e.getEntity().equals(pr.getShooter())) e.setCancelled(true);
            return;
        }
        if (!(e.getEntity() instanceof LivingEntity victim)) return;

        Player attacker = null;
        boolean melee = false;
        if (damager instanceof Player p) {
            attacker = p;
            melee = true;
        } else if (damager instanceof Projectile pr && pr.getShooter() instanceof Player p) {
            attacker = p;
        }
        if (attacker == null || attacker.equals(victim)) return;

        Set<SphereType> act = manager.active(attacker);
        if (act.isEmpty()) return;

        if (act.contains(SphereType.POSEIDON) && roll("poseidon.chance", 40)) {
            int secs = plugin.getConfig().getInt("poseidon.slow-seconds", 4);
            int amp = Math.max(0, plugin.getConfig().getInt("poseidon.slow-level", 2) - 1);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, secs * 20, amp));
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_SPLASH, 1f, 1f);
            actionBar(attacker, "poseidon-proc");
        }

        if (act.contains(SphereType.HADES)) {
            if (melee) victim.setFireTicks(plugin.getConfig().getInt("hades.fire-seconds", 6) * 20);
            if (roll("hades.chance", 18)) {
                launchFireball(attacker, victim);
                actionBar(attacker, "hades-proc");
            }
        }

        if (act.contains(SphereType.ZEUS) && melee) {
            boolean swordOnly = plugin.getConfig().getBoolean("zeus.sword-only", false);
            boolean okWeapon = !swordOnly || attacker.getInventory().getItemInMainHand().getType().name().endsWith("_SWORD");
            if (okWeapon && roll("zeus.chance", 18)) strikeZeus(attacker, victim);
        }

        if (act.contains(SphereType.ARES) && roll("ares.chance", 20)) {
            int secs = plugin.getConfig().getInt("ares.wither-seconds", 3);
            victim.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, secs * 20, 1));
            victim.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 100, 0));
            actionBar(attacker, "ares-proc");
        }

        if (act.contains(SphereType.APOLLO) && roll("apollo.chance", 15)) {
            double heal = plugin.getConfig().getDouble("apollo.heal", 4);
            var maxAttr = attacker.getAttribute(Attribute.MAX_HEALTH);
            double max = maxAttr == null ? 20 : maxAttr.getValue();
            attacker.setHealth(Math.min(max, attacker.getHealth() + heal));
            attacker.getWorld().playSound(attacker.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.6f);
            actionBar(attacker, "apollo-proc");
        }
    }

    /** Настоящая молния: удар в цель + дополнительный урон; атакующий защищён от своей молнии на 2 сек. */
    private void strikeZeus(Player attacker, LivingEntity victim) {
        lightningImmune.put(attacker.getUniqueId(), System.currentTimeMillis() + 2500);
        Location loc = victim.getLocation();
        loc.getWorld().strikeLightning(loc);
        double extra = plugin.getConfig().getDouble("zeus.extra-damage", 6.0);
        busy = true;
        try {
            victim.damage(extra, attacker);
        } finally {
            busy = false;
        }
        plugin.getServer().getScheduler().runTask(plugin, () -> attacker.setFireTicks(0));
        actionBar(attacker, "zeus-proc");
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLightningDamage(EntityDamageEvent e) {
        if (e.getCause() != EntityDamageEvent.DamageCause.LIGHTNING) return;
        if (!(e.getEntity() instanceof Player p)) return;
        Long until = lightningImmune.get(p.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) e.setCancelled(true);
    }

    private void launchFireball(Player attacker, LivingEntity victim) {
        Location from = attacker.getEyeLocation().add(attacker.getLocation().getDirection().multiply(1.0));
        Vector dir = victim.getEyeLocation().toVector().subtract(from.toVector());
        if (dir.lengthSquared() < 0.01) return;
        dir.normalize();
        float power = (float) plugin.getConfig().getDouble("hades.fireball-power", 2.0);
        attacker.getWorld().spawn(from, LargeFireball.class, fb -> {
            fb.setShooter(attacker);
            fb.setDirection(dir);
            fb.setYield(power);
            fb.setIsIncendiary(false);
            fb.getPersistentDataContainer().set(fireballKey, PersistentDataType.BYTE, (byte) 1);
        });
        attacker.getWorld().playSound(from, Sound.ENTITY_BLAZE_SHOOT, 1f, 0.8f);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(fireballKey, PersistentDataType.BYTE)
                && !plugin.getConfig().getBoolean("hades.fireball-break-blocks", false)) {
            e.blockList().clear();
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (e.getEntity() instanceof Player p && manager.active(p).contains(SphereType.HADES)) {
            e.getProjectile().setFireTicks(400);
        }
    }

    // ---------- головы ----------

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        if (victim.getKiller() == null || victim.getKiller().equals(victim)) return;
        if (random.nextDouble() * 100.0 < plugin.getConfig().getDouble("head-drop-chance", 100)) {
            e.getDrops().add(items.playerHead(victim));
        }
    }

    // ---------- крафт ----------

    @EventHandler
    public void onPrepareCraft(PrepareItemCraftEvent e) {
        Recipe recipe = e.getRecipe();
        if (!(recipe instanceof ShapedRecipe sr)) return;
        if (!sr.getKey().getNamespace().equals(plugin.getName().toLowerCase())) return;
        if (!sr.getKey().getKey().startsWith("recipe_")) return;

        ItemStack[] m = e.getInventory().getMatrix();
        if (m.length != 9) { e.getInventory().setResult(null); return; }

        boolean isEmptyRecipe = sr.getKey().getKey().equals("recipe_" + SphereType.EMPTY.id);
        if (isEmptyRecipe) {
            for (ItemStack it : m) {
                if (it == null || it.getType() != Material.PLAYER_HEAD || items.typeOf(it) != null) {
                    e.getInventory().setResult(null);
                    return;
                }
            }
        } else if (items.typeOf(m[4]) != SphereType.EMPTY) {
            e.getInventory().setResult(null);
        }
    }

    /** Сферы (головы) и спец-предметы нельзя ставить как блоки. */
    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack inHand = e.getItemInHand();
        if (items.typeOf(inHand) != null || plugin.getCatalog().idOf(inHand) != null) e.setCancelled(true);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        e.getPlayer().discoverRecipes(plugin.getRecipeKeys());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        manager.clearPlayer(e.getPlayer());
        lightningImmune.remove(e.getPlayer().getUniqueId());
    }
}
