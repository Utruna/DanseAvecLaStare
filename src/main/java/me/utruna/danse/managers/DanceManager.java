package me.utruna.danse.managers;

import me.utruna.danse.DanseAvecLaStare;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import java.util.logging.Level;
import java.io.StringWriter;
import java.io.PrintWriter;

public class DanceManager {

    private final DanseAvecLaStare plugin;

    private static class RunningDance {
        BukkitTask task;
        Dancer dancer;
        boolean previousInvisible;
    }

    private final Map<UUID, RunningDance> runningDances = new ConcurrentHashMap<>();

    private static final Map<String, DanceStyle> STYLES = new HashMap<>();
    static {
        STYLES.put("twist", new TwistStyle());
        STYLES.put("spin", new SpinStyle());
        STYLES.put("disco", new DiscoStyle());
        STYLES.put("moonwalk", new MoonwalkStyle());
        STYLES.put("wave", new WaveStyle());
    }

    public DanceManager(DanseAvecLaStare plugin) {
        this.plugin = plugin;
    }

    public boolean isDancing(UUID uuid) {
        return runningDances.containsKey(uuid);
    }

    public String getStylesLabel() {
        return STYLES.keySet().stream().sorted().collect(Collectors.joining(", "));
    }

    public List<String> getStyleNames() {
        List<String> list = new ArrayList<>(STYLES.keySet());
        list.sort(String::compareTo);
        return list;
    }

    public DanceStyle parseStyle(String value) {
        if (value == null) return null;
        return STYLES.get(value.toLowerCase(Locale.ROOT));
    }

    public void startDance(Player player, DanceStyle style) {
        startDance(player, style, true);
    }

    public void startDance(Player player, DanceStyle style, boolean hideFromOwner) {
        stopDance(player.getUniqueId());

        if (style == null) {
            player.sendMessage("§cStyle de danse invalide.");
            return;
        }

        final UUID uuid = player.getUniqueId();
        final Location origin = player.getLocation().clone();

        boolean useModelEngine = Bukkit.getPluginManager().isPluginEnabled("ModelEngine") 
            && plugin.getConfig().getBoolean("useModelEngine", false);

        Dancer dancer;
        if (useModelEngine) {
            String modelId = resolveModelIdForStyle(style);
            dancer = new ModelEngineDancer(plugin, modelId);
        } else {
            if (!Bukkit.getPluginManager().isPluginEnabled("Citizens") || !CitizensAPI.hasImplementation()) {
                player.sendMessage("§cCitizens est requis pour la danse avec skin complet.");
                return;
            }
            CitizensDancer citizensDancer = new CitizensDancer(plugin);
            citizensDancer.setHiddenFromOwner(hideFromOwner);
            dancer = citizensDancer;
        }

        try {
            dancer.spawn(origin, player);

            RunningDance running = new RunningDance();
            running.dancer = dancer;
            running.previousInvisible = player.isInvisible();

            // On rend le vrai joueur invisible
            player.setInvisible(true);

            BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
                private int tick = 0;

                @Override
                public void run() {
                    // 1. Vérification de sécurité : Si le joueur quitte ou meurt, on arrête tout
                    Player online = Bukkit.getPlayer(uuid);
                    if (online == null || !online.isOnline() || online.isDead()) {
                        stopDance(uuid);
                        return;
                    }

                    // 2. Animation via la stratégie
                    dancer.tick(tick, style);
                    tick++;
                }
            }, 0L, 1L); // 1L pour une fluidité de 20 images par seconde

            running.task = task;
            runningDances.put(uuid, running);
        } catch (Exception ex) {
            plugin.getLogger().log(Level.SEVERE, "Erreur lors de la création du NPC pour la danse", ex);
            StringWriter sw = new StringWriter();
            ex.printStackTrace(new PrintWriter(sw));
            plugin.getLogger().severe(sw.toString());
            try {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) p.setInvisible(false);
            } catch (Exception ignore) {}
        }
    }

    public void stopDance(UUID uuid) {
        RunningDance running = runningDances.remove(uuid);
        if (running != null) {
            if (running.task != null) running.task.cancel();
            if (running.dancer != null) {
                running.dancer.stop();
            }

            Player p = Bukkit.getPlayer(uuid);
            if (p != null && p.isOnline()) {
                p.setInvisible(running.previousInvisible);
            }
        }
    }

    public void stopAll() {
        for (UUID uuid : runningDances.keySet()) {
            stopDance(uuid);
        }
    }

    private String resolveModelIdForStyle(DanceStyle style) {
        String styleName = style.getName().toLowerCase(Locale.ROOT);

        String byStyle = plugin.getConfig().getString("modelEngine.styleModels." + styleName);
        if (byStyle != null && !byStyle.isBlank()) {
            return byStyle.trim();
        }

        String defaultModel = plugin.getConfig().getString("modelEngine.defaultModelId");
        if (defaultModel != null && !defaultModel.isBlank()) {
            return defaultModel.trim();
        }

        // Backward compatibility for old config key.
        String legacy = plugin.getConfig().getString("modelEngine.modelId");
        if (legacy != null && !legacy.isBlank()) {
            return legacy.trim();
        }

        return "danseur";
    }

    // Styles are implemented in dedicated classes under the same package.
}