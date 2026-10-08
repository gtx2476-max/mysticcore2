package ru.mysticcore;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Мелкие утилиты: цвета, предметы, время, деньги. */
public final class U {
    private U() {}

    public static Component c(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand()
                .deserialize(legacy.replace('\u00a7', '&'))
                .decoration(TextDecoration.ITALIC, false);
    }

    public static ItemStack item(Material m, String name, String... lore) {
        return item(m, name, Arrays.asList(lore));
    }

    public static ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(c(name));
        List<Component> l = new ArrayList<>();
        for (String s : lore) l.add(c(s));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    /** Копия предмета с добавленными строками лора в конец. */
    public static ItemStack withLore(ItemStack base, String... extra) {
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        List<Component> l = meta.hasLore() && meta.lore() != null ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        for (String s : extra) l.add(c(s));
        meta.lore(l);
        it.setItemMeta(meta);
        return it;
    }

    public static String time(long seconds) {
        seconds = Math.max(0, seconds);
        return String.format("%02d:%02d", seconds / 60, seconds % 60);
    }

    public static String money(long n) {
        return String.format(Locale.US, "%,d", n).replace(',', ' ');
    }

    public static String f(String template, Object... kv) {
        String s = template;
        for (int i = 0; i + 1 < kv.length; i += 2) {
            s = s.replace("{" + kv[i] + "}", String.valueOf(kv[i + 1]));
        }
        return s;
    }

    public static void give(Player p, ItemStack it) {
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack r : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), r);
    }

    public static void msg(Player p, String legacy) {
        p.sendMessage(c(legacy));
    }

    /** Название предмета на языке клиента игрока. */
    public static Component mat(Material m) {
        return Component.translatable(m.translationKey());
    }
}
