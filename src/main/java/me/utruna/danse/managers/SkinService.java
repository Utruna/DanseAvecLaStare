package me.utruna.danse.managers;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class SkinService {

    /**
     * Récupère le skin d'un joueur avec ses textures Mojang de manière asynchrone.
     * Priorité : joueur en ligne (profil immédiat) → profile.update() (appel Mojang).
     * Le profil peut être null en cas d'erreur ou de pseudo inexistant.
     */
    @SuppressWarnings("deprecation")
    public static void fetchSkin(Plugin plugin, String username, Consumer<PlayerProfile> callback) {
        // Joueur en ligne : son profil a déjà les textures, pas besoin d'appel Mojang
        Player online = Bukkit.getPlayerExact(username);
        if (online != null) {
            callback.accept(online.getPlayerProfile());
            return;
        }

        // Joueur hors ligne : on crée le profil puis on le complète via l'API Mojang
        CompletableFuture.runAsync(() -> {
            try {
                PlayerProfile profile = Bukkit.createPlayerProfile(username);
                PlayerProfile updated = profile.update().join();
                if (updated != null && updated.getTextures().getSkin() != null) {
                    callback.accept(updated);
                } else {
                    plugin.getLogger().warning("[SkinService] Profil sans texture pour: " + username);
                    callback.accept(null);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("[SkinService] Erreur Mojang pour " + username + ": " + e.getMessage());
                callback.accept(null);
            }
        });
    }
}
