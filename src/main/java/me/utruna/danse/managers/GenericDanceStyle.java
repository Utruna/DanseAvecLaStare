package me.utruna.danse.managers;

import org.bukkit.Location;
import org.bukkit.entity.ArmorStand;

/**
 * Classe de style de danse générique et configurable.
 * Deux modes : STATIC (joueur immobile) ou DYNAMIC (joueur se déplace).
 */
public class GenericDanceStyle implements DanceStyle {

    private final String name;
    private final boolean isStatic;          // true = STATIC, false = DYNAMIC
    private final String pattern;            // Pour DYNAMIC : "wave"

    public enum MovementType {
        STATIC,     // Joueur immobile
        DYNAMIC     // Joueur se déplace
    }

    /**
     * Constructeur complet avec pattern.
     */
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
            // STATIC : Le joueur ne bouge pas
            return loc;
        }

        // DYNAMIC : rotation simple; les mouvements visuels sont gérés par le BBMODEL
        double phase = tick * 0.15;
        loc.setYaw(normalizeYaw(origin.getYaw() + (float) (Math.sin(phase) * 40.0)));
        return loc;
    }

    @Override
    public void applyPose(ArmorStand as, int tick) {
        // Pour ModelEngine, on ne s'occupe pas de la pose des ArmorStands
        // La pose est gérée par le blueprint ModelEngine
    }

    protected float normalizeYaw(float yaw) {
        while (yaw >= 180.0f) yaw -= 360.0f;
        while (yaw < -180.0f) yaw += 360.0f;
        return yaw;
    }

    // Getters
    public boolean isStatic() {
        return isStatic;
    }

    public String getPattern() {
        return pattern;
    }
}
