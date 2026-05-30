package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

/**
 * Contract for a dance style: computes the dancer's position and orientation every tick.
 * The standard implementation is {@link GenericDanceStyle}, configured through {@code config.yml}.
 */
public interface DanceStyle {

    /**
    * Computes the dancer's position and yaw for the current tick.
     *
    * @param origin player owner position at tick time
    * @param tick   incremental counter since the dance started
    * @return target location to apply to the model (cloned from origin)
     */
    Location computeLocation(Location origin, int tick);

    /**
    * Applies a pose to an ArmorStand.
    * Not used with ModelEngine (the pose is handled by the blueprint).
     */
    void applyPose(ArmorStand as, int tick);

    /** Text identifier for the style, as defined in {@code config.yml}. */
    String getName();
}
