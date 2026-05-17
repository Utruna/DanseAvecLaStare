package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

/**
 * Contrat d'un style de danse : calcule la position et l'orientation du danseur chaque tick.
 * L'implémentation standard est {@link GenericDanceStyle}, configurée via {@code config.yml}.
 */
public interface DanceStyle {

    /**
     * Calcule la position et le yaw du danseur pour le tick courant.
     *
     * @param origin position du joueur propriétaire au moment du tick
     * @param tick   compteur incrémentiel depuis le démarrage de la danse
     * @return       location cible à appliquer au modèle (clonée depuis origin)
     */
    Location computeLocation(Location origin, int tick);

    /**
     * Applique une pose à un ArmorStand.
     * Non utilisé avec ModelEngine (la pose est gérée par le blueprint).
     */
    void applyPose(ArmorStand as, int tick);

    /** Identifiant textuel du style, tel que défini dans {@code config.yml}. */
    String getName();
}
