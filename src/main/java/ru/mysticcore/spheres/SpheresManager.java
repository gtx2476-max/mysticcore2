package ru.mysticcore.spheres;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import ru.mysticcore.AttrUtil;
import ru.mysticcore.MysticCore;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Пассивные эффекты сфер: бафы, дебафы, атрибуты, аура Фугу, частицы. Тик = 5 игровых тиков. */
public final class SpheresManager {

    private record Mod(Attribute attribute, String name, double amount, AttributeModifier.Operation op) {}

    private final MysticCore plugin;
    private final SphereItems items;
    private BukkitTask task;
    private int counter = 0;

    public SpheresManager(MysticCore plugin, SphereItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 5L, 5L);
    }

    public void shutdown() {
        if (task != null) task.cancel();
        for (Player p : Bukkit.getOnlinePlayers()) clearPlayer(p);
    }

    public void clearPlayer(Player p) {
        for (SphereType t : SphereType.values()) applyMods(p, t, false);
    }

    private FileConfiguration cfg() { return plugin.getConfig(); }

    private int amp(String path) { return Math.max(0, cfg().getInt(path, 1) - 1); }

    /** Какие сферы активны (лежат в руках). Аид может требовать две сферы. */
    public Set<SphereType> active(Player p) {
        Map<SphereType, Integer> counts = new EnumMap<>(SphereType.class);
        ItemStack[] hands = {p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand()};
        for (ItemStack it : hands) {
            SphereType t = items.typeOf(it);
            if (t != null && t != SphereType.EMPTY) counts.merge(t, 1, Integer::sum);
        }
        Set<SphereType> result = EnumSet.noneOf(SphereType.class);
        for (Map.Entry<SphereType, Integer> en : counts.entrySet()) {
            if (en.getKey() == SphereType.HADES && cfg().getBoolean("hades.require-both-hands", false)
                    && en.getValue() < 2) continue;
            result.add(en.getKey());
        }
        return result;
    }

    private void tick() {
        counter++;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getGameMode() == GameMode.SPECTATOR) continue;
            Set<SphereType> act = active(p);
            for (SphereType t : SphereType.values()) {
                if (t == SphereType.EMPTY) continue;
                applyMods(p, t, act.contains(t));
            }
            if (counter % 2 == 0) for (SphereType t : act) applyPassive(p, t);
            for (SphereType t : act) {
                if (t == SphereType.FUGU) fuguAura(p);
                else smallAura(p, t);
            }
        }
    }

    private void applyPassive(Player p, SphereType t) {
        switch (t) {
            case POSEIDON -> {
                eff(p, PotionEffectType.WATER_BREATHING, 0);
                eff(p, PotionEffectType.DOLPHINS_GRACE, 0);
            }
            case HADES -> {
                eff(p, PotionEffectType.SPEED, amp("hades.speed-level"));
                eff(p, PotionEffectType.FIRE_RESISTANCE, 0);
            }
            case ZEUS -> eff(p, PotionEffectType.HASTE, amp("zeus.haste-level"));
            case HERMES -> {
                eff(p, PotionEffectType.SPEED, amp("hermes.speed-level"));
                eff(p, PotionEffectType.JUMP_BOOST, amp("hermes.jump-level"));
            }
            case ATHENA -> eff(p, PotionEffectType.RESISTANCE, amp("athena.resistance-level"));
            case APOLLO -> eff(p, PotionEffectType.REGENERATION, amp("apollo.regen-level"));
            default -> {}
        }
    }

    private void eff(LivingEntity e, PotionEffectType type, int amplifier) {
        e.addPotionEffect(new PotionEffect(type, 45, amplifier, true, false, true));
    }

    private Color colorOf(SphereType t) {
        return switch (t) {
            case POSEIDON -> Color.AQUA;
            case HADES -> Color.RED;
            case ZEUS -> Color.YELLOW;
            case HERMES -> Color.WHITE;
            case ATHENA -> Color.BLUE;
            case ARES -> Color.MAROON;
            case APOLLO -> Color.ORANGE;
            default -> Color.LIME;
        };
    }

    /** Лёгкое кольцо частиц у ног — у каждой сферы свой цвет. */
    private void smallAura(Player p, SphereType t) {
        Particle.DustOptions dust = new Particle.DustOptions(colorOf(t), 1.0f);
        Location base = p.getLocation();
        for (int i = 0; i < 6; i++) {
            double ang = (counter * 0.4) + i * Math.PI / 3;
            p.getWorld().spawnParticle(Particle.DUST, base.clone().add(Math.cos(ang) * 0.8, 0.1, Math.sin(ang) * 0.8),
                    1, 0, 0, 0, 0, dust);
        }
    }

    private void fuguAura(Player holder) {
        double r = cfg().getDouble("fugu.radius", 2.0);
        Location base = holder.getLocation();
        Particle.DustOptions dust = new Particle.DustOptions(Color.LIME, 1.2f);
        int points = 24;
        for (int i = 0; i < points; i++) {
            double ang = 2 * Math.PI * i / points;
            double dx = Math.cos(ang) * r;
            double dz = Math.sin(ang) * r;
            holder.getWorld().spawnParticle(Particle.DUST, base.clone().add(dx, 0.15, dz), 1, 0, 0, 0, 0, dust);
            holder.getWorld().spawnParticle(Particle.DUST, base.clone().add(dx, 1.0, dz), 1, 0, 0, 0, 0, dust);
        }
        List<Entity> near = new ArrayList<>(holder.getNearbyEntities(r, 2.0, r));
        for (Entity en : near) {
            if (!(en instanceof LivingEntity le) || en instanceof ArmorStand || en.equals(holder)) continue;
            if (le instanceof Player other
                    && (other.getGameMode() == GameMode.CREATIVE || other.getGameMode() == GameMode.SPECTATOR)) continue;
            double dx = en.getLocation().getX() - base.getX();
            double dz = en.getLocation().getZ() - base.getZ();
            if (dx * dx + dz * dz > r * r) continue;
            le.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, amp("fugu.poison-level")));
            le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 60, amp("fugu.slow-level")));
        }
    }

    private double d(String path, double def) { return cfg().getDouble(path, def); }

    private List<Mod> mods(SphereType t) {
        AttributeModifier.Operation add = AttributeModifier.Operation.ADD_NUMBER;
        return switch (t) {
            case FUGU -> List.of(new Mod(Attribute.ARMOR, "armor", d("fugu.armor-bonus", 2), add));
            case POSEIDON -> List.of(
                    new Mod(Attribute.ARMOR, "armor", d("poseidon.armor-bonus", 4), add),
                    new Mod(Attribute.MAX_HEALTH, "health", -d("poseidon.health-loss", 4), add),
                    new Mod(Attribute.WATER_MOVEMENT_EFFICIENCY, "swim", d("poseidon.swim-speed", 1.0) - 0.0, add));
            case HADES -> List.of(
                    new Mod(Attribute.ARMOR, "armor", -d("hades.armor-loss", 3), add),
                    new Mod(Attribute.ATTACK_DAMAGE, "attack", d("hades.damage-bonus", 2), add));
            case ZEUS -> List.of(
                    new Mod(Attribute.ARMOR, "armor", -d("zeus.armor-loss", 2), add),
                    new Mod(Attribute.ATTACK_SPEED, "speed", d("zeus.attack-speed", 0.4), add));
            case HERMES -> List.of(
                    new Mod(Attribute.MAX_HEALTH, "health", -d("hermes.health-loss", 6), add),
                    new Mod(Attribute.SAFE_FALL_DISTANCE, "fall", d("hermes.safe-fall", 8), add));
            case ATHENA -> List.of(
                    new Mod(Attribute.ATTACK_DAMAGE, "attack", -d("athena.attack-loss", 2), add),
                    new Mod(Attribute.ARMOR_TOUGHNESS, "tough", d("athena.toughness", 4), add),
                    new Mod(Attribute.KNOCKBACK_RESISTANCE, "kb", d("athena.knockback-resist", 0.4), add));
            case ARES -> List.of(
                    new Mod(Attribute.ATTACK_DAMAGE, "attack", d("ares.damage-bonus", 4), add),
                    new Mod(Attribute.ATTACK_SPEED, "speed", d("ares.attack-speed", 0.3), add),
                    new Mod(Attribute.MAX_HEALTH, "health", -d("ares.health-loss", 4), add));
            case APOLLO -> List.of(
                    new Mod(Attribute.ATTACK_DAMAGE, "attack", -d("apollo.attack-loss", 1), add),
                    new Mod(Attribute.LUCK, "luck", d("apollo.luck", 1), add));
            default -> List.of();
        };
    }

    private void applyMods(Player p, SphereType t, boolean active) {
        for (Mod m : mods(t)) {
            AttrUtil.apply(plugin, p, "sphere_" + t.id + "_" + m.name(), m.attribute(), m.amount(), m.op(), active);
        }
    }
}
