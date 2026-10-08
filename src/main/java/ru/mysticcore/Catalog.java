package ru.mysticcore;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Каталог особых предметов: талисманы, кейсы, расходники, уникальные предметы ивента. */
public final class Catalog {

    public enum Cat { TALISMAN, CASE, CONSUMABLE, UNIQUE }

    public record Entry(String id, Cat cat, String name, Material material, long price, List<String> lore) {}

    private final Map<String, Entry> entries = new LinkedHashMap<>();
    private final NamespacedKey key;

    public Catalog(MysticCore plugin) {
        this.key = new NamespacedKey(plugin, "special");

        // --- Талисманы (работают, пока лежат в инвентаре) ---
        add("t_life", Cat.TALISMAN, "&c&lТалисман Жизни", Material.HEART_OF_THE_SEA, 1500,
                "&7Пока в инвентаре:", "&a✔ Здоровье +4 (2 сердца)");
        add("t_power", Cat.TALISMAN, "&6&lТалисман Силы", Material.BLAZE_ROD, 1800,
                "&7Пока в инвентаре:", "&a✔ Урон +2");
        add("t_wind", Cat.TALISMAN, "&f&lТалисман Ветра", Material.FEATHER, 1200,
                "&7Пока в инвентаре:", "&a✔ Скорость движения +10%");
        add("t_vision", Cat.TALISMAN, "&5&lТалисман Зоркости", Material.ENDER_EYE, 1000,
                "&7Пока в инвентаре:", "&a✔ Ночное зрение");
        add("t_luck", Cat.TALISMAN, "&a&lТалисман Удачи", Material.EMERALD, 1400,
                "&7Пока в инвентаре:", "&a✔ Удача +2");
        add("t_guard", Cat.TALISMAN, "&9&lТалисман Стража", Material.SHIELD, 2000,
                "&7Пока в инвентаре:", "&a✔ Броня +3", "&a✔ Сопротивление отбрасыванию +20%");

        // --- Кейсы (ПКМ — открыть) ---
        add("case_common", Cat.CASE, "&7&lОбычный кейс", Material.BARREL, 200,
                "&7ПКМ — открыть.", "&7Монетки, ресурсы, шанс на талисман.");
        add("case_rare", Cat.CASE, "&9&lРедкий кейс", Material.ENDER_CHEST, 700,
                "&7ПКМ — открыть.", "&7Монетки, алмазы, шанс на сферу и талисман.");
        add("case_mythic", Cat.CASE, "&6&lМифический кейс", Material.PURPLE_SHULKER_BOX, 2000,
                "&7ПКМ — открыть.", "&7Много монет, сферы, талисманы,", "&7токены и уникальные предметы.");

        // --- Расходники (ПКМ — использовать) ---
        add("c_heal", Cat.CONSUMABLE, "&c&lЭликсир здоровья", Material.RED_DYE, 250,
                "&7ПКМ: лечит 10 HP и даёт Регенерацию II (5 сек).");
        add("c_speed", Cat.CONSUMABLE, "&b&lЭликсир скорости", Material.LIGHT_BLUE_DYE, 300,
                "&7ПКМ: Скорость III на 30 секунд.");
        add("c_scroll", Cat.CONSUMABLE, "&d&lСвиток телепорта", Material.PAPER, 400,
                "&7ПКМ: случайная телепортация по карте выживания.");

        // --- Уникальные предметы ивента ---
        add("u_blade", Cat.UNIQUE, "&2&lКлинок Мёртвого озера", Material.NETHERITE_SWORD, 12000,
                "&7Уникальный предмет ивента.", "&8Остриё, Огонь, Добыча, Прочность, Починка");
        add("u_crown", Cat.UNIQUE, "&8&lКорона Чёрного рынка", Material.GOLDEN_HELMET, 9000,
                "&7Уникальный предмет ивента.", "&8Защита IV, Подводное дыхание III, Шипы III");
        add("u_trident", Cat.UNIQUE, "&3&lТрезубец Мамы Фугу", Material.TRIDENT, 14000,
                "&7Уникальный предмет ивента.", "&8Верность III, Пронзатель V, Прочность III");
        add("u_pick", Cat.UNIQUE, "&e&lКирка Подпольщика", Material.NETHERITE_PICKAXE, 10000,
                "&7Уникальный предмет ивента.", "&8Эффективность V, Удача III, Прочность III, Починка");
    }

    private void add(String id, Cat cat, String name, Material m, long price, String... lore) {
        entries.put(id, new Entry(id, cat, name, m, price, List.of(lore)));
    }

    public Entry get(String id) { return entries.get(id); }

    public Collection<Entry> all() { return entries.values(); }

    public List<Entry> byCat(Cat c) {
        List<Entry> res = new ArrayList<>();
        for (Entry e : entries.values()) if (e.cat() == c) res.add(e);
        return res;
    }

    public String idOf(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }

    public ItemStack create(String id) {
        return create(id, 1);
    }

    public ItemStack create(String id, int amount) {
        Entry e = entries.get(id);
        if (e == null) return new ItemStack(Material.STONE);
        ItemStack it = U.item(e.material(), e.name(), e.lore());
        it.setAmount(amount);
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, id);
        if (e.cat() == Cat.UNIQUE) {
            switch (id) {
                case "u_blade" -> {
                    meta.addEnchant(Enchantment.SHARPNESS, 6, true);
                    meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
                    meta.addEnchant(Enchantment.LOOTING, 3, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);
                }
                case "u_crown" -> {
                    meta.addEnchant(Enchantment.PROTECTION, 4, true);
                    meta.addEnchant(Enchantment.RESPIRATION, 3, true);
                    meta.addEnchant(Enchantment.THORNS, 3, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);
                }
                case "u_trident" -> {
                    meta.addEnchant(Enchantment.LOYALTY, 3, true);
                    meta.addEnchant(Enchantment.IMPALING, 5, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);
                }
                case "u_pick" -> {
                    meta.addEnchant(Enchantment.EFFICIENCY, 5, true);
                    meta.addEnchant(Enchantment.FORTUNE, 3, true);
                    meta.addEnchant(Enchantment.UNBREAKING, 3, true);
                    meta.addEnchant(Enchantment.MENDING, 1, true);
                }
                default -> {}
            }
        }
        it.setItemMeta(meta);
        return it;
    }
}
