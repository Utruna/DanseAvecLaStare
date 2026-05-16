package me.utruna.danse.managers;

import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitTask;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.logging.Level;

public class DanceManager {

    private final DanseAvecLaStare plugin;
    private final Map<UUID, RunningDance> runningDances = new ConcurrentHashMap<>();
    private final Map<String, DanceStyle> STYLES = new HashMap<>();
    private final Map<String, DanceConfig> danceConfigs = new HashMap<>();

    private static class DanceConfig {
        public final String displayName;
        public final String modelId;
        public final String animationName;
        public final GenericDanceStyle.MovementType movementType;
        public final double rotationSpeed;
        public final double radius;

        public DanceConfig(String displayName, String modelId, String animationName,
                           GenericDanceStyle.MovementType movementType, double rotationSpeed, double radius) {
            this.displayName = displayName;
            this.modelId = modelId;
            this.animationName = animationName;
            this.movementType = movementType;
            this.rotationSpeed = rotationSpeed;
            this.radius = radius;
        }
    }

    private static class RunningDance {
        BukkitTask task;
        Dancer dancer;
        boolean previousInvisible;
        int tickCounter = 0;  // Compteur pour tracker les ticks
    }

    public boolean isDancing(UUID uuid) {
        return runningDances.containsKey(uuid);
    }

    public DanceManager(DanseAvecLaStare plugin) {
        this.plugin = plugin;
        loadDancesFromConfig();
    }

    /**
     * Constructeur utilitaire pour les tests : charge les styles depuis une configuration donnée
     */
    public DanceManager(org.bukkit.configuration.file.FileConfiguration config) {
        this.plugin = null;
        loadDancesFromConfig(config);
    }

    // Les styles sont chargés dynamiquement depuis la configuration `dances`.
    // Si la configuration est vide, aucune danse par défaut n'est ajoutée.

    private void loadDancesFromConfig() {
        org.bukkit.configuration.file.FileConfiguration cfg = plugin == null ? null : plugin.getConfig();
        loadDancesFromConfig(cfg);
    }

    private void loadDancesFromConfig(org.bukkit.configuration.file.FileConfiguration cfg) {
        STYLES.clear();
        danceConfigs.clear();

        if (cfg == null) return;

        org.bukkit.configuration.ConfigurationSection dancesSection = cfg.getConfigurationSection("dances");

        if (dancesSection == null) return;

        for (String key : dancesSection.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection danceSection = dancesSection.getConfigurationSection(key);

            if (danceSection == null) continue;

            try {
                String displayName = danceSection.getString("displayName", key);
                String modelId = danceSection.getString("modelId");
                String animationName = danceSection.getString("animationName");
                String movementTypeStr = danceSection.getString("movementType", "STATIC");
                GenericDanceStyle.MovementType movementType;
                try {
                    movementType = GenericDanceStyle.MovementType.valueOf(movementTypeStr.toUpperCase());
                } catch (IllegalArgumentException e) {
                    if (plugin != null) plugin.getLogger().warning("Type de mouvement invalide pour '" + key + "': " + movementTypeStr + ". Doit être 'static' ou 'dynamic'.");
                    movementType = GenericDanceStyle.MovementType.STATIC;
                }

                boolean isStatic = movementType == GenericDanceStyle.MovementType.STATIC;
                String pattern = isStatic ? "none" : "wave";

                // Le nom interne du style doit être la clé (lowercase) pour la recherche insensible à la casse
                GenericDanceStyle style = new GenericDanceStyle(key.toLowerCase(), isStatic, pattern, 0.0, 0.0);

                STYLES.put(key.toLowerCase(), style);
                danceConfigs.put(key.toLowerCase(), new DanceConfig(displayName, modelId, animationName, movementType, 0.0, 0.0));
            } catch (Exception ex) {
                if (plugin != null) plugin.getLogger().log(Level.WARNING, "Erreur en chargeant la danse '" + key + "'", ex);
            }
        }
    }

    public void startDance(Player player, DanceStyle style, boolean hideFromOwner, String targetName) {
        stopDance(player.getUniqueId());

        String styleName = style.getName().toLowerCase();
        if (plugin.isPlayerDebug(player.getUniqueId())) {
            plugin.getLogger().info("[DEBUG] Lancement danse: " + styleName + " | Skin cible: " + targetName);
        }
        
        DanceConfig config = danceConfigs.get(styleName);
        if (config == null) {
            player.sendMessage("§cErreur: Configuration manquante pour le style '" + style.getName() + "'");
            return;
        }

        if (config.modelId == null || config.modelId.isBlank()) {
            player.sendMessage("§cErreur: le style '" + style.getName() + "' n'a pas de 'modelId' configuré.");
            plugin.getLogger().warning("Refused to start dance '" + styleName + "' because modelId is missing in config.");
            return;
        }

        boolean useModelEngine = Bukkit.getPluginManager().isPluginEnabled("ModelEngine") 
            && plugin.getConfig().getBoolean("useModelEngine", false);

        if (!useModelEngine) {
            player.sendMessage("§cModelEngine n'est pas activé.");
            return;
        }

        // Cas 1 : Skin d'un autre joueur → chercher via SkinService
        if (targetName != null && !targetName.isBlank()) {
            String targetTrimmed = targetName.trim();
            SkinService.fetchSkin(plugin, targetTrimmed, (profile) -> {
                if (profile == null) {
                    player.sendMessage("§cJoueur introuvable ou erreur Mojang: " + targetTrimmed);
                    return;
                }
                if (plugin.isPlayerDebug(player.getUniqueId())) plugin.getLogger().info("[DEBUG] Profil récupéré pour " + targetTrimmed);
                Dancer dancer = new ModelEngineDancer(plugin, config.modelId, config.animationName, profile);
                finishDanceSetup(player, dancer, style, hideFromOwner);
            });
            return;
        }

        // Cas 2 : Skin du joueur actuel → utiliser directement son profil
        @SuppressWarnings("deprecation")
        PlayerProfile currentProfile = player.getPlayerProfile();
        if (plugin.isPlayerDebug(player.getUniqueId())) plugin.getLogger().info("[DEBUG] Profil du joueur actuel: " + player.getName());
        Dancer dancer = new ModelEngineDancer(plugin, config.modelId, config.animationName, currentProfile);
        finishDanceSetup(player, dancer, style, hideFromOwner);
    }

    private void finishDanceSetup(Player player, Dancer dancer, DanceStyle style, boolean hideFromOwner) {
        final UUID uuid = player.getUniqueId();
        final Location origin = player.getLocation().clone();

        try {
            dancer.spawn(origin, player);
            player.sendMessage("§aDanse démarrée!");

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
                running.tickCounter++;
                dancer.tick(running.tickCounter, style);
            }, 0L, 1L);

            runningDances.put(uuid, running);
        } catch (Exception ex) {
            player.sendMessage("§cErreur lors du lancement: " + ex.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Erreur dance", ex);
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