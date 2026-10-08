package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Display;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.TextDisplay;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Один активный сундук: блок + голограмма + ОБЩИЙ виртуальный инвентарь на 54 слота (лут не дублируется). */
public final class Drop {

    public enum RemoveReason { LOOTED, EXPIRED, ADMIN, SHUTDOWN }

    public static final class Holder implements InventoryHolder {
        private final Drop drop;
        private Inventory inv;

        Holder(Drop drop) { this.drop = drop; }

        public Drop getDrop() { return drop; }

        @Override
        public Inventory getInventory() { return inv; }
    }

    private final MysticCore plugin;
    private final DropManager manager;
    private final int id;
    private final Tier tier;
    private final Location location;
    private final Inventory inventory;

    private TextDisplay hologram;
    private int secondsLeft;
    private int lifeLeft;
    private boolean ready = false;
    private boolean removed = false;

    public Drop(MysticCore plugin, DropManager manager, int id, Tier tier, Location location, Random random) {
        this.plugin = plugin;
        this.manager = manager;
        this.id = id;
        this.tier = tier;
        this.location = location.clone();
        this.secondsLeft = tier.unlockSeconds();
        this.lifeLeft = plugin.getConfig().getInt("drops.lifetime-after-open-seconds", 300);

        Holder holder = new Holder(this);
        this.inventory = Bukkit.createInventory(holder, 54, U.c(tier.name()));
        holder.inv = inventory;

        List<ItemStack> loot = LootTables.roll(plugin, tier.key(), random);
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < 54; i++) slots.add(i);
        Collections.shuffle(slots, random);
        for (int i = 0; i < loot.size() && i < slots.size(); i++) inventory.setItem(slots.get(i), loot.get(i));
    }

    public void place() {
        World w = location.getWorld();
        location.getBlock().setType(tier.block());
        hologram = w.spawn(location.clone().add(0.5, 1.7, 0.5), TextDisplay.class, td -> {
            td.setBillboard(Display.Billboard.CENTER);
            td.setPersistent(false);
            td.setShadowed(true);
            td.setViewRange(2.0f);
            td.setBackgroundColor(Color.fromARGB(0, 0, 0, 0));
        });
        updateHologram();
        w.strikeLightningEffect(location);
        w.playSound(location, Sound.ENTITY_GENERIC_EXPLODE, 2.0f, 0.8f);
        w.spawnParticle(Particle.FIREWORK, location.clone().add(0.5, 1, 0.5), 60, 0.5, 1.0, 0.5, 0.05);
    }

    public void tick() {
        if (removed) return;
        location.getWorld().spawnParticle(Particle.END_ROD, location.clone().add(0.5, 2.5, 0.5), 4, 0.2, 1.0, 0.2, 0.01);
        if (!ready) {
            secondsLeft--;
            if (secondsLeft <= 0) {
                ready = true;
                location.getWorld().playSound(location, Sound.BLOCK_CHEST_OPEN, 2.0f, 1.0f);
                manager.broadcastReady(this);
            }
        } else {
            lifeLeft--;
            if (isEmpty()) { remove(RemoveReason.LOOTED); return; }
            if (lifeLeft <= 0) { remove(RemoveReason.EXPIRED); return; }
        }
        updateHologram();
    }

    private void updateHologram() {
        if (hologram == null || !hologram.isValid()) return;
        String line2 = ready
                ? "&a&lМОЖНО ОТКРЫТЬ! &7(" + U.time(lifeLeft) + ")"
                : "&fОткроется через &e" + U.time(secondsLeft);
        hologram.text(U.c(tier.name() + "\n" + line2));
    }

    public boolean isEmpty() {
        for (ItemStack it : inventory.getContents()) {
            if (it != null && it.getType() != Material.AIR) return false;
        }
        return true;
    }

    public void remove(RemoveReason reason) {
        if (removed) return;
        removed = true;
        for (HumanEntity viewer : new ArrayList<>(inventory.getViewers())) viewer.closeInventory();
        if (hologram != null && hologram.isValid()) hologram.remove();
        Block b = location.getBlock();
        if (b.getType() == tier.block()) b.setType(Material.AIR);
        manager.unregister(this);
        manager.broadcastRemoved(this, reason);
    }

    public boolean isAt(Block block) {
        return block.getWorld().equals(location.getWorld())
                && block.getX() == location.getBlockX()
                && block.getY() == location.getBlockY()
                && block.getZ() == location.getBlockZ();
    }

    public int getId() { return id; }
    public Tier getTier() { return tier; }
    public Location getLocation() { return location.clone(); }
    public Inventory getInventory() { return inventory; }
    public boolean isReady() { return ready; }
    public boolean isRemoved() { return removed; }
    public int getSecondsUntilOpen() { return Math.max(0, secondsLeft); }
}
