package me.utruna.danse.listeners;

import me.utruna.danse.managers.DanceManager;
import me.utruna.danse.managers.PlaylistManager;
import me.utruna.danse.managers.StaticDancerManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.event.EventHandler;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import java.util.UUID;

/**
 * Listens to player events to stop dancing automatically on logout
 * and refresh static dancers on reconnect.
 *
 * <p>On join, {@link me.utruna.danse.managers.StaticDancerManager#refreshForPlayer} is
 * scheduled with a 40 tick delay. Nearby joins are coalesced: if several players join
 * within the same 40 tick window, only one refresh is executed.
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
        // Coalesce: if several players join within the same 40 tick window,
        // only one refreshAll runs instead of one per player.
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
        // Stop the playlist first (it calls stopDance internally if active),
        // then call stopDance directly to cover the case without a playlist.
        playlistManager.stopForPlayer(uuid);
        danceManager.stopDance(uuid);
    }
}
