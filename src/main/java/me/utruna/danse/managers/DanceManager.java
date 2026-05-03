package me.utruna.danse.managers;

import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Level;

public class DanceManager {

    private final DanseAvecLaStare plugin;
    private final Map<UUID, RunningDance> runningDances = new ConcurrentHashMap<>();
    private static final Map<String, DanceStyle> STYLES = new HashMap<>();

    static {
        STYLES.put("twist", new TwistStyle());
        STYLES.put("spin", new SpinStyle());
        STYLES.put("disco", new DiscoStyle());
        STYLES.put("moonwalk", new MoonwalkStyle());
        STYLES.put("wave", new WaveStyle());
        STYLES.put("dj", new DjStyle());
    }

    private static class RunningDance {
        BukkitTask task;
        Dancer dancer;
        boolean previousInvisible;
    }
    public boolean isDancing(UUID uuid) {
        return runningDances.containsKey(uuid);
    }
    public DanceManager(DanseAvecLaStare plugin) {
        this.plugin = plugin;
    }

    public void startDance(Player player, DanceStyle style, boolean hideFromOwner, String targetName) {
        stopDance(player.getUniqueId());

        // Cas 1 : On veut le skin d'un autre joueur (Asynchrone)
        if (targetName != null && !targetName.isBlank()) {
            SkinService.fetchSkin(plugin, targetName.trim(), (profile) -> {
                if (profile == null) {
                    player.sendMessage("§cJoueur introuvable ou erreur Mojang.");
                    return;
                }
                Dancer dancer = new ModelEngineDancer(plugin, resolveModelIdForStyle(style), profile);
                finishDanceSetup(player, dancer, style, hideFromOwner);
            });
            return;
        }

        // Cas 2 : On utilise le skin du joueur actuel (ou pas de skin)
        boolean useModelEngine = Bukkit.getPluginManager().isPluginEnabled("ModelEngine") 
            && plugin.getConfig().getBoolean("useModelEngine", false);

        Dancer dancer;
        if (useModelEngine) {
            // On passe null pour utiliser le skin par défaut/du joueur dans le Dummy
            dancer = new ModelEngineDancer(plugin, resolveModelIdForStyle(style), null);
        } else {
            // Fallback Citizens (à implémenter si besoin)
            player.sendMessage("§cModelEngine n'est pas activé.");
            return;
        }
        
        finishDanceSetup(player, dancer, style, hideFromOwner);
    }

    private void finishDanceSetup(Player player, Dancer dancer, DanceStyle style, boolean hideFromOwner) {
        final UUID uuid = player.getUniqueId();
        final Location origin = player.getLocation().clone();

        try {
            dancer.spawn(origin, player);

            RunningDance running = new RunningDance();
            running.dancer = dancer;
            running.previousInvisible = player.isInvisible();
            player.setInvisible(true);

            running.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
                Player online = Bukkit.getPlayer(uuid);
                if (online == null || !online.isOnline() || online.isDead()) {
                    stopDance(uuid);
                    return;
                }
                dancer.tick(0, style); // Le tick est géré par le style
            }, 0L, 1L);

            runningDances.put(uuid, running);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors du lancement de la danse", ex);
            player.setInvisible(false);
        }
    }

    public void stopDance(UUID uuid) {
        RunningDance running = runningDances.remove(uuid);
        if (running != null) {
            if (running.task != null) running.task.cancel();
            if (running.dancer != null) running.dancer.stop();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.setInvisible(running.previousInvisible);
        }
    }

    private String resolveModelIdForStyle(DanceStyle style) {
        String styleName = style.getName().toLowerCase(Locale.ROOT);
        String byStyle = plugin.getConfig().getString("modelEngine.styleModels." + styleName);
        if (byStyle != null && !byStyle.isBlank()) return byStyle.trim();
        return plugin.getConfig().getString("modelEngine.defaultModelId", "danseur");
    }

    public void stopAll() {
        runningDances.keySet().forEach(this::stopDance);
    }

    public List<String> getStyleNames() {
        return STYLES.keySet().stream().sorted().collect(Collectors.toList());
    }

    public DanceStyle parseStyle(String value) {
        return value == null ? null : STYLES.get(value.toLowerCase(Locale.ROOT));
    }
    
}