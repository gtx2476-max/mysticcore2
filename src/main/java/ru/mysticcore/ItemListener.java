package ru.mysticcore;

import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import ru.mysticcore.spheres.SphereType;

import java.util.Random;

/** Открытие кейсов и использование расходников (ПКМ). */
public final class ItemListener implements Listener {

    private final MysticCore plugin;
    private final Random random = new Random();

    public ItemListener(MysticCore plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onUse(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack item = e.getItem();
        String id = plugin.getCatalog().idOf(item);
        if (id == null) return;
        Catalog.Entry en = plugin.getCatalog().get(id);
        if (en == null) return;

        if (en.cat() == Catalog.Cat.CASE) {
            e.setCancelled(true);
            item.setAmount(item.getAmount() - 1);
            openCase(e.getPlayer(), id);
        } else if (en.cat() == Catalog.Cat.CONSUMABLE) {
            e.setCancelled(true);
            if (use(e.getPlayer(), id)) item.setAmount(item.getAmount() - 1);
        }
    }

    private boolean use(Player p, String id) {
        switch (id) {
            case "c_heal" -> {
                var attr = p.getAttribute(Attribute.MAX_HEALTH);
                double max = attr == null ? 20 : attr.getValue();
                p.setHealth(Math.min(max, p.getHealth() + 10));
                p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1));
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1f);
                return true;
            }
            case "c_speed" -> {
                p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 2));
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_DRINK, 1f, 1.3f);
                return true;
            }
            case "c_scroll" -> {
                return plugin.getRtp().teleport(p, true);
            }
            default -> {
                return false;
            }
        }
    }

    private int rnd(int min, int max) { return min + random.nextInt(max - min + 1); }

    private boolean chance(double percent) { return random.nextDouble() * 100.0 < percent; }

    private void randomSphere(Player p, boolean allowEmpty) {
        SphereType[] all = SphereType.values();
        SphereType t;
        do {
            t = all[random.nextInt(all.length)];
        } while (!allowEmpty && t == SphereType.EMPTY);
        U.give(p, plugin.getSphereItems().create(t));
        U.msg(p, "&7  ▸ &f" + t.displayName);
    }

    private void randomTalisman(Player p) {
        var list = plugin.getCatalog().byCat(Catalog.Cat.TALISMAN);
        Catalog.Entry en = list.get(random.nextInt(list.size()));
        U.give(p, plugin.getCatalog().create(en.id()));
        U.msg(p, "&7  ▸ &f" + en.name());
    }

    private void openCase(Player p, String id) {
        plugin.quests().progress(p, QuestManager.Type.OPEN_CASE, 1);
        U.msg(p, "&8&m          &r &6&lКейс открыт! &8&m          ");
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
        switch (id) {
            case "case_common" -> {
                giveCoins(p, rnd(50, 200));
                U.give(p, new ItemStack(Material.IRON_INGOT, rnd(8, 16)));
                U.msg(p, "&7  ▸ &fЖелезо");
                if (chance(30)) { U.give(p, new ItemStack(Material.GOLD_INGOT, rnd(3, 8))); U.msg(p, "&7  ▸ &fЗолото"); }
                if (chance(5)) randomTalisman(p);
                if (chance(2)) U.give(p, plugin.getSphereItems().create(SphereType.EMPTY));
            }
            case "case_rare" -> {
                giveCoins(p, rnd(250, 700));
                U.give(p, new ItemStack(Material.DIAMOND, rnd(2, 5)));
                U.msg(p, "&7  ▸ &fАлмазы");
                if (chance(10)) { U.give(p, plugin.getSphereItems().create(SphereType.EMPTY)); U.msg(p, "&7  ▸ &fПустая сфера"); }
                if (chance(8)) randomTalisman(p);
                if (chance(5)) { U.give(p, new ItemStack(Material.TOTEM_OF_UNDYING)); U.msg(p, "&7  ▸ &fТотем бессмертия"); }
            }
            case "case_mythic" -> {
                giveCoins(p, rnd(1000, 3000));
                U.give(p, new ItemStack(Material.DIAMOND, rnd(5, 12)));
                U.give(p, new ItemStack(Material.ENCHANTED_GOLDEN_APPLE, rnd(1, 3)));
                U.msg(p, "&7  ▸ &fАлмазы и зачарованные яблоки");
                if (chance(18)) randomSphere(p, false);
                if (chance(25)) randomTalisman(p);
                if (chance(10)) {
                    long t = rnd(50, 200);
                    plugin.getEconomy().addTokens(p.getUniqueId(), t);
                    U.msg(p, "&7  ▸ &d" + t + " токенов");
                }
                if (chance(4)) {
                    var list = plugin.getCatalog().byCat(Catalog.Cat.UNIQUE);
                    Catalog.Entry en = list.get(random.nextInt(list.size()));
                    U.give(p, plugin.getCatalog().create(en.id()));
                    U.msg(p, "&7  ▸ &6&lУНИКАЛЬНО: &f" + en.name());
                }
            }
            default -> {}
        }
    }

    private void giveCoins(Player p, long amount) {
        plugin.getEconomy().addCoins(p.getUniqueId(), amount);
        U.msg(p, "&7  ▸ &e" + U.money(amount) + " монеток");
    }
}
