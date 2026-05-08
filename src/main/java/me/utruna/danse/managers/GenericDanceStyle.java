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
    private final String pattern;            // Pour DYNAMIC : "spin", "orbit", "wave", "moonwalk"
    private final double rotationSpeed;      // En degrés par tick
    private final double radius;             // Pour ORBIT, en blocs

    public enum MovementType {
        STATIC,     // Joueur immobile
        DYNAMIC,    // Joueur se déplace
        // Anciens types (pour compatibilité)
        SPIN,       // Alias pour DYNAMIC avec pattern spin
        ORBIT,      // Alias pour DYNAMIC avec pattern orbit
        WAVE,       // Alias pour DYNAMIC avec pattern wave
        MOONWALK    // Alias pour DYNAMIC avec pattern moonwalk
    }

    /**
     * Constructeur simple pour un style statique.
     */
    public GenericDanceStyle(String name) {
        this(name, true, "none", 0.0, 0.0);  // isStatic=true
    }

    /**
     * Constructeur pour MovementType OLD API (compatibilité).
     */
    public GenericDanceStyle(String name, MovementType movementType, double rotationSpeed, double radius) {
        this(name, movementType == MovementType.STATIC, getPatternFromMovementType(movementType), rotationSpeed, radius);
    }

    /**
     * Constructeur complet avec pattern.
     */
    public GenericDanceStyle(String name, boolean isStatic, String pattern, double rotationSpeed, double radius) {
        this.name = name;
        this.isStatic = isStatic;
        this.pattern = pattern != null ? pattern.toLowerCase() : "wave";
        this.rotationSpeed = rotationSpeed;
        this.radius = radius;
    }

    private static String getPatternFromMovementType(MovementType movementType) {
        switch (movementType) {
            case SPIN:
                return "spin";
            case ORBIT:
                return "orbit";
            case WAVE:
                return "wave";
            case MOONWALK:
                return "moonwalk";
            default:
                return "wave";
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Location computeLocation(Location origin, int tick) {
        Location loc = origin.clone();

        if (isStatic) {
            // STATIC : Le joueur ne bouge pas, juste l'animation
            return loc;
        }

        // DYNAMIC : Appliquer le pattern de mouvement
        switch (pattern) {
            case "spin":
                // Rotation sur place
                float yaw = normalizeYaw(origin.getYaw() + (float) (rotationSpeed * tick));
                loc.setYaw(yaw);
                return loc;

            case "orbit":
                // Mouvement circulaire
                double angle = Math.toRadians(rotationSpeed * tick);
                double offsetX = radius * Math.sin(angle);
                double offsetZ = radius * Math.cos(angle);
                loc.add(offsetX, 0, offsetZ);
                loc.setYaw(normalizeYaw((float) Math.toDegrees(angle)));
                return loc;

            case "wave":
                // Ondulation sinusoïdale
                double phase = tick * 0.15;
                loc.setYaw(normalizeYaw(origin.getYaw() + (float) (Math.sin(phase) * 40.0)));
                return loc;

            case "moonwalk":
                // Mouvement en arrière (négatif)
                double backOffset = -0.1 * tick;
                loc.add(Math.sin(Math.toRadians(origin.getYaw())) * backOffset, 0,
                        Math.cos(Math.toRadians(origin.getYaw())) * backOffset);
                return loc;

            default:
                return loc;
        }
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

    public double getRotationSpeed() {
        return rotationSpeed;
    }

    public double getRadius() {
        return radius;
    }
}
