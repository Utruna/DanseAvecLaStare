package me.utruna.danse.managers;

import com.ticxo.modelengine.api.ModelEngineAPI;
import com.ticxo.modelengine.api.entity.Dummy;
import com.ticxo.modelengine.api.model.ActiveModel;
import com.ticxo.modelengine.api.model.ModeledEntity;
import me.utruna.danse.DanseAvecLaStare;
import org.bukkit.Bukkit;
import org.bukkit.Location;
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

    private static class StaticDancerEntry {
        @SuppressWarnings("deprecation")
        Dummy<PlayerProfile> dummy;
        ModeledEntity modeledEntity;
        ActiveModel activeModel;
        BukkitTask task;
        String resolvedAnimation;
        // Champs pour la persistance et le déplacement
        String styleName;
        String skinName;
        Location location;
    }

    public StaticDancerManager(DanseAvecLaStare plugin) {
        this.plugin = plugin;
    }

    // -------------------------------------------------------------------------
    // API publique
    // -------------------------------------------------------------------------

    /**
     * Fait apparaître un danseur statique avec le profil de skin du joueur.
     * skinName est sauvegardé pour restaurer le skin au redémarrage.
     */
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
            entry.resolvedAnimation = resolvedAnimation;
            entry.styleName = danceStyleName;
            entry.skinName = skinName;
            entry.location = location.clone();
            entry.task = startAnimationLoop(entry);

            activeDancers.put(id, entry);
            saveDancer(id, entry);
            plugin.getLogger().info("[StaticDancer] Danseur '" + id + "' apparu (style=" + danceStyleName + ", blueprint=" + blueprintId + ")");
            return true;

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "[StaticDancer] Erreur lors du spawn de '" + id + "'", e);
            return false;
        }
    }

    /**
     * Supprime un danseur et l'efface du fichier de sauvegarde.
     */
    public boolean removeStaticDancer(String id) {
        StaticDancerEntry entry = activeDancers.remove(id);
        if (entry == null) return false;
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
     * Déplace un danseur existant vers une nouvelle position et met à jour la sauvegarde.
     */
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

    /**
     * Renvoie un set immuable de tous les IDs actifs.
     */
    public Set<String> getDancerIds() {
        return Collections.unmodifiableSet(activeDancers.keySet());
    }

    /**
     * Nettoyage au onDisable : détruit les entités sans toucher au fichier
     * pour que les danseurs soient restaurés au prochain démarrage.
     */
    public void removeAll() {
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
    // Persistance
    // -------------------------------------------------------------------------

    /**
     * Charge et restaure tous les danseurs depuis static_dancers.yml.
     * Appelé dans onEnable, après que ModelEngine soit prêt.
     */
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
            String skin = ds.getString("skin"); // null si aucun skin configuré

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
                // Récupération du skin async → spawn sur le thread principal
                final String fId = id, fStyle = style, fSkin = skin;
                SkinService.fetchSkin(plugin, skin, profile ->
                        Bukkit.getScheduler().runTask(plugin, () ->
                                spawnStaticDancer(fId, loc, fStyle, profile, fSkin)
                        )
                );
            } else {
                spawnStaticDancer(id, loc, style, null, null);
            }
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

    private File getDancersFile() {
        plugin.getDataFolder().mkdirs();
        return new File(plugin.getDataFolder(), "static_dancers.yml");
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
