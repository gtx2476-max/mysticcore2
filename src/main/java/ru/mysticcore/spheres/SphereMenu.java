package ru.mysticcore.spheres;

import ru.mysticcore.MysticCore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Меню /spheres: список сфер -> клик -> экран с рецептом крафта. */
public final class SphereMenu implements Listener {

    /** type == null -> главное меню; иначе экран рецепта этой сферы. */
    public static final class MenuHolder implements InventoryHolder {
        private final SphereType type;
        private Inventory inventory;

        MenuHolder(SphereType type) { this.type = type; }

        public SphereType getType() { return type; }

        @Override
        public Inventory getInventory() { return inventory; }
    }

    private static final int[] GRID = {11, 12, 13, 20, 21, 22, 29, 30, 31};
    private static final int RESULT_SLOT = 25;
    private static final int ARROW_SLOT = 23;
    private static final int BACK_SLOT = 40;
    private static final int[] MAIN_SLOTS = {9, 10, 11, 12, 13, 14, 15, 16, 17};

    private final MysticCore plugin;
    private final SphereItems items;

    public SphereMenu(MysticCore plugin, SphereItems items) {
        this.plugin = plugin;
        this.items = items;
    }

    private ItemStack named(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.displayName(SphereItems.c(name));
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack pane = named(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    public void openMain(Player p) {
        MenuHolder holder = new MenuHolder(null);
        Inventory inv = Bukkit.createInventory(holder, 27, SphereItems.c("&8✦ Сферы ✦"));
        holder.inventory = inv;
        fill(inv);
        SphereType[] all = SphereType.values();
        for (int i = 0; i < all.length && i < MAIN_SLOTS.length; i++) {
            inv.setItem(MAIN_SLOTS[i], items.create(all[i], 1,
                    List.of("", "&e▶ Нажми, чтобы увидеть рецепт крафта")));
        }
        p.openInventory(inv);
    }

    public void openRecipe(Player p, SphereType type) {
        MenuHolder holder = new MenuHolder(type);
        Inventory inv = Bukkit.createInventory(holder, 45,
                SphereItems.c("&8Рецепт: " + type.displayName));
        holder.inventory = inv;
        fill(inv);

        for (int i = 0; i < 9; i++) {
            int r = i / 3, c = i % 3;
            ItemStack ing;
            if (type == SphereType.EMPTY) {
                ing = named(Material.PLAYER_HEAD, "&fГолова любого игрока");
            } else if (r == 1 && c == 1) {
                ing = items.create(SphereType.EMPTY);
            } else if ((r + c) % 2 == 0) {
                ing = new ItemStack(type.corner);
            } else {
                ing = new ItemStack(type.edge);
            }
            inv.setItem(GRID[i], ing);
        }
        inv.setItem(ARROW_SLOT, named(Material.ARROW, "&7→"));
        inv.setItem(RESULT_SLOT, items.create(type));
        inv.setItem(BACK_SLOT, named(Material.BARRIER, "&cНазад"));
        p.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof MenuHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null || e.getClickedInventory() != e.getView().getTopInventory()) return;

        if (holder.getType() == null) {
            SphereType t = items.typeOf(e.getCurrentItem());
            if (t != null) openRecipe(p, t);
        } else if (e.getRawSlot() == BACK_SLOT) {
            openMain(p);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof MenuHolder) e.setCancelled(true);
    }
}
