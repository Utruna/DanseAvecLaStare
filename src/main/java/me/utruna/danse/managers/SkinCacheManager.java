package me.utruna.danse.managers;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Cache persistant de skins (PlayerProfile) identifiés par un alias.
 *
 * <p>Les profils sont stockés dans {@code skin_cache.yml} via la sérialisation native
 * Bukkit ({@code ConfigurationSerializable}), ce qui préserve les données de texture
 * (value + signature) sans appel Mojang au rechargement.
 *
 * <p>Usage principal : pré-récupérer un skin une seule fois, le nommer, puis l'appliquer
 * à un ou plusieurs NPCs sans dépendre de la disponibilité de l'API Mojang.
 */
public class SkinCacheManager {

    private final Plugin plugin;
    private final Map<String, PlayerProfile> cache = new LinkedHashMap<>();

    public SkinCacheManager(Plugin plugin) {
        this.plugin = plugin;
        loadFromFile();
    }

    /** Enregistre un skin sous l'alias donné et persiste immédiatement. */
    @SuppressWarnings("deprecation")
    public void saveSkin(String alias, PlayerProfile profile) {
        cache.put(alias.toLowerCase(Locale.ROOT), profile);
        persist();
    }

    /** Retourne le profil mis en cache pour cet alias, ou {@code null} s'il est absent. */
    @SuppressWarnings("deprecation")
    public PlayerProfile getSkin(String alias) {
        return cache.get(alias.toLowerCase(Locale.ROOT));
    }

    /** Supprime l'alias du cache et persiste. Retourne {@code false} si l'alias était absent. */
    public boolean removeSkin(String alias) {
        boolean removed = cache.remove(alias.toLowerCase(Locale.ROOT)) != null;
        if (removed) persist();
        return removed;
    }

    /** Retourne la vue non-modifiable des alias enregistrés. */
    public Set<String> getAliases() {
        return Collections.unmodifiableSet(cache.keySet());
    }

    public boolean contains(String alias) {
        return cache.containsKey(alias.toLowerCase(Locale.ROOT));
    }

    @SuppressWarnings("deprecation")
    private void loadFromFile() {
        File file = getFile();
        if (!file.exists()) return;
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection section = yaml.getConfigurationSection("skins");
        if (section == null) return;
        int loaded = 0;
        for (String alias : section.getKeys(false)) {
            Object val = section.get(alias);
            if (val instanceof PlayerProfile profile) {
                cache.put(alias, profile);
                loaded++;
            }
        }
        if (loaded > 0) {
            plugin.getLogger().info("[SkinCache] " + loaded + " skin(s) chargé(s) depuis le cache.");
        }
    }

    @SuppressWarnings("deprecation")
    private synchronized void persist() {
        File file = getFile();
        YamlConfiguration yaml = new YamlConfiguration();
        for (Map.Entry<String, PlayerProfile> e : cache.entrySet()) {
            yaml.set("skins." + e.getKey(), e.getValue());
        }
        try {
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[SkinCache] Impossible de sauvegarder skin_cache.yml: " + e.getMessage());
        }
    }

    private File getFile() {
        plugin.getDataFolder().mkdirs();
        return new File(plugin.getDataFolder(), "skin_cache.yml");
    }
}
