package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.Player;

/**
 * Contract for a dancer: animated entity attached to a player and updated every tick.
 * The current implementation is {@link ModelEngineDancer} (through ModelEngine).
 */
public interface Dancer {

    /**
    * Creates the dancer entity at the given position.
    * Must be called on the Bukkit main thread.
     *
    * @param location dancer spawn location
    * @param player   player owning the dance
    * @throws IllegalStateException if a prerequisite is missing (blueprint not found, etc.)
     */
    void spawn(Location location, Player player);

    /**
    * Updates the position and animation for the current tick.
    * Called every tick by the repeating task in {@link DanceManager}.
     *
    * @param tick  incremental counter since the dance started
    * @param style active style used to compute the position
     */
    void tick(int tick, DanceStyle style);

    /** Destroys the entity and releases all associated resources. */
    void stop();

    /** Updates the render distance if the implementation supports it. */
    default void setRenderRadius(int radius) {
    }

    /**
    * Changes the dummy visibility for its owner.
    * {@code true} -> visible only to the owner (preview mode).
    * {@code false} -> hidden from the owner (normal dance mode).
     */
    default void setOwnerCanSee(boolean canSee) {
    }
}
