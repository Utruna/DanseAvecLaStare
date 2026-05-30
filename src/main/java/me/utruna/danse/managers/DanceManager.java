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

/**
 * Gère le cycle de vie des danses actives pour les joueurs en ligne.
 * Charge les styles dynamiquement depuis {@code config.yml} et pilote les instances {@link Dancer}.
 */
public class DanceManager {

    private final DanseAvecLaStare plugin;
    private final Map<UUID, RunningDance> runningDances = new ConcurrentHashMap<>();
    private final Map<String, DanceStyle> STYLES = new HashMap<>();
    private final Map<String, DanceConfig> danceConfigs = new HashMap<>();
    private final Map<UUID, BukkitTask> previewTasks = new ConcurrentHashMap<>();
    private List<String> styleNamesCache = null;
    private int maxConcurrentDances = 500;

    private static class DanceConfig {
        public final String modelId;
        public final String animationName;
        public final String permission;

        public DanceConfig(String modelId, String animationName, String permission) {
            this.modelId = modelId;
            this.animationName = animationName;
            this.permission = permission;
        }
    }

    private static class RunningDance {
        BukkitTask task;
        Dancer dancer;
        boolean previousInvisible;
        int tickCounter = 0;
        String styleName;
    }

    /** Retourne {@code true} si le joueur a une danse en cours. */
    public boolean isDancing(UUID uuid) {
        return runningDances.containsKey(uuid);
    }

    /** Retourne le nom du style de danse actif pour le joueur, ou {@code null} s'il ne danse pas. */
    public String getActiveDanceStyle(UUID uuid) {
        RunningDance r = runningDances.get(uuid);
        return r == null ? null : r.styleName;
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
        styleNamesCache = null;
        maxConcurrentDances = (cfg != null) ? cfg.getInt("dance.maxConcurrent", 500) : 500;

        if (cfg == null) return;

        org.bukkit.configuration.ConfigurationSection dancesSection = cfg.getConfigurationSection("dances");

        if (dancesSection == null) return;

        for (String key : dancesSection.getKeys(false)) {
            org.bukkit.configuration.ConfigurationSection danceSection = dancesSection.getConfigurationSection(key);

            if (danceSection == null) continue;

            try {
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
                String permission = danceSection.getString("permission", null);
                danceConfigs.put(key.toLowerCase(), new DanceConfig(modelId, animationName, permission));
            } catch (Exception ex) {
                if (plugin != null) plugin.getLogger().log(Level.WARNING, "Erreur en chargeant la danse '" + key + "'", ex);
            }
        }
    }

    /** Retourne la permission requise pour lancer le style donné, ou {@code null} si aucun contrôle requis. */
    public String getPermission(String styleName) {
        if (styleName == null) return null;
        DanceConfig cfg = danceConfigs.get(styleName.toLowerCase(Locale.ROOT));
        return cfg == null ? null : cfg.permission;
    }

    /**
     * Lance une danse pour un joueur.
     * Si {@code targetName} est non nul, le skin est récupéré de manière asynchrone via Mojang ;
     * sinon le profil du joueur connecté est utilisé directement.
     *
     * @param player        joueur qui danse
     * @param style         style de danse à appliquer
     * @param hideFromOwner si {@code true}, rend le joueur invisible pendant la danse
     * @param targetName    pseudo du joueur dont le skin est utilisé, ou {@code null} pour le joueur lui-même
     * @throws nothing      retourne silencieusement si {@code dance.maxConcurrent} est atteint (message envoyé au joueur)
     */
    public void startDance(Player player, DanceStyle style, boolean hideFromOwner, String targetName) {
        if (maxConcurrentDances > 0 && runningDances.size() >= maxConcurrentDances) {
            player.sendMessage("§cTrop de danses actives, réessaie dans un moment.");
            return;
        }
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
                Player current = Bukkit.getPlayer(player.getUniqueId());
                if (current == null) return;
                if (profile == null) {
                    current.sendMessage("§cJoueur introuvable ou erreur Mojang: " + targetTrimmed);
                    return;
                }
                if (plugin.isPlayerDebug(current.getUniqueId())) plugin.getLogger().info("[DEBUG] Profil récupéré pour " + targetTrimmed);
                Dancer dancer = new ModelEngineDancer(plugin, config.modelId, config.animationName, profile);
                finishDanceSetup(current, dancer, style, hideFromOwner);
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

    /**
     * Identique à {@link #startDance} mais n'envoie pas de message au joueur.
     * Utilisé par {@link PlaylistManager} pour les transitions automatiques entre pistes.
     * Retourne silencieusement si {@code dance.maxConcurrent} est atteint.
     */
    public void startDanceSilent(Player player, DanceStyle style, boolean hideFromOwner, String targetName) {
        if (maxConcurrentDances > 0 && runningDances.size() >= maxConcurrentDances) return;
        stopDance(player.getUniqueId());

        String styleName = style.getName().toLowerCase();
        DanceConfig config = danceConfigs.get(styleName);
        if (config == null || config.modelId == null || config.modelId.isBlank()) return;

        boolean useModelEngine = Bukkit.getPluginManager().isPluginEnabled("ModelEngine")
                && plugin.getConfig().getBoolean("useModelEngine", false);
        if (!useModelEngine) return;

        if (targetName != null && !targetName.isBlank()) {
            SkinService.fetchSkin(plugin, targetName.trim(), profile -> {
                if (profile == null) return;
                Player current = Bukkit.getPlayer(player.getUniqueId());
                if (current == null) return;
                Dancer dancer = new ModelEngineDancer(plugin, config.modelId, config.animationName, profile);
                finishDanceSetup(current, dancer, style, hideFromOwner, true);
            });
            return;
        }

        @SuppressWarnings("deprecation")
        org.bukkit.profile.PlayerProfile currentProfile = player.getPlayerProfile();
        Dancer dancer = new ModelEngineDancer(plugin, config.modelId, config.animationName, currentProfile);
        finishDanceSetup(player, dancer, style, hideFromOwner, true);
    }

    private void finishDanceSetup(Player player, Dancer dancer, DanceStyle style, boolean hideFromOwner) {
        finishDanceSetup(player, dancer, style, hideFromOwner, false);
    }

    /**
     * Finalise la mise en place d'une danse après résolution du skin.
     * Vérifie {@code player.isOnline()} en tête : un fetch Mojang async peut durer plusieurs
     * secondes, le joueur peut s'être déconnecté entre l'appel à {@code fetchSkin} et l'exécution
     * du callback sur le thread principal.
     */
    private void finishDanceSetup(Player player, Dancer dancer, DanceStyle style, boolean hideFromOwner, boolean silent) {
        if (!player.isOnline()) return;
        final UUID uuid = player.getUniqueId();
        final Location origin = player.getLocation().clone();

        try {
            dancer.spawn(origin, player);
            if (!silent) player.sendMessage("§aDanse démarrée!");

            RunningDance running = new RunningDance();
            running.dancer = dancer;
            running.styleName = style.getName();
            running.previousInvisible = player.isInvisible();
            if (hideFromOwner) {
                player.setInvisible(true);
            }

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
            if (!silent) player.sendMessage("§cErreur lors du lancement: " + ex.getMessage());
            plugin.getLogger().log(Level.SEVERE, "Erreur dance", ex);
            player.setInvisible(false);
        }
    }

    /** Arrête la danse en cours pour le joueur et restaure son état de visibilité. */
    public void stopDance(UUID uuid) {
        if (!Bukkit.isPrimaryThread()) {
            Bukkit.getScheduler().runTask(plugin, () -> stopDance(uuid));
            return;
        }
        RunningDance running = runningDances.remove(uuid);
        if (running != null) {
            if (running.task != null) running.task.cancel();
            if (running.dancer != null) running.dancer.stop();
            Player p = Bukkit.getPlayer(uuid);
            if (p != null) p.setInvisible(running.previousInvisible);
        }
        BukkitTask previewTask = previewTasks.remove(uuid);
        if (previewTask != null) previewTask.cancel();
    }

    /** Enregistre la tâche d'arrêt automatique d'un aperçu, pour pouvoir l'annuler si stopDance est appelé avant. */
    public void registerPreviewTask(UUID uuid, BukkitTask task) {
        previewTasks.put(uuid, task);
    }

    /** Lance un aperçu de danse : le joueur reste visible et le dummy n'est visible que par lui. */
    public void startDancePreview(Player player, DanceStyle style) {
        startDance(player, style, false, null);
        RunningDance running = runningDances.get(player.getUniqueId());
        if (running != null && running.dancer != null) {
            running.dancer.setOwnerCanSee(true);
        }
    }

    /** Recharge les styles de danse depuis la configuration actuelle. */
    public void reloadStyles() {
        loadDancesFromConfig();
    }

    /** Arrête toutes les danses en cours. Appelé lors du {@code onDisable} du plugin. */
    public void stopAll() {
        new ArrayList<>(runningDances.keySet()).forEach(this::stopDance);
    }

    /** Met à jour la distance d'affichage des dummies ModelEngine déjà spawnés pour les joueurs. */
    public void applyRenderRadiusToActiveDances(int radius) {
        int clampedRadius = Math.max(1, radius);
        for (RunningDance running : runningDances.values()) {
            if (running != null && running.dancer != null) {
                running.dancer.setRenderRadius(clampedRadius);
            }
        }
    }

    /** Force la restauration de la visibilité d'un joueur (utilitaire admin). */
    public void restoreVisibility(UUID uuid) {
        // Remove any running dance state and ensure player is visible
        runningDances.remove(uuid);
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) p.setInvisible(false);
    }

    /** Force la restauration de la visibilité pour tous les joueurs. */
    public void restoreAllVisibility() {
        runningDances.clear();
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.setInvisible(false);
        }
    }

    /** Retourne la liste triée des identifiants de styles disponibles (résultat mis en cache). */
    public List<String> getStyleNames() {
        if (styleNamesCache == null) {
            styleNamesCache = STYLES.keySet().stream().sorted().collect(Collectors.toList());
        }
        return styleNamesCache;
    }

    /**
     * Résout un style par son nom de manière insensible à la casse.
     *
     * @return le {@link DanceStyle} correspondant, ou {@code null} si inconnu
     */
    public DanceStyle parseStyle(String value) {
        return value == null ? null : STYLES.get(value.toLowerCase(Locale.ROOT));
    }


}