package me.utruna.danse.listeners;

import me.utruna.danse.managers.DanceManager;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

/**
 * Écoute les événements joueurs pour stopper automatiquement la danse à la déconnexion.
 */
public class PlayerListener implements Listener {

    private final DanceManager danceManager;

    public PlayerListener(DanceManager danceManager) {
        this.danceManager = danceManager;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage("§eUtilise §f/danse list §epour voir les styles de danse.");
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        danceManager.stopDance(event.getPlayer().getUniqueId());
    }
}