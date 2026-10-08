package ru.mysticcore;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class DropListener implements Listener {

    private final DropManager manager;

    public DropListener(DropManager manager) {
        this.manager = manager;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) return;
        Block block = e.getClickedBlock();
        if (block == null) return;
        Drop drop = manager.getByBlock(block);
        if (drop == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (!drop.isReady()) {
            U.msg(p, "&cСундук откроется через &f" + U.time(drop.getSecondsUntilOpen()));
            return;
        }
        p.openInventory(drop.getInventory());
        MysticCore.getInstance().quests().progress(p, QuestManager.Type.OPEN_DROP, 1);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (manager.getByBlock(e.getBlock()) != null) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> manager.getByBlock(b) != null);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> manager.getByBlock(b) != null);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof Drop.Holder h) {
            Drop d = h.getDrop();
            if (!d.isRemoved() && d.isReady() && d.isEmpty()) d.remove(Drop.RemoveReason.LOOTED);
        }
    }
}
