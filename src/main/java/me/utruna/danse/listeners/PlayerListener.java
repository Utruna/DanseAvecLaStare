package me.utruna.danse.listeners;

import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.PlaylistManager;
import me.utruna.danse.managers.StaticDancerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;

/**
 * Écoute les événements joueurs pour stopper automatiquement la danse à la déconnexion
 * et rafraîchir les danseurs statiques à la reconnexion.
 *
 * <p>Au join, {@link me.utruna.danse.managers.StaticDancerManager#refreshForPlayer} est
 * planifié avec un délai de 40 ticks. Les joins rapprochés sont coalesés : si plusieurs
 * joueurs rejoignent dans la même fenêtre de 40 ticks, un seul refresh est exécuté.
 */
public class PlayerListener implements Listener {

    private final DanceManager danceManager;
    private final PlaylistManager playlistManager;
    private final StaticDancerManager staticDancerManager;
    private final Plugin plugin;
    private BukkitTask pendingRefresh = null;

    public PlayerListener(DanceManager danceManager, PlaylistManager playlistManager,
                          StaticDancerManager staticDancerManager, Plugin plugin) {
        this.danceManager = danceManager;
        this.playlistManager = playlistManager;
        this.staticDancerManager = staticDancerManager;
        this.plugin = plugin;
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        if (!((JavaPlugin) plugin).getConfig().getBoolean("staticDancer.refreshOnJoin", false)) return;

        // Coalesce : si plusieurs joueurs rejoignent dans la même fenêtre de 40 ticks,
        // un seul refreshAll est exécuté au lieu d'en empiler un par joueur.
        final Player joiningPlayer = event.getPlayer();
        if (pendingRefresh == null || pendingRefresh.isCancelled()) {
            pendingRefresh = Bukkit.getScheduler().runTaskLater(plugin, () -> {
                staticDancerManager.refreshForPlayer(joiningPlayer);
                pendingRefresh = null;
            }, 40L);
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        // Arrêter la playlist en premier (elle appelle stopDance en interne si active),
        // puis stopDance directement pour couvrir le cas sans playlist.
        playlistManager.stopForPlayer(uuid);
        danceManager.stopDance(uuid);
    }
}
