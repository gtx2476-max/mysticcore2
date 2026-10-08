package ru.mysticcore;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class GuiListener implements Listener {

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof Gui gui)) return;
        e.setCancelled(true);
        if (e.getClickedInventory() != null && e.getClickedInventory() == e.getView().getTopInventory()) {
            gui.handle(e);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Gui) e.setCancelled(true);
    }
}
