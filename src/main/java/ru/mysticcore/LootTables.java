package ru.mysticcore;

import org.bukkit.Material;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import ru.mysticcore.spheres.SphereType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Лут сундуков по уровням: poor < solid < rich < elite < crusher.
 * Элитры, незеритовая броня и вещи Крушителя — только в самых редких сундуках и с малым шансом.
 */
public final class LootTables {

    private record E(Material m, int min, int max, int w) {}

    private static final Map<String, List<E>> T = new HashMap<>();
    private static final Map<String, int[]> COUNT = new HashMap<>();

    private static E e(Material m, int min, int max, int w) { return new E(m, min, max, w); }

    static {
        COUNT.put("poor", new int[]{6, 10});
        COUNT.put("solid", new int[]{9, 14});
        COUNT.put("rich", new int[]{12, 18});
        COUNT.put("elite", new int[]{14, 20});
        COUNT.put("crusher", new int[]{16, 24});

        T.put("poor", List.of(
                e(Material.BREAD, 8, 16, 20), e(Material.COOKED_BEEF, 6, 12, 20), e(Material.IRON_INGOT, 4, 10, 16),
                e(Material.COAL, 8, 20, 14), e(Material.GOLD_INGOT, 2, 6, 10), e(Material.ARROW, 16, 32, 10),
                e(Material.EXPERIENCE_BOTTLE, 3, 8, 10), e(Material.GOLDEN_CARROT, 4, 8, 8),
                e(Material.IRON_SWORD, 1, 1, 4), e(Material.IRON_CHESTPLATE, 1, 1, 3), e(Material.DIAMOND, 1, 2, 3)));

        T.put("solid", List.of(
                e(Material.COOKED_BEEF, 16, 32, 14), e(Material.IRON_INGOT, 8, 16, 16), e(Material.GOLD_INGOT, 6, 12, 12),
                e(Material.DIAMOND, 1, 4, 10), e(Material.GOLDEN_APPLE, 1, 3, 10), e(Material.EXPERIENCE_BOTTLE, 8, 16, 10),
                e(Material.ENDER_PEARL, 2, 5, 8), e(Material.EMERALD, 4, 12, 8), e(Material.OBSIDIAN, 4, 8, 6),
                e(Material.DIAMOND_SWORD, 1, 1, 3), e(Material.DIAMOND_PICKAXE, 1, 1, 3),
                e(Material.DIAMOND_HELMET, 1, 1, 2), e(Material.DIAMOND_BOOTS, 1, 1, 2)));

        T.put("rich", List.of(
                e(Material.DIAMOND, 4, 10, 14), e(Material.EMERALD, 8, 20, 12), e(Material.GOLDEN_APPLE, 3, 6, 12),
                e(Material.EXPERIENCE_BOTTLE, 16, 32, 10), e(Material.ENDER_PEARL, 4, 8, 8), e(Material.OBSIDIAN, 8, 16, 6),
                e(Material.DIAMOND_BLOCK, 1, 2, 4), e(Material.ANCIENT_DEBRIS, 1, 3, 4),
                e(Material.TOTEM_OF_UNDYING, 1, 1, 3), e(Material.ENCHANTED_GOLDEN_APPLE, 1, 1, 3),
                e(Material.DIAMOND_CHESTPLATE, 1, 1, 3), e(Material.DIAMOND_LEGGINGS, 1, 1, 3),
                e(Material.DIAMOND_SWORD, 1, 1, 3), e(Material.NETHERITE_INGOT, 1, 1, 2)));

        T.put("elite", List.of(
                e(Material.DIAMOND, 6, 14, 14), e(Material.EMERALD_BLOCK, 1, 3, 8), e(Material.DIAMOND_BLOCK, 1, 3, 8),
                e(Material.EXPERIENCE_BOTTLE, 24, 48, 10), e(Material.ENDER_PEARL, 6, 12, 8),
                e(Material.ANCIENT_DEBRIS, 2, 4, 8), e(Material.GOLDEN_APPLE, 4, 10, 8),
                e(Material.ENCHANTED_GOLDEN_APPLE, 1, 3, 6), e(Material.TOTEM_OF_UNDYING, 1, 2, 5),
                e(Material.NETHERITE_SCRAP, 1, 4, 5), e(Material.NETHERITE_INGOT, 1, 2, 4)));

        T.put("crusher", List.of(
                e(Material.DIAMOND, 8, 16, 14), e(Material.EMERALD_BLOCK, 1, 3, 8), e(Material.DIAMOND_BLOCK, 2, 4, 8),
                e(Material.EXPERIENCE_BOTTLE, 32, 64, 10), e(Material.ENDER_PEARL, 8, 16, 8),
                e(Material.ANCIENT_DEBRIS, 2, 5, 8), e(Material.NETHERITE_SCRAP, 2, 5, 6),
                e(Material.ENCHANTED_GOLDEN_APPLE, 2, 4, 7), e(Material.TOTEM_OF_UNDYING, 1, 2, 6),
                e(Material.NETHERITE_INGOT, 1, 3, 5), e(Material.GOLDEN_APPLE, 6, 12, 8)));
    }

    private LootTables() {}

    private static boolean chance(Random r, double percent) { return r.nextDouble() * 100.0 < percent; }

    public static ItemStack crusherGear(Material m, String ruName, Random r) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(U.c("&c&l" + ruName + " Крушителя"));
        String n = m.name();
        if (n.endsWith("_SWORD")) {
            meta.addEnchant(Enchantment.SHARPNESS, 5, true);
            meta.addEnchant(Enchantment.FIRE_ASPECT, 2, true);
            meta.addEnchant(Enchantment.LOOTING, 3, true);
        } else {
            meta.addEnchant(Enchantment.PROTECTION, 4, true);
        }
        meta.addEnchant(Enchantment.UNBREAKING, 3, true);
        meta.addEnchant(Enchantment.MENDING, 1, true);
        it.setItemMeta(meta);
        return it;
    }

    private static void randomGear(List<ItemStack> out, Random r) {
        Material[] gear = {Material.NETHERITE_SWORD, Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
        String[] names = {"Меч", "Шлем", "Нагрудник", "Поножи", "Ботинки"};
        int i = r.nextInt(gear.length);
        out.add(crusherGear(gear[i], names[i], r));
    }

    private static void randomSphere(MysticCore plugin, List<ItemStack> out, Random r, boolean allowEmpty) {
        SphereType[] all = SphereType.values();
        SphereType t;
        do {
            t = all[r.nextInt(all.length)];
        } while (!allowEmpty && t == SphereType.EMPTY);
        out.add(plugin.getSphereItems().create(t));
    }

    private static void randomTalisman(MysticCore plugin, List<ItemStack> out, Random r) {
        var list = plugin.getCatalog().byCat(Catalog.Cat.TALISMAN);
        out.add(plugin.getCatalog().create(list.get(r.nextInt(list.size())).id()));
    }

    public static List<ItemStack> roll(MysticCore plugin, String tier, Random r) {
        List<ItemStack> result = new ArrayList<>();
        List<E> table = T.getOrDefault(tier, T.get("poor"));
        int[] range = COUNT.getOrDefault(tier, COUNT.get("poor"));
        int count = range[0] + r.nextInt(range[1] - range[0] + 1);

        int total = 0;
        for (E en : table) total += en.w();
        for (int i = 0; i < count; i++) {
            int x = r.nextInt(total);
            for (E en : table) {
                x -= en.w();
                if (x < 0) {
                    ItemStack st = new ItemStack(en.m());
                    int amount = en.min() + r.nextInt(en.max() - en.min() + 1);
                    st.setAmount(Math.max(1, Math.min(amount, st.getMaxStackSize())));
                    result.add(st);
                    break;
                }
            }
        }

        switch (tier) {
            case "solid" -> {
                if (chance(r, 12)) result.add(plugin.getSphereItems().create(SphereType.EMPTY));
                if (chance(8)) result.add(new ItemStack(Material.TOTEM_OF_UNDYING));
                if (chance(r, 6)) randomTalisman(plugin, result, r);
            }
            case "rich" -> {
                if (chance(r, 25)) result.add(plugin.getSphereItems().create(SphereType.EMPTY));
                if (chance(r, 6)) randomSphere(plugin, result, r, false);
                if (chance(r, 15)) randomTalisman(plugin, result, r);
                if (chance(r, 2)) randomGear(result, r);
            }
            case "elite" -> {
                result.add(new ItemStack(Material.DIAMOND_BLOCK, 2));
                if (chance(r, 35)) result.add(plugin.getSphereItems().create(SphereType.EMPTY));
                if (chance(r, 15)) randomSphere(plugin, result, r, false);
                if (chance(r, 25)) randomTalisman(plugin, result, r);
                if (chance(r, 5)) randomGear(result, r);
            }
            case "crusher" -> {
                if (chance(r, 50)) result.add(plugin.getSphereItems().create(SphereType.EMPTY));
                if (chance(r, 30)) randomSphere(plugin, result, r, false);
                if (chance(r, 40)) randomTalisman(plugin, result, r);
                if (chance(r, 4)) result.add(new ItemStack(Material.ELYTRA));
                for (Material m : new Material[]{Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                        Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS}) {
                    if (chance(r, 3)) result.add(new ItemStack(m));
                }
                if (chance(r, 10)) randomGear(result, r);
            }
            default -> {}
        }
        return result;
    }

    private static boolean chance(double percent) { return new Random().nextDouble() * 100.0 < percent; }
}
