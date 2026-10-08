package ru.mysticcore.spheres;

import ru.mysticcore.MysticCore;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Создание предметов-сфер и определение сферы по предмету (через PersistentDataContainer). */
public final class SphereItems {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{([a-zA-Z0-9_.\\-]+)}");

    private final MysticCore plugin;
    public final NamespacedKey key;

    public SphereItems(MysticCore plugin) {
        this.plugin = plugin;
        this.key = new NamespacedKey(plugin, "sphere");
    }

    public static Component c(String legacy) {
        return LegacyComponentSerializer.legacyAmpersand().deserialize(legacy)
                .decoration(TextDecoration.ITALIC, false);
    }

    public SphereType typeOf(ItemStack item) {
        if (item == null || item.getType() != Material.PLAYER_HEAD || !item.hasItemMeta()) return null;
        String id = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        return id == null ? null : SphereType.byId(id);
    }

    public ItemStack create(SphereType type) {
        return create(type, 1, null);
    }

    /** extraLore — дополнительные строки в конец (используется в меню). */
    public ItemStack create(SphereType type, int amount, List<String> extraLore) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD, amount);
        SkullMeta meta = (SkullMeta) item.getItemMeta();

        String tex = plugin.getConfig().getString("textures." + type.name(), "");
        PlayerProfile profile;
        if (tex != null && !tex.isBlank()) {
            profile = Bukkit.createProfile(UUID.nameUUIDFromBytes(("sphere:" + type.id).getBytes(StandardCharsets.UTF_8)), "Sphere");
            profile.setProperty(new ProfileProperty("textures", normalizeTexture(tex.trim())));
        } else {
            profile = Bukkit.createProfile(type.fallbackHead);
        }
        meta.setPlayerProfile(profile);

        meta.displayName(c(type.displayName));
        List<Component> lore = new ArrayList<>();
        for (String line : type.lore) lore.add(c(resolve(line)));
        if (extraLore != null) for (String line : extraLore) lore.add(c(line));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, type.id);
        item.setItemMeta(meta);
        return item;
    }

    /** Обычная голова убитого игрока (без метки сферы!). */
    public ItemStack playerHead(Player owner) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(owner);
        meta.displayName(c("&fГолова &e" + owner.getName()));
        item.setItemMeta(meta);
        return item;
    }

    private String resolve(String line) {
        Matcher m = PLACEHOLDER.matcher(line);
        StringBuilder sb = new StringBuilder();
        while (m.find()) {
            Object v = plugin.getConfig().get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "?" : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    /** 64-символьный hex-хеш -> base64 JSON; иначе считаем, что это уже base64-значение. */
    private static String normalizeTexture(String raw) {
        if (raw.matches("[0-9a-fA-F]{64}")) {
            String json = "{\"textures\":{\"SKIN\":{\"url\":\"http://textures.minecraft.net/texture/" + raw + "\"}}}";
            return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
        }
        return raw;
    }
}
