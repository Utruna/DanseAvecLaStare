package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Contrat d'un danseur : entité animée associée à un joueur, mise à jour chaque tick.
 * L'implémentation actuelle est {@link ModelEngineDancer} (via ModelEngine).
 */
public interface Dancer {

    /**
     * Crée l'entité danseur à la position donnée.
     * Doit être appelé sur le thread principal Bukkit.
     *
     * @param location position de départ du danseur
     * @param player   joueur propriétaire de la danse
     * @throws IllegalStateException si un prérequis est manquant (blueprint introuvable, etc.)
     */
    void spawn(Location location, Player player);

    /**
     * Met à jour la position et l'animation pour le tick courant.
     * Appelé chaque tick par la tâche répétitive du {@link DanceManager}.
     *
     * @param tick  compteur incrémentiel depuis le démarrage de la danse
     * @param style style actif utilisé pour calculer la position
     */
    void tick(int tick, DanceStyle style);

    /** Détruit l'entité et libère toutes les ressources associées. */
    void stop();
}
