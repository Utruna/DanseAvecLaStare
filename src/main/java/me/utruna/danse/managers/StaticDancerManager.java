package me.utruna.danse.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public class StaticDancerManager {

    private final DanseAvecLaStare plugin;
    private final Map<String, StaticDancerEntry> activeDancers = new ConcurrentHashMap<>();

    // --- Chorégraphie ---
    // groupId → set ordonné d'IDs de danseurs
    private final Map<String, Set<String>> choreographyGroups = new LinkedHashMap<>();
    // groupId → tâche partagée du groupe
    private final Map<String, BukkitTask> groupTasks = new ConcurrentHashMap<>();
    // dancerId → groupId (lookup inverse)
    private final Map<String, String> dancerToGroup = new ConcurrentHashMap<>();

    private static class StaticDancerEntry {
        @SuppressWarnings("deprecation")
        Dummy<PlayerProfile> dummy;
        ModeledEntity modeledEntity;
        ActiveModel activeModel;
        String currentBlueprintId;
        @SuppressWarnings("deprecation")
        PlayerProfile skinProfile;
        BukkitTask task;
        String resolvedAnimation;
        String styleName;
        String skinName;
        Location location;
        double scale = 1.0;
    }

    public StaticDancerManager(DanseAvecLaStare plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // API publique — danseurs statiques
    // -------------------------------------------------------------------------

    @SuppressWarnings("deprecation")
    public boolean spawnStaticDancer(String id, Location location, String danceStyleName,
                                     @Nullable PlayerProfile skinProfile, @Nullable String skinName) {
        if (activeDancers.containsKey(id)) {
            return false;
        }

        ConfigurationSection danceSection =
                plugin.getConfig().getConfigurationSection("dances." + danceStyleName);
        if (danceSection == null) {
            plugin.getLogger().warning("[StaticDancer] Style introuvable dans la config: " + danceStyleName);
            return false;
        }

        String modelId = danceSection.getString("modelId");
        String animationName = danceSection.getString("animationName");

        boolean useFallbackMode = plugin.getConfig().getBoolean("modelEngine.useFallbackMode", false);
        String fallbackModelId = plugin.getConfig().getString("modelEngine.fallbackModelId", "joueur_fallback");
        String blueprintId = (useFallbackMode && fallbackModelId != null && !fallbackModelId.isBlank())
                ? fallbackModelId.trim()
                : (modelId != null ? modelId.trim() : null);

        if (blueprintId == null || blueprintId.isBlank()) {
            plugin.getLogger().warning("[StaticDancer] Aucun modelId configuré pour le style: " + danceStyleName);
            return false;
        }

        try {
            PlayerProfile effectiveProfile = skinProfile != null
                    ? skinProfile
                    : Bukkit.createPlayerProfile(UUID.randomUUID(), id.substring(0, Math.min(id.length(), 16)));

            Dummy<PlayerProfile> dummy = new Dummy<>(effectiveProfile);
            dummy.setLocation(location);
            dummy.setYBodyRot(location.getYaw());
            dummy.setYHeadRot(location.getYaw());
            dummy.setRenderRadius(64);

            ModeledEntity modeledEntity = ModelEngineAPI.createModeledEntity(dummy);
            if (modeledEntity == null) {
                plugin.getLogger().warning("[StaticDancer] ModelEngine n'a pas pu créer l'entité pour: " + id);
                return false;
            }
            modeledEntity.registerSelf();

            ActiveModel activeModel = ModelEngineAPI.createActiveModel(blueprintId);
            if (activeModel == null) {
                modeledEntity.destroy();
                plugin.getLogger().warning("[StaticDancer] Blueprint introuvable dans ModelEngine: " + blueprintId);
                return false;
            }

            modeledEntity.addModel(activeModel, true);

            if (skinProfile != null) {
                applySkinToModel(activeModel, skinProfile);
            }

            String resolvedAnimation = resolveAnimation(activeModel, animationName, blueprintId);

            StaticDancerEntry entry = new StaticDancerEntry();
            entry.dummy = dummy;
            entry.modeledEntity = modeledEntity;
            entry.activeModel = activeModel;
            entry.currentBlueprintId = blueprintId;
            entry.skinProfile = effectiveProfile;
            entry.resolvedAnimation = resolvedAnimation;
            entry.styleName = danceStyleName;
            entry.skinName = skinName;
            entry.location = location.clone();

            // Si ce danseur appartient déjà à un groupe (chargé depuis choreography.yml),
            // on ne démarre pas de tâche individuelle — le groupe s'en chargera.
            if (!dancerToGroup.containsKey(id)) {
                entry.task = startAnimationLoop(entry);
            }

            activeDancers.put(id, entry);
            saveDancer(id, entry);
            plugin.getLogger().info("[StaticDancer] Danseur '" + id + "' apparu (style=" + danceStyleName + ", blueprint=" + blueprintId + ")");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[StaticDancer] Erreur lors du spawn de '" + id + "'", e);
            return false;
        }
    }

    public boolean removeStaticDancer(String id) {
        StaticDancerEntry entry = activeDancers.remove(id);
        if (entry == null) return false;

        // Notifier le PlaylistManager avant de détruire
        if (playlistManager != null) playlistManager.onDancerRemoved(id);

        // Retirer du groupe de chorégraphie si applicable
        String groupId = dancerToGroup.remove(id);
        if (groupId != null) {
            Set<String> groupIds = choreographyGroups.get(groupId);
            if (groupIds != null) {
                groupIds.remove(id);
                if (groupIds.isEmpty()) {
                    BukkitTask old = groupTasks.remove(groupId);
                    if (old != null) old.cancel();
                    choreographyGroups.remove(groupId);
                    saveChoreography();
                }
            }
        }

        try {
            destroyEntry(entry);
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[StaticDancer] Erreur lors de la destruction de '" + id + "'", e);
        }
        removeDancerFromFile(id);
        plugin.getLogger().info("[StaticDancer] Danseur '" + id + "' supprimé.");
        return true;
    }

    /**
     * Change le style de danse d'un danseur statique et persiste le changement.
     * Gère automatiquement la pause/reprise de la tâche (individuelle ou de groupe).
     */
    public boolean changeDancerStyle(String id, String styleName) {
        StaticDancerEntry entry = activeDancers.get(id);
        if (entry == null) return false;

        String groupId = dancerToGroup.get(id);
        if (groupId != null) {
            pauseGroupTask(groupId);
            boolean ok = changeAnimation(id, styleName);
            if (ok) saveDancer(id, entry);
            resumeGroupTask(groupId);
            return ok;
        } else {
            pauseAnimationTask(id);
            boolean ok = changeAnimation(id, styleName);
            if (ok) saveDancer(id, entry);
            resumeAnimationTask(id);
            return ok;
        }
    }

    public boolean setScale(String id, double scale) {
        StaticDancerEntry entry = activeDancers.get(id);
        if (entry == null || entry.activeModel == null) return false;
        entry.scale = scale;
        entry.activeModel.setScale(scale);
        saveDancer(id, entry);
        return true;
    }

    public boolean moveStaticDancer(String id, Location newLocation) {
        StaticDancerEntry entry = activeDancers.get(id);
        if (entry == null) return false;
        entry.dummy.setLocation(newLocation);
        entry.dummy.setYBodyRot(newLocation.getYaw());
        entry.dummy.setYHeadRot(newLocation.getYaw());
        entry.location = newLocation.clone();
        saveDancer(id, entry);
        return true;
    }

    public Set<String> getDancerIds() {
        return Collections.unmodifiableSet(activeDancers.keySet());
    }

    /**
     * Signale un danseur avec des particules pendant {@code seconds} secondes.
     * Retourne false si l'ID est inconnu.
     */
    public boolean highlightDancer(String id, int seconds) {
        StaticDancerEntry entry = activeDancers.get(id);
        if (entry == null) return false;

        Location loc = entry.location;
        if (loc == null || loc.getWorld() == null) return true;

        long totalTicks = seconds * 20L;
        long[] elapsed = {0};
        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            elapsed[0] += 4;
            if (elapsed[0] > totalTicks || !activeDancers.containsKey(id)) {
                task.cancel();
                return;
            }
            World w = loc.getWorld();
            if (w == null) { task.cancel(); return; }
            // Colonne visible au-dessus du danseur
            w.spawnParticle(Particle.TOTEM_OF_UNDYING, loc.clone().add(0, 1.0, 0), 10, 0.3, 0.6, 0.3, 0.05);
            w.spawnParticle(Particle.CRIT, loc.clone().add(0, 2.3, 0), 5, 0.15, 0.15, 0.15, 0.08);
        }, 0L, 4L);

        return true;
    }

    public void removeAll() {
        for (BukkitTask task : groupTasks.values()) {
            if (task != null) task.cancel();
        }
        groupTasks.clear();
        choreographyGroups.clear();
        dancerToGroup.clear();

        for (Map.Entry<String, StaticDancerEntry> e : activeDancers.entrySet()) {
            try {
                destroyEntry(e.getValue());
            } catch (Exception ex) {
                plugin.getLogger().log(Level.WARNING, "[StaticDancer] Erreur nettoyage de '" + e.getKey() + "'", ex);
            }
        }
        activeDancers.clear();
    }

    // -------------------------------------------------------------------------
    // API publique — chorégraphie
    // -------------------------------------------------------------------------

    /**
     * Crée (ou remplace) un groupe de chorégraphie, annule les tâches individuelles
     * des danseurs concernés et synchronise leurs animations immédiatement.
     *
     * @return false si au moins un ID n'existe pas en tant que danseur statique actif
     */
    public boolean createChoreography(String groupId, List<String> dancerIds) {
        for (String id : dancerIds) {
            if (!activeDancers.containsKey(id)) return false;
        }

        // Dissoudre l'ancien groupe s'il existait
        disbandGroupInternal(groupId);

        Set<String> ids = new LinkedHashSet<>(dancerIds);
        choreographyGroups.put(groupId, ids);

        for (String id : ids) {
            // Retirer d'un éventuel autre groupe
            String oldGroup = dancerToGroup.get(id);
            if (oldGroup != null && !oldGroup.equals(groupId)) {
                disbandGroupInternal(oldGroup);
            }
            // Annuler la tâche individuelle
            StaticDancerEntry entry = activeDancers.get(id);
            if (entry != null && entry.task != null) {
                entry.task.cancel();
                entry.task = null;
            }
            dancerToGroup.put(id, groupId);
        }

        BukkitTask task = startGroupTask(groupId);
        groupTasks.put(groupId, task);
        saveChoreography();
        return true;
    }

    /**
     * Ajoute un danseur à un groupe existant (ou le crée) et re-synchronise.
     *
     * @return false si le danseur n'existe pas
     */
    public boolean addToChoreography(String groupId, String dancerId) {
        if (!activeDancers.containsKey(dancerId)) return false;

        Set<String> ids = choreographyGroups.computeIfAbsent(groupId, k -> new LinkedHashSet<>());
        if (ids.contains(dancerId)) return true;

        // Retirer d'un éventuel autre groupe
        String oldGroup = dancerToGroup.get(dancerId);
        if (oldGroup != null && !oldGroup.equals(groupId)) {
            removeFromChoreography(oldGroup, dancerId);
        }

        // Annuler la tâche individuelle
        StaticDancerEntry entry = activeDancers.get(dancerId);
        if (entry != null && entry.task != null) {
            entry.task.cancel();
            entry.task = null;
        }

        ids.add(dancerId);
        dancerToGroup.put(dancerId, groupId);

        // Re-démarrer la tâche de groupe avec re-synchronisation
        BukkitTask old = groupTasks.remove(groupId);
        if (old != null) old.cancel();
        groupTasks.put(groupId, startGroupTask(groupId));
        saveChoreography();
        return true;
    }

    /**
     * Retire un danseur d'un groupe et restaure sa tâche individuelle.
     *
     * @return false si le groupe ou le danseur n'existe pas dans ce groupe
     */
    public boolean removeFromChoreography(String groupId, String dancerId) {
        Set<String> ids = choreographyGroups.get(groupId);
        if (ids == null || !ids.contains(dancerId)) return false;

        ids.remove(dancerId);
        dancerToGroup.remove(dancerId);

        // Restaurer la tâche individuelle
        StaticDancerEntry entry = activeDancers.get(dancerId);
        if (entry != null) {
            entry.task = startAnimationLoop(entry);
        }

        if (ids.isEmpty()) {
            BukkitTask old = groupTasks.remove(groupId);
            if (old != null) old.cancel();
            choreographyGroups.remove(groupId);
        }

        saveChoreography();
        return true;
    }

    /**
     * Arrête puis redémarre toutes les animations du groupe au même tick → re-synchronisation.
     *
     * @return false si le groupe n'existe pas
     */
    public boolean syncChoreography(String groupId) {
        if (!choreographyGroups.containsKey(groupId)) return false;

        BukkitTask old = groupTasks.remove(groupId);
        if (old != null) old.cancel();
        groupTasks.put(groupId, startGroupTask(groupId));
        return true;
    }

    /**
     * Supprime un groupe et restitue à chaque danseur sa tâche individuelle.
     *
     * @return false si le groupe n'existe pas
     */
    public boolean deleteChoreography(String groupId) {
        Set<String> ids = choreographyGroups.remove(groupId);
        if (ids == null) return false;

        if (playlistManager != null) playlistManager.onGroupDeleted(groupId);

        BukkitTask old = groupTasks.remove(groupId);
        if (old != null) old.cancel();

        for (String id : ids) {
            dancerToGroup.remove(id);
            StaticDancerEntry entry = activeDancers.get(id);
            if (entry != null) {
                entry.task = startAnimationLoop(entry);
            }
        }

        saveChoreography();
        return true;
    }

    /** Retourne une vue immuable des groupes de chorégraphie et de leurs membres. */
    public Map<String, Set<String>> getChoreographyGroups() {
        return Collections.unmodifiableMap(choreographyGroups);
    }

    /** Retourne les noms des groupes existants (pour la tab-completion). */
    public Set<String> getChoreographyGroupIds() {
        return Collections.unmodifiableSet(choreographyGroups.keySet());
    }

    /**
     * Respawn propre de tous les danseurs actifs pour renvoyer les packets à un joueur qui vient de se connecter.
     * On détruit et recrée chaque ModeledEntity (en conservant le même Dummy) afin que ME4 envoie
     * un spawn packet frais — un simple registerSelf() peut laisser l'ActiveModel détaché côté ME4,
     * ce qui provoque la disparition du modèle à la fin de l'animation en cours.
     */
    public void refreshAll() {
        for (Map.Entry<String, StaticDancerEntry> mapEntry : new ArrayList<>(activeDancers.entrySet())) {
            String id = mapEntry.getKey();
            StaticDancerEntry entry = mapEntry.getValue();
            if (entry.currentBlueprintId == null || entry.dummy == null) continue;
            try {
                if (swapModel(entry, entry.currentBlueprintId) && entry.resolvedAnimation != null && entry.activeModel != null) {
                    entry.activeModel.getAnimationHandler().playAnimation(
                            entry.resolvedAnimation, 0.0, 0.0, 1.0, true);
                }
            } catch (Exception e) {
                plugin.getLogger().log(Level.WARNING, "[StaticDancer] Erreur refreshAll pour '" + id + "'", e);
            }
        }
    }

    /**
     * Change l'animation d'un danseur statique vers le style donné.
     * La tâche individuelle DOIT être mise en pause avant d'appeler cette méthode
     * ({@link #pauseAnimationTask}) pour éviter les conflits de redémarrage tick-à-tick.
     * La transition est gérée par ModelEngine (lerpIn=0 = coupure nette).
     */
    public boolean changeAnimation(String id, String styleName) {
        StaticDancerEntry entry = activeDancers.get(id);
        if (entry == null || entry.activeModel == null) return false;

        String animName = resolveStyleAnimationName(styleName);
        if (animName == null) return false;

        String newBlueprintId = resolveStyleModelId(styleName);
        if (newBlueprintId != null && !newBlueprintId.equals(entry.currentBlueprintId)) {
            if (!swapModel(entry, newBlueprintId)) return false;
        }

        entry.resolvedAnimation = resolveAnimation(entry.activeModel, animName, styleName);
        entry.styleName = styleName;
        dbg("changeAnimation('" + id + "') → anim='" + entry.resolvedAnimation + "' blueprint='" + entry.currentBlueprintId + "'");
        entry.activeModel.getAnimationHandler().playAnimation(entry.resolvedAnimation, 0.0, 0.0, 1.0, true);
        return true;
    }

    /**
     * Change l'animation de tous les membres d'un groupe simultanément (même tick, synchronisé).
     * La tâche de groupe DOIT être mise en pause avant d'appeler cette méthode
     * ({@link #pauseGroupTask}) pour éviter que la tâche ne redémarre l'ancienne animation.
     */
    public boolean changeGroupAnimation(String groupId, String styleName) {
        Set<String> ids = choreographyGroups.get(groupId);
        if (ids == null || ids.isEmpty()) return false;

        String animName = resolveStyleAnimationName(styleName);
        if (animName == null) return false;

        String newBlueprintId = resolveStyleModelId(styleName);

        // Phase 1 : swap de modèle si nécessaire + mise à jour des métadonnées
        List<StaticDancerEntry> toPlay = new ArrayList<>();
        for (String id : ids) {
            StaticDancerEntry entry = activeDancers.get(id);
            if (entry == null || entry.activeModel == null) continue;
            if (newBlueprintId != null && !newBlueprintId.equals(entry.currentBlueprintId)) {
                if (!swapModel(entry, newBlueprintId)) continue;
            }
            entry.resolvedAnimation = resolveAnimation(entry.activeModel, animName, styleName);
            entry.styleName = styleName;
            toPlay.add(entry);
        }

        dbg("changeGroupAnimation('" + groupId + "') → style='" + styleName + "' sur " + toPlay.size() + " danseur(s)");

        // Phase 2 : démarrage simultané dans le même tick (lerpIn=0 = coupure nette)
        for (StaticDancerEntry entry : toPlay) {
            dbg("  → playAnimation('" + entry.resolvedAnimation + "') sur blueprint='" + entry.currentBlueprintId + "'");
            entry.activeModel.getAnimationHandler().playAnimation(entry.resolvedAnimation, 0.0, 0.0, 1.0, true);
        }
        return true;
    }

    // -------------------------------------------------------------------------
    // Pause / Resume des tâches (utilisé par PlaylistManager)
    // -------------------------------------------------------------------------

    /**
     * Annule la tâche d'animation individuelle du danseur sans le détruire.
     * Appelé par PlaylistManager quand il prend le contrôle des animations.
     */
    public void pauseAnimationTask(String id) {
        StaticDancerEntry entry = activeDancers.get(id);
        if (entry != null && entry.task != null) {
            entry.task.cancel();
            entry.task = null;
            dbg("pauseAnimationTask('" + id + "') → tâche annulée");
        } else {
            dbg("pauseAnimationTask('" + id + "') → rien à annuler");
        }
    }

    /**
     * Relance la tâche d'animation individuelle (danseur hors groupe uniquement).
     * Appelé par PlaylistManager quand il rend le contrôle.
     */
    public void resumeAnimationTask(String id) {
        StaticDancerEntry entry = activeDancers.get(id);
        if (entry != null && entry.task == null && !dancerToGroup.containsKey(id)) {
            entry.task = startAnimationLoop(entry);
            dbg("resumeAnimationTask('" + id + "') → tâche relancée (anim='" + entry.resolvedAnimation + "')");
        } else {
            dbg("resumeAnimationTask('" + id + "') → ignoré (entry=" + (entry != null) + " taskNull=" + (entry != null && entry.task == null) + " inGroup=" + dancerToGroup.containsKey(id) + ")");
        }
    }

    /**
     * Annule la tâche partagée du groupe sans dissoudre le groupe.
     * Appelé par PlaylistManager pour éviter les conflits avec ses propres transitions.
     */
    public void pauseGroupTask(String groupId) {
        BukkitTask task = groupTasks.remove(groupId);
        if (task != null) {
            task.cancel();
            dbg("pauseGroupTask('" + groupId + "') → tâche annulée");
        } else {
            dbg("pauseGroupTask('" + groupId + "') → aucune tâche active");
        }
    }

    /**
     * Relance la tâche partagée du groupe SANS re-synchroniser les animations.
     * Appelé par PlaylistManager quand il rend le contrôle du groupe.
     */
    public void resumeGroupTask(String groupId) {
        if (!choreographyGroups.containsKey(groupId) || groupTasks.containsKey(groupId)) {
            dbg("resumeGroupTask('" + groupId + "') → ignoré (existe=" + choreographyGroups.containsKey(groupId) + " déjàActif=" + groupTasks.containsKey(groupId) + ")");
            return;
        }
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Set<String> ids = choreographyGroups.get(groupId);
            if (ids == null) return;
            for (String id : ids) {
                StaticDancerEntry entry = activeDancers.get(id);
                if (entry == null || entry.activeModel == null || entry.resolvedAnimation == null) continue;
                if (!entry.activeModel.getAnimationHandler().isPlayingAnimation(entry.resolvedAnimation)) {
                    entry.activeModel.getAnimationHandler().playAnimation(entry.resolvedAnimation, 0.1, 0.1, 1.0, true);
                }
            }
        }, 1L, 1L);
        groupTasks.put(groupId, task);
        dbg("resumeGroupTask('" + groupId + "') → tâche relancée sans re-sync");
    }

    // -------------------------------------------------------------------------
    // Intégration PlaylistManager (callback pour le nettoyage)
    // -------------------------------------------------------------------------

    private PlaylistManager playlistManager;

    /** Enregistre le PlaylistManager pour notifier les suppressions de danseurs/groupes. */
    public void setPlaylistManager(PlaylistManager pm) {
        this.playlistManager = pm;
    }

    private void dbg(String msg) {
        if (playlistManager != null && playlistManager.isDebugEnabled()) {
            plugin.getLogger().info("[StaticDancer-DEBUG] " + msg);
        }
    }

    // -------------------------------------------------------------------------
    // Persistance
    // -------------------------------------------------------------------------

    public void loadFromFile() {
        File file = getDancersFile();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("dancers");
        if (section == null) return;

        plugin.getLogger().info("[StaticDancer] Restauration des danseurs depuis " + file.getName() + "...");

        for (String id : section.getKeys(false)) {
            ConfigurationSection ds = section.getConfigurationSection(id);
            if (ds == null) continue;

            String worldName = ds.getString("world");
            double x = ds.getDouble("x");
            double y = ds.getDouble("y");
            double z = ds.getDouble("z");
            float yaw = (float) ds.getDouble("yaw", 0.0);
            String style = ds.getString("style");
            String skin = ds.getString("skin");
            double scale = ds.getDouble("scale", 1.0);

            if (worldName == null || style == null) {
                plugin.getLogger().warning("[StaticDancer] Entrée invalide dans le fichier, ignorée: " + id);
                continue;
            }

            World world = Bukkit.getWorld(worldName);
            if (world == null) {
                plugin.getLogger().warning("[StaticDancer] Monde '" + worldName + "' non chargé, danseur ignoré: " + id);
                continue;
            }

            Location loc = new Location(world, x, y, z, yaw, 0f);

            if (skin != null && !skin.isBlank()) {
                final String fId = id, fStyle = style, fSkin = skin;
                final double fScale = scale;
                SkinService.fetchSkin(plugin, skin, profile ->
                        Bukkit.getScheduler().runTask(plugin, () -> {
                            spawnStaticDancer(fId, loc, fStyle, profile, fSkin);
                            if (fScale != 1.0) setScale(fId, fScale);
                        })
                );
            } else {
                spawnStaticDancer(id, loc, style, null, null);
                if (scale != 1.0) setScale(id, scale);
            }
        }
    }

    /**
     * Charge les groupes de chorégraphie depuis choreography.yml.
     * Doit être appelé APRÈS loadFromFile() + délai pour les fetches de skin.
     */
    public void loadChoreographyFromFile() {
        File file = getChoreographyFile();
        if (!file.exists()) return;

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection groups = yaml.getConfigurationSection("groups");
        if (groups == null) return;

        int restored = 0;
        for (String groupId : groups.getKeys(false)) {
            List<String> ids = groups.getStringList(groupId);
            if (ids.isEmpty()) continue;

            List<String> validIds = ids.stream().filter(activeDancers::containsKey).toList();
            if (validIds.isEmpty()) continue;

            if (validIds.size() < ids.size()) {
                plugin.getLogger().warning("[Choreo] Groupe '" + groupId + "': "
                        + (ids.size() - validIds.size()) + " danseur(s) introuvable(s), ignoré(s).");
            }

            if (createChoreography(groupId, validIds)) restored++;
        }

        if (restored > 0) {
            plugin.getLogger().info("[Choreo] " + restored + " groupe(s) de chorégraphie restauré(s).");
        }
    }

    private synchronized void saveDancer(String id, StaticDancerEntry entry) {
        File file = getDancersFile();
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        String path = "dancers." + id;
        yaml.set(path + ".world", entry.location.getWorld() != null ? entry.location.getWorld().getName() : "world");
        yaml.set(path + ".x", entry.location.getX());
        yaml.set(path + ".y", entry.location.getY());
        yaml.set(path + ".z", entry.location.getZ());
        yaml.set(path + ".yaw", (double) entry.location.getYaw());
        yaml.set(path + ".style", entry.styleName);
        yaml.set(path + ".skin", entry.skinName);
        yaml.set(path + ".scale", entry.scale);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[StaticDancer] Impossible de sauvegarder le fichier", e);
        }
    }

    private synchronized void removeDancerFromFile(String id) {
        File file = getDancersFile();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        yaml.set("dancers." + id, null);
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[StaticDancer] Impossible de mettre à jour le fichier", e);
        }
    }

    private synchronized void saveChoreography() {
        File file = getChoreographyFile();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, Set<String>> entry : choreographyGroups.entrySet()) {
            yaml.set("groups." + entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().log(Level.WARNING, "[Choreo] Impossible de sauvegarder choreography.yml", e);
        }
    }

    private File getDancersFile() {
        plugin.getDataFolder().mkdirs();
        return new File(plugin.getDataFolder(), "static_dancers.yml");
    }

    private File getChoreographyFile() {
        plugin.getDataFolder().mkdirs();
        return new File(plugin.getDataFolder(), "choreography.yml");
    }

    // -------------------------------------------------------------------------
    // Internals
    // -------------------------------------------------------------------------

    private BukkitTask startAnimationLoop(StaticDancerEntry entry) {
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (entry.resolvedAnimation == null || entry.activeModel == null) return;
            if (!entry.activeModel.getAnimationHandler().isPlayingAnimation(entry.resolvedAnimation)) {
                entry.activeModel.getAnimationHandler().playAnimation(entry.resolvedAnimation, 0.1, 0.1, 1.0, true);
            }
        }, 0L, 1L);
    }

    /**
     * Démarre la tâche partagée d'un groupe après avoir synchronisé immédiatement toutes les animations.
     */
    private BukkitTask startGroupTask(String groupId) {
        syncAnimations(groupId);
        return Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Set<String> ids = choreographyGroups.get(groupId);
            if (ids == null) return;
            for (String id : ids) {
                StaticDancerEntry entry = activeDancers.get(id);
                if (entry == null || entry.activeModel == null || entry.resolvedAnimation == null) continue;
                if (!entry.activeModel.getAnimationHandler().isPlayingAnimation(entry.resolvedAnimation)) {
                    entry.activeModel.getAnimationHandler().playAnimation(entry.resolvedAnimation, 0.1, 0.1, 1.0, true);
                }
            }
        }, 1L, 1L);
    }

    /**
     * Arrête toutes les animations du groupe puis les redémarre au même tick (tick 0 partagé).
     * fadeIn=0 pour éviter tout blend et repartir exactement depuis la frame 0.
     */
    private void syncAnimations(String groupId) {
        Set<String> ids = choreographyGroups.get(groupId);
        if (ids == null || ids.isEmpty()) return;

        // Étape 1 : stop de toutes les animations
        for (String id : ids) {
            StaticDancerEntry entry = activeDancers.get(id);
            if (entry == null || entry.activeModel == null || entry.resolvedAnimation == null) continue;
            try {
                entry.activeModel.getAnimationHandler().getClass()
                        .getMethod("stopAnimation", String.class)
                        .invoke(entry.activeModel.getAnimationHandler(), entry.resolvedAnimation);
            } catch (Exception ignored) {
                // stopAnimation peut ne pas exister selon la version de ModelEngine
            }
        }

        // Étape 2 : restart de toutes les animations dans le même tick
        for (String id : ids) {
            StaticDancerEntry entry = activeDancers.get(id);
            if (entry == null || entry.activeModel == null || entry.resolvedAnimation == null) continue;
            entry.activeModel.getAnimationHandler().playAnimation(entry.resolvedAnimation, 0.0, 0.0, 1.0, true);
        }
    }

    /** Résout le nom d'animation brut configuré pour un style de danse, ou {@code null} si inconnu. */
    private String resolveStyleAnimationName(String styleName) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("dances." + styleName);
        if (section == null) return null;
        return section.getString("animationName");
    }

    /** Résout le blueprintId (modelId) pour un style, en tenant compte du fallback. */
    private String resolveStyleModelId(String styleName) {
        boolean useFallback = plugin.getConfig().getBoolean("modelEngine.useFallbackMode", false);
        if (useFallback) return plugin.getConfig().getString("modelEngine.fallbackModelId", "joueur_fallback");
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("dances." + styleName);
        return section == null ? null : section.getString("modelId");
    }

    /**
     * Remplace l'ActiveModel d'une entrée par un nouveau blueprint.
     * Tente de retirer l'ancien via reflection, ajoute le nouveau et réapplique le skin.
     */
    /**
     * Remplace le modèle actif d'une entrée sans toucher au Dummy ni à la ModeledEntity.
     * Utilise directement l'API ME4 pour retirer l'ancien modèle et ajouter le nouveau —
     * la rotation du Dummy est ainsi préservée sans aucun respawn.
     */
    /**
     * Remplace le modèle en détruisant le ModeledEntity (despawn visuel propre)
     * tout en réutilisant le même Dummy — sa rotation est préservée, pas de glitch.
     */
    private boolean swapModel(StaticDancerEntry entry, String newBlueprintId) {
        try {
            ActiveModel newModel = ModelEngineAPI.createActiveModel(newBlueprintId);
            if (newModel == null) {
                plugin.getLogger().warning("[StaticDancer] Blueprint introuvable pour swap: " + newBlueprintId);
                return false;
            }

            // Détruire l'ancienne entité (despawn visuel de tous les modèles côté client)
            if (entry.modeledEntity != null) {
                try { entry.modeledEntity.destroy(); } catch (Exception ignored) {}
            }

            // Recréer un ModeledEntity sur le MÊME Dummy (rotation préservée)
            ModeledEntity newEntity = ModelEngineAPI.createModeledEntity(entry.dummy);
            if (newEntity == null) {
                plugin.getLogger().warning("[StaticDancer] Impossible de recréer l'entité pour swap: " + newBlueprintId);
                return false;
            }
            newEntity.registerSelf();
            newEntity.addModel(newModel, true);
            if (entry.skinProfile != null) applySkinToModel(newModel, entry.skinProfile);

            entry.modeledEntity      = newEntity;
            entry.activeModel        = newModel;
            entry.currentBlueprintId = newBlueprintId;
            if (entry.scale != 1.0) newModel.setScale(entry.scale);
            dbg("swapModel → blueprint='" + newBlueprintId + "' (dummy recyclé)");
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "[StaticDancer] Erreur swap modèle → " + newBlueprintId, e);
            return false;
        }
    }

    /** Dissout un groupe sans sauvegarder : annule la tâche et retire les membres de dancerToGroup. */
    private void disbandGroupInternal(String groupId) {
        Set<String> ids = choreographyGroups.remove(groupId);
        BukkitTask old = groupTasks.remove(groupId);
        if (old != null) old.cancel();
        if (ids != null) {
            for (String id : ids) {
                dancerToGroup.remove(id);
            }
        }
    }

    private void destroyEntry(StaticDancerEntry entry) {
        if (entry.task != null) entry.task.cancel();
        if (entry.modeledEntity != null) entry.modeledEntity.destroy();
        entry.dummy = null;
        entry.modeledEntity = null;
        entry.activeModel = null;
    }

    private String resolveAnimation(ActiveModel activeModel, String requestedName, String blueprintId) {
        List<String> available = new ArrayList<>();
        try {
            Object modelObj = invokeMethod(activeModel, "getModel");
            Object animationsObj = modelObj != null ? invokeMethod(modelObj, "getAnimations") : null;
            if (animationsObj == null) animationsObj = invokeMethod(activeModel, "getAnimations");
            if (animationsObj instanceof Map) {
                ((Map<?, ?>) animationsObj).keySet().forEach(k -> { if (k != null) available.add(k.toString()); });
            } else if (animationsObj instanceof Collection) {
                ((Collection<?>) animationsObj).forEach(k -> { if (k != null) available.add(k.toString()); });
            }
        } catch (Exception ignored) {}

        if (requestedName != null && !requestedName.isBlank()) {
            for (String a : available) {
                if (a.equalsIgnoreCase(requestedName)) return a;
            }
        }

        if (!available.isEmpty()) {
            String fallback = available.get(0);
            if (requestedName != null && !requestedName.isBlank()) {
                plugin.getLogger().warning("[StaticDancer] Animation '" + requestedName + "' introuvable sur '" + blueprintId + "'. Utilisation de '" + fallback + "'.");
            }
            return fallback;
        }
        return requestedName;
    }

    @SuppressWarnings("deprecation")
    private void applySkinToModel(ActiveModel activeModel, PlayerProfile skinProfile) {
        try {
            Object bonesObj = invokeMethod(activeModel, "getBones");
            if (!(bonesObj instanceof Map)) return;
            for (Map.Entry<?, ?> boneEntry : ((Map<?, ?>) bonesObj).entrySet()) {
                Object bone = boneEntry.getValue();
                try {
                    Object behaviors = bone.getClass().getMethod("getImmutableBoneBehaviors").invoke(bone);
                    if (!(behaviors instanceof Map)) continue;
                    for (Map.Entry<?, ?> bEntry : ((Map<?, ?>) behaviors).entrySet()) {
                        Object behavior = bEntry.getValue();
                        if (behavior != null) trySetTexture(behavior, skinProfile);
                    }
                } catch (Exception ignored) {}
            }
        } catch (Exception e) {
            plugin.getLogger().warning("[StaticDancer] Erreur applySkin: " + e.getMessage());
        }
    }

    @SuppressWarnings("deprecation")
    private void trySetTexture(Object behavior, PlayerProfile profile) {
        for (java.lang.reflect.Method m : behavior.getClass().getMethods()) {
            if (!m.getName().equals("setTexture") || m.getParameterCount() != 1) continue;
            try {
                if (m.getParameterTypes()[0].isInstance(profile)) {
                    m.invoke(behavior, profile);
                    return;
                }
            } catch (Exception ignored) {}
        }
    }

    private Object invokeMethod(Object obj, String methodName) {
        try {
            return obj.getClass().getMethod(methodName).invoke(obj);
        } catch (Exception e) {
            return null;
        }
    }
}
