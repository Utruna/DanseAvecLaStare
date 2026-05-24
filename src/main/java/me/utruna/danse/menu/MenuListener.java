package me.utruna.danse.menu;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.InventoryHolder;

public class MenuListener implements Listener {

    private final DanceMenuManager menuManager;

    public MenuListener(DanceMenuManager menuManager) {
        this.menuManager = menuManager;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent e) {
        InventoryHolder holder = e.getInventory().getHolder();
        if (!(holder instanceof DanceMenu menu)) return;

        e.setCancelled(true);
        if (e.getCurrentItem() == null) return;
        if (e.getWhoClicked() instanceof Player player) {
            menu.handleClick(e, player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof DanceMenu)) return;
        if (!(e.getPlayer() instanceof Player player)) return;

        // 1-tick delay: if the player is navigating to another DanceMenu the new
        // inventory is already open before this fires, so we keep the handlers alive.
        Bukkit.getScheduler().runTaskLater(menuManager.getPlugin(), () -> {
            if (!(player.getOpenInventory().getTopInventory().getHolder() instanceof DanceMenu)) {
                menuManager.clearPlayer(player.getUniqueId());
            }
        }, 1L);
    }
}
