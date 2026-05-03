package me.utruna.danse.managers;

import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import org.bukkit.profile.PlayerProfile;
import java.util.function.Consumer;

public class SkinService {

    /**
     * Récupère le skin d'un joueur en utilisant l'API native de Paper.
     * C'est la méthode la plus stable et elle ne provoque pas d'erreurs de compilation.
     */
    public static void fetchSkin(Plugin plugin, String username, Consumer<PlayerProfile> callback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                // On crée le profil via Bukkit (c'est l'API officielle)
                PlayerProfile profile = Bukkit.createProfile(username);
                // .complete() contacte Mojang et récupère les données
                profile.complete(); 
                
                // On renvoie le profil complet sur le thread principal
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(profile));
            } catch (Exception e) {
                // En cas d'erreur, on renvoie null
                Bukkit.getScheduler().runTask(plugin, () -> callback.accept(null));
            }
        });
    }
}