package me.utruna.danse.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.InventoryHolder;

public interface DanceMenu extends InventoryHolder {
    void handleClick(InventoryClickEvent e, Player player);
}
