package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.PufferFish;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

/** Босс «Мама Фугу» в Мёртвом озере: аура яда, луч, всплеск, миньоны. Награда по урону — фугу, токены, монетки. */
public final class BossManager implements Listener {

    private record Saved(Location loc, BlockData data) {}

    private final MysticCore plugin;
    private final Random random = new Random();
    private final NamespacedKey bossKey;
    private final NamespacedKey minionKey;
    private final NamespacedKey fishKey;
    private final Map<UUID, Double> damage = new HashMap<>();
    private final List<Saved> pond = new ArrayList<>();
    private final Set<UUID> minions = new HashSet<>();

    private PufferFish boss;
    private BossBar bar;
    private BukkitTask spawnTask;
    private BukkitTask tickTask;
    private BukkitTask restoreTask;
    private int ticks;
    private int lifeLeft;

    public BossManager(MysticCore plugin) {
        this.plugin = plugin;
        this.bossKey = new NamespacedKey(plugin, "mama_fugu");
        this.minionKey = new NamespacedKey(plugin, "mama_minion");
        this.fishKey = new NamespacedKey(plugin, "boss_fish");
    }

    public void start() {
        long period = Math.max(1, plugin.getConfig().getInt("boss.spawn-interval-minutes", 7)) * 1200L;
        spawnTask = Bukkit.getScheduler().runTaskTimer(plugin, this::autoSpawn, period, period);
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public void shutdown() {
        if (spawnTask != null) spawnTask.cancel();
        if (tickTask != null) tickTask.cancel();
        if (restoreTask != null) restoreTask.cancel();
        endBoss();
        restorePond();
    }

    public boolean isAlive() { return boss != null && boss.isValid(); }

    private void autoSpawn() {
        if (isAlive() || restoreTask != null) return;
        World w = plugin.survivalWorld();
        if (w == null || w.getPlayers().isEmpty()) return;
        spawn();
    }

    // ---------- появление ----------

    private void change(Block b, Material m) {
        if (b.getType() == m) return;
        pond.add(new Saved(b.getLocation(), b.getBlockData()));
        b.setType(m, false);
    }

    public boolean spawn() {
        if (isAlive() || restoreTask != null) return false;
        Location l = plugin.drops().pickLocation();
        if (l == null) return false;
        World w = l.getWorld();
        int gy = l.getBlockY() - 1;
        int cx = l.getBlockX();
        int cz = l.getBlockZ();
        pond.clear();
        int radius = 5;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                if (dx * dx + dz * dz > radius * radius) continue;
                for (int y = gy; y >= gy - 3; y--) change(w.getBlockAt(cx + dx, y, cz + dz), Material.WATER);
                for (int y = gy + 1; y <= gy + 3; y++) {
                    Block b = w.getBlockAt(cx + dx, y, cz + dz);
                    if (!b.getType().isAir() && b.getType() != Material.WATER) change(b, Material.AIR);
                }
            }
        }
        Location spot = new Location(w, cx + 0.5, gy - 1, cz + 0.5);
        double hp = plugin.getConfig().getDouble("boss.health", 400);
        boss = w.spawn(spot, PufferFish.class, pf -> {
            AttributeInstance mh = pf.getAttribute(Attribute.MAX_HEALTH);
            if (mh != null) mh.setBaseValue(hp);
            pf.setHealth(hp);
            AttributeInstance sc = pf.getAttribute(Attribute.SCALE);
            if (sc != null) sc.setBaseValue(3.0);
            pf.customName(U.c("&2&lМама Фугу"));
            pf.setCustomNameVisible(true);
            pf.setRemoveWhenFarAway(false);
            pf.setPersistent(false);
            pf.setPuffState(2);
            pf.getPersistentDataContainer().set(bossKey, PersistentDataType.BYTE, (byte) 1);
        });
        bar = Bukkit.createBossBar("\u00a72\u00a7lМама Фугу", BarColor.GREEN, BarStyle.SEGMENTED_10);
        damage.clear();
        minions.clear();
        ticks = 0;
        lifeLeft = Math.max(1, plugin.getConfig().getInt("boss.lifetime-minutes", 10)) * 60;
        w.playSound(spot, Sound.ENTITY_PUFFER_FISH_BLOW_UP, 3f, 0.5f);

        Bukkit.broadcast(U.c(""));
        Bukkit.broadcast(U.c("&2&l[МЁРТВОЕ ОЗЕРО] &fПоявилась &a&lМама Фугу&f!"));
        Bukkit.broadcast(U.c("&fКоординаты: &aX: &e" + cx + "&f, &aY: &e" + (gy) + "&f, &aZ: &e" + cz + " &7(" + w.getName() + ")"));
        Bukkit.broadcast(U.c("&cОсторожно: ядовитая аура, луч и всплеск! &7Награда — фугу и токены по урону."));
        Bukkit.broadcast(U.c(""));
        return true;
    }

    // ---------- бой ----------

    private void tick() {
        if (boss == null) return;
        if (!boss.isValid()) {
            endBoss();
            scheduleRestore();
            return;
        }
        ticks++;
        lifeLeft--;
        if (lifeLeft <= 0) {
            Bukkit.broadcast(U.c("&2&l[МЁРТВОЕ ОЗЕРО] &7Мама Фугу скрылась в глубине..."));
            endBoss();
            scheduleRestore();
            return;
        }
        AttributeInstance mh = boss.getAttribute(Attribute.MAX_HEALTH);
        double max = mh == null ? 400 : mh.getValue();
        bar.setProgress(Math.max(0.0, Math.min(1.0, boss.getHealth() / max)));
        for (Player p : boss.getWorld().getPlayers()) {
            if (p.getLocation().distanceSquared(boss.getLocation()) <= 60 * 60) bar.addPlayer(p);
            else bar.removePlayer(p);
        }
        boss.setRemainingAir(boss.getMaximumAir());

        aura();
        if (ticks % 5 == 0) beam();
        if (ticks % 8 == 0) burst();
        if (ticks % 12 == 0) spawnMinions();
    }

    private List<Player> playersWithin(double r) {
        List<Player> res = new ArrayList<>();
        for (Entity en : boss.getNearbyEntities(r, r, r)) {
            if (en instanceof Player p && p.getGameMode() != org.bukkit.GameMode.SPECTATOR
                    && p.getGameMode() != org.bukkit.GameMode.CREATIVE) res.add(p);
        }
        return res;
    }

    private void aura() {
        double r = plugin.getConfig().getDouble("boss.aura-radius", 7);
        for (Player p : playersWithin(r)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 60, 1));
        }
    }

    private void beam() {
        Player target = null;
        double best = -1;
        for (Player p : playersWithin(25)) {
            double d = p.getLocation().distanceSquared(boss.getLocation());
            if (d > best) { best = d; target = p; }
        }
        if (target == null) return;
        Location from = boss.getLocation().add(0, 1.0, 0);
        Location to = target.getEyeLocation();
        Vector dir = to.toVector().subtract(from.toVector());
        double len = dir.length();
        if (len < 0.1) return;
        dir.normalize();
        Particle.DustOptions dust = new Particle.DustOptions(Color.GREEN, 1.4f);
        for (double d = 0; d < len; d += 0.6) {
            boss.getWorld().spawnParticle(Particle.DUST, from.clone().add(dir.clone().multiply(d)), 1, 0, 0, 0, 0, dust);
        }
        target.damage(plugin.getConfig().getDouble("boss.beam-damage", 8), boss);
        target.sendActionBar(U.c("&2☠ Ядовитый луч Мамы Фугу!"));
    }

    private void burst() {
        double r = plugin.getConfig().getDouble("boss.burst-radius", 5);
        Location c = boss.getLocation();
        boss.getWorld().playSound(c, Sound.ENTITY_PUFFER_FISH_BLOW_UP, 2f, 0.6f);
        Particle.DustOptions dust = new Particle.DustOptions(Color.OLIVE, 1.6f);
        for (int i = 0; i < 36; i++) {
            double ang = 2 * Math.PI * i / 36;
            boss.getWorld().spawnParticle(Particle.DUST, c.clone().add(Math.cos(ang) * r, 0.5, Math.sin(ang) * r), 2, 0, 0, 0, 0, dust);
        }
        boss.getWorld().spawnParticle(Particle.EXPLOSION, c, 1);
        double dmg = plugin.getConfig().getDouble("boss.burst-damage", 6);
        for (Player p : playersWithin(r)) {
            p.damage(dmg, boss);
            p.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 100, 2));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 100, 0));
        }
    }

    private void spawnMinions() {
        minions.removeIf(id -> {
            Entity en = Bukkit.getEntity(id);
            return en == null || !en.isValid();
        });
        if (minions.size() >= 6) return;
        for (int i = 0; i < 3; i++) {
            Location l = boss.getLocation().add(random.nextInt(5) - 2, 0, random.nextInt(5) - 2);
            PufferFish m = boss.getWorld().spawn(l, PufferFish.class, f -> {
                AttributeInstance mh = f.getAttribute(Attribute.MAX_HEALTH);
                if (mh != null) mh.setBaseValue(6);
                f.setHealth(6);
                f.customName(U.c("&aМалая фугу"));
                f.setRemoveWhenFarAway(true);
                f.setPersistent(false);
                f.getPersistentDataContainer().set(minionKey, PersistentDataType.BYTE, (byte) 1);
            });
            minions.add(m.getUniqueId());
        }
    }

    // ---------- события ----------

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (boss == null || !e.getEntity().getUniqueId().equals(boss.getUniqueId())) return;
        Player attacker = null;
        if (e.getDamager() instanceof Player p) attacker = p;
        else if (e.getDamager() instanceof Projectile pr && pr.getShooter() instanceof Player p) attacker = p;
        if (attacker != null) {
            damage.merge(attacker.getUniqueId(), e.getFinalDamage(), Double::sum);
            plugin.quests().progress(attacker, QuestManager.Type.BOSS_DAMAGE, (int) Math.round(e.getFinalDamage()));
        }
    }

    @EventHandler
    public void onDeath(EntityDeathEvent e) {
        LivingEntity en = e.getEntity();
        if (boss != null && en.getUniqueId().equals(boss.getUniqueId())) {
            e.getDrops().clear();
            e.setDroppedExp(300);
            reward();
            endBoss();
            scheduleRestore();
        } else if (en.getPersistentDataContainer().has(minionKey, PersistentDataType.BYTE)) {
            e.getDrops().clear();
            e.setDroppedExp(0);
            minions.remove(en.getUniqueId());
        }
    }

    // ---------- награды ----------

    private void reward() {
        List<Map.Entry<UUID, Double>> top = new ArrayList<>(damage.entrySet());
        top.sort(Comparator.comparingDouble((Map.Entry<UUID, Double> en) -> en.getValue()).reversed());
        Bukkit.broadcast(U.c(""));
        Bukkit.broadcast(U.c("&2&l[МЁРТВОЕ ОЗЕРО] &fМама Фугу повержена!"));
        if (top.isEmpty()) {
            Bukkit.broadcast(U.c("&7Никто не нанёс урона — награды нет."));
            return;
        }
        int fishBase = plugin.getConfig().getInt("boss.fish-reward", 12);
        long tokBase = plugin.getConfig().getLong("boss.reward-tokens", 500);
        for (int i = 0; i < top.size(); i++) {
            UUID id = top.get(i).getKey();
            double dmg = top.get(i).getValue();
            int fish = i == 0 ? fishBase : (i == 1 ? Math.max(1, fishBase * 2 / 3) : (i == 2 ? Math.max(1, fishBase / 2) : 3));
            double mult = i == 0 ? 1.0 : (i == 1 ? 0.6 : (i == 2 ? 0.4 : 0.2));
            long tokens = Math.round(tokBase * mult);
            plugin.getEconomy().addTokens(id, tokens);
            plugin.getEconomy().addCoins(id, tokens / 2);
            Player p = Bukkit.getPlayer(id);
            String name = p != null ? p.getName() : String.valueOf(Bukkit.getOfflinePlayer(id).getName());
            if (p != null) {
                plugin.quests().progress(p, QuestManager.Type.BOSS_KILL, 1);
                plugin.season().addXp(p, 100);
                U.give(p, fish(fish));
                U.msg(p, "&2[МЁРТВОЕ ОЗЕРО] &fВаше место: &e#" + (i + 1) + "&f. Награда: &a" + fish
                        + " фугу&f, &d+" + tokens + " токенов&f, &e+" + (tokens / 2) + " монеток&f.");
            }
            if (i < 3) {
                Bukkit.broadcast(U.c("&e#" + (i + 1) + " &f" + name + " &7— &c" + Math.round(dmg) + " урона &7(+" + fish
                        + " фугу, +" + tokens + " токенов)"));
            }
        }
        Bukkit.broadcast(U.c("&7Обменять фугу на токены можно у &2Фугафага &7в лобби или через /menu."));
        Bukkit.broadcast(U.c(""));
    }

    // ---------- завершение ----------

    private void endBoss() {
        if (bar != null) {
            bar.removeAll();
            bar = null;
        }
        for (UUID id : new ArrayList<>(minions)) {
            Entity en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        minions.clear();
        if (boss != null && boss.isValid()) boss.remove();
        boss = null;
        damage.clear();
    }

    private void scheduleRestore() {
        if (restoreTask != null) return;
        restoreTask = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            restorePond();
            restoreTask = null;
        }, 20L * 30);
    }

    private void restorePond() {
        for (int i = pond.size() - 1; i >= 0; i--) {
            Saved s = pond.get(i);
            s.loc().getBlock().setBlockData(s.data(), false);
        }
        pond.clear();
    }

    public void despawn() {
        endBoss();
        scheduleRestore();
    }

    // ---------- фугу и обмен ----------

    public ItemStack fish(int amount) {
        ItemStack it = U.item(Material.PUFFERFISH, "&a&lФугу Мёртвого озера",
                "&7Добыча с Мамы Фугу.", "&7Сдай Фугафагу: 5 штук = токены.");
        it.setAmount(Math.max(1, Math.min(64, amount)));
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(fishKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(meta);
        return it;
    }

    private boolean isFish(ItemStack it) {
        return it != null && it.getType() == Material.PUFFERFISH && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(fishKey, PersistentDataType.BYTE);
    }

    public void trade(Player p) {
        int total = 0;
        for (ItemStack it : p.getInventory().getContents()) if (isFish(it)) total += it.getAmount();
        int sets = total / 5;
        if (sets <= 0) {
            U.msg(p, "&cНужно минимум &f5 фугу &cс Мамы Фугу. У вас: &f" + total);
            return;
        }
        int toRemove = sets * 5;
        ItemStack[] contents = p.getInventory().getContents();
        for (int i = 0; i < contents.length && toRemove > 0; i++) {
            ItemStack it = contents[i];
            if (!isFish(it)) continue;
            int take = Math.min(it.getAmount(), toRemove);
            toRemove -= take;
            if (take >= it.getAmount()) p.getInventory().setItem(i, null);
            else it.setAmount(it.getAmount() - take);
        }
        long per = plugin.getConfig().getLong("trade.tokens-per-five", 250);
        plugin.getEconomy().addTokens(p.getUniqueId(), per * sets);
        U.msg(p, "&aОбмен: &f" + (sets * 5) + " фугу &a→ &d+" + (per * sets) + " токенов&a. Баланс: &d"
                + U.money(plugin.getEconomy().tokens(p.getUniqueId())));
    }
}
