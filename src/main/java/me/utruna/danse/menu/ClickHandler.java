package me.utruna.danse.menu;

import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;

@FunctionalInterface
public interface ClickHandler {
    void onClick(Player player, ClickType clickType);
}
