package me.utruna.danse.listeners;

import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.StaticDancerManager;
import org.bukkit.Bukkit;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;

/**
 * Écoute les événements joueurs pour stopper automatiquement la danse à la déconnexion
 * et rafraîchir les danseurs statiques à la reconnexion.
 */
public class PlayerListener implements Listener {

    private final DanceManager danceManager;
    private final StaticDancerManager staticDancerManager;
    private final Plugin plugin;

    public PlayerListener(DanceManager danceManager, StaticDancerManager staticDancerManager, Plugin plugin) {
        this.danceManager = danceManager;
        this.staticDancerManager = staticDancerManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        event.getPlayer().sendMessage("§eUtilise §f/danse list §epour voir les styles de danse.");
        // Délai de 40 ticks (2s) pour laisser le client charger le monde avant de recevoir les packets de spawn.
        Bukkit.getScheduler().runTaskLater(plugin, staticDancerManager::refreshAll, 40L);
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        danceManager.stopDance(event.getPlayer().getUniqueId());
    }
}
