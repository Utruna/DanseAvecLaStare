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
    private final Map<String, DanceStyle> STYLES = new HashMap<>();
    private final Map<String, DanceConfig> danceConfigs = new HashMap<>();  // Cache des configs

    /**
     * Configuration d'une danse, lue depuis config.yml
     */
    private static class DanceConfig {
        String displayName;
        String modelId;
        String animationName;
        GenericDanceStyle.MovementType movementType;
        double rotationSpeed;
        double radius;

        DanceConfig(String displayName, String modelId, String animationName, 
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
    }

    public boolean isDancing(UUID uuid) {
        return runningDances.containsKey(uuid);
    }

    public DanceManager(DanseAvecLaStare plugin) {
        this.plugin = plugin;
        loadDancesFromConfig();
        // Fallback : si aucun style n'a été chargé (plugin null ou pas de config), utiliser les styles par défaut
        if (STYLES.isEmpty()) {
            initializeDefaultStyles();
        }
    }

    /**
     * Initialise les styles par défaut (fallback pour tests ou pas de config.yml)
     */
    private void initializeDefaultStyles() {
        // Ces styles correspondent à l'ancienne architecture statique
        STYLES.put("twist", new GenericDanceStyle("twist", GenericDanceStyle.MovementType.WAVE, 0.0, 0.0));
        danceConfigs.put("twist", new DanceConfig("Twist", "danseur", "dance", GenericDanceStyle.MovementType.WAVE, 0.0, 0.0));

        STYLES.put("spin", new GenericDanceStyle("spin", GenericDanceStyle.MovementType.SPIN, 5.0, 0.0));
        danceConfigs.put("spin", new DanceConfig("Spin", "danseur", "dance", GenericDanceStyle.MovementType.SPIN, 5.0, 0.0));

        STYLES.put("disco", new GenericDanceStyle("disco", GenericDanceStyle.MovementType.STATIC, 0.0, 0.0));
        danceConfigs.put("disco", new DanceConfig("Disco", "danseur", "dance", GenericDanceStyle.MovementType.STATIC, 0.0, 0.0));

        STYLES.put("moonwalk", new GenericDanceStyle("moonwalk", GenericDanceStyle.MovementType.MOONWALK, 0.0, 0.0));
        danceConfigs.put("moonwalk", new DanceConfig("Moonwalk", "danseur", "dance", GenericDanceStyle.MovementType.MOONWALK, 0.0, 0.0));

        STYLES.put("wave", new GenericDanceStyle("wave", GenericDanceStyle.MovementType.WAVE, 0.0, 0.0));
        danceConfigs.put("wave", new DanceConfig("Wave", "danseur", "dance", GenericDanceStyle.MovementType.WAVE, 0.0, 0.0));

        STYLES.put("dj", new GenericDanceStyle("dj", GenericDanceStyle.MovementType.STATIC, 0.0, 0.0));
        danceConfigs.put("dj", new DanceConfig("DJ", "dj_animation1", "dj_dance", GenericDanceStyle.MovementType.STATIC, 0.0, 0.0));
    }

    /**
     * Charge toutes les dances depuis la section 'dances' de config.yml
     * Les styles sont créés dynamiquement via GenericDanceStyle
     */
    private void loadDancesFromConfig() {
        STYLES.clear();
        danceConfigs.clear();

        // En cas de plugin null (tests), ignorer le chargement
        if (plugin == null) {
            return;
        }

        org.bukkit.configuration.ConfigurationSection dancesSection = 
            plugin.getConfig().getConfigurationSection("dances");

        if (dancesSection == null) {
            plugin.getLogger().warning("Section 'dances' non trouvée dans config.yml");
            return;
        }

        for (String key : dancesSection.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection danceSection = 
                dancesSection.getConfigurationSection(key);

            if (danceSection == null) continue;

            try {
                String displayName = danceSection.getString("displayName", key);
                String modelId = danceSection.getString("modelId", "danseur");
                String animationName = danceSection.getString("animationName", "dance");
                String movementTypeStr = danceSection.getString("movementType", "STATIC");
                double rotationSpeed = danceSection.getDouble("rotationSpeed", 0.0);
                double radius = danceSection.getDouble("radius", 0.0);

                GenericDanceStyle.MovementType movementType;
                try {
                    movementType = GenericDanceStyle.MovementType.valueOf(movementTypeStr);
                } catch (IllegalArgumentException e) {
                    plugin.getLogger().warning("Type de mouvement invalide pour '" + key + "': " + movementTypeStr);
                    movementType = GenericDanceStyle.MovementType.STATIC;
                }

                // Convertir en isStatic et pattern
                boolean isStatic = movementType == GenericDanceStyle.MovementType.STATIC;
                String pattern = convertMovementTypeToPattern(movementType);

                // Créer le style dynamiquement
                GenericDanceStyle style = new GenericDanceStyle(
                    displayName,
                    isStatic,
                    pattern,
                    rotationSpeed,
                    radius
                );

                // Enregistrer le style et sa configuration
                STYLES.put(key.toLowerCase(), style);
                danceConfigs.put(key.toLowerCase(), new DanceConfig(
                    displayName,
                    modelId,
                    animationName,
                    movementType,
                    rotationSpeed,
                    radius
                ));
            } catch (Exception ex) {
                plugin.getLogger().log(java.util.logging.Level.WARNING, 
                    "Erreur en chargeant la danse '" + key + "'", ex);
            }
        }
    }

    public void startDance(Player player, DanceStyle style, boolean hideFromOwner, String targetName) {
        stopDance(player.getUniqueId());

        // Récupérer la config du style
        String styleName = style.getName().toLowerCase();
        DanceConfig config = danceConfigs.get(styleName);
        
        if (config == null) {
            player.sendMessage("§cErreur: Configuration manquante pour le style '" + style.getName() + "'");
            return;
        }

        // Cas 1 : On veut le skin d'un autre joueur (Asynchrone)
        if (targetName != null && !targetName.isBlank()) {
            SkinService.fetchSkin(plugin, targetName.trim(), (profile) -> {
                if (profile == null) {
                    player.sendMessage("§cJoueur introuvable ou erreur Mojang.");
                    return;
                }
                Dancer dancer = new ModelEngineDancer(plugin, config.modelId, config.animationName, profile);
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
            dancer = new ModelEngineDancer(plugin, config.modelId, config.animationName, null);
        } else {
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
                dancer.tick(0, style); // Le tick est géré par le style
            }, 0L, 1L);

            runningDances.put(uuid, running);
        } catch (Exception ex) {
            player.sendMessage("§cErreur lors du lancement: " + ex.getMessage());
            plugin.getLogger().log(java.util.logging.Level.SEVERE, "Erreur dance", ex);
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

    /**
     * Convertit un ancien MovementType en pattern pour GenericDanceStyle.
     */
    private String convertMovementTypeToPattern(GenericDanceStyle.MovementType movementType) {
        switch (movementType) {
            case STATIC:
            case DYNAMIC:
                return "wave";  // pattern par défaut
            case SPIN:
                return "spin";
            case ORBIT:
                return "orbit";
            case WAVE:
                return "wave";
            case MOONWALK:
                return "moonwalk";
            default:
                return "wave";
        }
    }
}