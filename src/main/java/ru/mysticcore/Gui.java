package ru.mysticcore;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

/** Универсальное меню: каждый слот может иметь действие по клику. */
public final class Gui implements InventoryHolder {
    private final Inventory inv;
    private final Map<Integer, Consumer<InventoryClickEvent>> actions = new HashMap<>();

    public Gui(int rows, String title) {
        this.inv = Bukkit.createInventory(this, rows * 9, U.c(title));
    }

    public Gui fill(Material pane) {
        ItemStack it = U.item(pane, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, it);
        return this;
    }

    public Gui set(int slot, ItemStack item) {
        inv.setItem(slot, item);
        actions.remove(slot);
        return this;
    }

    public Gui set(int slot, ItemStack item, Consumer<InventoryClickEvent> action) {
        inv.setItem(slot, item);
        actions.put(slot, action);
        return this;
    }

    public void clear() {
        inv.clear();
        actions.clear();
    }

    public void open(Player p) {
        p.openInventory(inv);
    }

    public void handle(InventoryClickEvent e) {
        Consumer<InventoryClickEvent> a = actions.get(e.getRawSlot());
        if (a != null) a.accept(e);
    }

    public Inventory getInv() { return inv; }

    @Override
    public Inventory getInventory() { return inv; }
}
