package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

/**
 * Configurable dance style loaded from {@code config.yml}.
 * {@code STATIC} mode: the model stays at the player's position.
 * {@code DYNAMIC} mode: the yaw oscillates sinusoidally around the original yaw.
 */
public class GenericDanceStyle implements DanceStyle {

    private final String name;
    private final boolean isStatic;          // true = STATIC, false = DYNAMIC
    private final String pattern;            // For DYNAMIC: "wave"

    public enum MovementType {
        STATIC,     // Player stationary
        DYNAMIC     // Player in motion
    }

    public GenericDanceStyle(String name, boolean isStatic, String pattern, double rotationSpeed, double radius) {
        this.name = name;
        this.isStatic = isStatic;
        this.pattern = pattern != null ? pattern.toLowerCase() : "wave";
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Location computeLocation(Location origin, int tick) {
        Location loc = origin.clone();

        if (isStatic) {
            // STATIC: the player does not move
            return loc;
        }

        // DYNAMIC: simple rotation; visual motion is handled by the BBMODEL
        double phase = tick * 0.15;
        loc.setYaw(normalizeYaw(origin.getYaw() + (float) (Math.sin(phase) * 40.0)));
        return loc;
    }

    @Override
    public void applyPose(ArmorStand as, int tick) {
        // For ModelEngine, ArmorStand posing is ignored here.
        // The pose is handled by the ModelEngine blueprint.
    }

    protected float normalizeYaw(float yaw) {
        while (yaw >= 180.0f) yaw -= 360.0f;
        while (yaw < -180.0f) yaw += 360.0f;
        return yaw;
    }

    // Accessors
    public boolean isStatic() {
        return isStatic;
    }

    public String getPattern() {
        return pattern;
    }
}
