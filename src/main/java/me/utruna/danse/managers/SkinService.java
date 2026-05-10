package me.utruna.danse.managers;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;

public class SkinService {

    /**
     * Récupère le skin d'un joueur en utilisant l'API native de Paper de manière asynchrone.
     * Le profil peut être null si le joueur n'est pas trouvé ou s'il y a une erreur Mojang.
     */
    @SuppressWarnings("deprecation")
    public static void fetchSkin(Plugin plugin, String username, Consumer<PlayerProfile> callback) {
        // Récupérer le profil de manière asynchrone
        CompletableFuture.runAsync(() -> {
            try {
                // Utiliser l'API Bukkit pour récupérer le profil du joueur avec sa texture
                PlayerProfile profile = Bukkit.createPlayerProfile(username);
                if (profile != null && !profile.getName().isEmpty()) {
                    callback.accept(profile);
                } else {
                    plugin.getLogger().warning("Could not fetch profile for player: " + username);
                    callback.accept(null);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("Error fetching skin for " + username + ": " + e.getMessage());
                callback.accept(null);
            }
        });
    }
}